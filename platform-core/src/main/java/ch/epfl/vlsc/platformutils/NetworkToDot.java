package ch.epfl.vlsc.platformutils;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.ConstantEvaluator;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

@Module
public interface NetworkToDot {

    @Binding(BindingKind.INJECTED)
    Emitter emitter();

    @Binding(BindingKind.INJECTED)
    GlobalNames globalNames();

    @Binding(BindingKind.INJECTED)
    ConstantEvaluator constants();

    default void generateNetworkDot(Network network, String name, Path path) {
        emitter().open(path);

        networkToDot(network, name);

        emitter().close();
    }

    default void networkToDot(Network network, String name) {
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
                emitter().emit("<tr><td bgcolor=\"#7ba79d\"><font point-size=\"25\" color=\"#ffffff\"> INPUTS </font></td></tr>");
                emitter().emit("<tr><td>");
                emitter().increaseIndentation();
                emitter().emit("<table border=\"0\" cellborder=\"0\" cellspacing=\"0\">");
                for (PortDecl port : network.getInputPorts()) {
                    emitter().emit("\t<tr><td align=\"right\" port=\"%s\"> <font point-size=\"15\"> %1$s </font> </td></tr>", port.getName());
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
            instanceToDot(instance);
        }

        // -- Output Ports
        if (network.getOutputPorts().size() > 0) {

            emitter().emit("__OUTs [label=<");
            emitter().increaseIndentation();
            {
                emitter().emit("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">");
                emitter().emit("<tr><td bgcolor=\"#7ba79d\"><font point-size=\"25\" color=\"#ffffff\"> OUTPUTS </font></td></tr>");
                emitter().emit("<tr><td>");
                emitter().increaseIndentation();
                emitter().emit("<table border=\"0\" cellborder=\"0\" cellspacing=\"0\">");
                for (PortDecl port : network.getOutputPorts()) {
                    emitter().emit("\t<tr><td align=\"left\" port=\"%s\"> <font point-size=\"15\"> %1$s </font> </td></tr>", port.getName());
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
            connectionToDot(connection);
        }

        emitter().decreaseIndentation();
        emitter().emit("}");
    }


    default void instanceToDot(Instance instance) {
        GlobalEntityDecl entityDecl = globalNames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();
        String instanceName = instance.getInstanceName();
        emitter().emit("%s [label=<", instanceName);
        emitter().increaseIndentation();

        emitter().emit("<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">");
        emitter().emit("<tr><td bgcolor=\"black\"><font point-size=\"30\" color=\"#ffffff\"> %s </font></td></tr>", instanceName);

        emitter().emit("<tr><td>");
        emitter().increaseIndentation();
        emitter().emit("<table border=\"1\" cellborder=\"0\" cellspacing=\"0\">");
        emitter().increaseIndentation();
        emitter().emit("<tr><td colspan=\"2\"><font point-size=\"15\">[%s]</font></td></tr>", entityDecl.getName());

        int rows = entity.getInputPorts().size() > entity.getOutputPorts().size() ? entity.getInputPorts().size() : entity.getOutputPorts().size();

        // -- Ports
        for (int i = 0; i < rows; i++) {
            String in = i < entity.getInputPorts().size() ? entity.getInputPorts().get(i).getName() : "";
            String out = i < entity.getOutputPorts().size() ? entity.getOutputPorts().get(i).getName() : "";
            emitter().emit("<tr><td align=\"left\" port=\"%s\"> %1$s </td><td align=\"right\" port=\"%s\"> <font point-size=\"15\"> %2$s </font> </td></tr>", in, out);
        }

        emitter().decreaseIndentation();
        emitter().emit("</table>");
        emitter().decreaseIndentation();
        emitter().emit("</td></tr>");


        emitter().emit("</table>>];");
        emitter().decreaseIndentation();

    }

    default void connectionToDot(Connection connection) {
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

        emitter().emit("%s:e -> %s:w [color=\"%s\", label=\"sz=%d\"];", source, target, encodeColor(hashColor(connection)), connectionBufferSize(connection));
    }

    default int connectionBufferSize(Connection connection) {
        Optional<ToolValueAttribute> attribute = connection.getValueAttribute("buffersize");
        if (!attribute.isPresent()) {
            attribute = connection.getValueAttribute("bufferSize");
        }
        if (attribute.isPresent()) {
            return (int) constants().intValue(attribute.get().getValue()).getAsLong();
        } else {
            return 4096;
        }
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
