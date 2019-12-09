package ch.epfl.vlsc.hls.backend.controllers;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.ScopeLiveness;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.OnOffSetting;

import java.util.*;
import java.util.function.Function;

@Module
public interface QuickJumpController {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }


    default Map<State, Integer> stateMap(List<? extends State> stateList) {
        int i = 0;
        Map<State, Integer> result = new HashMap<>();
        for (State s : stateList) {
            result.put(s, i++);
        }
        return result;
    }


    default void emitController(String name, ActorMachine actorMachine) {
        List<? extends State> stateList = actorMachine.controller().getStateList();
        Map<State, Integer> stateMap = stateMap(stateList);
        Set<State> waitTargets = collectWaitTargets(stateList);

        // -- Controller
        jumpInto(waitTargets.stream().mapToInt(stateMap::get).collect(BitSet::new, BitSet::set, BitSet::or));

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
                    emitter().emit("scope_%d(%s);", scope, backend().instance().scopeArguments(actorMachine.getScopes().get(scope)))
            );
            emitInstruction(actorMachine, name, instruction, stateMap);
        }

        //emitter().emit("out:");
        //emitter().emit("return this->__ret;");
    }


    void emitInstruction(ActorMachine am, String name, Instruction instruction, Map<State, Integer> stateNumbers);

    default void emitInstruction(ActorMachine am, String name, Test test, Map<State, Integer> stateNumbers) {
        String io = "";
        String waitKind = "";
        if(am.getCondition(test.condition()) instanceof PortCondition){
            PortCondition condition = (PortCondition) am.getCondition(test.condition());
            if(condition.isInputCondition()){
                waitKind = String.format("this->__ret = %s;", "RETURN_WAIT_INPUT");
            }else{
                waitKind = String.format("this->__ret = %s;", "RETURN_WAIT_OUTPUT");
            }
            String portName = ((PortCondition) am.getCondition(test.condition())).getPortName().getName();
            io = portName + ", io";
        }else{
            waitKind = String.format("this->__ret = %s;", "RETURN_WAIT_PREDICATE");
        }
        emitter().emit("if (condition_%d(%s)) {", test.condition(), io);
        emitter().increaseIndentation();
        emitter().emit("goto S%d;", stateNumbers.get(test.targetTrue()));
        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();
        if(!waitKind.isEmpty()){
            emitter().emit("%s", waitKind);
        }
        emitter().emit("goto S%d;", stateNumbers.get(test.targetFalse()));
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
    }

    default void emitInstruction(ActorMachine am, String name, Wait wait, Map<State, Integer> stateNumbers) {
        emitter().emit("this->program_counter = %d;", stateNumbers.get(wait.target()));
        emitter().emit("return this->__ret;");
        emitter().emit("");
    }

    default void emitInstruction(ActorMachine am, String name, Exec exec, Map<State, Integer> stateNumbers) {
        emitter().emit("transition_%d(%s);", exec.transition(), backend().instance().transitionIoArguments(am.getTransitions().get(exec.transition())));
        emitter().emit("this->program_counter = %d;", stateNumbers.get(exec.target()));
        emitter().emit("this->__ret = RETURN_EXECUTED;");
        emitter().emit("return  this->__ret;");
        emitter().emit("");
    }

    default void jumpInto(BitSet waitTargets) {
        emitter().emit("switch (this->program_counter) {");
        waitTargets.stream().forEach(s -> emitter().emit("case %d: goto S%1$d;", s));
        emitter().emit("}");
        emitter().emit("");
    }

    default Set<State> collectWaitTargets(List<? extends State> stateList) {
        Set<State> targets = new HashSet<>();
        for (State state : stateList) {
            Instruction i = state.getInstructions().get(0);
            if ((i.getKind() == InstructionKind.WAIT) || (i.getKind() == InstructionKind.EXEC) ) {
                i.forEachTarget(targets::add);
            }
        }
        return targets;
    }

}