package ch.epfl.vlsc.wsim.entity.ir.cpp.decl;

import ch.epfl.vlsc.wsim.entity.ir.cpp.CppFunctionTypeExpr;
import ch.epfl.vlsc.wsim.entity.ir.cpp.CppNominalTypeExpr;
import ch.epfl.vlsc.wsim.entity.ir.cpp.expression.ExprCppMethod;
import ch.epfl.vlsc.wsim.entity.ir.cpp.expression.ExprCppMethodCall;

import ch.epfl.vlsc.wsim.entity.ir.cpp.statement.StmtReturn;
import ch.epfl.vlsc.wsim.entity.ir.cpp.types.*;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.Variable;
import java.util.Optional;

public class CppWarpActorTypeDecl extends CppClassTypeDecl{

    public static class WarpActorUtils {
        public static CppMemberFunctionDecl numIOFunctionDecl(int numIO, boolean isInput) {
            String name = isInput ? "getNumInputs" : "getNumOutputs";
            return new CppMemberFunctionDecl(
                    name,
                    new CppFunctionTypeExpr(
                            ImmutableList.empty(),
                            new CppNominalTypeExpr(NativeTypeCpp.Int32(false))
                    ),
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
        public static ExprCppMethodCall getVirtualTimeExpression() {
            return new ExprCppMethodCall(
                    new ExprVariable(
                            Variable.variable("this")
                    ), true,
                    new ExprVariable(
                            Variable.variable("getVirtualTime")
                    ),
                    ImmutableList.of(
                            new ExprVariable(Variable.variable("::std::memory_order_relaxed"))
                    )
            );
        }
        public static ExprCppMethodCall setVirtualTimeExpression(Variable var) {
            return new ExprCppMethodCall(
                    new ExprVariable(
                            Variable.variable("this")
                    ), true,
                    new ExprVariable(
                            Variable.variable("setVirtualTime")
                    ),
                    ImmutableList.of(
                            new ExprVariable(var),
                            new ExprVariable(Variable.variable("::std::memory_order_relaxed"))
                    )
            );
        }
    }

//    private final ImmutableList<CppMemberFunctionDecl> memberFunctions;
//    private final ImmutableList<CppMemberFunctionDecl> conditionMemberFunctions;
//    private final ImmutableList<CppMemberFunctionDecl> actionMemberFunctions;
//    private final ImmutableList<CppMemberVarDecl> memberVarDecls;
    public CppWarpActorTypeDecl(CppClassTypeDecl original, String name,
                                ImmutableList<CppMemberVarDecl> memberVarDecls,
                                CppMemberFunctionDecl schedulerMethod,
                                ImmutableList<CppMemberFunctionDecl> memberFunctionDecls,
                                ImmutableList<CppParameterVarDecl> constructorParams,
                                ImmutableList<CppMemberFunctionDecl> conditions,
                                ImmutableList<CppMemberFunctionDecl> actions,
                                ImmutableList<PortCppType> inputPorts,
                                ImmutableList<PortCppType> outputPorts) {

        super(original, name,
                Optional.of(new WarpActorBaseCppType()),
                memberVarDecls,
                ImmutableList.<CppMemberFunctionDecl>builder()
                        .add(WarpActorUtils.numIOFunctionDecl(inputPorts.size(), true))
                        .add(WarpActorUtils.numIOFunctionDecl(outputPorts.size(), false))
                        .add(schedulerMethod)
                        .addAll(memberFunctionDecls)
                        .build(),
                ImmutableList.<CppParameterVarDecl>builder()
                .add(new CppParameterVarDecl(
                        new CppNominalTypeExpr(new StringCppType()),
                        "instanceName", true, true))
                .addAll(
                        constructorParams)
                .add(
                        new CppParameterVarDecl(
                                new CppNominalTypeExpr(new WarpAttributeListCppType()),
                                "attrs", true, true
                        )
                ).build());
//        this.conditionMemberFunctions = conditions;
//        this.actionMemberFunctions = actions;
    }
//    public CppWarpActorTypeDecl(String name,
//                                ImmutableList<CppMemberVarDecl> memberVarDecls,
//                                ImmutableList<CppMemberFunctionDecl> memberFunctionDecls,
//                                ImmutableList<CppParameterVarDecl> constructorParams){
////        this(null, name, memberVarDecls, memberFunctionDecls, constructorParams);
//    }
}
