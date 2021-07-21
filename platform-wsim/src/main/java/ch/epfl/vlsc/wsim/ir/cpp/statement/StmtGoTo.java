package ch.epfl.vlsc.wsim.ir.cpp.statement;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.stmt.Statement;

import java.util.List;
import java.util.function.Consumer;

public class StmtGoTo extends Statement {

    private final String label;

    public String getLabel() {
        return label;
    }

    public StmtGoTo(Statement original, String label) {
        super(original);
        this.label = label;
    }

    public StmtGoTo(String label) {
        this(null, label);
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {}

    @Override
    public StmtGoTo withAnnotations(List<Annotation> annotations) {
        return this;
    }

    @Override
    public StmtGoTo transformChildren(Transformation transformation) { return this; }

}
