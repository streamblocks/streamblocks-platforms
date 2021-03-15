package ch.epfl.vlsc.hls.backend.controllers;

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
import se.lth.cs.tycho.transformation.cal2am.Priorities;
import se.lth.cs.tycho.transformation.cal2am.Schedule;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Module
public interface CalActorController {

    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

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

        emitter().decreaseIndentation();
        emitter().decreaseIndentation();

        emitter().increaseIndentation();
        emitter().increaseIndentation();
    }

    default void emitTransition(CalActor actor, Schedule schedule, String state, Action action, boolean isElse){
        if(action.getInputPatterns().isEmpty()){
            emitter().emit("%s(guard_%s(io)){", (isElse ? "} else if": "if"), action.getTag().nameWithUnderscore());
        }else{
            emitter().emit("%s(%s && guard_%s(io)){", (isElse ? "} else if": "if"), String.join(" && ", inputConditions(action)), action.getTag().nameWithUnderscore());
        }

        if (!action.getOutputExpressions().isEmpty()) {
            emitter().increaseIndentation();
            emitter().emit("if( %s ) {", String.join(" && ", outputConditions(action)));
            emitter().emit("\t%s(%s);", action.getTag().nameWithUnderscore(), actionIoArguments(action));
            emitter().emit("\t_ret.fsmState = s_%s;", schedule.targetState(Collections.singleton(state), action).iterator().next());
            emitter().emit("} else {");
            emitter().emit("\t_ret.returnCode = RETURN_WAIT;");
            emitter().emit("}");

            emitter().decreaseIndentation();
        } else {
            emitter().emit("\t%s(%s);", action.getTag().nameWithUnderscore(), actionIoArguments(action));
            emitter().emit("\t_ret.fsmState = s_%s;", schedule.targetState(Collections.singleton(state), action).iterator().next());
        }
    }

    default String stateFunctionPrototype(String instanceName, boolean withClassName, String state) {
        // -- Actor Instance Name
        String className = "class_" + instanceName;

        return String.format("StateReturn %sstate_%s(%s)", withClassName ? className + "::" : "", state, backend().instance().entityPorts(instanceName, true, true));
    }


    default void emitStateFunction(String instanceName, CalActor actor, Schedule schedule, Priorities priorities, String state) {
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
    }

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

    default List<String> inputConditions(Action action){
        List<String> conditions = new ArrayList<>();

        for(InputPattern pattern : action.getInputPatterns()){
            if(pattern.getRepeatExpr() != null){
                conditions.add(String.format("(pinAvailIn(%s, io) >= %s) && !%1$s.empty()", backend().instance().channelutils().definedInputPort(pattern.getPort()), backend().expressioneval().evaluate(pattern.getRepeatExpr())));
            }else{
                conditions.add(String.format("!%1$s.empty()", backend().instance().channelutils().definedInputPort(pattern.getPort())));
            }
        }

        return conditions;
    }

    default List<String> outputConditions(Action action){
        List<String> conditions = new ArrayList<>();

        for(OutputExpression output : action.getOutputExpressions()){
            if(output.getRepeatExpr() != null){
                conditions.add(String.format("(pinAvailOut(%s, io) >= %s) && !%1$s.full()", backend().instance().channelutils().definedInputPort(output.getPort()), backend().expressioneval().evaluate(output.getRepeatExpr())));
            }else{
                conditions.add(String.format("!%1$s.full()", backend().instance().channelutils().definedOutputPort(output.getPort())));
            }
        }

        return conditions;
    }

}
