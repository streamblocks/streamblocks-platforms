package ch.epfl.vlsc.hls.backend.controllers;

import ch.epfl.vlsc.hls.backend.ExpressionEvaluator;
import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.cal.Action;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.entity.cal.InputPattern;
import se.lth.cs.tycho.ir.entity.cal.OutputExpression;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.transformation.cal2am.Priorities;
import se.lth.cs.tycho.transformation.cal2am.Schedule;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Module
public interface CalActorController {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default ExpressionEvaluator expressioneval() {
        return backend().expressioneval();
    }

    default Emitter emitter() {
        return backend().emitter();
    }


    default void emitController(String name, CalActor actor) {
        Priorities priorities = new Priorities(actor);
        Schedule schedule = new Schedule(actor);

        List<String> ports = new ArrayList<>();
        // -- External memories
        ports.addAll(
                backend().externalMemory()
                        .getExternalMemories(actor).map(v -> backend().externalMemory().name(name, v)));

        ports.addAll(actor.getInputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));
        ports.addAll(actor.getOutputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));
        ports.add("io");

        Map<String, List<Action>> eligibleStates = schedule.getEligible();

        emitter().emit("// Here is the generated controller for the CAL Actor");
        generateControllerSimple(actor, eligibleStates, priorities, schedule);
        /*if(eligibleStates.keySet().size() > 1) {

            emitter().emit("switch(_FSM_state){");
            {
                emitter().increaseIndentation();

                for (String state : eligibleStates.keySet()) {
                    emitter().emit("case s_%s:", state);
                    emitter().emit("\t_ret = state_%s(%s);", state, String.join(", ", ports));
                    emitter().emit("break;");
                    emitter().emitNewLine();
                }
                emitter().emit("default:");
                emitter().emit("\treturn RETURN_WAIT;");

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
            emitter().emitNewLine();
        }else{
            emitter().emit("\t_ret = state_%s(%s);", schedule.getInitialState().toArray()[0], String.join(", ", ports));
        }*/
    }

    default void generateControllerSimple(CalActor actor, Map<String, List<Action>> eligibleStates,
                                          Priorities priorities, Schedule schedule) {

        emitter().emit("_ret.returnCode = RETURN_EXECUTED;");

        // 1. Collect all input patterns, output expressions and guards across all the actors into a single list
        List<String> conditionList = new ArrayList<>();
        for (Action action : actor.getActions()) {
            conditionList.addAll(inputConditions(action));
            conditionList.addAll(outputConditions(action));
            conditionList.addAll(guards(action));
        }

        // 2. Assign each of the conditions to a variable at the start of the function. This should result in all
        // these values being evaluated in parallel in the HDL actor.
        emitter().emit("// Check all the conditions once and store them in a variable");
        // Save this variable name and condition expression to a map for use later so we can find the variable again
        // from the condition
        Map<String, String> condVarMap = new HashMap<>(conditionList.size());
        int condIndex = 0;
        for (String cond : conditionList) {
            String varName = "condition" + condIndex;
            condVarMap.put(cond, varName);
            emitter().emit("bool %s = %s;", varName, cond);
            condIndex++;
        }
        emitter().emitNewLine();

        // 3. Now we implement the controller to decide which action to fire based on the value of these conditions
        // First we need to check which state we are in, and then we try to execute the actions in each of that state
        emitter().emit("// Determine which action to fire based on the conditions variables and current state ");
        if (eligibleStates.size() == 1) {
            emitActionFiringsPerState(schedule.getInitialState().toArray()[0].toString(), condVarMap, priorities,
                    schedule);
        } else {
            emitter().emit("switch(_FSM_state){");
            for (String state : eligibleStates.keySet()) {
                emitter().emit("case s_%s:", state);
                emitter().increaseIndentation();
                emitActionFiringsPerState(state, condVarMap, priorities, schedule);
                emitter().decreaseIndentation();
                emitter().emit("break;");
            }
            emitter().emit("default:");
            emitter().increaseIndentation();
            emitter().emit("_ret.returnCode = RETURN_WAIT;");
            emitter().decreaseIndentation();
            emitter().emit("}");
        }
    }

    default void emitActionFiringsPerState(String state, Map<String, String> condVarMap, Priorities priorities,
                                           Schedule schedule) {
        // 1. Get all the actions in the state and order them from highest to lowest priority.
        List<Action> actionsOnState = schedule.getEligible().get(state);
        Set<QID> selectedTags = actionsOnState.stream().map(Action::getTag).collect(Collectors.toSet());
        Set<QID> prioritizedTags = priorities.getPrioritized(Collections.singleton(state), selectedTags);
        List<Action> priority = actionsOnState.stream()
                .filter(action -> prioritizedTags.contains(action.getTag()))
                .collect(Collectors.toList());
        List<Action> actions = Stream.concat(priority.stream(), actionsOnState.stream())
                .distinct()
                .collect(Collectors.toList());

        // 2. Now check if each action can fire (and do so if possible) in nested if statement based on above priority.
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);

            // 2.1 Gather all condition expressions for this specific action.
            List<String> conditionExpressions = Stream.concat(Stream.concat(inputConditions(action).stream(),
                            outputConditions(action).stream()), guards(action).stream())
                    .collect(Collectors.toList());

            // 2.2 Combine all these condition expression logically anded together into a string.
            String conditionString = conditionExpressions.stream().map(condVarMap::get).collect(Collectors.joining(" " +
                    "&& "));

            // 2.3. Check all these conditions in a single if statement
            if (i == 0) {
                emitter().emit("if (%s) {", conditionString);
            } else {
                emitter().emit("else if (%s) {", conditionString);
            }
            emitter().increaseIndentation();
            // 2.4. If all the conditions evaluate to true, execute the action
            emitter().emit("%s(%s);", action.getTag().nameWithUnderscore(), actionIoArguments(action));
            emitter().emit("_ret.fsmState = s_%s;",
                    schedule.targetState(Collections.singleton(state), action).iterator().next());
            emitter().decreaseIndentation();
            emitter().emit("}");
        }
        // 2.5 If no conditions evaluate to true
        emitter().emit("else {");
        emitter().increaseIndentation();
        emitter().emit("_ret.returnCode = RETURN_WAIT;");
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    /*default void emitTransition(CalActor actor, Schedule schedule, String state, Action action, boolean isElse){
        if(action.getInputPatterns().isEmpty()){
            emitter().emit("%s(guard_%s(io)){", (isElse ? "} else if": "if"), action.getTag().nameWithUnderscore());
        }else{
            emitter().emit("%s(%s && guard_%s(io)){", (isElse ? "} else if": "if"), String.join(" && ",
            inputConditions(action)), action.getTag().nameWithUnderscore());
        }

        if (!action.getOutputExpressions().isEmpty()) {
            emitter().increaseIndentation();
            emitter().emit("if( %s ) {", String.join(" && ", outputConditions(action)));
            emitter().emit("\t%s(%s);", action.getTag().nameWithUnderscore(), actionIoArguments(action));
            emitter().emit("\t_ret.fsmState = s_%s;", schedule.targetState(Collections.singleton(state), action)
            .iterator().next());
            emitter().emit("} else {");
            emitter().emit("\t_ret.returnCode = RETURN_WAIT;");
            emitter().emit("}");

            emitter().decreaseIndentation();
        } else {
            emitter().emit("\t%s(%s);", action.getTag().nameWithUnderscore(), actionIoArguments(action));
            emitter().emit("\t_ret.fsmState = s_%s;", schedule.targetState(Collections.singleton(state), action)
            .iterator().next());
        }
    }*/

    default String stateFunctionPrototype(String instanceName, boolean withClassName, String state) {
        // -- Actor Instance Name
        //String className = "class_" + instanceName;

        //return String.format("StateReturn %sstate_%s(%s)", withClassName ? className + "::" : "", state, backend()
        // .instance().entityPorts(instanceName, true, true));
        return "";
    }


    /*default void emitStateFunction(String instanceName, CalActor actor, Schedule schedule, Priorities priorities,
    String state) {
        emitter().emit("%s{", stateFunctionPrototype(instanceName, true, state));
        emitter().emit("#pragma HLS INLINE off");
        emitter().emit("#pragma HLS INTERFACE ap_hs port=io");
        emitter().increaseIndentation();

        emitter().emit("StateReturn _ret;");
        emitter().emit("_ret.fsmState = s_%s;", state);
        emitter().emit("_ret.returnCode = RETURN_EXECUTED;");
        emitter().emitNewLine();

        List<Action> actionsOnState = schedule.getEligible().get(state);
        Set<QID> selectedTags = actionsOnState.stream().map(Action::getTag).collect(Collectors.toSet());
        Set<QID> prioritizedTags = priorities.getPrioritized(Collections.singleton(state), selectedTags);

        List<Action> priority = actionsOnState.stream()
                .filter(action -> prioritizedTags.contains(action.getTag()))
                .collect(Collectors.toList());

        List<Action> actions = Stream.concat(priority.stream(), actionsOnState.stream())
                .distinct()
                .collect(Collectors.toList());

        Iterator<Action> iter = actions.iterator();

        Action action = iter.next();

        emitTransition(actor, schedule, state, action, false);

        if (iter.hasNext()) {
            while (iter.hasNext()) {
                action = iter.next();
                emitTransition(actor, schedule, state, action, true);
            }
        }

        emitter().emit("} else {");
        emitter().emit("\t_ret.returnCode = RETURN_WAIT;");
        emitter().emit("}");
        emitter().emitNewLine();

        emitter().emit("return _ret;");


        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();
    }*/

    default String actionIoArguments(Action action) {

        List<String> ports = new ArrayList<>();
        for (InputPattern input : action.getInputPatterns()) {
            ports.add(input.getPort().getName());
        }

        for (OutputExpression output : action.getOutputExpressions()) {
            ports.add(output.getPort().getName());
        }

        return String.join(", ", ports);
    }

    default List<String> inputConditions(Action action) {
        List<String> conditions = new ArrayList<>();

        for (InputPattern pattern : action.getInputPatterns()) {
            if (pattern.getRepeatExpr() != null) {
                conditions.add(String.format("(pinAvailIn(%s, io) >= %s) && !%1$s.empty()",
                        backend().instance().channelutils().definedInputPort(pattern.getPort()),
                        backend().expressioneval().evaluate(pattern.getRepeatExpr())));
            } else {
                conditions.add(String.format("!%1$s.empty()",
                        backend().instance().channelutils().definedInputPort(pattern.getPort())));
            }
        }

        return conditions;
    }

    default List<String> outputConditions(Action action) {
        List<String> conditions = new ArrayList<>();

        for (OutputExpression output : action.getOutputExpressions()) {
            if (output.getRepeatExpr() != null) {
                conditions.add(String.format("(pinAvailOut(%s, io) >= %s) && !%1$s.full()",
                        backend().instance().channelutils().definedInputPort(output.getPort()),
                        backend().expressioneval().evaluate(output.getRepeatExpr())));
            } else {
                conditions.add(String.format("!%1$s.full()",
                        backend().instance().channelutils().definedOutputPort(output.getPort())));
            }
        }

        return conditions;
    }

    default List<String> guards(Action action) {
        List<String> guards = new ArrayList<>();
        for (Expression guard : action.getGuards()) {
            guards.add(expressioneval().evaluate(guard));
        }

        return guards;
    }

}
