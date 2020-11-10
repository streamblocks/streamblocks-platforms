package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.ChannelUtils;
import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtWrite;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueDeref;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueIndexer;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.*;

import java.util.*;
import java.util.stream.Collectors;

@Module
public interface ExpressionEvaluator {

    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

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

    default ChannelUtils channelsutils() {
        return backend().channelUtils();
    }

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default Trackable memoryStack() {
        return backend().memoryStack();
    }

    // -- Evaluate Expressions

    default String evaluateCall(Expression expression) {
        return evaluate(expression);
    }

    default String evaluateCall(ExprVariable variable) {
        IRNode parent = backend().tree().parent(variable);
/*
        if (parent instanceof StmtCall || parent instanceof ExprApplication) {
            VarDecl decl = backend().varDecls().declaration(variable.getVariable());
            IRNode parentDecl = backend().tree().parent(decl);
            Instance instance = backend().instancebox().get();
            return variable.getVariable().getName();
        }
*/
        return variables().name(variable.getVariable());
    }

    String evaluate(Expression expr);


    /**
     * Evaluate expression variable
     *
     * @param variable
     * @return
     */
    default String evaluate(ExprVariable variable) {
        IRNode parent = backend().tree().parent(variable);
        return variables().name(variable.getVariable());
    }

    /**
     * Evaluate a reference expression
     *
     * @param ref
     * @return
     */
    default String evaluate(ExprRef ref) {
        VarDecl decl = backend().varDecls().declaration(ref.getVariable());
        Type type = backend().types().declaredType(decl);
        if (type instanceof ListType) {
            return variables().name(ref.getVariable());
        } else if (type instanceof RefType) {
            RefType refType = (RefType) type;
            if (refType.getType() instanceof ListType) {
                return variables().name(ref.getVariable());
            }
        }

        return "(&" + variables().name(ref.getVariable()) + ")";
    }

    /**
     * Evaluate a dereference expression
     *
     * @param deref
     * @return
     */
    default String evaluate(ExprDeref deref) {
        Expression expr = deref.getReference();
        if (expr instanceof ExprVariable) {
            Variable var = ((ExprVariable) expr).getVariable();
            VarDecl decl = backend().varDecls().declaration(var);
            Type type = backend().types().declaredType(decl);
            if (type instanceof ListType) {
                return evaluate(deref.getReference());
            } else if (type instanceof RefType) {
                return evaluate(deref.getReference());
            }
        }

        return "(*" + evaluate(deref.getReference()) + ")";
    }

