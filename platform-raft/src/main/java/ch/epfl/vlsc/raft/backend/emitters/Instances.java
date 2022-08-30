package ch.epfl.vlsc.raft.backend.emitters;


import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.Parameter;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Condition;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.PredicateCondition;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.expr.ExprComprehension;
import se.lth.cs.tycho.ir.expr.ExprInput;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprList;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;
import ch.epfl.vlsc.raft.backend.RaftBackend;

import java.nio.file.Path;
import java.util.Optional;

@Module
public interface Instances {

    String ACC_ANNOTATION = "acc";

    @Binding(BindingKind.INJECTED)
    RaftBackend backend();

    default Declarations declarations() {
        return backend().declarations();
    }

    default Variables variables() {
        return backend().variables();
    }

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default Types types() {
        return backend().types();
    }

    default void emitInstance(Instance instance) {
        // -- Add instance to box
        backend().instancebox().set(instance);

        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add entity to box
        backend().entitybox().set(entity);

        // -- Emit Header
        emitHeader(instance);

        // -- Emit Sources
        emitSource(instance);

        // -- Clear boxes
        backend().entitybox().clear();
        backend().instancebox().clear();
    }

    default void emitSource(Instance instance) {
        // -- Target file Path
        Path instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(instance.getInstanceName() + ".cpp");
        emitter().open(instanceTarget);
        // -- Entity
        Entity entity = backend().entitybox().get();

        assert entity instanceof ActorMachine;
        ActorMachine actorMachine = (ActorMachine) entity;

        // -- Instance Name
        String instanceName = instance.getInstanceName();

        // -- Includes
        defineIncludes(false);

        // -- Controller (aka Action selection)
        backend().controllers().emitController(instanceName, actorMachine);
        emitter().emitNewLine();

        ActorMachine actor = (ActorMachine) entity;
        // -- Scopes
        emitter().emit("// -- Scopes");
        actor.getScopes().forEach(s -> scope(instanceName, s, actor.getScopes().indexOf(s)));

        // -- Conditions
        emitter().emit("// -- Conditions");
        actor.getConditions().forEach(c -> condition(instanceName, c, actor.getConditions().indexOf(c)));

        // -- Transitions
        emitter().emit("// -- Transitions");

        actor.getTransitions().forEach(t -> transition(instanceName, t, actor.getTransitions().indexOf(t), actorMachine));

        // -- Callables
        callables(instanceName, actorMachine);

        // -- EOF
        emitter().close();
    }

