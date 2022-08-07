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
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.Diagnostic;

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

//    default ImmutableList<Test> getExecConditions(Exec exec, Controller controller) {
//
//
//
//    }

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
    }


    void emitInstruction(ActorMachine am, String name, Instruction instruction, Map<State, Integer> stateNumbers);

    default void emitInstruction(ActorMachine am, String name, Test test, Map<State, Integer> stateNumbers) {
        String io = "";
        if (am.getCondition(test.condition()) instanceof PortCondition) {
            PortCondition condition = (PortCondition) am.getCondition(test.condition());
            String portName = condition.getPortName().getName();
            io = portName + ", " + (backend().instance().hasPipelinedController(am) ? "local_io" : "io");
        } else{
            io = backend().instance().hasPipelinedController(am) ? "local_io" : "io";
        }
        emitter().emit("if (condition_%d(%s)) {", test.condition(), io);
        emitter().increaseIndentation();
        emitter().emit("goto S%d;", stateNumbers.get(test.targetTrue()));
        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();
        emitter().emit("goto S%d;", stateNumbers.get(test.targetFalse()));
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
    }

    default void emitInstruction(ActorMachine am, String name, Wait wait, Map<State, Integer> stateNumbers) {
        emitter().emit("this->program_counter = %d;", stateNumbers.get(wait.target()));
        boolean pipelined = backend().instance().hasPipelinedController(am);
        if (pipelined) {
            emitter().emit("yield = true;");
            emitter().emit("break;");
        } else {
            emitter().emit("return RETURN_WAIT;");
        }
        emitter().emit("");
    }

    default void emitInstruction(ActorMachine am, String name, Exec exec, Map<State, Integer> stateNumbers) {
        emitter().emit("transition_%d(%s);", exec.transition(), backend().instance().transitionIoArguments(am.getTransitions().get(exec.transition())));
        emitter().emit("this->program_counter = %d;", stateNumbers.get(exec.target()));

        boolean pipelined = backend().instance().hasPipelinedController(am);


        boolean traceEnabled = backend().context().getConfiguration().isDefined(PlatformSettings.enableActionProfile) &&
                backend().context().getConfiguration().get(PlatformSettings.enableActionProfile);

        if (traceEnabled && pipelined) {
            backend().context().getReporter().report(new Diagnostic(Diagnostic.Kind.WARNING, "Actor instance "
                    + name + " has controller pipelining enabled and can not be used for per action profiling" +
                    "consider turning off profiling with --set disable-pipelining=on"));
        }
        if (pipelined) {

            Transition transition = am.getTransitions().get(exec.transition());
            transition.getInputRates().forEach((port, n) -> {
                emitter().emit("local_io.%s_count -= %d;\n", port.getName(), n);
            });
            transition.getOutputRates().forEach((port, n) -> {
                emitter().emit("local_io.%s_count += %d;\n", port.getName(), n);
            });

            emitter().emit("_ret = RETURN_EXECUTED;");
            emitter().emit("continue;");

        } else {
            if (traceEnabled) {
                if (am.getTransitions().size() >= (1 << 15)) {

                    backend().context()
                            .getReporter()
                            .report(
                                    new Diagnostic(Diagnostic.Kind.ERROR, String.format(
                                            "The maximum number of supported actions" +
                                                    "while action profile is enabled is %d", (1 << 15) - 1)));

                }
                emitter().emit("action_id = %d << 17;", exec.transition());
                emitter().emit("action_size = %d << 2;", am.getTransitions().size());
                emitter().emit("return (action_id | action_size | RETURN_EXECUTED);");
            } else {

                emitter().emit("return RETURN_EXECUTED;");
            }
        }

        emitter().emit("");
    }

    default void jumpInto(BitSet waitTargets) {
        emitter().emit("switch (this->program_counter) {");
        emitter().increaseIndentation();
        waitTargets.stream().forEach(s -> emitter().emit("case %d: goto S%1$d;", s));
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
    }

    default Set<State> collectWaitTargets(List<? extends State> stateList) {
        Set<State> targets = new HashSet<>();
        for (State state : stateList) {
            Instruction i = state.getInstructions().get(0);
            if ((i.getKind() == InstructionKind.WAIT) || (i.getKind() == InstructionKind.EXEC)) {
                i.forEachTarget(targets::add);
            }
        }
        return targets;
    }

}
