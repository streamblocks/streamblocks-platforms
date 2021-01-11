package ch.epfl.vlsc.hls.backend.controllers;

import ch.epfl.vlsc.hls.backend.VivadoHLSBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.ScopeLiveness;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.OnOffSetting;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Module
public interface BranchingController {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    OnOffSetting scopeLivenessAnalysis = new OnOffSetting() {
        @Override
        public String getKey() {
            return "scope-liveness-analysis";
        }

        @Override
        public String getDescription() {
            return "Analyzes actor machine scope liveness for initialization.";
        }

        @Override
        public Boolean defaultValue(Configuration configuration) {
            return true;
        }
    };

    default Map<State, Integer> stateMap(List<? extends State> stateList) {
        int i = 0;
        Map<State, Integer> result = new HashMap<>();
        for (State s : stateList) {
            result.put(s, i++);
        }
        return result;
    }

    default BitSet waitTargetBitSets(ActorMachine actorMachine){
        List<? extends State> stateList = actorMachine.controller().getStateList();
        Map<State, Integer> stateMap = stateMap(stateList);
        Set<State> waitTargets = collectWaitTargets(stateList);

        return waitTargets.stream().mapToInt(stateMap::get).collect(BitSet::new, BitSet::set, BitSet::or);
    }

    default void emitController(String name, ActorMachine actorMachine) {
        List<? extends State> stateList = actorMachine.controller().getStateList();
        Map<State, Integer> stateMap = stateMap(stateList);
        Set<State> waitTargets = collectWaitTargets(stateList);

        BitSet waitTargetsBitSet = waitTargets.stream().mapToInt(stateMap::get).collect(BitSet::new, BitSet::set, BitSet::or);

        List<String> ports = new ArrayList<>();
        // -- External memories
        ports.addAll(
                backend().externalMemory()
                        .getExternalMemories(actorMachine).map(v -> backend().externalMemory().name(name, v)));

        ports.addAll(actorMachine.getInputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));
        ports.addAll(actorMachine.getOutputPorts().stream().map(PortDecl::getName).collect(Collectors.toList()));
        ports.add("io");

        emitter().emit("switch (this->program_counter) {");
        emitter().increaseIndentation();

        waitTargetsBitSet.stream().forEach(b -> {
            State s = stateMap.entrySet().stream().filter(entry -> Objects.equals(entry.getValue(), b)).map(Map.Entry::getKey).findAny().orElse(null);
            emitter().emit("case %d: ", stateMap.get(s));
            emitter().emit("\t_ret = s_%s_%s(%s);", name, stateMap.get(s), String.join(", ", ports));
            emitter().emit("break;");
        });
        emitter().emit("default:");
        emitter().emit("\treturn RETURN_WAIT;");
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emitNewLine();

    }

    default String stateFunctionPrototype(String instanceName, boolean withClassName, Integer state) {
        // -- Actor Instance Name
        String className = "class_" + instanceName;

        return String.format("StateReturn %ss_%s_%s(%s)", withClassName ? className + "::" : "", instanceName,  state, backend().instance().entityPorts(instanceName, true, true));
    }

    default void emitStateFunction(String name, ActorMachine actorMachine, Function<Instruction, BitSet> initialize, Map<State, Integer> stateMap, State s){
        emitter().emit("%s{", stateFunctionPrototype(name, true, stateMap.get(s)));
        emitter().emit("#pragma HLS INLINE off");
        emitter().increaseIndentation();

        emitter().emit("StateReturn _ret;");
        emitter().emit("_ret.program_counter = %s;", stateMap.get(s));
        emitter().emit("_ret.return_code = RETURN_EXECUTED;");
        emitter().emitNewLine();

        boolean traceEnabled = backend().context().getConfiguration().isDefined(PlatformSettings.enableActionProfile) &&
                backend().context().getConfiguration().get(PlatformSettings.enableActionProfile);
        if (traceEnabled) {
            emitter().emit("unsigned int action_id = 0;");
            emitter().emit("unsigned int action_size = 0;");
            emitter().emitNewLine();
        }

        Instruction instruction = s.getInstructions().get(0);

        emitInstruction(actorMachine, name, instruction, stateMap, initialize, null);

        emitter().emitNewLine();
        emitter().emit("return _ret;");
        emitter().decreaseIndentation();

        emitter().emit("}");
        emitter().emitNewLine();
    }

    void emitInstruction(ActorMachine am, String name, Instruction instruction, Map<State, Integer> stateNumbers, Function<Instruction, BitSet> initialize, Instruction previous);

    default void emitInstruction(ActorMachine am, String name, Test test, Map<State, Integer> stateNumbers, Function<Instruction, BitSet> initialize, Instruction previous) {
        String io = "";
        if (am.getCondition(test.condition()) instanceof PortCondition) {
            PortCondition condition = (PortCondition) am.getCondition(test.condition());
            String portName = condition.getPortName().getName();
            io = portName + ", io";
        }else{
            io = "io";
        }
        initialize.apply(test).stream().forEach(scope ->
                emitter().emit("scope_%d(%s);", scope, backend().instance().scopeArguments(am.getScopes().get(scope)))
        );
        emitter().emit("if (condition_%d(%s)) {", test.condition(), io);
        emitter().increaseIndentation();
        emitter().emit("//goto S%d;", stateNumbers.get(test.targetTrue()));
        State s = test.targetTrue();
        Instruction instruction = s.getInstructions().get(0);
        initialize.apply(instruction).stream().forEach(scope ->
                emitter().emit("scope_%d(%s);", scope, backend().instance().scopeArguments(am.getScopes().get(scope)))
        );
        emitInstruction(am, name, instruction, stateNumbers, initialize, test);

        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();

        emitter().emit("//goto S%d;", stateNumbers.get(test.targetFalse()));
        emitInstruction(am, name, test.targetFalse().getInstructions().get(0), stateNumbers, initialize, test);
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void emitInstruction(ActorMachine am, String name, Wait wait, Map<State, Integer> stateNumbers, Function<Instruction, BitSet> initialize, Instruction previous) {
        emitter().emit("_ret.program_counter = %d;", stateNumbers.get(wait.target()));
        emitter().emit("_ret.return_code = RETURN_WAIT;");
    }

    default void emitInstruction(ActorMachine am, String name, Exec exec, Map<State, Integer> stateNumbers, Function<Instruction, BitSet> initialize, Instruction previous) {
        emitter().emit("transition_%d(%s);", exec.transition(), backend().instance().transitionIoArguments(am.getTransitions().get(exec.transition())));
        emitter().emit("_ret.program_counter = %d;", stateNumbers.get(exec.target()));


        boolean traceEnabled = backend().context().getConfiguration().isDefined(PlatformSettings.enableActionProfile) &&
                backend().context().getConfiguration().get(PlatformSettings.enableActionProfile);

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
            emitter().emit("_ret.return_code = (action_id | action_size | RETURN_EXECUTED);");
        } else {

            emitter().emit("_ret.return_code = RETURN_EXECUTED;");
        }
    }

    default void jumpInto(BitSet waitTargets) {
        emitter().emit("switch (this->program_counter) {");
        waitTargets.stream().forEach(s -> emitter().emit("case %d: goto S%1$d;", s));
        emitter().emit("}");
        emitter().emit("break;");
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