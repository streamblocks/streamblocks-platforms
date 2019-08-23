package ch.epfl.vlsc.hls.backend.verilog;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.MathUtils;
import ch.epfl.vlsc.platformutils.utils.TypeUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface VerilogNetwork {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateNetwork() {
        // -- Identifier
        String identifier = backend().task().getIdentifier().getLast().toString();

        // -- Network
        Network network = backend().task().getNetwork();

        // -- Network file
        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + ".sv"));

        emitter().emit("`include \"TriggerTypes.sv\"");
        emitter().emit("import TriggerTypes::*;");

        emitter().emitNewLine();

        // -- Network module
        emitter().emit("module %s (", identifier);
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
            getInstances(network.getInstances());

            // -- Assignments
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

        // -- System IO
        getSystemSignals();
    }

    default void getPortDeclaration(PortDecl port, boolean isInput) {
        Type type = backend().types().declaredPortType(port);
        int bitSize = TypeUtils.sizeOfBits(type);
        if (isInput) {
            emitter().emit("input  wire [%d:0] %s_din,", bitSize - 1, port.getName());
            emitter().emit("output wire %s_full_n,", port.getName());
            emitter().emit("input  wire %s_write,", port.getName());
        } else {
            emitter().emit("output wire [%d:0] %s_dout,", bitSize - 1, port.getName());
            emitter().emit("output wire %s_empty_n,", port.getName());
            emitter().emit("input  wire %s_read,", port.getName());
        }
    }

    default void getSystemSignals() {
        emitter().emit("input  wire ap_clk,");
        emitter().emit("input  wire ap_rst_n,");
        emitter().emit("input  wire ap_start,");
        emitter().emit("output wire ap_idle,");
        emitter().emit("output wire  ap_done,");
        emitter().emit("input wire input_idle");
    }

    // ------------------------------------------------------------------------
    // -- Parameters

    default void getParameters(Network network) {
        // -- Queue depth parameters
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Parameters");
        emitter().emit("localparam mode_t trigger_mode = ACTOR_TRIGGER;");
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

        // -- Queue wires
        getFifoQueueWires(network.getConnections());
        emitter().emitNewLine();

        emitter().emitNewLine();
        // -- Instance AP control wires
        getInstanceApControlWires(network.getInstances());

        emitter().emitNewLine();
    }

    default void getFifoQueueWires(List<Connection> connections) {
        for (Connection connection : connections) {
            int dataWidth = getQueueDataWidth(connection);
            String queueName = queueNames().get(connection);

            emitter().emit("// -- Queue wires : %s", queueName);
            if (connection.getSource().getInstance().isPresent()) {
                String source = String.format("q_%s_%s", connection.getSource().getInstance().get(), connection.getSource().getPort());
                emitter().emit("wire [%d:0] %s_din;", dataWidth - 1, source);
                emitter().emit("wire %s_full_n;", source);
                emitter().emit("wire %s_write;", source);
                emitter().emitNewLine();
            }

            if (connection.getTarget().getInstance().isPresent()) {
                String target = String.format("q_%s_%s", connection.getTarget().getInstance().get(), connection.getTarget().getPort());
                emitter().emit("wire [%d:0] %s_dout;", dataWidth - 1, target);
                emitter().emit("wire %s_empty_n;", target);
                emitter().emit("wire %s_read;", target);
                emitter().emitNewLine();
            }

            emitter().emit("wire [%d:0] %s_peek;", dataWidth - 1, queueName);
            emitter().emit("wire [31:0] %s_count;", queueName);
            emitter().emit("wire [31:0] %s_size;", queueName);
            emitter().emitNewLine();
        }
    }

    default void getInstanceApControlWires(List<Instance> instances) {
        for (Instance instance : instances) {
            String name = instance.getInstanceName();
            emitter().emit("// -- Instance AP Control Wires : %s", name);
            emitter().emit("wire %s_ap_done;", name);
            emitter().emit("wire %s_ap_idle;", name);
            emitter().emit("wire %s_ap_ready;", name);
            emitter().emit("wire [31:0] %s_ap_return;", name);
            emitter().emitNewLine();
        }
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
        int dataWidth = getQueueDataWidth(connection);
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

            String source;
            if (connection.getSource().getInstance().isPresent()) {
                source = String.format("q_%s_%s", connection.getSource().getInstance().get(), connection.getSource().getPort());
            } else {
                source = connection.getSource().getPort();
            }
            emitter().emit(".if_full_n(%s_full_n),", source);
            emitter().emit(".if_write(%s_write),", source);
            emitter().emit(".if_din(%s_din),", source);
            emitter().emitNewLine();

            String target;
            if (connection.getTarget().getInstance().isPresent()) {
                target = String.format("q_%s_%s", connection.getTarget().getInstance().get(), connection.getTarget().getPort());
            } else {
                target = connection.getTarget().getPort();
            }
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

    default void getInstances(List<Instance> instances) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Instances");
        emitter().emitNewLine();
        List<String> idleList = instances.stream()
                    .map(
                        inst->backend().instaceQID(inst.getInstanceName(), "_") + "_ap_idle"
                    )
                    .collect(Collectors.toList());
        idleList.add("input_idle");



        for (Instance instance : instances) {
            String qidName = getInstance(instance);
            emitter().emit("// -- network idle condition for %s", qidName);
            emitter().emit("assign %s_network_idle = %s;", qidName,
                    String.join(" & ", idleList.stream()
                            .filter(inst->!inst.equals(qidName + "_ap_idle"))
                            .collect(Collectors.toList())
                    )
            );
        }
    }

    default String getInstance(Instance instance) {
        // -- Instance name
        String qidName = backend().instaceQID(instance.getInstanceName(), "_");

        String name = instance.getInstanceName();
        // -- Entity
        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        emitter().emit("// -- Instance : %s", qidName);
        if (entity instanceof ActorMachine) {
            // -- Actor trigger wires
            emitter().emit("// -- Signals for the trigger module");
            emitter().emit("wire    %s_trigger_ap_done;", qidName);
            emitter().emit("wire    %s_trigger_ap_idle;", qidName);
            emitter().emit("wire    %s_trigger_ap_ready;\t// currently inactive", qidName);
            emitter().emit("wire    %s_network_idle;", qidName);
            emitter().emit("// -- Signals for the module");
            emitter().emit("wire    %s_ap_start;", qidName);
            emitter().emit("wire    %s_ap_idle;", qidName);
            emitter().emit("wire    %s_ap_done;", qidName);
            emitter().emit("wire    [31 : 0] %s_ap_return;", qidName);


            emitter().emitNewLine();

            emitter().emit("trigger #(.mode(trigger_mode)) i_%s_trigger (", qidName);
            {
                emitter().increaseIndentation();

                emitter().emit(".ap_clk(ap_clk),");
                emitter().emit(".ap_rst_n(ap_rst_n),");
                emitter().emit(".ap_start(ap_start),");
                emitter().emit(".ap_done(%s_trigger_ap_done),", qidName);
                emitter().emit(".ap_idle(%s_trigger_ap_idle),", qidName);
                emitter().emit(".ap_ready(%s_trigger_ap_ready),", qidName);
                emitter().emit(".network_idle(%s_network_idle),", qidName);
                emitter().emit(".actor_return(%s_ap_return),", qidName);
                emitter().emit(".actor_done(%s_ap_done),", qidName);
                emitter().emit(".actor_ready(%s_ap_ready),", qidName);
                emitter().emit(".actor_idle(%s_ap_idle),", qidName);
                emitter().emit(".actor_launch_predicate(),");
                emitter().emit(".actor_start(%s_ap_start)", qidName);


                emitter().decreaseIndentation();
            }
            emitter().emit(");");
            emitter().emitNewLine();
        } else {
            emitter().emit("wire %s_ap_start = %s;", qidName,
                    String.join(" || ", entity.getInputPorts()
                            .stream().map(p -> String.format("q_%s_%s_empty_n", name, p.getName()))
                            .collect(Collectors.toList())));
            emitter().emitNewLine();
        }
        emitter().emit("%s i_%1$s(", qidName);
        {
            emitter().increaseIndentation();
            // -- Inputs
            for (PortDecl port : entity.getInputPorts()) {
                String portName = port.getName();
                emitter().emit(".%s%s_empty_n(%s),", portName, getPortExtension(), String.format("q_%s_%s_empty_n", name, portName));
                emitter().emit(".%s%s_read(%s),", portName, getPortExtension(), String.format("q_%s_%s_read", name, portName));
                emitter().emit(".%s%s_dout(%s),", portName, getPortExtension(), String.format("q_%s_%s_dout", name, portName));
                emitter().emitNewLine();
            }
            // -- Outputs
            for (PortDecl port : entity.getOutputPorts()) {
                String portName = port.getName();
                emitter().emit(".%s%s_full_n(%s),", portName, getPortExtension(), String.format("q_%s_%s_full_n", name, portName));
                emitter().emit(".%s%s_write(%s),", portName, getPortExtension(), String.format("q_%s_%s_write", name, portName));
                emitter().emit(".%s%s_din(%s),", portName, getPortExtension(), String.format("q_%s_%s_din", name, portName));
                emitter().emitNewLine();
            }

            if (entity instanceof ActorMachine) {
                // -- IO for Inputs
                for (PortDecl port : entity.getInputPorts()) {
                    String portName = port.getName();
                    Connection.End target = new Connection.End(Optional.of(name), portName);
                    Connection connection = backend().task().getNetwork()
                            .getConnections().stream()
                            .filter(c -> c.getTarget().equals(target)).findAny().orElse(null);
                    String queueName = queueNames().get(connection);

                    emitter().emit(".io_%s_peek(%s),", portName, String.format("%s_peek", queueName));
                    emitter().emit(".io_%s_count(%s),", portName, String.format("%s_count", queueName));
                    emitter().emitNewLine();
                }

                // -- IO for Outputs
                for (PortDecl port : entity.getOutputPorts()) {
                    String portName = port.getName();
                    Connection.End source = new Connection.End(Optional.of(name), portName);
                    Connection connection = backend().task().getNetwork()
                            .getConnections().stream()
                            .filter(c -> c.getSource().equals(source)).findAny().orElse(null);
                    String queueName = queueNames().get(connection);
                    emitter().emit(".io_%s_size(%s),", portName, String.format("%s_size", queueName));
                    emitter().emit(".io_%s_count(%s),", portName, String.format("%s_count", queueName));
                    emitter().emitNewLine();
                }
            }


            // -- Vivado HLS control signals
            emitter().emit(".ap_start(%s_ap_start),", qidName);
            emitter().emit(".ap_done(%s_ap_done),", qidName);
            emitter().emit(".ap_idle(%s_ap_idle),", qidName);
            emitter().emit(".ap_ready(%s_ap_ready),", qidName);
            emitter().emit(".ap_return(%s_ap_return),", qidName);
            emitter().emitNewLine();
            emitter().emit(".ap_clk(ap_clk),");
            emitter().emit(".ap_rst_n(ap_rst_n)");

            emitter().decreaseIndentation();
        }
        emitter().emit(");");
        return qidName;
    }

    // ------------------------------------------------------------------------
    // -- Assignments

    default void getAssignments(Network network) {
        emitter().emit("// ------------------------------------------------------------------------");
        emitter().emit("// -- Assignments");
        emitter().emitNewLine();


        // -- AP Control
        getApControlAssignments(network);

    }

    default void getNeworkIsBeingExecutingAssignment(Network network) {
        emitter().emit("// -- Network is executing");
        emitter().emit("assign active_instances = %s;", String.join(" || ", network.getInstances()
                .stream()
                .filter(i -> {
                    GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(i.getEntityName(), true);
                    Entity entity = entityDecl.getEntity();
                    return entity instanceof ActorMachine;
                })
                .map(i -> "(~" + i.getInstanceName() + "_ap_idle)")
                .collect(Collectors.toList())));

        emitter().emit("assign network_is_executing = (state == _EXECUTING_);");
        emitter().emitNewLine();
    }

    default void getApControlAssignments(Network network) {

        // -- AP Done
        emitter().emit("// -- AP Done");
        emitter().emitClikeBlockComment("Done not yet supported");
        emitter().emitNewLine();

        // -- AP Idle
        emitter().emit("// -- AP Idle");
        emitter().emit("assign ap_idle = %s;", String.join(" & ", network.getInstances()
                .stream().map(i -> backend().instaceQID(i.getInstanceName(),"_") + "_ap_idle")
                .collect(Collectors.toList())));
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Helper methods


    default String getPortExtension() {
        // -- TODO : Add _V_V for type accuracy
        return "_V";
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
                    queueNames().put(connection, String.format("q_%s_%s_%s", source.getPort(), target.getInstance().get(), target.getPort()));
                }
            } else {
                if (target.getInstance().isPresent()) {
                    queueNames().put(connection, String.format("q_%s_%s_%s_%s", source.getInstance().get(), source.getPort(), target.getInstance().get(), target.getPort()));
                } else {
                    queueNames().put(connection, String.format("q_%s_%s_%s", source.getInstance().get(), source.getPort(), target.getPort()));
                }
            }
        }
        return queueNames().get(connection);
    }

    default int getQueueDataWidth(Connection connection) {
        int dataWidth;
        if (!connection.getSource().getInstance().isPresent()) {
            String portName = connection.getSource().getPort();
            Network network = backend().task().getNetwork();
            PortDecl port = network.getInputPorts().stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);
            dataWidth = TypeUtils.sizeOfBits(backend().types().declaredPortType(port));
        } else {
            Network network = backend().task().getNetwork();
            String srcInstanceName = connection.getSource().getInstance().get();
            Instance srcInstance = network.getInstances().stream().
                    filter(p -> p.getInstanceName().equals(srcInstanceName)).
                    findAny().orElse(null);
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(srcInstance.getEntityName(), true);
            Entity entity = entityDecl.getEntity();
            String portName = connection.getSource().getPort();
            PortDecl port = entity.getOutputPorts().stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);
            dataWidth = TypeUtils.sizeOfBits(backend().types().declaredPortType(port));
        }
        return dataWidth;
    }

    default int getQueueAddrWidth(Connection connection) {
        return MathUtils.log2Ceil(backend().channelsutils().connectionBufferSize(connection));
    }

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


}
