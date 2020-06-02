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

        String identifier = "network_tester";

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
            emitter().emit("std::unique_ptr<%s> inst_%1$s;", network.getIdentifier());

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
        emitter().emit("#include <memory>");
        emitter().emitNewLine();

        emitter().emit("#define TRACE_SIG(FILE, SIG) sc_core::sc_trace(FILE, SIG, std::string(this->name()) + \".\" #SIG)");
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
            emitter().emit("std::unique_ptr<SimQueue::InputQueue<%s>> sim_buffer_%s;", type, input.getName());
        });
        emitter().emit("// -- software eater queues");
        network.getOutputs().map(SCNetwork.OutputIF::getPort).forEach(output -> {
            String type = backend().typeseval().type(backend().types().declaredPortType(output));
            emitter().emit("std::unique_ptr<SimQueue::OutputQueue<%s>> sim_buffer_%s;", type, output.getName());
        });
        // -- vcd trace file
        emitter().emit("sc_trace_file *vcd_dump;");
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
            emitter().emit("%s token = %s->peek();", type, simBufferName);
            emitter().emit("%s.write(token);", input.getWriter().getDin().getSignal().getName());

            emitter().emitNewLine();

            // -- write to the systemc input queue
            emitter().emit("// -- write to the systemc input queue from the sim buffer");
            emitter().emit("if (%s->empty_n() == true && %s.read() == %s) {",
                    simBufferName, input.getWriter().getFullN().getSignal().getName(), LogicValue.Value.SC_LOGIC_1);
            {
                emitter().increaseIndentation();
                emitter().emit("%s.write(%s);", input.getWriter().getWrite().getSignal().getName(),
                        LogicValue.Value.SC_LOGIC_1);
                // -- consume from the sim buffer;
                emitter().emit("%s->dequeue();", simBufferName);
                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("%s.write(%s);", input.getWriter().getWrite().getSignal().getName(),
                        LogicValue.Value.SC_LOGIC_0);
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


            emitter().emit("if (%s->full_n() == true && %s.read() == %s) {",
                    simBufferName, output.getReader().getEmptyN().getSignal().getName(), LogicValue.Value.SC_LOGIC_1);
            {
                emitter().increaseIndentation();
                emitter().emit("%s.write(%s);", output.getReader().getRead().getSignal().getName(),
                        LogicValue.Value.SC_LOGIC_1);
                emitter().emit("%s token = %s.read();", type, output.getReader().getDout().getSignal().getName());
                emitter().emit("%s->enqueue(token);", simBufferName);
                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("%s.write(%s);", output.getReader().getRead().getSignal().getName(),
                        LogicValue.Value.SC_LOGIC_0);
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
            emitter().emit("%s.write(%s);", network.getApControl().getResetSignal().getName(),
                    LogicValue.Value.SC_LOGIC_0);
            emitter().emit("sc_start(clock_period + clock_period);");
            emitter().emit("%s.write(%s);", network.getApControl().getResetSignal().getName(),
                    LogicValue.Value.SC_LOGIC_1);
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
            emitter().emit("std::cout << \"@\" << sc_time_stamp() << \" Starting sim\" << std::endl;");
            emitter().emit("while (%s.read() != %s) {", network.getApControl().getDoneSignal().getName(),
                    LogicValue.Value.SC_LOGIC_1);
            {

                emitter().increaseIndentation();
                emitter().emit("sc_start(clock_period);");
                emitter().emit("clock_counter ++;");
                emitter().emit("if (started) {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("%s.write(%s);", network.getApControl().getStartSignal().getName(),
                            LogicValue.Value.SC_LOGIC_0);
                    emitter().decreaseIndentation();
                }
                emitter().emit("} else {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("%s.write(%s);", network.getApControl().getStartSignal().getName(),
                            LogicValue.Value.SC_LOGIC_1);
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

        emitter().emit("%s(sc_module_name name, const sc_time clock_period, unsigned int trace_level = 0): ", identifier);

        {
            emitter().increaseIndentation();
            {
                emitter().increaseIndentation();
                emitter().emit("sc_module(name),");
                emitter().emit("clock_period(clock_period),");
                emitter().emit("%s(\"%1$s\", clock_period), ", network.getApControl().getClockSignal().getName());
                emitter().emit("%s(\"%1$s\", %s) {", network.getApControl().getStartSignal().getName(),
                        LogicValue.Value.SC_LOGIC_0);

                emitter().decreaseIndentation();
            }


            // -- construct the network
            emitter().emit("// -- network");
            emitter().emit("inst_%s = std::make_unique<%1$s>(\"inst_%1$s\");", network.getIdentifier());
            // -- construct the SimQueues
            // -- inputs
            network.getInputs().forEach(input -> {
                emitter().emit("// -- simulation buffer for %s", input.getPort().getName());
                String type = backend().typeseval().type(backend().types().declaredPortType(input.getPort()));
                emitter().emit("sim_buffer_%s = " +
                        "std::make_unique<SimQueue::InputQueue<%s>> (std::string(\"%1$s\"), std::string(\"\"));",
                        input.getPort().getName(), type);

            });
            emitter().emitNewLine();
            // -- outputs
            network.getOutputs().forEach(output -> {
                emitter().emit("// -- simulation buffer for %s", output.getPort().getName());
                String type = backend().typeseval().type(backend().types().declaredPortType(output.getPort()));
                emitter().emit("sim_buffer_%s = " +
                                "std::make_unique<SimQueue::OutputQueue<%s>> (std::string(\"%1$s\"), std::string(\"\"));",
                        output.getPort().getName(), type);
            });

            emitter().emitNewLine();
            // -- bind the ports to signals
            emitter().emit("// -- bind the ports to signals");
            network.stream().forEach(portIF -> {
                emitter().emit("inst_%s->%s.bind(%2$s);", network.getIdentifier(), portIF.getSignal().getName());
            });
            emitter().emitNewLine();
            // -- register methods
            emitter().emit("// -- registers queue emulator methods");
            emitter().emit("// -- input feeders");
            network.getInputs().stream().map(SCNetwork.InputIF::getPort).map(PortDecl::getName).forEach(port -> {
                emitter().emit("SC_METHOD(input_feeder_%s);", port);
                emitter().emit("sensitive << %s.posedge_event();", network.getApControl().getClockSignal().getName());
                emitter().emitNewLine();
            });
            emitter().emitNewLine();
            emitter().emit("// -- output eaters");
            network.getOutputs().stream().map(SCNetwork.OutputIF::getPort).map(PortDecl::getName).forEach(port -> {
                emitter().emit("SC_METHOD(output_eater_%s);", port);
                emitter().emit("sensitive << %s.posedge_event();", network.getApControl().getClockSignal().getName());
                emitter().emitNewLine();
            });
            emitter().emitNewLine();

            // -- register traces
            emitter().emit("if (trace_level > 0) {");
            {
                emitter().increaseIndentation();
                emitter().emit("vcd_dump = sc_create_vcd_trace_file(\"%s\");", identifier);
                emitter().emit("vcd_dump->set_time_unit(1, SC_PS);");

                network.stream().forEach(port -> {
                    emitter().emit("sc_trace(vcd_dump, %s, \"%1$s\");", port.getSignal().getName());
                });
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();
            emitter().emit("if (trace_level > 1) {");
            {
                emitter().increaseIndentation();

                network.getQueues().stream().forEach(queue -> {
                    String queueName = queue.getName();
                    queue.stream().forEach(port -> {
                        emitter().emit("sc_trace(vcd_dump, inst_%s->%s->%s, \"%1$s.%2$s.%3$s\");",
                                network.getIdentifier(),
                                queueName,
                                port.getName());
                    });
                });
                network.getInternalSignals().forEach(signal -> {
                    emitter().emit("sc_trace(vcd_dump, inst_%s->%s, \"%1$s.%2$s\");",
                            network.getIdentifier(), signal.getName());
                });

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();
            emitter().emit("if (trace_level > 2) {");
            {
                emitter().increaseIndentation();
                network.getTriggers().stream().forEach(trigger -> {
                    String triggerName = trigger.getName();
                    trigger.stream().forEach(port -> {
                        emitter().emit("sc_trace(vcd_dump, inst_%s->%s->%s, \"%1$s.%2$s.%3$s\");", network.getIdentifier(),
                                triggerName, port.getName());
                    });
                    emitter().emit("sc_trace(vcd_dump, inst_%s->%s->state, \"%1$s.%2$s.state\");",
                            network.getIdentifier(), triggerName);
                    emitter().emit("sc_trace(vcd_dump, inst_%s->%s->next_state, \"%1$s.%2$s.next_state\");",
                            network.getIdentifier(),
                            triggerName);
                });
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");


    }

}
