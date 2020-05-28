package ch.epfl.vlsc.hls.backend.systemc;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.Binding;
import se.lth.cs.tycho.ir.entity.PortDecl;


@Module
public interface SystemCTestBench {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default String getPostfix() {
        return "_tester";
    }
    default void generateTester() {

        SCNetwork network = backend().scnetwork().createSCNetwork(backend().task().getNetwork());

        String identifier = network.getIdentifier() + getPostfix();

        emitter().open(PathUtils.getTargetCodeGenInclude(backend().context()).resolve(identifier + ".h"));
        emitter().emit("#ifndef __%s_H__", identifier);
        emitter().emit("#define __%s_H__", identifier);
        emitter().emitNewLine();

        emitter().emitNewLine();
        getIncludes(network);

        emitter().emitNewLine();
        emitter().emit("namespace ap_rtl {");
        emitter().emitNewLine();
        emitter().emit("class %s: public sc_module {", identifier);
        {
            emitter().emit("public:");
            emitter().emitNewLine();
            emitter().increaseIndentation();

            // -- get signals of the network
            getModuleSignals(network);

            // -- get simulation related fields
            getSimFields(network);

            // -- get network instnace
            emitter().emit("// -- systemc network under test");
            emitter().emit("%s inst_%1$s;", network.getIdentifier());

            emitter().emitNewLine();

            // -- get queue emulators
            getQueueEmulators(network);

            // -- get reseter
            getReseter(network);
            // -- get simulator
            getSimulator(network);
            emitter().emitNewLine();

            emitter().emit("SC_HAS_PROCESS(%s);", identifier);
            emitter().emitNewLine();

            // -- get constructor
            getConstructor(network, identifier);

            emitter().decreaseIndentation();
        }
        emitter().emit("}; // class %s", identifier);
        emitter().emitNewLine();
        emitter().emit("} // namespace ap_rtl");
        emitter().emitNewLine();
        emitter().emit("#endif // __%s_H__", identifier);
        emitter().close();
    }


    default void getIncludes(SCNetwork network) {

        emitter().emit("#include \"%s.h\"", network.getIdentifier());
        emitter().emit("#include \"sim_queue.h\"");
        emitter().emit("#include <iostream>");
        emitter().emit("#include <fstream>");
        emitter().emit("#include <string>");
        emitter().emitNewLine();
    }


    default void getModuleSignals(SCNetwork network) {

        emitter().emit("sc_clock %s;", network.getApControl().getClockSignal().getName());
        network.getApControl().stream().forEach(port -> {
            if (!port.equals(network.getApControl().getClock()))
                emitter().emit("sc_signal<%s> %s;", port.getSignal().getType(), port.getSignal().getName());
        });
        emitter().emit("// -- network inputs");
        network.getInputs().stream()
                .flatMap(SCNetwork.InputIF::stream)
                .forEach(port -> {
                    if (!port.equals(network.getApControl().getClock()))
                        emitter().emit("sc_signal <%s> %s;",
                                port.getSignal().getType(), port.getSignal().getName());
                });
        emitter().emit("// -- network outputs");
        network.getOutputs().stream()
                .flatMap(SCNetwork.OutputIF::stream)
                .forEach(port -> {
                    emitter().emit("sc_signal <%s> %s;",
                            port.getSignal().getType(), port.getSignal().getName());
                });


        emitter().emitNewLine();
    }

    default void getSimFields(SCNetwork network) {

        // -- clock period
        emitter().emit("// -- simulation related stuff");
        emitter().emit("const sc_time clock_period;");
        emitter().emit("// -- software feeder queues");
        network.getInputs().map(SCNetwork.InputIF::getPort).forEach(input -> {
            String type = backend().typeseval().type(backend().types().declaredPortType(input));
            emitter().emit("SimQueue::InputQueue<%s> sim_buffer_%s;", type, input.getName());
        });
        emitter().emit("// -- software eater queues");
        network.getOutputs().map(SCNetwork.OutputIF::getPort).forEach(output -> {
            String type = backend().typeseval().type(backend().types().declaredPortType(output));
            emitter().emit("SimQueue::OutputQueue<%s> sim_buffer_%s;", type, output.getName());
        });
        emitter().emitNewLine();
    }

    default void getQueueEmulators(SCNetwork network) {

        network.getInputs().forEach(this::getInputFeeder);
        network.getOutputs().forEach(this::getOutputEater);

    }

