package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.Parameter;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.*;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Module
public interface Instances {
    static final int MAX_STATES_FOR_QUICK_JUMP_CONTROLLER = 200;

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


    default void generateInstance(Instance instance) {
        // -- Add instance to box
        backend().instancebox().set(instance);

        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add entity to box
        backend().entitybox().set(entity);



        // -- Generate Source
        generateSource(instance);

        // -- Generate Header
        generateHeader(instance);

        // -- Clear boxes

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
            // -- Scopes
            emitter().emit("// -- Scopes");
            actor.getScopes().forEach(s -> scope(instanceName, s, actor.getScopes().indexOf(s)));

            // -- Conditions
            emitter().emit("// -- Conditions");
            actor.getConditions().forEach(c -> condition(instanceName, c, actor.getConditions().indexOf(c)));

            // -- Transitions
            emitter().emit("// -- Transitions");
            actor.getTransitions().forEach(t -> transition(instanceName, t, actor.getTransitions().indexOf(t)));
        }

        // -- Callables
        callables(instanceName, entity);

        // -- EOF
        emitter().close();
    }

    default void staticCallofInstance(Instance instance) {
        Entity entity = backend().entitybox().get();
        boolean withIO = false;
        if (entity instanceof ActorMachine) {
            withIO = true;
        }

        String name = instance.getInstanceName();
        emitter().emitClikeBlockComment("HLS Top Function");
        emitter().emit("int %s(%s) {", name, entityPorts(name, withIO, true));

        // -- Data pack I/O with algebraic types
        List<String> algebraicIO = new ArrayList<>();
        List<String> algebraicPeek = new ArrayList<>();
        algebraicIO.addAll(entity.getInputPorts().stream().filter(p -> types().declaredPortType(p) instanceof AlgebraicType).map(PortDecl::getName).collect(Collectors.toList()));
        algebraicPeek.addAll(algebraicIO);
        algebraicIO.addAll(entity.getOutputPorts().stream().filter(p -> types().declaredPortType(p) instanceof AlgebraicType).map(PortDecl::getName).collect(Collectors.toList()));
        algebraicIO.forEach(io -> emitter().emit("#pragma HLS DATA_PACK variable=%s", io));
        algebraicPeek.forEach(peek -> emitter().emit("#pragma HLS DATA_PACK variable=io.%s_peek", peek));

        // -- Large state variables to external memories
        for (VarDecl decl : backend().externalMemory().getExternalMemories(entity)) {
            ListType listType = (ListType) types().declaredType(decl);
            List<Integer> dim = backend().typeseval().sizeByDimension(listType);
            Long listDepth = backend().externalMemory().memories().listDepth(listType);
            String memoryName = backend().externalMemory().name(instance, decl);
            emitter().emit("#pragma HLS INTERFACE m_axi depth=%d port=%s offset=direct bundle=%2$s", listDepth, memoryName);
        }
        emitter().increaseIndentation();

        // -- Static call
        String className = "class_" + instance.getInstanceName();
        emitter().emit("static %s i_%s;", className, name);
        emitter().emitNewLine();

        List<String> ports = new ArrayList<>();


        // -- External memories
        ports.addAll(
                backend().externalMemory()
                        .getExternalMemories(entity).map(v -> backend().externalMemory().name(instance, v)));

        ports.addAll(entity.getInputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));
        ports.addAll(entity.getOutputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));

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
                //throw new UnsupportedOperationException("Actors is not a Process.");
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
        if (entity instanceof ActorMachine) {
            ActorMachine actor = (ActorMachine) entity;
            structIO(actor);
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

    default void structIO(ActorMachine actor) {
        // -- Struct IO
        emitter().emit("// -- Struct IO");
        emitter().emit("struct IO {");
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
    }


    /*
     * Instance class
     */


    void instanceClass(String instanceName, Entity entity);

    default void instanceClass(String instanceName, CalActor actor) {

        emitter().emit("// -- Instance Class");
        String className = "class_" + instanceName;
        emitter().emit("class %s {", className);

        // -- Private
        if (!actor.getVarDecls().isEmpty()) {
            emitter().emit("private:");
            emitter().increaseIndentation();

            for (VarDecl var : actor.getVarDecls()) {
                if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                    backend().callables().callablePrototypes(instanceName, var.getValue());
                } else {
                    String decl = declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                    if (var.getValue() != null) {
                        emitter().emit("%s = %s;", decl, backend().expressioneval().evaluate(var.getValue()));
                    } else {
                        emitter().emit("%s;", decl);
                    }
                }
            }
        }
        emitter().decreaseIndentation();
        emitter().emitNewLine();

        // -- Public
        emitter().emit("public:");
        emitter().increaseIndentation();

        emitter().emit("int operator()(%s);", entityPorts(instanceName,false, false));

        emitter().decreaseIndentation();

        emitter().emit("};");
        emitter().emitNewLine();

    }

    default void instanceClass(String instanceName, ActorMachine actor) {
        emitter().emit("// -- Instance Class");
        String className = "class_" + instanceName;
        emitter().emit("class %s {", className);

        // -- Private
        emitter().emit("private:");
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
                                    emitter().emit("%s;", decl);
                                }
                            } else {
                                String decl = declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                                emitter().emit("%s;", decl);
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

            if (!actor.getValueParameters().isEmpty()) {
                emitter().emit("// -- Parameters");
                for (ParameterVarDecl vp : actor.getValueParameters()) {
                    String decl = declarations().declaration(types().declaredType(vp), backend().variables().declarationName(vp));
                    emitter().emit("%s;", decl);
                }
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

        String className = "class_" + instanceName;
        emitter().emit("%s(){", className);
        {
            emitter().increaseIndentation();

            for (Scope scope : actor.getScopes()) {
                if (!scope.getDeclarations().isEmpty()) {
                    emitter().emit("// -- Scope %d", actor.getScopes().indexOf(scope));
                    for (VarDecl var : scope.getDeclarations()) {
                        if (scope.isPersistent()) {
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

                                    backend().expressioneval().evaluate(var.getValue());

                                    emitter().decreaseIndentation();
                                    emitter().emit("}");
                                } else if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                                    // -- Do nothing
                                } else {
                                    emitter().emit("%s = %s;", decl, backend().expressioneval().evaluate(var.getValue()));
                                }
                            }
                        }

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
            ports.add("IO io");
        }

        return String.join(", ", ports);
    }


    /*
     * Top of Instance
     */

    void topOfInstance(String instanceName, Entity entity);

    default void topOfInstance(String instanceName, CalActor actor) {
        if (actor.getProcessDescription() != null) {
            String className = "class_" + instanceName;
            emitter().emit("int %s::operator()(%s) {", className, entityPorts(instanceName, false, true));
            emitter().emit("#pragma HLS INLINE");
            {
                emitter().increaseIndentation();

                // -- External Memories
                for (VarDecl var : backend().externalMemory().getExternalMemories(actor)) {
                    String decl = backend().variables().declarationName(var);
                    String portName = backend().externalMemory().name(instanceName, var);
                    emitter().emit("%s = %s;", decl, portName);
                }

                actor.getProcessDescription().getStatements().forEach(backend().statements()::execute);

                emitter().emit("return RETURN_EXECUTED;");

                emitter().decreaseIndentation();
            }

            emitter().emit("}");

        } else {
            //throw new UnsupportedOperationException("Actors is not a Process.");
        }
    }

    default void topOfInstance(String instanceName, ActorMachine actor) {
        String className = "class_" + instanceName;
        emitter().emit("int %s::operator()(%s) {", className, entityPorts(instanceName, true, true));
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

            if (actor.controller().getStateList().size() > MAX_STATES_FOR_QUICK_JUMP_CONTROLLER) {
                backend().fsmController().emitController(instanceName, actor);
            } else {
                backend().quickJumpController().emitController(instanceName, actor);

            }
            emitter().emit("return this->__ret;");
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
        String io = scopeIO(scope);

        return String.format("void %sscope_%d(%s)", withClassName ? className + "::" : "", index, io);
    }


    default String scopeIO(Scope scope) {
        return "IO io";
    }

    default String scopeArguments(Scope scope) {
        return "io";
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
            io = io + ", " + "IO io";
        }
        return String.format("bool %scondition_%d(%s)", withClassName ? className + "::" : "", index, io);
    }

    String evaluateCondition(Condition condition);

    default String evaluateCondition(PredicateCondition condition) {
        return expressioneval().evaluate(condition.getExpression());
    }

    default String evaluateCondition(PortCondition condition) {
        if (condition.isInputCondition()) {
            if (condition.N() > 1) {
                return String.format("(pinAvailIn(%s, io) >= %d) && !%1$s.empty()", channelutils().definedInputPort(condition.getPortName()), condition.N());
            } else {
                return String.format("!%1$s.empty()", channelutils().definedInputPort(condition.getPortName()), condition.N());
            }
        } else {
            if (condition.N() > 1) {
                return String.format("(pinAvailOut(%s, io) >= %d) && !%1$s.full()", channelutils().definedOutputPort(condition.getPortName()), condition.N());
            } else {
                return String.format("!%1$s.full()", channelutils().definedOutputPort(condition.getPortName()), condition.N());
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

    default void transition(String instanceName, Transition transition, int index) {
        // -- Actor Instance Name
        emitter().emit("%s{", transitionPrototype(instanceName, transition, index, true));
        emitter().emit("#pragma HLS INLINE");
        {
            emitter().increaseIndentation();

            transition.getBody().forEach(backend().statements()::execute);

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
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
        String className = "class_" + instanceName;
        for (VarDecl decl : actor.getVarDecls()) {
            if (decl.getValue() != null) {
                Expression expr = decl.getValue();
                if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                    backend().callables().callableDefinition(className, expr);
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

}
