package ch.epfl.vlsc.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.mlir.backend.ExpressionEvaluator;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.ir.ValueParameter;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.expr.ExprGlobalVariable;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.nio.file.Path;
import java.util.*;

@Module
public interface Main {

    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default Emitter emitter() {
        return backend().emitter();
    }

    default ExpressionEvaluator evaluator() {
        return backend().expressionEval();
    }

    default void main(){
        Path mainTarget = PathUtils.getTargetCodeGen(backend().context()).resolve("main.mlir");
        emitter().open(mainTarget);
        emitter().emit("//open main {");
        initNetwork();
        emitter().emit("//close main }");
        emitter().close();
    }

    default void initNetwork() {
        // -- Network
        Network network = backend().task().getNetwork();

        // -- Connections
        List<Connection> connections = network.getConnections();
        Map<Connection.End, List<Connection.End>> srcToTgt = new HashMap<>();

        for (Connection connection : connections) {
            Connection.End src = connection.getSource();
            Connection.End tgt = connection.getTarget();
            srcToTgt.computeIfAbsent(src, x -> new ArrayList<>())
                    .add(tgt);
        }

        // -- JoinQID & ActorClass names
        Map<Instance, String> instanceQIDs = new HashMap<>();
        Map<Instance, String> instanceActorClasses = new HashMap<>();
        for (Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String joinQID = instance.getInstanceName();
            instanceQIDs.put(instance, joinQID);

            if (!entityDecl.getExternal()) {
                instanceActorClasses.put(instance, joinQID);
            } else {
                instanceActorClasses.put(instance, entityDecl.getOriginalName());
            }
        }

        emitter().emit("// -- Instances declaration");
        for (Instance instance : network.getInstances()) {
            // -- Get global entity declaration
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String joinQID = instanceQIDs.get(instance);
            String actorClassName = instanceActorClasses.get(instance);

            emitter().emit("Actor name: %s of type:  %s;", actorClassName, entityDecl.getName());

            for (PortDecl inputPort : entityDecl.getEntity().getInputPorts()) {
                if (backend().channelsutils().isTargetConnected(instance.getInstanceName(), inputPort.getName())) {
                    emitter().emit("InputPort %s.%s;", joinQID, inputPort.getName());
                }
            }
            for (PortDecl outputPort : entityDecl.getEntity().getOutputPorts()) {
                if (backend().channelsutils().isSourceConnected(instance.getInstanceName(), outputPort.getName())) {
                    emitter().emit("OutputPort %s.%s;", joinQID, outputPort.getName());
                }
            }
        }

        emitter().emit("// -- Instances instantiation");
        /*for (Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String joinQID = instanceQIDs.get(instance);
            String actorClass = instanceActorClasses.get(instance);
            emitter().emit("%s = createActorInstance(&ActorClass_%s);", joinQID, actorClass);
            emitter().emit("%s->name = (char *) calloc(%d, sizeof(char));", joinQID, joinQID.length() + 1);
            emitter().emit("strcpy(%s->name, \"%1$s\");", joinQID);
            for (ValueParameter parameter : instance.getValueParameters()) {
                if (entityDecl.getExternal()) {
                    if (parameter.getValue() instanceof ExprGlobalVariable) {
                        emitter().emit("setParameter(%s, \"%s\", %s);", joinQID, parameter.getName(), evaluator().evaluate(parameter.getValue()));
                    } else {
                        emitter().emit("setParameter(%s, \"%s\", \"%s\");", joinQID, parameter.getName(), evaluator().evaluate(parameter.getValue()).replaceAll("^\"|\"$", ""));
                    }
                }
            }

            // -- Instantiate instance ports
            for (PortDecl inputPort : entityDecl.getEntity().getInputPorts()) {
                if (backend().channelsutils().isTargetConnected(instance.getInstanceName(), inputPort.getName())) {
                    int bufferSize = backend().channelsutils().targetEndSize(new Connection.End(Optional.of(instance.getInstanceName()), inputPort.getName()));
                    emitter().emit("%s_%s = createInputPort(%1$s, \"%2$s\", use_default == 1 ? default_buffer_depth : %d);", joinQID, inputPort.getName(), bufferSize);
                }
            }
            for (PortDecl outputPort : entityDecl.getEntity().getOutputPorts()) {
                if (backend().channelsutils().isSourceConnected(instance.getInstanceName(), outputPort.getName())) {
                    Connection.End end = new Connection.End(Optional.of(instance.getInstanceName()), outputPort.getName());
                    List<Connection.End> outgoing = srcToTgt.getOrDefault(end, Collections.emptyList());
                    emitter().emit("%s_%s = createOutputPort(%1$s, \"%2$s\", %d);", joinQID, outputPort.getName(), outgoing.size());
                }
            }

            emitter().emit("actorInstances[%s] = %s;", network.getInstances().indexOf(instance), joinQID);
            emitter().emitNewLine();
        }*/

        // -- Connections
        emitter().emit("// -- Connections");
        for (Connection connection : connections) {
            // -- Source instance
            String srcInstanceName = connection.getSource().getInstance().get();
            String srcJoinQID = srcInstanceName;

            // -- Target instance
            String tgtInstanceName = connection.getTarget().getInstance().get();
            String tgtJoinQID = tgtInstanceName;


            emitter().emit("%s_%s->%s_%s;", srcJoinQID, connection.getSource().getPort(), tgtJoinQID, connection.getTarget().getPort());
        }

    }
}
