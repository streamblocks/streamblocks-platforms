package ch.epfl.vlsc.backend.verilog;

import ch.epfl.vlsc.backend.VivadoHLSBackend;
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
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.Type;
import sun.nio.ch.Net;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        emitter().open(PathUtils.getTargetCodeGenRtl(backend().context()).resolve(identifier + ".v"));

        emitter().emit("`timescale 1ns/1ps");
        emitter().emit("//`default_nettype none");

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

            // -- Parameters
            getParameters(network);

            // -- Wires
            getWires(network);

            // -- Queues
            getQueues(network.getConnections());

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
            emitter().emit("input  wire [%d:0] %s_dout,", bitSize, port.getName());
            emitter().emit("input  wire %s_empty_n,", port.getName());
            emitter().emit("output wire %s_read,", port.getName());
        } else {
            emitter().emit("output wire [%d:0] %s_din,", bitSize, port.getName());
            emitter().emit("input  wire %s_full_n,", port.getName());
            emitter().emit("output wire %s_write,", port.getName());
        }
    }

    default void getSystemSignals() {
        emitter().emit("input  wire ap_clk,");
        emitter().emit("input  wire ap_rst_n,");
        emitter().emit("input  wire ap_start,");
        emitter().emit("output wire ap_idle");
    }

    // ------------------------------------------------------------------------
    // -- Parameters

    default void getParameters(Network network) {
        // -- Queue depth parameters

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
        getFifoQueueWires(network.getConnections());
    }

    default void getFifoQueueWires(List<Connection> connections) {
        for (Connection connection : connections) {
            int dataWidth = getQueueDataWidth(connection);

            String queueName = queueNames().get(connection);
            emitter().emit("// -- Queue wires : %s", queueName);
            emitter().emit("wire [%d:0] %s_din;", dataWidth - 1, queueName);
            emitter().emit("wire %s_full_n;", queueName);
            emitter().emit("wire %s_write;", queueName);
            emitter().emitNewLine();
            emitter().emit("wire [%d:0] %s_dout;", dataWidth - 1, queueName);
            emitter().emit("wire %s_empty_n;", queueName);
            emitter().emit("wire %s_read;", queueName);
            emitter().emitNewLine();
            emitter().emit("wire [%d:0] %s_peek;", dataWidth - 1, queueName);
            emitter().emit("wire [31:0] %s_count;", queueName);
            emitter().emit("wire [31:0] %s_size;", queueName);
            emitter().emitNewLine();
        }
    }

    // ------------------------------------------------------------------------
    // -- Queues

    default void getQueues(List<Connection> connections) {
        emitter().emit("// -- FIFO Queues");
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
            emitter().emit(".DATA_WIDTH(%d)", dataWidth);
            emitter().emit(".ADDR_WIDTH(%s_ADDR_WIDTH)", queueName.toUpperCase());
            emitter().decreaseIndentation();
        }
        emitter().emit(") %s (", queueName);
        {
            emitter().increaseIndentation();
            emitter().emit(".clk(ap_clk),");
            emitter().emit(".reset_n(ap_rst_n),");
            emitter().emit(".if_full_n(%s_full_n)", queueName);
            emitter().emit(".if_write(%s_write)", queueName);
            emitter().emit(".if_din(%s_din)", queueName);
            emitter().emitNewLine();
            emitter().emit(".if_empty_n(%s_empty_n)", queueName);
            emitter().emit(".if_read(%s_read)", queueName);
            emitter().emit(".if_dout(%s_dout)", queueName);
            emitter().emitNewLine();
            emitter().emit(".peek(%s_peek),", queueName);
            emitter().emit(".count(%s_count),", queueName);
            emitter().emit(".size(%s_size),", queueName);
            emitter().decreaseIndentation();
        }
        emitter().emit(");");
    }

    // ------------------------------------------------------------------------
    // -- Helper methods

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


}
