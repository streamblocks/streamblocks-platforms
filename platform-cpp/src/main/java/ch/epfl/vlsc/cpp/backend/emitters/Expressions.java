package ch.epfl.vlsc.cpp.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.cpp.backend.CppBackend;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.GlobalDecl;
import se.lth.cs.tycho.ir.decl.GlobalVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.expr.ExprApplication;
import se.lth.cs.tycho.ir.expr.ExprBinaryOp;
import se.lth.cs.tycho.ir.expr.ExprCase;
import se.lth.cs.tycho.ir.expr.ExprComprehension;
import se.lth.cs.tycho.ir.expr.ExprDeref;
import se.lth.cs.tycho.ir.expr.ExprField;
import se.lth.cs.tycho.ir.expr.ExprGlobalVariable;
import se.lth.cs.tycho.ir.expr.ExprIf;
import se.lth.cs.tycho.ir.expr.ExprIndexer;
import se.lth.cs.tycho.ir.expr.ExprInput;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprLet;
import se.lth.cs.tycho.ir.expr.ExprList;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.expr.ExprMap;
import se.lth.cs.tycho.ir.expr.ExprNth;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.expr.ExprRef;
import se.lth.cs.tycho.ir.expr.ExprSet;
import se.lth.cs.tycho.ir.expr.ExprTuple;
import se.lth.cs.tycho.ir.expr.ExprTypeAssertion;
import se.lth.cs.tycho.ir.expr.ExprTypeConstruction;
import se.lth.cs.tycho.ir.expr.ExprUnaryOp;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.StmtCall;
import se.lth.cs.tycho.ir.type.FunctionTypeExpr;
import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.MapType;
import se.lth.cs.tycho.type.NumberType;
import se.lth.cs.tycho.type.RealType;
import se.lth.cs.tycho.type.SetType;
import se.lth.cs.tycho.type.StringType;
import se.lth.cs.tycho.type.TupleType;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

@Module
public interface Expressions {

    @Binding(BindingKind.INJECTED)
    CppBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default Types types() {
        return backend().types();
    }

    default Variables variables() {
        return backend().variables();
    }

