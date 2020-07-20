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
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.*;
import java.util.function.Function;

@Module
public interface FsmController {
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

    default Set<State> keepStates(ActorMachine actorMachine) {
        Set<State> keepStates = new HashSet<>();
        Set<State> skipStates = new HashSet<>();

        List<? extends State> stateList = actorMachine.controller().getStateList();
        for (State s : stateList) {
            if (!skipStates.contains(s)) {
                Instruction instruction = s.getInstructions().get(0);
                if ((instruction.getKind() == InstructionKind.TEST)) {

                    Test test = (Test) instruction;

                    // -- Treat true
                    State trueState = test.targetTrue();
                    Instruction trueStateInstruction = trueState.getInstructions().get(0);
                    if (trueStateInstruction.getKind() == InstructionKind.TEST) {
                        Test secondTest = (Test) trueStateInstruction;
                        if (secondTest.targetTrue().getInstructions().get(0).getKind() == InstructionKind.EXEC) {
                            Exec exec = (Exec) secondTest.targetTrue().getInstructions().get(0);
                            keepStates.add(exec.target());
                        } else {
                            keepStates.add(trueState);
                        }

                        keepStates.add(secondTest.targetFalse());
                        skipStates.add(trueState);
                    }

                    // -- Treat false
                    State falseState = test.targetFalse();
                    Instruction falseStateInstruction = falseState.getInstructions().get(0);
                    if (falseStateInstruction.getKind() == InstructionKind.EXEC) {
                        Exec exec = (Exec) falseStateInstruction;
                        keepStates.add(exec.target());
                    } else {
                        keepStates.add(falseState);
                    }
                }
            }
        }
        return keepStates;
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

        Set<State> keepStates = keepStates(actorMachine);

        emitter().emit("switch (this->program_counter) {");
        emitter().increaseIndentation();
        {
            for (State s : stateList) {
                Instruction instruction = s.getInstructions().get(0);
                if (instruction.getKind() == InstructionKind.TEST && keepStates.contains(s)) {
                    emitter().emit("case %d:", stateMap.get(s));
                    initialize.apply(instruction).stream().forEach(scope ->
                            emitter().emit("scope_%d(%s);", scope, backend().instance().scopeArguments(actorMachine.getScopes().get(scope)))
                    );
                    emitInstruction(actorMachine, name, instruction, stateMap);
                    emitter().emit("break;");
                    emitter().emitNewLine();
                }
            }
            emitter().decreaseIndentation();
        }
        emitter().emit("\tdefault:");
        emitter().emit("\t\tthis->program_counter = 0;");

        emitter().emit("}");

        emitter().emit("return this->__ret;");
    }


    void emitInstruction(ActorMachine am, String name, Instruction instruction, Map<State, Integer> stateNumbers);

