package ch.epfl.vlsc.transformation.cal2am;

import se.lth.cs.tycho.attribute.ConstantEvaluator;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.PortCondition;
import se.lth.cs.tycho.ir.entity.am.PredicateCondition;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;
import se.lth.cs.tycho.ir.entity.cal.Action;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.entity.cal.InputPattern;
import se.lth.cs.tycho.ir.entity.cal.OutputExpression;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.transformation.cal2am.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CalToAmHw {
    protected final CalActor actor;
    private final EnumSet<KnowledgeRemoval.KnowledgeKind> onWait;
    private final EnumSet<KnowledgeRemoval.KnowledgeKind> onExec;
    private final Priorities priorities;
    private final Schedule schedule;

    private final Scopes scopes;
    private final Transitions transitions;
    private final Conditions conditions;

    private final Map<CalState, CalState> stateCache;

    public CalToAmHw(CalActor actor, Configuration configuration, ConstantEvaluator constants, Types types) {
        this.actor = actor;
        this.onWait = configuration.get(KnowledgeRemoval.forgetOnWait);
        this.onExec = configuration.get(KnowledgeRemoval.forgetOnExec);
        this.priorities = new Priorities(actor);
        this.schedule = new Schedule(actor);
        this.conditions = new Conditions(actor, constants);
        this.scopes = new Scopes(actor, constants, types);
        this.transitions = new Transitions(actor, scopes, conditions);
        this.stateCache = new HashMap<>();
    }

    public ActorMachine buildActorMachine() {
        return new ActorMachine(actor.getInputPorts(), actor.getOutputPorts(), actor.getTypeParameters(), actor.getValueParameters(), scopes.getScopes(), new CalController(), transitions.getAllTransitions(), conditions.getAllConditions());
    }

    private class CalController implements Controller {
        @Override
        public State getInitialState() {
            return cached(new CalState(schedule.getInitialState(), null, null, null));
        }
    }

    private CalState cached(CalState s) {
        return stateCache.computeIfAbsent(s, Function.identity());
    }

    private class CalState implements State {
        private List<Instruction> instructions;
        private final Set<String> state;
        private final Map<Port, PortKnowledge> inputPorts;
        private final Map<Port, PortKnowledge> outputPorts;
        private final Map<PredicateCondition, Boolean> predicateConditions;

        public CalState(Set<String> state, Map<Port, PortKnowledge> inputPorts, Map<Port, PortKnowledge> outputPorts, Map<PredicateCondition, Boolean> predicateConditions) {
            this.state = state == null ? Collections.emptySet() : state;
            this.inputPorts = inputPorts == null ? Collections.emptyMap() : inputPorts;
            this.outputPorts = outputPorts == null ? Collections.emptyMap() : outputPorts;
            this.predicateConditions = predicateConditions == null ? Collections.emptyMap() : predicateConditions;
        }

        @Override
        public List<Instruction> getInstructions() {
            if (instructions == null) {
                instructions = computeInstructions();
            }
            return instructions;
        }

        private List<Instruction> computeInstructions() {
            List<Action> notDisabled = schedule.getEligibleActions(state).stream()
                    .filter(action -> inputConditions(action) != Knowledge.FALSE)
                    .filter(action -> predicateConditions(action) != Knowledge.FALSE)
                    .collect(Collectors.toList());

            Set<QID> selectedTags = notDisabled.stream().map(Action::getTag).collect(Collectors.toSet());

            Set<QID> prioritizedTags = priorities.getPrioritized(state, selectedTags);

            List<Action> highPrioNotDisabled = notDisabled.stream()
                    .filter(action -> prioritizedTags.contains(action.getTag()))
                    .collect(Collectors.toList());

            List<Instruction> execInstrucitons = highPrioNotDisabled.stream()
                    .filter(action -> inputConditions(action) == Knowledge.TRUE)
                    .filter(action -> predicateConditions(action) == Knowledge.TRUE)
                    .filter(action -> outputConditions(action) == Knowledge.TRUE)
                    .map(this::createExec)
                    .collect(Collectors.toList());

            if (!execInstrucitons.isEmpty()) {
                return execInstrucitons;
            }

            List<Action> testable = highPrioNotDisabled.stream()
                    .filter(action -> outputConditions(action) != Knowledge.FALSE)
                    .collect(Collectors.toList());

            Stream<Instruction> inputTests = testable.stream()
                    .flatMap(action -> action.getInputPatterns().stream())
                    .filter(input -> portCondition(conditions.getCondition(input)) == Knowledge.UNKNOWN)
                    .map(this::createTest);

            Stream<Instruction> outputTests = testable.stream()
                    .flatMap(action -> action.getOutputExpressions().stream())
                    .filter(output -> portCondition(conditions.getCondition(output)) == Knowledge.UNKNOWN)
                    .map(this::createTest);

            Stream<Instruction> guardTests = testable.stream()
                    .flatMap(action -> action.getGuards().stream())
                    .filter(guard -> predicateCondition(conditions.getCondition(guard)) == Knowledge.UNKNOWN)
                    .map(this::createTest);

            List<Instruction> execOrTestInstrucitons = Stream.concat(execInstrucitons.stream(), Stream.concat(inputTests, Stream.concat(outputTests, guardTests))).collect(Collectors.toList());

            if (execOrTestInstrucitons.isEmpty()) {
                return Collections.singletonList(createWait());
            } else {
                return execOrTestInstrucitons;
            }
        }

        private Knowledge inputConditions(Action action) {
            Knowledge result = Knowledge.TRUE;
            for (InputPattern in : action.getInputPatterns()) {
                result = result.and(portCondition(conditions.getCondition(in)));
                if (result == Knowledge.FALSE) return result;
            }
            return result;
        }

        private Knowledge outputConditions(Action action) {
            Knowledge result = Knowledge.TRUE;
            for (OutputExpression out : action.getOutputExpressions()) {
                result = result.and(portCondition(conditions.getCondition(out)));
                if (result == Knowledge.FALSE) return result;
            }
            return result;
        }

        private Knowledge predicateConditions(Action action) {
            Knowledge result = Knowledge.TRUE;
            for (Expression guard : action.getGuards()) {
                result = result.and(predicateCondition(conditions.getCondition(guard)));
                if (result == Knowledge.FALSE) return result;
            }
            return result;
        }

        private Knowledge predicateCondition(PredicateCondition predicateCondition) {
            return Knowledge.ofNullable(predicateConditions.get(predicateCondition));
        }

        private Knowledge portCondition(PortCondition condition) {
            Map<Port, PortKnowledge> knowledge;
            if (condition.isInputCondition()) {
                knowledge = inputPorts;
            } else {
                knowledge = outputPorts;
            }
            return knowledge.getOrDefault(condition.getPortName(), PortKnowledge.nil()).has(condition.N());
        }

        private Exec createExec(Action action) {
            Map<Port, PortKnowledge> input;
            if (onExec.contains(KnowledgeRemoval.KnowledgeKind.INPUT)) {
                input = null;
            } else {
                input = new HashMap<>(inputPorts);
                action.getInputPatterns().stream()
                        .map(conditions::getCondition)
                        .forEach(cond -> input.compute(cond.getPortName(), (port, k) -> k.add(-cond.N())));
            }
            Map<Port, PortKnowledge> output;
            if (onExec.contains(KnowledgeRemoval.KnowledgeKind.OUTPUT)) {
                output = null;
            } else {
                output = new HashMap<>(outputPorts);
                action.getOutputExpressions().stream()
                        .map(conditions::getCondition)
                        .forEach(cond -> output.compute(cond.getPortName(), (port, k) -> k.add(-cond.N())));
            }
            Map<PredicateCondition, Boolean> guards;
            if (onExec.contains(KnowledgeRemoval.KnowledgeKind.GUARDS)) {
                guards = null;
            } else {
                guards = null; // TODO: implement a more fine grained removal
            }
            CalState target = cached(new CalState(schedule.targetState(state, action), input, output, guards));
            return new Exec(transitions.getTransitionIndex(action), target);
        }

        private CalState withCondition(PortCondition condition, boolean value) {
            Map<Port, PortKnowledge> ports;
            if (condition.isInputCondition()) {
                ports = new HashMap<>(inputPorts);
            } else {
                ports = new HashMap<>(outputPorts);
            }
            PortKnowledge current = ports.getOrDefault(condition.getPortName(), PortKnowledge.nil());
            PortKnowledge next;
            if (value) {
                next = current.withLowerBound(condition.N());
            } else {
                next = current.withUpperBound(condition.N() - 1);
            }
            ports.put(condition.getPortName(), next);
            Map<Port, PortKnowledge> inputPorts = this.inputPorts;
            Map<Port, PortKnowledge> outputPorts = this.outputPorts;
            if (condition.isInputCondition()) {
                inputPorts = ports;
            } else {
                outputPorts = ports;
            }
            return cached(new CalState(state, inputPorts, outputPorts, predicateConditions));
        }

        private CalState withCondition(PredicateCondition condition, boolean value) {
            Map<PredicateCondition, Boolean> predicateConditions = new HashMap<>(this.predicateConditions);
            predicateConditions.put(condition, value);
            return cached(new CalState(state, inputPorts, outputPorts, predicateConditions));
        }

        private Test createTest(InputPattern input) {
            PortCondition portCondition = conditions.getCondition(input);
            int index = conditions.getConditionIndex(input);
            return new Test(index, withCondition(portCondition, true), withCondition(portCondition, false));
        }

        private Test createTest(OutputExpression output) {
            PortCondition portCondition = conditions.getCondition(output);
            int index = conditions.getConditionIndex(output);
            return new Test(index, withCondition(portCondition, true), withCondition(portCondition, false));
        }

        private Test createTest(Expression guard) {
            PredicateCondition condition = conditions.getCondition(guard);
            int index = conditions.getConditionIndex(guard);
            return new Test(index, withCondition(condition, true), withCondition(condition, false));
        }

        public Wait createWait() {
            List<Action> temporarilyDisabled = schedule.getEligibleActions(state).stream()
                    .filter(action -> inputConditions(action) == Knowledge.FALSE || outputConditions(action) == Knowledge.FALSE)
                    .collect(Collectors.toList());
            BitSet waitingFor = new BitSet();
            temporarilyDisabled.stream()
                    .flatMap(action -> action.getInputPatterns().stream())
                    .mapToInt(conditions::getConditionIndex)
                    .forEach(waitingFor::set);
            temporarilyDisabled.stream()
                    .flatMap(action -> action.getOutputExpressions().stream())
                    .mapToInt(conditions::getConditionIndex)
                    .forEach(waitingFor::set);

            Map<Port, PortKnowledge> input;
            if (onWait.contains(KnowledgeRemoval.KnowledgeKind.INPUT)) {
                input = null;
            } else {
                input = withoutAbsenceKnowledge(inputPorts);
            }
            Map<Port, PortKnowledge> output;
            if (onWait.contains(KnowledgeRemoval.KnowledgeKind.OUTPUT)) {
                output = null;
            } else {
                output = withoutAbsenceKnowledge(outputPorts);
            }
            Map<PredicateCondition, Boolean> guards;
            if (onWait.contains(KnowledgeRemoval.KnowledgeKind.GUARDS)) {
                guards = null;
            } else {
                guards = predicateConditions;
            }
            CalState target = cached(new CalState(state, input, output, guards));
            return new Wait(target, waitingFor);
        }

        public Map<Port, PortKnowledge> withoutAbsenceKnowledge(Map<Port, PortKnowledge> knowledge) {
            Map<Port, PortKnowledge> result = new HashMap<>(knowledge);
            List<Port> ports = new ArrayList<>(result.keySet());
            for (Port port : ports) {
                PortKnowledge k = result.get(port);
                if (k.lowerBound() == 0) {
                    result.remove(port);
                } else {
                    result.put(port, k.withoutUpperBound());
                }
            }
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CalState calState = (CalState) o;

            if (getActor() != calState.getActor())
                if (!state.equals(calState.state)) return false;
            if (!inputPorts.equals(calState.inputPorts)) return false;
            if (!outputPorts.equals(calState.outputPorts)) return false;
            return predicateConditions.equals(calState.predicateConditions);

        }

        private CalActor getActor() {
            return actor;
        }

        @Override
        public int hashCode() {
            int result = state.hashCode();
            result = 31 * result + inputPorts.hashCode();
            result = 31 * result + outputPorts.hashCode();
            result = 31 * result + predicateConditions.hashCode();
            return result;
        }
    }
}
