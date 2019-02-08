package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.expr.*;

@Module
public interface ExpressionEvaluator {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default Variables variables() {
        return backend().variables();
    }

    default Declarations declarations() {
        return backend().declarations();
    }

    default Types types() {
        return backend().types();
    }

    default ChannelsUtils channelsutils() {
        return backend().channelsutils();
    }

    // -- Evaluate Expressions

    String evaluate(Expression expr);


    /**
     * Evaluate expression variable
     *
     * @param variable
     * @return
     */
    default String evaluate(ExprVariable variable) {
        return variables().name(variable.getVariable());
    }

    /**
     * Evaluate a reference expression
     *
     * @param ref
     * @return
     */
    default String evaluate(ExprRef ref) {
        return "(&" + variables().name(ref.getVariable()) + ")";
    }

    /**
     * Evaluate a dereference expression
     *
     * @param deref
     * @return
     */
    default String evaluate(ExprDeref deref) {
        return "(*" + evaluate(deref.getReference()) + ")";
    }

    /**
     * Evaluate an expression Global variable
     *
     * @param variable
     * @return
     */
    default String evaluate(ExprGlobalVariable variable) {
        return variables().globalName(variable);
    }

    /**
     * Evaluate expression literal
     *
     * @param literal
     * @return
     */

    default String evaluate(ExprLiteral literal) {
        switch (literal.getKind()) {
            case Integer:
                return literal.getText();
            case True:
                return "true";
            case False:
                return "false";
            case Real:
                return literal.getText();
            case String:
                return literal.getText();
            default:
                throw new UnsupportedOperationException(literal.getText());
        }
    }

    /**
     * Evaluate expression input
     *
     * @param input
     * @return
     */
    default String evaluate(ExprInput input) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declarations().declaration(types().type(input), tmp));
        if (input.hasRepeat()) {
            if (input.getOffset() == 0) {
                emitter().emit("pinPeekRepeat_%s(%s,/* list buf*/, %d);", tmp, channelsutils().inputPortTypeSize(input.getPort()), channelsutils().definedInputPort(input.getPort()), input.getRepeat());
            } else {
                throw new RuntimeException("not implemented");
            }
        } else {
            if (input.getOffset() == 0) {
                emitter().emit("%s = pinPeekFront_%s(%s);", tmp, channelsutils().inputPortTypeSize(input.getPort()), channelsutils().definedInputPort(input.getPort()));
            } else {
                emitter().emit("%s = pinPeek_%s(%s, %d);", tmp, channelsutils().inputPortTypeSize(input.getPort()), channelsutils().definedInputPort(input.getPort()), input.getOffset());
            }
        }

        return tmp;
    }

    /**
     * Evaluate binary expression
     *
     * @param binaryOp
     * @return
     */

    default String evaluate(ExprBinaryOp binaryOp) {
        assert binaryOp.getOperations().size() == 1 && binaryOp.getOperands().size() == 2;
        String operation = binaryOp.getOperations().get(0);
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        switch (operation) {
            case "+":
            case "-":
            case "*":
            case "/":
            case "<":
            case "<=":
            case ">":
            case ">=":
            case "==":
            case "!=":
            case "<<":
            case ">>":
            case "&":
            case "|":
            case "^":
                return String.format("(%s %s %s)", evaluate(left), operation, evaluate(right));
            case "=":
                return String.format("(%s == %s)", evaluate(left), evaluate(right));
            case "mod":
                return String.format("(%s %% %s)", evaluate(left), evaluate(right));
            case "and":
            case "&&":
                String andResult = variables().generateTemp();
                emitter().emit("_Bool %s;", andResult);
                emitter().emit("if (%s) {", evaluate(left));
                emitter().increaseIndentation();
                emitter().emit("%s = %s;", andResult, evaluate(right));
                emitter().decreaseIndentation();
                emitter().emit("} else {");
                emitter().increaseIndentation();
                emitter().emit("%s = false;", andResult);
                emitter().decreaseIndentation();
                emitter().emit("}");
                return andResult;
            case "||":
            case "or":
                String orResult = variables().generateTemp();
                emitter().emit("_Bool %s;", orResult);
                emitter().emit("if (%s) {", evaluate(left));
                emitter().increaseIndentation();
                emitter().emit("%s = true;", orResult);
                emitter().decreaseIndentation();
                emitter().emit("} else {");
                emitter().increaseIndentation();
                emitter().emit("%s = %s;", orResult, evaluate(right));
                emitter().decreaseIndentation();
                emitter().emit("}");
                return orResult;
            default:
                throw new UnsupportedOperationException(operation);
        }
    }

    /**
     * Evaluate unary expression
     *
     * @param unaryOp
     * @return
     */
    default String evaluate(ExprUnaryOp unaryOp) {
        switch (unaryOp.getOperation()) {
            case "-":
            case "~":
                return String.format("%s(%s)", unaryOp.getOperation(), evaluate(unaryOp.getOperand()));
            case "not":
                return String.format("!%s", evaluate(unaryOp.getOperand()));
            default:
                throw new UnsupportedOperationException(unaryOp.getOperation());
        }
    }

    /**
     * Evaluate comprehensoin expression
     *
     * @param comprehension
     * @return
     */
    default String evaluate(ExprComprehension comprehension) {
        // TODO: Implement me
        return "/* TODO:ExprComprehension */";
    }

    /**
     * Evaluate list expression
     *
     * @param list
     * @return
     */
    default String evaluate(ExprList list) {
        // TODO: Implement me
        return "/* TODO:ExprList */";
    }

    /**
     * Evaluate an indexer expression
     *
     * @param indexer
     * @return
     */
    default String evaluate(ExprIndexer indexer) {
        return String.format("%s.p[%s]", evaluate(indexer.getStructure()), evaluate(indexer.getIndex()));
    }

    /**
     * Evaluate expression if
     *
     * @param expr
     * @return
     */
    default String evaluate(ExprIf expr) {
        // TODO: Implement me
        return "/* TODO:ExprIf */";
    }


    /**
     * Evaluate application expression
     *
     * @param apply
     * @return
     */
    default String evaluate(ExprApplication apply) {
        // TODO: Implement me
        return "/* TODO:ExprApplication */";
    }

    /**
     * Evaluate expression lambda
     *
     * @param lambda
     * @return
     */
    default String evaluate(ExprLambda lambda) {
        // TODO: Implement me
        return "/* TODO:ExprLambda */";
    }

    /**
     * Evaluate expression proc
     *
     * @param proc
     * @return
     */
    default String evaluate(ExprProc proc) {
        // TODO: Implement me
        return "/* TODO:ExprProc */";
    }

    /**
     * Evaluate expression let
     *
     * @param let
     * @return
     */
    default String evaluate(ExprLet let) {
        // TODO: Implement me
        return "/* TODO:ExprLet */";
    }


}
