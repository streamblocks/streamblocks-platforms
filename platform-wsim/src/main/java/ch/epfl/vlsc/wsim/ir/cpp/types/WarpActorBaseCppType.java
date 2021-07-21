package ch.epfl.vlsc.wsim.ir.cpp.types;

import ch.epfl.vlsc.wsim.ir.cpp.CppNominalTypeExpr;
import ch.epfl.vlsc.wsim.ir.cpp.decl.CppMemberFunctionDecl;
import ch.epfl.vlsc.wsim.ir.cpp.decl.CppParameterVarDecl;
import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprCppMethod;
import ch.epfl.vlsc.wsim.ir.cpp.statement.StmtMethodCall;
import ch.epfl.vlsc.wsim.ir.cpp.statement.StmtReturn;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.expr.ExprApplication;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtCall;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.function.Consumer;

public class WarpActorBaseCppType extends LibraryObjectCppType {

    public WarpActorBaseCppType(WarpActorBaseCppType original, boolean isPointer) {
        super(original, isPointer);
    }
    public WarpActorBaseCppType(boolean isPointer) {
        this(null, isPointer);
    }
    public WarpActorBaseCppType() {
        this(null, false);
    }
    @Override
    public String toString() {
        return "::wsim::ActorBase";
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that == null) {
            return false;
        } else if (!(that instanceof WarpActorBaseCppType)) {
            return false;
        } else {
            WarpActorBaseCppType thatCasted = (WarpActorBaseCppType) that;
            return this.isPointer() == thatCasted.isPointer();
        }
    }

    @Override
    public void forEachChild(Consumer<? super  IRNode> action) {

    }
    @Override
    public WarpActorBaseCppType transformChildren(Transformation transformation) {
        return this;
    }


    /**
     * Create {@link ExprApplication} to the getVirtualTime method, should be used
     * to create a call statement inside the Actor methods, e.g., the scheduler
     * @return
     */
    public static ExprApplication makeGetVirtualTimeStmtCall() {
        return new ExprApplication(
                new ExprVariable(Variable.variable("getVirtualTime")),
                ImmutableList.empty()
        );
    }

    /**
     * Like the makeGetVirtualTimeStmtCall, but creates a call to setVirtualMethod
     * and return a {@link StmtCall} because the setter does not return anything.
     * @param value
     * @return
     */
    public static StmtCall makeSetVirtualTimeStmtCall(Variable value) {
        return  new StmtCall(
                new ExprVariable(Variable.variable("setVirtualTime")),
                ImmutableList.of(
                        new ExprVariable(Variable.variable(value.getName()))
                )
        );
    }


    public static CppMemberFunctionDecl numIOFunctionDecl(int numIO, boolean isInput) {
        String name = isInput ? "getNumInputs" : "getNumOutputs";
        return new CppMemberFunctionDecl(
                name,
                new ExprCppMethod(
                        ImmutableList.empty(),
                        ImmutableList.of(
                                new StmtReturn(
                                        new ExprLiteral(
                                                ExprLiteral.Kind.Integer,
                                                String.valueOf(numIO))
                                )
                        ),
                        new CppNominalTypeExpr(NativeTypeCpp.Int32(false))
                ),
                true, false
        );
    }

    /**
     * Create scheduler method declaration
     * @param definition
     * @return
     */
    public static CppMemberFunctionDecl schedulerDecl(ImmutableList<Statement> definition,
                                                      Variable queryVariable) {
        return new CppMemberFunctionDecl(
                "scheduler",
                new ExprCppMethod(
                        ImmutableList.of(
                                new CppParameterVarDecl(
                                        new CppNominalTypeExpr(
                                            new WarpActorScheduleQueryCppType()),
                                        queryVariable.getName(),
                                        false,
                                        CppParameterVarDecl.PASS_BY_REFERENCE)
                        ),
                        definition,
                        new CppNominalTypeExpr(
                                NativeTypeCpp.Void()
                        )
                ),
                false, false
        );
    }
}
