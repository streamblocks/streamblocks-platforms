package ch.epfl.vlsc.wsim.entity.ir.cpp.statement;

import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.Statement;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class StmtReturn extends Statement {

    private final Expression returnExpression;

    public StmtReturn(StmtReturn original, Expression returnExpression) {
        super(original);
        this.returnExpression = returnExpression;
    }

    public StmtReturn(Expression returnExpression) {
        this(null, returnExpression);
    }

    public Expression getReturnExpression() {
        return returnExpression;
    }

    private StmtReturn copy(Expression returnExpression) {
        if (Objects.equals(returnExpression, this.getReturnExpression())) {
            return this;
        } else {
            return new StmtReturn(this, returnExpression);
        }
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(this.getReturnExpression());
    }

    @Override
    public StmtReturn transformChildren(IRNode.Transformation transformation) {
        return copy(
                transformation.applyChecked(Expression.class, this.getReturnExpression())
        );
    }

    @Override
    public Statement withAnnotations(List<Annotation> annotations) {
        return this;
    }
}