    default void emitInstruction(ActorMachine am, String name, Test test, Map<State, Integer> stateNumbers) {
        String io = "";
        if (am.getCondition(test.condition()) instanceof PortCondition) {
            PortCondition condition = (PortCondition) am.getCondition(test.condition());

            String portName = condition.getPortName().getName();
            io = portName + ", io";
        }else{
            io = "io";
        }

        emitter().emit("if (condition_%d(%s)) {", test.condition(), io);
        emitter().increaseIndentation();


        State tgtState = test.targetTrue();

        if (tgtState.getInstructions().get(0) instanceof Exec || tgtState.getInstructions().get(0) instanceof Wait) {
            Function<Instruction, BitSet> initialize;
            ScopeLiveness liveness = new ScopeLiveness(backend().scopes(), am, backend().scopeDependencies());
            initialize = liveness::init;
            Instruction instruction = tgtState.getInstructions().get(0);
            initialize.apply(instruction).stream().forEach(scope ->
                    emitter().emit("scope_%d(%s);", scope, backend().instance().scopeArguments(am.getScopes().get(scope)))
            );

            emitInstruction(am, name, instruction, stateNumbers);
        } else {
            Test secondTest = (Test) tgtState.getInstructions().get(0);
            State sencondTrueState = secondTest.targetTrue();
            Function<Instruction, BitSet> initialize;
            ScopeLiveness liveness = new ScopeLiveness(backend().scopes(), am, backend().scopeDependencies());
            initialize = liveness::init;
            io = "";
            if (am.getCondition(secondTest.condition()) instanceof PortCondition) {
                PortCondition condition = (PortCondition) am.getCondition(secondTest.condition());
                String portName = condition.getPortName().getName();
                io = portName + ", io";
            }else{
                io = "io";
            }

            Instruction instruction = tgtState.getInstructions().get(0);
            initialize.apply(instruction).stream().forEach(scope ->
                    emitter().emit("scope_%d(%s);", scope, backend().instance().scopeArguments(am.getScopes().get(scope)))
            );

            emitter().emit("if (condition_%d(%s)) {", secondTest.condition(), io);
            emitter().increaseIndentation();


            instruction = sencondTrueState.getInstructions().get(0);
            initialize.apply(instruction).stream().forEach(scope ->
                    emitter().emit("scope_%d(%s);", scope, backend().instance().scopeArguments(am.getScopes().get(scope)))
            );
            emitInstruction(am, name, instruction, stateNumbers);
            emitter().decreaseIndentation();
            emitter().emit("} else {");

            emitter().increaseIndentation();
            {
                tgtState = secondTest.targetFalse();
                if (tgtState.getInstructions().get(0) instanceof Exec || tgtState.getInstructions().get(0) instanceof Wait) {
                    liveness = new ScopeLiveness(backend().scopes(), am, backend().scopeDependencies());
                    initialize = liveness::init;
                    instruction = tgtState.getInstructions().get(0);
                    initialize.apply(instruction).stream().forEach(scope ->
                            emitter().emit("scope_%d(%s);", scope, backend().instance().scopeArguments(am.getScopes().get(scope)))
                    );

                    emitInstruction(am, name, instruction, stateNumbers);
                } else {
                    emitter().emit("this->program_counter = %d;", stateNumbers.get(secondTest.targetFalse()));
                    emitter().emit("this->__ret = RETURN_TEST;");
                }

            }
            emitter().decreaseIndentation();

            emitter().emit("}");
        }

        emitter().decreaseIndentation();
        emitter().emit("} else {");

        emitter().increaseIndentation();

        tgtState = test.targetFalse();

        if (tgtState.getInstructions().get(0) instanceof Exec || tgtState.getInstructions().get(0) instanceof Wait) {
            Function<Instruction, BitSet> initialize;
            ScopeLiveness liveness = new ScopeLiveness(backend().scopes(), am, backend().scopeDependencies());
            initialize = liveness::init;
            Instruction instruction = tgtState.getInstructions().get(0);
            initialize.apply(instruction).stream().forEach(scope ->
                    emitter().emit("scope_%d(%s);", scope, backend().instance().scopeArguments(am.getScopes().get(scope)))
            );
            emitInstruction(am, name, instruction, stateNumbers);
        } else {
            emitter().emit("this->program_counter = %d;", stateNumbers.get(test.targetFalse()));
            emitter().emit("this->__ret = RETURN_TEST;");
        }

        emitter().decreaseIndentation();
        emitter().emit("}");

    }

    default void emitInstruction(ActorMachine am, String name, Wait wait, Map<State, Integer> stateNumbers) {
        emitter().emit("this->program_counter = %d;", stateNumbers.get(wait.target()));
        emitter().emit("this->__ret = RETURN_WAIT;");
    }

    default void emitInstruction(ActorMachine am, String name, Exec exec, Map<State, Integer> stateNumbers) {
        emitter().emit("transition_%d(%s);", exec.transition(), backend().instance().transitionIoArguments(am.getTransitions().get(exec.transition())));
        State target = exec.target();
        boolean traceEnabled = backend().context().getConfiguration().isDefined(PlatformSettings.enableActionProfile) &&
                backend().context().getConfiguration().get(PlatformSettings.enableActionProfile);
        if (target.getInstructions().get(0) instanceof Exec) {
            Exec secondCall = (Exec) target.getInstructions().get(0);
            emitter().emit("transition_%d(%s);", secondCall.transition(), backend().instance().transitionIoArguments(am.getTransitions().get(secondCall.transition())));
            emitter().emit("this->program_counter = %d;", stateNumbers.get(secondCall.target()));
            if (traceEnabled) {
                backend().context()
                        .getReporter()
                        .report(
                                new Diagnostic(Diagnostic.Kind.ERROR,
                                        String.format("Error while emitting the FSMController: " +
                                                "Back to back transitions %d and %d lead to invalid " +
                                                "profiling information.", exec.transition(),
                                                secondCall.transition())));
            }

        } else {
            emitter().emit("this->program_counter = %d;", stateNumbers.get(exec.target()));

        }
        if (traceEnabled) {
            if (am.getTransitions().size() >= (1 << 15)) {

                backend().context()
                    .getReporter()
                    .report(
                            new Diagnostic(Diagnostic.Kind.ERROR, String.format(
                                    "The maximum number of supported actions" +
                                            "while traces are enabled is %d", (1 << 15) - 1)));

            }

            emitter().emit("action_id = %d << 17;", exec.transition());
            emitter().emit("action_size = %d << 2;", am.getTransitions().size());
            emitter().emit("this->__ret = action_id | action_size | RETURN_EXECUTED;");
        } else {

            emitter().emit("this->__ret = RETURN_EXECUTED;");
        }
    }

    default void jumpInto(BitSet waitTargets) {
        emitter().emit("switch (this->program_counter) {");
        waitTargets.stream().forEach(s -> emitter().emit("case %d: goto %1$d;", s));
        emitter().emit("}");
        emitter().emit("");
    }
}

