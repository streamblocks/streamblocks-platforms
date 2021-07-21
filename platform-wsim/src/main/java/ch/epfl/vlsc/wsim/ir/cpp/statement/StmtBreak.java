package ch.epfl.vlsc.wsim.ir.cpp.statement;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.stmt.Statement;

import java.util.List;
import java.util.function.Consumer;

public class StmtBreak extends Statement {

    public StmtBreak(StmtBreak original) {
        super(original);
    }
    public StmtBreak() {
        this(null);
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {}

    @Override
    public StmtBreak transformChildren(IRNode.Transformation transformation) {
        return this;
    }

    @Override
    public Statement withAnnotations(List<Annotation> annotations) {
        return this;
    }

}