    /**
     * Evaluate an expression Globals variable
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
        Type type = types().type(input);

        emitter().emit("%s = %s;", declarations().declarationTemp(types().type(input), tmp), backend().defaultValues().defaultValue(types().type(input)));

        if (type instanceof AlgebraicType) {
            // memoryStack().trackPointer(tmp, type);
        }

        if (input.hasRepeat()) {
            if (input.getOffset() == 0) {
                emitter().emit("for(int i = 0; i < %s; i++){", input.getRepeat());
                {
                    emitter().increaseIndentation();

                    emitter().emit("%s[i] = tokens_%s[(index_%2$s + (i)) %% SIZE_%2$s];", tmp, input.getPort().getName());

                    emitter().decreaseIndentation();
                }
                emitter().emit("}");

            } else {
                throw new RuntimeException("not implemented");
            }
        } else {
            emitter().emit("%s = tokens_%s[(index_%2$s + (%d)) %% SIZE_%2$s];", tmp, input.getPort().getName(), input.getOffset());
        }

        return tmp;
    }

    void evaluateWithLvalue(String lvalue, Expression expr);

    default void evaluateWithLvalue(String lvalue, ExprInput input) {
        // String type = channelsutils().inputPortTypeSize(input.getPort());
        if (backend().channelUtils().isTargetConnected(backend().instancebox().get().getInstanceName(), input.getPort().getName())) {
            if (input.hasRepeat()) {
                if (input.getOffset() == 0) {
                    String index = variables().generateTemp();
                    emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, input.getRepeat());
                    emitter().emit("\t%1$s[%2$s] = tokens_%3$s[(index_%3$s + (%2$s)) %% SIZE_%3$s];", lvalue, index, input.getPort().getName());
                    emitter().emit("}");
                } else {
                    throw new RuntimeException("not implemented");
                }
            } else {
                emitter().emit("%s = tokens_%s[(index_%2$s + (%d)) %% SIZE_%2$s];", lvalue, input.getPort().getName(), input.getOffset());
            }
        }
    }

    default String compare(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        return String.format("(%s == %s)", lvalue, rvalue);
    }

    default String compare(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        String index = variables().generateTemp();
        emitter().emit("%s = true;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("for (size_t %1$s = 0; (%1$s < %2$s) && %3$s; %1$s++) {", index, lvalueType.getSize().getAsInt(), tmp);
        emitter().increaseIndentation();
        emitter().emit("%s &= %s;", tmp, compare(lvalueType.getElementType(), String.format("%s.data[%s]", lvalue, index), rvalueType.getElementType(), String.format("%s.data[%s]", rvalue, index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return tmp;
    }

    default String compare(SetType lvalueType, String lvalue, SetType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = compare_%2$s(%3$s, %4$s);", tmp, typeseval().type(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(MapType lvalueType, String lvalue, MapType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = compare_%2$s(%3$s, %4$s);", tmp, typeseval().type(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(StringType lvalueType, String lvalue, StringType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = compare_%2$s(%3$s, %4$s);", tmp, typeseval().type(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(AlgebraicType lvalueType, String lvalue, AlgebraicType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = compare_%s(%s, %s);", declarations().declaration(BoolType.INSTANCE, tmp), backend().algebraic().utils().name(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(AliasType lvalueType, String lvalue, AliasType rvalueType, String rvalue) {
        return compare(lvalueType.getType(), lvalue, rvalueType.getType(), rvalue);
    }

    default String compare(TupleType lvalueType, String lvalue, TupleType rvalueType, String rvalue) {
        return compare(backend().tuples().convert().apply(lvalueType), lvalue, backend().tuples().convert().apply(rvalueType), rvalue);
    }


    default String evaluate(ExprBinaryOp binaryOp) {
        assert binaryOp.getOperations().size() == 1 && binaryOp.getOperands().size() == 2;
        Type lhs = types().type(binaryOp.getOperands().get(0));
        Type rhs = types().type(binaryOp.getOperands().get(1));
        String operation = binaryOp.getOperations().get(0);
        switch (operation) {
            case "+":
                return evaluateBinaryAdd(lhs, rhs, binaryOp);
            case "-":
                return evaluateBinarySub(lhs, rhs, binaryOp);
            case "*":
                return evaluateBinaryTimes(lhs, rhs, binaryOp);
            case "/":
                return evaluateBinaryDiv(lhs, rhs, binaryOp);
            case "div":
                return evaluateBinaryIntDiv(lhs, rhs, binaryOp);
            case "%":
            case "mod":
                return evaluateBinaryMod(lhs, rhs, binaryOp);
            case "^":
                return evaluateBinaryBitXor(lhs, rhs, binaryOp);
            case "&":
                return evaluateBinaryBitAnd(lhs, rhs, binaryOp);
            case "<<":
                return evaluateBinaryShiftL(lhs, rhs, binaryOp);
            case ">>":
                return evaluateBinaryShiftR(lhs, rhs, binaryOp);
            case "&&":
            case "and":
                return evaluateBinaryAnd(lhs, rhs, binaryOp);
            case "|":
                return evaluateBinaryBitOr(lhs, rhs, binaryOp);
            case "||":
            case "or":
                return evaluateBinaryOr(lhs, rhs, binaryOp);
            case "=":
            case "==":
                return evaluateBinaryEq(lhs, rhs, binaryOp);
            case "!=":
                return evaluateBinaryNeq(lhs, rhs, binaryOp);
            case "<":
                return evaluateBinaryLtn(lhs, rhs, binaryOp);
            case "<=":
                return evaluateBinaryLeq(lhs, rhs, binaryOp);
            case ">":
                return evaluateBinaryGtn(lhs, rhs, binaryOp);
            case ">=":
                return evaluateBinaryGeq(lhs, rhs, binaryOp);
            case "in":
                return evaluateBinaryIn(lhs, rhs, binaryOp);
            default:
                throw new UnsupportedOperationException(operation);
        }
    }

    default String evaluateBinaryAdd(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryAdd(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s + %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryAdd(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = union_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(Type lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(rhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(lhs), typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, Type rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(lhs), typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(RealType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(rhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(RealType.f64), typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, RealType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(lhs), typeseval().type(RealType.f64), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(IntType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Type type = lhs.isSigned() ? new IntType(OptionalInt.empty(), true) : new IntType(OptionalInt.empty(), false);
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(rhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(type), typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Type type = rhs.isSigned() ? new IntType(OptionalInt.empty(), true) : new IntType(OptionalInt.empty(), false);
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(lhs), typeseval().type(type), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinarySub(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinarySub(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s - %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinarySub(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = difference_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryTimes(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryTimes(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s * %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryTimes(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = intersect_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryDiv(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryDiv(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s / %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryIntDiv(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryIntDiv(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s / %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryMod(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryMod(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s %% %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryBitXor(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryBitXor(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s ^ %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryMod(RealType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("pow(%s, %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryBitAnd(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryBitAnd(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s & %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryShiftL(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryShiftL(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s << %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryShiftR(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryShiftR(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s >> %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryAnd(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryAnd(BoolType lhs, BoolType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        String andResult = variables().generateTemp();
        emitter().emit("bool %s;", andResult);
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
    }

    default String evaluateBinaryBitOr(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryBitOr(IntType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s | %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryOr(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryOr(BoolType lhs, BoolType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        String orResult = variables().generateTemp();
        emitter().emit("bool %s;", orResult);
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
    }

    default String evaluateBinaryEq(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return compare(types().type(left), evaluate(left), types().type(right), evaluate(right));
    }

    default String evaluateBinaryNeq(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return "!" + compare(types().type(left), evaluate(left), types().type(right), evaluate(right));
    }

    default String evaluateBinaryLtn(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryLtn(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s < %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryLtn(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryLtn(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryLeq(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryLeq(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s <= %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryLeq(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_equal_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryLeq(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_equal_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGtn(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryGtn(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s > %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryGtn(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGtn(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGeq(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryGeq(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return String.format("(%s >= %s)", evaluate(left), evaluate(right));
    }

    default String evaluateBinaryGeq(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_equal_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGeq(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_equal_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryIn(Type lhs, ListType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        String index = variables().generateTemp();
        String elem = evaluate(binaryOp.getOperands().get(0));
        String list = evaluate(binaryOp.getOperands().get(1));
        emitter().emit("%s = false;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("for (size_t %1$s = 0; (%1$s < %2$s) && !(%3$s); %1$s++) {", index, rhs.getSize().getAsInt(), tmp);
        emitter().increaseIndentation();
        emitter().emit("%s |= %s;", tmp, compare(lhs, elem, rhs.getElementType(), String.format("%s.data[%s]", list, index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, MapType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluate(ExprUnaryOp unaryOp) {
        switch (unaryOp.getOperation()) {
            case "-":
                return evaluateUnaryMinus(types().type(unaryOp.getOperand()), unaryOp);
            case "~":
                return evaluateUnaryInvert(types().type(unaryOp.getOperand()), unaryOp);
            case "!":
            case "not":
                return evaluateUnaryNot(types().type(unaryOp.getOperand()), unaryOp);
            case "dom":
                return evaluateUnaryDom(types().type(unaryOp.getOperand()), unaryOp);
            case "rng":
                return evaluateUnaryRng(types().type(unaryOp.getOperand()), unaryOp);
            case "#":
                return evaluateUnarySize(types().type(unaryOp.getOperand()), unaryOp);
            default:
                throw new UnsupportedOperationException(unaryOp.getOperation());
        }
    }

    default String evaluateUnaryMinus(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    default String evaluateUnaryMinus(NumberType type, ExprUnaryOp expr) {
        return String.format("-(%s)", evaluate(expr.getOperand()));
    }

    default String evaluateUnaryInvert(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    default String evaluateUnaryInvert(IntType type, ExprUnaryOp expr) {
        return String.format("~(%s)", evaluate(expr.getOperand()));
    }

    default String evaluateUnaryNot(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    default String evaluateUnaryNot(BoolType type, ExprUnaryOp expr) {
        return String.format("!(%s)", evaluate(expr.getOperand()));
    }

    default String evaluateUnaryDom(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    default String evaluateUnaryDom(MapType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = domain_%s(%s);", declarations().declaration(types().type(expr), tmp), typeseval().type(type), evaluate(expr.getOperand()));
        return tmp;
    }

    default String evaluateUnaryRng(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    default String evaluateUnaryRng(MapType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = range_%s(%s);", declarations().declaration(types().type(expr), tmp), typeseval().type(type), evaluate(expr.getOperand()));
        return tmp;
    }

    default String evaluateUnarySize(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    default String evaluateUnarySize(ListType type, ExprUnaryOp expr) {
        return "" + type.getSize().getAsInt();
    }

    default String evaluateUnarySize(SetType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s->size;", declarations().declaration(types().type(expr), tmp), evaluate(expr.getOperand()));
        return tmp;
    }

    default String evaluateUnarySize(MapType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s->size;", declarations().declaration(types().type(expr), tmp), evaluate(expr.getOperand()));
        return tmp;
    }

    default String evaluateUnarySize(StringType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = strlen(%s);", declarations().declaration(types().type(expr), tmp), evaluate(expr.getOperand()));
        return tmp;
    }


    /**
     * Evaluate unary expression
     *
     * @param unaryOp
     * @return default String evaluate(ExprUnaryOp unaryOp) {
    switch (unaryOp.getOperation()) {
    case "-":
    case "~":
    return String.format("%s(%s)", unaryOp.getOperation(), evaluate(unaryOp.getOperand()));
    case "not":
    return String.format("!%s", evaluate(unaryOp.getOperand()));
    default:
    throw new UnsupportedOperationException(unaryOp.getOperation());
    }
    }*/

