package ch.epfl.vlsc.wsim.ir.cpp.expression;

import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.util.Lists;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Class representing a method call expression such as
 * obj.fun(arg1, arg2,...)
 * objPtr->fun(arg1, arg2,...)
 */
public class ExprCppMethodCall extends Expression {
    public static boolean ArrowAccess = true;
    public static boolean DotAccess = false;
    private final Expression object;
    private final boolean byDereference; // is it a dereference access, i.e., by -> or normal access, i.e., by "dot"
    private final Expression function;
    private final ImmutableList<Expression> args;

    public ExprCppMethodCall(Expression original, Expression object, boolean byDereference,
                         Expression function, ImmutableList<Expression> args) {
        super(original);
        this.object = object;
        this.byDereference = byDereference;
        this.function = function;
        this.args = args;
    }

    public ExprCppMethodCall(Expression object, boolean byDereference,
                         Expression function, ImmutableList<Expression> args) {
        this(null, object, byDereference, function, args);
    }


    public Expression getObject() { return object; }
    public Expression getFunction() {return  function; }
    public ImmutableList<Expression> getArgs() { return args; }
    public boolean isByDereference() { return this.byDereference; }

    private ExprCppMethodCall copy(Expression object, boolean byDereference,
                                   Expression function, ImmutableList<Expression> args){
        if (Objects.equals(this.object, object) &&
        Objects.equals(this.function, function) && this.byDereference == byDereference &&
                Lists.sameElements(args, this.args)) {
            return this;
        } else {
            return new ExprCppMethodCall(this, object, byDereference, function, args);
        }
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(object);
        action.accept(function);
        args.forEach(action);
    }
    @Override
    public ExprCppMethodCall transformChildren(Transformation transformation) {
        return copy(
                transformation.applyChecked(Expression.class, object),
                byDereference,
                transformation.applyChecked(Expression.class, function),
                ImmutableList.from(transformation.mapChecked(Expression.class, args))
        );
    }
}