    default void emitHeader(Instance instance) {
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

        assert entity instanceof ActorMachine;
        ActorMachine actorMachine = (ActorMachine) entity;

        // -- Class
        instanceClass(instanceName, actorMachine);

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
            backend().includeSystem("raft");
            backend().includeSystem("stdint.h");
            backend().includeUser("globals.h");
            backend().includeUser("prelude.h");
        } else {
            Instance instance = backend().instancebox().get();
            String headerName = instance.getInstanceName() + ".h";

            backend().includeUser(headerName);
            backend().includeUser("natives.h");
        }
        emitter().emitNewLine();
    }

    default void instanceClass(String instanceName, ActorMachine actor) {
        emitter().emit("// -- Instance Class");

        emitter().emit("class %s: public raft::kernel {", instanceName);

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
                            emitter().emit("%s;", decl);
                        }
                    }
                }
            }

            emitter().emit("// -- Actor machine state");
            emitter().emit("int program_counter;");

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
            emitter().emitNewLine();

            emitter().emit("// -- Conditions");
            actor.getConditions().forEach(c -> emitter().emit("%s;", conditionPrototype(instanceName, c, actor.getConditions().indexOf(c), false)));
            emitter().emitNewLine();

            emitter().emit("// -- Transitions");
            actor.getTransitions().forEach(t -> emitter().emit("%s;", transitionPrototype(instanceName, t, actor.getTransitions().indexOf(t), false)));
            emitter().emitNewLine();

            emitter().decreaseIndentation();

        }
        // -- Private
        emitter().emit("public:");
        {
            emitter().increaseIndentation();

            // -- Constructor
            instanceConstructor(instanceName, actor);
            emitter().emitNewLine();

            emitter().emit("// -- Action Selection");
            emitter().emit("virtual raft::kstatus run();");

            emitter().decreaseIndentation();
        }


        emitter().emit("};");
    }


    default void instanceConstructor(String instanceName, ActorMachine actor) {
        emitter().emit("%s() : raft::kernel() {", instanceName);
        {
            emitter().increaseIndentation();

            // -- Ports
            for(PortDecl port : actor.getInputPorts()){
                emitter().emit("input.addPort<%s>(\"%s\");", backend().typeseval().type(types().type(port.getType())), port.getName());
            }
            for(PortDecl port : actor.getOutputPorts()){
                emitter().emit("output.addPort<%s>(\"%s\");", backend().typeseval().type(types().type(port.getType())), port.getName());
            }
            emitter().emitNewLine();


            // -- Program counter
            emitter().emit("// -- Program counter");
            emitter().emit("program_counter = %d;", backend().controllers().stateMap(actor.controller().getStateList()).get(actor.controller().getInitialState()));
            emitter().emitNewLine();

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

                                    backend().statements().copy(types().declaredType(var), backend().variables().declarationName(var), types().type(var.getValue()), backend().expressions().evaluate(var.getValue()));

                                    emitter().decreaseIndentation();
                                    emitter().emit("}");
                                } else if (var.getValue() instanceof ExprComprehension) {
                                    emitter().emit("{");
                                    emitter().increaseIndentation();

                                    backend().expressions().evaluate(var.getValue());

                                    emitter().decreaseIndentation();
                                    emitter().emit("}");
                                } else if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                                    // -- Do nothing
                                } else {
                                    emitter().emit("%s = %s;", decl, backend().expressions().evaluate(var.getValue()));
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
                        emitter().emit("this->%s = %s;", backend().variables().declarationName(par), backend().expressions().evaluate(assignment.getValue()));
                        assigned = true;
                    }
                }
                if (!assigned) {
                    throw new RuntimeException(String.format("Could not assign to %s. Candidates: {%s}.", par.getName(), String.join(", ", instance.getValueParameters().map(Parameter::getName))));
                }
            }

            emitter().decreaseIndentation();
        }

        emitter().decreaseIndentation();
        emitter().emit("}");
    }


    // ------------------------------------------------------------------------
    // -- Prototypes

    default String scopePrototype(String instanceName, Scope scope, int index, boolean withClassName) {
        // -- Actor Instance Name

        return String.format("void %sscope_%d()", withClassName ? instanceName + "::" : "", index);
    }

    default String conditionPrototype(String instanceName, Condition condition, int index, boolean withClassName) {
        // -- Actor Instance Name

        return String.format("bool %scondition_%d()", withClassName ? instanceName + "::" : "", index);
    }

    default String transitionPrototype(String instanceName, Transition transition, int index, boolean withClassName) {
        // -- Actor Instance Name

        return String.format("void %stransition_%d()", withClassName ? instanceName + "::" : "", index);
    }

    // ------------------------------------------------------------------------
    // -- Scopes

    default void scope(String instanceName, Scope scope, int index) {
        // if (scope.isPersistent()) {
        if (scope.getDeclarations().size() > 0) {
            if (index != 0) {
                emitter().emit("inline %s{", scopePrototype(instanceName, scope, index, true));
                {
                    emitter().increaseIndentation();

                    for (VarDecl var : scope.getDeclarations()) {
                        Type type = types().declaredType(var);
                        if (var.isExternal() && type instanceof CallableType) {
                            // -- Do Nothing
                        } else if (var.getValue() != null) {
                            emitter().emit("{");
                            emitter().increaseIndentation();

                            backend().statements().copy(types().declaredType(var), "this->" + backend().variables().declarationName(var), types().type(var.getValue()), backend().expressions().evaluate(var.getValue()));

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
        // }

    }

    // ------------------------------------------------------------------------
    // -- Conditions

    default void condition(String instanceName, Condition condition, int index) {
        // -- Actor Instance Name
        emitter().emit("inline %s{", conditionPrototype(instanceName, condition, index, true));
        emitter().increaseIndentation();
        {
            emitter().emit("return %s;", evaluateCondition(condition));
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();
    }

    String evaluateCondition(Condition condition);

    default String evaluateCondition(PredicateCondition condition) {
        return backend().expressions().evaluate(condition.getExpression());
    }

    default String evaluateCondition(PortCondition condition) {
        if (condition.isInputCondition()) {
            return String.format("input[\"%s\"].size() >= %d", condition.getPortName().getName(), condition.N());
        } else {
            return String.format("output[\"%s\"].space_avail() >= %d", condition.getPortName().getName(), condition.N());
        }
    }

    // ------------------------------------------------------------------------
    // -- Transitions
    default void transition(String instanceName, Transition transition, int index, ActorMachine actor) {
        Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());
        if (annotation.isPresent()) {
            String actionTag = ((ExprLiteral) annotation.get().getParameters().get(0).getExpression()).getText();
            emitter().emit("// -- Action Tag : %s", actionTag);
        }
        boolean acceleratedTransition = Annotation.hasAnnotationWithName(ACC_ANNOTATION, transition.getAnnotations());
        emitter().emit("inline %s{", transitionPrototype(instanceName, transition, index, true));
        {
            emitter().increaseIndentation();
            transition.getBody().forEach(backend().statements()::execute);
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();

    }

    // ------------------------------------------------------------------------
    // -- Callables

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
