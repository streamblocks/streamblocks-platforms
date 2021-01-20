package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Ports;
import se.lth.cs.tycho.attribute.ScopeLiveness;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.PredicateCondition;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.OnOffSetting;

import java.util.*;
import java.util.function.Function;

@Module
public interface Controllers {
    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

    @Binding(BindingKind.INJECTED)
    Ports ports();

    default Emitter emitter() {
        return backend().emitter();
    }


    default void emitController(String name, ActorMachine actorMachine) {
        List<? extends State> stateList = actorMachine.controller().getStateList();
        Map<State, Integer> stateMap = stateMap(stateList);
        Set<State> waitTargets = collectWaitTargets(stateList);

        int sizeIN = 0;
        for (PortDecl inputPort : actorMachine.getInputPorts()) {
            if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), inputPort.getName())) {
                sizeIN++;
            }
        }

        int sizeOUT = 0;
        for (PortDecl outputPort : actorMachine.getOutputPorts()) {
            if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), outputPort.getName())) {
                sizeOUT++;
            }
        }

        emitter().emit("// -- Scheduler Definitions");
        emitter().emit("static const int exitcode_block_Any[3] = {1,0,1};");
        emitter().emitNewLine();
        emitter().emit("ART_ACTION_SCHEDULER(%s_scheduler){", name);
        emitter().increaseIndentation();

        emitter().emit("const int *result = EXIT_CODE_YIELD;");
        emitter().emitNewLine();

        // -- Actor Instance
        String actorInstanceName = "ActorInstance_" + name;
        emitter().emit("%s *thisActor = (%1$s*) pBase;", actorInstanceName);
        emitter().emit("ART_ACTION_SCHEDULER_ENTER(%d, %d)", sizeIN, sizeOUT);

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
                    emitter().emit("%s_init_scope_%d(context, thisActor);", name, scope)
            );
            emitInstruction(actorMachine, name, instruction, s, stateMap);
        }

        emitter().emit("out:");
        emitter().emit("ART_ACTION_SCHEDULER_EXIT(%d, %d)", sizeIN, sizeOUT);
        emitter().emit("return result;");
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

    void emitInstruction(ActorMachine am, String name, Instruction instruction, State from, Map<State, Integer> stateNumbers);

    default void emitInstruction(ActorMachine am, String name, Test test, State from, Map<State, Integer> stateNumbers) {
        String exitCode = "";
        if (am.getCondition(test.condition()) instanceof PortCondition) {
            PortCondition condition = (PortCondition) am.getCondition(test.condition());
            PortDecl port = ports().declaration(condition.getPortName());
            if (condition.isInputCondition()) {
                exitCode = String.format("static const int exitCode[] = {EXITCODE_BLOCK(1), %d, %d};", am.getInputPorts().indexOf(port), condition.N());
            } else {
                exitCode = String.format("static const int exitCode[] = {EXITCODE_BLOCK(1), %d, %d};", am.getOutputPorts().indexOf(port), condition.N());
            }
        } else {
            exitCode = String.format("static const int exitCode[] = {EXIT_CODE_PREDICATE, EXITCODE_PREDICATE(%d)};", test.condition());
        }
        emitter().emit("if (ART_TEST_CONDITION(%s_condition_%d)) {", name, test.condition());
        emitter().increaseIndentation();
        emitter().emit("goto S%d;", stateNumbers.get(test.targetTrue()));
        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();
        emitter().emit("%s", exitCode);
        emitter().emit("result = exitCode;");
        emitter().emit("goto S%d;", stateNumbers.get(test.targetFalse()));
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
    }

    default void emitInstruction(ActorMachine am, String name, Wait wait, State from, Map<State, Integer> stateNumbers) {
        emitter().emit("thisActor->program_counter = %d;", stateNumbers.get(wait.target()));
        State to = wait.target();
        if (from == to) {
            emitter().emit("result = EXITCODE_TERMINATE;");
        }
        emitter().emit("goto out;");
        emitter().emit("");
    }

    default void emitInstruction(ActorMachine am, String name, Exec exec, State from, Map<State, Integer> stateNumbers) {
        emitter().emit("ART_EXEC_TRANSITION(%s_transition_%d);", name, exec.transition());
        emitter().emit("goto S%d;", stateNumbers.get(exec.target()));
        emitter().emit("");
    }

    default void jumpInto(BitSet waitTargets) {
        emitter().emit("switch (thisActor->program_counter) {");
        waitTargets.stream().forEach(s -> emitter().emit("case %d: goto S%1$d;", s));
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
