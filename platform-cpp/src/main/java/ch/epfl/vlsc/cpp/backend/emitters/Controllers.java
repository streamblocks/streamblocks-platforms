package ch.epfl.vlsc.cpp.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.ScopeLiveness;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.ctrl.Exec;
import se.lth.cs.tycho.ir.entity.am.ctrl.Instruction;
import se.lth.cs.tycho.ir.entity.am.ctrl.InstructionKind;
import se.lth.cs.tycho.ir.entity.am.ctrl.State;
import se.lth.cs.tycho.ir.entity.am.ctrl.Test;
import se.lth.cs.tycho.ir.entity.am.ctrl.Wait;
import ch.epfl.vlsc.cpp.backend.CppBackend;

import java.util.*;
import java.util.function.Function;

@Module
public interface Controllers {
    @Binding(BindingKind.INJECTED)
    CppBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }


    default void emitController(String name, ActorMachine actorMachine) {
        List<? extends State> stateList = actorMachine.controller().getStateList();
        Map<State, Integer> stateMap = stateMap(stateList);
        Set<State> waitTargets = collectWaitTargets(stateList);

        emitter().emit("bool %s::action_selection(EStatus& status)  {", name);
        emitter().increaseIndentation();

        emitter().emit("bool progress = false;");
        emitter().emitNewLine();

        emitter().emit("// -- Status");
        for (PortDecl port : actorMachine.getInputPorts()) {
            // -- FIXME : reader id
            emitter().emit("status_%s_ = port_%1$s->count(0);", port.getName());
        }
        for (PortDecl port : actorMachine.getOutputPorts()) {
            emitter().emit("status_%s_ = port_%1$s->rooms();", port.getName());
        }

        emitter().emitNewLine();

        jumpInto(waitTargets.stream().mapToInt(stateMap::get).collect(BitSet::new, BitSet::set, BitSet::or), stateMap.get(actorMachine.controller().getInitialState()));

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
            emitInstruction(name, instruction, s, stateMap, actorMachine);
        }

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

    void emitInstruction(String name, Instruction instruction, State from, Map<State, Integer> stateNumbers, ActorMachine am);

    default void emitInstruction(String name, Test test, State from, Map<State, Integer> stateNumbers, ActorMachine am) {
        emitter().emit("if (condition_%d()) {", test.condition());
        emitter().increaseIndentation();
        emitter().emit("goto S%d;", stateNumbers.get(test.targetTrue()));
        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();
        if (am.getCondition(test.condition()) instanceof PortCondition) {
            PortCondition condition = (PortCondition) am.getCondition(test.condition());
            if (condition.isInputCondition()) {
                emitter().emit("status = EStatus::Starvation;");
            } else {
                emitter().emit("status = EStatus::Fullness;");
            }
        } else {
            emitter().emit("status = EStatus::hasSuspended;");
        }
        emitter().emit("goto S%d;", stateNumbers.get(test.targetFalse()));
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
    }

    default void emitInstruction(String name, Wait wait, State from, Map<State, Integer> stateNumbers, ActorMachine am) {
        emitter().emit("this->program_counter = %d;", stateNumbers.get(wait.target()));
           /*
        State to = wait.target();

        if (from == to) {
            emitter().emit("return EStatus::None;");
        }else{
            emitter().emit("return EStatus::hasExecuted;");
        }
         */
        emitter().emit("return progress;");
        emitter().emit("");
    }

    default void emitInstruction(String name, Exec exec, State from, Map<State, Integer> stateNumbers, ActorMachine am) {
        emitter().emit("transition_%d();", exec.transition());
        emitter().emit("progress = true;");
        emitter().emit("status = EStatus::hasExecuted;");
        emitter().emit("goto S%d;", stateNumbers.get(exec.target()));
        emitter().emit("");
    }

    default void jumpInto(BitSet waitTargets, int initialState) {
        emitter().emit("switch (this->program_counter) {");
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

