package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default Statements statements(){
        return backend().statements();
    }

    default ChannelsUtils channelutils(){
        return backend().channelsutils();
    }

    default void generateInstance(Instance instance) {
        // -- Add instance to box
        backend().instancebox().set(instance);

        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add entity to box
        backend().entitybox().set(entity);

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".c");
        emitter().open(instanceTarget);

        // -- Includes
        defineIncludes();

        // -- Ports IO
        portsIO(entity);

        // -- Constants

        // -- State
        instanceState(instanceName, entity);

        // -- Callables

        // -- Prototypes
        prototypes(instanceName, entity);

        // -- Port Description
        portDescription(instanceName, entity);

        // -- Port Rate
        portRate(instanceName, entity);

        // -- Action/Transitions Description
        acttransDescription(instanceName, entity);

        // -- ART ActorClass
        actorClass(instanceName, entity);

        // -- Actions/Transitions
        acttransDefinitions(instanceName, entity);
        // -- Constructor

        // -- Scheduler (aka Actor Machine )

        emitter().close();

        // -- Clear boxes
        backend().instancebox().clear();
        backend().entitybox().clear();
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
    default void portsIO(Entity entity) {


        // -- Inputs
        for (PortDecl inputPort : entity.getInputPorts()) {
            emitter().emit("#define IN%d_%s ART_INPUT(%1$d)", entity.getInputPorts().indexOf(inputPort), inputPort.getName());
        }

        // -- Outputs
        for (PortDecl outputPort : entity.getOutputPorts()) {
            emitter().emit("#define OUT%d_%s ART_OUTPUT(%1$d)", entity.getOutputPorts().indexOf(outputPort), outputPort.getName());
        }
        emitter().emitNewLine();
    }

    /*
     * Instance State
     */

    void instanceState(String instanceName, Entity entity);

    default void instanceState(String instanceName, ActorMachine am) {
        emitter().emit("// -- Instance state");
        emitter().emit("typedef struct{");
        emitter().increaseIndentation();

        emitter().emit("AbstractActorInstance base;");

        emitter().emit("int32_t program_counter;");


        // -- Scopes
        for (Scope scope : am.getScopes()) {
            emitter().emit("// -- Scope %d", am.getScopes().indexOf(scope));
            for (VarDecl var : scope.getDeclarations()) {
                String decl = declarations().declaration(types().declaredType(var), var.getName());
                emitter().emit("%s;", decl);
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("} ActorInstance_%s;", backend().instaceQID(instanceName, "_"));
        emitter().emitNewLine();
    }


    /*
     * Callables
     */

    // TODO : Implement callables definition

    /*
     * Context/Transition/Scheduler Prototypes
     */
    void prototypes(String instanceName, Entity entity);


    default void prototypes(String instanceName, ActorMachine am) {


        // -- ART Context
        emitter().emit("// -- Action Context structure");
        emitter().emit("ART_ACTION_CONTEXT(%d,%d)", am.getInputPorts().size(), am.getOutputPorts().size());
        emitter().emitNewLine();

        // -- ART Action Prototypes (aka AM Transitions)
        emitter().emit("// -- Transition prototypes");
        for (Transition transition : am.getTransitions()) {
            String transitionName = instanceName + "_transition_" + am.getTransitions().indexOf(transition);
            String actorInstanceName = "ActorInstance_" + backend().instaceQID(instanceName, "_");
            emitter().emit("ART_ACTION(%s, %s);", transitionName, actorInstanceName);
        }
        emitter().emit("ART_ACTION_SCHEDULER(%s_scheduler);", backend().instaceQID(instanceName, "_"));
        emitter().emitNewLine();
    }

    /*
     * Port Descriptions
     */

    default void portDescription(String instanceName, Entity entity) {
        emitter().emit("// -- Input & Output Port Description");

        // -- Input Port Descriptions
        if (!entity.getInputPorts().isEmpty()) {
            emitter().emit("static const PortDescription inputPortDescriptions[]={");
            emitter().increaseIndentation();

            for (PortDecl inputPort : entity.getInputPorts()) {
                String type = channelutils().targetEndTypeSize(new Connection.End(Optional.of(instanceName), inputPort.getName()));
                portDescriptionByPort(inputPort.getName(), type);
            }

            emitter().decreaseIndentation();
            emitter().emit("};");
            emitter().emitNewLine();
        }
        // -- Output Port Description
        if (!entity.getOutputPorts().isEmpty()) {
            emitter().emit("static const PortDescription outputPortDescriptions[]={");
            emitter().increaseIndentation();

            for (PortDecl outputPort : entity.getOutputPorts()) {
                Connection.End source = new Connection.End(Optional.of(instanceName), outputPort.getName());
                String type = channelutils().sourceEndTypeSize(source);
                portDescriptionByPort(outputPort.getName(), type);
            }

            emitter().decreaseIndentation();
            emitter().emit("};");
            emitter().emitNewLine();
        }

    }

    default void portDescriptionByPort(String name, String type) {
        emitter().emit("{0, \"%s\", (sizeof(%s))", name, type);
        emitter().emit("#ifdef CAL_RT_CALVIN");
        emitter().emit(", NULL");
        emitter().emit("#endif");
        emitter().emit("},");
    }

    /*
     * Port Rate
     */
    void portRate(String instanceName, Entity entity);

    default void portRate(String instanceName, ActorMachine am) {
        emitter().emit("// -- Input/ Output Port Rate by Transition");
        for (Transition transition : am.getTransitions()) {
            List<String> inputRates = new ArrayList<>(am.getInputPorts().size());
            List<String> outputRates = new ArrayList<>(am.getOutputPorts().size());
            // --Inputs
            for (PortDecl inputPort : am.getInputPorts()) {
                Port port = transition.getInputRates().entrySet().stream().filter(e -> e.getKey().getName().equals(inputPort.getName()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);

                if (port != null) {
                    Integer rate = transition.getInputRate(port);
                    inputRates.add(String.valueOf(rate));
                } else {
                    inputRates.add("0");
                }
            }

            // --Outputs
            for (PortDecl outputPort : am.getOutputPorts()) {
                Port port = transition.getOutputRates().entrySet().stream().filter(e -> e.getKey().getName().equals(outputPort.getName()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);

                if (port != null) {
                    Integer rate = transition.getOutputRate(port);
                    outputRates.add(String.valueOf(rate));
                } else {
                    outputRates.add("0");
                }
            }

            String transitionName = instanceName + "_transition_" + am.getTransitions().indexOf(transition);
            emitter().emit("static const int portRate_in_%s[] = {%s};", transitionName, String.join(", ", inputRates));
            emitter().emitNewLine();
            emitter().emit("static const int portRate_out_%s[] = {%s};", transitionName, String.join(", ", outputRates));
            emitter().emitNewLine();
        }
    }

    /*
     * Transitions Description
     */

    void acttransDescription(String instanceName, Entity entity);

    default void acttransDescription(String instanceName, ActorMachine am) {
        emitter().emit("// -- Transitions Description");
        emitter().emit("static const ActionDescription actionDescriptions[] = {");
        emitter().increaseIndentation();

        for (Transition transition : am.getTransitions()) {
            String transitionName = instanceName + "_transition_" + am.getTransitions().indexOf(transition);
            emitter().emit("{\"%s\", portRate_in_%1$s, portRate_out_%1$s},", transitionName);
        }

        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();
    }

    /*
     * ART Actor Class
     */

    void actorClass(String instanceName, Entity entity);

    default void actorClass(String instanceName, ActorMachine am) {
        String instanceQID = backend().instaceQID(instanceName, "_");
        emitter().emit("// -- Actor Class");

        emitter().emit("#ifdef CAL_RT_CALVIN");
        emitter().emit("ActorClass klass");
        emitter().emit("#else");
        emitter().emit("ActorClass ActorClass_%s", instanceQID);
        emitter().emit("#endif");
        emitter().increaseIndentation();
        emitter().emit("= INIT_ActorClass(");
        emitter().increaseIndentation();
        emitter().emit("\"%s\",", instanceQID);
        emitter().emit("ActorInstance_%s,", instanceQID);
        emitter().emit("ActorInstance_%s_constructor,", instanceQID);
        emitter().emit("0, // -- setParam not needed anymore (we instantiate with params)");
        emitter().emit("%s_scheduler,", instanceQID);
        emitter().emit("0, // -- no destructor");
        emitter().emit("%d, inputPortDescriptions,", am.getInputPorts().size());
        emitter().emit("%d, outputPortDescriptions,", am.getOutputPorts().size());
        emitter().emit("%d, actionDescriptions", am.getTransitions().size());

        emitter().decreaseIndentation();
        emitter().emit(");");
        emitter().decreaseIndentation();
        emitter().emitNewLine();
    }


    /*
     * Action / Transition Definitions
     */

    void acttransDefinitions(String instanceName, Entity entity);

    default void acttransDefinitions(String instanceName, ActorMachine am) {
        emitter().emit("// -- Transitions Definitions");
        for (Transition transition : am.getTransitions()) {
            String instanceQID = backend().instaceQID(instanceName, "_");
            emitter().emit("ART_ACTION(%s_transition_%d, ActorInstance_%s){", instanceName, am.getTransitions().indexOf(transition), instanceQID);
            emitter().increaseIndentation();

            emitter().emit("ART_ACTION_ENTER(%s_transition_%d, %2$d);", instanceName, am.getTransitions().indexOf(transition));

            transition.getBody().forEach(statements()::execute);

            emitter().emit("ART_ACTION_EXIT(%s_transition_%d, %2$d);", instanceName, am.getTransitions().indexOf(transition));

            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emitNewLine();
        }
    }

}
