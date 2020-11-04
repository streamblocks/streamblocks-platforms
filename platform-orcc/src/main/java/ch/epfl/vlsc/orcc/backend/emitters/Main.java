package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Module
public interface Main {

    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default ExpressionEvaluator expressionEval() {
        return backend().expressionEval();
    }

    default void getTop() {
        String topName = backend().task().getIdentifier().getLast().toString();
        // -- Create file
        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve(topName + ".c"));

        // -- Info
        emitter().emit("// -- Generated from \"%s\"", backend().task().getIdentifier().toString());
        emitter().emitNewLine();

        // -- Includes
        includes();

        // -- Fifo allocation and pointer assignment
        fifoAllocationAndPointerAssignment();

        // -- Actor functions
        actorFunctions();

        // -- Actors array
        actorsArray();

        // -- Connections arrays
        connectionsArray();

        // -- Network Declaration
        networkDeclaration();

        // -- main
        main();

        // -- EOF
        emitter().close();
    }

    default void includes() {
        backend().includeSystem("locale.h");
        backend().includeSystem("stdio.h");
        backend().includeSystem("stdlib.h");
        emitter().emitNewLine();

        backend().includeUser("types.h");
        backend().includeUser("fifo.h");
        backend().includeUser("util.h");
        backend().includeUser("dataflow.h");
        backend().includeUser("serialize.h");
        backend().includeUser("options.h");
        backend().includeUser("scheduler.h");
        emitter().emitNewLine();
    }

    @Binding(BindingKind.LAZY)
    default Map<Connection.End, Integer> connectionEndNbrReaders(){
        return new HashMap<>();
    }

    @Binding(BindingKind.LAZY)
    default Map<Connection.End, Integer> connectionId() {
        return new HashMap<>();
    }

    default void fifoAllocationAndPointerAssignment() {
        emitter().emitClikeBlockComment("FIFO Allocation");
        Network network = backend().task().getNetwork();
        //Map<Connection.End, Integer> connectionEndNbrReaders = new HashMap<>();
        //Map<Connection.End, Integer> connectionId = new HashMap<>();
        int countId = 0;
        for (Connection connection : network.getConnections()) {
            Connection.End source = connection.getSource();
            Optional<Connection.End> f = connectionEndNbrReaders().keySet()
                    .stream()
                    .filter(e -> e.getInstance().equals(source.getInstance()) && e.getPort().equals(source.getPort()))
                    .findAny();
            if (!f.isPresent()) {
                connectionEndNbrReaders().put(source, 1);
                connectionId().put(source, countId);
                countId++;
            } else {
                Connection.End c = f.get();
                Integer r = connectionEndNbrReaders().get(c) + 1;
                connectionEndNbrReaders().put(c, r);
            }
        }

        for (Connection.End c : connectionId().keySet()) {
            emitter().emit("DECLARE_FIFO(%s, %d, %d, %d)",
                    backend().typeseval().type(backend().channelUtils().sourceEndType(c)),
                    backend().channelUtils().connectionBufferSize(backend().channelUtils().targetEndConnections(c).get(0)),
                    connectionId().get(c),
                    connectionEndNbrReaders().get(c)
            );
        }
        emitter().emitNewLine();

        emitter().emitClikeBlockComment("FIFO pointer assignments");

        for (Connection.End source : connectionId().keySet()) {
            int id = connectionId().get(source);
            String type = backend().typeseval().type(backend().channelUtils().sourceEndType(source));
            List<Connection> targetConnections = backend().channelUtils().targetEndConnections(source);
            emitter().emit("fifo_%s_t *%s_%s = &fifo_%d;", type, backend().instaceQID(source.getInstance().get(), "_"), source.getPort(), id);
            for(Connection c : targetConnections){
                emitter().emit("fifo_%s_t *%s_%s = &fifo_%d;", type, backend().instaceQID(c.getTarget().getInstance().get(), "_"), c.getTarget().getPort(), id);
            }
            emitter().emitNewLine();
        }

    }

    default void actorFunctions() {
        emitter().emitClikeBlockComment("Actor functions");
        Network network = backend().task().getNetwork();
        network.getInstances().forEach(i -> {
            emitter().emit("extern void %s_initialize(schedinfo_t *si);", backend().instaceQID(i.getInstanceName(), "_"));
            emitter().emit("extern void %s_scheduler(schedinfo_t *si);", backend().instaceQID(i.getInstanceName(), "_"));
        });
        emitter().emitNewLine();
    }

    default void actorsArray() {
        emitter().emitClikeBlockComment("Declaration of the actors array");

        Network network = backend().task().getNetwork();
        network.getInstances().forEach(i -> {
            emitter().emit("actor_t %s = {\"%1$s\", %1$s_initialize, %1$s_scheduler, 0, 0, 0, 0, NULL, -1, %d, 0, 1, 0, 0, 0, NULL, 0, 0, \"\", 0, 0, 0};",
                    backend().instaceQID(i.getInstanceName(), "_"), network.getInstances().indexOf(i));
        });
        emitter().emitNewLine();

        emitter().emit("actor_t *actors[] = {");
        {
            emitter().increaseIndentation();

            network.getInstances().forEach(i -> {
                emitter().emit("&%s%s",
                        backend().instaceQID(i.getInstanceName(), "_"),
                        network.getInstances().indexOf(i) != network.getInstances().size() - 1 ? "," : "");
            });

            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();

    }

    default void connectionsArray() {
        emitter().emitClikeBlockComment("Declaration of the connections array");
        Network network = backend().task().getNetwork();
        for (Connection connection : network.getConnections()) {
            Connection.End source = connection.getSource();
            Connection.End target = connection.getTarget();
            emitter().emit("connection_t connection_%s_%s = {&%s, &%s, 0, 0};",
                    backend().instaceQID(target.getInstance().get(), "_"),
                    target.getPort(),
                    backend().instaceQID(source.getInstance().get(), "_"),
                    backend().instaceQID(target.getInstance().get(), "_"));
        }
        emitter().emitNewLine();

        emitter().emit("connection_t *connections[] = {");
        {
            emitter().increaseIndentation();
            for (Connection connection : network.getConnections()) {
                Connection.End target = connection.getTarget();
                if (network.getConnections().indexOf(connection) == network.getConnections().size() - 1) {
                    emitter().emit("&connection_%s_%s", backend().instaceQID(target.getInstance().get(), "_"), target.getPort());
                } else {
                    emitter().emit("&connection_%s_%s,", backend().instaceQID(target.getInstance().get(), "_"), target.getPort());
                }
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();
    }

    default void networkDeclaration() {
        emitter().emitClikeBlockComment("Declaration of the network");
        String networkQID = backend().task().getIdentifier().toString();
        Network network = backend().task().getNetwork();
        emitter().emit("network_t network = {\"%s\", actors, connections, %d, %d};", networkQID, network.getInstances().size(), network.getConnections().size());

    }

    default void main() {
        emitter().emitClikeBlockComment("Main");
        emitter().emit("int main(int argc, char *argv[]){");
        {
            emitter().increaseIndentation();

            emitter().emit("options_t *opt = init_orcc(argc, argv);");
            emitter().emit("set_scheduling_strategy(\"RR\", opt);");
            emitter().emitNewLine();

            emitter().emit("launcher(opt, &network);");
            emitter().emitNewLine();

            emitter().emit("return compareErrors;");

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }


}
