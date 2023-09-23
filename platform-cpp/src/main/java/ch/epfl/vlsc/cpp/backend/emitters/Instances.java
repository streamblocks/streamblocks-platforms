package ch.epfl.vlsc.cpp.backend.emitters;


import ch.epfl.vlsc.cpp.backend.CppBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.Parameter;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.*;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.Optional;

@Module
public interface Instances {

    String ACC_ANNOTATION = "acc";

    @Binding(BindingKind.INJECTED)
    CppBackend backend();

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
            backend().includeSystem("stdint.h");
            backend().includeUser("actor.h");
            backend().includeUser("fifo.h");
            backend().includeUser("globals.h");
            backend().includeUser("prelude.h");
            emitter().emitNewLine();

            emitter().emit("// -- TURNUS TRACE");
            emitter().emit("#ifdef TRACE_TURNUS");
            emitter().emit("#include \"turnus_tracer.h\"");
            emitter().emit("#include \"op_counter.h\"");
            backend().includeSystem("map");
            backend().includeSystem("string");
            emitter().emit("extern long long firingId;");
            emitter().emit("#endif");
            emitter().emitNewLine();

            emitter().emit("#ifdef WEIGHT_PROFILING");
            backend().includeSystem("string");
            backend().includeUser("profiling_data.h");
            backend().includeUser("cycle.h");
            emitter().emit("#endif");

        } else {
            Instance instance = backend().instancebox().get();
            String headerName = instance.getInstanceName() + ".h";

            backend().includeUser(headerName);
            backend().includeUser("natives.h");
            emitter().emitNewLine();
        }
        emitter().emitNewLine();

    }

    default void instanceClass(String instanceName, ActorMachine actor) {
        emitter().emit("// -- Instance Class");

        emitter().emit("class %s: public Actor {", instanceName);

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

            // -- Ports
            for (PortDecl port : actor.getInputPorts()) {
                if (backend().channels().isTargetConnected(backend().instancebox().get().getInstanceName(), port.getName()))
                    emitter().emit("std::uint32_t status_%s_;", port.getName());
            }
            for (PortDecl port : actor.getOutputPorts()) {
                if (backend().channels().isSourceConnected(backend().instancebox().get().getInstanceName(), port.getName()))
                    emitter().emit("std::uint32_t status_%s_;", port.getName());
            }
            emitter().emitNewLine();

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

            emitter().emit("#ifdef TRACE_TURNUS");
            emitter().emit("TurnusTracer *tracer;");
            emitter().emit("#endif");
            emitter().emitNewLine();

            emitter().emit("#ifdef WEIGHT_PROFILING");
            emitter().emit("ProfilingData *profiling_data;");
            emitter().emit("std::string last_selected_action;");
            emitter().emit("#endif");
            emitter().emitNewLine();

            emitter().decreaseIndentation();

        }
        // -- Public
        emitter().emit("public:");
        {
            emitter().increaseIndentation();

            // -- Ports
            emitter().emit("// -- Ports");
            for (PortDecl port : actor.getInputPorts()) {
                if (backend().channels().isTargetConnected(backend().instancebox().get().getInstanceName(), port.getName()))
                    emitter().emit("Fifo<%s >* port_%s;", backend().typeseval().type(types().type(port.getType())), port.getName());
            }
            for (PortDecl port : actor.getOutputPorts()) {
                if (backend().channels().isSourceConnected(backend().instancebox().get().getInstanceName(), port.getName()))
                    emitter().emit("Fifo<%s >* port_%s;", backend().typeseval().type(types().type(port.getType())), port.getName());
            }
            emitter().emitNewLine();

            // -- Constructor
            instanceConstructor(instanceName, actor);

            emitter().emit("// -- Action Selection");
            emitter().emit("bool action_selection(EStatus& status);");
            emitter().emitNewLine();

            emitter().emit("std::string name(){");
            emitter().emit("\treturn \"%s\";", instanceName);
            emitter().emit("}");
            emitter().emitNewLine();


            emitter().emit("#ifdef TRACE_TURNUS");
            emitter().emit("// -- TurnusTracer");
            emitter().emit("void set_tracer(TurnusTracer *t){");
            emitter().emit("\t tracer = t;");
            emitter().emit("}");
            emitter().emit("#endif");
            emitter().emitNewLine();

            emitter().emit("#ifdef WEIGHT_PROFILING");
            emitter().emit("void set_profiling_data(ProfilingData *p){");
            emitter().emit("\t profiling_data = p;");
            emitter().emit("}");
            emitter().emit("#endif");

            emitter().decreaseIndentation();
        }


        emitter().emit("};");
    }


    default void instanceConstructor(String instanceName, ActorMachine actor) {
        emitter().emit("%s() {", instanceName);
        {
            emitter().increaseIndentation();

            // -- Status
            emitter().emit("// -- Ports Status");
            // -- Ports
            for (PortDecl port : actor.getInputPorts()) {
                if (backend().channels().isTargetConnected(backend().instancebox().get().getInstanceName(), port.getName())) {
                    emitter().emit("status_%s_ = 0;", port.getName());
                }
            }
            for (PortDecl port : actor.getOutputPorts()) {
                if (backend().channels().isSourceConnected(backend().instancebox().get().getInstanceName(), port.getName())) {
                    emitter().emit("status_%s_ = 0;", port.getName());
                }
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
                        Type type = backend().types().type(par.getType());
                        if (type instanceof ListType) {
                            emitter().emit("this->%s = %s;", backend().variables().declarationName(par), backend().expressions().evaluateWithType(assignment.getValue(), type));
                        } else {
                            emitter().emit("this->%s = %s;", backend().variables().declarationName(par), backend().expressions().evaluate(assignment.getValue()));
                        }
                        assigned = true;
                    }
                }
                if (!assigned) {
                    throw new RuntimeException(String.format("Could not assign to %s. Candidates: {%s}.", par.getName(), String.join(", ", instance.getValueParameters().map(Parameter::getName))));
                }
            }
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
                            if (var.getValue() instanceof ExprInput) {
                                ExprInput input = (ExprInput) var.getValue();
                                if (input.getRepeat() > 1) {
                                    emitter().emit("auto *%s = port_%1$s->read_address(0, %s);",
                                            input.getPort().getName(),
                                            input.getRepeat());
                                } else {
                                    emitter().emit("auto *%s = port_%1$s->read_address(0);",
                                            input.getPort().getName());
                                }
                            }

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
        if (condition instanceof PredicateCondition) {
            PredicateCondition pred = (PredicateCondition) condition;
            if (pred.getExpression() instanceof ExprLet) {
                ExprLet _let = (ExprLet) pred.getExpression();
                for (LocalVarDecl var : _let.getVarDecls()) {
                    if (var.getValue() instanceof ExprInput) {
                        ExprInput input = (ExprInput) var.getValue();
                        if (input.getRepeat() > 1) {
                            emitter().emit("auto *%s = port_%1$s->read_address(0, %s);",
                                    input.getPort().getName(),
                                    input.getRepeat());
                        } else {
                            emitter().emit("auto *%s = port_%1$s->read_address(0);",
                                    input.getPort().getName());
                        }
                    }
                }
            }
        }

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
            if (!backend().channels().isTargetConnected(backend().instancebox().get().getInstanceName(), condition.getPortName().getName())) {
                return "false";
            }
        } else {
            if (!backend().channels().isSourceConnected(backend().instancebox().get().getInstanceName(), condition.getPortName().getName())) {
                return "true";
            }
        }
        return String.format("status_%s_ >= %d", condition.getPortName().getName(), condition.N());
    }

    // ------------------------------------------------------------------------
    // -- Transitions
    default void transition(String instanceName, Transition transition, int index, ActorMachine actor) {
        emitter().emit("#ifndef TRACE_TURNUS");
        backend().profilingbox().set(false);
        transitionContent(instanceName, transition, index, actor);
        emitter().emit("#else");
        backend().profilingbox().set(true);
        transitionContent(instanceName, transition, index, actor);
        emitter().emit("#endif");
        backend().profilingbox().set(false);
        emitter().emitNewLine();
    }


    default void transitionContent(String instanceName, Transition transition, int index, ActorMachine actor) {
        String actionTag = "";
        Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId", transition.getAnnotations());
        if (annotation.isPresent()) {
            actionTag = ((ExprLiteral) annotation.get().getParameters().get(0).getExpression()).getText();
            emitter().emit("// -- Action Tag : %s", actionTag);
        }

        emitter().emit("inline %s{", transitionPrototype(instanceName, transition, index, true));
        {
            emitter().increaseIndentation();
            // Profiling
            if (!backend().profilingbox().get()) {
                emitter().emit("#ifdef WEIGHT_PROFILING");
                emitter().emit("ticks __start = getticks();");
                emitter().emit("#endif");
                emitter().emitNewLine();
            }

            // -- Trace
            if (backend().profilingbox().get()) {
                emitter().emit("std::map<std::string, int> iPortRate;");
                for (Port port : transition.getInputRates().keySet()) {
                    if (backend().channels().isTargetConnected(backend().instancebox().get().getInstanceName(), port.getName())) {
                        emitter().emit("iPortRate.insert(std::pair<std::string,int>(\"%s\", %s));", port.getName(), transition.getInputRate(port));
                    }
                }
                emitter().emitNewLine();
                emitter().emit("std::map<std::string, int> oPortRate;");
                for (Port port : transition.getOutputRates().keySet()) {
                    if (backend().channels().isSourceConnected(backend().instancebox().get().getInstanceName(), port.getName())) {
                        emitter().emit("oPortRate.insert(std::pair<std::string,int>(\"%s\", %s));", port.getName(), transition.getOutputRate(port));
                    }
                }
                emitter().emitNewLine();
                emitter().emit("OpCounters __opCounters(\"%s\", \"%s\", firingId, iPortRate, oPortRate);", instanceName, actionTag);
            }
            for (Port port : transition.getInputRates().keySet()) {
                if (backend().channels().isTargetConnected(backend().instancebox().get().getInstanceName(), port.getName())) {
                    PortDecl pDecl = backend().ports().declaration(port);
                    if (transition.getInputRate(port) > 1) {
                        emitter().emit("auto %s = port_%s->read_address(0, %s);",
                                pDecl.getName(),
                                pDecl.getName(),
                                transition.getInputRate(port));
                    } else {
                        emitter().emit("auto %s = port_%s->read_address(0);",
                                pDecl.getName(),
                                pDecl.getName());
                    }
                } else {
                    emitter().emit("// -- FIXME");
                }

            }
            for (Port port : transition.getOutputRates().keySet()) {
                if (backend().channels().isSourceConnected(backend().instancebox().get().getInstanceName(), port.getName())) {
                    PortDecl pDecl = backend().ports().declaration(port);
                    emitter().emit("auto %s = port_%s->write_address();",
                            pDecl.getName(),
                            pDecl.getName());
                } else {
                    emitter().emit("// -- FIXME");
                }
            }


            transition.getBody().forEach(backend().statements()::execute);

            for (Port port : transition.getOutputRates().keySet()) {
                if (backend().channels().isSourceConnected(backend().instancebox().get().getInstanceName(), port.getName())) {
                    if (transition.getOutputRate(port) > 1) {
                        emitter().emit("port_%s->write_advance(%s);", port.getName(), transition.getOutputRate(port));
                    } else {
                        emitter().emit("port_%s->write_advance();", port.getName());
                    }
                    emitter().emit("status_%s_ -= %s;", port.getName(), transition.getOutputRate(port));
                }
            }

            if (backend().profilingbox().get()) {
                emitter().emitNewLine();
                emitter().emit("auto sstream = tracer->getOutputStream();");
                emitter().emit("*sstream << __opCounters.profiling().str();");
                emitter().emit("firingId++;");
            }

            if (!backend().profilingbox().get()) {
                emitter().emit("#ifdef WEIGHT_PROFILING");
                emitter().emit("ticks __end = getticks();");
                emitter().emit("ticks __elapsed = elapsed(__end, __start);");
                emitter().emit("profiling_data->addFiring(\"%s\", \"%s\", __elapsed);", instanceName, actionTag);
                emitter().emit("#endif");
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("}");
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