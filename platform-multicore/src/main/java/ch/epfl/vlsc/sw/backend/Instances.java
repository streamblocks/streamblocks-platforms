package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
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
import se.lth.cs.tycho.ir.entity.am.*;
import se.lth.cs.tycho.ir.expr.ExprInput;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Module
public interface Instances {
    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();


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

    default Statements statements() {
        return backend().statements();
    }

    default ChannelsUtils channelutils() {
        return backend().channelsutils();
    }

    default ExpressionEvaluator expressioneval() {
        return backend().expressioneval();
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
        Path instanceTarget;
        if(backend().context().getConfiguration().get(PlatformSettings.runOnNode)){
            instanceTarget = PathUtils.getTargetCodeGenSourceCC(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".cc");
        }else{
            instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".c");
        }

        emitter().open(instanceTarget);

        // -- Includes
        defineIncludes();

        // -- Ports IO
        portsIO(entity);

        // -- Context
        actionContext(instanceName, entity);

        // -- State
        instanceState(instanceName, entity);

        // -- Prototypes
        prototypes(instanceName, entity);

        // -- Callables
        callables(instanceName, entity);

        // -- Port Description
        portDescription(instanceName, entity);

        // -- Port Rate
        portRate(instanceName, entity);

        // -- Action/Transitions Description
        acttransDescription(instanceName, entity);

        // -- Scopes
        if (entity instanceof ActorMachine) {
            scopes(instanceName, (ActorMachine) entity);
        }

        // -- Conditions
        if (entity instanceof ActorMachine) {
            conditions(instanceName, (ActorMachine) entity);
        }

        // -- ART ActorClass
        actorClass(instanceName, entity);

        // -- Actions/Transitions
        acttransDefinitions(instanceName, entity);

        // -- Constructor
        constructorDefinition(instanceName, entity);

        // -- Destructor
        destructorDefinition(instanceName, entity);

        // -- Scheduler (aka Actor Machine )
        scheduler(instanceName, entity);

        // -- EOF
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
        backend().includeSystem("stdbool.h");
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
     * Action Context
     */

    void actionContext(String instanceName, Entity entity);

    default void actionContext(String instanceName, ActorMachine am) {
        // -- ART Context
        emitter().emit("// -- Action Context structure");
        emitter().emit("ART_ACTION_CONTEXT(%d,%d)", am.getInputPorts().size(), am.getOutputPorts().size());
        emitter().emitNewLine();
    }


    /*
     * Callables
     */

    void callables(String instanceName, Entity entity);

