package ch.epfl.vlsc.wsim.entity.ir.cpp.decl;

import ch.epfl.vlsc.wsim.entity.ir.cpp.CppFunctionTypeExpr;

import ch.epfl.vlsc.wsim.entity.ir.cpp.expression.ExprCppMethod;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.util.ImmutableList;

import java.util.Objects;

import java.util.function.Consumer;

public class CppMemberFunctionDecl extends CppMemberDecl {

    public CppMemberFunctionDecl(CppMemberFunctionDecl original, String name, CppFunctionTypeExpr type,
                                 ExprCppMethod definition,
                                 boolean constant, boolean isPrivate) {
        super(original, ImmutableList.empty(), type, name, definition, constant, isPrivate);
    }

    public CppMemberFunctionDecl(String name, CppFunctionTypeExpr type, ExprCppMethod definition,
                                 boolean constant, boolean isPrivate) {
        this(null, name, type, definition, constant, isPrivate);
    }


    private CppMemberFunctionDecl copy(String name, CppFunctionTypeExpr type,
                                  ExprCppMethod definition, boolean constant,
                                  boolean isPrivate) {
        if (Objects.equals(name, this.getName()) &&
                this.getType() == type && this.getValue() == definition &&
                this.isConstant() == constant && this.isPrivate() == isPrivate)
            return this;
        else
            return new CppMemberFunctionDecl(name, type, definition, constant, isPrivate);

    }

    public CppFunctionTypeExpr getType() {
        return (CppFunctionTypeExpr) super.getType();
    }

    public ExprCppMethod getValue() {
        return (ExprCppMethod) super.getValue();
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(this.getValue());
        action.accept(this.getType());

    }

    @Override
    public VarDecl transformChildren(Transformation transformation) {
        return copy(
                this.getName(),
                transformation.applyChecked(CppFunctionTypeExpr.class, this.getType()),
                transformation.applyChecked(ExprCppMethod.class, this.getValue()),
                this.isConstant(),
                this.isPrivate()
        );
    }

    @Override
    public CppMemberFunctionDecl withName(String name) {
        return copy(name, this.getType(), this.getValue(), this.isConstant(), this.isExternal());
    }
}
