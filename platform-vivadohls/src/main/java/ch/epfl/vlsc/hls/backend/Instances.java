package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.hls.backend.directives.Directives;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.ScopeLiveness;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.Parameter;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.entity.am.*;
import se.lth.cs.tycho.ir.entity.am.ctrl.Instruction;
import se.lth.cs.tycho.ir.entity.am.ctrl.State;
import se.lth.cs.tycho.ir.entity.cal.*;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
import se.lth.cs.tycho.transformation.cal2am.Priorities;
import se.lth.cs.tycho.transformation.cal2am.Schedule;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Module
public interface Instances {
    int MAX_STATES_FOR_QUICK_JUMP_CONTROLLER = 200;

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default Types types() {
        return backend().types();
    }

    default Declarations declarations() {
        return backend().declarations();
    }

    default ExpressionEvaluator expressioneval() {
        return backend().expressioneval();
    }

    default ChannelsUtils channelutils() {
        return backend().channelsutils();
    }

    default boolean hasPipelinedController(Entity entity) {

        boolean pipelined =
                !backend().context().getConfiguration().get(PlatformSettings.disablePipelining)
                        && entity.getAnnotations().stream().anyMatch(annon ->
                        Directives.directive(annon.getName()) == Directives.PIPELINE);
        return  pipelined;
    }
    default void generateInstance(Instance instance) {
        // -- Add instance to box
        backend().instancebox().set(instance);

        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();




        // -- Check complex read
        if (entity instanceof ActorMachine) {
            ActorMachine actor = (ActorMachine) entity;
            boolean complexRead = checkReadComplexity(actor.getScopes());
            backend().complexReadBox().set(complexRead);
        }

        // -- Add entity to box
        backend().entitybox().set(entity);

        // -- Generate Source
        generateSource(instance);

        // -- Generate Header
        generateHeader(instance);

        // -- Clear boxes
        backend().complexReadBox().clear();
        backend().entitybox().clear();
        backend().instancebox().clear();
    }

    default void generateSource(Instance instance) {
        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(instance.getInstanceName() + ".cpp");
        emitter().open(instanceTarget);

        // -- Entity
        Entity entity = backend().entitybox().get();


        // -- Instance Name
        String instanceName = instance.getInstanceName();

        // -- Includes
        defineIncludes(false);

        // -- Static call of instance
        staticCallofInstance(instance);

        emitter().emitClikeBlockComment(instanceName + " : Members declaration");
        emitter().emitNewLine();

        // -- Top of Instance
        topOfInstance(instanceName, entity);

        if (entity instanceof ActorMachine) {
            ActorMachine actor = (ActorMachine) entity;

            if (backend().context().getConfiguration().get(PlatformSettings.defaultController) == PlatformSettings.ControllerKind.BC || actor.controller().getStateList().size() > MAX_STATES_FOR_QUICK_JUMP_CONTROLLER) {
                // -- State Functions
                emitter().emit("// -- State Functions");

                List<? extends State> stateList = actor.controller().getStateList();
                Map<State, Integer> stateMap = backend().branchingController().stateMap(stateList);
                Function<Instruction, BitSet> initialize;
                ScopeLiveness liveness = new ScopeLiveness(backend().scopes(), actor, backend().scopeDependencies());
                initialize = liveness::init;

                backend().branchingController().waitTargetBitSets(actor).stream().forEach(s -> {
                    State state = stateMap.entrySet().stream().filter(entry -> Objects.equals(entry.getValue(), s)).map(Map.Entry::getKey).findAny().orElse(null);
                    backend().branchingController().emitStateFunction(instanceName, actor, initialize, stateMap, state);
                });
            }

            // -- Scopes
            emitter().emit("// -- Scopes");
            actor.getScopes().forEach(s -> scope(instanceName, s, actor.getScopes().indexOf(s)));

            // -- Conditions
            emitter().emit("// -- Conditions");
            actor.getConditions().forEach(c -> condition(instanceName, c, actor.getConditions().indexOf(c)));

            // -- Transitions
            emitter().emit("// -- Transitions");
            actor.getTransitions().forEach(t -> transition(instanceName, t, actor.getTransitions().indexOf(t)));
        } else {
            CalActor actor = (CalActor) entity;
            // -- Declarations

            // -- State functions
            Schedule schedule = new Schedule(actor);
            Priorities priorities = new Priorities(actor);
            for (String state : schedule.getEligible().keySet()) {
                backend().calActorController().emitStateFunction(instanceName, actor, schedule, priorities, state);
            }

            // -- Actions
            actor.getActions().forEach(a -> action(instanceName, a));
        }

        // -- Callables
        callables(instanceName, entity);

        // -- EOF
        emitter().close();
    }