    /**
     * Evaluate comprehensoin expression
     *
     * @param comprehension
     * @return
     */
    default String evaluate(ExprComprehension comprehension) {
        return evaluateComprehension(comprehension, types().type(comprehension));
    }

    String evaluateComprehension(ExprComprehension comprehension, Type t);

    default String evaluateComprehension(ExprComprehension comprehension, ListType t) {
        Type typeForTmp = t;
        IRNode parent = backend().tree().parent(comprehension);
        String name = null;
        Boolean isStmtWrite = false;
        if (parent instanceof StmtWrite) {
            name = ((StmtWrite) parent).getPort().getName();
            isStmtWrite = true;
        }  else {
            name = variables().generateTemp();
            String decl = declarations().declarationTemp(t, name);
            emitter().emit("%s;", decl);
        }

        String index = variables().generateTemp();
        emitter().emit("size_t %s = 0;", index);

        backend().writeBox().set(isStmtWrite);

        evaluateListComprehension(comprehension, name, index);

        backend().writeBox().clear();
        return name;
    }

    @Module
    interface LValueName {
        String name(LValue lValue);

        default String name(LValueVariable var) {
            return var.getVariable().getName();
        }

        default String name(LValueIndexer indexer) {
            return name(indexer.getStructure());
        }

        default String name(LValueDeref deref) {
            return name(deref.getVariable());
        }
    }

