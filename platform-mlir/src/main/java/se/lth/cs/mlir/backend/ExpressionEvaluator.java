package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.StackSSA;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtCall;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.*;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Module
public interface ExpressionEvaluator {

    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

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

    default StackSSA ssaValueNumberingStack() {
        return backend().ssaValueNumberingStack();
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


    default String evaluate(Expression expr) {
        throw new UnsupportedOperationException("ExpressionEvaluator.evaluate() function not supported for: " + expr.getClass());
    }


    /**
     * Evaluate expression variable
     *
     * @param variable
     * @return
     */
    default String evaluate(ExprVariable variable) {
        VarDecl decl = backend().varDecls().declaration(variable);
        /*if (!(decl.getValue() instanceof ExprInput)) {
            throw new Error("ExprInput not implemented");
            /*IRNode parent = backend().tree().parent(decl);
            if ((parent instanceof Scope) || (parent instanceof ActorMachine) || (parent instanceof NamespaceDecl)) {
                Type type = backend().types().type(decl.getType());
                if (type instanceof ListType) {
                    backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_LIST_LOAD += 1;");
                } else {
                    backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_LOAD += 1;");
                }
            }
        }*/
        String variableName = ssaValueNumberingStack().getVarName(variables().name(variable.getVariable()));
        //String outputName = ssaValueNumberingStack().getNewTempVar();
        //generateNOPEquivalentOperation(types().type(variable), variableName, outputName);
        return variableName;
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

    /*
     * Evaluate a dereference expression
     *
     * @param deref
     * @return
     */
    /*default String evaluate(ExprDeref deref) {
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
    }*/

    /*
     * Evaluate an expression Globals variable
     *
     * @param variable
     * @return
     */
    /*default String evaluate(ExprGlobalVariable variable) {
        return variables().globalName(variable);
    }*/

    /**
     * Evaluate expression literal
     *
     * @param literal
     * @return
     */

    default String evaluate(ExprLiteral literal) {
        String tempName = ssaValueNumberingStack().getNewTempVar();
        switch (literal.getKind()) {
            case Integer:
                emitter().emit("%%%s = arith.constant %s : %s", tempName, literal.getText(),
                        typeseval().type(types().type(literal)));
                return tempName;
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

    /*
     * Evaluate expression input
     *
     * @param input
     * @return
     */
    /*default String evaluate(ExprInput input) {
        String tmp = variables().generateTemp();
        Type type = types().type(input);
        //if (input.hasRepeat()) {
        //    String maxIndex = typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect
        (Collectors.joining("*"));
        //    emitter().emit("%s = malloc(sizeof(%s) * (%s));", declarations().declaration(types().type(input), tmp),
         maxIndex);
        //} else {
        emitter().emit("%s = %s;", declarations().declarationTemp(types().type(input), tmp), backend().defaultValues
        ().defaultValue(types().type(input)));
        //}
        if (type instanceof AlgebraicType) {
            // memoryStack().trackPointer(tmp, type);
        }

        if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), input
        .getPort().getName())) {
            if (input.hasRepeat()) {
                if (input.getOffset() == 0) {
                    emitter().emit("pinPeekRepeat_%s(%s, %s, %d);", channelsutils().inputPortTypeSize(input.getPort()
                    ), channelsutils().definedInputPort(input.getPort()), tmp, input.getRepeat());
                } else {
                    throw new RuntimeException("not implemented");
                }
            } else {
                if (input.getOffset() == 0) {
                    emitter().emit("%s = pinPeekFront_%s(%s);", tmp, channelsutils().inputPortTypeSize(input.getPort
                    ()), channelsutils().definedInputPort(input.getPort()));
                } else {
                    emitter().emit("%s = pinPeek_%s(%s, %d);", tmp, channelsutils().inputPortTypeSize(input.getPort()
                    ), channelsutils().definedInputPort(input.getPort()), input.getOffset());
                }
            }
        }

        return tmp;
    }*/

    void evaluateWithLvalue(String lvalue, Expression expr);

    default void evaluateWithLvalue(String lvalue, ExprInput input) {
        Type type = types().type(input);
        String sType = backend().typeseval().type(type);

        if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(),
                input.getPort().getName())) {
            if (input.hasRepeat()) {
                throw new RuntimeException("not implemented");
                //if (input.getOffset() == 0) {
                //    emitter().emit("pinPeekRepeat_%s(%s, %s, %d);", sType, channelsutils().definedInputPort(input
                //    .getPort()), lvalue, input.getRepeat());
                //} else {
                //    throw new RuntimeException("not implemented");
                //}
            } else {
                //if (input.getOffset() == 0) {
                String lValueSSA = ssaValueNumberingStack().getVarToBeAssignedTo(lvalue);
                emitter().emit("%%%s = dfg.pull %%%s : %s", lValueSSA, input.getPort().getName(),
                        typeseval().type(type));
                //emitter().emit("%s = dfg.pull %s(%s);", lvalue, sType, channelsutils().definedInputPort(input
                // .getPort()));
                //} else {
                //    throw new UnsupportedOperationException("Popping values not off the front of the queue is not
                //    yet supported");
                //    //emitter().emit("%s = pinPeek_%s(%s, %d);", lvalue, sType, channelsutils().definedInputPort
                //    (input.getPort()), input.getOffset());
                //}
            }
        }
    }

    /*default String compare(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        return String.format("(%s == %s)", lvalue, rvalue);
    }

    default String compare(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        String index = variables().generateTemp();
        emitter().emit("%s = true;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("for (size_t %1$s = 0; (%1$s < %2$s) && %3$s; %1$s++) {", index, lvalueType.getSize().getAsInt
        (), tmp);
        emitter().increaseIndentation();
        emitter().emit("%s &= %s;", tmp, compare(lvalueType.getElementType(), String.format("%s.data[%s]", lvalue,
        index), rvalueType.getElementType(), String.format("%s.data[%s]", rvalue, index)));
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
        emitter().emit("%s = compare_%s(%s, %s);", declarations().declaration(BoolType.INSTANCE, tmp), backend()
        .algebraic().utils().name(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(AliasType lvalueType, String lvalue, AliasType rvalueType, String rvalue) {
        return compare(lvalueType.getType(), lvalue, rvalueType.getType(), rvalue);
    }*/

    /**
     * Evaluate binary expression
     *
     * @param binaryOp
     * @return
     */
    default String evaluate(ExprBinaryOp binaryOp) {
        assert binaryOp.getOperations().size() == 1 && binaryOp.getOperands().size() == 2;
        String operation = binaryOp.getOperations().get(0);
        CommonTypeStruct convertedOperands = convertBinaryExprTypes(binaryOp);

        switch (operation) {
            case "+":
                return evaluateBinaryAdd(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "-":
                return evaluateBinarySub(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "*":
                return evaluateBinaryTimes(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "/":
                return evaluateBinaryDiv(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "div":
                return evaluateBinaryIntDiv(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "%":
            case "mod":
                return evaluateBinaryMod(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "^":
                return evaluateBinaryBitXor(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "&":
                return evaluateBinaryBitAnd(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "<<":
                return evaluateBinaryShiftL(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case ">>":
                return evaluateBinaryShiftR(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "&&":
            case "and":
                //throw new UnsupportedOperationException(operation);
                return evaluateBinaryAnd(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "|":
                return evaluateBinaryBitOr(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "||":
            case "or":
                return evaluateBinaryOr(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "=":
            case "==":
                return evaluateBinaryEq(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "!=":
                return evaluateBinaryNEq(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "<":
                return evaluateBinaryLtn(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "<=":
                return evaluateBinaryLeq(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case ">":
                return evaluateBinaryGtn(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case ">=":
                return evaluateBinaryGeq(convertedOperands.commonType, convertedOperands.lhsOperand,
                        convertedOperands.rhsOperand);
            case "in":
                throw new UnsupportedOperationException(operation);
                //return evaluateBinaryIn(lhs, rhs, binaryOp);
            default:
                throw new UnsupportedOperationException(operation);
        }
    }

    default CommonTypeStruct convertBinaryExprTypes(ExprBinaryOp binaryOp) {
        //Type commonType = types().type(binaryOp); //typeseval().getCommonType(lhs, rhs);
        Type lhs = types().type(binaryOp.getOperands().get(0));
        Type rhs = types().type(binaryOp.getOperands().get(1));
        Type commonType = getExpressionType(binaryOp);
        String lhsTempVar = evaluate(binaryOp.getOperands().get(0));
        String rhsTempVar = evaluate(binaryOp.getOperands().get(1));
        lhsTempVar = typeseval().castType(lhs, commonType, lhsTempVar);
        rhsTempVar = typeseval().castType(rhs, commonType, rhsTempVar);
        return new CommonTypeStruct(commonType, lhsTempVar, rhsTempVar);
    }

    default String evaluateBinaryAdd(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryAdd(IntType type, String lhsOperand, String rhsOperand) {
        String output = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.addi %%%s, %%%s : %s", output, lhsOperand, rhsOperand, typeseval().type(type));
        return output;
    }

    /*default String evaluateBinaryAdd(NumberType lhs, NumberType rhs, ExprBinaryOp binaryOp) {
        //emitter().emit("%%%s = arith.addi %%%s, %%%s : %s", tempOutput, lhsTempVar, rhsTempVar, type);
        return  "";
    }*/

    /*default String evaluateBinaryAdd(SetType lhs, SetType rhs, ExprBinaryOp binaryOp) {
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
        emitter().emit("%1$s = concat_%2$s_%2$s(%3$s, %4$s);", tmp, typeseval().type(lhs), evaluate(left), evaluate
        (right));
        return tmp;
    }

    default String evaluateBinaryAdd(Type lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(rhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(lhs), typeseval().type(rhs),
        evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, Type rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(lhs), typeseval().type(rhs),
        evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(RealType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(rhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(RealType.f64), typeseval().type
        (rhs), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, RealType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(lhs), typeseval().type(RealType
        .f64), evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(IntType lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Type type = lhs.isSigned() ? new IntType(OptionalInt.empty(), true) : new IntType(OptionalInt.empty(), false);
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(rhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(type), typeseval().type(rhs),
        evaluate(left), evaluate(right));
        return tmp;
    }

    default String evaluateBinaryAdd(StringType lhs, IntType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Type type = rhs.isSigned() ? new IntType(OptionalInt.empty(), true) : new IntType(OptionalInt.empty(), false);
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(lhs, tmp));
        emitter().emit("%1$s = concat_%2$s_%3$s(%4$s, %5$s);", tmp, typeseval().type(lhs), typeseval().type(type),
        evaluate(left), evaluate(right));
        return tmp;
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

    default String evaluateBinaryIn(Type lhs, Type rhs, ExprBinaryOp binaryOp) {
        throw new UnsupportedOperationException(binaryOp.getOperations().get(0));
    }

    default String evaluateBinaryIn(Type lhs, ListType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        String index = variables().generateTemp();
        String elem = evaluate(binaryOp.getOperands().get(0));
        String list = evaluate(binaryOp.getOperands().get(1));
        emitter().emit("%s = false;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("for (size_t %1$s = 0; (%1$s < %2$s) && !(%3$s); %1$s++) {", index, rhs.getSize().getAsInt(),
        tmp);
        emitter().increaseIndentation();
        emitter().emit("%s |= %s;", tmp, compare(lhs, elem, rhs.getElementType(), String.format("%s.data[%s]", list,
        index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, SetType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, typeseval().type(rhs), evaluate(left), evaluate
        (right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, MapType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, typeseval().type(rhs), evaluate(left), evaluate
        (right));
        return tmp;
    }

    default String evaluateBinaryIn(Type lhs, StringType rhs, ExprBinaryOp binaryOp) {
        String tmp = variables().generateTemp();
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = membership_%2$s(%4$s, %3$s);", tmp, typeseval().type(rhs), evaluate(left), evaluate
        (right));
        return tmp;
    }*/

    default String evaluateBinarySub(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinarySub(IntType type, String lhsOperand, String rhsOperand) {
        String output = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.subi %%%s, %%%s : %s", output, lhsOperand, rhsOperand, typeseval().type(type));
        return output;
    }

    default String evaluateBinaryTimes(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryTimes(IntType type, String lhsOperand, String rhsOperand) {
        String output = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.muli %%%s, %%%s : %s", output, lhsOperand, rhsOperand, typeseval().type(type));
        return output;
    }

    default String evaluateBinaryDiv(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryDiv(IntType type, String lhsOperand, String rhsOperand) {
        String output = ssaValueNumberingStack().getNewTempVar();
        if (type.isSigned())
            emitter().emit("%%%s = arith.divsi %%%s, %%%s : %s", output, lhsOperand, rhsOperand,
                    typeseval().type(type));
        else
            emitter().emit("%%%s = arith.divui %%%s, %%%s : %s", output, lhsOperand, rhsOperand,
                    typeseval().type(type));
        return output;
    }

    default String evaluateBinaryIntDiv(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryIntDiv(IntType type, String lhsOperand, String rhsOperand) {
        return evaluateBinaryDiv(type, lhsOperand, rhsOperand);
    }

    default String evaluateBinaryMod(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryMod(IntType type, String lhsOperand, String rhsOperand) {
        String output = ssaValueNumberingStack().getNewTempVar();
        if (type.isSigned())
            emitter().emit("%%%s = arith.remsi %%%s, %%%s : %s", output, lhsOperand, rhsOperand,
                    typeseval().type(type));
        else
            emitter().emit("%%%s = arith.remui %%%s, %%%s : %s", output, lhsOperand, rhsOperand,
                    typeseval().type(type));
        return output;
    }

    default String evaluateBinaryBitXor(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryBitXor(IntType type, String lhsOperand, String rhsOperand) {
        String output = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.xori %%%s, %%%s : %s", output, lhsOperand, rhsOperand, typeseval().type(type));
        return output;
    }

    default String evaluateBinaryBitAnd(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryBitAnd(IntType type, String lhsOperand, String rhsOperand) {
        String output = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.andi %%%s, %%%s : %s", output, lhsOperand, rhsOperand, typeseval().type(type));
        return output;
    }

    default String evaluateBinaryBitOr(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryBitOr(IntType type, String lhsOperand, String rhsOperand) {
        String output = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.ori %%%s, %%%s : %s", output, lhsOperand, rhsOperand, typeseval().type(type));
        return output;
    }

    default String evaluateBinaryEq(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryEq(IntType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.cmpi eq, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                typeseval().type(type));
        return tempResult;
    }

    default String evaluateBinaryNEq(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryNEq(IntType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.cmpi ne, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                typeseval().type(type));
        return tempResult;
    }

    default String evaluateBinaryLtn(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryLtn(IntType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        if (type.isSigned())
            emitter().emit("%%%s = arith.cmpi slt, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        else
            emitter().emit("%%%s = arith.cmpi ult, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        return tempResult;
    }

    default String evaluateBinaryLeq(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryLeq(IntType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        if (type.isSigned())
            emitter().emit("%%%s = arith.cmpi sle, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        else
            emitter().emit("%%%s = arith.cmpi ule, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        return tempResult;
    }

    default String evaluateBinaryGtn(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryGtn(IntType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        if (type.isSigned())
            emitter().emit("%%%s = arith.cmpi sgt, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        else
            emitter().emit("%%%s = arith.cmpi ugt, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        return tempResult;
    }

    default String evaluateBinaryGeq(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryGeq(IntType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        if (type.isSigned())
            emitter().emit("%%%s = arith.cmpi sge, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        else
            emitter().emit("%%%s = arith.cmpi uge, %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        return tempResult;
    }

    default String evaluateBinaryAnd(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryAnd(BoolType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.andi %%%s, %%%s : i1", tempResult, lhsOperand, rhsOperand);
        return tempResult;
    }

    default String evaluateBinaryOr(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryOr(BoolType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.ori %%%s, %%%s : i1", tempResult, lhsOperand, rhsOperand);
        return tempResult;
    }

    default String evaluateBinaryShiftR(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryShiftR(IntType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        if (type.isSigned()) {
            emitter().emit("%%%s = arith.shrsi %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        } else {
            emitter().emit("%%%s = arith.shrui %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                    typeseval().type(type));
        }
        return tempResult;
    }

    default String evaluateBinaryShiftL(Type type, String lhsOperand, String rhsOperand) {
        throw new UnsupportedOperationException();
    }

    default String evaluateBinaryShiftL(IntType type, String lhsOperand, String rhsOperand) {
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.shli %%%s, %%%s : %s", tempResult, lhsOperand, rhsOperand,
                typeseval().type(type));
        return tempResult;
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

    default String evaluateUnaryMinus(IntType type, ExprUnaryOp expr) {
        String ssaName = evaluate(expr.getOperand());
        String zeroConstant = ssaValueNumberingStack().getNewTempVar();
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        String typeString = typeseval().type(type);
        emitter().emit("%%%s = arith.constant 0 : %s", zeroConstant, typeString);
        emitter().emit("%%%s = arith.subi %%%s, %%%s : %s", tempResult, zeroConstant, ssaName, typeString);
        return tempResult;
    }

    default String evaluateUnaryInvert(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    default String evaluateUnaryInvert(IntType type, ExprUnaryOp expr) {
        // No unary not in MLIR arith dialect for integer. XOR with binary 0b111111... should produce the same result.
        // MLIR can read hex but not binary. So we generate this binary string and then convert it to hex.

        // Create string containing the binary representations the integer of size type.getSize() where all bits are 1.
        char[] charArray = new char[type.getSize().orElse(32)];
        Arrays.fill(charArray, '1');
        String binaryStr = new String(charArray);

        // Convert the binary string to a string in hex representations
        String hexStr = new BigInteger(binaryStr, 2).toString(16);

        // Now generate the MLIR
        String ssaName = evaluate(expr.getOperand());
        String onesConstant = ssaValueNumberingStack().getNewTempVar();
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        String typeString = typeseval().type(type);
        emitter().emit("%%%s = arith.constant 0x%s : %s", onesConstant, hexStr, typeString);
        emitter().emit("%%%s = arith.xori %%%s, %%%s : %s", tempResult, ssaName, onesConstant, typeString);
        return tempResult;
    }

    default String evaluateUnaryNot(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    default String evaluateUnaryNot(BoolType type, ExprUnaryOp expr) {
        // No unary not in MLIR arith dialect for integer. XOR with 1 should produce the same result

        String ssaName = evaluate(expr.getOperand());
        String oneConstant = ssaValueNumberingStack().getNewTempVar();
        String tempResult = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.constant 1 : i1", oneConstant);
        emitter().emit("%%%s = arith.xori %%%s, %%%s : i1", tempResult, ssaName, oneConstant);
        return tempResult;
    }

    default String evaluateUnaryDom(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    /*default String evaluateUnaryDom(MapType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = domain_%s(%s);", declarations().declaration(types().type(expr), tmp),
                typeseval().type(type), evaluate(expr.getOperand()));
        return tmp;
    }*/

    default String evaluateUnaryRng(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    /*default String evaluateUnaryRng(MapType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = range_%s(%s);", declarations().declaration(types().type(expr), tmp),
                typeseval().type(type), evaluate(expr.getOperand()));
        return tmp;
    }*/

    default String evaluateUnarySize(Type type, ExprUnaryOp expr) {
        throw new UnsupportedOperationException(expr.getOperation());
    }

    /*default String evaluateUnarySize(ListType type, ExprUnaryOp expr) {
        return "" + type.getSize().getAsInt();
    }*/

    /*default String evaluateUnarySize(SetType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s->size;", declarations().declaration(types().type(expr), tmp),
                evaluate(expr.getOperand()));
        return tmp;
    }*/

    /*default String evaluateUnarySize(MapType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s->size;", declarations().declaration(types().type(expr), tmp),
                evaluate(expr.getOperand()));
        return tmp;
    }*/

    /*default String evaluateUnarySize(StringType type, ExprUnaryOp expr) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = strlen(%s);", declarations().declaration(types().type(expr), tmp),
                evaluate(expr.getOperand()));
        return tmp;
    }*/

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
                        emitter().emit("memcpy(%s + %s*(%s++), %s, sizeof(%4$s));", result, type.getSize().getAsInt()
                                , index, name);
                    } else {
                        emitter().emit("%s[%s++] = %s;", result, index, evaluate(element));
                    }
                }
        );
    }

    /*
     * This is a call expression when a function is called
     */
    default String evaluate(ExprApplication exprApplication) {
        String ssaTemp = ssaValueNumberingStack().getNewTempVar();
        if (backend().callablesInActor().directlyCallable(exprApplication.getFunction())) {
            List<String> paramNames = new ArrayList<>();
            List<String> paramTypes = new ArrayList<>();

            for (Expression parameter : exprApplication.getArgs()) {
                String argName = "%" + evaluate(parameter);
                paramNames.add(argName);
                String argType = typeseval().type(types().type(parameter));
                paramTypes.add(argType);
            }
            String paramNamesString = String.join(",", paramNames);
            String paramTypesString = String.join(",", paramTypes);

            String funcName = evaluateCall(exprApplication.getFunction());
            String funcReturnType = typeseval().type(types().type(exprApplication));

            emitter().emit("%%%s = func.call @%s(%s) : (%s) -> %s", ssaTemp, funcName, paramNamesString,
                    paramTypesString, funcReturnType);
        } else {
            throw new UnsupportedOperationException("Function is not directly callable");
        }
        return ssaTemp;
    }

    void withGenerator(Expression collection, ImmutableList<GeneratorVarDecl> varDecls, Runnable body);

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


    /*
   default String evaluate(ExprIndexer indexer) {
       return exprIndexing(types().type(indexer.getStructure()), indexer);
   }

   String exprIndexing(Type type, ExprIndexer indexer);

   default String exprIndexing(ListType type, ExprIndexer indexer) {
       return String.format("%s[%s]", evaluate(indexer.getStructure()), evaluate(indexer.getIndex()));
   }
*/

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
            emitter().emit("%s = %s;", declarations().declaration(type, name),
                    backend().defaultValues().defaultValue(type));
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
        emitter().emit("%s = (%s)(%s);", decl, typeseval().type(type) + (type instanceof AlgebraicType ? "*" : ""),
                evaluate(assertion.getExpression()));
        return result;

    }

    default String evaluate(ExprField field) {
        return String.format("%s->members.%s", evaluate(field.getStructure()), field.getField().getName());
    }

    /**
     * Sometimes we just want to assign an operand directly to a result (%a = %b) but MLIR does support this. So we
     * generate a execute an operation on the operand that will not affect it and assign that to the result
     *
     * @param type          The type of the input
     * @param inputOperand  The SSA name of the input operand
     * @param outputOperand The SSA name of the input operand
     * @return none
     */
    default void generateNOPEquivalentOperation(Type type, String inputOperand, String outputOperand) {
        throw new Error("No MLIR NOP equivalent for type: " + type);
    }

    default void generateNOPEquivalentOperation(NumberType type, String inputOperand, String outputOperand) {
        String typeAsString = typeseval().type(type);
        emitter().emit("%%%s = arith.bitcast %%%s: %s to %s", outputOperand, inputOperand, typeAsString, typeAsString);
    }

    default Type getExpressionType(Expression expr) {
        return types().type(expr);
    }

    default Type getExpressionType(ExprBinaryOp expr) {
        Type lhs = types().type(expr.getOperands().get(0));
        Type rhs = types().type(expr.getOperands().get(1));
        Type commonType = typeseval().getCommonType(lhs, rhs);
        return commonType;
    }

    public class CommonTypeStruct {
        public Type commonType;
        public String lhsOperand;
        public String rhsOperand;

        public CommonTypeStruct(Type commonType, String lhsOperand, String rhsOperand) {
            this.commonType = commonType;
            this.lhsOperand = lhsOperand;
            this.rhsOperand = rhsOperand;
        }

        public String toString() {
            return "Type: " + commonType + ", lhs operand name: " + lhsOperand + ", rhs operand name: " + rhsOperand;
        }
    }
}
