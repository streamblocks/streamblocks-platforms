package ch.epfl.vlsc.wsim.ir.cpp.decl;

import ch.epfl.vlsc.wsim.ir.cpp.CppFunctionTypeExpr;

import ch.epfl.vlsc.wsim.ir.cpp.CppNominalTypeExpr;
import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprCppMethod;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.Objects;

import java.util.function.Consumer;

public class CppMemberFunctionDecl extends CppMemberDecl {

    public CppMemberFunctionDecl(CppMemberFunctionDecl original, String name,
                                 ExprCppMethod definition,
                                 boolean constant, boolean isPrivate) {
        super(original, ImmutableList.empty(), null, name, definition, constant, isPrivate);
    }

    public CppMemberFunctionDecl(String name, ExprCppMethod definition,
                                 boolean constant, boolean isPrivate) {
        this(null, name, definition, constant, isPrivate);
    }


    private CppMemberFunctionDecl copy(String name,
                                  ExprCppMethod definition, boolean constant,
                                  boolean isPrivate) {
        if (Objects.equals(name, this.getName()) &&
                this.getValue() == definition &&
                this.isConstant() == constant && this.isPrivate() == isPrivate)
            return this;
        else
            return new CppMemberFunctionDecl(name, definition, constant, isPrivate);

    }

//    public CppFunctionTypeExpr getType() {
//        return (CppFunctionTypeExpr) super.getType();
//    }

    public ExprCppMethod getValue() {
        return (ExprCppMethod) super.getValue();
    }

    public ExprCppMethod getDefinition() { return getValue(); }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(this.getValue());
        action.accept(this.getType());

    }

    @Override
    public VarDecl transformChildren(Transformation transformation) {
        return copy(
                this.getName(),
                transformation.applyChecked(ExprCppMethod.class, this.getValue()),
                this.isConstant(),
                this.isPrivate()
        );
    }

    @Override
    public CppMemberFunctionDecl withName(String name) {
        return copy(name, this.getValue(), this.isConstant(), this.isPrivate());
    }

    @Override
    public CppMemberFunctionDecl withType(TypeExpr type) {
        throw new CompilationException(
                new Diagnostic(Diagnostic.Kind.ERROR,
                        "Can not construct a CppMemberFunctionDecl with a type, construct with value instead"));
//        return this;
    }
}
