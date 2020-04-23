package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.platformutils.utils.MathUtils;
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
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Module
public interface Instances {
    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default GlobalNames globalnames() {
        return backend().globalnames();
    }

    default Types types() {
        return backend().types();
    }

    default TypesEvaluator typesEval() {
        return backend().typesEval();
    }

    default ExpressionEvaluator expressionEval() {
        return backend().expressionEval();
    }

    default Boolean enableTraces() {
        return backend().context().getConfiguration().get(PlatformSettings.enableTraces);
    }

    default String instanceQidName() {
        Instance instance = backend().instancebox().get();
        return backend().instaceQID(instance.getInstanceName(), "_");
    }

    @Binding(BindingKind.LAZY)
    default Map<Transition, Boolean> transitionAlignable() {
        return new HashMap<>();
    }

    @Binding(BindingKind.LAZY)
    default Map<Transition, Boolean> transitionAlwaysAligned() {
        return new HashMap<>();
    }

    @Binding(BindingKind.LAZY)
    default Map<Port, Map<String, Integer>> portTransitionRate() {
        return new HashMap<>();
    }

    @Binding(BindingKind.LAZY)
    default Map<Port, Boolean> portAlwaysAligned() {
        return new HashMap<>();
    }

    default void generateInstance(Instance instance) {
        // -- Add instance to box
        backend().instancebox().set(instance);

        // -- Get Entity
        GlobalEntityDecl entityDecl = globalnames().entityDecl(instance.getEntityName(), true);
        Entity entity = entityDecl.getEntity();

        // -- Add entity to box
        backend().entitybox().set(entity);

        // -- Path
        Path instanceTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve(backend().instaceQID(instance.getInstanceName(), "_") + ".c");
        emitter().open(instanceTarget);

        emitter().emit("// -- Source file is \"%s\"", "");

        // -- Includes
        defineIncludes();

        // -- Extern instance
        externInstance();

        // -- Input(s)
        inputs();

        // -- Output(s)
        outputs();

        // -- Instance parameters
        parameters();

        // -- State variables
        stateVariables();

        // -- AM State
        amState();

        // -- Token functions
        tokenFunctions();

        // -- Actor Function/Procedures
        callablesInActor();

        // -- Scopes
        initScopes();

        // -- Conditions
        conditions();

        // -- Transitions
        transitions();

        // -- Initialize
        initialize();

        // -- Scheduler (controller)
        scheduler();

        // -- EOF
        emitter().close();

        // -- Clear boxes
        backend().instancebox().clear();
        backend().entitybox().clear();
    }

    default void defineIncludes() {
        emitter().emit("#include <stdio.h>");
        emitter().emit("#include <stdlib.h>");
        emitter().emit("#include \"orcc_config.h\"");
        emitter().emitNewLine();

        emitter().emit("#include \"types.h\"");
        emitter().emit("#include \"fifo.h\"");
        emitter().emit("#include \"util.h\"");
        emitter().emit("#include \"scheduler.h\"");
        emitter().emit("#include \"dataflow.h\"");
        emitter().emit("#include \"cycle.h\"");
        emitter().emit("#include \"globals.h\"");
        emitter().emitNewLine();
    }

    default void externInstance() {
        emitter().emitClikeBlockComment("Instance");
        emitter().emit("extern actor_t %s;", instanceQidName());
        emitter().emitNewLine();
    }

