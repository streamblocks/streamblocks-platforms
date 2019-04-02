package ch.epfl.vlsc.backend;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.Type;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface ChannelsUtils {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

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
        String definedInput = port.getName();

        return definedInput;
    }

    default String definedInputPort(String portName) {
        String definedInput = portName;

        return definedInput;
    }


    default String definedOutputPort(Port port) {
        String definedOutput = port.getName();

        return definedOutput;
    }

    default String definedOutputPort(String portName) {
        String definedOutput = portName;

        return definedOutput;
    }

    default int targetEndSize(Connection.End target) {
        Network network = backend().task().getNetwork();
        try {
            Connection connection = network.getConnections().stream()
                    .filter(conn -> conn.getTarget().equals(target))
                    .findFirst().get();
            return connectionBufferSize(connection);
        } catch (NoSuchElementException e){
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
            return 512;
        }
    }


}
