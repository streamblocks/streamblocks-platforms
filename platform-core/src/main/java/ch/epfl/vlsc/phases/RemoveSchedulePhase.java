package ch.epfl.vlsc.phases;

import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.Decl;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.cal.Action;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.entity.cal.Transition;
import se.lth.cs.tycho.ir.expr.ExprBinaryOp;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtIf;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.type.NominalTypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.AddSchedulePhase;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.ScheduleInitializersPhase;
import se.lth.cs.tycho.phase.ScheduleUntaggedPhase;
import se.lth.cs.tycho.transformation.cal2am.Schedule;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Phase that removes the explicit schedule FSM from actors by:
 *  - Introducing a local state variable holding the current FSM state (as String).
 *  - Adding a guard to each action restricting it to the set of FSM states in which it is eligible.
 *  - Appending an assignment at the end of the action body updating the state variable to the
 *    (deterministic) target state after firing the action.
 *  - Finally removing the schedule FSM from the actor.
 *
 */
public class RemoveSchedulePhase implements Phase {

    @Override
    public String getDescription() {
        return "Removes FSM schedule by lowering it to a state variable + guards";
    }

    @Override
    public Set<Class<? extends Phase>> dependencies() {
        // We need the schedule to be fully constructed first (including untagged + initializers)
        return Stream.of(ScheduleInitializersPhase.class, ScheduleUntaggedPhase.class, AddSchedulePhase.class)
                .collect(Collectors.toSet());
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) {
        return task.transformChildren(MultiJ.instance(Transformation.class));
    }

    @Module
    interface Transformation extends IRNode.Transformation {
        @Override
        default IRNode apply(IRNode node) { return node.transformChildren(this); }

        default IRNode apply(Decl decl) { return decl; }

        default IRNode apply(GlobalEntityDecl decl) { return decl.transformChildren(this); }

        default IRNode apply(Entity entity) { return entity; }

        default IRNode apply(CalActor actor) {
            if (actor.getScheduleFSM() == null) {
                return actor; // -- Nothing to remove.
            }

            Schedule schedule = new Schedule(actor);

            // -- Collect and enumerate all FSM states (integer encoding) – ensure deterministic order with initial state first.
            LinkedHashSet<String> allStates = new LinkedHashSet<>();
            allStates.add(actor.getScheduleFSM().getInitialState());
            actor.getScheduleFSM().getTransitions().forEach(t -> { allStates.add(t.getSourceState()); allStates.add(t.getDestinationState()); });

            // -- Build mapping stateName -> int id
            Map<String, Integer> stateIds = new LinkedHashMap<>();
            int idCounter = 0;
            for (String s : allStates) {
                stateIds.put(s, idCounter++);
            }

            // -- Create (or reuse if name clashes) state variable name.
            String stateVarName = "$state";
            Set<String> existingVarNames = actor.getVarDecls().stream().map(LocalVarDecl::getName).collect(Collectors.toSet());
            int suffix = 0;
            while (existingVarNames.contains(stateVarName)) {
                stateVarName = "$state" + (++suffix);
            }

            // -- Initial value literal for state variable (integer encoded state id).
            int initId = stateIds.get(actor.getScheduleFSM().getInitialState());
            ExprLiteral initValue = new ExprLiteral(ExprLiteral.Kind.Integer, Integer.toString(initId));
            LocalVarDecl stateVarDecl = new LocalVarDecl(Collections.emptyList(), new NominalTypeExpr("int"), stateVarName, initValue, false);

            Variable stateVar = Variable.variable(stateVarName);
            ExprVariable stateVarExpr = new ExprVariable(stateVar);

            List<Action> transformed = new ArrayList<>();
            for (Action action : actor.getActions()) {
                Action newAction = transformAction(action, actor, schedule, allStates, stateIds, stateVarExpr, stateVarName);
                transformed.add(newAction);
            }

            List<LocalVarDecl> newVarDecls = new ArrayList<>(actor.getVarDecls());
            newVarDecls.add(stateVarDecl);

            // -- Remove schedule FSM (set to null)
            return actor.withActions(transformed)
                    .withVarDecls(newVarDecls)
                    .withScheduleFSM(null);
        }

