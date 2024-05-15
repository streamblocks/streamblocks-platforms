package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.StackSSA;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.*;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueIndexer;
import se.lth.cs.tycho.type.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.multij.BindingKind.LAZY;

@Module
public interface Statements {
    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default ExpressionEvaluator expressioneval() {
        return backend().expressionEval();
    }

    default LValues lvalues() {
        return backend().lvalues();
    }

    default Types types() {
        return backend().types();
    }

    default Variables variables() {
        return backend().variables();
    }

    default Declarations declarartions() {
        return backend().declarations();
    }

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default ChannelsUtils channelsutils() {
        return backend().channelsutils();
    }

    @Binding(LAZY)
    default StackSSA ssaValueNumberingStack() {
        return backend().ssaValueNumberingStack();
    }

    void execute(Statement stmt);

    @Binding(BindingKind.LAZY)
    default List profilingOp() {
        return new ArrayList<String>();
    }


    /*
     * Statement Consume
     */

    default void execute(StmtConsume consume) {
        System.out.println("StmtConsume");
        emitter().emit("// StmtConsume not implemented: consume happens on peaking right now");
        /*throw new UnsupportedOperationException("StmtConsume not implemented in MLIR.");/*
        if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), consume
        .getPort().getName())) {
            if (consume.getNumberOfTokens() > 1) {
                emitter().emit("pinConsumeRepeat_%s(%s, %d);", channelsutils().inputPortTypeSize(consume.getPort()),
                channelsutils().definedInputPort(consume.getPort()), consume.getNumberOfTokens());
                backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_LIST_LOAD += 1;");
            } else {
                emitter().emit("pinConsume_%s(%s);", channelsutils().inputPortTypeSize(consume.getPort()),
                channelsutils().definedInputPort(consume.getPort()));
                backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_LOAD += 1;");
            }
        }*/
    }

    /*
     * Statement Write
     */

