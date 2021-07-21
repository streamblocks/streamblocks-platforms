package ch.epfl.vlsc.wsim.ir.cpp.statement;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.stmt.Statement;

import java.util.List;
import java.util.function.Consumer;

public class StmtVarDecl extends Statement {

    private final VarDecl declaration;

    private StmtVarDecl(Statement original, VarDecl declaration) {
        super(original);
        this.declaration = declaration;
    }

    public StmtVarDecl(VarDecl declaration) {
        this(null, declaration);
    }

    public VarDecl getDeclaration() {
        return declaration;
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(declaration);
    }

    private StmtVarDecl copy(VarDecl declaration) {
        if (this.declaration == declaration) {
            return this;
        } else {
            return new StmtVarDecl(this, declaration);
        }
    }
    @Override
    public StmtVarDecl withAnnotations(List<Annotation> annotations) {
        return this;
    }

    @Override
    public StmtVarDecl transformChildren(Transformation transformation) {
        return copy(
            transformation.applyChecked(VarDecl.class, this.declaration)
        );
    }

}
