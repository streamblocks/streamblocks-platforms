package ch.epfl.vlsc.wsim.ir.cpp.types;

import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprCppMethodCall;
import ch.epfl.vlsc.wsim.ir.cpp.statement.StmtMethodCall;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.function.Consumer;

public class WarpActorScheduleQueryCppType extends LibraryObjectCppType{


    public WarpActorScheduleQueryCppType(WarpActorScheduleQueryCppType original, boolean isPointer) {
        super(original, isPointer);
    }

    public WarpActorScheduleQueryCppType() {
        this(null, false);
    }

    public WarpActorScheduleQueryCppType(boolean isPointer) {
        this(null, isPointer);
    }

    @Override
    public String toString() {
        return "::wsim::ActorScheduleQuery";
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {

    }

    @Override
    public WarpActorScheduleQueryCppType transformChildren(Transformation transformation) {
        return this;
    }


    /**
     * Create a method call expression
     * @param queryObject the query object, e.g., an {@link ExprVariable} where the variable is declared as
     *                    {@link WarpActorScheduleQueryCppType} type
     * @param byDeref     is the access by dereference operator (i.e., ->) or not (i.e., by .)
     * @param actionIndex the action id
     * @return
     */
    public static StmtMethodCall makeNotifyActionStmtMethodCall(Expression queryObject,
                                                                   boolean byDeref, int actionIndex) {
        return new StmtMethodCall(
                queryObject.deepClone(),
                byDeref,
                new ExprVariable(
                        Variable.variable("notifyAction")),
                ImmutableList.of(new ExprLiteral(ExprLiteral.Kind.Integer, String.valueOf(actionIndex)))
                );
    }

    /**
     * Same as makeNotifyActionExprMethodCall but creates a method call to notifyWait
     * @param queryObject
     * @param byDeref
     * @param conditionIndex
     * @return
     */
    public static StmtMethodCall makeNotifyWaitStmtMethodCall(Expression queryObject,
                                                              boolean byDeref, int conditionIndex) {
        return new StmtMethodCall(
                queryObject.deepClone(),
                byDeref,
                new ExprVariable(
                        Variable.variable("notifyWait")),
                ImmutableList.of(new ExprLiteral(ExprLiteral.Kind.Integer,
                        String.valueOf(conditionIndex))));

    }

}
