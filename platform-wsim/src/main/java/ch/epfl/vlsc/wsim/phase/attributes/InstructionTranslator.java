package ch.epfl.vlsc.wsim.phase.attributes;

import ch.epfl.vlsc.wsim.ir.cpp.CppNominalTypeExpr;
import ch.epfl.vlsc.wsim.ir.cpp.decl.CppMemberFunctionDecl;
import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprCppMethodCall;
import ch.epfl.vlsc.wsim.ir.cpp.statement.*;
import ch.epfl.vlsc.wsim.ir.cpp.types.NativeTypeCpp;
import ch.epfl.vlsc.wsim.ir.cpp.types.PairCppType;
import ch.epfl.vlsc.wsim.ir.cpp.types.WarpActorScheduleQueryCppType;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.ModuleKey;
import se.lth.cs.tycho.ir.Field;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.LocalVarDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.ctrl.*;
import se.lth.cs.tycho.ir.expr.ExprApplication;
import se.lth.cs.tycho.ir.expr.ExprField;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.stmt.*;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.Map;

/**
 * Translate actor controller instruction to Statements
 */
public interface InstructionTranslator {

    ModuleKey<InstructionTranslator> key = task -> MultiJ.from(Implementation.class).instance();

    /**
     * Translate an instruction in a given state to a list of {@link Statement}s
     * @param actorMachine
     * @param conditions
     * @param actions
     * @param stateNumber
     * @param currentState
     * @return
     */
    ImmutableList<Statement> translate(
            ActorMachine actorMachine,
            ImmutableList<CppMemberFunctionDecl> conditions,
            ImmutableList<CppMemberFunctionDecl> actions,
            Map<State, Integer> stateNumber,
            State currentState, Variable currentLvt, Variable queryObject,
            Variable actionLatencyVariable);
    default String getStateLabel(Integer stateNumber) {
        return "S" + stateNumber;
    }

    default String exitLabel() {
        return "out";
    }

    @Module
    interface Implementation extends InstructionTranslator {

        default ImmutableList<Statement> translate(
                ActorMachine actorMachine,
                ImmutableList<CppMemberFunctionDecl> conditions,
                ImmutableList<CppMemberFunctionDecl> actions,
                Map<State, Integer> stateNumber,
                State currentState, Variable currentLvt, Variable queryObject,
                Variable actionLatencyVariable) {
            return translateInstruction(
                    actorMachine, conditions, actions, stateNumber,
                    currentState, currentLvt, queryObject, actionLatencyVariable,
                    currentState.getInstructions().get(0));
        }


        ImmutableList<Statement> translateInstruction(
                ActorMachine actorMachine,
                ImmutableList<CppMemberFunctionDecl> conditions,
                ImmutableList<CppMemberFunctionDecl> actions,
                Map<State, Integer> stateNumber,
                State currentState, Variable currentLvt, Variable queryObject,
                Variable actionLatencyVariable, Instruction testInstruction);

