package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface Instances {
    @Binding(BindingKind.INJECTED)
    Backend backend();


    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default Declarations declarations() {
        return backend().declarations();
    }

    default Types types() {
        return backend().types();
    }

    default TypesEvaluator typeseval(){
        return backend().typeseval();
    }


    default void generateInstance(Instance instance) {
        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".c");
        emitter().open(instanceTarget);

        // -- Includes
        defineIncludes();

        // -- Ports IO
        portsIO(instance);

        // -- Constants

        // -- State
        instanceState(instance);

        // -- Callables

        // -- Prototypes
        prototypes(instance);

        // -- Port Description
        portDescription(instance);

        // -- Port Rate

        // -- Transition Description

        // -- ActorClass

        // -- Transitions

        // -- Constructor

        // -- Scheduler (aka Actor Machine )

        emitter().close();
    }

    /*
     * Includes
     */
    default void defineIncludes() {
        backend().includeUser("actors-rts.h");
        backend().includeUser("natives.h");
        backend().includeUser("globals.h");
        emitter().emitNewLine();
    }

    /*
     * Ports IO
     */
    default void portsIO(Instance instance) {
        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);

        // -- Inputs
        for (PortDecl inputPort : entityDecl.getEntity().getInputPorts()) {
            emitter().emit("#define IN%d_%s ART_INPUT(%1$d)", entityDecl.getEntity().getInputPorts().indexOf(inputPort), inputPort.getName());
        }

        // -- Outputs
        for (PortDecl outputPort : entityDecl.getEntity().getOutputPorts()) {
            emitter().emit("#define OUT%d_%s ART_OUTPUT(%1$d)", entityDecl.getEntity().getOutputPorts().indexOf(outputPort), outputPort.getName());
        }
        emitter().emitNewLine();
    }

    /*
     * Instance State
     */
    default void instanceState(Instance instance) {
        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);

        emitter().emit("// -- Instance state");
        emitter().emit("typedef struct{");
        emitter().increaseIndentation();

        emitter().emit("AbstractActorInstance base;");

        emitter().emit("int32_t program_counter;");

        // -- Check if Entity is an Actor Machine
        if (!(entityDecl.getEntity() instanceof ActorMachine)) {
            throw new UnsupportedOperationException("Entity is not an Actor Machine");
        }

        // -- Get Actor Machine
        ActorMachine am = (ActorMachine) entityDecl.getEntity();

        // -- Scopes
        for (Scope scope : am.getScopes()) {
            emitter().emit("// -- Scope %d", am.getScopes().indexOf(scope));
            for (VarDecl var : scope.getDeclarations()) {
                String decl = declarations().declaration(types().declaredType(var), var.getName());
                emitter().emit("%s;", decl);
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("} ActorInstance_%s;", backend().instaceQID(instance.getInstanceName(), "_"));
        emitter().emitNewLine();
    }

    /*
     * Callables
     */

    // TODO : Implement callables definition

    /*
     * Context/Transition/Scheduler Prototypes
     */
    default void prototypes(Instance instance) {
        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);

        // -- Check if Entity is an Actor Machine
        if (!(entityDecl.getEntity() instanceof ActorMachine)) {
            throw new UnsupportedOperationException("Entity is not an Actor Machine");
        }

        // -- Get Actor Machine
        ActorMachine am = (ActorMachine) entityDecl.getEntity();

        // -- ART Context
        emitter().emit("// -- Action Context structure");
        emitter().emit("ART_ACTION_CONTEXT(%d,%d)", am.getInputPorts().size(), am.getOutputPorts().size());
        emitter().emitNewLine();

        // -- ART Action Prototypes (aka AM Transitions)
        emitter().emit("// -- Transition prototypes");
        for(Transition transition : am.getTransitions()){
            String transitionName = instance.getInstanceName() + "_transition_" + am.getTransitions().indexOf(transition);
            String actorInstanceName = "ActorInstance_" + backend().instaceQID(instance.getInstanceName(), "_");
            emitter().emit("ART_ACTION(%s, %s);", transitionName, actorInstanceName);
        }
        emitter().emit("ART_ACTION_SCHEDULER(%s_scheduler);",backend().instaceQID(instance.getInstanceName(),"_"));
        emitter().emitNewLine();
    }

    /*
     * Port Descriptions
     */

    default void portDescription(Instance instance){
        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);

        // -- Check if Entity is an Actor Machine
        if (!(entityDecl.getEntity() instanceof ActorMachine)) {
            throw new UnsupportedOperationException("Entity is not an Actor Machine");
        }

        // -- Get Actor Machine
        ActorMachine am = (ActorMachine) entityDecl.getEntity();


        // -- Input Port Descriptions
        if(!entityDecl.getEntity().getInputPorts().isEmpty()) {
            emitter().emit("static const PortDescription inputPortDescriptions[]={");
            emitter().increaseIndentation();

            for (PortDecl inputPort : entityDecl.getEntity().getInputPorts()) {
                String type = targetEndTypeSize(new Connection.End(Optional.of(instance.getInstanceName()), inputPort.getName()));
                portDescriptionByPort(inputPort.getName(),type);
            }

            emitter().decreaseIndentation();
            emitter().emit("};");
            emitter().emitNewLine();
        }
        // -- Output Port Description
        if(!entityDecl.getEntity().getOutputPorts().isEmpty()) {
            emitter().emit("static const PortDescription outputPortDescriptions[]={");
            emitter().increaseIndentation();

            for (PortDecl outputPort : entityDecl.getEntity().getOutputPorts()) {
                Connection.End source = new Connection.End(Optional.of(instance.getInstanceName()), outputPort.getName());
                String type = sourceEndTypeSize(source);
                portDescriptionByPort(outputPort.getName(),type);
            }

            emitter().decreaseIndentation();
            emitter().emit("};");
            emitter().emitNewLine();
        }

    }

    default void portDescriptionByPort(String name, String type){
        emitter().emit("{0, \"%s\", (sizeof(%s))", name, type);
        emitter().emit("#ifdef CAL_RT_CALVIN");
        emitter().emit(", NULL");
        emitter().emit("#endif");
        emitter().emit("},");
    }

    /*
     * Utils
     */
    // TODO: Put me in a different module
    default String sourceEndTypeSize(Connection.End source) {
        Network network = backend().task().getNetwork();
        List<Connection> connections = network.getConnections().stream()
                .filter(conn -> conn.getSource().equals(source))
                .collect(Collectors.toList());
        Type type = backend().types().connectionType(network, connections.get(0));

        return typeseval().type(type);
    }

    default String targetEndTypeSize(Connection.End target) {
        Network network = backend().task().getNetwork();
        Connection connection = network.getConnections().stream()
                .filter(conn -> conn.getTarget().equals(target))
                .findFirst().get();
        Type type = backend().types().connectionType(network, connection);
        return typeseval().type(type);
    }



}
