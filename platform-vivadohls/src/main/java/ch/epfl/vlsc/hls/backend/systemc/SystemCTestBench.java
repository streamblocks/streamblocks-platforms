package ch.epfl.vlsc.hls.backend.systemc;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.Binding;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;


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

        String identifier = "NetworkTester";
        String fileId = "network_tester";
        emitter().open(PathUtils.getTargetCodeGenInclude(backend().context()).resolve(fileId + ".h"));
        emitter().emit("#ifndef __%s_H__", fileId.toUpperCase());
        emitter().emit("#define __%s_H__", fileId.toUpperCase());
        emitter().emitNewLine();

        emitter().emitNewLine();
        getIncludes(network);

        emitter().emitNewLine();
        emitter().emit("namespace ap_rtl {");


        emitter().emit("class %s: public sc_module {", identifier);
        {
            emitter().emit("public:");
            emitter().emitNewLine();
            emitter().increaseIndentation();

            // -- port addresses
            emitter().emitNewLine();
            emitter().emit("// -- simulation port addresses, each network IO will have a unique address");
            getMemoryAddresses(network);
            emitter().emitNewLine();


            // -- get signals of the network
            getModuleSignals(network);

            // -- get simulation related fields
            getSimFields(network);

            // -- get network instnace
            emitter().emit("// -- systemc network under test");
            emitter().emit("std::unique_ptr<%s> inst_%1$s;", network.getIdentifier());

            emitter().emitNewLine();
            // -- memory allocate
            getMemoryAllocate(network);

            // -- memory write
            getMemoryWrite(network);

            // -- memory read
            getMemoryRead(network);

            // -- size query
            getQuerySize(network);

            // -- set arg
            getMemoryArgs(network);

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
        emitter().emit("#endif // __%s_H__", fileId.toUpperCase());
        emitter().close();
    }

    default void getMemoryAddresses(SCNetwork network) {

        emitter().emit("enum class PortAddress {");
        {
            emitter().increaseIndentation();
            emitter().emit("// -- input stage port addresses");
            network.getInputStages().stream().map(SCInputStage::getPort).map(PortDecl::getName)
                    .forEach(p -> emitter().emit("%s, ", p));
            emitter().emit("// -- output stage port addresses");
            network.getOutputStages().stream().map(SCOutputStage::getPort).map(PortDecl::getName)
                    .forEach(p -> emitter().emit("%s, ", p));
            emitter().emit("// -- invalid port address");
            emitter().emit("INVALID_PORT");
            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();
    }


    default void getIncludes(SCNetwork network) {

        emitter().emit("#include <iostream>");
        emitter().emit("#include <fstream>");
        emitter().emit("#include <string>");
        emitter().emit("#include <memory>");
        emitter().emit("#include <chrono>");
        emitter().emit("#include \"debug_macros.h\"");
        emitter().emit("#include \"%s.h\"", network.getIdentifier());
        emitter().emitNewLine();
        emitter().emitNewLine();
    }


    default void getModuleSignals(SCNetwork network) {

        emitter().emit("sc_clock %s;", network.getApControl().getClockSignal().getName());
        emitter().emit("sc_signal<%s> %s;", network.getInit().getSignal().getType(),
                network.getInit().getName());
        network.getApControl().stream().forEach(port -> {
            if (!port.equals(network.getApControl().getClock()))
                emitter().emit("sc_signal<%s> %s;", port.getSignal().getType(), port.getSignal().getName());
        });

        emitter().emitNewLine();
    }

    default void getSimFields(SCNetwork network) {

        // -- clock period
        emitter().emit("// -- simulation related stuff");
        emitter().emit("const sc_time clock_period;");
        emitter().emit("std::size_t total_ticks;");
        // -- vcd trace file
        emitter().emit("sc_trace_file *vcd_dump;");

        emitter().emitNewLine();
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
        emitter().emit("std::size_t simulate(std::size_t report_every = 1000000) {");
        {
            emitter().increaseIndentation();
            emitter().emit("bool started = false;");
            emitter().emit("std::size_t start_ticks = total_ticks;");
            emitter().emit("std::size_t break_point = start_ticks == 0 ? report_every : " +
                    "( (start_ticks - 1) / report_every + 1 ) * report_every;");
            emitter().emit("auto start_time = std::chrono::high_resolution_clock::now();");
            emitter().emit("STATUS_REPORT(\"@ %%s starting simulation\\n\",\n" +
                    "                  sc_time_stamp().to_string().c_str());");
            emitter().emit("auto sim_start_time = sc_time_stamp();");
            emitter().emit("while (%s.read() != %s) {", network.getApControl().getDoneSignal().getName(),
                    LogicValue.Value.SC_LOGIC_1);
            {

                emitter().increaseIndentation();
                emitter().emit("if (total_ticks == break_point) {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("auto current_time = std::chrono::high_resolution_clock::now();");
                    emitter().emit(" auto diff_time = " +
                            "std::chrono::duration_cast<std::chrono::milliseconds>(current_time - start_time);");
                    emitter().emit("auto sim_curr_time = sc_time_stamp();");
                    emitter().emit("auto sim_time_diff = sim_curr_time - sim_start_time;");
                    emitter().emit("auto slow_down = diff_time.count() / sim_time_diff.to_seconds() / 1e3;");

                    emitter().emit("STATUS_REPORT(\n" +
                            "            \"\\nSimulated for %%10lu cycles \\n\\tsystemc time: \"\n" +
                            "            \"%%.9f s (%%s) \\n\\treal time   : %%3.6f s\\n\\tslow down   : %%6.3f\\n\\n\",\n" +
                            "            total_ticks , sim_curr_time.to_seconds(), sim_curr_time.to_string().c_str(),\n" +
                            "            diff_time.count() / 1e3, slow_down);");
                    emitter().emit("break_point += report_every;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().emit("sc_start(clock_period);");
                emitter().emit("total_ticks ++;");
                emitter().emit("if (started) {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("%s.write(%s);", network.getApControl().getStartSignal().getName(),
                            LogicValue.Value.SC_LOGIC_0);
                    emitter().emit("%s.write(%s);", network.getInit().getSignal().getName(),
                            LogicValue.Value.SC_LOGIC_0);
                    emitter().decreaseIndentation();
                }
                emitter().emit("} else {");
                {
                    emitter().increaseIndentation();
                    emitter().emit("%s.write(%s);", network.getApControl().getStartSignal().getName(),
                            LogicValue.Value.SC_LOGIC_1);
                    emitter().emit("%s.write(%s);", network.getInit().getSignal().getName(),
                            LogicValue.Value.SC_LOGIC_1);
                    emitter().emit("started = true;");
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emit("sc_start(clock_period);");
            emitter().emitNewLine();

            emitter().emit("return total_ticks - start_ticks;");
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

            emitter().emitNewLine();
            // -- bind the ports to signals
            emitter().emit("// -- bind the ports to signals");
            network.stream().forEach(portIF -> {
                emitter().emit("inst_%s->%s.bind(%2$s);", network.getIdentifier(), portIF.getSignal().getName());
            });
            emitter().emitNewLine();
            // -- total simulated ticks
            emitter().emit("total_ticks = 0;");
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
                network.getInputStages().stream().forEach(input -> traceIO(input, network));
                network.getInputStages().stream().forEach(input -> traceIOInternals(input, network));
                network.getOutputStages().stream().forEach(output -> traceIO(output, network));
                network.getOutputStages().stream().forEach(output -> traceIOInternals(output, network));
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
                network.getAllTriggers().stream().forEach(trigger -> {
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

    default void traceIO(SCInstanceIF iostage, SCNetwork network) {
        iostage.stream().forEach(port -> {
            emitter().emit("sc_trace(vcd_dump, inst_%s->%s, \"%1$s.%2$s\");",
                    network.getIdentifier(), port.getSignal().getName());
        });
    }

    default void traceIOInternals(SCInputOutputIF inputOutput, SCNetwork network) {

        traceIODetails(
            Stream.of(
                    "tokens_processed",
                    "tokens_to_process",
                    "state",
                    "next_state"),
                inputOutput.getInstanceName(), network.getIdentifier());

    }


    default void traceIODetails(Stream<String> details, String stageName, String networkName){

        details.forEach(sig ->
                emitter().emit("sc_trace(vcd_dump, inst_%s->%s->%s, \"%1$s.%2$s.%3$s\");",
                        networkName, stageName, sig)
        );
    }
    default void getDumpStats(SCNetwork network) {

        emitter().emit("void dumpStats(std::ofstream& stats_dump) {");
        {

            emitter().emit("stats_dump << \"<network name=\\\"%s\\\" />\" << std::endl;", network.getIdentifier());

            for (SCTrigger trigger: network.getInstanceTriggers())
                dumpInstanceStats(trigger, network);

            emitter().emit("stats_dump << \"</network>\" << std::endl;");

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }
    default void dumpInstanceStats(SCTrigger trigger, SCNetwork network) {

        emitter().emit("this->inst_%s->%s->dumpStats(stats_dump);", network.getIdentifier(), trigger.getName());
    }


    default void getMemoryAllocate(SCNetwork network) {

        String funcName = "allocateMemory";
        ImmutableList<String> args = ImmutableList.of("std::size_t size");

        Map<PortDecl, ImmutableList<String>> stmts = new HashMap<>();
        Stream.concat(network.getInputStages().stream(), network.getOutputStages().stream()).forEach(port -> {
            String alloctStmt = String.format("inst_%s->%s->allocateDeviceMemory(size)", network.getIdentifier(),
                    port.getInstanceName());
            stmts.put(port.getPort(), ImmutableList.of(alloctStmt));
        });

        getMemoryOperation(funcName, args, stmts, ImmutableList.empty(), "", "void", ImmutableList.empty());
    }

    default void getMemoryArgs(SCNetwork network) {
        String funName = "setArg";
        ImmutableList<String> args = ImmutableList.of("std::size_t value");
        Map<PortDecl, ImmutableList<String>> stmts = new HashMap<>();
        Stream.concat(network.getInputStages().stream(), network.getOutputStages().stream()).forEach(port -> {
            String setArgStmt = String.format("inst_%s->%s->setArg(value);", network.getIdentifier(),
                    port.getInstanceName());
            stmts.put(port.getPort(), ImmutableList.of(setArgStmt));
        });

        getMemoryOperation(funName, args, stmts, ImmutableList.empty(), "value", "std::size_t",
                ImmutableList.empty());
    }
    default void getMemoryWrite(SCNetwork network) {

        String funName = "writeDeviceMemory";
        ImmutableList<String> args = ImmutableList.of("std::vector<T> &host_buffer", "std::size_t n=0");
        Map<PortDecl, ImmutableList<String>> stmts = new HashMap<>();

        network.getInputStages().forEach(port -> {
           String writeStmt = String.format("inst_%s->%s->writeDeviceMemory(host_buffer, n)", network.getIdentifier(),
                   port.getInstanceName());
           stmts.put(port.getPort(), ImmutableList.of(writeStmt));
        });

        network.getOutputStages().forEach(port -> {
            String panicStmt = String.format("PANIC(\"Port address %s is read-only!\")", port.getPort().getName());
            stmts.put(port.getPort(), ImmutableList.of(panicStmt));
        });

        getMemoryOperation(funName, args, stmts, ImmutableList.empty(), "", "void",
                ImmutableList.of("typename T"));

    }

    default void getMemoryRead(SCNetwork network) {

        String funName = "readDeviceMemory";
        ImmutableList<String> args = ImmutableList.of("std::vector<T> &host_buffer", "std::size_t n=0");
        Map<PortDecl, ImmutableList<String>> stmts = new HashMap<>();

        network.getInputStages().forEach(port -> {
            String panicStmt = String.format("PANIC(\"Port address %s is write-only\")", port.getPort().getName());
            stmts.put(port.getPort(), ImmutableList.of(panicStmt));
        });

        network.getOutputStages().forEach(port -> {
            String readStmt = String.format("inst_%s->%s->readDeviceMemory(host_buffer, n);", network.getIdentifier(),
                    port.getInstanceName());
            stmts.put(port.getPort(), ImmutableList.of(readStmt));
        });

        getMemoryOperation(funName, args, stmts, ImmutableList.empty(), "", "void",
                ImmutableList.of("typename T"));

    }

    default void getQuerySize(SCNetwork network) {

        String funName = "querySize";
        ImmutableList<String> args = ImmutableList.empty();
        ImmutableList<String> defines = ImmutableList.of("std::size_t size = 0");
        Map<PortDecl, ImmutableList<String>> stmts = new HashMap<>();

        Stream.concat(network.getInputStages().stream(), network.getOutputStages().stream()).forEach(port -> {
            String queryStmt = String.format("size = inst_%s->%s->querySize()", network.getIdentifier(),
                    port.getInstanceName());
            stmts.put(port.getPort(), ImmutableList.of(queryStmt));
        });

        getMemoryOperation(funName, args, stmts, defines, "size", "std::size_t", ImmutableList.empty());
    }


    default void getMemoryOperation(String op, ImmutableList<String> args, Map<PortDecl, ImmutableList<String>> statements,
                                    ImmutableList<String> defines, String ret, String retType, ImmutableList<String> templates) {
        String commaArgs = "PortAddress address";

        if (args.size() != 0) {

            commaArgs = commaArgs + ", " + String.join(", ", args);

        }
        if (templates.size() > 0)
            emitter().emit("template <%s>", String.join(", ", templates));

        emitter().emit("%s %s(%s) {", retType, op, commaArgs);
        {
            emitter().increaseIndentation();
            defines.forEach(def ->
                    emitter().emit("%s;", def)
            );
            emitter().emitNewLine();
            emitter().emit("switch(address) {");
            {
                statements.forEach(this::getMemoryOperationCase);

                emitter().emit("case PortAddress::INVALID_PORT:");
                emitter().emit("default:");
                {
                    emitter().increaseIndentation();
                    emitter().emit("PANIC(\"Invalid port address %%lu\", address)");
                    emitter().emit("break;");
                    emitter().decreaseIndentation();
                }
            }
            emitter().emit("}");
            if (!ret.isEmpty())
                emitter().emit("return %s;", ret);
            else
                emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }
    default void getMemoryOperationCase(PortDecl port, ImmutableList<String> stmts) {
        emitter().emit("case PortAddress::%s: {", port.getName());
        {
            emitter().increaseIndentation();
            stmts.forEach(stmt -> {
                emitter().emit("%s;", stmt);
            });
            emitter().emit("break;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

}
