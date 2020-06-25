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

            // -- stats dumper
            getDumpStats(network);
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

        emitter().emit("#include <iostream>");
        emitter().emit("#include <fstream>");
        emitter().emit("#include <string>");
        emitter().emit("#include <memory>");
        emitter().emit("#include <chrono>");
        emitter().emit("#include \"debug_macros.h\"");
        emitter().emit("#include \"%s.h\"", network.getIdentifier());
        emitter().emit("#include \"sim_queue.h\"");
        emitter().emitNewLine();
        emitter().emitNewLine();
    }


    default void getModuleSignals(SCNetwork network) {

        emitter().emit("sc_clock %s;", network.getApControl().getClockSignal().getName());
        network.getApControl().stream().forEach(port -> {
            if (!port.equals(network.getApControl().getClock()))
                emitter().emit("sc_signal<%s> %s;", port.getSignal().getType(), port.getSignal().getName());
        });
        emitter().emit("// -- network inputs");
//        network.getInputs().stream()
//                .flatMap(SCNetwork.InputIF::stream)
//                .forEach(port -> {
//                    if (!port.equals(network.getApControl().getClock()))
//                        emitter().emit("sc_signal <%s> %s;",
//                                port.getSignal().getType(), port.getSignal().getName());
//                });
        emitter().emit("// -- network outputs");
//        network.getOutputs().stream()
//                .flatMap(SCNetwork.OutputIF::stream)
//                .forEach(port -> {
//                    emitter().emit("sc_signal <%s> %s;",
//                            port.getSignal().getType(), port.getSignal().getName());
//                });


        emitter().emitNewLine();
    }

    default void getSimFields(SCNetwork network) {

        // -- clock period
        emitter().emit("// -- simulation related stuff");
        emitter().emit("const sc_time clock_period;");
        emitter().emit("// -- software feeder queues");
//        network.getInputs().map(SCNetwork.InputIF::getPort).forEach(input -> {
//            String type = backend().typeseval().type(backend().types().declaredPortType(input));
//            emitter().emit("std::unique_ptr<SimQueue::InputQueue<%s>> sim_buffer_%s;", type, input.getName());
//            emitter().emitNewLine();
//            emitter().emit("// -- helper signals");
//            emitter().emit("sc_signal<bool> sim_buffer_%s_read;", input.getName());
//            emitter().emit("sc_signal<%s>   sim_buffer_%s_dout;", type, input.getName());
//            emitter().emitNewLine();
//        });
        emitter().emit("// -- software eater queues");
//        network.getOutputs().map(SCNetwork.OutputIF::getPort).forEach(output -> {
//            String type = backend().typeseval().type(backend().types().declaredPortType(output));
//            emitter().emit("std::unique_ptr<SimQueue::OutputQueue<%s>> sim_buffer_%s;", type, output.getName());
//            emitter().emitNewLine();
//            emitter().emit("// -- helper signals");
//            emitter().emit("sc_signal<bool> sim_buffer_%s_write;", output.getName());
//            emitter().emit("sc_signal<%s>   sim_buffer_%s_din;", type, output.getName());
//            emitter().emitNewLine();
//        });
        // -- vcd trace file
        emitter().emit("sc_trace_file *vcd_dump;");

        emitter().emitNewLine();
    }

    default void getQueueEmulators(SCNetwork network) {
//
//        network.getInputs().forEach(this::getInputFeeder);
//        network.getInputs().forEach((this::getInputReadSetter));
//        network.getOutputs().forEach(this::getOutputEater);
//        network.getOutputs().forEach(this::getOutputWriteSetter);

    }

    default void getInputFeeder(SCNetwork.InputIF input) {

        String simBufferName = "sim_buffer_" + input.getPort().getName();
        emitter().emit("// -- input queue emulator for %s", input.getPort().getName());
        emitter().emit("void input_feeder_%s () {", input.getPort().getName());
        {
            emitter().increaseIndentation();
            emitter().emit("while(true) {");
            {
                emitter().increaseIndentation();
                emitter().emit("wait();");
                emitter().emit("if (%s_read.read() == %s) {", simBufferName, LogicValue.Value.SC_LOGIC_1);
                {
                    emitter().increaseIndentation();
                    String type = backend().typeseval().type(backend().types().declaredPortType(input.getPort()));
                    emitter().emit("%s token = %s->peek();", type, simBufferName);
                    emitter().emit("%s_dout.write(token);", simBufferName);
                    emitter().emit("%s->dequeue();", simBufferName);
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void getInputReadSetter(SCNetwork.InputIF input) {
        String simBufferName = "sim_buffer_" + input.getPort().getName();
        emitter().emit("// -- input queue emulator read setter for %s", input.getPort().getName());
        emitter().emit("void input_read_setter_%s () {", input.getPort().getName());
        {
            emitter().increaseIndentation();
            emitter().emit("%s.write(%s_dout);", input.getWriter().getDin().getSignal().getName(), simBufferName);
            emitter().emitNewLine();
            emitter().emit("if (%s->empty_n() == true && %s.read() == %s) {", simBufferName,
                    input.getWriter().getFullN().getSignal().getName(), LogicValue.Value.SC_LOGIC_1);
            {
                emitter().increaseIndentation();
                emitter().emit("%s_read.write(%s);", simBufferName, LogicValue.Value.SC_LOGIC_1);
                emitter().emit("%s.write(%s);", input.getWriter().getWrite().getSignal().getName(),
                        LogicValue.Value.SC_LOGIC_1);
                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("%s_read.write(%s);", simBufferName, LogicValue.Value.SC_LOGIC_0);
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

            emitter().emit("while(true) {");
            {
                emitter().increaseIndentation();
                emitter().emit("wait();");
                emitter().emit("if (%s_write.read() == %s) {", simBufferName, LogicValue.Value.SC_LOGIC_1);
                {
                    emitter().increaseIndentation();
                    String type = backend().typeseval().type(backend().types().declaredPortType(output.getPort()));
                    emitter().emit("%s token = %s_din.read();", type, simBufferName);
                    emitter().emit("%s->enqueue(token);", simBufferName);
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    default void getOutputWriteSetter(SCNetwork.OutputIF output) {

        String simBufferName = "sim_buffer_" + output.getPort().getName();

        emitter().emit("// -- output queue emulator write setter for %s", output.getPort().getName());
        emitter().emit("void output_write_setter_%s() {", output.getPort().getName());
        {
            emitter().increaseIndentation();
            emitter().emit("%s_din.write(%s.read());", simBufferName,
                    output.getReader().getDout().getSignal().getName());
            emitter().emit("if (%s->full_n() == true && %s.read() == %s) {",
                    simBufferName, output.getReader().getEmptyN().getSignal().getName(), LogicValue.Value.SC_LOGIC_1);
            {
                emitter().increaseIndentation();
                emitter().emit("%s_write.write(%s);", simBufferName, LogicValue.Value.SC_LOGIC_1);
                emitter().emit("%s.write(%s);", output.getReader().getRead().getSignal().getName(),
                        LogicValue.Value.SC_LOGIC_1);
                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("%s_write.write(%s);", simBufferName, LogicValue.Value.SC_LOGIC_0);
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
            emitter().emit("uint64_t break_point = 1000000;");
            emitter().emit("auto start_time = std::chrono::high_resolution_clock::now();");
            emitter().emit("STATUS_REPORT(\"@ %%s starting simulation\\n\",\n" +
                    "                  sc_time_stamp().to_string().c_str());");

            emitter().emit("while (%s.read() != %s) {", network.getApControl().getDoneSignal().getName(),
                    LogicValue.Value.SC_LOGIC_1);
            {

                emitter().increaseIndentation();
                emitter().emit("if (clock_counter == break_point) {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("auto current_time = std::chrono::high_resolution_clock::now();");
                    emitter().emit(" auto diff_time = " +
                            "std::chrono::duration_cast<std::chrono::milliseconds>(current_time - start_time);");
                    emitter().emit("auto sim_time = sc_time_stamp();");
                    emitter().emit("auto slow_down = diff_time.count() / sim_time.to_seconds() / 1e3;");
                    emitter().emit("STATUS_REPORT(\n" +
                            "            \"\\nSimulated for %%5lu million cycles \\n\\tsystemc time: \"\n" +
                            "            \"%%.9f s (%%s) \\n\\treal time   : %%3.6f s\\n\\tslow down   : %%6.3f\\n\\n\\n\",\n" +
                            "            clock_counter / 1000000, sim_time.to_seconds(), sim_time.to_string().c_str(),\n" +
                            "            diff_time.count() / 1e3, slow_down);");
                    emitter().emit("this->dump_stats(break_point);");
                    emitter().emit("break_point += 1000000;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
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
            emitter().emit("sc_start(clock_period);");
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
//            network.getInputs().forEach(input -> {
//                emitter().emit("// -- simulation buffer for %s", input.getPort().getName());
//                String type = backend().typeseval().type(backend().types().declaredPortType(input.getPort()));
//                emitter().emit("sim_buffer_%s = " +
//                                "std::make_unique<SimQueue::InputQueue<%s>> (std::string(\"%1$s\"));",
//                        input.getPort().getName(), type);
//
//            });
            emitter().emitNewLine();
            // -- outputs
//            network.getOutputs().forEach(output -> {
//                emitter().emit("// -- simulation buffer for %s", output.getPort().getName());
//                String type = backend().typeseval().type(backend().types().declaredPortType(output.getPort()));
//                emitter().emit("sim_buffer_%s = " +
//                                "std::make_unique<SimQueue::OutputQueue<%s>> (std::string(\"%1$s\"));",
//                        output.getPort().getName(), type);
//            });

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
//            network.getInputs().stream().map(SCNetwork.InputIF::getPort).map(PortDecl::getName).forEach(port -> {
//                emitter().emit("SC_THREAD(input_feeder_%s);", port);
//                emitter().emit("sensitive << %s.posedge_event();", network.getApControl().getClockSignal().getName());
//                emitter().emitNewLine();
//            });
            emitter().emitNewLine();
            emitter().emit("// -- input read setters");
//            network.getInputs().forEach(port -> {
//                emitter().emit("SC_METHOD(input_read_setter_%s);", port.getPort().getName());
//                emitter().emit("sensitive << %s << sim_buffer_%s_dout;",
//                        port.getWriter().getFullN().getSignal().getName(),
//                        port.getPort().getName());
//            });
            emitter().emitNewLine();
            emitter().emit("// -- output eaters");
//            network.getOutputs().stream().map(SCNetwork.OutputIF::getPort).map(PortDecl::getName).forEach(port -> {
//                emitter().emit("SC_THREAD(output_eater_%s);", port);
//                emitter().emit("sensitive << %s.posedge_event();", network.getApControl().getClockSignal().getName());
//                emitter().emitNewLine();
//            });
            emitter().emitNewLine();
            emitter().emit("// -- output write setters");
//            network.getOutputs().stream().forEach(port -> {
//                emitter().emit("SC_METHOD(output_write_setter_%s);", port.getPort().getName());
//                emitter().emit("sensitive << %s << %s << %s;",
//                        port.getReader().getEmptyN().getSignal().getName(),
//                        port.getReader().getDout().getSignal().getName(),
//                        port.getAuxiliary().getPeek().getSignal().getName());
//            });
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
                network.getInstanceTriggers().stream().forEach(trigger -> {
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

    default void getDumpStats(SCNetwork network) {

        emitter().emit("void dump_stats(uint64_t break_point=0) {");
        {
            emitter().emit("std::string extension;");
            emitter().increaseIndentation();
            emitter().emit("std::stringstream convert;");
            emitter().emit("if (break_point != 0) {");
            {
                emitter().increaseIndentation();
                emitter().emit("convert << \".\" <<break_point << \".xml\";");
                emitter().emit("convert >> extension;");
                emitter().decreaseIndentation();
            }
            emitter().emit("} else {");
            {
                emitter().increaseIndentation();
                emitter().emit("extension = \".xml\";");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emit("std::string file_name = std::string(this->name()) + extension;");

            emitter().emit("std::ofstream stats_dump (file_name, std::ios::out);");

//            emitter().emit("stats_dump << \"<?xml version = \\\"1.0\\\" encoding = \\\"UTF-8\\\"?>\" << std::endl;");
            emitter().emit("stats_dump << \"<network name=\\\"%s\\\" />\" << std::endl;", network.getIdentifier());

            for (SCTrigger trigger: network.getInstanceTriggers())
                dumpInstanceStats(trigger, network);

            emitter().emit("stats_dump << \"</network>\" << std::endl;");

            emitter().emit("stats_dump.close();");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }
    default void dumpInstanceStats(SCTrigger trigger, SCNetwork network) {

        emitter().emit("this->inst_%s->%s->dumpStats(stats_dump);", network.getIdentifier(), trigger.getName());
    }





}
