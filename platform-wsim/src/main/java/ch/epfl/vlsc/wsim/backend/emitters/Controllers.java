package ch.epfl.vlsc.wsim.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.ScopeLiveness;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.PredicateCondition;
import se.lth.cs.tycho.ir.entity.am.ctrl.Exec;
import se.lth.cs.tycho.ir.entity.am.ctrl.Instruction;
import se.lth.cs.tycho.ir.entity.am.ctrl.InstructionKind;
import se.lth.cs.tycho.ir.entity.am.ctrl.State;
import se.lth.cs.tycho.ir.entity.am.ctrl.Test;
import se.lth.cs.tycho.ir.entity.am.ctrl.Wait;
import ch.epfl.vlsc.wsim.backend.WSimBackend;

import java.util.*;
import java.util.function.Function;

@Module
public interface Controllers {
    @Binding(BindingKind.INJECTED)
    WSimBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }
    default String currentLvt() { return "current_ts"; }

    default void emitController(String name, ActorMachine actorMachine) {
        List<? extends State> stateList = actorMachine.controller().getStateList();
        Map<State, Integer> stateMap = stateMap(stateList);
        Set<State> waitTargets = collectWaitTargets(stateList);

        emitter().emit("void %s::scheduler(wsim::ActorScheduleQuery& query) {", name);
        emitter().increaseIndentation();

        emitter().emit("");
        emitter().emit("auto %s = ::std::max(query.getScheduleTime().value_or(0), getVirtualTime());", currentLvt());
        emitter().emitNewLine();

        jumpInto(waitTargets.stream().mapToInt(stateMap::get).collect(BitSet::new, BitSet::set, BitSet::or),
                stateMap.get(actorMachine.controller().getInitialState()));

        Function<Instruction, BitSet> initialize;
        if (backend().context().getConfiguration().get(PlatformSettings.scopeLivenessAnalysis)) {
            ScopeLiveness liveness = new ScopeLiveness(backend().scopes(), actorMachine, backend().scopeDependencies());
            initialize = liveness::init;
        } else {
            initialize = instruction -> backend().scopes().init(actorMachine, instruction);
        }

        for (State s : stateList) {
            emitter().emit("S%d:", stateMap.get(s));
            Instruction instruction = s.getInstructions().get(0);
            initialize.apply(instruction).stream().forEach(scope ->
                    emitter().emit("scope_%d();", scope)
            );
            emitInstruction(name, instruction, s, stateMap);
        }
        emitter().emit("out:");
        emitter().emit("setVirtualTime(current_ts);");
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default Map<State, Integer> stateMap(List<? extends State> stateList) {
        int i = 0;
        Map<State, Integer> result = new HashMap<>();
        for (State s : stateList) {
            result.put(s, i++);
        }
        return result;
    }

    void emitInstruction(String name, Instruction instruction, State from, Map<State, Integer> stateNumbers);

    default void emitInstruction(String name, Test test, State from, Map<State, Integer> stateNumbers) {

        emitter().emit("{");
        {
            emitter().increaseIndentation();
            emitter().emit("const auto [cond, cond_ts] = condition_%d();", test.condition());
            emitter().emit("%s = ::std::max(%1$s, cond_ts);", currentLvt());
            emitter().emit("if (cond) {", test.condition());
            emitter().increaseIndentation();
            emitter().emit("goto S%d;", stateNumbers.get(test.targetTrue()));
            emitter().decreaseIndentation();
            emitter().emit("} else {");
            emitter().increaseIndentation();
            if (test.targetFalse().getInstructions().get(0) instanceof Wait) {
                emitter().emit("query.notifyWait(%d);", test.condition());
            }
            emitter().emit("goto S%d;", stateNumbers.get(test.targetFalse()));
            emitter().decreaseIndentation();
            emitter().emit("}");
            emitter().emitNewLine();
            emitter().decreaseIndentation();
        }
        emitter().emit("}");

    }

    default void emitInstruction(String name, Wait wait, State from, Map<State, Integer> stateNumbers) {
        emitter().emit("setPC(%d);;", stateNumbers.get(wait.target()));
        emitter().emit("goto out;");
        emitter().emit("");
    }

    default void emitInstruction(String name, Exec exec, State from, Map<State, Integer> stateNumbers) {
        emitter().emit("registerTrace(%d, %s, %s);", exec.transition(), currentLvt(), backend().instance().getLatency(exec.transition()));
        emitter().emit("%s += %s;", currentLvt(), backend().instance().getLatency(exec.transition()));
        emitter().emit("query.notifyAction(%d);", exec.transition());
        emitter().emit("transition_%d(%s);", exec.transition(), currentLvt());
        emitter().emit("goto S%d;", stateNumbers.get(exec.target()));
        emitter().emit("");
    }

    default void jumpInto(BitSet waitTargets, int initialState) {
        emitter().emit("switch (getPC()) {");
        waitTargets.stream().forEach(s -> emitter().emit("case %d: goto S%1$d;", s));
        emitter().emit("default: goto S%d;", initialState);
        emitter().emit("}");
        emitter().emit("");
    }

    default Set<State> collectWaitTargets(List<? extends State> stateList) {
        Set<State> targets = new HashSet<>();
        for (State state : stateList) {
            Instruction i = state.getInstructions().get(0);
            if (i.getKind() == InstructionKind.WAIT) {
                i.forEachTarget(targets::add);
            }
        }
        return targets;
    }

}

