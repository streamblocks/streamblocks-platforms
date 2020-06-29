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


        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve("simulate.cpp"));
        BufferedReader reader;
        try {


            reader = new BufferedReader(
                    new InputStreamReader(getClass().getResourceAsStream("/lib/systemc/simulate.cpp")));
            String line  = reader.readLine();
            while (line != null) {
                emitter().emitRawLine(line);
                line = reader.readLine();
            }

        } catch (IOException e) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Could not get the SystemC simulator"));
        }

        emitter().emit("int sc_main(int argc, char *argv[]) {");
        {
            emitter().increaseIndentation();
            emitter().emit("Options opts = parseArgs(argc, argv);");
            emitter().emit("const sc_time period(opts.period, SC_NS);");
            emitter().emit("using Network = ap_rtl::NetworkTester;");
            emitter().emit("using PortAddress = Network::PortAddress;");
            // -- create the network object
            emitter().emit("std::unique_ptr<Network> mut = std::make_unique<Network>(\"network\", period, opts.trace_level);");
            emitter().emit("// -- get the file streams");
            // -- get the writers
            network.getInputPorts().forEach(this::getWriter);
            // -- get the readers
            network.getOutputPorts().forEach(this::getReader);
            // -- get host buffers
            emitter().emit("// -- allocate host buffers");
            ImmutableList.concat(network.getInputPorts(), network.getOutputPorts()).forEach(this::getHostBuffer);
            emitter().emitNewLine();

            // -- allocate memory on the device
            emitter().emit("// -- allocate memory on the device");
            ImmutableList.concat(network.getInputPorts(), network.getOutputPorts()).forEach(this::getAllocateCall);

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
        emitter().emit("std::vector<%s> buffer_%s(opts.alloc_size);", type.getType(), port.getName());
        emitter().emitNewLine();
    }

    default void getAllocateCall(PortDecl port) {
        emitter().emit("mut->allocateMemory(PortAddress::%s, opts.alloc_size);", port.getName());
        emitter().emitNewLine();
    }
    default void getSimulationLoop(Network network) {

        emitter().emit("std::size_t total_ticks = 0;");
        emitter().emit("auto start_time = std::chrono::high_resolution_clock::now();");
        emitter().emit("while(true) {");
        {

            emitter().increaseIndentation();
            emitter().emitNewLine();

            network.getInputPorts().forEach(port -> {
                emitter().emit("auto req_sz_%s = writer_%1$s.fillBuffer(buffer_%1$s);", port.getName());
            });

            emitter().emitNewLine();

            // -- set args
            emitter().emit("// -- set request size and capacity arguments");
            network.getInputPorts().forEach(port -> {
                emitter().emit("mut->setArg(PortAddress::%s, req_sz_%1$s);", port.getName());
            });

            network.getOutputPorts().forEach(port -> {
                emitter().emit("mut->setArg(PortAddress::%s, opts.alloc_size);", port.getName());
            });

            emitter().emitNewLine();

            // -- write to device memory
            emitter().emit("// -- write host buffers to device memory");
            network.getInputPorts().forEach(port -> {
                emitter().emit("mut->writeDeviceMemory(PortAddress::%s, buffer_%1$s);", port.getName());
            });

            emitter().emit("auto ticks = mut->simulate(opts.report_every);");

            emitter().emitNewLine();

            // -- get production and consumption info

            emitter().emit("// -- production and consumption info");
            ImmutableList.concat(network.getInputPorts(), network.getOutputPorts()).forEach(port -> {
                emitter().emit("auto size_%s = mut->querySize(PortAddress::%1$s);", port.getName());
            });

            emitter().emitNewLine();

            // -- read from device memory

            emitter().emit("// -- read from device memory");
            network.getOutputPorts().forEach(port -> {
                emitter().emit("mut->readDeviceMemory(PortAddress::%s, buffer_%1$s);", port.getName());
            });

            // -- consume tokens
            emitter().emit("// -- consume tokens and input ports");

            network.getInputPorts().forEach(port-> {
                emitter().emit("writer_%s.consumeTokens(size_%1$s);", port.getName());
            });

            emitter().emit("// -- verify produced tokens;");
            network.getOutputPorts().forEach(port -> {
                emitter().emit("reader_%s.verify(buffer_%1$s, size_%1$s);", port.getName());
            });

            // -- dump profiling info
            getProfileDump();

            emitter().emit("total_ticks += ticks;");

            emitter().emitNewLine();

            emitter().emit("bool no_input_tokens = %s;",
                    String.join(" && ",
                            network.getInputPorts().map(port -> String.format("req_sz_%s == 0", port.getName()))));
            emitter().emit("bool no_consumption = %s;",
                    String.join(" && ",
                            network.getInputPorts().map(port -> String.format("size_%s == 0", port.getName()))));
            emitter().emit("bool no_production = %s;",
                    String.join(" && ",
                            network.getOutputPorts().map(port -> String.format("size_%s == 0", port.getName()))));

            emitter().emitNewLine();
            emitter().emit("if (no_consumption && no_production) {");
            {
                emitter().increaseIndentation();
                emitter().emit("if (!no_input_tokens) {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("WARNING(\"Network may be deadlocked, not all tokens have been consumed\");");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
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
        emitter().emit("STATUS_REPORT(\"simulation finished after %%lu ticks and %%3.6f milliseconds\", " +
                "total_ticks, total_time.count() / 1e3);");

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
            emitter().emit("} else {" );
            {
                emitter().increaseIndentation();

                emitter().emit("WARNING(\"Could not open %%s\\n\", opts.profile_dump.c_str());" );

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().decreaseIndentation();

        }
        emitter().emit("}");
    }

}