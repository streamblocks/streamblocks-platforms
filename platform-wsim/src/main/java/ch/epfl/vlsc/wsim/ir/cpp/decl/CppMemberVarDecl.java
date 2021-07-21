package ch.epfl.vlsc.wsim.ir.cpp.decl;

import ch.epfl.vlsc.wsim.ir.cpp.CppNominalTypeExpr;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.Objects;
import java.util.function.Consumer;

public class CppMemberVarDecl extends CppMemberDecl {


    public static CppMemberVarDecl privateMemberVarDecl(CppNominalTypeExpr type, String name) {
        return new CppMemberVarDecl(type, name, false, true);
    }
    public static CppMemberVarDecl publicMemberVarDecl(CppNominalTypeExpr type, String name) {
        return new CppMemberVarDecl(type, name, false, false);
    }
    public CppMemberVarDecl(CppNominalTypeExpr type, String name, boolean constant, boolean isPrivate) {
        this(null, type, name, constant, isPrivate);
    }
    public CppMemberVarDecl(VarDecl original, CppNominalTypeExpr type,
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
    public CppMemberVarDecl withName(String name) {
        return copy(this.getType(), name, this.isConstant(), this.isPrivate());
    }

    @Override
    public CppMemberVarDecl withType(TypeExpr type) {
        return copy((CppNominalTypeExpr) type, getName(), isConstant(), isPrivate());
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
