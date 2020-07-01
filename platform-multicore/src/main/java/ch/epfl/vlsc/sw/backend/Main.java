package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import ch.epfl.vlsc.sw.ir.PartitionLink;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.ir.ValueParameter;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.nio.file.Path;
import java.util.*;

@Module
public interface Main {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default ExpressionEvaluator evaluator() {
        return backend().expressionEval();
    }

    default void main() {


        boolean isSimulated =
                backend().context().getConfiguration().isDefined(PlatformSettings.PartitionNetwork) &&
                        backend().context().getConfiguration().get(PlatformSettings.PartitionNetwork) &&
                        backend().context().getConfiguration().isDefined(PlatformSettings.enableSystemC) &&
                        backend().context().getConfiguration().get(PlatformSettings.enableSystemC);

        Path mainTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve("main.cc");
        emitter().open(mainTarget);
        backend().includeUser("actors-rts.h");
        backend().includeUser("natives.h");
        backend().includeUser("globals.h");
        if (isSimulated)
            backend().includeUser("systemc.h");
        emitter().emitNewLine();
        // -- Init Network function
        initNetwork();
        emitter().emitNewLine();

        // -- Main function

        if (isSimulated)
            emitter().emit("int sc_main(int argc, char *argv[]) {");
        else
            emitter().emit("int main(int argc, char *argv[]) {");
        emitter().increaseIndentation();
        emitter().emit("RuntimeOptions *options = (RuntimeOptions *) calloc(1, sizeof(RuntimeOptions));");
        emitter().emit("int numberOfInstances;");
        emitter().emitNewLine();
        emitter().emit("pre_parse_args(argc, argv, options);");
        emitter().emit("AbstractActorInstance **instances;");
        emitter().emit("initNetwork(&instances, &numberOfInstances, options);");
        emitter().emit("return executeNetwork(argc, argv, options, instances, numberOfInstances);");

        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().close();
    }

    default void initNetwork() {
        emitter().emit("static void initNetwork(AbstractActorInstance ***pInstances, int *pNumberOfInstances, RuntimeOptions *options) {");
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
            String joinQID = instance.getInstanceName();
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
            if (entityDecl.getEntity() instanceof PartitionLink) {
                emitter().emit("if(options->vcd_trace_level != NULL)");
                emitter().increaseIndentation();
                emitter().emit("setParameter(%s, \"vcd-trace-level\", options->vcd_trace_level);", joinQID);
                emitter().decreaseIndentation();
                emitter().emit("if(options->hardwareProfileFileName != NULL)");
                emitter().increaseIndentation();
                emitter().emit("setParameter(%s, \"profile-file-name\", options->hardwareProfileFileName);", joinQID);
                emitter().decreaseIndentation();
            }
            for (ValueParameter parameter : instance.getValueParameters()) {
                if (entityDecl.getExternal()) {
                    emitter().emit("setParameter(%s, \"%s\", \"%s\");", joinQID, parameter.getName(), evaluator().evaluate(parameter.getValue()).replaceAll("^\"|\"$", ""));
                }
            }

            // -- Instantiate instance ports
            for (PortDecl inputPort : entityDecl.getEntity().getInputPorts()) {


                int bufferSize = backend().channelsutils().targetEndSize(new Connection.End(Optional.of(instance.getInstanceName()), inputPort.getName()));
                emitter().emit("%s_%s = createInputPort(%1$s, \"%2$s\", %d);", joinQID, inputPort.getName(), bufferSize);

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
            String srcJoinQID = srcInstanceName;

            // -- Target instance
            String tgtInstanceName = connection.getTarget().getInstance().get();
            Instance tgtInstance = network.getInstances().stream().
                    filter(p -> p.getInstanceName().equals(tgtInstanceName)).
                    findAny().orElse(null);
            String tgtJoinQID = tgtInstanceName;


            emitter().emit("connectPorts(%s_%s, %s_%s);", srcJoinQID, connection.getSource().getPort(), tgtJoinQID, connection.getTarget().getPort());
        }
        emitter().emitNewLine();

        emitter().emit("// -- Initialize Global Variables");
        emitter().emit("init_global_variables();");

        emitter().decreaseIndentation();
        emitter().emit("}");

    }

}
