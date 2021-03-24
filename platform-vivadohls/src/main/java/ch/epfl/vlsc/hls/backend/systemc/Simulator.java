package ch.epfl.vlsc.hls.backend.systemc;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Stream;

@Module
public interface Simulator {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void genrateSimulator(Network network) {


        emitter().open(PathUtils.getTarget(backend().context()).resolve("systemc/src/simulate.cpp"));

        emitter().emit("#include \"simulation-kernel.h\"");
        emitter().emit("#include \"simulate.h\"");
        emitter().emitNewLine();

        emitter().emit("int sc_main(int argc, char *argv[]) {");
        {
            emitter().increaseIndentation();
            emitter().emit("Options opts = parseArgs(argc, argv);");
            emitter().emit("const sc_time period(opts.period, SC_NS);");
            emitter().emit("using Network = ap_rtl::SimulationKernel;");
            emitter().emit("using PortAddress = Network::PortAddress;");
            // -- create the network object
            emitter().emit("std::unique_ptr<Network> mut = std::make_unique<Network>(\"network\", period, opts.queue_capacity, opts.trace_level);");
            emitter().emit("// -- get the file streams");
            // -- get the writers
            network.getInputPorts().forEach(this::getWriter);
            // -- get the readers
            network.getOutputPorts().forEach(this::getReader);
            // -- get host buffers
            emitter().emit("// -- host/device buffers");
            ImmutableList.concat(network.getInputPorts(), network.getOutputPorts()).forEach(this::getHostBuffer);
            emitter().emitNewLine();


            // -- reset the device

            emitter().emit("mut->reset();");

            emitter().emitNewLine();

            // -- simulation loop
            getSimulationLoop(network);

            emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().close();

    }

    default LogicVector getSCPortType(PortDecl port) {
        int bitWidth = backend().typeseval().sizeOfBits(backend().types().declaredPortType(port));
        return new LogicVector(bitWidth);
    }

    default void getWriter(PortDecl inputPort) {

        LogicVector type = getSCPortType(inputPort);

        emitter().emit("Writer<%s> writer_%s(opts.alloc_size, \"%2$s\");", type.getType(), inputPort.getName());
        emitter().emitNewLine();
    }

    default void getReader(PortDecl outputPort) {

        LogicVector type = getSCPortType(outputPort);

        emitter().emit("Reader<%s> reader_%s(opts.alloc_size, \"%2$s\");", type.getType(), outputPort.getName());
        emitter().emitNewLine();

    }

    default void getHostBuffer(PortDecl port) {

        LogicVector type = getSCPortType(port);
        emitter().emit("SimulationBuffer<%s> buffer_%s(opts.alloc_size);", type.getType(), port.getName());
        emitter().emitNewLine();
    }


