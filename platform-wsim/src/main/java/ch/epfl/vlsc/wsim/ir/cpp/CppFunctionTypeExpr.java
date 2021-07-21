package ch.epfl.vlsc.wsim.ir.cpp;

import se.lth.cs.tycho.ir.AbstractIRNode;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.util.Lists;

import java.util.function.Consumer;

public class CppFunctionTypeExpr extends AbstractIRNode implements TypeExpr<CppFunctionTypeExpr> {

    private final ImmutableList<CppNominalTypeExpr> parameterTypes;
    private final CppNominalTypeExpr returnType;

    public CppFunctionTypeExpr(CppFunctionTypeExpr original, ImmutableList<CppNominalTypeExpr> parameterTypes,
                               CppNominalTypeExpr returnType) {
        super(original);
        this.parameterTypes = ImmutableList.from(parameterTypes);
        this.returnType = returnType;
    }

    public CppFunctionTypeExpr(ImmutableList<CppNominalTypeExpr> parameterTypes, CppNominalTypeExpr returnType) {
        this(null, parameterTypes, returnType);
    }

    public ImmutableList<CppNominalTypeExpr> getParameterTypes() {
        return parameterTypes;
    }

    public CppNominalTypeExpr getReturnType() {
        return returnType;
    }


    private CppFunctionTypeExpr copy(ImmutableList<CppNominalTypeExpr> parameterTypes, CppNominalTypeExpr returnType) {
        if (Lists.sameElements(parameterTypes, this.getParameterTypes()) &&
                this.getReturnType() == returnType)
            return this;
        else
            return new CppFunctionTypeExpr(parameterTypes, returnType);
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        parameterTypes.forEach(action);
        action.accept(returnType);
    }


    @Override
    public CppFunctionTypeExpr transformChildren(Transformation transformation) {
        return copy(
                ImmutableList.from(
                        transformation.mapChecked(CppNominalTypeExpr.class, this.parameterTypes)),
                transformation.applyChecked(CppNominalTypeExpr.class, this.returnType)
        );
    }

}