        default ImmutableList<Statement> translateInstruction(
                ActorMachine actorMachine,
                ImmutableList<CppMemberFunctionDecl> conditions,
                ImmutableList<CppMemberFunctionDecl> actions,
                Map<State, Integer> stateNumber,
                State currentState, Variable currentLvt,
                Variable queryObject, Variable actionLatencyVariable,
                Test testInstruction) {

            int conditionIndex = testInstruction.condition();
            String conditionFunction = conditions.get(conditionIndex).getName();

            boolean unsatisfiedConditionsLeadsToWait =
                    testInstruction.targetFalse().getInstructions().get(0) instanceof Wait;

            ImmutableList<Statement> thenBranch = ImmutableList.of(
                    new StmtGoTo(
                            "S" + stateNumber.get(testInstruction.targetTrue())
                    )
            );
            ImmutableList<Statement> elseBranch =
                    unsatisfiedConditionsLeadsToWait ?
                            ImmutableList.of(
                                    WarpActorScheduleQueryCppType.makeNotifyWaitStmtMethodCall(
                                            new ExprVariable(Variable.variable(queryObject.getName())),
                                            StmtMethodCall.DotAccess,
                                            conditionIndex
                                    ),
                                    new StmtGoTo(
                                            "S" + stateNumber.get(testInstruction.targetFalse())
                                    )
                            ) :
                    ImmutableList.of(
                        new StmtGoTo(
                                "S" + stateNumber.get(testInstruction.targetFalse())
                    )
            );

            if (unsatisfiedConditionsLeadsToWait) {

            }
            if (testInstruction.targetFalse().getInstructions().get(0) instanceof Wait) {

            }
            StmtBlock evaluationBlock =
                    new StmtBlock(
                            ImmutableList.empty(),
                            ImmutableList.of(
                                    new LocalVarDecl(
                                            ImmutableList.empty(),
                                            new CppNominalTypeExpr(
                                                    new PairCppType(NativeTypeCpp.Bool(),
                                                            NativeTypeCpp.VirtualTime())
                                            ),
                                            "__cond_eval__",
                                            new ExprApplication(
                                                new ExprVariable(Variable.variable(conditionFunction)),
                                                    ImmutableList.empty()
                                            ),
                                            true
                                    )
                            ),
                            ImmutableList.of(
                                    new StmtAssignment(
                                          new LValueVariable(Variable.variable(currentLvt.getName())),
                                          new ExprApplication(
                                                  new ExprVariable(
                                                          Variable.variable("::std::max")
                                                  ),
                                                  ImmutableList.of(
                                                          new ExprField(
                                                                  new ExprVariable(
                                                                          Variable.variable("__cond_eval__")
                                                                  ),
                                                                  new Field("second")),
                                                          new ExprVariable(
                                                                  Variable.variable(currentLvt.getName())
                                                          )
                                                  )
                                          )
                                    ),
                                    new StmtIf(
                                          new ExprField(
                                                  new ExprVariable(
                                                          Variable.variable("__cond_eval__")
                                                  ),
                                                  new Field("first")
                                          ),
                                        thenBranch, elseBranch
                                    )
                            )

                    );

            return ImmutableList.of(
                    new StmtLabel(getStateLabel(stateNumber.get(currentState))),
                    evaluationBlock);

        }

        default ImmutableList<Statement> translateInstruction(
                ActorMachine actorMachine,
                ImmutableList<CppMemberFunctionDecl> conditions,
                ImmutableList<CppMemberFunctionDecl> actions,
                Map<State, Integer> stateNumber,
                State currentState, Variable currentLvt, Variable queryObject,
                Variable actionLatencyVariable, Exec execInstruction) {

            int actionIndex = execInstruction.transition();
            String actionFunction = actions.get(actionIndex).getName();
            return ImmutableList.of(
                    new StmtLabel(getStateLabel(stateNumber.get(currentState))),
                    new StmtCall(
                            new ExprVariable(
                                    Variable.variable(actionFunction)
                            ), ImmutableList.empty()
                    ),
                    WarpActorScheduleQueryCppType.makeNotifyActionStmtMethodCall(
                            new ExprVariable(Variable.variable(queryObject.getName())),
                            StmtMethodCall.DotAccess,
                            actionIndex
                    ),
                    new StmtGoTo(
                            "S" + stateNumber.get(execInstruction.target())
                    )
            );
        }

        default ImmutableList<Statement> translateInstruction(
                ActorMachine actorMachine,
                ImmutableList<CppMemberFunctionDecl> conditions,
                ImmutableList<CppMemberFunctionDecl> actions,
                Map<State, Integer> stateNumber,
                State currentState, Variable currentLvt, Variable queryObject,
                Variable actionLatencyVariable,
                Wait waitInstruction) {
            return ImmutableList.of(
                    new StmtLabel(getStateLabel(stateNumber.get(currentState))),
                    new StmtGoTo(exitLabel())
            );
        }
    }
}
