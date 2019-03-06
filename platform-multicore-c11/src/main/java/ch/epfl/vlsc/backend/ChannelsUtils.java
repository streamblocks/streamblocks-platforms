package ch.epfl.vlsc.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.Type;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface ChannelsUtils {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default String sourceEndTypeSize(Connection.End source) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getSource().equals(source))
                .collect(Collectors.toList());
        Type type = backend().types().connectionType(network, connections.get(0));

        return backend().typeseval().type(type);
    }

    default String targetEndTypeSize(Connection.End target) {
        Network network = backend().task().getNetwork();
        Connection connection = network.getConnections().stream()
                .filter(conn -> conn.getTarget().equals(target))
                .findFirst().get();
        Type type = backend().types().connectionType(network, connection);
        return backend().typeseval().type(type);
    }


    default String inputPortTypeSize(Port port) {
        return targetEndTypeSize(new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName()));
    }

    default String outputPortTypeSize(Port port) {
        Connection.End source = new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName());
        return sourceEndTypeSize(source);
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
        Connection connection = network.getConnections().stream()
                .filter(conn -> conn.getTarget().equals(target))
                .findFirst().get();
        return connectionBufferSize(connection);
    }


    default int connectionBufferSize(Connection connection) {
        Optional<ToolValueAttribute> attribute = connection.getValueAttribute("buffersize");
        if (!attribute.isPresent()) {
            attribute = connection.getValueAttribute("bufferSize");
        }
        if (attribute.isPresent()) {
            return (int) backend().constants().intValue(attribute.get().getValue()).getAsLong();
        } else {
            return 4096;
        }
    }


}
