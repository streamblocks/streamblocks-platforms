package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.MathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Ports;
import se.lth.cs.tycho.attribute.ScopeLiveness;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;

import java.util.*;
import java.util.function.Function;

@Module
public interface Controller {
    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    @Binding(BindingKind.INJECTED)
    Ports ports();

    default Emitter emitter() {
        return backend().emitter();
    }


    default void emitController(String name, ActorMachine actorMachine) {
        List<? extends State> stateList = actorMachine.controller().getStateList();
        Map<State, Integer> stateMap = stateMap(stateList);
        Set<State> waitTargets = collectWaitTargets(stateList);


        emitter().emitClikeBlockComment("Action Scheduler");
        emitter().emitNewLine();
        emitter().emit("void %s_scheduler(schedinfo_t *si){", name);
        emitter().increaseIndentation();

        emitter().emit("int i = 0;");
        emitter().emit("si->ports = 0;");
        emitter().emitNewLine();

        // -- Traces
        if (backend().instance().enableTraces()) {
            actorMachine.getInputPorts().forEach(port ->
                    emitter().emit("file_%s = fopen(\"traces/%s_%1$s.txt\", \"a\");", port.getName(), backend().instance().instanceQidName())
            );
            actorMachine.getOutputPorts().forEach(port ->
                    emitter().emit("file_%s = fopen(\"traces/%s_%1$s.txt\", \"a\");", port.getName(), backend().instance().instanceQidName())
            );
            emitter().emitNewLine();
        }

        actorMachine.getInputPorts().forEach(p -> {
            if (backend().channelUtils().isTargetConnected(backend().instancebox().get().getInstanceName(), p.getName())) {
                if (backend().channelUtils().isTargetConnected(backend().instancebox().get().getInstanceName(), p.getName())) {
                    emitter().emit("read_%s();", p.getName());
                }
            }
        });
        actorMachine.getOutputPorts().forEach(p -> {
                    if (backend().channelUtils().isSourceConnected(backend().instancebox().get().getInstanceName(), p.getName())) {
                        emitter().emit("write_%s();", p.getName());
                    }
                }
        );
        emitter().emitNewLine();

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
            emitter().emit("l_S%d:", stateMap.get(s));
            Instruction instruction = s.getInstructions().get(0);
            initialize.apply(instruction).stream().forEach(scope ->
                    emitter().emit("%s_init_scope_%d();", name, scope)
            );
            emitInstruction(actorMachine, name, instruction, stateMap);
        }

        emitter().emit("finished:");
        // -- Traces
        if (backend().instance().enableTraces()) {
            actorMachine.getInputPorts().forEach(port ->
                    emitter().emit("fclose(file_%s);", port.getName()));
            actorMachine.getOutputPorts().forEach(port ->
                    emitter().emit("fclose(file_%s);", port.getName()));
            emitter().emitNewLine();
        }
        actorMachine.getInputPorts().forEach(p -> {
                    if (backend().channelUtils().isTargetConnected(backend().instancebox().get().getInstanceName(), p.getName())) {
                        emitter().emit("read_end_%s();", p.getName());
                    }
                }
        );
        actorMachine.getOutputPorts().forEach(p -> {
            if (backend().channelUtils().isSourceConnected(backend().instancebox().get().getInstanceName(), p.getName())) {
                emitter().emit("write_end_%s();", p.getName());
            }
        });


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

    void emitInstruction(ActorMachine am, String name, Instruction instruction, Map<State, Integer> stateNumbers);

    default void emitInstruction(ActorMachine am, String name, Test test, Map<State, Integer> stateNumbers) {
        emitter().emit("if (%s_condition_%d()) {", name, test.condition());
        emitter().increaseIndentation();
        emitter().emit("goto l_S%d;", stateNumbers.get(test.targetTrue()));
        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();
        if (am.getCondition(test.condition()) instanceof PortCondition) {
            emitter().emit("si->num_firings = i;");
            PortCondition condition = (PortCondition) am.getCondition(test.condition());
            if (condition.isInputCondition()) {
                emitter().emit("si->reason = starved;");
            } else {
                emitter().emit("si->reason = full;");
            }
        }
        emitter().emit("goto l_S%d;", stateNumbers.get(test.targetFalse()));
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("");
    }

    default void emitInstruction(ActorMachine am, String name, Wait wait, Map<State, Integer> stateNumbers) {
        emitter().emit("_FSM_state = my_state_S%d;", stateNumbers.get(wait.target()));
        emitter().emit("goto finished;");
        emitter().emit("");
    }

    default void emitInstruction(ActorMachine am, String name, Exec exec, Map<State, Integer> stateNumbers) {
        boolean alwaysAligned = backend().instance().transitionAlwaysAligned().get(am.getTransitions().get(exec.transition()));
        boolean alignable = backend().instance().transitionAlignable().get(am.getTransitions().get(exec.transition()));

        if (alwaysAligned) {
            emitter().emit("%s_transition_%d_aligned();", name, exec.transition());
        } else if (alignable) {
            emitter().emit("{");
            emitter().increaseIndentation();
            emitter().emit("i32 isAligned = 1;");
            Transition transition = am.getTransitions().get(exec.transition());
            for (Port port : transition.getInputRates().keySet()) {
                if (backend().channelUtils().isTargetConnected(backend().instancebox().get().getInstanceName(), port.getName())) {
                    boolean isAlignable = transition.getInputRates().get(port) >= 2;

                    if (isAlignable && !backend().instance().portAlwaysAligned().get(port)) {
                        emitter().emit("isAligned &= ((index_%1$s %% SIZE_%1$s) < ((index_%1$s + %2$d) %% SIZE_%1$s));", port.getName(), transition.getInputRates().get(port));
                    }
                }
            }

            for (Port port : transition.getOutputRates().keySet()) {
                if (backend().channelUtils().isSourceConnected(backend().instancebox().get().getInstanceName(), port.getName())) {
                    boolean isAlignable = transition.getOutputRates().get(port) >= 2;

                    if (isAlignable && !backend().instance().portAlwaysAligned().get(port)) {
                        emitter().emit("isAligned &= ((index_%1$s %% SIZE_%1$s) < ((index_%1$s + %2$d) %% SIZE_%1$s));", port.getName(), transition.getOutputRates().get(port));
                    }
                }
            }


            emitter().emit("if(isAligned){");
            emitter().emit("\t%s_transition_%d_aligned();", name, exec.transition());
            emitter().emit("} else {");
            emitter().emit("\t%s_transition_%d();", name, exec.transition());
            emitter().emit("}");
            emitter().decreaseIndentation();
            emitter().emit("}");
        } else {
            emitter().emit("%s_transition_%d();", name, exec.transition());
        }

        emitter().emit("i++;");
        emitter().emit("goto l_S%d;", stateNumbers.get(exec.target()));
        emitter().emit("");
    }

    default void jumpInto(BitSet waitTargets) {
        emitter().emit("switch (_FSM_state) {");
        waitTargets.stream().forEach(s -> emitter().emit("case my_state_S%d: goto l_S%1$d;", s));
        //emitter().emit("default:");
        //emitter().emit("\tprintf(\"unknown state : %%s\\n\", stateNames[_FSM_state]);");
        //emitter().emit("\texit(1);");
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
