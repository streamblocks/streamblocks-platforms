package ch.epfl.vlsc.wsim.ir.cpp.decl;

import ch.epfl.vlsc.wsim.ir.cpp.CppFunctionTypeExpr;
import ch.epfl.vlsc.wsim.ir.cpp.CppNominalTypeExpr;
import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprCppMethod;
import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprCppMethodCall;

import ch.epfl.vlsc.wsim.ir.cpp.statement.StmtReturn;
import ch.epfl.vlsc.wsim.ir.cpp.types.*;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.Variable;
import java.util.Optional;

public class CppWarpActorTypeDecl extends CppClassTypeDecl{

    /**
     * Create C++ class declaration and set the positions according to the original actor machine
     * @param original
     * @param name name of the actor
     * @param memberVarDecls member variable, e.g, actor variables from scopes
     * @param schedulerMethod scheduler method
     * @param memberFunctionDecls procedures and lambdas
     * @param constructorParams parameters for the constructor
     * @param conditions function describing conditions
     * @param actions functions describing actions
     * @param inputPorts input port variable declaration
     * @param outputPorts input port variable declaration
     * @return and c++ for the ActorMachine
     */
    public static CppWarpActorTypeDecl create(ActorMachine original, String name,
                                       ImmutableList<CppMemberVarDecl> memberVarDecls,
                                       CppMemberFunctionDecl schedulerMethod,
                                       ImmutableList<CppMemberFunctionDecl> memberFunctionDecls,
                                       ImmutableList<CppParameterVarDecl> constructorParams,
                                       ImmutableList<CppMemberFunctionDecl> conditions,
                                       ImmutableList<CppMemberFunctionDecl> actions,
                                       ImmutableList<CppMemberVarDecl> inputPorts,
                                       ImmutableList<CppMemberVarDecl> outputPorts) {
        CppWarpActorTypeDecl cls = new CppWarpActorTypeDecl(name, memberVarDecls, schedulerMethod,
                memberFunctionDecls, constructorParams, conditions,
                actions, inputPorts, outputPorts);
        cls.setPosition(original);
        return cls;
    }



//    private final ImmutableList<CppMemberFunctionDecl> memberFunctions;
    private final ImmutableList<CppMemberFunctionDecl> actorMethods;
    private final ImmutableList<CppMemberFunctionDecl> conditionMemberFunctions;
    private final ImmutableList<CppMemberFunctionDecl> actionMemberFunctions;
    private final ImmutableList<CppMemberVarDecl> inputPorts;
    private final ImmutableList<CppMemberVarDecl> outputPorts;
//    private final ImmutableList<CppMemberVarDecl> memberVarDecls;

    /**
     *
     * @param original
     * @param name name of the actor
     * @param memberVarDecls member variable, e.g, actor variables from scopes
     * @param schedulerMethod scheduler method
     * @param memberFunctionDecls procedures and lambdas
     * @param constructorParams parameters for the constructor
     * @param conditions function describing conditions
     * @param actions functions describing actions
     * @param inputPorts input port variable declaration
     * @param outputPorts input port variable declaration
     */
    public CppWarpActorTypeDecl(CppClassTypeDecl original, String name,
                                ImmutableList<CppMemberVarDecl> memberVarDecls,
                                CppMemberFunctionDecl schedulerMethod,
                                ImmutableList<CppMemberFunctionDecl> memberFunctionDecls,
                                ImmutableList<CppParameterVarDecl> constructorParams,
                                ImmutableList<CppMemberFunctionDecl> conditions,
                                ImmutableList<CppMemberFunctionDecl> actions,
                                ImmutableList<CppMemberVarDecl> inputPorts,
                                ImmutableList<CppMemberVarDecl> outputPorts) {

        super(original, name,
                Optional.of(new WarpActorBaseCppType()),
                memberVarDecls,
                ImmutableList.<CppMemberFunctionDecl>builder()
                        .add(WarpActorBaseCppType.numIOFunctionDecl(inputPorts.size(), true))
                        .add(WarpActorBaseCppType.numIOFunctionDecl(outputPorts.size(), false))
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
        this.actorMethods = memberFunctionDecls;
        this.conditionMemberFunctions = conditions;
        this.actionMemberFunctions = actions;
        this.inputPorts = inputPorts;
        this.outputPorts = outputPorts;
    }

    public CppWarpActorTypeDecl(String name, ImmutableList<CppMemberVarDecl> memberVarDecls,
                                CppMemberFunctionDecl schedulerMethod,
                                ImmutableList<CppMemberFunctionDecl> memberFunctionDecls,
                                ImmutableList<CppParameterVarDecl> constructorParams,
                                ImmutableList<CppMemberFunctionDecl> conditions,
                                ImmutableList<CppMemberFunctionDecl> actions,
                                ImmutableList<CppMemberVarDecl> inputPorts,
                                ImmutableList<CppMemberVarDecl> outputPorts){
        this(null, name, memberVarDecls, schedulerMethod, memberFunctionDecls,
                constructorParams, conditions, actions, inputPorts, outputPorts);

    }
}
