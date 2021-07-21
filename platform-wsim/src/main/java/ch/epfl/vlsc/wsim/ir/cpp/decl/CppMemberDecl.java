package ch.epfl.vlsc.wsim.ir.cpp.decl;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.type.TypeExpr;

import java.util.List;

public abstract class CppMemberDecl extends VarDecl {

    private final boolean isPrivate;
    protected CppMemberDecl(VarDecl original, List<Annotation> annotations, TypeExpr type,
                            String name, Expression value, boolean constant, boolean isPrivate) {
        super(original, annotations, type, name, value, constant, false);
        this.isPrivate = isPrivate;
    }

    public boolean isPrivate() {
        return isPrivate;
    }
}
