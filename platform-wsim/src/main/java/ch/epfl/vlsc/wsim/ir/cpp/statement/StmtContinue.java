package ch.epfl.vlsc.wsim.ir.cpp.statement;


import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.stmt.Statement;

import java.util.List;
import java.util.function.Consumer;

public class StmtContinue extends Statement {

    public StmtContinue(StmtBreak original) {
        super(original);
    }
    public StmtContinue() {
        this(null);
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {}

    @Override
    public StmtContinue transformChildren(IRNode.Transformation transformation) {
        return this;
    }

    @Override
    public StmtContinue withAnnotations(List<Annotation> annotations) {
        return this;
    }
}
