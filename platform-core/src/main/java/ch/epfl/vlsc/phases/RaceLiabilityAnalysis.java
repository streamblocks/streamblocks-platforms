package ch.epfl.vlsc.phases;


import org.multij.*;
import org.multij.Module;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Condition;
import se.lth.cs.tycho.ir.entity.am.Transition;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.reporting.Reporter;



public class RaceLiabilityAnalysis implements Phase{

    @Override
    public String getDescription() {
        return "Analyses Actor Machine controllers for race conditions (timing dependent action conditions) " +
                "and issues warnings";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        RaceChecker checker = MultiJ.from(RaceChecker.class)
                .bind("tree").to(task.getModule(TreeShadow.key))
                .bind("context").to(context).instance();
        checker.check(task);
        return task;
    }

    static class TransitionSelectionPath {
        public static class ResolvedCondition {
            public final int cond;
            public final boolean resolve;
            public ResolvedCondition(int cond, boolean truth) {
                this.cond = cond;
                this.resolve = truth;
            }
        }
        public final ImmutableList<ResolvedCondition> conds;
        public final int transition;
        public TransitionSelectionPath (ImmutableList<ResolvedCondition> conds, int trans) {
            this.conds = conds;
            this.transition = trans;
        }




    }

    static class TransitionPathCollector {

        private ImmutableList.Builder<TransitionSelectionPath> paths;
        private final ActorMachine am;
        public TransitionPathCollector(ActorMachine am) {
            this.paths = ImmutableList.builder();
            this.am = am;
        }
        public static TransitionPathCollector builder(ActorMachine am) {
            return new TransitionPathCollector(am);
        }
        public TransitionPathCollector build(State startState) {
            return build(startState, ImmutableList.empty());

        }
        private TransitionPathCollector build(State startState, ImmutableList<TransitionSelectionPath.ResolvedCondition> conditions) {

            Instruction inst = startState.getInstructions().get(0);
            if (inst.getKind() == InstructionKind.EXEC) {
                Exec execInst = (Exec) inst;
                this.paths.add(
                        new TransitionSelectionPath(
                                conditions,
                                execInst.transition()
                        ));
            } else if (inst.getKind() == InstructionKind.TEST) {
                Test testInst  = (Test) inst;
                ImmutableList.Builder<TransitionSelectionPath.ResolvedCondition> newLsTrue = ImmutableList.builder();
                newLsTrue.addAll(conditions).add(new TransitionSelectionPath.ResolvedCondition(testInst.condition(), true));
                build(testInst.targetTrue(), newLsTrue.build());

                ImmutableList.Builder<TransitionSelectionPath.ResolvedCondition> newLsFalse = ImmutableList.builder();
                newLsFalse.addAll(conditions).add(new TransitionSelectionPath.ResolvedCondition(testInst.condition(), false));
                build(testInst.targetFalse(), newLsFalse.build());

            } else { // InstructionKind.WAIT

            }
            return this;
        }
        public ImmutableList<TransitionSelectionPath> getPaths() {
            return this.paths.build();
        }

        public static boolean checkMutual(TransitionSelectionPath p1, TransitionSelectionPath p2, ActorMachine am) {

            int maxIndex = Math.min(p1.conds.size(), p2.conds.size());
            int ix;
            for (ix = 0; ix < maxIndex; ix++) {
                if (p1.conds.get(ix).cond != p2.conds.get(ix).cond)
                    break;
            }

            if (ix == 0) {
                throw new CompilationException(new Diagnostic(Diagnostic.Kind.ERROR, "Invalid actor controller!"));
            }
            int largestPrefixIndex = ix - 1;
            Condition cond = am.getCondition(p1.conds.get(largestPrefixIndex).cond);
            if (cond.kind() == Condition.ConditionKind.predicate) {
                return true;
            } else {
                return p1.transition == p2.transition;
            }
        }
    }
    @Module
    interface RaceChecker {


        @Binding(BindingKind.INJECTED)
        TreeShadow tree();
        @Binding(BindingKind.INJECTED)
        Context context();



        default void check(IRNode task) {
            task.forEachChild(this::check);
        }
        default void check(ActorMachine am) {
            ImmutableList<State> startStates = collectStartStates(am);

            for(State s : startStates) {

                ImmutableList<TransitionSelectionPath> actionPaths =
                        TransitionPathCollector.builder(am).build(s).getPaths();

                for (TransitionSelectionPath p1 : actionPaths) {
                    for (TransitionSelectionPath p2: actionPaths) {
                        if (!p1.equals(p2)) {
                            try {
                                boolean mutual = TransitionPathCollector.checkMutual(p1, p2, am);
                                if (mutual == false) {
                                    context().getReporter().report(
                                            new Diagnostic(Diagnostic.Kind.WARNING, "Actor machine has a timing dependent behavior", getSource(am), am)
                                    );
                                    Transition t1 = am.getTransitions().get(p1.transition);
                                    Transition t2 = am.getTransitions().get(p2.transition);
                                    context().getReporter().report(
                                            new Diagnostic(Diagnostic.Kind.WARNING, "Timing dependent action", getSource(am), t1)
                                    );
                                    context().getReporter().report(
                                            new Diagnostic(Diagnostic.Kind.WARNING, "Timing dependent action", getSource(am), t2)
                                    );
                                }
                            } catch (CompilationException e) {
                                context().getReporter().report(
                                        new Diagnostic(Diagnostic.Kind.WARNING, "Possible error in action selection logic", getSource(am), am)
                                );
                            }
                        }
                    }
                }
                if (actionPaths.size() > 1) {

                }
            }

        }

        /**
         * Consider a controller with states S and condition values given by a vector C. The controller is not
         * race prone if for any s in S, and truth assignment c1 and c2 of C where c1 and c2 only differ in
         * the truth of input and output conditions, if under c1, s can reach an EXEC instruction without any other
         * WAIT or EXEC, then under c2, it can only reach the same EXEC or a WAIT.
         */


//        default void collectTransitionSelectionPath(State startState, ActorMachine am, )

        default ImmutableList<State> collectStartStates(ActorMachine am) {
            ImmutableList.Builder<State> builder = ImmutableList.builder();
            am.controller().getStateList().forEach(s -> {
                if (s.getInstructions().size() > 1) {
                    context().getReporter().report(
                            new Diagnostic(Diagnostic.Kind.ERROR, "Multiple instruction actor machine",
                                    getSource(am), am)
                    );
                } else {
                    Instruction inst = s.getInstructions().get(0);
                    // the current instruction is an EXEC or WAIT, so the next state is a start state
                    if (inst.getKind() == InstructionKind.EXEC || inst.getKind() == InstructionKind.WAIT) {
                        inst.forEachTarget(builder::add);
                    }
                }
            });

            return builder.build();
        }


        default SourceUnit getSource(IRNode node) {
            return getSource(tree().parent(node));
        }
        default SourceUnit getSource(SourceUnit src) {
            return src;
        }

    }

}
