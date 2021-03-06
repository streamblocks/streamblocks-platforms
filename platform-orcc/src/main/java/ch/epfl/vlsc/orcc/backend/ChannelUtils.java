package ch.epfl.vlsc.orcc.backend;

import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface ChannelUtils {

    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    default Instance sourceEndInstance(Connection.End target) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getTarget().equals(target))
                .collect(Collectors.toList());
        Instance instance = network.getInstances().stream()
                .filter(inst -> inst.getInstanceName().equals(connections.get(0).getSource().getInstance().get()))
                .findFirst().get();
        return instance;
    }

    default Connection sourceEndConnection(Connection.End target) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getTarget().equals(target))
                .collect(Collectors.toList());
        if (connections.isEmpty()) {
            return null;
        }
        return connections.get(0);
    }

    default List<Instance> targetEndInstance(Connection.End source) {
        List<Instance> instances = new ArrayList<>();
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getSource().equals(source))
                .collect(Collectors.toList());
        for (Connection connection : connections) {
            Instance instance = network.getInstances().stream()
                    .filter(inst -> inst.getInstanceName().equals(connection.getTarget().getInstance().get()))
                    .findFirst().get();
            if (!instances.contains(instance))
                instances.add(instance);
        }

        return instances;
    }

    default List<Connection> targetEndConnections(Connection.End source) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getSource().equals(source))
                .collect(Collectors.toList());

        return connections;
    }

    default boolean isSourceConnected(String instance, String port) {
        Connection.End source = new Connection.End(Optional.of(instance), port);
        return !backend().channelUtils().targetEndConnections(source).isEmpty();
    }

    default boolean isTargetConnected(String instance, String port){
        Connection.End target = new Connection.End(Optional.of(instance), port);
        return backend().channelUtils().sourceEndConnection(target)  != null;
    }

    default Type sourceEndType(Connection.End source) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getSource().equals(source))
                .collect(Collectors.toList());
        Type type = backend().types().connectionType(network, connections.get(0));
        return type;
    }

    default Type targetEndType(Connection.End target) {
        Network network = backend().task().getNetwork();
        Connection connection = network.getConnections().stream()
                .filter(conn -> conn.getTarget().equals(target))
                .findFirst().get();
        Type type = backend().types().connectionType(network, connection);
        return type;
    }

    default String inputPortTypeSize(Port port) {
        Type type = targetEndType(new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName()));
        if (type instanceof AlgebraicType) {
            return "ref";
        } else {
            return backend().typeseval().type(type);
        }
    }

    default Type inputPortType(Port port) {
        Type type = targetEndType(new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName()));
        return type;
    }

    default String outputPortTypeSize(Port port) {
        Connection.End source = new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName());
        Type type = sourceEndType(source);
        if (type instanceof AlgebraicType) {
            return "ref";
        } else {
            return backend().typeseval().type(type);
        }
    }

    default Type outputPortType(Port port) {
        Connection.End source = new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName());
        return sourceEndType(source);
    }

    default String definedInputPort(Port port) {
        Entity entity = backend().entitybox().get();
        PortDecl portDecl = entity.getInputPorts().stream().filter(p -> p.getName().equals(port.getName())).findAny().orElse(null);
        String definedInput = "IN" + entity.getInputPorts().indexOf(portDecl) + "_" + port.getName();

        return definedInput;
    }

    default String definedInputPort(String portName) {
        Entity entity = backend().entitybox().get();
        PortDecl portDecl = entity.getInputPorts().stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);
        String definedInput = "IN" + entity.getInputPorts().indexOf(portDecl) + "_" + portName;

        return definedInput;
    }

    default String definedOutputPort(Port port) {
        Entity entity = backend().entitybox().get();
        PortDecl portDecl = entity.getOutputPorts().stream().filter(p -> p.getName().equals(port.getName())).findAny().orElse(null);
        String definedOutput = "OUT" + entity.getOutputPorts().indexOf(portDecl) + "_" + port.getName();

        return definedOutput;
    }

    default String definedOutputPort(String portName) {
        Entity entity = backend().entitybox().get();
        PortDecl portDecl = entity.getOutputPorts().stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);
        String definedOutput = "OUT" + entity.getOutputPorts().indexOf(portDecl) + "_" + portName;

        return definedOutput;
    }

    default int targetEndSize(Connection.End target) {
        Network network = backend().task().getNetwork();
        try {
            Connection connection = network.getConnections().stream()
                    .filter(conn -> conn.getTarget().equals(target))
                    .findFirst().get();
            return connectionBufferSize(connection);
        } catch (NoSuchElementException e) {
            backend().context().getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, String.format("floating port %s.%s", target.getInstance().get(), target.getPort())));
            throw new RuntimeException(e);
        }

    }

    default int connectionBufferSize(Connection connection) {
        Optional<ToolValueAttribute> attribute = connection.getValueAttribute("buffersize");
        if (!attribute.isPresent()) {
            attribute = connection.getValueAttribute("bufferSize");
        }
        if (attribute.isPresent()) {
            return (int) backend().constants().intValue(attribute.get().getValue()).getAsLong();
        } else {
            return backend().context().getConfiguration().get(PlatformSettings.defaultBufferDepth);
        }
    }

}