    default void callables(String instanceName, ActorMachine am) {
        emitter().emit("// -- Callables");
        for (Scope scope : am.getScopes()) {
            if (scope.isPersistent()) {
                for (VarDecl decl : scope.getDeclarations()) {
                    if (decl.getValue() != null) {
                        Expression expr = decl.getValue();
                        if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                            backend().callablesInActor().callableDefinition(instanceName, expr);
                            emitter().emitNewLine();
                        }
                    }
                }
            }
        }
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
            //backend().callables().declareEnvironmentForCallablesInScope(scope);
            for (VarDecl var : scope.getDeclarations()) {
                if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                    // -- Do nothing
                } else {
                    String decl = declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                    emitter().emit("%s;", decl);
                }

            }
        }

        emitter().decreaseIndentation();
        emitter().emit("} ActorInstance_%s;", backend().instaceQID(instanceName, "_"));
        emitter().emitNewLine();
    }

    /*
     * Context/Transition/Scheduler Prototypes
     */
    void prototypes(String instanceName, Entity entity);


    default void prototypes(String instanceName, ActorMachine am) {
        // -- Actor Instance Name
        String actorInstanceName = "ActorInstance_" + backend().instaceQID(instanceName, "_");

        // -- Callables Prototypes
        emitter().emit("// -- Callables Prototypes");
        for (Scope scope : am.getScopes()) {
            if (scope.isPersistent()) {
                for (VarDecl decl : scope.getDeclarations()) {
                    if (decl.getValue() != null) {
                        Expression expr = decl.getValue();
                        if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                            backend().callablesInActor().callablePrototypes(instanceName, expr);
                            emitter().emitNewLine();
                        }
                    }
                }
            }
        }

        // -- ART Action Prototypes (aka AM Transitions)
        emitter().emit("// -- Transition prototypes");
        for (Transition transition : am.getTransitions()) {
            String transitionName = instanceName + "_transition_" + am.getTransitions().indexOf(transition);
            emitter().emit("ART_ACTION(%s, %s);", transitionName, actorInstanceName);
        }
        emitter().emit("ART_ACTION_SCHEDULER(%s_scheduler);", backend().instaceQID(instanceName, "_"));
        emitter().emit("static void %s_constructor(AbstractActorInstance *);", actorInstanceName);
        emitter().emit("static void %s_destructor(AbstractActorInstance *);", actorInstanceName);
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
        emitter().emit("};");
        emitter().emitNewLine();
    }

    /*
     * Scopes
     */

    default void scopes(String instanceName, ActorMachine am) {
        // -- Actor Instance Name
        String actorInstanceName = "ActorInstance_" + backend().instaceQID(instanceName, "_");

        emitter().emit("// -- Scopes");
        for (Scope scope : am.getScopes()) {
            if (scope.getDeclarations().size() > 0 || scope.isPersistent()) {
                if (am.getScopes().indexOf(scope) != 0) {
                    String scopeName = instanceName + "_init_scope_" + am.getScopes().indexOf(scope);
                    emitter().emit("ART_SCOPE(%s, %s){", scopeName, actorInstanceName);
                    emitter().increaseIndentation();

                    for (VarDecl var : scope.getDeclarations()) {
                        Type type = types().declaredType(var);
                        if (var.isExternal() && type instanceof CallableType) {
                            String wrapperName = backend().callables().externalWrapperFunctionName(var);
                            String variableName = backend().variables().declarationName(var);
                            String t = backend().callables().mangle(type).encode();
                            emitter().emit("thisActor->%s = (%s) { *%s, NULL };", variableName, t, wrapperName);
                        } else if (var.getValue() != null) {
                            emitter().emit("{");
                            emitter().increaseIndentation();
                            if (var.getValue() instanceof ExprInput) {
                                expressioneval().evaluateWithLvalue("thisActor->" + backend().variables().declarationName(var), (ExprInput) var.getValue());
                            } else {
                                statements().copy(types().declaredType(var), "thisActor->" + backend().variables().declarationName(var), types().type(var.getValue()), expressioneval().evaluate(var.getValue()));
                            }
                            emitter().decreaseIndentation();
                            emitter().emit("}");
                        }
                    }
                    emitter().decreaseIndentation();
                    emitter().emit("}");
                }

                emitter().emitNewLine();
            }
        }
    }

    /*
     * Conditions
     */

    default void conditions(String instanceName, ActorMachine am) {
        // -- Actor Instance Name
        String actorInstanceName = "ActorInstance_" + backend().instaceQID(instanceName, "_");

        emitter().emit("// -- Conditions");
        for (Condition condition : am.getConditions()) {
            String conditionName = instanceName + "_condition_" + am.getConditions().indexOf(condition);
            emitter().emit("ART_CONDITION(%s, %s){", conditionName, actorInstanceName);
            emitter().increaseIndentation();

            emitter().emit("return %s;", evaluateCondition(condition));

            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emitNewLine();
        }
    }

    String evaluateCondition(Condition condition);

    default String evaluateCondition(PredicateCondition condition) {
        return expressioneval().evaluate(condition.getExpression());
    }

    default String evaluateCondition(PortCondition condition) {
        if (condition.isInputCondition()) {
            return String.format("pinAvailIn_%s(%s) >=  %d", channelutils().inputPortTypeSize(condition.getPortName()), channelutils().definedInputPort(condition.getPortName()), condition.N());
        } else {
            return String.format("pinAvailOut_%s(%s) >= %d", channelutils().outputPortTypeSize(condition.getPortName()), channelutils().definedOutputPort(condition.getPortName()), condition.N());
        }
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
        emitter().emit("ActorInstance_%s_destructor,", instanceQID);
        emitter().emit("%d, %s,", am.getInputPorts().size(), am.getInputPorts().size() == 0 ? "0" : "inputPortDescriptions");
        emitter().emit("%d, %s,", am.getOutputPorts().size(), am.getOutputPorts().size() == 0 ? "0" : "outputPortDescriptions");
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

    /*
     * Constructor
     */

    void constructorDefinition(String instanceName, Entity entity);

    default void constructorDefinition(String instanceName, ActorMachine am) {
        emitter().emit("// -- Constructor Definitions");

        String actorInstanceName = "ActorInstance_" + backend().instaceQID(instanceName, "_");
        emitter().emit("static void %s_constructor(AbstractActorInstance *pBase){", actorInstanceName);
        emitter().increaseIndentation();

        emitter().emit("%s *thisActor = (%1$s*) pBase;", actorInstanceName);
        emitter().emit("// -- Actor Machine Initial Program Counter");
        emitter().emit("thisActor->program_counter = %d;", 0);
        emitter().emitNewLine();

        emitter().emit("#ifdef CAL_RT_CALVIN");
        emitter().emit("init_global_variables();");
        emitter().emit("#endif");
        emitter().emitNewLine();

        emitter().emit("// -- Initialize persistent scopes");
        for (Scope scope : am.getScopes()) {
            if (am.getScopes().indexOf(scope) == 0) {
                for (VarDecl var : scope.getDeclarations()) {
                    Type type = types().declaredType(var);
                    if (type instanceof ListType) {
                        emitter().emit("{");
                        emitter().increaseIndentation();
                        String maxIndex = typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
                        //emitter().emit("thisActor->%s = malloc(sizeof(%s) * (%s));", backend().variables().declarationName(var), typeseval().type(type), maxIndex);
                        emitter().emit("thisActor->%s = (%s*) calloc(%s, sizeof(%2$s));", backend().variables().declarationName(var), typeseval().type(type), maxIndex);
                        emitter().decreaseIndentation();
                        emitter().emit("}");
                    }
                    if (var.isExternal() && type instanceof CallableType) {
                        //String wrapperName = backend().callables().externalWrapperFunctionName(var);
                        //String variableName = backend().variables().declarationName(var);
                        //String t = backend().callables().mangle(type).encode();
                        //emitter().emit("thisActor->%s = (%s) { *%s, NULL };", variableName, t, wrapperName);
                    } else if (var.getValue() != null) {
                        if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                            // -- Do nothing
                        } else if (var.getValue() instanceof ExprInput) {
                            emitter().emit("{");
                            emitter().increaseIndentation();
                            expressioneval().evaluateWithLvalue("thisActor->" + backend().variables().declarationName(var), (ExprInput) var.getValue());
                            emitter().decreaseIndentation();
                            emitter().emit("}");
                        } else {
                            emitter().emit("{");
                            emitter().increaseIndentation();
                            statements().copy(types().declaredType(var), "thisActor->" + backend().variables().declarationName(var), types().type(var.getValue()), expressioneval().evaluate(var.getValue()));
                            emitter().decreaseIndentation();
                            emitter().emit("}");
                        }
                    }
                }
            } else {
                for (VarDecl var : scope.getDeclarations()) {
                    Type type = types().declaredType(var);
                    if (type instanceof ListType) {
                        emitter().emit("{");
                        emitter().increaseIndentation();
                        String maxIndex = typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
                        //emitter().emit("thisActor->%s = malloc(sizeof(%s) * (%s));", backend().variables().declarationName(var), typeseval().type(type), maxIndex);
                        emitter().emit("thisActor->%s = (%s*) calloc(%s, sizeof(%2$s));", backend().variables().declarationName(var), typeseval().type(type), maxIndex);
                        emitter().decreaseIndentation();
                        emitter().emit("}");
                    }
                }
            }

        }
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();
    }

    /*
     * Destructor
     */


    void destructorDefinition(String instanceName, Entity entity);

    default void destructorDefinition(String instanceName, ActorMachine am) {
        emitter().emit("// -- Constructor Definitions");

        String actorInstanceName = "ActorInstance_" + backend().instaceQID(instanceName, "_");
        emitter().emit("static void %s_destructor(AbstractActorInstance *pBase){", actorInstanceName);
        emitter().increaseIndentation();

        emitter().emit("%s *thisActor = (%1$s*) pBase;", actorInstanceName);
        for (Scope scope : am.getScopes()) {
            for (VarDecl decl : scope.getDeclarations()) {
                Type t = types().declaredType(decl);
                if (t instanceof ListType) {
                    String declarationName = backend().variables().declarationName(decl);
                    emitter().emit("free(thisActor->%s);", declarationName);
                }
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();
    }

    /*
     * Scheduler
     */

    void scheduler(String instanceName, Entity entity);

    default void scheduler(String instanceName, ActorMachine am) {
        backend().controllers().emitController(instanceName, am);
    }

}
