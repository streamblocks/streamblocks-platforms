package ch.epfl.vlsc.wsim.entity.ir.cpp.expression;

import ch.epfl.vlsc.wsim.entity.ir.cpp.CppNominalTypeExpr;
import ch.epfl.vlsc.wsim.entity.ir.cpp.decl.CppParameterVarDecl;
import se.lth.cs.tycho.ir.IRNode;

import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.util.Lists;


import java.util.List;
import java.util.function.Consumer;

/**
 * A CPP method IRNode
 */
public class ExprCppMethod extends Expression {

    public ExprCppMethod(List<CppParameterVarDecl> valueParams, List<Statement> body,
                         CppNominalTypeExpr returnType) {
        this(null, valueParams, body, returnType);
    }
    public ExprCppMethod(ExprCppMethod original, List<CppParameterVarDecl> valueParameters,
                         List<Statement> body, CppNominalTypeExpr returnType) {
        super(original);
        this.valueParameters = ImmutableList.from(valueParameters);
        this.body = ImmutableList.from(body);
        this.returnType = returnType;
    }

    private final ImmutableList<CppParameterVarDecl> valueParameters;
    private final ImmutableList<Statement> body;
    private final CppNominalTypeExpr returnType;


    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        valueParameters.forEach(action);
        action.accept(returnType);
        body.forEach(action);
    }

    @Override
    public Expression transformChildren(Transformation transformation) {
        return copy(
                transformation.mapChecked(CppParameterVarDecl.class, valueParameters),
                transformation.mapChecked(Statement.class, body),
                transformation.applyChecked(CppNominalTypeExpr.class, returnType)
        );
    }


    private ExprCppMethod copy(List<CppParameterVarDecl> valueParameters, List<Statement> body, CppNominalTypeExpr returnType) {
        if (Lists.sameElements(this.valueParameters, valueParameters) &&
            Lists.sameElements(this.body, body) && this.returnType == returnType) {
            return  this;
        } else {
            return new ExprCppMethod(valueParameters, body, returnType);
        }
    }

    public ImmutableList<Statement> getBody() {
        return body;
    }
    public ImmutableList<CppParameterVarDecl> getValueParameters() {
        return valueParameters;
    }

    public TypeExpr getReturnTypeExpr() {
        return returnType;
    }

    public ExprCppMethod withBody(ImmutableList<Statement> body) {
        return copy(this.valueParameters, body, this.returnType);
    }

    public ExprCppMethod withValueParameters(ImmutableList<CppParameterVarDecl> valueParameters) {
        return copy(valueParameters, this.body, this.returnType);
    }

    public ExprCppMethod withReturnTypeExpr(CppNominalTypeExpr returnType) {
        return copy(this.valueParameters, this.body, returnType);
    }
}
