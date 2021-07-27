package ch.epfl.vlsc.wsim.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.Type;
import ch.epfl.vlsc.wsim.backend.WSimBackend;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface Channels {

    @Binding(BindingKind.INJECTED)
    WSimBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default String outputPortTypeSize(Port port) {
        Connection.End source = new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName());
        return sourceEndTypeSize(source);
    }

    default String inputPortTypeSize(Port port) {
        return targetEndTypeSize(new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName()));
    }

    default String sourceEndTypeSize(Connection.End source) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getSource().equals(source))
                .collect(Collectors.toList());
        Type type = backend().types().connectionType(network, connections.get(0));

        return backend().typeseval().type(type);
    }

    default Connection sourceEndConnection(Connection.End target) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getTarget().equals(target))
                .collect(Collectors.toList());
        if(connections.isEmpty()){
            return null;
        }
        return connections.get(0);
    }


    default List<Connection> targetEndConnections(Connection.End source) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getSource().equals(source))
                .collect(Collectors.toList());

        return connections;
    }

    default String targetEndTypeSize(Connection.End target) {
        Network network = backend().task().getNetwork();
        Connection connection = network.getConnections().stream()
                .filter(conn -> conn.getTarget().equals(target))
                .findFirst().get();
        Type type = backend().types().connectionType(network, connection);
        return backend().typeseval().type(type);
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

    default Connection targetEndConnection(Connection.End target) {
        Network network = backend().task().getNetwork();
        try {
            Connection connection = network.getConnections().stream()
                    .filter(conn -> conn.getTarget().equals(target))
                    .findFirst().get();
           return connection;
        } catch (NoSuchElementException e) {
            backend().context().getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, String.format("floating port %s.%s", target.getInstance().get(), target.getPort())));
            throw new RuntimeException(e);
        }

    }

    default String connectionName(Connection connection) {
        return String.format("%s_%s_%s_%s",
                connection.getSource().getInstance().get(),
                connection.getSource().getPort(),
                connection.getTarget().getInstance().get(),
                connection.getTarget().getPort());
    }


    default int connectionBufferSize(Connection connection) {
        Optional<ToolValueAttribute> attribute = connection.getValueAttribute("buffersize");
        if (!attribute.isPresent()) {
            attribute = connection.getValueAttribute("bufferSize");
        }
        if (attribute.isPresent()) {
            return (int) backend().constants().intValue(attribute.get().getValue()).getAsLong();
        } else if (backend().context().getConfiguration().isDefined(PlatformSettings.defaultQueueDepth)) {
            return backend().context().getConfiguration().get(PlatformSettings.defaultQueueDepth);
        } else {
            return PlatformSettings.defaultQueueDepth.defaultValue(backend().context().getConfiguration());
        }
    }

    default Optional<Instance> sourceInstance(Connection connection) {

        return backend().task().getNetwork().getInstances().stream().filter(i ->
                connection.getSource().getInstance().isPresent() && i.getInstanceName().equals(
                        connection.getSource().getInstance().get()
                )
        ).findAny();
    }

    default Optional<Instance> targetInstance(Connection connection) {
        return  backend().task().getNetwork().getInstances().stream().filter(i ->
                connection.getTarget().getInstance().isPresent() && i.getInstanceName().equals(
                        connection.getTarget().getInstance().get()
                )
        ).findAny();
    }


}
