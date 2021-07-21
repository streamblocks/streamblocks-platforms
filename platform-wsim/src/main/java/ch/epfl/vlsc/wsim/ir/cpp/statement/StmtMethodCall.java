package ch.epfl.vlsc.wsim.ir.cpp.statement;

import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprCppMethodCall;
import se.lth.cs.tycho.ir.Annotation;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.util.Lists;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;


public class StmtMethodCall extends Statement {


    public static boolean ArrowAccess = ExprCppMethodCall.ArrowAccess;
    public static boolean DotAccess = ExprCppMethodCall.DotAccess;

    private final ExprCppMethodCall callExpression;

    private StmtMethodCall(Statement original, Expression object, boolean byDereference,
                           Expression function, ImmutableList<Expression> args) {
        super(original);
        this.callExpression = new ExprCppMethodCall(object, byDereference, function, args);
    }

    public StmtMethodCall(Expression object, boolean byDereference,
                          Expression function, ImmutableList<Expression> args) {
        this(null, object, byDereference, function, args);
    }

    public Expression getObject() {
        return callExpression.getObject();
    }
    public boolean isByDereference() {
        return  callExpression.isByDereference();
    }
    public Expression getFunction() {
        return callExpression.getFunction();
    }
    public ImmutableList<Expression> getArgs() {
        return callExpression.getArgs();
    }



    private StmtMethodCall copy(Expression object, boolean byDereference,
                                Expression function, ImmutableList<Expression> args) {
        if (Objects.equals(this.getObject(), object) && this.isByDereference() == byDereference &&
                Objects.equals(this.getFunction(), function) && Lists.sameElements(this.getArgs(), args)) {

            return  this;
        }  else {
            return new StmtMethodCall(this, object, byDereference, function, args);
        }
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(this.callExpression);
    }

    @Override
    public StmtMethodCall transformChildren(Transformation transformation) {
        ExprCppMethodCall transformed = transformation.applyChecked(ExprCppMethodCall.class, callExpression);
        return copy(
                transformed.getObject(), transformed.isByDereference(), transformed.getFunction(),
                transformed.getArgs()
        );
    }

    @Override
    public Statement withAnnotations(List<Annotation> annotations) {
        return this;
    }

}