    default void execute(StmtWrite write) {
        emitter().emit("// Stmt Write");
        System.out.println("Stmt Write");
        if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), write
                .getPort().getName())) {
            if (write.getRepeatExpression() != null) {
                throw new Error("not implemented");
            }

            String tempVar = "";
            if (write.getValues().size() > 1) {
                throw new Error("Not able to handle size greater than 1 yet");
            }
            for (Expression expr : write.getValues()) {
                tempVar = expressioneval().evaluate(expr);
                //emitter().emit("%%%s = %%%s", tempVar, expressioneval().evaluate(expr));
                //emitter().emit("pinWrite_%s(%s, %s);", portType, channelsutils().definedOutputPort(write.getPort
                //        ()), tmp);
            }
            Type type = types().portType(write.getPort());
            String portType = typeseval().type(type);
            String portName = write.getPort().getName();


            emitter().emit("dfg.push(%%%s) %%%s : %s", tempVar, portName, portType);
        }

        /*if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), write
        .getPort().getName())) {
            if (write.getRepeatExpression() == null) {
                Type type = types().portType(write.getPort());
                String portType;
                if (type instanceof AlgebraicType) {
                    portType = "ref";

                } else {
                    portType = typeseval().type(type);

                }
                String tmp = variables().generateTemp();
                emitter().emit("%s = %s;", declarartions().declaration(types().portType(write.getPort()), tmp),
                backend().defaultValues().defaultValue(type));
                for (Expression expr : write.getValues()) {
                    emitter().emit("%s = %s;", tmp, expressioneval().evaluate(expr));
                    emitter().emit("pinWrite_%s(%s, %s);", portType, channelsutils().definedOutputPort(write.getPort
                    ()), tmp);
                    profilingOp().add("__opCounters->prof_DATAHANDLING_STORE += 1;");
                }
            } else if (write.getValues().size() == 1) {
                Type valueType = types().type(write.getValues().get(0));
                Type portType = channelsutils().outputPortType(write.getPort());
                String value = expressioneval().evaluate(write.getValues().get(0));
                String repeat = expressioneval().evaluate(write.getRepeatExpression());

                // -- Hack type conversion : to be fixed
                if (valueType instanceof ListType) {
                    ListType listType = (ListType) valueType;
                    if (!listType.getElementType().equals(portType)) {
                        String index = variables().generateTemp();
                        emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, repeat);
                        emitter().emit("\tpinWrite_%s(%s, %s[%s]);", channelsutils().outputPortTypeSize(write.getPort
                        ()), channelsutils().definedOutputPort(write.getPort()), value, index);
                        profilingOp().add("__opCounters->prof_DATAHANDLING_STORE += 1;");
                        emitter().emit("}");
                    } else {
                        emitter().emit("pinWriteRepeat_%s(%s, %s, %s);", channelsutils().outputPortTypeSize(write
                        .getPort()), channelsutils().definedOutputPort(write.getPort()), value, repeat);
                        profilingOp().add("__opCounters->prof_DATAHANDLING_LIST_STORE += 1;");
                    }
                } else {
                    emitter().emit("pinWriteRepeat_%s(%s, %s, %s);", channelsutils().outputPortTypeSize(write.getPort
                    ()), channelsutils().definedOutputPort(write.getPort()), value, repeat);
                    profilingOp().add("__opCounters->prof_DATAHANDLING_LIST_STORE += 1;");
                }

            } else {
                throw new Error("not implemented");
            }
        }*/
    }

    /*
     * Statement Assign
     */

    default void execute(StmtAssignment assign) {
        System.out.println("StmtAssignment");
        emitter().emit("// Assignment Statement: Start");
        /*Type type = types().type(assign.getLValue());
        String lvalue = lvalues().lvalue(assign.getLValue());
        //if ((type instanceof ListType && assign.getLValue() instanceof LValueVariable) && !(assign.getExpression()
        instanceof ExprList)) {
        //if (assign.getExpression() instanceof ExprComprehension) {
        //    expressioneval().evaluate(assign.getExpression());
        //} else {
        if (assign.getLValue() instanceof LValueIndexer) {
            LValueIndexer indexer = (LValueIndexer) assign.getLValue();
            if (lvalues().subIndexAccess(indexer)) {
                String varName = variables().name(lvalues().evalLValueIndexerVar(indexer));
                String index = lvalues().singleDimIndex(indexer);

                emitter().emit("{");
                emitter().increaseIndentation();
                String eval = expressioneval().evaluate(assign.getExpression());
                Type exprType = types().type(assign.getExpression());

                copySubAccess((ListType) type, varName, (ListType) exprType, eval, index);
                emitter().decreaseIndentation();
                emitter().emit("}");
            } else {
                if (assign.getExpression() instanceof ExprComprehension) {
                    emitter().emit("{");
                    emitter().increaseIndentation();
                    String eval = expressioneval().evaluate(assign.getExpression());
                    copy(type, lvalue, types().type(assign.getExpression()), eval);
                    emitter().decreaseIndentation();
                    emitter().emit("}");
                } else {
                    copy(type, lvalue, types().type(assign.getExpression()), expressioneval().evaluate(assign
                    .getExpression()));
                }
            }
        } else {
            if (assign.getExpression() instanceof ExprComprehension) {
                emitter().emit("{");
                emitter().increaseIndentation();
                String eval = expressioneval().evaluate(assign.getExpression());
                copy(type, lvalue, types().type(assign.getExpression()), eval);
                emitter().decreaseIndentation();
                emitter().emit("}");
            } else {
                copy(type, lvalue, types().type(assign.getExpression()), expressioneval().evaluate(assign
                .getExpression()));
            }
        }
        //}
        profilingOp().add("__opCounters->prof_DATAHANDLING_ASSIGN += 1;");*/

        if (assign.getLValue() instanceof LValueIndexer) {
            throw new Error("LValueIndexer functionality not implemented in execute(StmtAssignment)");
        }

        if (assign.getExpression() instanceof ExprComprehension) {
            throw new Error("ExprComprehension functionality not implemented in execute(StmtAssignment)");
        }

        String lvalue = lvalues().lvalue(assign.getLValue());
        Type type = types().type(assign.getLValue());
        assign(type, lvalue, assign.getExpression());
        /*default void assign(Type lvalueType, String lvalue, Expression expr) {
            Type inputType = types().type(expr);
            Type outputType = typeseval().getCommonType(lvalueType, types().type(expr));
            String rvalueTemp = expressioneval().evaluate(expr);
            String rvalue = typeseval().castType(inputType,outputType,rvalueTemp);

            expressioneval().generateNOPEquivalentOperation(outputType, rvalue, lvalue);
            //emitter().emit("%%%s = %s : %s", lvalue, rvalue , backend().typeseval().type(lvalueType));
        }*/

        emitter().emit("// Assignment Statement: End");
    }

    default void copy(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        String mlirOp = backend().typeseval().mlirTypeConstantInstruction(lvalueType);
        emitter().emit("%%%s = %s %s : %s", lvalue, mlirOp, rvalue, backend().typeseval().type(lvalueType));
    }


    /*default void copy(IntType lvalueType, String lvalue, IntType rvalueType, String rvalue) {
        String mlirOp = "";
        if(rvalue.matches("[0-9]+")){
            mlirOp = backend().typeseval().mlirTypeConstantInstruction(lvalueType);
        }
        emitter().emit("%%%s = %s %s : %s", lvalue, mlirOp , rvalue , backend().typeseval().type(lvalueType));
    }*/

    default void copy(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue) {
        //if (!lvalueType.equals(rvalueType)) {
        String maxIndex =
                typeseval().sizeByDimension(lvalueType).stream().map(Object::toString).collect(Collectors.joining(" *" +
                        " "));
        String index = variables().generateTemp();
        emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, maxIndex);
        emitter().increaseIndentation();
        emitter().emit("%s[%s] = %s[%2$s];", lvalue, index, rvalue);
        emitter().decreaseIndentation();
        emitter().emit("}");
        //}
    }

    default void copySubAccess(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue,
                               String singleDimIndex) {
        //if (!lvalueType.equals(rvalueType)) {
        String maxIndex =
                typeseval().sizeByDimension(lvalueType).stream().map(Object::toString).collect(Collectors.joining(" *" +
                        " "));
        String index = variables().generateTemp();
        emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, maxIndex);
        emitter().increaseIndentation();
        emitter().emit("%1$s[%2$s + %3$s] = %4$s[%3$s];", lvalue, singleDimIndex, index, rvalue);
        emitter().decreaseIndentation();
        emitter().emit("}");
        //}
    }


    default void copy(SetType lvalueType, String lvalue, SetType rvalueType, String rvalue) {
        emitter().emit("copy_%1$s(&(%2$s), %3$s);", typeseval().type(lvalueType), lvalue, rvalue);
    }

    default void copy(MapType lvalueType, String lvalue, MapType rvalueType, String rvalue) {
        emitter().emit("copy_%1$s(&(%2$s), %3$s);", typeseval().type(lvalueType), lvalue, rvalue);
    }

    default void copy(StringType lvalueType, String lvalue, StringType rvalueType, String rvalue) {
        emitter().emit("copy_%1$s(&(%2$s), %3$s);", typeseval().type(lvalueType), lvalue, rvalue);
    }

    default void copy(AlgebraicType lvalueType, String lvalue, AlgebraicType rvalueType, String rvalue) {
        emitter().emit("copy_%s(&(%s), %s);", backend().algebraic().utils().name(lvalueType), lvalue, rvalue);
    }

    default void copy(AliasType lvalueType, String lvalue, AliasType rvalueType, String rvalue) {
        copy(lvalueType.getType(), lvalue, rvalueType.getType(), rvalue);
    }

    /*
     * Statement Call
     */

    default void execute(StmtCall call) {
        System.out.println("StmtCall");
        throw new UnsupportedOperationException("StmtCall not implemented in MLIR.");
        /*String proc;
        List<String> parameters = new ArrayList<>();
        boolean directlyCallable = backend().callablesInActor().directlyCallable(call.getProcedure());*/
/*
        if (directlyCallable.isPresent()) {
            proc = directlyCallable.get();
            parameters.add("NULL");
        } else {
            String name = expressioneval().evaluate(call.getProcedure());
            proc = name + ".f";
            parameters.add(name + ".env");
        }*/

       /* if (!directlyCallable) {
            parameters.add("thisActor");
        }
        proc = expressioneval().evaluateCall(call.getProcedure());

        for (Expression parameter : call.getArgs()) {
            parameters.add(expressioneval().evaluate(parameter));
        }

        emitter().emit("%s(%s);", proc, String.join(", ", parameters));
        profilingOp().add("__opCounters->prof_DATAHANDLING_CALL += 1;");*/
    }

    /*
     * Statement Block
     */
    default void execute(StmtBlock block) {
        System.out.println("StmtBlock");
        /*throw new UnsupportedOperationException("StmtBlock not implemented in MLIR.");*/
        emitter().emit("// Block Statement: Begin");
        //emitter().increaseIndentation();
        ssaValueNumberingStack().newBlock();
        emitter().emit("//     Variable declarations attached to block statement: Begin");
        for (VarDecl decl : block.getVarDecls()) {
            emitVarDecl(decl);
        }
        emitter().emit("//     Variable declarations attached to block statement: End");

        block.getStatements().forEach(this::execute);
        ssaValueNumberingStack().blockDone();
        emitter().emit("// Block Statement: End");

        //emitter().decreaseIndentation();
        //emitter().emit("}");
    }

    /**
     * Statement If - Defined using the MLIR structured control flow dialect (scf)
     * <p>
     * If statements have the form:
     * %x, %y = scf.if %b -> (f32, f32) {
     * .... %x_true = ...
     * .... %y_true = ...
     * .... scf.yield %x_true, %y_true : f32, f32
     * } else {
     * .... %x_false = ...
     * .... %y_false = ...
     * .... scf.yield %x_false, %y_false : f32, f32
     * }
     * <p>
     * Only operands returned by "scf.if" are accesible outside of the control flow blocks. The returned values
     * correspond to those returned in scf.yield. Much of the logic in the function goes to generating the correct
     * values in the yield and the scf return for variable assignments to ensure that they remain in scope.
     */
    default void execute(StmtIf stmt) {
        System.out.println("StmtIf");
        emitter().emit("// If Statement: Begin");
        String conditionVar = expressioneval().evaluate(stmt.getCondition());

        // Get every value that is assigned in to in the if statement
        // We need this as the mlir if statement y
        Set<LValue> assignedVars = getConditionalReturnLvalues(stmt);

        // For every assigned value, get the type and combine it into a single string. These are the return types for
        // the scb.if block
        String returnValuesTypes = assignedVars.stream()
                .map(x -> typeseval().type(types().type(x)))
                .collect(Collectors.joining(", "));

        // 1. Generate the conditions check

        // For every assigned value, get its SSA name and join together in a string. These are the return values
        // for the scf.if statement
        // getVarToBeAssignedBeforeBlockOpen(...) ensures that the SSA values are incremented correctly for when the
        // then/else blocks get generated. Requires that getVarToBeAssignedBeforeBlockClose() is called when this
        // statement is done.
        String returnValues = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarToBeAssignedBeforeBlockOpen(lvalues().lvalue(x)))
                .collect(Collectors.joining(", "));
        if (!returnValues.isEmpty())
            emitter().emit("%s = scf.if %%%s -> (%s) {", returnValues, conditionVar, returnValuesTypes);
        else
            emitter().emit("scf.if %%%s {", conditionVar);

        // 2. Generate the then branch
        emitter().increaseIndentation();
        ssaValueNumberingStack().newBlock();
        stmt.getThenBranch().forEach(this::execute);
        // The then block has to yield and return the values to be assigned to the returnValues string above
        if (!returnValues.isEmpty()) {
            // Generate the list of values to be in the yield statement for the true/then branch
            String returnValuesInYield = assignedVars.stream()
                    .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                    .collect(Collectors.joining(", "));
            emitter().emit("scf.yield %s : %s", returnValuesInYield, returnValuesTypes);
        }
        ssaValueNumberingStack().blockDone();
        emitter().decreaseIndentation();

        // 3. Generate the else branch. Even if the branch does not exist in CAL, we still need it and the
        // corresponding yield in MLIR.

        emitter().emit("} else {");
        emitter().increaseIndentation();
        ssaValueNumberingStack().newBlock();

        if (stmt.getElseBranch() != null) {
            if (stmt.getElseBranch().size() > 0) {
                stmt.getElseBranch().forEach(this::execute);
                // The else branch has to yield and return the values to be assigned to the returnValues string above
            }
        }

        if (!returnValues.isEmpty()) {
            // Generate the list of values to be in the yield statement for the false/else branch
            String returnValuesInYield = assignedVars.stream()
                    .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                    .collect(Collectors.joining(", "));
            emitter().emit("scf.yield %s : %s", returnValuesInYield, returnValuesTypes);
        }
        ssaValueNumberingStack().blockDone();
        emitter().decreaseIndentation();

        // Closing function for the ssaValueNumberingStack().getVarToBeAssignedBeforeBlockOpen() called before.
        assignedVars.forEach(x -> ssaValueNumberingStack().getVarToBeAssignedBeforeBlockClose(lvalues().lvalue(x)));

        emitter().emit("}");
        emitter().emit("// If Statement: End");

    }

    /*
     * Statement Foreach
     */

    default void execute(StmtForeach foreach) {
        System.out.println("StmtForEach");
        throw new UnsupportedOperationException("StmtForEach not implemented in MLIR.");
        /*forEach(foreach.getGenerator().getCollection(), foreach.getGenerator().getVarDecls(), () -> {
            for (Expression filter : foreach.getFilters()) {
                emitter().emit("if (%s) {", expressioneval().evaluate(filter));
                emitter().increaseIndentation();
            }
            foreach.getBody().forEach(this::execute);
            for (Expression filter : foreach.getFilters()) {
                emitter().decreaseIndentation();
                emitter().emit("}");
            }
        });*/
    }

    /*
     * Statement While
     */

    default void execute(StmtWhile stmt) {
        System.out.println("StmtWhile");
        throw new UnsupportedOperationException("StmtWhile not implemented in MLIR.");
        /*emitter().emit("while (true) {");
        emitter().increaseIndentation();
        emitter().emit("if (!%s) break;", expressioneval().evaluate(stmt.getCondition()));
        stmt.getBody().forEach(this::execute);
        emitter().decreaseIndentation();
        emitter().emit("}");*/
    }

    void forEach(Expression collection, List<GeneratorVarDecl> varDecls, Runnable action);

    default void forEach(ExprBinaryOp binOp, List<GeneratorVarDecl> varDecls, Runnable action) {
        System.out.println("ExprBinaryOp");
        throw new UnsupportedOperationException("ExprBinaryOp not implemented in MLIR.");
        /*emitter().emit("{");
        emitter().increaseIndentation();
        if (binOp.getOperations().equals(Collections.singletonList(".."))) {
            Type type = types().declaredType(varDecls.get(0));
            for (VarDecl d : varDecls) {
                emitter().emit("%s;", declarartions().declaration(type, variables().declarationName(d)));
            }
            String temp = variables().generateTemp();
            emitter().emit("%s = %s;", declarartions().declaration(type, temp), expressioneval().evaluate(binOp
            .getOperands().get(0)));
            emitter().emit("while (%s <= %s) {", temp, expressioneval().evaluate(binOp.getOperands().get(1)));
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
        emitter().emit("}");*/
    }

    /**
     * Generate variable declaration in MLIR. A declaration involves creating the mlir result and assigning a value
     * to it. These values can be either:
     * 1. The default value for the variable type
     * 2. The result of an expression
     * 3. A token from
     *
     * @param decl The variable declaration
     */
    default void emitVarDecl(VarDecl decl) {
        Type t = types().declaredType(decl);
        String declarationName = variables().declarationName(decl);
        if (decl.getValue() != null) {
            if (decl.getValue() instanceof ExprInput) {
                ExprInput input = (ExprInput) decl.getValue();
                // 1. Assign the output from a port to the variable
                if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(),
                        input.getPort().getName())) {
                    expressioneval().evaluateWithLvalue(declarationName,
                            (ExprInput) decl.getValue());
                } else {
                    // This case arises when a port is not connected - I am not sure what decl.getValue() is in this
                    // case.
                    assign(t, declarationName, decl.getValue());
                }
            } else {
                // 2. Assign the declaration expression to the value
                assign(t, declarationName, decl.getValue());
            }
        } else {
            // 3. Assign the default value to the variable
            assign(t, declarationName, new ExprLiteral(ExprLiteral.Kind.Integer,
                    backend().defaultValues().defaultValue(t)));
        }
    }

    /**
     * Assign an expression to an lvalue (or an mlir operand) and ensure that the types are consistent
     *
     * @param lvalueType The type of the lvalue
     * @param lvalue     The name of the operand the expression is assigned to
     * @param expr       The expression to assign to the operand
     */
    default String assign(Type lvalueType, String lvalue, Expression expr) {
        Type inputType = expressioneval().getExpressionType(expr);
        Type outputType = typeseval().getCommonType(lvalueType, types().type(expr));
        String rvalueTemp = expressioneval().evaluate(expr);
        String rvalue = typeseval().castType(inputType, outputType, rvalueTemp);
        String lvalueSSA = ssaValueNumberingStack().getVarToBeAssignedTo(lvalue);
        expressioneval().generateNOPEquivalentOperation(outputType, rvalue, lvalueSSA);
        return lvalueSSA;
        //emitter().emit("%%%s = %s : %s", lvalue, rvalue , backend().typeseval().type(lvalueType));
    }

    default Set<LValue> getNestedAssignments(Statement stmt) {
        throw new Error("getNestedAssignments not implemented for: " + stmt.getClass());
    }

    default Set<LValue> getNestedAssignments(StmtIf stmt) {
        Set<LValue> mergedSet = Stream.concat(stmt.getThenBranch().stream(), stmt.getElseBranch().stream())
                .flatMap(x -> getNestedAssignments(x).stream())
                .collect(Collectors.toSet());
        //mergedSet.addAll(getNestedAssignments(stmt.getThenBranch()));
        //mergedSet.addAll(getNestedAssignments(stmt.getElseBranch()));
        return mergedSet;
    }

    default Set<LValue> getNestedAssignments(StmtAssignment stmt) {
        return Collections.singleton(stmt.getLValue());
    }


    default Set<LValue> getConditionalReturnLvalues(Statement stmt) {
        Set<LValue> lvaluesOriginal = getNestedAssignments(stmt);
        Set<LValue> lvaluesToReturn = new HashSet<>();
        Set<String> lvaluesNames = new HashSet<>();

        // The Lvalues returned by getNestedAssignments can be different but sometimes they point to the same
        // varDecl, in this step we remove values that point to the same step. I want to do this with the distinct()
        // func in the streams class but I would need to have my own comparitor that overrides the equals method and
        // I am not sure how to do that.
        for (LValue lVal : lvaluesOriginal) {
            String lValueName = lvalues().lvalue(lVal);
            if (lvaluesNames.add(lValueName)) {
                lvaluesToReturn.add(lVal);
            }
        }

        return lvaluesToReturn;
    }
}
