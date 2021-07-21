package ch.epfl.vlsc.wsim.ir.cpp.statement;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.stmt.Statement;

import java.util.List;
import java.util.function.Consumer;

public class StmtLabel extends Statement {

    private final String label;

    private StmtLabel(StmtLabel original, String label){
        super(original);
        this.label = label;
    }

    public StmtLabel(String label) {
        this(null, label);
    }


    @Override
    public void forEachChild(Consumer<? super IRNode> action) {}

    @Override
    public StmtLabel withAnnotations(List<Annotation> annotations) {
        return this;
    }

    @Override
    public StmtLabel transformChildren(Transformation transformation) { return this; }
}
