package ch.epfl.vlsc.hls.backend;

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
import se.lth.cs.tycho.ir.entity.am.*;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Module
public interface Instances {
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
        Path instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".cpp");
        emitter().open(instanceTarget);

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        // -- Includes
        defineIncludes(false);

        // -- Static call of instance
        staticCallofInstance(instance);


        // -- EOF
        emitter().close();
    }

    default void staticCallofInstance(Instance instance) {
        Entity entity = backend().entitybox().get();
        boolean withIO = false;
        if (entity instanceof ActorMachine) {
            withIO = true;
        }

        String name = backend().instaceQID(instance.getInstanceName(), "_");
        emitter().emit("int %s(%s) {", name, entityPorts(withIO));
        emitter().emit("#pragma HLS INTERFACE ap_ctrl_hs register port=return");
        emitter().increaseIndentation();

        String className = "class_" + backend().instaceQID(instance.getInstanceName(), "_");
        emitter().emit("static %s i_%s;", className, name);
        emitter().emitNewLine();


        List<String> ports = entity.getInputPorts().stream().map(PortDecl::getName).collect(Collectors.toList());
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
    }


    default void generateHeader(Instance instance) {
        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenInclude(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".h");
        emitter().open(instanceTarget);

        // -- Entity
        Entity entity = backend().entitybox().get();

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        // -- Includes
        defineIncludes(true);

        // -- IO Struct
        if (entity instanceof ActorMachine) {
            ActorMachine actor = (ActorMachine) entity;
            structIO(actor);
        }

        // -- Instance State
        instanceClass(instanceName, entity);

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


    /*
     * Instance headers
     */

    default void defineIncludes(boolean isHeader) {
        if (isHeader) {
            backend().includeSystem("ap_int.h");
            backend().includeSystem("hls_stream.h");
            backend().includeSystem("stdint.h");
            backend().includeUser("globals.h");
        } else {
            Instance instance = backend().instancebox().get();
            String headerName = backend().instaceQID(instance.getInstanceName(), "_") + ".h";

            backend().includeUser(headerName);
        }
        emitter().emitNewLine();
    }

    /*
     * Struct IO
     */

    default void structIO(ActorMachine actor){
        // -- Struct IO
        emitter().emit("// -- Struct IO");
        emitter().emit("struct IO {");
        {
            emitter().increaseIndentation();

            for(PortDecl port : actor.getInputPorts()){
                Type type = backend().types().declaredPortType(port);
                emitter().emit("%s;",backend().declarations().declaration(type, String.format("%s_peek",port.getName())));
                emitter().emit("int %s_count;", port.getName());
            }

            for(PortDecl port : actor.getOutputPorts()){
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
        String className = "class_" + backend().instaceQID(instanceName, "_");
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
                    if (var.getValue() != null ) {
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

        emitter().emit("int operator()(%s);", entityPorts(false));

        emitter().decreaseIndentation();

        emitter().emit("};");
        emitter().emitNewLine();

    }

    default void instanceClass(String instanceName, ActorMachine actor) {
        emitter().emit("// -- Instance Class");
        String className = "class_" + backend().instaceQID(instanceName, "_");
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
                            String decl = declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                            if (var.getValue() != null && !(var.getValue() instanceof ExprInput)) {
                                if(var.getValue() instanceof ExprList){
                                    emitter().emit("%s = {%s};", decl, backend().expressioneval().evaluateExprList(var.getValue()));
                                }else {
                                    emitter().emit("%s = %s;", decl, backend().expressioneval().evaluate(var.getValue()));
                                }
                            } else {
                                emitter().emit("%s;", decl);
                            }
                        }
                    }
                }
            }

            emitter().emit("// -- Actor machine state");
            emitter().emit("int program_counter;");
            emitter().emitNewLine();

            emitter().emit("// -- Scopes");
            for (Scope s : actor.getScopes()) {
                if (!(s.getDeclarations().isEmpty() || actor.getScopes().indexOf(s) == 0)) {
                    emitter().emit("%s;", scopePrototype(instanceName, s, actor.getScopes().indexOf(s), false));
                }
            }
            emitter().emit("// -- Conditions");
            actor.getConditions().forEach(c -> emitter().emit("%s;", conditionPrototype(instanceName, c, actor.getConditions().indexOf(c), false)));
            emitter().emit("// -- Transitions");
            actor.getTransitions().forEach(t -> emitter().emit("%s;", transitionPrototype(instanceName, t, actor.getTransitions().indexOf(t), false)));


            emitter().decreaseIndentation();
            emitter().emitNewLine();
        }

        // -- Public
        emitter().emit("public:");
        {
            emitter().increaseIndentation();

            emitter().emit("int operator()(%s);", entityPorts(true));

            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();
    }


    default String entityPorts(boolean withIO) {
        Entity entity = backend().entitybox().get();
        List<String> ports = new ArrayList<>();
        for (PortDecl port : entity.getInputPorts()) {
            ports.add(backend().declarations().portDeclaration(port));
        }

        for (PortDecl port : entity.getOutputPorts()) {
            ports.add(backend().declarations().portDeclaration(port));
        }

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
            String className = "class_" + backend().instaceQID(instanceName, "_");
            emitter().emit("int %s::operator()(%s) {", className, entityPorts(false));
            emitter().emit("#pragma HLS INLINE");
            {
                emitter().increaseIndentation();

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
        String className = "class_" + backend().instaceQID(instanceName, "_");
        emitter().emit("int %s::operator()(%s) {", className, entityPorts(true));
        emitter().emit("#pragma HLS INLINE");
        {
            emitter().increaseIndentation();

            emitter().emit("int ret = RETURN_IDLE;");

            backend().controllers().emitController(instanceName, actor);

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    // ------------------------------------------------------------------------
    // -- Scopes

    default void scope(String instanceName, Scope scope, int index) {
        // -- Actor Instance Name
        String className = "class_" + backend().instaceQID(instanceName, "_");
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
        String className = "class_" + backend().instaceQID(instanceName, "_");
        String io = scopeIO(scope);

        return String.format("void %sscope_%d(%s)", withClassName ? className + "::" : "", index, io);
    }


    default String scopeIO(Scope scope) {
        return "IO io";
    }

    default String scopeArguments(Scope scope){
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
        String className = "class_" + backend().instaceQID(instanceName, "_");
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
            if(condition.N() > 1){
                return String.format("(pinAvailIn(%s, io) >= %d) && !%1$s.empty()", channelutils().definedInputPort(condition.getPortName()), condition.N());
            }else {
                return String.format("!%1$s.empty()", channelutils().definedInputPort(condition.getPortName()), condition.N());
            }
        } else {
            if(condition.N() > 1){
                return String.format("(pinAvailOut(%s, io) >= %d) && !%1$s.full()", channelutils().definedOutputPort(condition.getPortName()), condition.N());
            }else{
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
    }


    default String transitionPrototype(String instanceName, Transition transition, int index, boolean withClassName) {
        // -- Actor Instance Name
        String className = "class_" + backend().instaceQID(instanceName, "_");
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
        String className = "class_" + backend().instaceQID(instanceName, "_");
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
