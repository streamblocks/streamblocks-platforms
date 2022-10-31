package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtCall;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.*;

import java.util.*;
import java.util.stream.Collectors;

@Module
public interface ExpressionEvaluator {

    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

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

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default Statements statements() {
        return backend().statements();
    }

    // -- Evaluate Expressions

    default String evaluateCall(Expression expression) {
        return evaluate(expression);
    }

    default String evaluateCall(ExprVariable variable) {
        IRNode parent = backend().tree().parent(variable);

        if (parent instanceof StmtCall || parent instanceof ExprApplication) {
            VarDecl decl = backend().varDecls().declaration(variable.getVariable());
            String prefix = "";
            if (!backend().instancebox().isEmpty()) {
                Instance instance = backend().instancebox().get();
                prefix = instance.getInstanceName() + "_";
            }
            return prefix + variable.getVariable().getName();
        }

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
        VarDecl decl = backend().varDecls().declaration(variable);
        if (!(decl.getValue() instanceof ExprInput)) {
            IRNode parent = backend().tree().parent(decl);
            if ((parent instanceof Scope) || (parent instanceof ActorMachine) || (parent instanceof NamespaceDecl)) {
                Type type = backend().types().type(decl.getType());
                if (type instanceof ListType) {
                    backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_LIST_LOAD += 1;");
                } else {
                    backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_LOAD += 1;");
                }
            }
        }

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
        //if (input.hasRepeat()) {
        //    String maxIndex = typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
        //    emitter().emit("%s = malloc(sizeof(%s) * (%s));", declarations().declaration(types().type(input), tmp), maxIndex);
        //} else {
        emitter().emit("%s;", declarations().declarationTemp(types().type(input), tmp));
        //emitter().emit("%s = %s;", declarations().declarationTemp(types().type(input), tmp), backend().defaultValues().defaultValue(types().type(input)));
        //}
        if (type instanceof AlgebraicType) {
            // memoryStack().trackPointer(tmp, type);
        }

        if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), input.getPort().getName())) {
            if (input.hasRepeat()) {
                if (input.getOffset() == 0) {
                    emitter().emit("pinPeekRepeat_%s(%s, %s, %d);", channelsutils().inputPortTypeSize(input.getPort()), channelsutils().definedInputPort(input.getPort()), tmp, input.getRepeat());
                } else {
                    throw new RuntimeException("not implemented");
                }
            } else {
                if(type instanceof TensorType){
                    String tensor_tmp = variables().generateTemp();
                    if (input.getOffset() == 0) {
                        emitter().emit("Tensor *%s = (Tensor*) pinPeekFront_ref(%s);", tensor_tmp, channelsutils().definedInputPort(input.getPort()));
                    } else {
                        emitter().emit("Tensor *%s = (Tensor*) pinPeek_ref(%s, %d);", tensor_tmp, channelsutils().definedInputPort(input.getPort()), input.getOffset());
                    }
                    emitter().emit("%s = *%s;", tmp, tensor_tmp);
                }else {
                    if (input.getOffset() == 0) {
                        emitter().emit("%s = pinPeekFront_%s(%s);", tmp, channelsutils().inputPortTypeSize(input.getPort()), channelsutils().definedInputPort(input.getPort()));
                    } else {
                        emitter().emit("%s = pinPeek_%s(%s, %d);", tmp, channelsutils().inputPortTypeSize(input.getPort()), channelsutils().definedInputPort(input.getPort()), input.getOffset());
                    }
                }
            }
        }

        return tmp;
    }

    void evaluateWithLvalue(String lvalue, Expression expr);

    default void evaluateWithLvalue(String lvalue, ExprInput input) {
        Type type = types().type(input);
        String sType = backend().typeseval().type(type);
        if((type instanceof TensorType) || (type instanceof AlgebraicType)){
            sType = "ref";
        }

        if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), input.getPort().getName())) {
            if (input.hasRepeat()) {
                if (input.getOffset() == 0) {
                    emitter().emit("pinPeekRepeat_%s(%s, %s, %d);", sType, channelsutils().definedInputPort(input.getPort()), lvalue, input.getRepeat());
                } else {
                    throw new RuntimeException("not implemented");
                }
            } else {
                if(type instanceof TensorType){
                    String tmp = variables().generateTemp();
                    if (input.getOffset() == 0) {
                        emitter().emit("Tensor *%s = (Tensor*) pinPeekFront_ref(%s);", tmp, channelsutils().definedInputPort(input.getPort()));
                    } else {
                        emitter().emit("Tensor *%s = (Tensor*) pinPeek_ref(%s, %d);", tmp, channelsutils().definedInputPort(input.getPort()), input.getOffset());
                    }
                    emitter().emit("%s = *%s;", lvalue, tmp);
                }else{
                    if (input.getOffset() == 0) {
                        emitter().emit("%s = pinPeekFront_%s(%s);", lvalue, sType, channelsutils().definedInputPort(input.getPort()));
                    } else {
                        emitter().emit("%s = pinPeek_%s(%s, %d);", lvalue, sType, channelsutils().definedInputPort(input.getPort()), input.getOffset());
                    }
                }

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
/*
    default String compare(TupleType lvalueType, String lvalue, TupleType rvalueType, String rvalue) {
        return compare(backend().tuples().convert().apply(lvalueType), lvalue, backend().tuples().convert().apply(rvalueType), rvalue);
    }

 */


    /**
     * Evaluate binary expression
     *
     * @param binaryOp
     * @return
     */

    default String evaluate(ExprBinaryOp binaryOp) {
        assert binaryOp.getOperations().size() == 1 && binaryOp.getOperands().size() == 2;
        statements().profilingOp().add(getOpBinaryPlus(binaryOp));
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
        statements().profilingOp().add(getOpUnaryPlus(unaryOp));
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


    default String getOpBinaryPlus(ExprBinaryOp binaryOp) {
        String operation = binaryOp.getOperations().get(0);
        switch (operation) {
            case "+":
                return String.format("__opCounters->prof_BINARY_PLUS += 1;");
            case "-":
                return String.format("__opCounters->prof_BINARY_MINUS += 1;");
            case "*":
                return String.format("__opCounters->prof_BINARY_TIMES += 1;");
            case "/":
                return String.format("__opCounters->prof_BINARY_DIV += 1;");
            case "<":
                return String.format("__opCounters->prof_BINARY_LT += 1;");
            case "<=":
                return String.format("__opCounters->prof_BINARY_LE += 1;");
            case ">":
                return String.format("__opCounters->prof_BINARY_GT += 1;");
            case ">=":
                return String.format("__opCounters->prof_BINARY_GE += 1;");
            case "==":
            case "=":
                return String.format("__opCounters->prof_BINARY_EQ += 1;");
            case "!=":
                return String.format("__opCounters->prof_BINARY_NE += 1;");
            case "<<":
                return String.format("__opCounters->prof_BINARY_SHIFT_LEFT += 1;");
            case ">>":
                return String.format("__opCounters->prof_BINARY_SHIFT_RIGHT += 1;");
            case "&":
                return String.format("__opCounters->prof_BINARY_BIT_AND += 1;");
            case "|":
                return String.format("__opCounters->prof_BINARY_BIT_OR += 1;");
            case "^":
                return String.format("__opCounters->prof_BINARY_EXP += 1;");
            case "mod":
                return String.format("__opCounters->prof_BINARY_MOD += 1;");
            case "and":
            case "&&":
                return String.format("__opCounters->prof_BINARY_LOGIC_AND += 1;");
            case "||":
            case "or":
                return String.format("__opCounters->prof_BINARY_LOGIC_OR += 1;");
            default:
                throw new UnsupportedOperationException(operation);
        }
    }

    default String getOpUnaryPlus(ExprUnaryOp unaryOp) {
        switch (unaryOp.getOperation()) {
            case "-":
                return String.format("__opCounters->prof_UNARY_MINUS += 1;");
            case "~":
                return String.format("__opCounters->prof_UNARY_BIT_NOT += 1;");
            case "not":
                return String.format("__opCounters->prof_UNARY_LOGIC_NOT += 1;");
            case "#":
                return String.format("__opCounters->prof_UNARY_ELEMENTS += 1;");
            default:
                throw new UnsupportedOperationException(unaryOp.getOperation());
        }
    }

    /**
     * Evaluate comprehension expression
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
        String name;
        if (parent instanceof StmtAssignment) {
            StmtAssignment stmt = (StmtAssignment) parent;
            if (stmt.getLValue() instanceof LValueVariable) {
                LValueVariable lvalue = (LValueVariable) stmt.getLValue();
                name = backend().variables().name(lvalue.getVariable());
            } else {
                name = variables().generateTemp();
                String decl = declarations().declarationTemp(t, name);
                emitter().emit("%s;", decl);
            }
        } else {
            name = variables().generateTemp();
            String decl = declarations().declarationTemp(t, name);
            emitter().emit("%s;", decl);
        }

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
        list.getElements().forEach(element -> {
                    if (element instanceof ExprComprehension) {
                        //emitter().emit("%s[%2$s] = %3$s[%2$s++];", result, index, evaluate(element));
                        ListType type = (ListType) backend().types().type(element);
                        String name = evaluate(element);
                        emitter().emit("memcpy(%s + %s*(%s++), %s, sizeof(%4$s));", result, type.getSize().getAsInt(), index, name);
                    } else {
                        emitter().emit("%s[%s++] = %s;", result, index, evaluate(element));
                    }
                }
        );
    }

    /*
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
                    if (element instanceof ExprComprehension) {
                        String eval = evaluate(element);
                        emitter().emit("memcpy(%1$s[%2$s], %3$s, sizeof(%1$s[%2$s]));", result, index, eval);
                        emitter().emit("%s++;", index);
                    } else {
                        emitter().emit("%s[%s++] = %s;", result, index, evaluate(element));
                    }
                }
        );
    }
    */


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

            String name = variables().generateTemp();
            String decl = declarations().declarationTemp(t, name);
            String value = evaluateExprList(list);

            String init = "{" + value + " }";
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
                .collect(Collectors.joining(", "));
        return value;
    }


    /*
   default String evaluate(ExprIndexer indexer) {
       return exprIndexing(types().type(indexer.getStructure()), indexer);
   }

   String exprIndexing(Type type, ExprIndexer indexer);

   default String exprIndexing(ListType type, ExprIndexer indexer) {
       return String.format("%s[%s]", evaluate(indexer.getStructure()), evaluate(indexer.getIndex()));
   }
*/

    default String evaluate(ExprIndexer indexer) {
        VarDecl varDecl = evalExprIndexVar(indexer);

        Optional<String> str = Optional.empty();
        String ind;
        if (indexer.getStructure() instanceof ExprIndexer) {

            Type t = backend().types().declaredType(varDecl);
            ListType listType = null;
            if (t instanceof ListType) {
                listType = (ListType) t;
            } else if (t instanceof RefType) {
                listType = (ListType) ((RefType) t).getType();
            }

            List<Integer> sizeByDim = typeseval().sizeByDimension((ListType) listType.getElementType());
            List<String> indexByDim = getListIndexes((ExprIndexer) indexer.getStructure());
            Collections.reverse(indexByDim);

            List<String> structureIndex = new ArrayList<>();
            for (int i = 0; i < indexByDim.size(); i++) {
                List<String> dims = new ArrayList<>();
                for (int j = i; j < sizeByDim.size(); j++) {
                    dims.add(Integer.toString(sizeByDim.get(j)));
                }
                structureIndex.add(String.format("%s*%s", String.join("*", dims), indexByDim.get(i)));
            }
            str = Optional.of(String.join(" + ", structureIndex));
        }

        if (indexer.getIndex() instanceof ExprIndexer) {
            ind = String.format("%s", evaluate(indexer.getIndex()));
        } else {
            ind = evaluate(indexer.getIndex());
        }

        if (str.isPresent()) {
            return String.format("%s[%s + %s]", variables().name(varDecl), str.get(), ind);
        } else {
            return String.format("%s[%s]", variables().name(varDecl), ind);
        }
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

    default String evaluate(ExprNth nth) {
        Type type = types().type(nth.getStructure());
        if (type instanceof TupleType) {
            return String.format("std::get<%s>(%s)",  nth.getNth().getNumber() - 1, evaluate(nth.getStructure()));
        }
        // -- See if there is something else
        return String.format("%s->%s", evaluate(nth.getStructure()), "_" + nth.getNth().getNumber());
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
        emitter().emit("%s", decl);
        //emitter().emit("%s = %s;", decl, backend().defaultValues().defaultValue(type));
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
        boolean directlyCallable = backend().callablesInActor().directlyCallable(apply.getFunction());
        String fn;
        List<String> parameters = new ArrayList<>();

        if (!directlyCallable) {
            parameters.add("thisActor");
        }

        if (!backend().profilingbox().isEmpty()) {
            boolean isExternal = false;
            if (apply.getFunction() instanceof ExprGlobalVariable) {
                VarDecl declaration = backend().varDecls().declaration((ExprGlobalVariable) apply.getFunction());
                isExternal = declaration.isExternal();
            }

            if (!isExternal)
                parameters.add("__opCounters");
        }

        for (Expression parameter : apply.getArgs()) {
            if (parameter instanceof ExprList) {
                parameters.add(evaluateOnlyValue((ExprList) parameter));
            } else {
                if (parameter instanceof ExprGlobalVariable){
                    VarDecl decl = backend().varDecls().declaration((ExprGlobalVariable) parameter);
                    if(decl.getValue() instanceof ExprList){
                        parameters.add(evaluateOnlyValue((ExprList) decl.getValue()));
                    }else{
                        parameters.add(evaluate(parameter));
                    }
                }else{
                    parameters.add(evaluate(parameter));
                }
            }
        }


        fn = evaluateCall(apply.getFunction());
        Type type = types().type(apply);
        String result = variables().generateTemp();
        String decl = declarations().declarationTemp(types().type(apply), result);
        if(type instanceof TensorType){
            emitter().emit("%s = %s(%s);", decl, fn, String.join(", ", parameters));
        }else{
            emitter().emit("%s = %s(%s);", decl, fn, String.join(", ", parameters));
        }
        backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_CALL += 1;");
        return result;
    }

    default String evaluateOnlyValue(ExprList list) {
        ListType t = (ListType) types().type(list);
        Type elementType = t.getElementType();
        String value = list.getElements().stream().sequential()
                .map(element -> {
                    if (elementType instanceof AlgebraicType || backend().alias().isAlgebraicType(elementType)) {
                        String tmp = variables().generateTemp();
                        emitter().emit("%s;", backend().declarations().declaration(elementType, tmp));
                        //emitter().emit("%s = %s;", backend().declarations().declaration(elementType, tmp), backend().defaultValues().defaultValue(elementType));
                        backend().statements().copy(elementType, tmp, elementType, evaluate(element));
                        return tmp;
                    }
                    return evaluate(element);
                })
                .collect(Collectors.joining(", ", "{", "}"));
        return value;
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
                            emitter().emit("%s;", backend().declarations().declaration(elementType, tmp));
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
            emitter().emit("%s;", declarations().declaration(type, name));
            //emitter().emit("%s = %s;", declarations().declaration(type, name), backend().defaultValues().defaultValue(type));
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

    default String evaluate(ExprTuple tuple) {
        List<String> parameters = new ArrayList<>();
        for (Expression parameter : tuple.getElements()) {
            parameters.add(evaluate(parameter));
        }
        String result = variables().generateTemp();
        String decl = backend().declarations().declaration(types().type(tuple), result);
        emitter().emit("%s = std::make_tuple(%s);", decl, String.join(", ", parameters));
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
