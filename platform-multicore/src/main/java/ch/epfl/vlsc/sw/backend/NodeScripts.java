package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.ir.ValueParameter;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Module
public interface NodeScripts {
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

    default void scriptNetwork() {
        Path script = PathUtils.getTargetBin(backend().context()).resolve(backend().task().getIdentifier().getLast().toString() + ".script");
        emitter().open(script);

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

        // -- Load
        for (Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String actorClass = instanceActorClasses.get(instance);

            if (!entityDecl.getExternal()) {
                emitter().emit("LOAD ./modules/%s", actorClass);
            }
        }
        emitter().emitNewLine();

        // -- New instance
        for (Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String actorClass = instance.getEntityName().toString();
            String joinQID = instanceQIDs.get(instance);
            String parameters = "";
            for (ValueParameter p : instance.getValueParameters()) {
                String value = String.format("%s=\"%s\"", p.getName(), evaluator().evaluate(p.getValue()).replaceAll("^[\"']+|[\"']+$", ""));
                parameters += value + " ";
            }


            emitter().emit("NEW %s %s %s", actorClass, joinQID, parameters);
        }
        emitter().emitNewLine();

        // -- Connections
        for (Connection connection : connections) {
            if (!connection.getSource().getInstance().isPresent() || !connection.getTarget().getInstance().isPresent())
                continue;
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

            emitter().emit("CONNECT %s.%s %s.%s", srcJoinQID, connection.getSource().getPort(), tgtJoinQID, connection.getTarget().getPort());
        }
        emitter().emitNewLine();

        // -- ENABLE
        emitter().emit("ENABLE %s", instanceQIDs.entrySet().stream().map(i -> i.getValue()).collect(java.util.stream.Collectors.joining(" ")));
        emitter().emitNewLine();

        // -- JOIN
        emitter().emit("JOIN");

        emitter().close();
    }

    default void pythonScriptNode() {
        Path script = PathUtils.getTargetBin(backend().context()).resolve(backend().task().getIdentifier().getLast().toString() + ".py");
        emitter().open(script);

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

        emitter().emit("import streamblocks");
        emitter().emit("import time");
        emitter().emitNewLine();

        emitter().emit("# expects a streamblocks node to be running at localhost:9000");
        emitter().emitNewLine();

        emitter().emit("n = streamblocks.Node(\"localhost\", 9000)");
        emitter().emitNewLine();


        // -- Load
        for (Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String actorClass = instanceActorClasses.get(instance);

            if (!entityDecl.getExternal()) {
                emitter().emit("n.load(\"./modules/%s\")", actorClass);
            }
        }
        emitter().emitNewLine();

        // -- New instance
        for (Instance instance : network.getInstances()) {
            GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
            String actorClass = instance.getEntityName().toString();
            String joinQID = instanceQIDs.get(instance);
            List<String> parameters = new ArrayList<>();

            // -- add actorClass
            //parameters.add(actorClass);

            for (ValueParameter p : instance.getValueParameters()) {
                String value = String.format("%s=\"%s\"", p.getName(), evaluator().evaluate(p.getValue()).replaceAll("^[\"']+|[\"']+$", ""));
                parameters.add(value);
            }

            String arguments = String.join(", ", parameters);

            emitter().emit("%s = n.new(\"%s\", \"%s\"%s)", joinQID, actorClass, joinQID, arguments.isEmpty() ? "" : ", " + arguments);
        }
        emitter().emitNewLine();


        // -- Connections
        for (Connection connection : connections) {
            if (!connection.getSource().getInstance().isPresent() || !connection.getTarget().getInstance().isPresent())
                continue;
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

            emitter().emit("%s.%s >> %s.%s", srcJoinQID, connection.getSource().getPort(), tgtJoinQID, connection.getTarget().getPort());
        }
        emitter().emitNewLine();

        // -- Actors

        emitter().emit("actors = (%s)", instanceQIDs.entrySet().stream().map(i -> i.getValue()).collect(java.util.stream.Collectors.joining(", ")));
        emitter().emitNewLine();

        // -- Enable
        emitter().emit("for actor in actors:");
        emitter().increaseIndentation();
        emitter().emit("actor.enable()");
        emitter().decreaseIndentation();
        emitter().emitNewLine();

        // -- Sleep
        emitter().emit("time.sleep(5)");
        emitter().emitNewLine();


        // -- Disable
        emitter().emit("for actor in actors:");
        emitter().increaseIndentation();
        emitter().emit("actor.disable()");
        emitter().decreaseIndentation();
        emitter().emitNewLine();

        // -- Destroy
        emitter().emit("for actor in actors:");
        emitter().increaseIndentation();
        emitter().emit("actor.destroy()");
        emitter().decreaseIndentation();
        emitter().emitNewLine();

        emitter().close();
    }


}
