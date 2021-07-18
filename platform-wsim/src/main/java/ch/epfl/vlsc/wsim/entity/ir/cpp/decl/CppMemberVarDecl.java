package ch.epfl.vlsc.wsim.entity.ir.cpp.decl;

import ch.epfl.vlsc.wsim.entity.ir.cpp.CppNominalTypeExpr;
import com.sun.tools.javac.util.List;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.Objects;
import java.util.function.Consumer;

public class CppMemberVarDecl extends CppMemberDecl {


    public CppMemberVarDecl(CppNominalTypeExpr type, String name, boolean constant, boolean isPrivate) {
        this(null, type, name, constant, isPrivate);
    }
    public CppMemberVarDecl(CppMemberVarDecl original, CppNominalTypeExpr type,
                            String name, boolean constant, boolean isPrivate) {
        super(original, ImmutableList.empty(), type, name, null, constant, false);

    }

    private CppMemberVarDecl copy(CppNominalTypeExpr type, String name, boolean constant, boolean isPrivate) {
        if (Objects.equals(name, this.getName()) &&
                this.getType() == type && this.isConstant() == constant &&
                this.isPrivate() == isPrivate)
            return this;
        else
            return new CppMemberVarDecl(type, name, constant, isPrivate);
    }



    @Override
    public CppNominalTypeExpr getType() {
        return (CppNominalTypeExpr) super.getType();
    }


    @Override
    public VarDecl withName(String name) {
        return copy(this.getType(), name, this.isConstant(), this.isPrivate());
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(this.getType());
    }

    @Override
    public VarDecl transformChildren(Transformation transformation) {
        return copy(
                transformation.applyChecked(CppNominalTypeExpr.class, this.getType()),
                this.getName(),
                this.isConstant(),
                this.isPrivate()
        );
    }
}
