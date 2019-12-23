package ch.epfl.vlsc.hls.backend.controllers;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.ScopeLiveness;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;

import java.util.*;
import java.util.function.Function;

@Module
public interface StrawManController {
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

    default State getTargetState(State s) {
        Instruction instruction = s.getInstructions().get(0);

        if (instruction.getKind() == InstructionKind.EXEC) {
            Exec exec = (Exec) instruction;
            return exec.target();
        } else {
            Wait exec = (Wait) instruction;
            return exec.target();
        }

    }

    default void emitController(String name, ActorMachine actorMachine) {
        List<? extends State> stateList = actorMachine.controller().getStateList();
        Map<State, Integer> stateMap = stateMap(stateList);

        Function<Instruction, BitSet> initialize;
        ScopeLiveness liveness = new ScopeLiveness(backend().scopes(), actorMachine, backend().scopeDependencies());
        initialize = liveness::init;

        emitter().emit("int\tcurrent_state = this->program_counter;");
        emitter().emit("int\tnext_state = this->program_counter;");
        emitter().emit("bool waited = false;");
        emitter().emit("bool executed = false;");
        emitter().emit("this->__ret = -1;");
        emitter().emit("// StrawMan Controller");
        emitter().emit("while (!waited & !executed) {");
        {
            emitter().increaseIndentation();

            emitter().emit("switch (current_state) {");
            emitter().increaseIndentation();
            {
                for (State s : stateList) {

                    // Assuming Single Instruction Actor Machine
                    assert (s.getInstructions().size() == 1);
                    Instruction instruction = s.getInstructions().get(0);

                    if (instruction.getKind() == InstructionKind.WAIT) {
                        emitter().emit("// %d is WAIT instruction", stateMap.get(s));
                    } else if (instruction.getKind() == InstructionKind.TEST) {
                        emitter().emit("// %d is TEST instruction", stateMap.get(s));
                    } else if (instruction.getKind() == InstructionKind.EXEC) {
                        emitter().emit("// %d is EXEC instruction", stateMap.get(s));
                    }
                    emitter().emit("case %d:", stateMap.get(s));
                    emitInstruction(actorMachine, name, instruction, stateMap);

                }
                emitter().decreaseIndentation();
            }
            emitter().emit("\tdefault: // should not happen!");
            // emitter().emit("\t\tstd::cout << this->program_counter << std::endl; ");
            // emitter().emit("\t\tthis->__ret = -10;");
            emitter().emit("\t\tthis->program_counter = 0;");
            emitter().emit("}");


            emitter().emitNewLine();

            emitter().emit("current_state = next_state;");
            emitter().emit("");
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emit("this->program_counter = current_state;");
        emitter().emit("return this->__ret;");
    }

    void emitInstruction(ActorMachine am, String name, Instruction instruction, Map<State, Integer> stateNumbers);

    default void emitInstruction(ActorMachine am, String name, Test test, Map<State, Integer> stateNumbers) {
        String testArgs = "";

        String waitKind = "";
        if (am.getCondition(test.condition()) instanceof PortCondition) {
            PortCondition condition = (PortCondition) am.getCondition(test.condition());
            if (condition.isInputCondition()) {
                waitKind = String.format("this->__ret = %s;", "RETURN_WAIT_INPUT");
            } else {
                waitKind = String.format("this->__ret = %s;", "RETURN_WAIT_OUTPUT");
            }
            String portName = ((PortCondition) am.getCondition(test.condition())).getPortName().getName();
            testArgs = portName + ", io";
        } else {
            waitKind = String.format("this->__ret = %s;", "RETURN_WAIT_PREDICATE");
        }
        emitter().emit("if (condition_%d(%s)) { // condition true", test.condition(), testArgs);
        {
            emitter().increaseIndentation();
            emitter().emit("next_state = %d;", stateNumbers.get(test.targetTrue()));
            emitter().decreaseIndentation();
        }
        emitter().emit("} else { // condition false");
        {
            emitter().increaseIndentation();
            emitter().emit("next_state = %d;", stateNumbers.get(test.targetFalse()));
            emitter().emit(waitKind);
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
        emitter().emit("break;");
        emitter().emitNewLine();
    }

    default void emitInstruction(ActorMachine am, String name, Wait wait, Map<State, Integer> stateNumbers) {

        emitter().emit("next_state = %d;", stateNumbers.get(wait.target()));
        emitter().emit("waited = true;");
        emitter().emit("break;");

        emitter().emitNewLine();
    }

    default void emitInstruction(ActorMachine am, String name, Exec exec, Map<State, Integer> stateNumbers) {

        emitter().emit("transition_%d(%s);", exec.transition(),
                backend().instance().transitionIoArguments(am.getTransitions().get(exec.transition())));
        emitter().emit("executed = true;");
        emitter().emit("next_state = %d;", stateNumbers.get(exec.target()));
        emitter().emit("this->__ret = RETURN_EXECUTED;");

        emitter().emit("break;");
        emitter().emitNewLine();
    }

}