    default void getSimulationLoop(Network network) {

        emitter().emit("std::size_t total_ticks = 0;");
        emitter().emit("bool should_retry = false;");
        emitter().emit("auto start_time = std::chrono::high_resolution_clock::now();");
        emitter().emit("while(true) {");
        {

            emitter().increaseIndentation();
            emitter().emitNewLine();

            // -- set args
            emitter().emit("STATUS_REPORT(\"Setting simulation kernel arguments\\n\");");


            emitter().emitNewLine();
            emitter().emit("std::size_t new_tokens = 0;");
            // -- update input tokens
            emitter().emit("STATUS_REPORT(\"Reading input streams from file\\n\");");
            network.getInputPorts().forEach(port -> {
                emitter().emit("{");
                {
                    emitter().increaseIndentation();
                    emitter().emit("new_tokens += writer_%s.update(&buffer_%1$s);", port.getName());

                    emitter().emit("STATUS_REPORT(\"%s enqueued %%lu tokens\\n\", new_tokens);", port.getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
            });

            emitter().emitNewLine();

            // -- check if we can start the simulation
            emitter().emit("if (new_tokens > 0 || should_retry) {");
            {
                emitter().increaseIndentation();

                emitter().emit("// -- set kernel arguments");
                emitter().emit("int arg_ix = 0;");
                ImmutableList.concat(network.getInputPorts(), network.getOutputPorts()).forEach(port -> {
                    emitter().emit("mut->setArg(arg_ix++, buffer_%s.asArgument());", port.getName());
                });

                emitter().emit("STATUS_REPORT(\"Starting simulation\\n\");");
                emitter().emit("auto ticks = mut->simulate(opts.report_every);");
                emitter().emit("total_ticks += ticks;");
                emitter().emit("STATUS_REPORT(\"Simulation returned after %%lu ticks " +
                        "(total ticks so far: %%lu)\\n\", ticks, total_ticks);");
                emitter().emit("STATUS_REPORT(\"Verifying produced outputs\\n\");");


                emitter().emit("STATUS_REPORT(\"Updating input stream indices\\n\");");
                network.getInputPorts().forEach(port -> {
                    emitter().emit("{");
                    {
                        emitter().increaseIndentation();

                        emitter().emit("auto old_tail = buffer_%s.tail;", port.getName());
                        emitter().emit("auto new_tail = reinterpret_cast<uint32_t const*>(buffer_%s.meta_buffer)[0];",
                                port.getName());
                        emitter().emit("auto consumption = distance(old_tail, new_tail, buffer_%s.alloc_size);",
                                port.getName());
                        emitter().emit("STATUS_REPORT(\"%s consumed %%u tokens\\n\", consumption);", port.getName());

                        emitter().emit("buffer_%s.tail = new_tail;",
                                port.getName());
                        emitter().decreaseIndentation();
                    }
                    emitter().emit("}");
                });

                emitter().emit("uint32_t new_production = 0;");
                emitter().emit("should_retry = false;");
                network.getOutputPorts().forEach(port -> {
                    emitter().emit("{");
                    {
                        emitter().increaseIndentation();
                        emitter().emit("auto old_head = buffer_%s.head;", port.getName());
                        emitter().emit("auto new_head = reinterpret_cast<uint32_t const*>(buffer_%s.meta_buffer)[0];",
                                port.getName());
                        emitter().emit("auto production = distance(old_head, new_head, buffer_%s.alloc_size);",
                                port.getName());
                        emitter().emit("STATUS_REPORT(\"%s produced %%u tokens\\n\", production);", port.getName());
                        emitter().emit("buffer_%s.head = new_head;", port.getName());
                        emitter().emit("should_retry |= (reader_%s.update(&buffer_%1$s) " +
                                "== buffer_%1$s.alloc_size - 1);", port.getName());

                        emitter().decreaseIndentation();
                        emitter().decreaseIndentation();
                    }
                    emitter().emit("}");
                });

                emitter().emit("STATUS_REPORT(\"Saving profiling data\\n\");");

                getProfileDump();

                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("STATUS_REPORT(\"Simulation can not make anymore progress\\n\");");
                emitter().emit("break;");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            emitter().emitNewLine();

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emit("auto finish_time = std::chrono::high_resolution_clock::now();");
        emitter().emit("auto total_time = std::chrono::duration_cast<std::chrono::milliseconds>(finish_time - start_time);");
        emitter().emit("STATUS_REPORT(\"simulation finished after %%lu ticks and %%3.6f milliseconds\\n\", " +
                "total_ticks, total_time.count() / 1e3);");
        emitter().emit("return 0;");
    }

    default void getProfileDump() {

        emitter().emit("if (!opts.profile_dump.empty()) {");
        {
            emitter().increaseIndentation();

            emitter().emit("std::ofstream prof(opts.profile_dump, std::ios::out);");
            emitter().emit("if (prof.is_open()) {");
            {
                emitter().increaseIndentation();

                emitter().emit("mut->dumpStats(prof);");
                emitter().emit("prof.close();");

                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();

                emitter().emit("WARNING(\"Could not open %%s\\n\", opts.profile_dump.c_str());");

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().decreaseIndentation();

        }
        emitter().emit("}");
    }

}