    default void inputs() {
        emitter().emitClikeBlockComment("Input FIFOs");
        Entity entity = backend().entitybox().get();

        for (PortDecl port : entity.getInputPorts()) {
            emitter().emit("extern fifo_%s_t *%s_%s;", typesEval().type(types().type(port.getType())), instanceQidName(), port.getName());
        }
        emitter().emitNewLine();

        emitter().emitClikeBlockComment("Input Fifo control variables");
        for (PortDecl port : entity.getInputPorts()) {
            Connection.End target = new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName());
            emitter().emit("static unsigned int index_%s;", port.getName());
            emitter().emit("static unsigned int numTokens_%s;", port.getName());
            emitter().emit("#define SIZE_%s %d", port.getName(), backend().channelUtils().connectionBufferSize(backend().channelUtils().sourceEndConnection(target)));
            emitter().emit("#define tokens_%s %s_%1$s->contents", port.getName(), instanceQidName());
            emitter().emitNewLine();

            emitter().emit("extern connection_t connection_%s_%s;", instanceQidName(), port.getName());
            emitter().emit("#define rate_%s connection_%s_%1$s.rate", port.getName(), instanceQidName());
            emitter().emitNewLine();
        }

        if (enableTraces()) {
            emitter().emitClikeBlockComment("Trace files declaration (in)");
            for (PortDecl port : entity.getInputPorts()) {
                emitter().emit("FILE *file_%s;", port.getName());
            }
            emitter().emitNewLine();
        }