    default void getInputFeeder(SCNetwork.InputIF input) {

        String simBufferName = "sim_buffer_" + input.getPort().getName();
        emitter().emit("// -- input queue emulator for %s", input.getPort().getName());
        emitter().emit("void input_feeder_%s () {", input.getPort().getName());
        {
            emitter().increaseIndentation();
            String type = backend().typeseval().type(backend().types().declaredPortType(input.getPort()));
            // -- set din and peek
            emitter().emit("%s token = %s.peek();", type , simBufferName);
            emitter().emit("%s.write(token);", input.getWriter().getDin().getSignal().getName());

            emitter().emitNewLine();

            // -- write to the systemc input queue
            emitter().emit("// -- write to the systemc input queue from the sim buffer");
            emitter().emit("if (%s.empty_n() == true && %s.read() == SC_LOGIC_1) {",
                    simBufferName, input.getWriter().getFullN().getSignal().getName());
            {
                emitter().increaseIndentation();
                emitter().emit("%s.write(SC_LOGIC_1);", input.getWriter().getWrite().getSignal().getName());
                // -- consume from the sim buffer;
                emitter().emit("%s.dequeue();", simBufferName);
                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("%s.write(SC_LOGIC_0);", input.getWriter().getWrite().getSignal().getName());
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void getOutputEater(SCNetwork.OutputIF output) {
        String simBufferName = "sim_buffer_" + output.getPort().getName();

        emitter().emit("// -- output queue emulator for %s", output.getPort().getName());
        emitter().emit("void output_eater_%s () {", output.getPort().getName());
        {
            emitter().increaseIndentation();
            String type = backend().typeseval().type(backend().types().declaredPortType(output.getPort()));
            emitter().emit("%s token = %s.read().to_ulong();", type, output.getReader().getDout().getSignal().getName());

            emitter().emit("if (%s.full_n() == true && %s.read() == SC_LOGIC_1) {",
                    simBufferName, output.getReader().getEmptyN().getSignal().getName());
            {
                emitter().increaseIndentation();
                emitter().emit("%s.write(SC_LOGIC_1);", output.getReader().getRead().getSignal().getName());
                emitter().emit("%s.enqueue(token);", simBufferName);
                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("%s.write(SC_LOGIC_0);", output.getReader().getRead().getSignal().getName());
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }


    default void getReseter(SCNetwork network) {
        emitter().emit("// -- reset method");
        emitter().emit("void reset() {");
        {
            emitter().increaseIndentation();
            emitter().emit("%s.write(SC_LOGIC_0);", network.getApControl().getResetSignal().getName());
            emitter().emit("sc_start(clock_period + clock_period);");
            emitter().emit("%s.write(SC_LOGIC_1);", network.getApControl().getResetSignal().getName());
            emitter().emit("sc_start(clock_period);");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }
    default void getSimulator(SCNetwork network) {

        emitter().emit("// -- simulator method");
        emitter().emit("void simulate() {");
        {
            emitter().increaseIndentation();
            emitter().emit("bool started = false;");
            emitter().emit("uint64_t clock_counter = 0;");
            emitter().emit("std::cout << \"@\" << sc_time_stamp() << \"Starting sim\" << std::endl;");
            emitter().emit("while (%s.read() != SC_LOGIC_1) {", network.getApControl().getDoneSignal().getName());
            {

                emitter().increaseIndentation();
                emitter().emit("sc_start(clock_period);");
                emitter().emit("clock_counter ++;");
                emitter().emit("if (started) {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("%s.write(SC_LOGIC_0);", network.getApControl().getStartSignal().getName());
                    emitter().decreaseIndentation();
                }
                emitter().emit("} else {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("%s.write(SC_LOGIC_1);", network.getApControl().getStartSignal().getName());
                    emitter().emit("started = true;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emit("std::cout << \"clock count: \" << clock_counter << std::endl;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();

    }

    default void getConstructor(SCNetwork network, String identifier) {
        emitter().emit("// -- constructor");

        emitter().emit("%s(sc_module_name name, const sc_time clock_period): ", identifier);

        {
            emitter().increaseIndentation();
            {
                emitter().increaseIndentation();
                emitter().emit("sc_module(name),");
                emitter().emit("inst_%s(\"inst_%1$s\"),", network.getIdentifier());
                emitter().emit("// -- software input queues");
                network.getInputs().forEach(input -> {
                    emitter().emit("// simulation buffer for %s", input.getPort().getName());
                    emitter().emit("sim_buffer_%s(std::string(\"%1$s\"), std::string(\"\")),",
                            input.getPort().getName());
                });
                network.getOutputs().forEach(output -> {
                    emitter().emit("// simulation buffer for %s", output.getPort().getName());
                    emitter().emit("sim_buffer_%s(std::string(\"%1$s\"), std::string(\"\")),",
                            output.getPort().getName());
                });
                emitter().emit("clock_period(clock_period),");
                emitter().emit("%s(\"%1$s\", clock_period) {", network.getApControl().getClockSignal().getName());

                emitter().decreaseIndentation();
            }
            // -- bind the ports to signals
            emitter().emit("// -- bind the ports to signals");
            network.stream().forEach(portIF -> {
                emitter().emit("inst_%s.%s(%2$s);", network.getIdentifier(), portIF.getSignal().getName());
            });
            emitter().emitNewLine();
            // -- register methods
            emitter().emit("// -- registers queue emulator methods");
            emitter().emit("// -- input feeders");
            network.getInputs().stream().map(SCNetwork.InputIF::getPort).map(PortDecl::getName).forEach(port -> {
                emitter().emit("SC_METHOD(input_feeder_%s);", port);
                emitter().emit("sensitive_pos << %s;", network.getApControl().getClockSignal().getName());
                emitter().emitNewLine();
            });
            emitter().emitNewLine();
            emitter().emit("// -- output eaters");
            network.getOutputs().stream().map(SCNetwork.OutputIF::getPort).map(PortDecl::getName).forEach(port -> {
                emitter().emit("SC_METHOD(output_eater_%s);", port);
                emitter().emit("sensitive_pos << %s;", network.getApControl().getClockSignal().getName());
                emitter().emitNewLine();
            });
            emitter().emitNewLine();


            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

}