    void evaluateListComprehension(Expression comprehension, String result, String index);

    default void evaluateListComprehension(ExprComprehension comprehension, String result, String index) {
        if (!comprehension.getFilters().isEmpty()) {
            throw new UnsupportedOperationException("Filters in comprehensions not supported.");
        }
        withGenerator(comprehension.getGenerator().getCollection(), comprehension.getGenerator().getVarDecls(), () -> {
            evaluateListComprehension(comprehension.getCollection(), result, index);
        });
    }

    default void evaluateListComprehension(ExprList list, String result, String index) {
        list.getElements().forEach(element -> {
                    boolean isStmtWrite = backend().writeBox().get();
                    if (isStmtWrite) {
                        emitter().emit("tokens_%1$s[(index_%1$s + (%2$s++)) %% SIZE_%1$s] = %3$s;", result, index, evaluate(element));
                    } else {
                        if (element instanceof ExprComprehension) {
                            String eval = evaluate(element);
                            emitter().emit("memcpy(%1$s[%2$s], %3$s, sizeof(%1$s[%2$s]));", result, index, eval);
                            emitter().emit("%s++;", index);
                        } else {
                            emitter().emit("%s[%s++] = %s;", result, index, evaluate(element));
                        }
                    }
                }
        );
    }

    void withGenerator(Expression collection, ImmutableList<GeneratorVarDecl> varDecls, Runnable body);