    default Declarations declarations() {
        return backend().declarations();
    }

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }


    String evaluate(Expression expr);

    default String evaluate(ExprVariable variable) {
        if (backend().profilingbox().get()) {
            VarDecl decl = backend().varDecls().declaration(variable);
            if (!(decl.getValue() instanceof ExprInput)) {
                IRNode parent = backend().tree().parent(decl);
                if ((parent instanceof Scope) || (parent instanceof ActorMachine) || (parent instanceof NamespaceDecl)) {
                    Type type = backend().types().type(decl.getType());
                    if (type instanceof ListType) {
                        emitter().emit("__opCounters.DATAHANDLING_LIST_LOAD += 1;");
                    } else {
                        emitter().emit("__opCounters.DATAHANDLING_LOAD += 1;");
                    }

                    if (parent instanceof Scope) {
                        emitter().emit("__opCounters.updateReadCounter(\"%s\");", decl.getOriginalName());
                    }
                }
            }
        }
        return variables().name(variable.getVariable());
    }

    default String evaluate(ExprRef ref) {
        return variables().name(ref.getVariable());
    }

    default String evaluate(ExprDeref deref) {
        return evaluate(deref.getReference());
    }

    default String evaluate(ExprGlobalVariable variable) {
        return variables().globalName(variable);
    }

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
            case String: {
                return literal.getText();
            }
            case Char:
                return literal.getText();
            default:
                throw new UnsupportedOperationException(literal.getText());
        }
    }

    default String evaluate(ExprInput input) {
        String tmp = variables().generateTemp();
        Type type = types().type(input);
        if (input.hasRepeat()) {
            if (input.getOffset() == 0) {
                emitter().emit("auto %s = input[\"%s\"].peek_range<%s>(%d);", tmp, input.getPort().getName(), typeseval().type(type), input.getRepeat());
            } else {
                throw new RuntimeException("not implemented");
            }
        } else {
            emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
            if (input.getOffset() == 0) {
                emitter().emit("%s = %s[0];", tmp, input.getPort().getName());
            } else {
                String auto = variables().generateTemp();
                emitter().emit("auto %s = input[\"%s\"].peek_range<%s>(%s);", auto, input.getPort().getName(), typeseval().type(type), input.getOffset() + 1);
                emitter().emit("%s = %s[%s].ele;", tmp, auto, input.getOffset());
            }
        }
        return tmp;
    }

    default void evaluateWithDecl(String name, Type type, ExprInput input) {
        if (input.hasRepeat()) {
            if (input.getOffset() == 0) {
                emitter().emit("auto %s = input[\"%s\"].peek_range<%s>(%d);", name, input.getPort().getName(), typeseval().type(type), input.getRepeat());
            } else {
                throw new RuntimeException("not implemented");
            }
        } else {
            emitter().emit("%s = %s;", declarations().declaration(type, name), backend().defaultValues().defaultValue(type));
            if (input.getOffset() == 0) {
                emitter().emit("%s = %s[0];", name, input.getPort().getName());
            } else {
                String auto = variables().generateTemp();
                emitter().emit("auto %s = input[\"%s\"].peek_range<%s>(%s);", auto, input.getPort().getName(), typeseval().type(type), input.getOffset() + 1);
                emitter().emit("%s = %s[%s].ele;", name, auto, input.getOffset());
            }
        }
    }

    default String evaluate(ExprBinaryOp binaryOp) {
        assert binaryOp.getOperations().size() == 1 && binaryOp.getOperands().size() == 2;
        if (backend().profilingbox().get()) {
            emitter().emit(getOpBinaryPlus(binaryOp));
        }
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

    default String getOpBinaryPlus(ExprBinaryOp binaryOp) {
        String operation = binaryOp.getOperations().get(0);
        switch (operation) {
            case "+":
                return String.format("__opCounters.BINARY_PLUS += 1;");
            case "-":
                return String.format("__opCounters.BINARY_MINUS += 1;");
            case "*":
                return String.format("__opCounters.BINARY_TIMES += 1;");
            case "/":
                return String.format("__opCounters.BINARY_DIV += 1;");
            case "<":
                return String.format("__opCounters.BINARY_LT += 1;");
            case "<=":
                return String.format("__opCounters.BINARY_LE += 1;");
            case ">":
                return String.format("__opCounters.BINARY_GT += 1;");
            case ">=":
                return String.format("__opCounters.BINARY_GE += 1;");
            case "==":
            case "=":
                return String.format("__opCounters.BINARY_EQ += 1;");
            case "!=":
                return String.format("__opCounters.BINARY_NE += 1;");
            case "<<":
                return String.format("__opCounters.BINARY_SHIFT_LEFT += 1;");
            case ">>":
                return String.format("__opCounters.BINARY_SHIFT_RIGHT += 1;");
            case "&":
                return String.format("__opCounters.BINARY_BIT_AND += 1;");
            case "|":
                return String.format("__opCounters.BINARY_BIT_OR += 1;");
            case "^":
                return String.format("__opCounters.BINARY_EXP += 1;");
            case "mod":
                return String.format("__opCounters.BINARY_MOD += 1;");
            case "and":
            case "&&":
                return String.format("__opCounters.BINARY_LOGIC_AND += 1;");
            case "||":
            case "or":
                return String.format("__opCounters.BINARY_LOGIC_OR += 1;");
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
        emitter().emit("%s;", backend().declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = union_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = %2$s + %3$s;", tmp, evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(Type lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(rhs, tmp));
        emitter().emit("%1$s = std::to_string(%2$s), %3$s;", tmp, evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, Type rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = %2$s + std::to_string(%2$s);", tmp, evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(RealType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(rhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, backend().typeseval().type(RealType.f64), backend().typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, RealType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, backend().typeseval().type(lhs), backend().typeseval().type(RealType.f64), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(IntType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Type type = lhs.isSigned() ? new IntType(OptionalInt.empty(), true) : new IntType(OptionalInt.empty(), false);
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(rhs, tmp));
        emitter().emit("%1$s = std::to_string(%2$s) + %3$s;", tmp, evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Type type = rhs.isSigned() ? new IntType(OptionalInt.empty(), true) : new IntType(OptionalInt.empty(), false);
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = %2$s + std::to_string(%3$s);", tmp, evaluate(left), evaluate(right));
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
        emitter().emit("%s;", backend().declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = difference_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
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
        emitter().emit("%s;", backend().declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = intersect_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
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
        return backend().statements().compare(types().type(left), evaluate(left), types().type(right), evaluate(right));
    }

    default String evaluateBinaryNeq(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        return "!" + backend().statements().compare(types().type(left), evaluate(left), types().type(right), evaluate(right));
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
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryLtn(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
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
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_equal_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryLeq(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = less_than_equal_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
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
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGtn(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
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
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_equal_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryGeq(StringType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = greater_than_equal_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lhs), evaluate(left), evaluate(right));
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
        emitter().emit("%s = false;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("for (size_t %1$s = 0; (%1$s < %2$s) && !(%3$s); %1$s++) {", index, rhs.getSize().getAsInt(), tmp);
        emitter().increaseIndentation();
        emitter().emit("%s |= %s;", tmp, backend().statements().compare(lhs, elem, rhs.getElementType(), String.format("%s.data[%s]", list, index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, backend().typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, MapType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, backend().typeseval().type(rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", backend().declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, backend().typeseval().type(rhs), evaluate(left), evaluate(right));
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
        emitter().emit("%s = domain_%s(%s);", backend().declarations().declaration(types().type(expr), tmp), backend().typeseval().type(type), evaluate(expr.getOperand()));
        return tmp;
    }

    default String evaluateUnaryRng(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    default String evaluateUnaryRng(MapType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = range_%s(%s);", backend().declarations().declaration(types().type(expr), tmp), backend().typeseval().type(type), evaluate(expr.getOperand()));
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
        emitter().emit("%s = %s->size;", backend().declarations().declaration(types().type(expr), tmp), evaluate(expr.getOperand()));
        return tmp;
    }

    default String evaluateUnarySize(MapType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s->size;", backend().declarations().declaration(types().type(expr), tmp), evaluate(expr.getOperand()));
        return tmp;
    }

    default String evaluateUnarySize(StringType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = strlen(%s);", backend().declarations().declaration(types().type(expr), tmp), evaluate(expr.getOperand()));
        return tmp;
    }

    default String evaluate(ExprComprehension comprehension) {
        return evaluateComprehension(comprehension, types().type(comprehension));
    }

    String evaluateComprehension(ExprComprehension comprehension, Type t);

    default String evaluateComprehension(ExprComprehension comprehension, ListType t) {
        String name = variables().generateTemp();
        String decl = backend().declarations().declaration(t, name);
        emitter().emit("%s = %s;", decl, backend().defaultValues().defaultValue(t));
        String index = variables().generateTemp();
        emitter().emit("size_t %s = 0;", index);
        evaluateListComprehension(comprehension, name, index);
        return name;
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
        list.getElements().forEach(element ->
                emitter().emit("%s[%s++] = %s;", result, index, evaluate(element))
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
                emitter().emit("%s = %s;", backend().declarations().declaration(type, name), from);
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


    default String evaluate(ExprList list) {
        ListType t = (ListType) types().type(list);
        if (t.getSize().isPresent()) {
            String name = variables().generateTemp();
            Type elementType = t.getElementType();
            String decl = backend().declarations().declaration(t, name);
            String value = list.getElements().stream().sequential()
                    .map(element -> {
                        if (elementType instanceof AlgebraicType || backend().alias().isAlgebraicType(elementType)) {
                            String tmp = variables().generateTemp();
                            emitter().emit("%s = %s;", backend().declarations().declaration(elementType, tmp), backend().defaultValues().defaultValue(elementType));
                            backend().statements().copy(elementType, tmp, elementType, evaluate(element));
                            return tmp;
                        }
                        return evaluate(element);
                    })
                    .collect(Collectors.joining(", ", "{", "}"));
            emitter().emit("const %s = %s;", decl, value);
            return name;
        } else {
            return "NULL /* TODO: implement dynamically sized lists */";
        }
    }

    void forEach(Expression collection, List<GeneratorVarDecl> varDecls, Runnable action);

    default void forEach(ExprBinaryOp binOp, List<GeneratorVarDecl> varDecls, Runnable action) {
        emitter().emit("{");
        emitter().increaseIndentation();
        if (binOp.getOperations().equals(Collections.singletonList(".."))) {
            Type type = types().declaredType(varDecls.get(0));
            for (VarDecl d : varDecls) {
                emitter().emit("%s;", backend().declarations().declaration(type, variables().declarationName(d)));
            }
            String temp = variables().generateTemp();
            emitter().emit("%s = %s;", backend().declarations().declaration(type, temp), evaluate(binOp.getOperands().get(0)));
            emitter().emit("while (%s <= %s) {", temp, evaluate(binOp.getOperands().get(1)));
            emitter().increaseIndentation();
            for (VarDecl d : varDecls) {
                emitter().emit("%s = %s++;", variables().declarationName(d), temp);
            }
            action.run();
            emitter().decreaseIndentation();
            emitter().emit("}");
        } else {
            throw new UnsupportedOperationException(binOp.getOperations().get(0));
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default String evaluate(ExprSet set) {
        String name = variables().generateTemp();
        SetType type = (SetType) types().type(set);
        emitter().emit("%1$s = init_%2$s();", backend().declarations().declaration(type, name), backend().typeseval().type(type));
        set.getElements().stream().map(this::evaluate).forEach(elem -> {
            emitter().emit("add_%1$s(%2$s, %3$s);", backend().typeseval().type(type), name, elem);
        });
        return name;
    }

    default String evaluate(ExprMap map) {
        String name = variables().generateTemp();
        MapType type = (MapType) types().type(map);
        emitter().emit("%1$s = init_%2$s();", backend().declarations().declaration(type, name), backend().typeseval().type(type));
        map.getMappings().forEach(mapping -> {
            String key = evaluate(mapping.getKey());
            String value = evaluate(mapping.getValue());
            emitter().emit("add_%1$s(%2$s, %3$s, %4$s);", backend().typeseval().type(type), name, key, value);

        });
        return name;
    }

    default String evaluate(ExprIndexer indexer) {
        return exprIndexing(types().type(indexer.getStructure()), indexer);
    }

    String exprIndexing(Type type, ExprIndexer indexer);

    default String exprIndexing(ListType type, ExprIndexer indexer) {
        boolean fromInput = false;
        if (indexer.getStructure() instanceof ExprVariable) {
            VarDecl decl = backend().varDecls().declaration((ExprVariable) indexer.getStructure());
            if (decl.getValue() != null) {
                if (decl.getValue() instanceof ExprInput) {
                    fromInput = true;
                }
            }
        }

        return String.format("%s[%s]", evaluate(indexer.getStructure()), evaluate(indexer.getIndex()));
    }

    default String exprIndexing(MapType type, ExprIndexer indexer) {
        String index = variables().generateTemp();
        String map = evaluate(indexer.getStructure());
        String key = evaluate(indexer.getIndex());
        emitter().emit("size_t %s;", index);
        emitter().emit("for (%1$s = 0; %1$s < %2$s->size; %1$s++) {", index, map);
        emitter().increaseIndentation();
        emitter().emit("if (%s) break;", backend().statements().compare(type.getKeyType(), key, type.getValueType(), String.format("%s->data[%s].key", map, index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return String.format("%s->data[%s].value", map, index);
    }

    default String exprIndexing(StringType type, ExprIndexer indexer) {
        return String.format("%s[%s]", evaluate(indexer.getStructure()), evaluate(indexer.getIndex()));
    }

    default String evaluate(ExprTuple tuple) {
        String fn = backend().tuples().utils().constructor((TupleType) types().type(tuple));
        List<String> parameters = new ArrayList<>();
        for (Expression parameter : tuple.getElements()) {
            parameters.add(evaluate(parameter));
        }
        String result = variables().generateTemp();
        String decl = backend().declarations().declaration(types().type(tuple), result);
        emitter().emit("%s = %s(%s);", decl, fn, String.join(", ", parameters));
        return result;
    }

    default String evaluate(ExprNth nth) {
        return String.format("%s->%s", evaluate(nth.getStructure()), "_" + nth.getNth().getNumber());
    }

    default String evaluate(ExprIf expr) {
        Type type = types().type(expr);
        String temp = variables().generateTemp();
        String decl = backend().declarations().declaration(type, temp);
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

    default String evaluate(ExprApplication apply) {
        String fn;
        List<String> parameters = new ArrayList<>();

        VarDecl vDecl;
        List<TypeExpr> paramTypeExpr = null;
        if (apply.getFunction() instanceof ExprGlobalVariable) {
            ExprGlobalVariable globalVariable = (ExprGlobalVariable) apply.getFunction();
            vDecl = backend().globalnames().varDecl(globalVariable.getGlobalName(), true);
            if (vDecl instanceof GlobalVarDecl) {
                GlobalVarDecl gVarDecl = (GlobalVarDecl) vDecl;
                FunctionTypeExpr functionTypeExpr = (FunctionTypeExpr) vDecl.getType();
                paramTypeExpr = functionTypeExpr.getParameterTypes();
            }
        }
/*
        if (paramTypeExpr == null) {
            for (Expression parameter : apply.getArgs()) {
                parameters.add(evaluate(parameter));
            }
        } else {
            for (Expression parameter : apply.getArgs()) {
                Type type = backend().types().type(paramTypeExpr.get(apply.getArgs().indexOf(parameter)));
                parameters.add(evaluateWithType(parameter, type));
            }
        }
*/
        for (Expression parameter : apply.getArgs()) {
            if (parameter instanceof ExprList){
                parameters.add(evaluateOnlyValue((ExprList) parameter));
            }else {
                parameters.add(evaluate(parameter));
            }
        }


        fn = evaluateCall(apply.getFunction());

        String result = variables().generateTemp();
        String decl = declarations().declarationTemp(types().type(apply), result);
        emitter().emit("%s = %s(%s);", decl, fn, String.join(", ", parameters));
        if (backend().profilingbox().get()) {
            emitter().emit("__opCounters.DATAHANDLING_CALL += 1;");
        }
        return result;
    }

    default String evaluateWithType(Expression expr, Type type) {
        return evaluate(expr);
    }


    default String evaluateWithType(ExprList list, Type type) {
        ListType t = (ListType) type;
        if (t.getSize().isPresent()) {
            String name = variables().generateTemp();
            Type elementType = t.getElementType();
            String decl = backend().declarations().declaration(t, name);
            String value = list.getElements().stream().sequential()
                    .map(element -> {
                        if (elementType instanceof AlgebraicType || backend().alias().isAlgebraicType(elementType)) {
                            String tmp = variables().generateTemp();
                            emitter().emit("%s = %s;", backend().declarations().declaration(elementType, tmp), backend().defaultValues().defaultValue(elementType));
                            backend().statements().copy(elementType, tmp, elementType, evaluate(element));
                            return tmp;
                        }
                        return evaluate(element);
                    })
                    .collect(Collectors.joining(", ", "{", "}"));
            emitter().emit("const %s = %s;", decl, value);
            return name;
        } else {
            return "NULL /* TODO: implement dynamically sized lists */";
        }
    }

    String evaluateOnlyValue(Expressions expr);

    default String evaluateOnlyValue(ExprList list) {
        ListType t = (ListType) types().type(list);
        Type elementType = t.getElementType();
        String value = list.getElements().stream().sequential()
                .map(element -> {
                    if (elementType instanceof AlgebraicType || backend().alias().isAlgebraicType(elementType)) {
                        String tmp = variables().generateTemp();
                        emitter().emit("%s = %s;", backend().declarations().declaration(elementType, tmp), backend().defaultValues().defaultValue(elementType));
                        backend().statements().copy(elementType, tmp, elementType, evaluate(element));
                        return tmp;
                    }
                    return evaluate(element);
                })
                .collect(Collectors.joining(", ", "{", "}"));
        return value;
    }


    default String evaluateCall(Expression expression) {
        return evaluate(expression);
    }

    default String evaluateCall(ExprVariable variable) {
        IRNode parent = backend().tree().parent(variable);

        if (parent instanceof StmtCall || parent instanceof ExprApplication) {
            return variable.getVariable().getName();
        }

        return variables().name(variable.getVariable());
    }

    default String evaluate(ExprLambda lambda) {
        /*
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

         */
        throw new Error("not implemented : evaluate(ExprLambda lambda)");
    }

    default String evaluate(ExprProc proc) {
        /*
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
        return funPtr;*/
        throw new Error("not implemented : evaluate(ExprProc proc)");
    }

    default String evaluate(ExprLet let) {
        //let.forEachChild(backend().callables()::declareEnvironmentForCallablesInScope);
        for (VarDecl decl : let.getVarDecls()) {
            Type type = types().declaredType(decl);
            String name = variables().declarationName(decl);
            emitter().emit("%s = %s;", backend().declarations().declaration(type, name), backend().defaultValues().defaultValue(type));
            backend().statements().copy(type, name, types().type(decl.getValue()), evaluate(decl.getValue()));
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
        String decl = backend().declarations().declaration(type, result);
        emitter().emit("%s = (%s)(%s);", decl, backend().typeseval().type(type) + (type instanceof AlgebraicType || backend().alias().isAlgebraicType(type) ? "*" : ""), evaluate(assertion.getExpression()));
        return result;
    }

    default String evaluate(ExprField field) {
        return String.format("%s->%s", evaluate(field.getStructure()), field.getField().getName());
    }

    default String evaluate(ExprCase caseExpr) {
        return backend().patmat().evaluate(caseExpr);
    }

}