        default Action transformAction(Action action, CalActor actor, Schedule schedule, Set<String> allStates,
                                       Map<String, Integer> stateIds, ExprVariable stateVarExpr, String stateVarName) {
            Set<String> eligibleStates = schedule.getStates(action);
            if (eligibleStates == null || eligibleStates.isEmpty()) {
                // Should not happen; leave action unchanged.
                return action;
            }

            // -- Build guard if not eligible in all states.
            List<Expression> newGuards = new ArrayList<>(action.getGuards());
            if (eligibleStates.size() != allStates.size()) {
                Expression guard = null;
                for (String st : eligibleStates) {
                    ExprLiteral stLit = new ExprLiteral(ExprLiteral.Kind.Integer, Integer.toString(stateIds.get(st)));
                    Expression eq = new ExprBinaryOp(ImmutableList.of("=="), ImmutableList.of(stateVarExpr.deepClone(), stLit));
                    if (guard == null) {
                        guard = eq;
                    } else {
                        guard = new ExprBinaryOp(ImmutableList.of("or"), ImmutableList.of(guard, eq));
                    }
                }
                newGuards.add(guard);
            }

            // -- Compute per-source-state target mapping.
            Map<String, String> perSourceTarget = new LinkedHashMap<>(); // source -> destination state name
            boolean nondeterministicInState = false;
            Set<String> allDestinations = new LinkedHashSet<>();
            if (action.getTag() != null) {
                for (String src : eligibleStates) {
                    List<String> dests = actor.getScheduleFSM().getTransitions().stream()
                            .filter(t -> t.getSourceState().equals(src))
                            .filter(t -> t.getActionTags().stream().anyMatch(tag -> tag.isPrefixOf(action.getTag())))
                            .map(Transition::getDestinationState)
                            .distinct()
                            .collect(Collectors.toList());
                    if (dests.size() > 1) { // true non-determinism inside a single source state
                        nondeterministicInState = true;
                        break;
                    } else if (dests.size() == 1) {
                        perSourceTarget.put(src, dests.get(0));
                        allDestinations.add(dests.get(0));
                    }
                }
            }

            List<Statement> newBody = new ArrayList<>(action.getBody());
            if (!nondeterministicInState && !perSourceTarget.isEmpty()) {
                if (allDestinations.size() == 1) {
                    // -- Single destination irrespective of source state -> single assignment
                    String nextState = allDestinations.iterator().next();
                    ExprLiteral nextLit = new ExprLiteral(ExprLiteral.Kind.Integer, Integer.toString(stateIds.get(nextState)));
                    StmtAssignment assign = new StmtAssignment(new LValueVariable(Variable.variable(stateVarExpr.getVariable().getName())), nextLit);
                    newBody.add(assign);
                } else {
                    // -- Multiple possible destinations depending on current state; generate if statements
                    for (Map.Entry<String, String> e : perSourceTarget.entrySet()) {
                        String src = e.getKey();
                        String dst = e.getValue();
                        if (dst == null || dst.equals(src)) continue; // skip self-loop (no state change)
                        ExprLiteral srcLit = new ExprLiteral(ExprLiteral.Kind.Integer, Integer.toString(stateIds.get(src)));
                        Expression cond = new ExprBinaryOp(ImmutableList.of("=="), ImmutableList.of(stateVarExpr.deepClone(), srcLit));
                        ExprLiteral dstLit = new ExprLiteral(ExprLiteral.Kind.Integer, Integer.toString(stateIds.get(dst)));
                        StmtAssignment assign = new StmtAssignment(new LValueVariable(Variable.variable(stateVarExpr.getVariable().getName())), dstLit);
                        StmtIf stmtIf = new StmtIf(cond, Collections.singletonList(assign), null);
                        newBody.add(stmtIf);
                    }
                }
            }

            // Rebuild action via copy
            return action.copy(
                    action.getTag(),
                    action.getAnnotations(),
                    action.getInputPatterns(),
                    action.getOutputExpressions(),
                    action.getTypeDecls(),
                    action.getVarDecls(),
                    ImmutableList.from(newGuards),
                    ImmutableList.from(newBody),
                    action.getDelay(),
                    action.getPreconditions(),
                    action.getPostconditions()
            );
        }

    }
}