    default void withGenerator(ExprBinaryOp binOp, ImmutableList<GeneratorVarDecl> varDecls, Runnable action) {
        if (binOp.getOperations().equals(Collections.singletonList(".."))) {
            String from = evaluate(binOp.getOperands().get(0));
            String to = evaluate(binOp.getOperands().get(1));
            for (VarDecl d : varDecls) {
                Type type = types().declaredType(d);
                String name = variables().declarationName(d);
                emitter().emit("%s = %s;", declarations().declaration(type, name), from);
                emitter().emit("while (%s <= %s) {", name, to);
                emitter().increaseIndentation();
            }
            action.run();
            List<VarDecl> reversed = new ArrayList<>(varDecls);
            Collections.reverse(reversed);
            for (VarDecl d : reversed) {
                emitter().emit("%s++;", variables().declarationName(d));
                emitter().decreaseIndentation();
                emitter().emit("}");
            }
        } else {
            throw new UnsupportedOperationException(binOp.getOperations().get(0));
        }
    }

    /**
     * Evaluate list expression
     *
     * @param list
     * @return
     */
    default String evaluate(ExprList list) {
        ListType t = (ListType) types().type(list);
        if (t.getSize().isPresent()) {

            IRNode parent = backend().tree().parent(list);
            if(parent instanceof VarDecl){
                VarDecl decl = (VarDecl) parent;
                t = (ListType) types().declaredType(decl);
            }


            String name = variables().generateTemp();
            String decl = declarations().declarationTemp(t, name);
            String value = evaluateExprList(list);

            String init = value;
            emitter().emit("%s = %s;", decl, init);
            return name;
        } else {
            return "NULL /* TODO: implement dynamically sized lists */";
        }
    }


    default String evaluateExprList(Expression expr) {
        return evaluate(expr);
    }

    default String evaluateExprList(ExprList list) {
        String value = list.getElements().stream().sequential()
                .map(this::evaluateExprList)
                .collect(Collectors.joining(", ", "{", "}"));
        return value;
    }


    default String evaluate(ExprIndexer indexer) {
        return exprIndexing(types().type(indexer.getStructure()), indexer);
    }

    String exprIndexing(Type type, ExprIndexer indexer);

    default String exprIndexing(ListType type, ExprIndexer indexer) {
        return String.format("%s[%s]", evaluate(indexer.getStructure()), evaluate(indexer.getIndex()));
    }

    VarDecl evalExprIndexVar(Expression expr);


    default VarDecl evalExprIndexVar(ExprVariable expr) {

        return backend().varDecls().declaration(expr);
    }

    default VarDecl evalExprIndexVar(ExprGlobalVariable expr) {
        return backend().varDecls().declaration(expr);
    }

    default VarDecl evalExprIndexVar(ExprDeref expr) {
        if (expr.getReference() instanceof ExprVariable) {
            return backend().varDecls().declaration((ExprVariable) expr.getReference());
        }

        throw new UnsupportedOperationException();
    }

    default VarDecl evalExprIndexVar(ExprIndexer expr) {
        return evalExprIndexVar(expr.getStructure());
    }

    default List<String> getListIndexes(ExprIndexer expr) {
        List<String> indexByDim = new ArrayList<>();
        if (expr.getStructure() instanceof ExprIndexer) {
            indexByDim.add(evaluate(expr.getIndex()));
            getListIndexes((ExprIndexer) expr.getStructure()).stream().forEachOrdered(indexByDim::add);
        } else {
            indexByDim.add(evaluate(expr.getIndex()));
        }

        return indexByDim;
    }

    /**
     * Evaluate expression if
     *
     * @param expr
     * @return
     */
    default String evaluate(ExprIf expr) {
        Type type = types().type(expr);
        String temp = variables().generateTemp();

        String decl = declarations().declarationTemp(type, temp);
        emitter().emit("%s = %s;", decl, backend().defaultValues().defaultValue(type));
        emitter().emit("if (%s) {", evaluate(expr.getCondition()));
        emitter().increaseIndentation();
        Type thenType = types().type(expr.getThenExpr());
        String thenValue = evaluate(expr.getThenExpr());
        backend().statements().copy(type, temp, thenType, thenValue);
        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();
        Type elseType = types().type(expr.getElseExpr());
        String elseValue = evaluate(expr.getElseExpr());
        backend().statements().copy(type, temp, elseType, elseValue);
        emitter().decreaseIndentation();
        emitter().emit("}");
        return temp;
    }