        emitter().emitClikeBlockComment("Predecessors");
        List<String> predecessors = new ArrayList<>();
        for (PortDecl port : entity.getInputPorts()) {
            Connection.End target = new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName());
            Instance predecessor = backend().channelUtils().sourceEndInstance(target);
            String qidInstanceName = backend().instaceQID(predecessor.getInstanceName(), "_");
            if (!predecessors.contains(qidInstanceName))
                predecessors.add(qidInstanceName);
        }

        predecessors.forEach(p -> emitter().emit("extern actor_t %s;", p));
        emitter().emitNewLine();
    }

    default void outputs() {
        emitter().emitClikeBlockComment("Output FIFOs");
        Entity entity = backend().entitybox().get();

        for (PortDecl port : entity.getOutputPorts()) {
            emitter().emit("extern fifo_%s_t *%s_%s;", typesEval().type(types().type(port.getType())), instanceQidName(), port.getName());
        }
        emitter().emitNewLine();

        emitter().emitClikeBlockComment("Output Fifo control variables");
        for (PortDecl port : entity.getOutputPorts()) {
            Connection.End source = new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName());
            emitter().emit("static unsigned int index_%s;", port.getName());
            emitter().emit("#define NUM_READERS_%s %d", port.getName(), backend().channelUtils().targetEndConnections(source).size());
            emitter().emit("#define SIZE_%s %d", port.getName(), backend().channelUtils().connectionBufferSize(backend().channelUtils().targetEndConnections(source).get(0)));
            emitter().emit("#define tokens_%s %s_%1$s->contents", port.getName(), instanceQidName());
            emitter().emitNewLine();
        }

        if (enableTraces()) {
            emitter().emitClikeBlockComment("Trace files declaration (out)");
            for (PortDecl port : entity.getOutputPorts()) {
                emitter().emit("FILE *file_%s;", port.getName());
            }
            emitter().emitNewLine();
        }

        emitter().emitClikeBlockComment("Successors");
        List<String> successors = new ArrayList<>();
        for (PortDecl port : entity.getOutputPorts()) {
            Connection.End target = new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName());
            List<Instance> instances = backend().channelUtils().targetEndInstance(target);
            for (Instance successor : instances) {
                String qidInstanceName = backend().instaceQID(successor.getInstanceName(), "_");
                if (!successors.contains(qidInstanceName))
                    successors.add(qidInstanceName);
            }
        }
        successors.forEach(s -> emitter().emit("extern actor_t %s;", s));
        if (!successors.isEmpty()) {
            emitter().emitNewLine();
        }
    }

    default void parameters() {
        Instance instance = backend().instancebox().get();
        Entity entity = backend().entitybox().get();

        if (!instance.getValueParameters().isEmpty() || !entity.getValueParameters().isEmpty()) {
            emitter().emitClikeBlockComment("Parameter values of the instance");

            for (ParameterVarDecl par : entity.getValueParameters()) {
                boolean assigned = false;
                for (Parameter<Expression, ?> assignment : instance.getValueParameters()) {
                    if (par.getName().equals(assignment.getName())) {
                        // -- TODO
                        //emitter().emit("%s = %s;", backend().variables().declarationName(par), expressioneval().evaluate(assignment.getValue()));
                        assigned = true;
                    }
                }
                if (!assigned) {
                    throw new RuntimeException(String.format("Could not assign to %s. Candidates: {%s}.", par.getName(), String.join(", ", instance.getValueParameters().map(Parameter::getName))));
                }
            }

        }
    }

    default void stateVariables() {
        emitter().emitClikeBlockComment("State variables of the actor");
        Entity entity = backend().entitybox().get();

        assert !(entity instanceof ActorMachine);
        ActorMachine am = (ActorMachine) entity;

        for (Scope scope : am.getScopes()) {
            if (scope.isPersistent()) {
                if (scope.getDeclarations().size() > 0)
                    emitter().emit("// -- Scope %d", am.getScopes().indexOf(scope));
                for (VarDecl var : scope.getDeclarations()) {
                    if (var.getValue() == null) {
                        String decl = backend().declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                        emitter().emit("static %s;", decl);
                    } else {
                        if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                            // -- Do nothing
                        } else if (var.getValue() instanceof ExprInput) {
                            String decl = backend().declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                            emitter().emit("static %s;", decl);
                        } else if (var.getValue() instanceof ExprLiteral) {
                            if (var.isConstant()) {
                                emitter().emit("#define %s %s", backend().variables().declarationName(var), backend().expressionEval().evaluate(var.getValue()));
                            } else {
                                String decl = backend().declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                                emitter().emit("static %s%s = %s;", var.isConstant() ? "const " : "", decl, backend().expressionEval().evaluate(var.getValue()));
                            }
                        } else {
                            String decl = backend().declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                            emitter().emit("static %s;", decl);
                            //emitter().emit("static %s%s = %s;", var.isConstant() ? "const " : "", decl, backend().expressionEval().evaluate(var.getValue()));
                        }
                    }
                }
            } else {
                if (scope.getDeclarations().size() > 0)
                    emitter().emit("// -- Scope %d", am.getScopes().indexOf(scope));
                for (VarDecl var : scope.getDeclarations()) {
                    if (var.getValue() instanceof ExprLambda || var.getValue() instanceof ExprProc) {
                        // -- Do nothing
                    } else {
                        String decl = backend().declarations().declaration(types().declaredType(var), backend().variables().declarationName(var));
                        emitter().emit("static %s;", decl);
                    }
                }
            }
        }
        emitter().emitNewLine();
    }

    default void amState() {
        emitter().emitClikeBlockComment("Initial FSM state of the actor");
        Entity entity = backend().entitybox().get();

        assert !(entity instanceof ActorMachine);
        ActorMachine am = (ActorMachine) entity;

        int nbrStates = am.controller().getStateList().size();

        emitter().emit("enum states {");
        {
            emitter().increaseIndentation();

            for (int i = 0; i < nbrStates; i++) {
                if (i == (nbrStates - 1))
                    emitter().emit("my_state_S%d", i);
                else
                    emitter().emit("my_state_S%d,", i);
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();

        emitter().emit("static char *stateNames[] = {");
        {
            emitter().increaseIndentation();

            for (int i = 0; i < nbrStates; i++) {
                if (i == (nbrStates - 1))
                    emitter().emit("\"S%d\"", i);
                else
                    emitter().emit("\"S%d,\"", i);
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("};");
        emitter().emitNewLine();

        emitter().emit("static enum states _FSM_state;");
        emitter().emitNewLine();
    }

    default void tokenFunctions() {
        emitter().emitClikeBlockComment("Token functions");

        Network network = backend().task().getNetwork();
        Map<Connection, Integer> connectionReaderId = new HashMap<>();
        for (Connection connection : network.getConnections()) {
            Connection.End source = connection.getSource();
            int counter = 0;
            for (Connection r : connectionReaderId.keySet()) {
                Connection.End compare = r.getSource();
                if (source.getInstance().equals(compare.getInstance()) && source.getPort().equals(compare.getPort())) {
                    counter++;
                }
            }
            connectionReaderId.put(connection, counter);
        }

        Entity entity = backend().entitybox().get();
        String qidInstanceName = instanceQidName();
        for (PortDecl port : entity.getInputPorts()) {
            Connection.End target = new Connection.End(Optional.of(backend().instancebox().get().getInstanceName()), port.getName());

            int readerId = connectionReaderId.get(backend().channelUtils().sourceEndConnection(target));

            // int readerId =  backend().main().connectionId().get(backend().channelUtils().sourceEndConnection(target).getSource());

            emitter().emit("static void read_%s(){", port.getName());
            {
                emitter().increaseIndentation();

                emitter().emit("index_%s = %s_%1$s->read_inds[%d];", port.getName(), qidInstanceName, readerId);
                emitter().emit("numTokens_%s = index_%1$s + fifo_%s_get_num_tokens(%s_%1$s, %d);", port.getName(), typesEval().type(backend().channelUtils().targetEndType(target)), qidInstanceName, readerId);

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();

            emitter().emit("static void read_end_%s(){", port.getName());

            emitter().emit("\t%s_%s->read_inds[%d] = index_%2$s;", qidInstanceName, port.getName(), readerId);

            emitter().emit("}");
            emitter().emitNewLine();
        }

        for (PortDecl port : entity.getOutputPorts()) {
            emitter().emit("static void write_%s(){", port.getName());

            emitter().emit("\tindex_%s = %s_%1$s->write_ind;", port.getName(), qidInstanceName);

            emitter().emit("}");

            emitter().emitNewLine();

            emitter().emit("static void write_end_%s(){", port.getName());

            emitter().emit("\t%s_%s->write_ind = index_%2$s;", qidInstanceName, port.getName());

            emitter().emit("}");

            emitter().emitNewLine();
        }
    }

    default void callablesInActor() {
        emitter().emitClikeBlockComment("Functions / Procedures");
        Entity entity = backend().entitybox().get();

        assert !(entity instanceof ActorMachine);
        ActorMachine am = (ActorMachine) entity;
        for (Scope scope : am.getScopes()) {
            if (scope.isPersistent()) {
                for (VarDecl decl : scope.getDeclarations()) {
                    if (decl.getValue() != null) {
                        Expression expr = decl.getValue();
                        if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                            emitter().emit("static %s;", backend().callablesInActors().callablePrototypes(expr));
                            emitter().emitNewLine();
                        }
                    }
                }
            }
        }

        for (Scope scope : am.getScopes()) {
            if (scope.isPersistent()) {
                for (VarDecl decl : scope.getDeclarations()) {
                    if (decl.getValue() != null) {
                        Expression expr = decl.getValue();
                        if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                            backend().callablesInActors().callableDefinition(expr);
                            emitter().emitNewLine();
                        }
                    }
                }
            }
        }
    }

    /**
     * Initialize Scopes
     */
    default void initScopes() {
        emitter().emitClikeBlockComment("Initialize Scopes");
        Entity entity = backend().entitybox().get();

        assert !(entity instanceof ActorMachine);
        ActorMachine am = (ActorMachine) entity;

        for (Scope scope : am.getScopes()) {
            if (scope.getDeclarations().size() > 0 || scope.isPersistent()) {
                if (am.getScopes().indexOf(scope) != 0) {
                    String scopeName = instanceQidName() + "_init_scope_" + am.getScopes().indexOf(scope);
                    emitter().emit("static void %s(){", scopeName);
                    emitter().increaseIndentation();

                    for (VarDecl var : scope.getDeclarations()) {
                        Type type = types().declaredType(var);
                        if (var.isExternal() && type instanceof CallableType) {
                            String wrapperName = backend().callables().externalWrapperFunctionName(var);
                            String variableName = backend().variables().declarationName(var);
                            String t = backend().callables().mangle(type).encode();
                            emitter().emit("%s = (%s) { *%s, NULL };", variableName, t, wrapperName);
                        } else if (var.getValue() != null) {
                            emitter().emit("{");
                            emitter().increaseIndentation();
                            if (var.getValue() instanceof ExprInput) {
                                expressionEval().evaluateWithLvalue(backend().variables().declarationName(var), (ExprInput) var.getValue());
                            } else {
                                backend().statements().copy(types().declaredType(var), backend().variables().declarationName(var), types().type(var.getValue()), expressionEval().evaluate(var.getValue()));
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

    /**
     * Conditions
     */
    default void conditions() {
        emitter().emitClikeBlockComment("Conditions");
        Entity entity = backend().entitybox().get();

        assert !(entity instanceof ActorMachine);
        ActorMachine am = (ActorMachine) entity;

        for (Condition condition : am.getConditions()) {
            String conditionName = instanceQidName() + "_condition_" + am.getConditions().indexOf(condition);
            emitter().emit("static i32 %s() {", conditionName);
            emitter().increaseIndentation();
            evaluateCondition(condition);

            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emitNewLine();
        }
    }

    void evaluateCondition(Condition condition);

    default void evaluateCondition(PredicateCondition condition) {
        backend().memoryStack().enterScope();
        emitter().emit("i32 cond = %s;", expressionEval().evaluate(condition.getExpression()));
        backend().memoryStack().exitScope();
        emitter().emit("return cond;");
    }

    default void evaluateCondition(PortCondition condition) {
        if (condition.isInputCondition()) {
            emitter().emit("i32 cond = %s;", String.format("numTokens_%s - index_%1$s >= %d", condition.getPortName().getName(), condition.N()));
            emitter().emit("return cond;");
        } else {
            Instance instance = backend().instancebox().get();
            Connection.End source = new Connection.End(Optional.of(instance.getInstanceName()), condition.getPortName().getName());
            int readers = 0;
            for (Connection.End end : backend().main().connectionEndNbrReaders().keySet()) {
                if (source.equals(end)) {
                    readers = backend().main().connectionEndNbrReaders().get(end);
                }
            }
            List<String> nbrReaderConditions = new ArrayList<>();
            for (int i = 0; i < readers; i++) {
                String outCondition = String.format("%d > SIZE_%s - index_%2$s + %s_%2$s->read_inds[%d]", condition.N(), condition.getPortName().getName(), instanceQidName(), i);
                emitter().emit("if(%s){", outCondition);
                emitter().emit("\treturn false;");
                emitter().emit("}");
                nbrReaderConditions.add(outCondition);
            }

            emitter().emit("return true;");

        }
    }


    default void portAlignability(Port port, int index, int rate) {
        boolean isAlignable = rate >= 2;
        if (portTransitionRate().containsKey(port)) {
            if (isAlignable) {
                portTransitionRate().get(port).put("transition_" + index + "_ALIGNABLE", rate);
            } else {
                portTransitionRate().get(port).put("transition_" + index + "_NON_ALIGNABLE", rate);

            }
        } else {
            Map<String, Integer> m = new HashMap<>();
            if (isAlignable) {
                m.put("transition_" + index + "_ALIGNABLE", rate);
            } else {
                m.put("transition_" + index + "_NON_ALIGNABLE", rate);
            }
            portTransitionRate().put(port, m);
        }
    }

    default void setPortAlwaysAligned(Port port, int rate) {
        boolean isAlwaysAligned = rate >= 2 && MathUtils.isPowerOfTwo(rate);
        if (isAlwaysAligned) {
            isAlwaysAligned = isAlwaysAligned
                    && portTransitionRate().get(port).keySet().stream().filter(n -> n.endsWith("_ALIGNABLE")).count() == 1
                    && portTransitionRate().get(port).keySet().stream().filter(n -> n.endsWith("_NOT_ALIGNABLE")).count() == 0;

            if (portTransitionRate().get(port).keySet().stream().filter(n -> n.endsWith("_ALIGNABLE")).count() > 1) {
                for (String id : (portTransitionRate().get(port).keySet().stream().filter(n -> n.endsWith("_ALIGNABLE")).collect(Collectors.toList()))) {
                    isAlwaysAligned = isAlwaysAligned && (portTransitionRate().get(port).get(id) == rate);
                }
            }
        }
        portAlwaysAligned().put(port, isAlwaysAligned);
    }

    /**
     * Transitions
     */
    default void transitions() {
        emitter().emitClikeBlockComment("Transitions");
        Entity entity = backend().entitybox().get();

        assert !(entity instanceof ActorMachine);
        ActorMachine am = (ActorMachine) entity;


        // -- Aligned / Always aligned
        portTransitionRate().clear();
        for (Transition transition : am.getTransitions()) {
            boolean isVectorizable = false;

            for (Port port : transition.getInputRates().keySet()) {
                isVectorizable = transition.getInputRates().get(port) >= 2 || isVectorizable;

                portAlignability(port, am.getTransitions().indexOf(transition), transition.getInputRate(port));
            }


            for (Port port : transition.getOutputRates().keySet()) {
                isVectorizable = transition.getOutputRates().get(port) >= 2 || isVectorizable;

                portAlignability(port, am.getTransitions().indexOf(transition), transition.getOutputRate(port));
            }
            transitionAlignable().put(transition, isVectorizable);
        }


        for (Transition transition : am.getTransitions()) {
            boolean isAlwaysAligned = !transition.getInputRates().isEmpty() || !transition.getOutputRates().isEmpty();

            for (Port port : transition.getInputRates().keySet()) {
                setPortAlwaysAligned(port, transition.getInputRates().get(port));
                isAlwaysAligned = isAlwaysAligned && portAlwaysAligned().get(port);
            }

            for (Port port : transition.getOutputRates().keySet()) {
                setPortAlwaysAligned(port, transition.getOutputRates().get(port));
                isAlwaysAligned = isAlwaysAligned && portAlwaysAligned().get(port);
            }
            transitionAlwaysAligned().put(transition, isAlwaysAligned);
        }


        for (Transition transition : am.getTransitions()) {
            // -- Aligned / Always aligned
            if (transitionAlwaysAligned().get(transition) && false) {
                transition(am, transition, true);
            } else {
                transition(am, transition, false);
                emitter().emitNewLine();
                if (transitionAlignable().get(transition)) {
                    transition(am, transition, true);
                }
            }
        }
    }

    default void transition(ActorMachine am, Transition transition, boolean isAligned) {
        backend().alignedBox().set(isAligned);
        if (isAligned) {
            emitter().emit("static void %s_transition_%d_aligned(){", instanceQidName(), am.getTransitions().indexOf(transition));
        } else {
            emitter().emit("static void %s_transition_%d(){", instanceQidName(), am.getTransitions().indexOf(transition));
        }
        {
            emitter().increaseIndentation();

            // -- Traces IN
            if (enableTraces() && !transition.getInputRates().isEmpty()) {

                emitter().emit("{");
                {
                    emitter().increaseIndentation();

                    for (Port port : transition.getInputRates().keySet()) {
                        String index = backend().variables().generateTemp();
                        emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, transition.getInputRates().get(port));
                        emitter().emit("\tfprintf(file_%1$s, \"%%%3$s\\n\" ,tokens_%1$s[(index_%1$s + (%2$s)) %% SIZE_%1$s]);", port.getName(), index, typesEval().printFormat(backend().channelUtils().inputPortType(port)));
                        emitter().emit("}");
                    }

                    emitter().decreaseIndentation();
                    emitter().emit("}");
                }
            }

            backend().memoryStack().enterScope();
            transition.getBody().forEach(backend().statements()::execute);
            backend().memoryStack().exitScope();


            // -- Traces OUT
            if (enableTraces() && !transition.getOutputRates().isEmpty()) {

                emitter().emit("{");
                {
                    emitter().increaseIndentation();

                    for (Port port : transition.getOutputRates().keySet()) {
                        String index = backend().variables().generateTemp();
                        emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, transition.getOutputRates().get(port));
                        emitter().emit("\tfprintf(file_%1$s, \"%%%3$s\\n\" ,tokens_%1$s[(index_%1$s + (%2$s)) %% SIZE_%1$s]);", port.getName(), index, typesEval().printFormat(backend().channelUtils().outputPortType(port)));
                        emitter().emit("}");
                    }

                    emitter().decreaseIndentation();
                    emitter().emit("}");
                }
            }

            // -- I/O Update
            for (Port port : transition.getInputRates().keySet()) {
                emitter().emit("index_%s += %d;", port.getName(), transition.getInputRates().get(port));
                if (transition.getInputRates().get(port) > 2) {
                    emitter().emit("read_end_%s();", port.getName());
                }
            }
            for (Port port : transition.getOutputRates().keySet()) {
                emitter().emit("index_%s += %d;", port.getName(), transition.getOutputRates().get(port));
                if (transition.getOutputRates().get(port) > 2) {
                    emitter().emit("write_end_%s();", port.getName());
                }
            }
            for (Port port : transition.getInputRates().keySet()) {
                emitter().emit("rate_%s += %d;", port.getName(), transition.getInputRates().get(port));
            }

            emitter().decreaseIndentation();
        }

        emitter().emit("}");
        emitter().emitNewLine();
        backend().alignedBox().clear();
    }


    /**
     * Initialize
     */
    default void initialize() {
        emitter().emitClikeBlockComment("Initializes");
        Entity entity = backend().entitybox().get();

        emitter().emit("void %s_initialize(schedinfo_t *si){", instanceQidName());
        {
            emitter().increaseIndentation();

            for (PortDecl port : entity.getOutputPorts()) {
                emitter().emit("write_%s();", port.getName());
            }
            emitter().emitNewLine();

            // -- Init Scopes
            assert !(entity instanceof ActorMachine);
            ActorMachine am = (ActorMachine) entity;
            for (Scope scope : am.getScopes()) {
                if (scope.isPersistent())
                    if (scope.getDeclarations().size() > 0) {
                        emitter().emit("// -- Scope %d", am.getScopes().indexOf(scope));
                        emitter().emit("{");
                        emitter().increaseIndentation();
                        for (VarDecl decl : scope.getDeclarations()) {
                            if (decl.getValue() != null) {
                                if (!(decl.getValue() instanceof ExprLiteral) && !(decl.getValue() instanceof ExprLambda) && !(decl.getValue() instanceof ExprProc) && !(decl.getValue() instanceof ExprInput)) {
                                    emitter().emit("{");
                                    emitter().increaseIndentation();
                                    backend().statements().copy(types().declaredType(decl), backend().variables().declarationName(decl), types().type(decl.getValue()), backend().expressionEval().evaluate(decl.getValue()));
                                    emitter().decreaseIndentation();
                                    emitter().emit("}");
                                }
                            }
                        }
                        emitter().decreaseIndentation();
                        emitter().emit("}");
                    }
            }


            emitter().emit("/* Set initial state to current FSM state */");
            emitter().emit("_FSM_state = my_state_S0;");
            emitter().emitNewLine();

            for (PortDecl port : entity.getOutputPorts()) {
                emitter().emit("write_end_%s();", port.getName());
            }

            emitter().emit("return;");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emitNewLine();
    }

    default void scheduler() {
        Entity entity = backend().entitybox().get();

        assert !(entity instanceof ActorMachine);
        ActorMachine am = (ActorMachine) entity;
        backend().controller().emitController(instanceQidName(), am);
    }

}
