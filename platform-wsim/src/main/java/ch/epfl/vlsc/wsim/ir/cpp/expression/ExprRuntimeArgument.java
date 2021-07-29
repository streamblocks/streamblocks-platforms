package ch.epfl.vlsc.wsim.ir.cpp.expression;

import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.expr.Expression;

import java.util.function.Consumer;

public class ExprRuntimeArgument extends Expression {

    private ExprRuntimeArgument(Expression original) {
        super(original);
    }

    public ExprRuntimeArgument() {
        super(null);
    }

    public static ExprRuntimeArgument make() {
        return new ExprRuntimeArgument();
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {

    }

    @Override
    public Expression transformChildren(Transformation transformation) {
        return this;
    }
}