    /**
     * Evaluate application expression
     *
     * @param apply
     * @return
     */
    default String evaluate(ExprApplication apply) {
        String fn;
        List<String> parameters = new ArrayList<>();

        for (Expression parameter : apply.getArgs()) {
            parameters.add(evaluate(parameter));
        }

        fn = evaluateCall(apply.getFunction());
        String result = variables().generateTemp();
        String decl = declarations().declarationTemp(types().type(apply), result);

        emitter().emit("%s = %s(%s);", decl, fn, String.join(", ", parameters));
        return result;
    }

    /**
     * Evaluate expression lambda
     *
     * @param lambda
     * @return
     */
    default String evaluate(ExprLambda lambda) {
        backend().emitter().emit("// begin evaluate(ExprLambda)");
        String functionName = backend().callables().functionName(lambda);
        String env = backend().callables().environmentName(lambda);
        for (VarDecl var : backend().callables().closure(lambda)) {
            emitter().emit("%s.%s = %s;", env, variables().declarationName(var), variables().reference(var));
        }

        Type type = backend().types().type(lambda);
        String typeName = backend().callables().mangle(type).encode();
        String funPtr = backend().variables().generateTemp();
        backend().emitter().emit("%s %s = { &%s, &%s };", typeName, funPtr, functionName, env);

        backend().emitter().emit("// end evaluate(ExprLambda)");
        return funPtr;
    }

    /**
     * Evaluate expression proc
     *
     * @param proc
     * @return
     */
    default String evaluate(ExprProc proc) {
        backend().emitter().emit("// begin evaluate(ExprProc)");
        String functionName = backend().callables().functionName(proc);
        String env = backend().callables().environmentName(proc);
        for (VarDecl var : backend().callables().closure(proc)) {
            emitter().emit("%s.%s = %s;", env, variables().declarationName(var), variables().reference(var));
        }

        Type type = backend().types().type(proc);
        String typeName = backend().callables().mangle(type).encode();
        String funPtr = backend().variables().generateTemp();
        backend().emitter().emit("%s %s = { &%s, &%s };", typeName, funPtr, functionName, env);

        backend().emitter().emit("// end evaluate(ExprProc)");
        return funPtr;
    }

    /**
     * Evaluate expression let
     *
     * @param let
     * @return
     */
    default String evaluate(ExprLet let) {
        let.forEachChild(backend().callables()::declareEnvironmentForCallablesInScope);
        for (VarDecl decl : let.getVarDecls()) {
            Type type = types().declaredType(decl);
            String name = variables().declarationName(decl);
            emitter().emit("%s = %s;", declarations().declaration(type, name), backend().defaultValues().defaultValue(type));
            emitter().emit("{");
            emitter().increaseIndentation();
            String eval = evaluate(decl.getValue());
            backend().statements().copy(type, name, types().type(decl.getValue()), eval);
            emitter().decreaseIndentation();
            emitter().emit("}");
        }
        return evaluate(let.getBody());
    }

    default String evaluate(ExprTypeConstruction construction) {
        String fn = backend().algebraic().utils().constructor(construction.getConstructor());
        List<String> parameters = new ArrayList<>();
        for (Expression parameter : construction.getArgs()) {
            parameters.add(evaluate(parameter));
        }
        String result = variables().generateTemp();
        String decl = backend().declarations().declaration(types().type(construction), result);
        emitter().emit("%s = %s(%s);", decl, fn, String.join(", ", parameters));
        return result;
    }

    default String evaluate(ExprTypeAssertion assertion) {
        Type type = types().type(assertion.getType());
        String result = variables().generateTemp();
        String decl = declarations().declaration(type, result);
        emitter().emit("%s = (%s)(%s);", decl, typeseval().type(type) + (type instanceof AlgebraicType ? "*" : ""), evaluate(assertion.getExpression()));
        return result;

    }

    default String evaluate(ExprField field) {
        return String.format("%s->members.%s", evaluate(field.getStructure()), field.getField().getName());
    }

}
