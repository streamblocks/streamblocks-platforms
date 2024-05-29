package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.ir.PartitionHandle;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface ChannelsUtils {

    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default Type sourceEndType(Connection.End source) {
        Network network = backend().task().getNetwork();
        try {
            List<Connection> connections = network.getConnections().stream()
                    .filter(conn -> conn.getSource().equals(source))
                    .collect(Collectors.toList());
            Type type = backend().types().connectionType(network, connections.get(0));
            return type;
        } catch (IndexOutOfBoundsException e){
            throw new RuntimeException("Error in connection for: " + source.getInstance().orElse("top_network") + "." + source.getPort().toString() + ". The port may not be connected or it is connected to an unknown actor/port.");
        }
    }

    default Type targetEndType(Connection.End target) {
        Network network = backend().task().getNetwork();
        try {
            Connection connection = network.getConnections().stream()
                    .filter(conn -> conn.getTarget().equals(target))
                    .findFirst().get();
            Type type = backend().types().connectionType(network, connection);
            return type;
        } catch (NoSuchElementException e){
            throw new RuntimeException("Error in connection for: " + target.getInstance().orElse("top_network") + "." + target.getPort().toString() + ". The port may not be connected or it is connected to an unknown actor/port.");
        }
    }

    default String inputPortTypeSize(Port port) {
        Type type = targetEndType(new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName()));
        if (type instanceof AlgebraicType) {
            return "ref";
        } else {
            return backend().typeseval().type(type);
        }
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
        String definedInput = "port_IN_" + port.getName();

        return definedInput;
    }

    default String definedInputPort(String portName) {
        //Entity entity = backend().entitybox().get();
        //PortDecl portDecl = entity.getInputPorts().stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);
        String definedInput = "port_IN_" + portName;

        return definedInput;
    }

    default String definedInputPort(PortDecl port) {
        //Entity entity = backend().entitybox().get();
        //PortDecl portDecl = entity.getInputPorts().stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);
        String definedInput = "port_IN_" + port.getName();

        return definedInput;
    }

    default String definedOutputPort(PortDecl port) {
        //Entity entity = backend().entitybox().get();
        //PortDecl portDecl = entity.getInputPorts().stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);
        String definedInput = "port_OUT_" + port.getName();

        return definedInput;
    }

    default String definedOutputPort(Port port) {
        Entity entity = backend().entitybox().get();
        PortDecl portDecl = entity.getOutputPorts().stream().filter(p -> p.getName().equals(port.getName())).findAny().orElse(null);
        String definedOutput = "port_OUT_" + port.getName();

        return definedOutput;
    }

    default String definedOutputPort(String portName) {
        //Entity entity = backend().entitybox().get();
        //PortDecl portDecl = entity.getOutputPorts().stream().filter(p -> p.getName().equals(portName)).findAny().orElse(null);
        //String definedOutput = "port_OUT_" + entity.getOutputPorts().indexOf(portDecl) + "_" + portName;
        String definedOutput = "port_OUT_" + portName;

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

    default List<Connection> targetEndConnections(Connection.End source) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getSource().equals(source))
                .collect(Collectors.toList());

        return connections;
    }

    default boolean isSourceConnected(String instance, String port) {
        Connection.End source = new Connection.End(Optional.of(instance), port);
        return !targetEndConnections(source).isEmpty();
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

    default boolean isTargetConnected(String instance, String port){
        Connection.End target = new Connection.End(Optional.of(instance), port);
        return sourceEndConnection(target)  != null;
    }

    default int sourceEndSize(Connection.End source) {
        Network network = backend().task().getNetwork();
        try {
            Connection connection = network.getConnections().stream()
                    .filter(conn -> conn.getSource().equals(source))
                    .findFirst().get();
            return connectionBufferSize(connection);
        } catch (NoSuchElementException e) {
            backend().context().getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, String.format("floating port %s.%s", source.getInstance().get(), source.getPort())));
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
        } else if (backend().context().getConfiguration().isDefined(PlatformSettings.defaultBufferDepth)){
            return backend().context().getConfiguration().get(PlatformSettings.defaultBufferDepth);
        } else {
            return PlatformSettings.defaultBufferDepth.defaultValue(backend().context().getConfiguration());
        }
    }

    default List<PartitionHandle.Pair<PortDecl, String>> getInputPortNamesAndTypes(String entityName, GlobalEntityDecl entityDecl){
        List<PartitionHandle.Pair<PortDecl, String>> portNamesTypes = new ArrayList<>();
        for (PortDecl port : entityDecl.getEntity().getInputPorts()) {
            Connection.End tgt = new Connection.End(Optional.of(entityName), port.getName());
            String tokenType = backend().typeseval().type(backend().channelsutils().targetEndType(tgt));

            portNamesTypes.add(new PartitionHandle.Pair(port.getName(), tokenType));
        }
        return portNamesTypes;
    }

    default List<PartitionHandle.Pair<PortDecl, String>> getOutputPortNamesAndTypes(String entityName, GlobalEntityDecl entityDecl){
        List<PartitionHandle.Pair<PortDecl, String>> portNamesTypes = new ArrayList<>();
        for (PortDecl port : entityDecl.getEntity().getOutputPorts()) {
            Connection.End src = new Connection.End(Optional.of(entityName), port.getName());
            String tokenType = backend().typeseval().type(backend().channelsutils().sourceEndType(src)).toString();
            portNamesTypes.add(new PartitionHandle.Pair(port.getName(), tokenType));
        }
        return portNamesTypes;
    }
}
