package ch.epfl.vlsc.wsim.ir.cpp.statement;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.stmt.Statement;

import java.util.List;
import java.util.function.Consumer;

public class StmtWarpActorException extends Statement {

    private final String message;

    public String getMessage() {
        return this.message;
    }

    private StmtWarpActorException(Statement original, String message) {
        super(original);
        this.message = message;
    }

    public StmtWarpActorException(String message) {
        this(null, message);
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {}

    @Override
    public StmtWarpActorException withAnnotations(List<Annotation> annotations) {
        return this;
    }

    @Override
    public StmtWarpActorException transformChildren(Transformation transformation) { return this; }

}
