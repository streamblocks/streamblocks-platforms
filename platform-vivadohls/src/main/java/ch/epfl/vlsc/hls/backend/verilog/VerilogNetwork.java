package ch.epfl.vlsc.hls.backend.verilog;

import ch.epfl.vlsc.attributes.Memories;
import ch.epfl.vlsc.hls.backend.ExternalMemory;
import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.hls.backend.directives.Directives;
import ch.epfl.vlsc.hls.backend.kernel.AxiConstants;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.MathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.*;
import java.util.stream.Collectors;

@Module
public interface VerilogNetwork {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }



    default String getAllSyncSleepSignal() {
        return "all_sync_sleep";
    }



    default String getAllSleepSignal() {
        return "all_sleep";
    }

    default String getAllWaitedSignal() {
        return "all_waited";
    }

    default Boolean useTrigger() {
        if (backend().triggerBox().isEmpty()) {
            return false;
        } else {
            return backend().triggerBox().get();
        }
    }

    default void generateNetwork() {
        backend().triggerBox().set(true);
        networkContent();
        backend().triggerBox().set(false);
        networkContent();
        backend().triggerBox().clear();
    }


    default void networkContent() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Network file
        if (!useTrigger()) {
            identifier = identifier + "_pure";
        }

        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + ".sv"));


        // -- Network module
        if (backend().externalMemory().getExternalMemories(network).isEmpty()) {
            emitter().emit("module %s (", identifier);
        } else {
            emitter().emit("module %s #(", identifier);

            emitter().increaseIndentation();

            // -- external memory parameters
            getExternalMemoryAxiParams(network, "");

            emitter().decreaseIndentation();

            emitter().emit(")");
            emitter().emit("(");
        }
        // -- Ports I/O
        {
            emitter().increaseIndentation();

            getModulePortNames(network);

            emitter().decreaseIndentation();
        }

        emitter().emit(");");
        {
            // -- Components
            emitter().increaseIndentation();
            emitter().emit("timeunit 1ps;");
            emitter().emit("timeprecision 1ps;");
            // -- Parameters
            getParameters(network);

            // -- Wires
            getWires(network);

            // -- Queues
            getQueues(network.getConnections());

            // -- Instances
            getInstances(network.getInstances(), network.getConnections());

            // -- ILA for debug
            // getILA(network.getInstances());

            // -- Assignments
            if (useTrigger())
                getAssignments(network);

            emitter().decreaseIndentation();
        }
        emitter().emitNewLine();
        emitter().emit("endmodule");
        emitter().close();
    }

    // ------------------------------------------------------------------------
    // -- Module Port IO

    default void getModulePortNames(Network network) {
        // -- External Memory ports
        if (!backend().externalMemory().getExternalMemories(network).isEmpty()) {
            for (Memories.InstanceVarDeclPair pair : backend().externalMemory().getExternalMemories(network)) {
                String name = backend().externalMemory().namePair(pair);
                backend().topkernel().getAxiMasterPorts(name);
            }
        }

        // -- Network input ports
        if (!network.getInputPorts().isEmpty()) {
            for (PortDecl port : network.getInputPorts()) {
                getPortDeclaration(port, true);
            }
        }

        // -- Network Output ports
        if (!network.getOutputPorts().isEmpty()) {
            for (PortDecl port : network.getOutputPorts()) {
                getPortDeclaration(port, false);
            }
        }

        for (Memories.InstanceVarDeclPair pair: backend().externalMemory().getExternalMemories(network)) {
            String memName = backend().externalMemory().namePair(pair);
            emitter().emit("input  wire    [64 - 1 : 0]    %s_offset,", memName);
        }

        if (useTrigger()) {
            // -- Trigger signals
            getTriggerSignals(network);
        }

        // -- System IO
        getSystemSignals();
    }

    default void getPortDeclaration(PortDecl port, boolean isInput) {
        Type type = backend().types().declaredPortType(port);
        long bitSize = backend().typeseval().sizeOfBits(type);
        if (isInput) {
            emitter().emit("input  wire [%d:0] %s_din,", bitSize - 1, port.getName());
            emitter().emit("output wire %s_full_n,", port.getName());
            emitter().emit("input  wire %s_write,", port.getName());

        } else {
            emitter().emit("output wire [%d:0] %s_dout,", bitSize - 1, port.getName());
            emitter().emit("output wire %s_empty_n,", port.getName());
            emitter().emit("input  wire %s_read,", port.getName());

        }
        emitter().emit("output wire [31:0] %s_fifo_count,", port.getName());
        emitter().emit("output wire [31:0] %s_fifo_size,", port.getName());
    }

    default void getSystemSignals() {
        emitter().emit("input  wire ap_clk,");
        emitter().emit("input  wire ap_rst_n,");
        if (useTrigger()) {
            emitter().emit("input  wire ap_start,");
            emitter().emit("output wire ap_idle,");
            emitter().emit("output wire ap_done");
        } else {
            emitter().emit("input  wire ap_start");
        }
        // emitter().emit("input wire input_idle");
    }

    default void getTriggerSignals(Network network) {

        emitter().emit("// -- Trigger signals from IO stages");
        emitter().emit("// -- inputs");
        for (PortDecl port : network.getInputPorts()) {
            emitter().emit("input  wire %s_sleep,", port.getSafeName());
            emitter().emit("input  wire %s_sync_sleep,", port.getSafeName());
            emitter().emit("input  wire %s_waited,", port.getSafeName());
        }
        emitter().emit("// -- outputs");
        for (PortDecl port : network.getOutputPorts()) {
            emitter().emit("input  wire %s_sleep,", port.getSafeName());
            emitter().emit("input  wire %s_sync_sleep,", port.getSafeName());
            emitter().emit("input  wire %s_waited,", port.getSafeName());

        }

        emitter().emit("// -- global signals");

        emitter().emit("output wire %s,", getAllSleepSignal());
        emitter().emit("output wire %s,", getAllSyncSleepSignal());
        emitter().emit("output wire %s,", getAllWaitedSignal());

        // emitter().emit("output wire all_waited,");

    }
    // ------------------------------------------------------------------------
    // -- Parameters

    default void getParameters(Network network) {
        // -- Queue depth parameters
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Parameters");
        emitter().emitNewLine();

        getQueueDepthParameters(network.getConnections());
    }

    default void getQueueDepthParameters(List<Connection> connections) {
        emitter().emit("// -- Queue depth parameters");
        for (Connection connection : connections) {
            String queueName = getQueueName(connection);

            emitter().emit("parameter %s_ADDR_WIDTH = %d;", queueName.toUpperCase(), getQueueAddrWidth(connection));
        }
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Wires

    default void getWires(Network network) {
        // -- Fifo Queue Wires
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Wires & Regs");
        emitter().emitNewLine();

        // -- Network trigger idle result
        if (useTrigger()) {
            // -- Network trigger idle
            emitter().emit("reg am_idle_r = 1'b1;");
            emitter().emit("wire am_idle;");
            emitter().emitNewLine();
        }

        // -- Queue wires
        getFifoQueueWires(network.getConnections());

        // -- Instance AP control wires
        getInstanceApControlWires(network.getInstances());

        if (useTrigger()) {
            // -- Trigger wires

            getLocalTriggerWires(network.getInstances());

        }
    }

    default void getFifoQueueWires(List<Connection> connections) {

        for (Connection connection : connections) {
            String queueName = queueNames().get(connection);
            Type type = getQueueType(connection);

            emitter().emit("// -- Queue wires : %s", queueName);
            if (connection.getSource().getInstance().isPresent()) {
                int dataWidth = getQueueDataWidth(connection);

                String source = String.format("q_%s_%s", connection.getSource().getInstance().get(),
                        connection.getSource().getPort());
                getFifoQueueWiresIO(dataWidth, source, true);
                emitter().emitNewLine();
            }

            if (connection.getTarget().getInstance().isPresent()) {
                int dataWidth = getQueueDataWidth(connection);

                String target = String.format("q_%s_%s", connection.getTarget().getInstance().get(),
                        connection.getTarget().getPort());
                getFifoQueueWiresIO(dataWidth, target, false);
                emitter().emitNewLine();
            }

            int dataWidth = getQueueDataWidth(connection);
            emitter().emit("wire [%d:0] %s_peek;", dataWidth - 1, queueName);
            emitter().emit("wire [31:0] %s_count;", queueName);
            emitter().emit("wire [31:0] %s_size;", queueName);
            emitter().emitNewLine();
        }

    }

    default void getFifoQueueWiresIO(Integer dataWidth, String name, Boolean isInput) {
        if (isInput) {
            emitter().emit("wire [%d:0] %s_din;", dataWidth - 1, name);
            emitter().emit("wire %s_full_n;", name);
            emitter().emit("wire %s_write;", name);
        } else {
            emitter().emit("wire [%d:0] %s_dout;", dataWidth - 1, name);
            emitter().emit("wire %s_empty_n;", name);
            emitter().emit("wire %s_read;", name);
        }

    }


    default void getInstanceApControlWires(List<Instance> instances) {
        for (Instance instance : instances) {
            String name = instance.getInstanceName();
            emitter().emit("// -- Instance AP Control Wires : %s", name);
            emitter().emit("wire %s_ap_start;", name);
            emitter().emit("wire %s_ap_done;", name);
            emitter().emit("wire %s_ap_idle;", name);
            emitter().emit("wire %s_ap_ready;", name);
            emitter().emit("wire [31:0] %s_ap_return;", name);
            emitter().emitNewLine();
        }
    }


    default void getLocalTriggerWires(ImmutableList<Instance> instances) {
        for (Instance instance : instances) {
            String name = instance.getInstanceName();

            emitter().emit("// -- Signals for the trigger module of %s", name);
            emitter().emit("wire    %s_trigger_ap_done;", name);
            emitter().emit("wire    %s_trigger_ap_idle;", name);
            emitter().emit("wire    %s_trigger_ap_ready;\t// currently inactive", name);




/*
            emitter().emit("// -- Signals for the module");
            emitter().emit("wire    %s_ap_start;", qidName);
            emitter().emit("wire    %s_ap_idle;", qidName);
            emitter().emit("wire    %s_ap_done;", qidName);
            emitter().emit("wire    %s_ap_ready;", qidName);
            emitter().emit("wire    [31 : 0] %s_ap_return;", qidName);
*/
            emitter().emit("// -- Signal for wake up and sleep");
            emitter().emit("wire    %s_waited;", name);
            emitter().emit("wire    %s;", getTriggerSignalByName(instance, "sleep"));
            emitter().emit("wire    %s;", getTriggerSignalByName(instance, "sync_sleep"));
            emitter().emitNewLine();
        }
        emitter().emitNewLine();
    }
    // ------------------------------------------------------------------------
    // -- Queues

    default void getQueues(List<Connection> connections) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- FIFO Queues");
        emitter().emitNewLine();
        for (Connection connection : connections) {
            getQueue(connection);
            emitter().emitNewLine();
        }
    }

    default void getQueue(Connection connection) {
        String queueName = queueNames().get(connection);

        String source;
        if (connection.getSource().getInstance().isPresent()) {
            source = String.format("q_%s_%s", connection.getSource().getInstance().get(),
                    connection.getSource().getPort());
        } else {
            source = connection.getSource().getPort();
        }
        String target;
        if (connection.getTarget().getInstance().isPresent()) {
            target = String.format("q_%s_%s", connection.getTarget().getInstance().get(),
                    connection.getTarget().getPort());
        } else {
            target = connection.getTarget().getPort();
        }

        int dataWidth = getQueueDataWidth(connection);
        getQueueInstantiation(queueName, dataWidth, source, target);
    }

    default void getQueueInstantiation(String queueName, int dataWidth, String source, String target) {
        emitter().emit("// -- Queue FIFO : %s", queueName);
        emitter().emit("FIFO #(");
        {
            emitter().increaseIndentation();
            emitter().emit(".MEM_STYLE(\"block\"),");
            emitter().emit(".DATA_WIDTH(%d),", dataWidth);
            emitter().emit(".ADDR_WIDTH(%s_ADDR_WIDTH)", queueName.toUpperCase());
            emitter().decreaseIndentation();
        }
        emitter().emit(") %s (", queueName);
        {
            emitter().increaseIndentation();
            emitter().emit(".clk(ap_clk),");
            emitter().emit(".reset_n(ap_rst_n),");

            emitter().emit(".if_full_n(%s_full_n),", source);
            emitter().emit(".if_write(%s_write),", source);
            emitter().emit(".if_din(%s_din),", source);
            emitter().emitNewLine();

            emitter().emit(".if_empty_n(%s_empty_n),", target);
            emitter().emit(".if_read(%s_read),", target);
            emitter().emit(".if_dout(%s_dout),", target);
            emitter().emitNewLine();

            emitter().emit(".peek(%s_peek),", queueName);
            emitter().emit(".count(%s_count),", queueName);
            emitter().emit(".size(%s_size)", queueName);
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

    // ------------------------------------------------------------------------
    // -- Instances

    default void getInstances(List<Instance> instances, List<Connection> connections) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Instances");
        emitter().emitNewLine();

        for (Instance instance : instances) {
            String qidName = getInstance(instance);
        }

    }

    default String getInstance(Instance instance) {
        // -- Instance name

        String name = instance.getInstanceName();
        // -- Entity
        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        emitter().emit("// -- Instance : %s", name);
        if (useTrigger()) {
            if (entity instanceof ActorMachine) {

                String triggerClass = "Trigger";
                emitter().emit("%s i_%s_trigger (", triggerClass, name);
                {
                    emitter().increaseIndentation();

                    emitter().emit(".ap_clk(ap_clk),");
                    emitter().emit(".ap_rst_n(ap_rst_n),");
                    emitter().emit(".ap_start(ap_start),");
                    emitter().emit(".ap_done(%s_trigger_ap_done),", name);
                    emitter().emit(".ap_idle(%s_trigger_ap_idle),", name);
                    emitter().emit(".ap_ready(%s_trigger_ap_ready),", name);

                    emitter().emit(".all_sleep(%s),", getAllSleepSignal());
                    emitter().emit(".all_sync_sleep(%s),", getAllSyncSleepSignal());
                    emitter().emit(".sleep(%s),", getTriggerSignalByName(instance, "sleep"));
                    emitter().emit(".sync_sleep(%s),", getTriggerSignalByName(instance, "sync_sleep"));
                    emitter().emit(".waited(%s),", getTriggerSignalByName(instance, "waited"));
                    emitter().emit(".all_waited(%s),", getAllWaitedSignal());
                    emitter().emit(".actor_return(%s_ap_return[1:0]),", name);
                    emitter().emit(".actor_done(%s_ap_done),", name);
                    emitter().emit(".actor_ready(%s_ap_ready),", name);
                    emitter().emit(".actor_idle(%s_ap_idle),", name);
                    emitter().emit(".actor_start(%s_ap_start)", name);

                    emitter().decreaseIndentation();
                }
                emitter().emit(");");
            } else {
                emitter().emit("assign %s_ap_start = %s;", name, String.join(" || ", entity.getInputPorts().stream()
                        .map(p -> String.format("q_%s_%s_empty_n", name, p.getName())).collect(Collectors.toList())));
            }
            emitter().emitNewLine();
        } else {
            emitter().emit("assign %s_ap_start = ap_start;", name);
            emitter().emitNewLine();
        }

        // -- External memories

        ImmutableList<VarDecl> mems = backend().externalMemory().getExternalMemories(entity);

        if (mems.isEmpty()) {
            emitter().emit("%s i_%1$s(", name);
        } else {
            emitter().emit("%s #(", name);

            emitter().increaseIndentation();

            for (VarDecl decl : mems) {
                boolean lastElement = mems.indexOf(decl) == mems.size() - 1;
                String memName = backend().externalMemory().name(instance, decl);
                emitter().emit(".C_M_AXI_%s_ID_WIDTH( C_M_AXI_%s_ID_WIDTH ),", memName.toUpperCase(),
                        memName.toUpperCase());
                emitter().emit(".C_M_AXI_%s_ADDR_WIDTH( C_M_AXI_%s_ADDR_WIDTH ),", memName.toUpperCase(),
                        memName.toUpperCase());
                emitter().emit(".C_M_AXI_%s_DATA_WIDTH( C_M_AXI_%s_DATA_WIDTH ),", memName.toUpperCase(),
                        memName.toUpperCase());
                emitter().emit(".C_M_AXI_%s_AWUSER_WIDTH( C_M_AXI_%s_AWUSER_WIDTH ),", memName.toUpperCase(),
                        memName.toUpperCase());
                emitter().emit(".C_M_AXI_%s_ARUSER_WIDTH( C_M_AXI_%s_ARUSER_WIDTH ),", memName.toUpperCase(),
                        memName.toUpperCase());
                emitter().emit(".C_M_AXI_%s_WUSER_WIDTH( C_M_AXI_%s_WUSER_WIDTH ),", memName.toUpperCase(),
                        memName.toUpperCase());
                emitter().emit(".C_M_AXI_%s_RUSER_WIDTH( C_M_AXI_%s_RUSER_WIDTH ),", memName.toUpperCase(),
                        memName.toUpperCase());
                emitter().emit(".C_M_AXI_%s_BUSER_WIDTH( C_M_AXI_%s_BUSER_WIDTH )%s", memName.toUpperCase(),
                        memName.toUpperCase(), lastElement ? "" : ",");
            }


            emitter().decreaseIndentation();

            emitter().emit(") i_%s (", name);
        }

        {
            emitter().increaseIndentation();

            // -- External Memories
            for (VarDecl decl : mems) {
                String memName = backend().externalMemory().name(instance, decl);
                getAxiMasterByPort(memName, memName);
                Type type = backend().types().declaredType(decl);
                String suffix = "";
                if (backend().context().getConfiguration().get(PlatformSettings.arbitraryPrecisionIntegers)) {
                    assert type instanceof ListType;
                    Type innerType = backend().typeseval().innerType(type);
                    if (innerType instanceof IntType) {
                        suffix = "V";
                    } else {
                        suffix = "offset";
                    }
                } else {
                    suffix = "offset";
                }

                emitter().emit(".%s_%s(%1$s_offset),", memName, suffix);
                emitter().emitNewLine();
            }

            // -- Inputs
            for (PortDecl port : entity.getInputPorts()) {
                getInstancePortDeclaration(port, name, true);
                emitter().emitNewLine();

            }
            // -- Outputs
            for (PortDecl port : entity.getOutputPorts()) {
                getInstancePortDeclaration(port, name, false);
                emitter().emitNewLine();
            }

            if (entity instanceof ActorMachine) {
                // -- IO for Inputs
                for (PortDecl port : entity.getInputPorts()) {
                    String portName = port.getName();
                    Connection.End target = new Connection.End(Optional.of(name), portName);
                    Connection connection = backend().task().getNetwork().getConnections().stream()
                            .filter(c -> c.getTarget().equals(target)).findAny().orElse(null);
                    String queueName = queueNames().get(connection);
                    if (backend().context().getConfiguration().get(PlatformSettings.arbitraryPrecisionIntegers)) {
                        Type type = backend().types().declaredPortType(port);
                        if (type instanceof IntType) {
                            emitter().emit(".io_%s_peek_V(%s),", portName, String.format("%s_peek", queueName));
                        } else {
                            emitter().emit(".io_%s_peek(%s),", portName, String.format("%s_peek", queueName));
                        }
                    } else {
                        emitter().emit(".io_%s_peek(%s),", portName, String.format("%s_peek", queueName));
                    }
                    emitter().emit(".io_%s_count(%s),", portName, String.format("%s_count", queueName));

                    emitter().emitNewLine();
                }

                // -- IO for Outputs
                for (PortDecl port : entity.getOutputPorts()) {
                    String portName = port.getName();
                    Connection.End source = new Connection.End(Optional.of(name), portName);
                    Connection connection = backend().task().getNetwork().getConnections().stream()
                            .filter(c -> c.getSource().equals(source)).findAny().orElse(null);
                    String queueName = queueNames().get(connection);

                    emitter().emit(".io_%s_size(%s),", portName, String.format("%s_size", queueName));
                    emitter().emit(".io_%s_count(%s),", portName, String.format("%s_count", queueName));

                    emitter().emitNewLine();
                }
            }

            // -- Vivado HLS control signals
            emitter().emit(".ap_start(%s_ap_start),", name);
            emitter().emit(".ap_done(%s_ap_done),", name);
            emitter().emit(".ap_idle(%s_ap_idle),", name);
            emitter().emit(".ap_ready(%s_ap_ready),", name);
            emitter().emit(".ap_return(%s_ap_return),", name);
            emitter().emitNewLine();
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n)");

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        emitter().emitNewLine();
        return name;
    }


    default void getInstancePortDeclaration(PortDecl port, String name, Boolean isInput) {
        getInstanceIOPortDeclaration(port, name, "", isInput);
        emitter().emitNewLine();
    }

    default void getInstanceIOPortDeclaration(PortDecl port, String name, String portNameExtension, Boolean isInput) {
        String portName = port.getName();
        Type type = backend().types().declaredPortType(port);
        String getPortExtension = getPortExtension(type);
        if (isInput) {
            emitter().emit(".%s%s%s_empty_n(%s),", portName, getPortExtension, portNameExtension,
                    String.format("q_%s_%s%s_empty_n", name, portName, portNameExtension));
            emitter().emit(".%s%s%s_read(%s),", portName, getPortExtension, portNameExtension,
                    String.format("q_%s_%s%s_read", name, portName, portNameExtension));
            emitter().emit(".%s%s%s_dout(%s),", portName, getPortExtension, portNameExtension,
                    String.format("q_%s_%s%s_dout", name, portName, portNameExtension));
        } else {
            emitter().emit(".%s%s%s_full_n(%s),", portName, getPortExtension, portNameExtension,
                    String.format("q_%s_%s%s_full_n", name, portName, portNameExtension));
            emitter().emit(".%s%s%s_write(%s),", portName, getPortExtension, portNameExtension,
                    String.format("q_%s_%s%s_write", name, portName, portNameExtension));
            emitter().emit(".%s%s%s_din(%s),", portName, getPortExtension, portNameExtension,
                    String.format("q_%s_%s%s_din", name, portName, portNameExtension));
        }
    }


    // ------------------------------------------------------------------------
    // -- ILA for debug
    default void getILA(List<Instance> instances) {
        emitter().emit("ila_0 i_ila_0(");
        {
            emitter().increaseIndentation();

            for (Instance instance : instances) {
                // -- Instance name
                String name = instance.getInstanceName();

                emitter().emit(".probe%d(%s_ap_return),", instances.indexOf(instance), name);
            }

            emitter().emit(".clk(ap_clk)");

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

    // ------------------------------------------------------------------------
    // -- Assignments

    default void getAssignments(Network network) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Assignments");
        emitter().emitNewLine();

        // -- Trigger assignments
        getTriggerAssignments(network.getInstances());

        // -- AP Control
        getApControlAssignments(network);

        // -- Count and Size
        getExternalAssignments(network);
    }

    default void getNetworkIsBeingExecutingAssignment(Network network) {
        emitter().emit("// -- Network is executing");
        emitter().emit("assign active_instances = %s;",
                String.join(" || ", network.getInstances().stream().filter(i -> {
                    GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(i.getEntityName(), true);
                    Entity entity = entityDecl.getEntity();
                    return entity instanceof ActorMachine;
                }).map(i -> "(~" + i.getInstanceName() + "_ap_idle)").collect(Collectors.toList())));

        emitter().emit("assign network_is_executing = (state == _EXECUTING_);");
        emitter().emitNewLine();
    }

    default void getApControlAssignments(Network network) {

        // -- Actor machine idleness
        emitter().emit("// -- Actor Machine Idleness");

        emitter().emit("always @(posedge ap_clk) begin");
        {
            emitter().increaseIndentation();
            emitter().emit("if (ap_rst_n == 1'b0)");
            emitter().emit("\tam_idle_r <= 1'b1;");
            emitter().emit("else");
            emitter().emit("\tam_idle_r <= am_idle;");
            emitter().decreaseIndentation();

        }
        emitter().emit("end");

        emitter().emit("assign am_idle = %s;",
                String.join(" & ",
                        network.getInstances().stream()
                                .filter(inst -> (backend().globalnames().entityDecl(inst.getEntityName(), true)
                                        .getEntity() instanceof ActorMachine))
                                .map(i -> i.getInstanceName() + "_trigger_ap_idle")
                                .collect(Collectors.toList())));

        // -- AP Done
        emitter().emit("// -- AP Done");

        emitter().emit("assign ap_done = am_idle & (~am_idle_r);");
        emitter().emitNewLine();

        // -- AP Idle
        emitter().emit("// -- AP Idle");

        emitter().emit("assign ap_idle = am_idle;");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Trigger Logic

    default void getApDoneLogic() {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- AP done logic");
        emitter().emitNewLine();

        emitter().emit("// -- Next state");
        emitter().emit("always @(posedge ap_clk) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("if(ap_rst_n == 1'b0)");
            emitter().emit("\tstate <= _INIT_;");
            emitter().emit("else");
            emitter().emit("\tstate <= next_state;");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
        emitter().emitNewLine();

        emitter().emit("// -- AP done state machine");
        emitter().emit("always @(*) begin");
        {
            emitter().increaseIndentation();

            emitter().emit("ap_done = 1'b0;");
            emitter().emitNewLine();

            emitter().emit("case(state)");

            // -- INIT
            {
                emitter().emit("_INIT_:");
                emitter().emit(" begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("ap_done = 1'b0;");
                    emitter().emit("if(ap_start)");
                    emitter().emit("\tnext_state = _EXECUTING_;");
                    emitter().emit("else");
                    emitter().emit("\tnext_state = _INIT_;");

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");
            }

            // -- EXECUTING
            {
                emitter().emit("_EXECUTING_:");
                emitter().emit(" begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("if (~active_instances) begin");
                    emitter().emit("\tap_done = 1'b1;");
                    emitter().emit("\tnext_state = _DONE_;");
                    emitter().emit("end else begin");
                    emitter().emit("\tnext_state = _EXECUTING_;");
                    emitter().emit("\tap_done = 1'b0;");
                    emitter().emit("end");

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");
            }

            // -- DONE
            {
                emitter().emit("_DONE_:");
                emitter().emit(" begin");
                {
                    emitter().increaseIndentation();

                    emitter().emit("ap_done = 1'b0;");
                    emitter().emit("next_state = _INIT_;");

                    emitter().decreaseIndentation();
                }
                emitter().emit("end");
            }

            // -- default
            {
                emitter().emit("default: begin");
                emitter().emit("\tap_done = 1'b0;");
                emitter().emit("\tnext_state = _INIT_;");
                emitter().emit("end");
            }

            emitter().emit("endcase");

            emitter().decreaseIndentation();
        }
        emitter().emit("end");
    }

    default void getTriggerAssignments(ImmutableList<Instance> instances) {
        List<String> sleepSignals = getTriggerSignalsByName(instances, "sleep");
        List<String> syncSleepSignals = getTriggerSignalsByName(instances, "sync_sleep");

        List<String> lastWaitedSignals = getTriggerSignalsByName(instances, "waited");


        sleepSignals.addAll(getPortTriggerSignalsByName(backend().task().getNetwork().getInputPorts(), "sleep"));
        sleepSignals.addAll(getPortTriggerSignalsByName(backend().task().getNetwork().getOutputPorts(), "sleep"));

        syncSleepSignals.addAll(getPortTriggerSignalsByName(backend().task().getNetwork().getInputPorts(), "sync_sleep"));
        syncSleepSignals.addAll(getPortTriggerSignalsByName(backend().task().getNetwork().getOutputPorts(), "sync_sleep"));


        lastWaitedSignals.addAll(getPortTriggerSignalsByName(backend().task().getNetwork().getInputPorts(), "waited"));
        lastWaitedSignals.addAll(getPortTriggerSignalsByName(backend().task().getNetwork().getOutputPorts(), "waited"));


        emitter().emitNewLine();
        emitter().emit("// -- global sync signals");
        emitter().emit("assign %s = %s;", getAllSleepSignal(), String.join(" & ", sleepSignals));
        emitter().emit("assign %s = %s;", getAllSyncSleepSignal(), String.join(" & ", syncSleepSignals));
        emitter().emit("assign %s = %s;", getAllWaitedSignal(), String.join(" & ", lastWaitedSignals));

        emitter().emitNewLine();
    }

    default List<String> getTriggerSignalsByName(ImmutableList<Instance> instances, String name) {
        List<String> signals = instances.stream()
                .filter(inst -> (backend().globalnames().entityDecl(inst.getEntityName(), true)
                        .getEntity() instanceof ActorMachine))
                .map(inst -> getTriggerSignalByName(inst, name)).collect(Collectors.toList());
        return signals;
    }

    default String getTriggerSignalByName(Instance instance, String name) {
        return instance.getInstanceName() + "_" + name;
    }

    default void getExternalAssignments(Network network) {

        emitter().emit("// -- external assignments");

        for (Connection connection : network.getConnections()) {
            String queueName = queueNames().get(connection);
            if (!connection.getSource().getInstance().isPresent()) {
                emitter().emit("assign  %s_fifo_count = %s_count;", connection.getSource().getPort(), queueName);
                emitter().emit("assign  %s_fifo_size = %s_size;", connection.getSource().getPort(), queueName);
            }
            if (!connection.getTarget().getInstance().isPresent()) {
                emitter().emit("assign  %s_fifo_count = %s_count;", connection.getTarget().getPort(), queueName);
                emitter().emit("assign  %s_fifo_size = %s_size;", connection.getTarget().getPort(), queueName);
            }
        }
        emitter().emitNewLine();
    }

    default List<String> getPortTriggerSignalsByName(List<PortDecl> ports, String name) {
        List<String> signals = ports.stream().map(port -> getPortTriggerSignalByName(port, name))
                .collect(Collectors.toList());
        return signals;
    }

    default String getPortTriggerSignalByName(PortDecl port, String name) {
        return (port.getSafeName() + "_" + name);
    }
    // -- Helper Methods

    default void getAxiMasterByPort(String safeName, String name) {
        emitter().emit(".m_axi_%s_AWVALID(m_axi_%s_AWVALID),", safeName, name);
        emitter().emit(".m_axi_%s_AWREADY(m_axi_%s_AWREADY),", safeName, name);
        emitter().emit(".m_axi_%s_AWADDR(m_axi_%s_AWADDR),", safeName, name);
        emitter().emit(".m_axi_%s_AWID(m_axi_%s_AWID),", safeName, name);
        emitter().emit(".m_axi_%s_AWLEN(m_axi_%s_AWLEN),", safeName, name);
        emitter().emit(".m_axi_%s_AWSIZE(m_axi_%s_AWSIZE),", safeName, name);
        emitter().emit(".m_axi_%s_AWBURST(m_axi_%s_AWBURST),", safeName, name);
        emitter().emit(".m_axi_%s_AWLOCK(m_axi_%s_AWLOCK),", safeName, name);
        emitter().emit(".m_axi_%s_AWCACHE(m_axi_%s_AWCACHE),", safeName, name);
        emitter().emit(".m_axi_%s_AWPROT(m_axi_%s_AWPROT),", safeName, name);
        emitter().emit(".m_axi_%s_AWQOS(m_axi_%s_AWQOS),", safeName, name);
        emitter().emit(".m_axi_%s_AWREGION(m_axi_%s_AWREGION),", safeName, name);
        emitter().emit(".m_axi_%s_AWUSER(m_axi_%s_AWUSER),", safeName, name);
        emitter().emit(".m_axi_%s_WVALID(m_axi_%s_WVALID),", safeName, name);
        emitter().emit(".m_axi_%s_WREADY(m_axi_%s_WREADY),", safeName, name);
        emitter().emit(".m_axi_%s_WDATA(m_axi_%s_WDATA),", safeName, name);
        emitter().emit(".m_axi_%s_WSTRB(m_axi_%s_WSTRB),", safeName, name);
        emitter().emit(".m_axi_%s_WLAST(m_axi_%s_WLAST),", safeName, name);
        emitter().emit(".m_axi_%s_WID(m_axi_%s_WID),", safeName, name);
        emitter().emit(".m_axi_%s_WUSER(m_axi_%s_WUSER),", safeName, name);
        emitter().emit(".m_axi_%s_ARVALID(m_axi_%s_ARVALID),", safeName, name);
        emitter().emit(".m_axi_%s_ARREADY(m_axi_%s_ARREADY),", safeName, name);
        emitter().emit(".m_axi_%s_ARADDR(m_axi_%s_ARADDR),", safeName, name);
        emitter().emit(".m_axi_%s_ARID(m_axi_%s_ARID),", safeName, name);
        emitter().emit(".m_axi_%s_ARLEN(m_axi_%s_ARLEN),", safeName, name);
        emitter().emit(".m_axi_%s_ARSIZE(m_axi_%s_ARSIZE),", safeName, name);
        emitter().emit(".m_axi_%s_ARBURST(m_axi_%s_ARBURST),", safeName, name);
        emitter().emit(".m_axi_%s_ARLOCK(m_axi_%s_ARLOCK),", safeName, name);
        emitter().emit(".m_axi_%s_ARCACHE(m_axi_%s_ARCACHE),", safeName, name);
        emitter().emit(".m_axi_%s_ARPROT(m_axi_%s_ARPROT),", safeName, name);
        emitter().emit(".m_axi_%s_ARQOS(m_axi_%s_ARQOS),", safeName, name);
        emitter().emit(".m_axi_%s_ARREGION(m_axi_%s_ARREGION),", safeName, name);
        emitter().emit(".m_axi_%s_ARUSER(m_axi_%s_ARUSER),", safeName, name);
        emitter().emit(".m_axi_%s_RVALID(m_axi_%s_RVALID),", safeName, name);
        emitter().emit(".m_axi_%s_RREADY(m_axi_%s_RREADY),", safeName, name);
        emitter().emit(".m_axi_%s_RDATA(m_axi_%s_RDATA),", safeName, name);
        emitter().emit(".m_axi_%s_RLAST(m_axi_%s_RLAST),", safeName, name);
        emitter().emit(".m_axi_%s_RID(m_axi_%s_RID),", safeName, name);
        emitter().emit(".m_axi_%s_RUSER(m_axi_%s_RUSER),", safeName, name);
        emitter().emit(".m_axi_%s_RRESP(m_axi_%s_RRESP),", safeName, name);
        emitter().emit(".m_axi_%s_BVALID(m_axi_%s_BVALID),", safeName, name);
        emitter().emit(".m_axi_%s_BREADY(m_axi_%s_BREADY),", safeName, name);
        emitter().emit(".m_axi_%s_BRESP(m_axi_%s_BRESP),", safeName, name);
        emitter().emit(".m_axi_%s_BID(m_axi_%s_BID),", safeName, name);
        emitter().emit(".m_axi_%s_BUSER(m_axi_%s_BUSER),", safeName, name);
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Helper methods

    default String getPortExtension(Type type) {
        if (type instanceof IntType) {
            if (backend().context().getConfiguration().get(PlatformSettings.arbitraryPrecisionIntegers)) {
                return "_V_V";
            } else {
                return "_V";
            }
        } else {
            return "_V";
        }

    }

    @Binding(BindingKind.LAZY)
    default Map<Connection, String> queueNames() {
        return new HashMap<>();
    }

    default String getQueueName(Connection connection) {
        if (!queueNames().containsKey(connection)) {
            Connection.End source = connection.getSource();
            Connection.End target = connection.getTarget();

            if (!source.getInstance().isPresent()) {
                if (target.getInstance().isPresent()) {
                    queueNames().put(connection, String.format("q_%s_%s_%s", source.getPort(),
                            target.getInstance().get(), target.getPort()));
                }
            } else {
                if (target.getInstance().isPresent()) {
                    queueNames().put(connection, String.format("q_%s_%s_%s_%s", source.getInstance().get(),
                            source.getPort(), target.getInstance().get(), target.getPort()));
                } else {
                    queueNames().put(connection, String.format("q_%s_%s_%s", source.getInstance().get(),
                            source.getPort(), target.getPort()));
                }
            }
        }
        return queueNames().get(connection);
    }

    default int getQueueDataWidth(Connection connection) {
        return (int) backend().typeseval().sizeOfBits(getQueueType(connection));
    }

    default Type getQueueType(Connection connection) {
        if (!connection.getSource().getInstance().isPresent()) {
            String portName = connection.getSource().getPort();
            Network network = backend().task().getNetwork();
            PortDecl port = network.getInputPorts().stream().filter(p -> p.getName().equals(portName)).findAny()
                    .orElse(null);
            return backend().types().declaredPortType(port);
        } else {
            Network network = backend().task().getNetwork();
            String srcInstanceName = connection.getSource().getInstance().get();
            Instance srcInstance = network.getInstances().stream()
                    .filter(p -> p.getInstanceName().equals(srcInstanceName)).findAny().orElse(null);
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(srcInstance.getEntityName(), true);
            Entity entity = entityDecl.getEntity();
            String portName = connection.getSource().getPort();
            PortDecl port = entity.getOutputPorts().stream().filter(p -> p.getName().equals(portName)).findAny()
                    .orElse(null);
            return backend().types().declaredPortType(port);
        }
    }

    default int getQueueAddrWidth(Connection connection) {

        if (connection.getSource().getInstance().isPresent() && connection.getTarget().getInstance().isPresent())
            return MathUtils.log2Ceil(backend().channelsutils().connectionBufferSize(connection));
        else
            return 12;
    }

    default void getExternalMemoryAxiParams(Network network, String lastDelim) {

        ImmutableList<Memories.InstanceVarDeclPair> mems = backend()
                .externalMemory().getExternalMemories(network);
        for (Memories.InstanceVarDeclPair pair: mems) {

            ImmutableList<String> params =  backend().externalMemory().getAxiParams(pair);
            for (String param: params) {
                boolean last = (mems.indexOf(pair) == mems.size() - 1) && (params.indexOf(param) == params.size() - 1);
                emitter().emit("%s%s", param, last ? lastDelim : ",");
            }
        }
    }
}