    default void staticCallofInstance(Instance instance) {
        Entity entity = backend().entitybox().get();
        boolean withIO = true;
        if (entity instanceof CalActor) {
            withIO = ((CalActor) entity).getProcessDescription() == null;
        }

        String name = instance.getInstanceName();
        emitter().emitClikeBlockComment("HLS Top Function");
        emitter().emitNewLine();

        // -- Static call
        String className = "class_" + instance.getInstanceName();
        emitter().emit("static %s i_%s;", className, name);
        emitter().emitNewLine();
        emitter().emit("int %s(%s) {", name, entityPorts(name, withIO, true));

        // -- Data pack I/O with algebraic types
        List<String> algebraicIO = new ArrayList<>();
        List<String> algebraicPeek = new ArrayList<>();
        algebraicIO.addAll(entity.getInputPorts().stream().filter(p -> types().declaredPortType(p) instanceof AlgebraicType).map(PortDecl::getName).collect(Collectors.toList()));
        algebraicPeek.addAll(algebraicIO);
        algebraicIO.addAll(entity.getOutputPorts().stream().filter(p -> types().declaredPortType(p) instanceof AlgebraicType).map(PortDecl::getName).collect(Collectors.toList()));
        algebraicIO.forEach(io -> emitter().emit("#pragma HLS DATA_PACK variable=%s", io));
        algebraicPeek.forEach(peek -> emitter().emit("#pragma HLS DATA_PACK variable=io.%s_peek", peek));

        // ---------------------------------------------------------
        // -- Directives

        // -- Large state variables to external memories
        for (VarDecl decl : backend().externalMemory().getExternalMemories(entity)) {
            ListType listType = (ListType) types().declaredType(decl);
            List<Integer> dim = backend().typeseval().sizeByDimension(listType);
            Long listDepth = backend().externalMemory().memories().listDepth(listType);
            String memoryName = backend().externalMemory().name(instance, decl);
            emitter().emit("#pragma HLS INTERFACE m_axi depth=%d port=%s offset=direct bundle=%2$s", listDepth, memoryName);
        }

        // -- Top Directives
        List<Annotation> annotations = entity.getAnnotations();
        for (Annotation ann : annotations) {
            if (Directives.directive(ann.getName()) == Directives.PIPELINE && hasPipelinedController(entity)) {
                // do not emit the pipeline annotation to the top function since we are using
                continue;
            } else {

                backend().annotations().emitTop(ann);
            }
        }
        if (!annotations.isEmpty()) {
            emitter().emitNewLine();
        }
        emitter().increaseIndentation();

        List<String> ports = new ArrayList<>();
        // -- External memories
        ports.addAll(
                backend().externalMemory()
                        .getExternalMemories(entity).map(v -> backend().externalMemory().name(instance, v)));

        ports.addAll(entity.getInputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));
        ports.addAll(entity.getOutputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));

        // -- Instance based Directive
        for (Annotation ann : annotations) {
            backend().annotations().emitInstance(ann);
        }
        emitter().emitNewLine();

        if (entity instanceof CalActor) {
            CalActor actor = (CalActor) entity;
            if (actor.getProcessDescription() != null) {
                if (actor.getProcessDescription().isRepeated()) {
                    emitter().emit("return i_%s(%s);", name, String.join(", ", ports));
                } else {
                    emitter().emit("bool has_executed = false;");
                    emitter().emitNewLine();
                    emitter().emit("if (!has_executed) {");
                    emitter().increaseIndentation();

                    emitter().emit("return i_%s(%s);", name, String.join(", ", ports));
                    emitter().emit("has_executed = true;");

                    emitter().decreaseIndentation();
                    emitter().emit("}");
                }
            } else {
                emitter().emit("return i_%s(%s, io);", name, String.join(", ", ports));
            }
        } else {
            emitter().emit("return i_%s(%s, io);", name, String.join(", ", ports));
        }

        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();
    }


    default void generateHeader(Instance instance) {
        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenInclude(backend().context()).resolve(instance.getInstanceName() + ".h");
        emitter().open(instanceTarget);

        // -- Entity
        Entity entity = backend().entitybox().get();

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        emitter().emit("#ifndef __%s__", instanceName.toUpperCase());
        emitter().emit("#define __%s__", instanceName.toUpperCase());
        emitter().emitNewLine();

        // -- Includes
        defineIncludes(true);

        // -- IO Struct

        structIO(instanceName, entity);

        // -- Enum State
        if (entity instanceof CalActor) {
            CalActor actor = (CalActor) entity;
            if (actor.getProcessDescription() == null)
                enumStates(actor);
        }


        // -- Instance State
        instanceClass(instanceName, entity);

        emitter().emit("#endif // __%s__", instanceName.toUpperCase());
        emitter().emitNewLine();

        // -- EOF
        emitter().close();
    }


    /*
     * Instance headers
     */

    default void defineIncludes(boolean isHeader) {
        if (isHeader) {
            backend().includeSystem("ap_int.h");
            backend().includeSystem("hls_stream.h");
            backend().includeSystem("stdint.h");
            backend().includeSystem("stddef.h");
            backend().includeUser("globals.h");
        } else {
            Instance instance = backend().instancebox().get();
            String headerName = instance.getInstanceName() + ".h";

            backend().includeUser(headerName);
        }
        emitter().emitNewLine();
    }

    /*
     * Struct IO
     */

    default void structIO(String instanceName, Entity actor) {
        // -- Struct IO
        emitter().emit("// -- Struct IO");
        emitter().emit("struct IO_%s {", instanceName);
        {
            emitter().increaseIndentation();

            for (PortDecl port : actor.getInputPorts()) {
                Type type = backend().types().declaredPortType(port);
                emitter().emit("%s;", backend().declarations().declaration(type, String.format("%s_peek", port.getName())));
                emitter().emit("int %s_count;", port.getName());
            }

            for (PortDecl port : actor.getOutputPorts()) {
                emitter().emit("int %s_size;", port.getName());
                emitter().emit("int %s_count;", port.getName());
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();
        if (actor instanceof ActorMachine) {
            ActorMachine am = (ActorMachine) actor;

            if (backend().context().getConfiguration().get(PlatformSettings.defaultController) == PlatformSettings.ControllerKind.BC || am.controller().getStateList().size() > MAX_STATES_FOR_QUICK_JUMP_CONTROLLER) {
                emitter().emit("struct StateReturn {");
                emitter().emit("\tint program_counter;");
                emitter().emit("\tint return_code;");
                emitter().emit("};");
                emitter().emitNewLine();
            }
        }
    }

    /*
     * Enum States
     */

    default void enumStates(CalActor actor) {
        Schedule schedule = new Schedule(actor);

        Map<String, List<Action>> eligibleStates = schedule.getEligible();

        emitter().emit("enum states{");
        emitter().increaseIndentation();

        List<String> states = new ArrayList<>(eligibleStates.keySet());
        for (String state : states) {
            emitter().emit("s_%s%s", state, states.indexOf(states) == states.size() - 1 ? "" : ",");
        }

        emitter().decreaseIndentation();
        emitter().emit("};");
        emitter().emitNewLine();

        // -- StateReturn
        emitter().emit("struct StateReturn {");
        emitter().emit("\tstates fsmState;");
        emitter().emit("\tint returnCode;");
        emitter().emit("};");
        emitter().emitNewLine();
    }

    /*
     * Instance class
     */


    void instanceClass(String instanceName, Entity entity);

    default void instanceClass(String instanceName, CalActor actor) {

        emitter().emit("// -- Instance Class");
        String className = "class_" + instanceName;
        emitter().emit("class %s {", className);


        if (!actor.getVarDecls().isEmpty()) {
            emitter().emit("public:");
            emitter().increaseIndentation();

            emitter().emit("states _FSM_state;");

            for (VarDecl var : actor.getVarDecls()) {
                if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                    backend().callables().callablePrototypes(instanceName, var.getValue());
                } else {
                    Type type = backend().types().declaredType(var);
                    if (type instanceof ListType) {
                        if (backend().externalMemory().isStoredExternally(var)) {
                            ListType listType = (ListType) type;
                            emitter().emit("%s* %s;",
                                    backend().typeseval().type(
                                            listType.getElementType()),
                                    backend().variables().declarationName(var));
                        } else {
                            String decl = declarations().declaration(types().declaredType(var),
                                    backend().variables().declarationName(var));
                            emitter().emit("%s%s;", var.isConstant() ? "const " : "", decl);
                        }
                    } else {
                        String decl = declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                        emitter().emit("%s%s;", (var.isConstant() && !(var.getValue() instanceof ExprInput)) ? "const " : "", decl);
                    }
                }
            }
            emitter().emit("// -- State functions");
            Schedule schedule = new Schedule(actor);
            schedule.getEligible().keySet().forEach(s -> emitter().emit("%s;", backend().calActorController().stateFunctionPrototype(instanceName, false, s)));

            emitter().emit("// -- Guards");
            actor.getActions().forEach(a -> emitter().emit("%s;", actionGuardPrototype(instanceName, a, false)));
            emitter().emitNewLine();

            emitter().emit("// -- Actions");
            actor.getActions().forEach(a -> emitter().emit("%s;", actionBodyPrototype(instanceName, a, false)));
            emitter().emitNewLine();


        }
        emitter().decreaseIndentation();
        emitter().emitNewLine();

        // -- Public
        emitter().emit("public:");
        emitter().increaseIndentation();

        instanceConstructor(instanceName, actor);

        emitter().emit("int operator()(%s);", entityPorts(instanceName, actor.getProcessDescription() == null, actor.getProcessDescription() == null));

        emitter().decreaseIndentation();

        emitter().emit("};");
        emitter().emitNewLine();

    }

    default void instanceClass(String instanceName, ActorMachine actor) {
        emitter().emit("// -- Instance Class");
        String className = "class_" + instanceName;
        emitter().emit("class %s {", className);

        emitter().emit("public:");
        {
            emitter().increaseIndentation();
            for (Scope scope : actor.getScopes()) {
                if (!scope.getDeclarations().isEmpty()) {
                    emitter().emit("// -- Scope %d", actor.getScopes().indexOf(scope));
                    for (VarDecl var : scope.getDeclarations()) {
                        if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                            backend().callables().callablePrototypes(instanceName, var.getValue());
                        } else {
                            Type type = backend().types().declaredType(var);
                            if (type instanceof ListType) {
                                if (backend().externalMemory().isStoredExternally(var)) {
                                    ListType listType = (ListType) type;
                                    emitter().emit("%s* %s;",
                                            backend().typeseval().type(
                                                    listType.getElementType()),
                                            backend().variables().declarationName(var));
                                } else {
                                    String decl = declarations().declaration(types().declaredType(var),
                                            backend().variables().declarationName(var));
                                    emitter().emit("%s%s;", var.isConstant() ? "const " : "", decl);
                                }
                            } else {
                                String decl = declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                                emitter().emit("%s%s;", (var.isConstant() && !(var.getValue() instanceof ExprInput)) ? "const " : "", decl);
                            }
                        }
                    }
                }
            }

            emitter().emit("// -- Actor machine state");
            emitter().emit("int program_counter;");
            emitter().emit("// -- Actor return value");
            emitter().emit("unsigned int __ret;");
            emitter().emitNewLine();

            if (backend().complexReadBox().get())
                if (!actor.getInputPorts().isEmpty()) {
                    emitter().emit("// -- PinConsume");
                    for (PortDecl port : actor.getInputPorts()) {
                        emitter().emit("uint32_t __consume_%s;", port.getName());
                    }
                }

            if (!actor.getValueParameters().isEmpty()) {
                emitter().emit("// -- Parameters");
                for (ParameterVarDecl vp : actor.getValueParameters()) {
                    String decl = declarations().declaration(types().declaredType(vp), backend().variables().declarationName(vp));
                    emitter().emit("%s;", decl);
                }
                emitter().emitNewLine();
            }


            if (backend().context().getConfiguration().get(PlatformSettings.defaultController) == PlatformSettings.ControllerKind.BC || actor.controller().getStateList().size() > MAX_STATES_FOR_QUICK_JUMP_CONTROLLER) {
                // -- State Functions
                emitter().emit("// -- State Functions");
                backend().branchingController().waitTargetBitSets(actor).stream().forEach(s -> {
                    emitter().emit("%s;", backend().branchingController().stateFunctionPrototype(instanceName, false, s));
                });
                emitter().emitNewLine();
            }


            emitter().emit("// -- Scopes");
            for (Scope s : actor.getScopes()) {
                if (!(s.getDeclarations().isEmpty() || actor.getScopes().indexOf(s) == 0)) {
                    emitter().emit("%s;", scopePrototype(instanceName, s, actor.getScopes().indexOf(s), false));
                }
            }
            emitter().emit("// -- Conditions");
            actor.getConditions().forEach(c -> emitter().emit("%s;", conditionPrototype(instanceName, c, actor.getConditions().indexOf(c), false)));
            emitter().emitNewLine();

            emitter().emit("// -- Transitions");
            actor.getTransitions().forEach(t -> emitter().emit("%s;", transitionPrototype(instanceName, t, actor.getTransitions().indexOf(t), false)));
            emitter().emitNewLine();

            emitter().decreaseIndentation();
        }

        // -- Public
        emitter().emit("public:");
        {
            emitter().increaseIndentation();

            instanceConstructor(instanceName, actor);

            emitter().emit("int operator()(%s);", entityPorts(instanceName, true, true));

            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();
    }

    default void instanceConstructor(String instanceName, ActorMachine actor) {
        // -- External memories

        List<String> initializations = new ArrayList<>();
        for (Scope scope : actor.getScopes()) {
            if (!scope.getDeclarations().isEmpty()) {
                emitter().emit("// -- Scope %d", actor.getScopes().indexOf(scope));
                for (VarDecl var : scope.getDeclarations()) {
                    if (scope.isPersistent()) {
                        String decl = backend().variables().declarationName(var);
                        if (var.getValue() != null && !(var.getValue() instanceof ExprInput)) {
                            if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                                // -- Do nothing
                            } else {
                                Interpreter interpreter = backend().interpreter();
                                Environment environment = new Environment();
                                Value value = interpreter.eval(var.getValue(), environment);
                                Expression expression = backend().converter().apply(value);
                                if (expression instanceof ExprList) {
                                    initializations.add(String.format("%s%s", decl, backend().expressioneval().evaluateWithoutTemp((ExprList) expression)));
                                } else {
                                    initializations.add(String.format("%s(%s)", decl, backend().expressioneval().evaluate(expression)));
                                }
                            }
                        }
                    }
                }
            }
        }


        String className = "class_" + instanceName;
        emitter().emit("%s():", className);
        emitter().increaseIndentation();
        emitter().emit("program_counter(%d)%s", backend().quickJumpController().stateMap(actor.controller().getStateList()).get(actor.controller().getInitialState()), initializations.isEmpty() ? "" : ",");

        for (String init : initializations) {
            if (initializations.indexOf(init) == initializations.size() - 1) {
                emitter().emit(init);
            } else {
                emitter().emit("%s,", init);
            }
        }

        emitter().decreaseIndentation();


        emitter().emit("{");
        {
            emitter().increaseIndentation();


            // -- Parameters
            Instance instance = backend().instancebox().get();

            for (ParameterVarDecl par : actor.getValueParameters()) {
                boolean assigned = false;
                for (Parameter<Expression, ?> assignment : instance.getValueParameters()) {
                    if (par.getName().equals(assignment.getName())) {
                        emitter().emit("this->%s = %s;", backend().variables().declarationName(par), expressioneval().evaluate(assignment.getValue()));
                        assigned = true;
                    }
                }
                if (!assigned) {
                    throw new RuntimeException(String.format("Could not assign to %s. Candidates: {%s}.", par.getName(), String.join(", ", instance.getValueParameters().map(Parameter::getName))));
                }
            }
            if (backend().complexReadBox().get())
                if (!actor.getInputPorts().isEmpty()) {
                    emitter().emit("// -- PinConsume");
                    for (PortDecl port : actor.getInputPorts()) {
                        emitter().emit("__consume_%s = 0;", port.getName());
                    }
                }

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }


    default void instanceConstructor(String instanceName, CalActor actor) {
        // -- External memories

        String className = "class_" + instanceName;
        emitter().emit("%s(){", className);
        {
            emitter().increaseIndentation();

            emitter().emit("_FSM_state = s_%s; ", actor.getScheduleFSM().getInitialState());

            for (VarDecl var : actor.getVarDecls()) {
                String decl = backend().variables().declarationName(var);
                if (var.getValue() != null && !(var.getValue() instanceof ExprInput)) {
                    if (var.getValue() instanceof ExprList) {
                        emitter().emit("{");
                        emitter().increaseIndentation();

                        backend().statements().copy(types().declaredType(var), backend().variables().declarationName(var), types().type(var.getValue()), expressioneval().evaluate(var.getValue()));

                        emitter().decreaseIndentation();
                        emitter().emit("}");
                    } else if (var.getValue() instanceof ExprComprehension) {
                        emitter().emit("{");
                        emitter().increaseIndentation();

                        Interpreter interpreter = backend().interpreter();
                        Environment environment = new Environment();
                        Value value = interpreter.eval((ExprComprehension) var.getValue(), environment);
                        Expression expression = backend().converter().apply(value);

                        backend().statements().copy(types().declaredType(var), backend().variables().declarationName(var), types().type(var.getValue()), expressioneval().evaluate(expression));

                        emitter().decreaseIndentation();
                        emitter().emit("}");
                    } else if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                        // -- Do nothing
                    } else {
                        Interpreter interpreter = backend().interpreter();
                        Environment environment = new Environment();
                        Value value = interpreter.eval((ExprComprehension) var.getValue(), environment);
                        Expression expression = backend().converter().apply(value);
                        emitter().emit("%s = %s;", decl, backend().expressioneval().evaluate(expression));
                    }
                }

            }


            // -- Parameters
            Instance instance = backend().instancebox().get();

            for (ParameterVarDecl par : actor.getValueParameters()) {
                boolean assigned = false;
                for (Parameter<Expression, ?> assignment : instance.getValueParameters()) {
                    if (par.getName().equals(assignment.getName())) {
                        emitter().emit("this->%s = %s;", backend().variables().declarationName(par), expressioneval().evaluate(assignment.getValue()));
                        assigned = true;
                    }
                }
                if (!assigned) {
                    throw new RuntimeException(String.format("Could not assign to %s. Candidates: {%s}.", par.getName(), String.join(", ", instance.getValueParameters().map(Parameter::getName))));
                }
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }


    default String entityPorts(String instanceName, boolean withIO, boolean withExternalMemories) {
        Entity entity = backend().entitybox().get();
        List<String> ports = new ArrayList<>();

        if (withExternalMemories) {
            // -- External memories
            for (VarDecl decl : backend().externalMemory().getExternalMemories(entity)) {
                ListType listType = (ListType) backend().types().declaredType(decl);
                String mem = String.format("%s* %s", backend().typeseval().type(listType.getElementType()),
                        backend().externalMemory().memories().name(instanceName, decl));
                ports.add(mem);
            }
        }

        // -- Input Ports
        for (PortDecl port : entity.getInputPorts()) {
            ports.add(backend().declarations().portDeclaration(port));
        }

        // -- Output Ports
        for (PortDecl port : entity.getOutputPorts()) {
            ports.add(backend().declarations().portDeclaration(port));
        }

        // -- IO port
        if (withIO) {
            ports.add(String.format("IO_%s io", instanceName));
        }

        return String.join(", ", ports);
    }


    /*
     * Top of Instance
     */

    void topOfInstance(String instanceName, Entity entity);

    default void topOfInstance(String instanceName, CalActor actor) {
        String className = "class_" + instanceName;
        emitter().emit("int %s::operator()(%s) {", className, entityPorts(instanceName, actor.getProcessDescription() == null, true));
        emitter().emit("#pragma HLS INLINE");
        {
            emitter().increaseIndentation();


            boolean traceEnabled = backend().context().getConfiguration().isDefined(PlatformSettings.enableActionProfile) &&
                    backend().context().getConfiguration().get(PlatformSettings.enableActionProfile);
            if (traceEnabled) {
                emitter().emit("unsigned int action_id = 0;");
                emitter().emit("unsigned int action_size = 0;");
            }
            // -- External Memories
            if (!backend().externalMemory().getExternalMemories(actor).isEmpty()) {
                emitter().emit("// -- Initialize large memory pointers");
                for (VarDecl var : backend().externalMemory().getExternalMemories(actor)) {
                    String decl = backend().variables().declarationName(var);
                    String portName = backend().externalMemory().name(instanceName, var);
                    emitter().emit("%s = %s;", decl, portName);
                }
                emitter().emitNewLine();
            }

            if (actor.getProcessDescription() != null) {
                actor.getProcessDescription().getStatements().forEach(backend().statements()::execute);
            } else {
                emitter().emit("StateReturn _ret;");
                emitter().emitNewLine();

                backend().calActorController().emitController(instanceName, actor);

                emitter().emitNewLine();
                emitter().emit("_FSM_state = _ret.fsmState;");
                emitter().emit("return _ret.returnCode;");
            }
            emitter().decreaseIndentation();
        }

        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void topOfInstance(String instanceName, ActorMachine actor) {
        String className = "class_" + instanceName;
        emitter().emit("int %s::operator()(%s) {", className, entityPorts(instanceName, true, true));
        emitter().emit("#pragma HLS INLINE");
        {

            PlatformSettings.ControllerKind controller = backend().context().getConfiguration().get(PlatformSettings.defaultController);

            emitter().increaseIndentation();
            if (controller == PlatformSettings.ControllerKind.QJ && actor.controller().getStateList().size() < MAX_STATES_FOR_QUICK_JUMP_CONTROLLER) {
                emitter().emit("int _ret = RETURN_WAIT;");
            } else {
                emitter().emit("StateReturn _ret;");
            }

            if (controller == PlatformSettings.ControllerKind.QJ) {
                boolean traceEnabled = backend().context().getConfiguration().isDefined(PlatformSettings.enableActionProfile) &&
                        backend().context().getConfiguration().get(PlatformSettings.enableActionProfile);
                if (traceEnabled) {
                    emitter().emit("unsigned int action_id = 0;");
                    emitter().emit("unsigned int action_size = 0;");
                }
            }
            // -- External Memories
            if (!backend().externalMemory().getExternalMemories(actor).isEmpty()) {
                emitter().emit("// -- Initialize large memory pointers");
                for (VarDecl var : backend().externalMemory().getExternalMemories(actor)) {
                    String decl = backend().variables().declarationName(var);
                    String portName = backend().externalMemory().name(instanceName, var);
                    emitter().emit("%s = %s;", decl, portName);
                }
                emitter().emitNewLine();
            }

            if (controller == PlatformSettings.ControllerKind.QJ) {
                if (actor.controller().getStateList().size() > MAX_STATES_FOR_QUICK_JUMP_CONTROLLER) {
                    backend().branchingController().emitController(instanceName, actor);
                } else {

                    if (hasPipelinedController(actor)) {
                        emitter().emit("IO_%s local_io = io;", instanceName);
                        emitter().emit("bool yield = false;");
                        emitter().emit("while(!yield) {");
                        emitter().emit("#pragma HLS pipeline");
                        {
                            emitter().increaseIndentation();

                            backend().quickJumpController().emitController(instanceName, actor);

                            emitter().decreaseIndentation();
                        }
                        emitter().emit("}");
                    } else {
                        backend().quickJumpController().emitController(instanceName, actor);
                    }

                }
            } else {
                backend().branchingController().emitController(instanceName, actor);
            }
            if (controller == PlatformSettings.ControllerKind.QJ && actor.controller().getStateList().size() < MAX_STATES_FOR_QUICK_JUMP_CONTROLLER) {
                emitter().emit("return _ret;");
            } else {
                emitter().emit("this->program_counter = _ret.program_counter;");
                emitter().emit("return _ret.return_code;");
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Scopes

    default void scope(String instanceName, Scope scope, int index) {
        // -- Actor Instance Name
        String className = "class_" + instanceName;
        if (scope.getDeclarations().size() > 0 || scope.isPersistent()) {
            if (index != 0) {
                emitter().emit("%s{", scopePrototype(instanceName, scope, index, true));
                emitter().emit("#pragma HLS INLINE");
                {
                    emitter().increaseIndentation();

                    for (VarDecl var : scope.getDeclarations()) {
                        Type type = types().declaredType(var);
                        if (var.isExternal() && type instanceof CallableType) {
                            // -- Do Nothing
                        } else if (var.getValue() != null) {
                            emitter().emit("{");
                            emitter().increaseIndentation();
                            if (var.getValue() instanceof ExprInput) {
                                expressioneval().evaluateWithLvalue("this->" + backend().variables().declarationName(var), (ExprInput) var.getValue());
                            } else {
                                backend().statements().copy(types().declaredType(var), "this->" + backend().variables().declarationName(var), types().type(var.getValue()), expressioneval().evaluate(var.getValue()));
                            }
                            emitter().decreaseIndentation();
                            emitter().emit("}");
                        }
                    }
                    emitter().decreaseIndentation();
                }
                emitter().emit("}");
                emitter().emitNewLine();
            }
        }
    }

    default String scopePrototype(String instanceName, Scope scope, int index, boolean withClassName) {
        // -- Actor Instance Name
        String className = "class_" + instanceName;
        String io = scopeIO(instanceName, scope);

        return String.format("void %sscope_%d(%s)", withClassName ? className + "::" : "", index, io);
    }


    default String scopeIO(String instanceName, Scope scope) {
        List<String> arguments = new ArrayList<>();
        for (VarDecl decl : scope.getDeclarations()) {
            if (decl.getValue() != null) {
                if (decl.getValue() instanceof ExprInput) {
                    ExprInput input = (ExprInput) decl.getValue();
                    Port port = input.getPort();
                    String arg = String.format("hls::stream< %s > &%s", backend().typeseval().type(backend().types().portType(port)), port.getName());
                    arguments.add(arg);
                }
            }
        }
        arguments.add(String.format("IO_%s io", instanceName));


        return String.join(", ", arguments);
    }

    default String scopeArguments(Scope scope) {
        List<String> arguments = new ArrayList<>();
        for (VarDecl decl : scope.getDeclarations()) {
            if (decl.getValue() != null) {
                if (decl.getValue() instanceof ExprInput) {
                    ExprInput input = (ExprInput) decl.getValue();
                    arguments.add(input.getPort().getName());
                }
            }
        }
        arguments.add("io");
        return String.join(", ", arguments);
    }

    // ------------------------------------------------------------------------
    // -- Conditions

    default void condition(String instanceName, Condition condition, int index) {
        // -- Actor Instance Name
        emitter().emit("%s{", conditionPrototype(instanceName, condition, index, true));
        emitter().emit("#pragma HLS INLINE");
        emitter().increaseIndentation();
        {
            emitter().emit("return %s;", evaluateCondition(condition));
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default String conditionPrototype(String instanceName, Condition condition, int index, boolean withClassName) {
        // -- Actor Instance Name
        String className = "class_" + instanceName;
        String io = conditionIO(condition);
        if (condition instanceof PortCondition) {
            io = io + ", " + String.format("IO_%s io", instanceName);
        } else {
            io = String.format("IO_%s io", instanceName);
        }
        return String.format("bool %scondition_%d(%s)", withClassName ? className + "::" : "", index, io);
    }

    String evaluateCondition(Condition condition);

    default String evaluateCondition(PredicateCondition condition) {
        return expressioneval().evaluate(condition.getExpression());
    }

    default String evaluateCondition(PortCondition condition) {

        boolean pipelined = hasPipelinedController(backend().entitybox().get());

        if (condition.isInputCondition()) {
            if (pipelined) {
                // don't check for the emptyness, since we are going to use local io counters to break the dependencies
                return String.format("(pinAvailIn(%s, io) >= %d)", channelutils().definedInputPort(condition.getPortName()), condition.N());
            } else {
                if (condition.N() > 1) {
                    return String.format("(pinAvailIn(%s, io) >= %d) && !%1$s.empty()", channelutils().definedInputPort(condition.getPortName()), condition.N());
                } else {
                    return String.format("!%1$s.empty()", channelutils().definedInputPort(condition.getPortName()), condition.N());
                }
            }

        } else {
            if (pipelined) {
                // don't check for fullness to break the dependencies, instead use local io counters to burst pipeline actions
                return String.format("(pinAvailOut(%s, io) >= %d)", channelutils().definedOutputPort(condition.getPortName()), condition.N());
            } else {
                if (condition.N() > 1) {
                    return String.format("(pinAvailOut(%s, io) >= %d) && !%1$s.full()", channelutils().definedOutputPort(condition.getPortName()), condition.N());
                } else {
                    return String.format("!%1$s.full()", channelutils().definedOutputPort(condition.getPortName()), condition.N());
                }
            }


        }
    }

    String conditionIO(Condition condition);

    default String conditionIO(PredicateCondition condition) {
        return "";
    }

    default String conditionIO(PortCondition condition) {
        Entity entity = backend().entitybox().get();

        if (condition.isInputCondition()) {
            PortDecl portDecl = entity.getInputPorts().stream().filter(p -> p.getName().equals(condition.getPortName().getName())).findAny().orElse(null);
            return backend().declarations().portDeclaration(portDecl);
        } else {
            PortDecl portDecl = entity.getOutputPorts().stream().filter(p -> p.getName().equals(condition.getPortName().getName())).findAny().orElse(null);
            return backend().declarations().portDeclaration(portDecl);
        }
    }

    // ------------------------------------------------------------------------
    // -- Transitions

    @Binding(BindingKind.LAZY)
    default Map<Port, Boolean> hasRead() {
        return new HashMap<>();
    }

    default void transition(String instanceName, Transition transition, int index) {
        for (Port port : transition.getInputRates().keySet()) {
            hasRead().put(port, false);
        }

        // -- Actor Instance Name
        Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());
        if (annotation.isPresent()) {
            String actionTag = ((ExprLiteral) annotation.get().getParameters().get(0).getExpression()).getText();
            emitter().emit("// -- Action Tag : %s", actionTag);
        }
        emitter().emit("%s{", transitionPrototype(instanceName, transition, index, true));
        emitter().emit("#pragma HLS INLINE");

        // -- Emit transition annotations
        List<Annotation> annotations = transition.getAnnotations();
        for (Annotation ann : annotations) {
            backend().annotations().emitTopAction(ann);
        }
        {
            emitter().increaseIndentation();

            transition.getBody().forEach(backend().statements()::execute);

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        hasRead().clear();
        emitter().emitNewLine();
    }


    default String transitionPrototype(String instanceName, Transition transition, int index, boolean withClassName) {
        // -- Actor Instance Name
        String className = "class_" + instanceName;
        String io = transitionIO(transition);

        return String.format("void %stransition_%d(%s)", withClassName ? className + "::" : "", index, io);
    }


    default String transitionIO(Transition transition) {
        Entity entity = backend().entitybox().get();

        List<String> ports = new ArrayList<>();
        for (Port port : transition.getInputRates().keySet()) {
            PortDecl portDecl = entity.getInputPorts().stream().filter(p -> p.getName().equals(port.getName())).findAny().orElse(null);
            ports.add(backend().declarations().portDeclaration(portDecl));
        }

        for (Port port : transition.getOutputRates().keySet()) {
            PortDecl portDecl = entity.getOutputPorts().stream().filter(p -> p.getName().equals(port.getName())).findAny().orElse(null);
            ports.add(backend().declarations().portDeclaration(portDecl));
        }

        return String.join(", ", ports);
    }


    default String transitionIoArguments(Transition transition) {

        List<String> ports = new ArrayList<>();
        for (Port port : transition.getInputRates().keySet()) {
            ports.add(port.getName());
        }

        for (Port port : transition.getOutputRates().keySet()) {
            ports.add(port.getName());
        }

        return String.join(", ", ports);
    }

    // ------------------------------------------------------------------------
    // -- Callables

    void callables(String instanceName, Entity entity);

    default void callables(String instanceName, CalActor actor) {
        emitter().emit("// -- Callables");
        for (VarDecl decl : actor.getVarDecls()) {
            if (decl.getValue() != null) {
                Expression expr = decl.getValue();
                if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                    backend().callables().callableDefinition(instanceName, expr);
                }
            }
        }
    }

    default void callables(String instanceName, ActorMachine am) {
        emitter().emit("// -- Callables");
        for (Scope scope : am.getScopes()) {
            if (scope.isPersistent()) {
                for (VarDecl decl : scope.getDeclarations()) {
                    if (decl.getValue() != null) {
                        Expression expr = decl.getValue();
                        if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                            backend().callables().callableDefinition(instanceName, expr);
                            emitter().emitNewLine();
                        }
                    }
                }
            }
        }
    }

    default boolean checkReadComplexity(List<Scope> scopes) {
        for (Scope scope : scopes) {
            for (VarDecl decl : scope.getDeclarations()) {
                if (decl.getValue() != null) {
                    if (decl.getValue() instanceof ExprInput) {
                        ExprInput input = (ExprInput) decl.getValue();
                        if (input.hasRepeat()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    // ------------------------------------------------------------------------
    // -- CAL Actor

    default String actionIO(Action action) {
        Entity entity = backend().entitybox().get();

        List<String> ports = new ArrayList<>();
        for (InputPattern input : action.getInputPatterns()) {
            PortDecl portDecl = entity.getInputPorts().stream().filter(p -> p.getName().equals(input.getPort().getName())).findAny().orElse(null);
            ports.add(backend().declarations().portDeclaration(portDecl));
        }

        for (OutputExpression output : action.getOutputExpressions()) {
            PortDecl portDecl = entity.getOutputPorts().stream().filter(p -> p.getName().equals(output.getPort().getName())).findAny().orElse(null);
            ports.add(backend().declarations().portDeclaration(portDecl));
        }

        return String.join(", ", ports);
    }


    default String actionGuardPrototype(String instanceName, Action action, boolean withClassName) {
        // -- Actor Instance Name
        String className = "class_" + instanceName;

        return String.format("bool %sguard_%s(IO_%s io)", withClassName ? className + "::" : "", action.getTag().nameWithUnderscore(), instanceName);
    }


    default String actionBodyPrototype(String instanceName, Action action, boolean withClassName) {
        // -- Actor Instance Name
        String className = "class_" + instanceName;

        String io = actionIO(action);
        return String.format("void %s%s(%s)", withClassName ? className + "::" : "", action.getTag().nameWithUnderscore(), io);
    }


    default void action(String instanceName, Action action) {
        actionGuard(instanceName, action);
        emitter().emitNewLine();
        actionBody(instanceName, action);
    }

    default void actionGuard(String instanceName, Action action) {
        emitter().emit("%s{", actionGuardPrototype(instanceName, action, true));
        emitter().emit("#pragma HLS INLINE");

        // -- Emit transition annotations
        List<Annotation> annotations = action.getAnnotations();
        for (Annotation ann : annotations) {
            backend().annotations().emitTopAction(ann);
        }
        {
            emitter().increaseIndentation();

            if (action.getGuards().isEmpty()) {
                emitter().emit("return true;");
            } else {
                List<String> guards = new ArrayList<>();

                Set<VarDecl> declsUsedInGuards = new HashSet<>();
                for (Expression expression : action.getGuards()) {
                    declsUsedInGuards.addAll(backend().varDecls().declarations(expression));
                }

                for (VarDecl var : declsUsedInGuards) {
                    String decl = backend().variables().declarationName(var);
                    Type type = types().declaredType(var);
                    emitter().emit("%s;", backend().declarations().declaration(type, decl));


                    for (InputPattern pattern : action.getInputPatterns()) {
                        for (Match match : pattern.getMatches()) {
                            if (match.getDeclaration().equals(var)) {
                                if (pattern.getMatches().indexOf(match) == 0) {
                                    if (pattern.getRepeatExpr() != null) {
                                        emitter().emit("pinPeekFront(%s, %s[0]);", backend().channelsutils().definedInputPort(pattern.getPort()), decl);
                                    } else {
                                        emitter().emit("pinPeekFront(%s, %s);", backend().channelsutils().definedInputPort(pattern.getPort()), decl);
                                    }
                                } else {
                                    throw new UnsupportedOperationException("Peek supported only on the head of the queue.");
                                }
                            }
                        }
                    }
                }

                action.getGuards().forEach(g -> {
                    guards.add(expressioneval().evaluate(g));
                });
                emitter().emit("return %s;", String.join(" && ", guards));
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void actionBody(String instanceName, Action action) {
        for (InputPattern input : action.getInputPatterns()) {
            hasRead().put(input.getPort(), false);
        }

        emitter().emit("%s{", actionBodyPrototype(instanceName, action, true));
        emitter().emit("#pragma HLS INLINE");

        // -- Emit transition annotations
        List<Annotation> annotations = action.getAnnotations();
        for (Annotation ann : annotations) {
            backend().annotations().emitTopAction(ann);
        }
        {
            emitter().increaseIndentation();

            // -- Consume
            for (InputPattern pattern : action.getInputPatterns()) {
                for (Match match : pattern.getMatches()) {
                    VarDecl var = match.getDeclaration();
                    String decl = backend().variables().declarationName(var);
                    Type type = types().declaredType(var);
                    emitter().emit("%s;", backend().declarations().declaration(type, decl));
                }
            }

            for (VarDecl decl : action.getVarDecls()) {

                Type t = types().declaredType(decl);
                String declarationName = backend().variables().declarationName(decl);
                String d = backend().declarations().declarationTemp(t, declarationName);
                emitter().emit("%s;", d);
                if (decl.getValue() != null) {

                    if (backend().context().getConfiguration().get(PlatformSettings.arbitraryPrecisionIntegers)) {
                        if (decl.getValue() instanceof ExprLiteral) {
                            ExprLiteral literal = (ExprLiteral) decl.getValue();
                            if (literal.getKind() == ExprLiteral.Kind.Integer) {
                                int radix = literal.intRadix().getAsInt();
                                String value = radix != 8 ? expressioneval().evaluate(decl.getValue()) : expressioneval().evaluate(decl.getValue()).substring(1);
                                emitter().emit("%s = %s(\"%s\", %d);", declarationName, backend().typeseval().type(t), value, radix);
                            } else {
                                backend().statements().copy(t, declarationName, types().type(decl.getValue()), expressioneval().evaluate(decl.getValue()));
                            }
                        } else {
                            backend().statements().copy(t, declarationName, types().type(decl.getValue()), expressioneval().evaluate(decl.getValue()));
                        }
                    } else {
                        backend().statements().copy(t, declarationName, types().type(decl.getValue()), expressioneval().evaluate(decl.getValue()));
                    }
                }
            }

            for (InputPattern pattern : action.getInputPatterns()) {
                for (Match match : pattern.getMatches()) {
                    VarDecl var = match.getDeclaration();
                    String decl = backend().variables().declarationName(var);

                    if (pattern.getRepeatExpr() == null) {
                        emitter().emit("pinRead(%s, %s);", pattern.getPort().getName(), decl);
                    } else {
                        emitter().emit("pinReadRepeat(%s, %s, %s);", pattern.getPort().getName(), decl, expressioneval().evaluate(pattern.getRepeatExpr()));
                    }
                }
            }

            action.getBody().forEach(backend().statements()::execute);

            // -- Produce
            for (OutputExpression output : action.getOutputExpressions()) {
                for (Expression expr : output.getExpressions()) {
                    if (output.getRepeatExpr() == null) {
                        emitter().emit("pinWrite(%s, %s);", output.getPort().getName(), expressioneval().evaluate(expr));
                    } else {
                        emitter().emit("pinWriteRepeat(%s, %s, %s);", backend().channelsutils().definedOutputPort(output.getPort()), expressioneval().evaluate(expr), expressioneval().evaluate(output.getRepeatExpr()));

                    }
                }
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        hasRead().clear();
        emitter().emitNewLine();
    }


}
