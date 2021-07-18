package ch.epfl.vlsc.wsim.entity.ir.cpp.decl;

import ch.epfl.vlsc.wsim.entity.ir.cpp.CppNominalTypeExpr;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.Objects;
import java.util.function.Consumer;

public class CppParameterVarDecl extends VarDecl {

    private final boolean isReference;


    public CppParameterVarDecl(VarDecl original, CppNominalTypeExpr type, String name, boolean isConst, boolean isReference) {
        super(original, ImmutableList.empty(), type, name, null, isConst, false );
        this.isReference = isReference;
    }

    public CppParameterVarDecl(CppNominalTypeExpr type, String name, boolean isConst, boolean isReference) {
        this(null, type, name, isConst, isReference);
    }

    private CppParameterVarDecl copy(CppNominalTypeExpr type, String name, boolean isConst, boolean isReference) {
        if (Objects.equals(this.getName(), name) &&
                (this.getType() == type) &&
                (isConst == this.isConstant()) &&
                (isReference == this.isReference()))
            return this;
        else
            return new CppParameterVarDecl(type, name, isConst, isReference);
    }

    public boolean isReference() {
        return isReference;
    }


    public CppParameterVarDecl withType(CppNominalTypeExpr type) {
        return copy(type, this.getName(), this.isConstant(), this.isReference());
    }
    public CppParameterVarDecl withReference(boolean isReference) {
        return copy(this.getType(), this.getName(), this.isConstant(), isReference);
    }

    public CppParameterVarDecl withConstant(boolean isConstant) {
        return copy(this.getType(), this.getName(), isConstant, this.isReference());
    }

    @Override
    public VarDecl withName(String name) {
        return copy(this.getType(), name, this.isConstant(), this.isReference());
    }

    @Override
    public CppNominalTypeExpr getType() {
        return (CppNominalTypeExpr) super.getType();
    }


    @Override
    public CppParameterVarDecl transformChildren(Transformation transformation) {

        return copy(
                transformation.applyChecked(CppNominalTypeExpr.class, this.getType()),
                this.getName(), this.isConstant(), this.isReference()
        );
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(this.getType());
    }

}
