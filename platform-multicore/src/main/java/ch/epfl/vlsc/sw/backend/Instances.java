package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.attributes.Defines;
import ch.epfl.vlsc.attributes.Uses;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.AnnotationParameter;
import se.lth.cs.tycho.ir.Parameter;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.*;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.type.*;

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
        return backend().expressionEval();
    }


    default void generateInstance(Instance instance) {
        // -- Add instance to box
        backend().instancebox().set(instance);
        stateVariables().clear();

        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add entity to box
        backend().entitybox().set(entity);

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        // -- Target file Path
        Path instanceTarget;
        if (backend().context().getConfiguration().get(PlatformSettings.runOnNode)) {
            instanceTarget = PathUtils.getTargetCodeGenSourceCC(backend().context()).resolve(instanceName + ".cc");
        } else {
            instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(instanceName + ".cc");
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

        // -- State Variable Description
        stateVariableDescription(instanceName, entity);

        // -- Port Rate
        portRate(instanceName, entity);

        // -- Use / Defines
        usesDefines(instanceName, entity);

        // -- Action/Transitions Description
        acttransDescription(instanceName, entity);

        // -- Used state vars in conditions
        usedVariablesInConditions(instanceName, entity);

        // -- Conditions description
        conditionDescription(instanceName, entity);

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
        int nbrIN = 0;
        for (PortDecl inputPort : entity.getInputPorts()) {
            if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), inputPort.getName())) {
                String definedInput = "IN" + entity.getInputPorts().indexOf(inputPort) + "_" + inputPort.getName();
                emitter().emit("#define %s ART_INPUT(%d)", definedInput, nbrIN);
                nbrIN++;
            }
        }

        // -- Outputs
        int nbrOUT = 0;
        for (PortDecl outputPort : entity.getOutputPorts()) {
            if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), outputPort.getName())) {
                String definedOutput = "OUT" + entity.getOutputPorts().indexOf(outputPort) + "_" + outputPort.getName();
                emitter().emit("#define %s ART_OUTPUT(%d)", definedOutput, nbrOUT);
                nbrOUT++;
            }
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
        int sizeIN = 0;
        int sizeOUT = 0;

        for (PortDecl inputPort : am.getInputPorts()) {
            if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), inputPort.getName())) {
                sizeIN++;
            }
        }
        for (PortDecl outputPort : am.getOutputPorts()) {
            if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), outputPort.getName())) {
                sizeOUT++;
            }
        }
        emitter().emit("ART_ACTION_CONTEXT(%d,%d)", sizeIN, sizeOUT);
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
                            emitter().emit("#ifndef TRACE_TURNUS");
                            backend().callablesInActor().callableDefinition(instanceName, expr);
                            emitter().emit("#else");
                            backend().profilingbox().set(true);
                            backend().callablesInActor().callableDefinition(instanceName, expr);
                            backend().profilingbox().clear();
                            emitter().emit("#endif");
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
                    String decl = declarations().persistentDeclaration(types().declaredType(var), backend().variables().declarationName(var));
                    emitter().emit("%s;", decl);
                }
            }
        }

        // -- Parameters
        if (!am.getValueParameters().isEmpty()) {
            emitter().emitNewLine();
            emitter().emit("// -- Parameters");
            for (ParameterVarDecl vp : am.getValueParameters()) {
                String decl = declarations().declaration(types().declaredType(vp), backend().variables().declarationName(vp));
                emitter().emit("%s;", decl);
            }
        }

        emitter().decreaseIndentation();
        emitter().emit("} ActorInstance_%s;", instanceName);
        emitter().emitNewLine();
    }

    /*
     * Context/Transition/Scheduler Prototypes
     */
    void prototypes(String instanceName, Entity entity);


    default void prototypes(String instanceName, ActorMachine am) {
        // -- Actor Instance Name
        String actorInstanceName = "ActorInstance_" + instanceName;

        // -- Callables Prototypes
        emitter().emit("// -- Callables Prototypes");
        for (Scope scope : am.getScopes()) {
            if (scope.isPersistent()) {
                for (VarDecl decl : scope.getDeclarations()) {
                    if (decl.getValue() != null) {
                        Expression expr = decl.getValue();
                        if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                            emitter().emit("#ifndef TRACE_TURNUS");
                            backend().callablesInActor().callablePrototypes(instanceName, expr);
                            emitter().emit("#else");
                            backend().profilingbox().set(true);
                            backend().callablesInActor().callablePrototypes(instanceName, expr);
                            backend().profilingbox().clear();
                            emitter().emit("#endif");
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
        emitter().emit("ART_ACTION_SCHEDULER(%s_scheduler);", instanceName);
        emitter().emit("static void %s_constructor(AbstractActorInstance *);", actorInstanceName);
        emitter().emit("static void %s_destructor(AbstractActorInstance *);", actorInstanceName);
        emitter().emitNewLine();

    }


    default String getDynamicShapeAnnotation(String annotationName, String portName, List<Annotation> annotations){

        List<Annotation> shapes = Annotation.getAnnotationsWithName(annotationName, annotations);
        if (!shapes.isEmpty()){
            for (Annotation ann : shapes){
                List<AnnotationParameter> params = ann.getParameters();
                AnnotationParameter port = params.get(0);
                ExprLiteral literal = (ExprLiteral) port.getExpression();
                if(literal.getText().replaceAll("\"", "").equals(portName)){
                    return ((ExprLiteral) params.get(1).getExpression()).getText().replaceAll("\"", "");
                }
            }
        }

        return "0";
    }

    /*
     * Port Descriptions
     */
    default void portDescription(String instanceName, Entity entity) {
        emitter().emit("// -- Input & Output Port Description");

        emitter().emit("#ifdef CAL_RT_CALVIN");
        emitter().emit("static struct tokenFn serDesTensor = {");
        emitter().increaseIndentation();
        emitter().emit("(char *(*)(void *, char *))serializeTensor,");
        emitter().emit("(char *(*)(void **, char *, long))deserializeTensor,");
        emitter().emit("(long (*)(void *))sizeTensor, (int (*)(void *, int))freeTensor};");
        emitter().decreaseIndentation();
        emitter().emit("#endif");
        emitter().emitNewLine();

        List<Annotation> annotations = new ArrayList<>();
        if(!entity.getAnnotations().isEmpty()){
            annotations = entity.getAnnotations();
        }

        // -- Input Port Descriptions
        if (!entity.getInputPorts().isEmpty()) {
            emitter().emit("static const PortDescription inputPortDescriptions[]={");
            emitter().increaseIndentation();

            for (PortDecl inputPort : entity.getInputPorts()) {
                if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), inputPort.getName())) {
                    Type type = channelutils().targetEndType(new Connection.End(Optional.of(instanceName), inputPort.getName()));
                    String dynamic_size =  getDynamicShapeAnnotation("input_dynamic_shape", inputPort.getName(), annotations);
                    portDescriptionByPort(inputPort.getName(), type, dynamic_size);
                }
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
                if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), outputPort.getName())) {
                    Connection.End source = new Connection.End(Optional.of(instanceName), outputPort.getName());
                    Type type = channelutils().sourceEndType(source);
                    String dynamic_size =  getDynamicShapeAnnotation("output_dynamic_shape", outputPort.getName(), annotations);
                    portDescriptionByPort(outputPort.getName(), type, dynamic_size);
                }
            }

            emitter().decreaseIndentation();
            emitter().emit("};");
            emitter().emitNewLine();
        }

    }

    default void portDescriptionByPort(String name, Type type, String dynamic_size) {
        String evaluatedType;
//        if (type instanceof ProductType | type instanceof SumType | type instanceof TensorType) {
        if (type instanceof ProductType | type instanceof SumType) {
            evaluatedType = "void*";
        } else {
            evaluatedType = backend().typeseval().type(type);
        }

        emitter().emit("{0, \"%s\", (sizeof(%s)), %s", name, evaluatedType, dynamic_size);
        emitter().emit("#ifdef CAL_RT_CALVIN");
//        if(type instanceof TensorType){
//            emitter().emit(", &serDesTensor");
//        }else{
            emitter().emit(", NULL");
//        }
        emitter().emit("#endif");
        emitter().emit("},");
    }

    /*
     * Port Rate
     */
    void portRate(String instanceName, Entity entity);

    default void portRate(String instanceName, ActorMachine am) {
        emitter().emit("// -- Input / Output Port Rate by Transition");
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

    void usesDefines(String instanceName, Entity entity);

    default void usesDefines(String instanceName, ActorMachine am) {
        emitter().emit("// -- Uses & defines in Transition");
        for (Transition transition : am.getTransitions()) {
            Uses uses = MultiJ.from(Uses.class)
                    .bind("stateVariables").to(stateVariables())
                    .bind("vardecls").to(backend().varDecls())
                    .instance();
            Defines defines = MultiJ.from(Defines.class)
                    .bind("stateVariables").to(stateVariables())
                    .bind("vardecls").to(backend().varDecls())
                    .instance();

            List<VarDecl> usedVar = uses.uses(transition);
            List<VarDecl> definesVar = defines.defines(transition);

            List<String> usedVarsInTransition = new ArrayList<>(stateVariables().size());
            List<String> definesVarsInTransition = new ArrayList<>(stateVariables().size());

            for (VarDecl varDecl : stateVariables()) {
                if (usedVar.contains(varDecl)) {
                    usedVarsInTransition.add(String.valueOf(usedVar.size()));
                } else {
                    usedVarsInTransition.add("0");
                }

                if (definesVar.contains(varDecl)) {
                    definesVarsInTransition.add(String.valueOf(definesVar.size()));
                } else {
                    definesVarsInTransition.add("0");
                }
            }

            String transitionName = instanceName + "_transition_" + am.getTransitions().indexOf(transition);
            emitter().emit("static const int uses_in_%s[] = {%s};", transitionName, String.join(", ", usedVarsInTransition));
            emitter().emitNewLine();
            emitter().emit("static const int defines_in_%s[] = {%s};", transitionName, String.join(", ", definesVarsInTransition));
            emitter().emitNewLine();
        }
    }

    /*
     * State variable description
     */

    @Binding(BindingKind.LAZY)
    default List<VarDecl> stateVariables() {
        return new ArrayList<>();
    }

    void stateVariableDescription(String instanceName, Entity entity);

    default void stateVariableDescription(String instanceName, ActorMachine am) {
        emitter().emit("// -- State Variable Description");
        emitter().emit("static const StateVariableDescription stateVariableDescription[] = {");
        emitter().increaseIndentation();

        for (Scope scope : am.getScopes()) {
            for (VarDecl var : scope.getDeclarations()) {
                String variableName = backend().variables().declarationName(var);
                Type type = types().declaredType(var);
                if (var.isExternal() || type instanceof CallableType || var.getValue() instanceof ExprInput) {
                    // -- Do nothing
                } else {
                    if (type instanceof ListType) {
                        String maxIndex = typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
                        emitter().emit("{\"%s\", \"%s\", sizeof(%s)*%s},", variableName, var.getOriginalName(), typeseval().type(type), maxIndex);
                    } else {
                        emitter().emit("{\"%s\", \"%s\", sizeof(%s)},", variableName, var.getOriginalName(), typeseval().type(type));
                    }
                    stateVariables().add(var);
                }
            }
        }

        emitter().decreaseIndentation();
        emitter().emit("};");
        emitter().emitNewLine();
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
            Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());
            String actionTag = "";
            if (annotation.isPresent()) {
                actionTag = ((ExprLiteral) annotation.get().getParameters().get(0).getExpression()).getText();
            }
            emitter().emit("{\"%s\", \"%s\", portRate_in_%1$s, portRate_out_%1$s, uses_in_%1$s, defines_in_%1$s},", transitionName, actionTag);
        }

        emitter().decreaseIndentation();
        emitter().emit("};");
        emitter().emitNewLine();
    }


    void usedVariablesInConditions(String instanceName, Entity entity);

    default void usedVariablesInConditions(String instanceName, ActorMachine am) {
        emitter().emit("// -- State variables in Condition");
        for (Condition condition : am.getConditions()) {
            if (condition.kind() == Condition.ConditionKind.predicate) {
                Uses uses = MultiJ.from(Uses.class)
                        .bind("stateVariables").to(stateVariables())
                        .bind("vardecls").to(backend().varDecls())
                        .instance();

                List<VarDecl> usedVar = uses.uses(condition);


                List<String> usedVarsInTransition = new ArrayList<>(stateVariables().size());

                for (VarDecl varDecl : stateVariables()) {
                    if (usedVar.contains(varDecl)) {
                        usedVarsInTransition.add("1");
                    } else {
                        usedVarsInTransition.add("0");
                    }

                }

                String conditionName = instanceName + "_condition_" + am.getConditions().indexOf(condition);
                emitter().emit("static const int state_var_in_%s[] = {%s};", conditionName, String.join(", ", usedVarsInTransition));
                emitter().emitNewLine();
            }
        }
    }

    /*
     * Condition description
     */

    void conditionDescription(String instanceName, Entity entity);

    default void conditionDescription(String instanceName, ActorMachine am) {
        emitter().emit("// -- Condition description");
        emitter().emit("static const ConditionDescription conditionDescription[] = {");
        emitter().increaseIndentation();

        for (Condition condition : am.getConditions()) {
            String conditionName = instanceName + "_condition_" + am.getConditions().indexOf(condition);
            if (condition.kind() == Condition.ConditionKind.input) {
                emitter().emit("{\"%s\", INPUT_KIND, %d, %d, NULL},", conditionName, am.getInputPorts().indexOf(backend().ports().declaration(((PortCondition) condition).getPortName())), ((PortCondition) condition).N());
            } else if (condition.kind() == Condition.ConditionKind.output) {
                emitter().emit("{\"%s\", OUTPUT_KIND, %d, %d, NULL},", conditionName, am.getInputPorts().indexOf(backend().ports().declaration(((PortCondition) condition).getPortName())), ((PortCondition) condition).N());
            } else {
                emitter().emit("{\"%s\", PREDICATE_KIND, -1, -1, state_var_in_%1$s},", conditionName);
            }
        }

        emitter().decreaseIndentation();
        emitter().emit("};");
        emitter().emitNewLine();
    }

    /*
     * Scopes
     */

    default void scopes(String instanceName, ActorMachine am) {
        emitter().emit("// -- Scopes");
        for (Scope scope : am.getScopes()) {
            if (scope.getDeclarations().size() > 0 || scope.isPersistent()) {
                if (am.getScopes().indexOf(scope) != 0) {
                    scope(instanceName, am, scope);
                }
            }
        }
    }

    default void scope(String instanceName, ActorMachine am, Scope scope) {
        // -- Actor Instance Name
        String actorInstanceName = "ActorInstance_" + instanceName;
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
                if(var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc ){
                    // -- Do nothing
                }else{
                    emitter().emit("#ifndef TRACE_TURNUS");
                    evaluateVarInit(var);
                    emitter().emit("#else");
                    backend().profilingbox().set(true);
                    evaluateVarInit(var);
                    backend().profilingbox().clear();
                    emitter().emit("#endif");
                }
            }
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    /*
     * Conditions
     */

    default void evaluateVarInit(VarDecl var) {
        emitter().emit("{");
        emitter().increaseIndentation();
        if (!backend().profilingbox().isEmpty()) {
            emitter().emit("OpCounters *__opCounters = new OpCounters();");
        }

        if (var.getValue() instanceof ExprInput) {
            expressioneval().evaluateWithLvalue("thisActor->" + backend().variables().declarationName(var), (ExprInput) var.getValue());
        } else {
            Type type = types().declaredType(var);
//            if (type instanceof TensorType) {
//                expressioneval().evaluateWithLvalue("thisActor->" + backend().variables().declarationName(var), var.getValue());
//            } else {
                statements().copy(types().declaredType(var), "thisActor->" + backend().variables().declarationName(var), types().type(var.getValue()), expressioneval().evaluate(var.getValue()));
//            }
        }
        if (!backend().profilingbox().isEmpty()) {
            emitter().emit("delete __opCounters;");
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void conditions(String instanceName, ActorMachine am) {


        emitter().emit("// -- Conditions");
        for (Condition condition : am.getConditions()) {
            emitter().emit("#ifndef TRACE_TURNUS");
            condition(instanceName, am, condition);
            emitter().emit("#else");
            backend().profilingbox().set(true);
            condition(instanceName, am, condition);
            backend().profilingbox().clear();
            emitter().emit("#endif");
            emitter().emitNewLine();
        }
    }

    default void condition(String instanceName, ActorMachine am, Condition condition) {
        // -- Actor Instance Name
        String actorInstanceName = "ActorInstance_" + instanceName;
        String conditionName = instanceName + "_condition_" + am.getConditions().indexOf(condition);
        emitter().emit("ART_CONDITION(%s, %s){", conditionName, actorInstanceName);
        emitter().increaseIndentation();
        emitter().emit("ART_CONDITION_ENTER(%s, %d)", conditionName, am.getConditions().indexOf(condition));
        emitter().emit("bool cond = %s;", evaluateCondition(condition));
        emitter().emit("ART_CONDITION_EXIT(%s, %d)", conditionName, am.getConditions().indexOf(condition));
        emitter().emit("return cond;");
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    String evaluateCondition(Condition condition);

    default String evaluateCondition(PredicateCondition condition) {
        return expressioneval().evaluate(condition.getExpression());
    }

    default String evaluateCondition(PortCondition condition) {
        if (condition.isInputCondition()) {
            if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), condition.getPortName().getName())) {
                return String.format("pinAvailIn_%s(%s) >=  %d", channelutils().inputPortTypeSize(condition.getPortName()), channelutils().definedInputPort(condition.getPortName()), condition.N());
            } else {
                return "false";
            }
        } else {
            if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), condition.getPortName().getName())) {
                return String.format("pinAvailOut_%s(%s) >= %d", channelutils().outputPortTypeSize(condition.getPortName()), channelutils().definedOutputPort(condition.getPortName()), condition.N());
            } else {
                return "true";
            }
        }
    }


    /*
     * ART Actor Class
     */

    void actorClass(String instanceName, Entity entity);

    default void actorClass(String instanceName, ActorMachine am) {
        String instanceQID = instanceName;
        int sizeIN = 0;
        for (PortDecl inputPort : am.getInputPorts()) {
            if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), inputPort.getName())) {
                sizeIN++;
            }
        }

        int sizeOUT = 0;
        for (PortDecl outputPort : am.getOutputPorts()) {
            if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), outputPort.getName())) {
                sizeOUT++;
            }
        }

        emitter().emit("// -- Actor Class");
        Instance instance = backend().instancebox().get();

        emitter().emit("#ifdef CAL_RT_CALVIN");
        emitter().emit("ActorClass klass");
        emitter().increaseIndentation();
        emitter().emit("= INIT_ActorClass(");
        emitter().increaseIndentation();

        emitter().emit("(char*) \"%s\",", instance.getEntityName().toString());
        emitter().emit("ActorInstance_%s,", instanceQID);
        emitter().emit("ActorInstance_%s_constructor,", instanceQID);
        emitter().emit("0, // -- setParam not needed anymore (we instantiate with params)");
        emitter().emit("#ifdef CAL_RT_CALVIN_SERIALIZE");
        emitter().emit("NULL, // -- TODO : actor serializer");
        emitter().emit("NULL, // -- TODO : actor deserializer");
        emitter().emit("#endif");
        emitter().emit("%s_scheduler,", instanceQID);
        emitter().emit("ActorInstance_%s_destructor,", instanceQID);
        emitter().emit("%d, %s,", sizeIN, am.getInputPorts().size() == 0 ? "NULL" : "inputPortDescriptions");
        emitter().emit("%d, %s,", sizeOUT, am.getOutputPorts().size() == 0 ? "NULL" : "outputPortDescriptions");
        emitter().emit("%d, actionDescriptions", am.getTransitions().size());

        emitter().decreaseIndentation();
        emitter().emit(");");
        emitter().decreaseIndentation();
        emitter().emit("#else");

        emitter().emit("ActorClass ActorClass_%s", instanceQID);
        emitter().increaseIndentation();
        emitter().emit("= INIT_ActorClass(");
        emitter().increaseIndentation();

        emitter().emit("(char*) \"%s\",", instance.getEntityName().toString());
        emitter().emit("ActorInstance_%s,", instanceQID);
        emitter().emit("ActorInstance_%s_constructor,", instanceQID);
        emitter().emit("0, // -- setParam not needed anymore (we instantiate with params)");
        emitter().emit("%s_scheduler,", instanceQID);
        emitter().emit("ActorInstance_%s_destructor,", instanceQID);
        emitter().emit("%d, %s,", sizeIN, am.getInputPorts().size() == 0 ? "0" : "inputPortDescriptions");
        emitter().emit("%d, %s,", sizeOUT, am.getOutputPorts().size() == 0 ? "0" : "outputPortDescriptions");
        emitter().emit("%d, actionDescriptions,", am.getTransitions().size());
        emitter().emit("%d, conditionDescription,", am.getConditions().size());
        emitter().emit("%d, stateVariableDescription", stateVariables().size());

        emitter().decreaseIndentation();
        emitter().emit(");");
        emitter().decreaseIndentation();
        emitter().emit("#endif");
        emitter().emitNewLine();
    }


    /*
     * Action / Transition Definitions
     */

    void acttransDefinitions(String instanceName, Entity entity);

    default void acttransDefinitions(String instanceName, ActorMachine am) {
        emitter().emit("// -- Transitions Definitions");
        for (Transition transition : am.getTransitions()) {
            emitter().emit("#ifndef TRACE_TURNUS");
            acttransDefinition(instanceName, am, transition);
            emitter().emit("#else");
            backend().profilingbox().set(true);
            acttransDefinition(instanceName, am, transition);
            backend().profilingbox().clear();
            emitter().emit("#endif");

            emitter().emitNewLine();
        }
    }

    default void acttransDefinition(String instanceName, ActorMachine am, Transition transition) {
        String instanceQID = instanceName;
        Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());
        String actionTag = "";
        if (annotation.isPresent()) {
            actionTag = ((ExprLiteral) annotation.get().getParameters().get(0).getExpression()).getText();
            emitter().emit("// -- Action Tag : %s", actionTag);
        }
        emitter().emit("ART_ACTION(%s_transition_%d, ActorInstance_%s){", instanceName, am.getTransitions().indexOf(transition), instanceQID);
        emitter().increaseIndentation();

        emitter().emit("ART_ACTION_ENTER(%s_transition_%d, %2$d);", instanceName, am.getTransitions().indexOf(transition));
        //emitter().emit("printf(\"%s\\n\");", actionTag);
        transition.getBody().forEach(statements()::execute);

        emitter().emit("ART_ACTION_EXIT(%s_transition_%d, %2$d);", instanceName, am.getTransitions().indexOf(transition));

        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    /*
     * Constructor
     */

    void constructorDefinition(String instanceName, Entity entity);

    default void constructorDefinition(String instanceName, ActorMachine am) {
        emitter().emit("// -- Constructor Definitions");

        String actorInstanceName = "ActorInstance_" + instanceName;
        emitter().emit("static void %s_constructor(AbstractActorInstance *pBase){", actorInstanceName);
        emitter().increaseIndentation();

        emitter().emit("%s *thisActor = (%1$s*) pBase;", actorInstanceName);
        emitter().emit("// -- Actor Machine Initial Program Counter");
        emitter().emit("thisActor->program_counter = %d;", 0);
        emitter().emitNewLine();

        emitter().emit("#ifdef CAL_RT_CALVIN");
        emitter().emit("//init_global_variables();");
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
                        } else {
                            emitter().emit("#ifndef TRACE_TURNUS");
                            evaluateVarInit(var);
                            emitter().emit("#else");
                            backend().profilingbox().set(true);
                            evaluateVarInit(var);
                            backend().profilingbox().clear();
                            emitter().emit("#endif");
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

        // -- Parameters
        Instance instance = backend().instancebox().get();

        for (ParameterVarDecl par : am.getValueParameters()) {
            boolean assigned = false;
            for (Parameter<Expression, ?> assignment : instance.getValueParameters()) {
                if (par.getName().equals(assignment.getName())) {
                    emitter().emit("thisActor->%s = %s;", backend().variables().declarationName(par), expressioneval().evaluate(assignment.getValue()));
                    assigned = true;
                }
            }
            if (!assigned) {
                throw new RuntimeException(String.format("Could not assign to %s. Candidates: {%s}.", par.getName(), String.join(", ", instance.getValueParameters().map(Parameter::getName))));
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

        String actorInstanceName = "ActorInstance_" + instanceName;
        emitter().emit("static void %s_destructor(AbstractActorInstance *pBase){", actorInstanceName);
        emitter().increaseIndentation();

        emitter().emit("%s *thisActor = (%1$s*) pBase;", actorInstanceName);
        for (Scope scope : am.getScopes()) {
            for (VarDecl decl : scope.getDeclarations()) {
                Type t = types().declaredType(decl);
                if (t instanceof ListType) {
                    String name = String.format("thisActor->%s", backend().variables().declarationName(decl));
                    //backend().free().apply(t, name);
                    emitter().emit("free(%s);", name);
                }
            }
        }

/*
        for (ParameterVarDecl par : am.getValueParameters()) {
            Type type = types().declaredType(par);
            if (type instanceof AlgebraicType) {
                AlgebraicType at = (AlgebraicType) type;
                emitter().emit("%s(thisActor->%s, 1);", backend().algebraic().destructor((AlgebraicType) type), backend().variables().declarationName(par));
            }
        }
*/
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
