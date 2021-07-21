package ch.epfl.vlsc.wsim.ir.cpp.expression;


import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.expr.Expression;

import java.util.Objects;
import java.util.function.Consumer;

public class ExprCppLabel extends Expression {

    private final String label;

    public ExprCppLabel(Expression original, String label) {
        super(original);
        this.label = label;
    }

    public ExprCppLabel(String label) {
        this(null, label);
    }

    public String getLabel() {
        return label;
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {

    }

    @Override
    public ExprCppLabel transformChildren(Transformation transformation) {
        return this;
    }

    public ExprCppLabel copy(String label) {
        if (Objects.equals(this.label, label)) {
            return this;
        } else {
            return new ExprCppLabel(this, label);
        }
    }

    public ExprCppLabel withLabel(String label) {
        return copy(label);
    }
}
