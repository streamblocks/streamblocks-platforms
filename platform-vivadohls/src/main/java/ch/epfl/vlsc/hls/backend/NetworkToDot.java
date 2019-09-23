package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Module
public interface NetworkToDot {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateNetworkDot() {
        String fileName = String.join("_", backend().task().getIdentifier().parts());

        Path dotPath = PathUtils.getAuxiliary(backend().context()).resolve(fileName + ".dot");

        emitter().open(dotPath);

        Network network = backend().task().getNetwork();

        toDot(network);

        emitter().close();
    }


    default void toDot(Network network) {
        // -- Connections
        List<Connection> connections = network.getConnections();
        Map<Connection.End, List<Connection.End>> srcToTgt = new HashMap<>();

        for (Connection connection : connections) {
            Connection.End src = connection.getSource();
            Connection.End tgt = connection.getTarget();
            srcToTgt.computeIfAbsent(src, x -> new ArrayList<>())
                    .add(tgt);
        }

        emitter().emit("/* StreamBlocks Network Dotty printer */");
        String name = backend().task().getIdentifier().getLast().toString();

        emitter().emit("digraph %s {", name);
        emitter().increaseIndentation();

        emitter().emit("node [shape=none];");
        emitter().emit("rankdir=LR;");

        // -- Input Ports
        if (network.getInputPorts().size() > 0) {
            emitter().emit("__INs [label=<");
            emitter().increaseIndentation();
            {
                emitter().emit("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">");
                emitter().emit("<tr><td bgcolor=\"black\"><font color=\"#ffffff\"> INPUTS </font></td></tr>");
                emitter().emit("<tr><td>");
                emitter().increaseIndentation();
                emitter().emit("<table border=\"0\" cellborder=\"0\" cellspacing=\"0\">");
                for (PortDecl port : network.getInputPorts()) {
                    emitter().emit("\t<tr><td align=\"right\" port=\"%s\"> %1$s </td></tr>", port.getName());
                }
                emitter().emit("</table>");
                emitter().decreaseIndentation();
                emitter().emit("</td></tr>");
                emitter().emit("</table>");
            }
            emitter().decreaseIndentation();
            emitter().emit(">];");
        }

        // -- Instances
        for (Instance instance : network.getInstances()) {
            toDot(instance);
        }

        // -- Output Ports
        if (network.getOutputPorts().size() > 0) {

            emitter().emit("__OUTs [label=<");
            emitter().increaseIndentation();
            {
                emitter().emit("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">");
                emitter().emit("<tr><td bgcolor=\"black\"><font color=\"#ffffff\"> OUTPUTS </font></td></tr>");
                emitter().emit("<tr><td>");
                emitter().increaseIndentation();
                emitter().emit("<table border=\"0\" cellborder=\"0\" cellspacing=\"0\">");
                for (PortDecl port : network.getOutputPorts()) {
                    emitter().emit("\t<tr><td align=\"left\" port=\"%s\"> %1$s </td></tr>", port.getName());
                }
                emitter().emit("</table>");
                emitter().decreaseIndentation();
                emitter().emit("</td></tr>");
                emitter().emit("</table>");
            }
            emitter().decreaseIndentation();
            emitter().emit(">];");
        }

        // -- Connections
        for (Connection connection : connections) {
            toDot(connection);
        }

        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void toDot(Instance instance) {
        GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();
        String instanceName = instance.getInstanceName();
        emitter().emit("%s [label=<", instanceName);
        emitter().increaseIndentation();

        emitter().emit("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">");
        emitter().emit("<tr><td bgcolor=\"black\"><font color=\"#ffffff\"> %s </font></td></tr>", instanceName);

        emitter().emit("<tr><td>");
        emitter().increaseIndentation();
        emitter().emit("<table border=\"1\" cellborder=\"0\" cellspacing=\"0\">");
        emitter().increaseIndentation();
        emitter().emit("<tr><td colspan=\"2\"><font point-size=\"8\">[%s]</font></td></tr>", entityDecl.getName());

        int rows = entity.getInputPorts().size() > entity.getOutputPorts().size() ? entity.getInputPorts().size() : entity.getOutputPorts().size();

        // -- Ports
        for (int i = 0; i < rows; i++) {
            String in = i < entity.getInputPorts().size() ? entity.getInputPorts().get(i).getName() : "";
            String out = i < entity.getOutputPorts().size() ? entity.getOutputPorts().get(i).getName() : "";
            emitter().emit("<tr><td align=\"left\" port=\"%s\"> %1$s </td><td align=\"right\" port=\"%s\"> %2$s </td></tr>", in, out);
        }

        emitter().decreaseIndentation();
        emitter().emit("</table>");
        emitter().decreaseIndentation();
        emitter().emit("</td></tr>");


        emitter().emit("</table>>];");
        emitter().decreaseIndentation();

    }

    default void toDot(Connection connection) {
        String srcInstanceName = "";
        if (connection.getSource().getInstance().isPresent()) {
            srcInstanceName = connection.getSource().getInstance().get() + ":";
        } else {
            srcInstanceName = "__INs:";
        }

        String tgtInstanceName = "";
        if (connection.getTarget().getInstance().isPresent()) {
            tgtInstanceName = connection.getTarget().getInstance().get() + ":";
        } else {
            tgtInstanceName = "__OUTs:";
        }


        String source = srcInstanceName + connection.getSource().getPort();
        String target = tgtInstanceName + connection.getTarget().getPort();

        emitter().emit("%s:e -> %s:w [color=\"%s\", label=\"sz=%d\"];", source, target, encodeColor(hashColor(connection)), backend().channelsutils().connectionBufferSize(connection));
    }

    /**
     * Get an RGB color from object hash code
     *
     * @param value
     * @return
     */
    default Color hashColor(Object value) {
        if (value == null) {
            return Color.WHITE.darker();
        } else {
            int r = 0xff - (Math.abs(1 + value.hashCode()) % 0xce);
            int g = 0xff - (Math.abs(1 + value.hashCode()) % 0xdd);
            int b = 0xff - (Math.abs(1 + value.hashCode()) % 0xec);
            return new Color(r, g, b);
        }
    }

    /**
     * @return a hex Color string in the format #rrggbb.
     */
    default String encodeColor(Color color) {
        return "#" + String.format("%06x", color.getRGB() & 0xffffff);

    }


}
