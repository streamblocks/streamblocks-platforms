package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.ValueParameter;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.nio.file.Path;
import java.util.*;

@Module
public interface Main {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default ExpressionEvaluator evaluator() {
        return backend().expressioneval();
    }

    default void main() {
        Path mainTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve("main.c");
        emitter().open(mainTarget);
        backend().includeUser("actors-rts.h");
        backend().includeUser("natives.h");
        backend().includeUser("globals.h");
        emitter().emitNewLine();
        // -- Init Network function
        initNetwork();
        emitter().emitNewLine();

        // -- Main function
        emitter().emit("int main(int argc, char *argv[]) {");
        emitter().increaseIndentation();

        emitter().emit("int numberOfInstances;");
        emitter().emit("AbstractActorInstance **instances;");
        emitter().emit("initNetwork(&instances, &numberOfInstances);");
        emitter().emit("return executeNetwork(argc, argv, instances, numberOfInstances);");

        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().close();
    }

    default void initNetwork() {
        emitter().emit("static void initNetwork(AbstractActorInstance ***pInstances, int *pNumberOfInstances) {");
        emitter().increaseIndentation();
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
            String joinQID = backend().instaceQID(instance.getInstanceName(), "_");
            instanceQIDs.put(instance, joinQID);

            if (!entityDecl.getExternal()) {
                instanceActorClasses.put(instance, joinQID);
            } else {
                instanceActorClasses.put(instance, instance.getEntityName().getLast().toString());
            }
        }


        emitter().emit("int numberOfInstances = %d;", network.getInstances().size());
        emitter().emitNewLine();
        emitter().emit("AbstractActorInstance **actorInstances = (AbstractActorInstance **) malloc(numberOfInstances * sizeof(AbstractActorInstance *));");
        emitter().emit("*pInstances = actorInstances;");
        emitter().emit("*pNumberOfInstances = numberOfInstances;");
        emitter().emitNewLine();

        emitter().emit("// -- Instances declaration");
        for (Instance instance : network.getInstances()) {
            // -- Get global entity declaration
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String joinQID = instanceQIDs.get(instance);
            String actorClassName = instanceActorClasses.get(instance);

            emitter().emit("extern ActorClass ActorClass_%s;", actorClassName);
            emitter().emit("AbstractActorInstance *%s;", joinQID);


            for (PortDecl inputPort : entityDecl.getEntity().getInputPorts()) {
                emitter().emit("InputPort *%s_%s;", joinQID, inputPort.getName());
            }
            for (PortDecl outputPort : entityDecl.getEntity().getOutputPorts()) {
                emitter().emit("OutputPort *%s_%s;", joinQID, outputPort.getName());
            }
            emitter().emitNewLine();
        }

        emitter().emit("// -- Instances instantiation");
        for (Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String joinQID = instanceQIDs.get(instance);
            String actorClass = instanceActorClasses.get(instance);
            emitter().emit("%s = createActorInstance(&ActorClass_%s);", joinQID, actorClass);
            // -- Instantiate Parameters
            for (ValueParameter parameter : instance.getValueParameters()) {
                emitter().emit("setParameter(%s, \"%s\", \"%s\");", joinQID, parameter.getName(), evaluator().evaluate(parameter.getValue()).replaceAll("^\"|\"$", ""));
            }

            // -- Instantiate instance ports
            for (PortDecl inputPort : entityDecl.getEntity().getInputPorts()) {
                emitter().emit("%s_%s = createInputPort(%1$s, \"%2$s\", %d);", joinQID, inputPort.getName(), 4096);
            }
            for (PortDecl outputPort : entityDecl.getEntity().getOutputPorts()) {
                Connection.End end = new Connection.End(Optional.of(instance.getInstanceName()), outputPort.getName());
                List<Connection.End> outgoing = srcToTgt.getOrDefault(end, Collections.emptyList());
                emitter().emit("%s_%s = createOutputPort(%1$s, \"%2$s\", %d);", joinQID, outputPort.getName(), outgoing.size());
            }

            emitter().emit("actorInstances[%s] = %s;", network.getInstances().indexOf(instance), joinQID);
            emitter().emitNewLine();
        }

        // -- Connections
        emitter().emit("// -- Connections");
        for (Connection connection : connections) {
            // -- Source instance
            String srcInstanceName = connection.getSource().getInstance().get();
            Instance srcInstance = network.getInstances().stream().
                    filter(p -> p.getInstanceName().equals(srcInstanceName)).
                    findAny().orElse(null);
            String srcJoinQID = backend().instaceQID(srcInstanceName, "_");

            // -- Target instance
            String tgtInstanceName = connection.getTarget().getInstance().get();
            Instance tgtInstance = network.getInstances().stream().
                    filter(p -> p.getInstanceName().equals(tgtInstanceName)).
                    findAny().orElse(null);
            String tgtJoinQID = backend().instaceQID(tgtInstanceName, "_");


            emitter().emit("connectPorts(%s_%s, %s_%s);", srcJoinQID, connection.getSource().getPort(), tgtJoinQID, connection.getTarget().getPort());
        }

        emitter().emit("// -- Initialize Global Variables");
        emitter().emit("init_global_variables();");

        emitter().decreaseIndentation();
        emitter().emit("}");

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
