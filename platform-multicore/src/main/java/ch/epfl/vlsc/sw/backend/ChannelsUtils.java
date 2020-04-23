package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Diagnostic.Kind;
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
    MulticoreBackend backend();

    default String sourceEndTypeSize(Connection.End source) {
        return backend().typeseval().type(sourceEndType(source));
    }

    default Type sourceEndType(Connection.End source) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getSource().equals(source))
                .collect(Collectors.toList());
        Type type = backend().types().connectionType(network, connections.get(0));
        return type;
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
        } catch (NoSuchElementException e){
            backend().context().getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, String.format("floating port %s.%s", target.getInstance().get(), target.getPort())));
            throw new RuntimeException(e);
        }

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
            attribute = connection.getValueAttribute("buffersize");
        }
        if (attribute.isPresent()) {
            return (int) backend().constants().intValue(attribute.get().getValue()).getAsLong();
        } else if (backend().context().getConfiguration().isDefined(PlatformSettings.defaultBufferSize)){
            return backend().context().getConfiguration().get(PlatformSettings.defaultBufferSize);
        } else {
            return PlatformSettings.defaultBufferSize.defaultValue(backend().context().getConfiguration());
        }
    }


}
