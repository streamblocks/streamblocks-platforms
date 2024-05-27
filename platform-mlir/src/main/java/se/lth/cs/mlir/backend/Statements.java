package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.StackSSA;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.ExprBinaryOp;
import se.lth.cs.tycho.ir.expr.ExprComprehension;
import se.lth.cs.tycho.ir.expr.ExprInput;
import se.lth.cs.tycho.ir.expr.Expression;
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
        emitter().emit("// Stmt Write: Begin");
        System.out.println("Stmt Write");
        if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), write
                .getPort().getName())) {
            if (write.getRepeatExpression() != null) {
                throw new Error("not implemented");
            }

            String tempVar = "";
            Type type = types().portType(write.getPort());
            String portType = typeseval().type(type);
            String portName = write.getPort().getName();
            for (Expression expr : write.getValues()) {
                tempVar = expressioneval().evaluate(expr);
                emitter().emit("dfg.push(%%%s) %%%s : %s", tempVar, portName, portType);
            }
        }
        emitter().emit("// Stmt Write: End");

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

    /**
     * Generate MLIR for assignment statements. There are three different kinds of assignments:
     * 1. Standard assignments eg: x := 3
     * 2. Assignment a single element to a container eg: listVar[2] := 3
     * 3. ExprComprehension assignment: not yet implemented
     * @param assign Assignment statement from which MLIR is generated.
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
            // Assigning values to containers
            LValueIndexer indexer = (LValueIndexer) assign.getLValue();

            // 1. Get the list index and convert it to an index type (MLIR requires index types not integers to index
            // memref objects)
            Type listType = types().type(indexer.getStructure());
            String listName = variables().name(lvalues().evalLValueIndexerVar(indexer));
            String listSSA = ssaValueNumberingStack().getVarName(listName);
            String exprIndexNotAsIndexType = expressioneval().evaluate(indexer.getIndex());
            String exprIndexSSA = typeseval().castToIndex(types().type(indexer.getIndex()), exprIndexNotAsIndexType);

            // 2. Get value to assign to the container
            Type inputType = types().type(assign.getExpression());
            Type outputType = types().type(indexer);
            String rvalueSSATemp = expressioneval().evaluate(assign.getExpression());
            String rvalueSSA = typeseval().castType(inputType, outputType, rvalueSSATemp);

            // 3. Emit the operation that stores the value in the memref
            emitter().emit("memref.store %%%s, %%%s[%%%s] : %s", rvalueSSA, listSSA, exprIndexSSA,
                    typeseval().type(listType));

        } else if (assign.getExpression() instanceof ExprComprehension) {
            throw new Error("ExprComprehension functionality not implemented in execute(StmtAssignment)");
        } else {
            // Standard assignment to a variable
            String lvalue = lvalues().lvalue(assign.getLValue());
            Type type = types().type(assign.getLValue());
            initialiseWithExpression(type, lvalue, assign.getExpression());
        }

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
        // We need this as the mlir if statement needs to return these values and yield them
        List<LValue> assignedVars = getConditionalReturnLvalues(stmt);

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
        System.out.println("StmtForeach");
        throw new UnsupportedOperationException("StmtForeach not implemented in MLIR.");
        /*emitter().emit("// Foreach Statement: Begin");
        //emitter().emit("//     Variable declarations attached to foreach statement: Begin");
        if (foreach.getGenerator().getVarDecls().size() > 1) {
            throw new UnsupportedOperationException("MLIR backend currently only supports single " +
                    "variables in foreach statements.");
        }
        if(!(foreach.getGenerator().getCollection() instanceof ExprBinaryOp)){
            throw new UnsupportedOperationException("MLIR backend currently only supports foreach " +
                    "statements over a range, eg: 1..10. Other collections not yet supported.");
        }

        // Generate the declared variable
        VarDecl decl = foreach.getGenerator().getVarDecls().get(0);
        String declarationName = variables().declarationName(decl);
        String ssaName = ssaValueNumberingStack().getVarToBeAssignedTo(declarationName);

        // Generate the loop upper and lower bounds
        ExprBinaryOp rangeExpr = (ExprBinaryOp) foreach.getGenerator().getCollection();
        Type initalValueType = types().type(rangeExpr.getOperands().get(0));
        String initialValue = expressioneval().evaluate(rangeExpr.getOperands().get(0));
        String initialValueCast = ssaValueNumberingStack().getNewTempVar() + "_lb";
        emitter().emit("%%%s = index.casts %%%s : %s to index", initialValueCast, initialValue, typeseval().type
        (initalValueType));
        Type finalValueType = types().type(rangeExpr.getOperands().get(1));
        String finalValue = expressioneval().evaluate(rangeExpr.getOperands().get(1));
        String finalValueCast = ssaValueNumberingStack().getNewTempVar() + "_ub";
        emitter().emit("%%%s = index.casts %%%s : %s to index", finalValueCast, finalValue, typeseval().type
        (finalValueType));
        String stepValue = ssaValueNumberingStack().getNewTempVar() + "_step";
        emitter().emit("%%%s = index.constant 1", stepValue);

        // Generate the return arguments and the arguments passed in
        List<LValue> assignedVars = getConditionalReturnLvalues(foreach);
        String returnValuesTypes = assignedVars.stream()
                .map(x -> typeseval().type(types().type(x)))
                .collect(Collectors.joining(", "));

        // 1. Condition check block of the while statement ()
        // We need three different SSA arguments in the scf.while line (before region).
        //    - initialValues - the values passed into the while loop from the surrounding context
        //    - whileReturnValues - the SSA values that are returned from the while loop
        //    - argumentNames - these are the names that the initial values get assigned to in this scope
        // The initial values and argument names get merged together into the inputToArgumentString.
        // eg: %whileReturnValue1 = scf.while (%argumentName1 = %initialValue1)
        List<String> initialValues = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                .collect(Collectors.toList());
        String forReturnValues = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarToBeAssignedTo(lvalues().lvalue(x)))
                .collect(Collectors.joining(", "));
        ssaValueNumberingStack().newBlock();
        List<String> argumentNames = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarToBeAssignedTo(lvalues().lvalue(x)))
                .collect(Collectors.toList());
        String inputToArgumentString = "";
        for (int i = 0; i < initialValues.size(); i++) {
            inputToArgumentString = inputToArgumentString + argumentNames.get(i) + " = " + initialValues.get(i) + ", ";
        }
        if (!inputToArgumentString.equals("")) {
            inputToArgumentString = inputToArgumentString.substring(0, inputToArgumentString.length() - 2);
        }


        emitter().emit("%s = scf.for %%%s = %%%s to %%%s step %%%s ", forReturnValues, ssaName, initialValueCast,
        finalValueCast, stepValue);
        emitter().emit("\t\titer_args(%s) -> (%s) {", inputToArgumentString, returnValuesTypes);
        emitter().increaseIndentation();
        foreach.getBody().forEach(this::execute);

        String returnValuesInYield = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                .collect(Collectors.joining(", "));
        emitter().emit("scf.yield %s : %s", returnValuesInYield, returnValuesTypes);
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("// Foreach Statement: End");
        ssaValueNumberingStack().blockDone();*/
    }

    /**
     * Statement While - Defined using the MLIR structured control flow dialect (scf)
     * <p>
     * While statements have the form:
     * %res = scf.while (%arg1 = %init1) : (f32) -> f32 {
     * ....// "Before" region.
     * ....// In a "while" loop, this region computes the condition.
     * ....%condition = call @evaluate_condition(%arg1) : (f32) -> i1
     * ....// Forward the argument (as result or "after" region argument).
     * ....scf.condition(%condition) %arg1 : f32
     * } do {
     * ^bb0(%arg2: f32):
     * ....// "After" region.
     * ....// In a "while" loop, this region is the loop body.
     * ....%next = call @payload(%arg2) : (f32) -> f32
     * ....// Forward the new value to the "before" region.
     * ....// The operand types must match the types of the `scf.while` operands.
     * ....scf.yield %next : f32
     * }
     */
    default void execute(StmtWhile stmt) {
        System.out.println("StmtWhile");
        emitter().emit("// While Statement: Begin");

        // Get every value that is assigned to during the while statement
        // We need this as the mlir if statement needs to return these values and yield them
        List<LValue> assignedVars = getConditionalReturnLvalues(stmt);
        String returnValuesTypes = assignedVars.stream()
                .map(x -> typeseval().type(types().type(x)))
                .collect(Collectors.joining(", "));

        // 1. Condition check block of the while statement ()
        // We need three different SSA arguments in the scf.while line (before region).
        //    - initialValues - the values passed into the while loop from the surrounding context
        //    - whileReturnValues - the SSA values that are returned from the while loop
        //    - argumentNames - these are the names that the initial values get assigned to in this scope
        // The initial values and argument names get merged together into the inputToArgumentString.
        // eg: %whileReturnValue1 = scf.while (%argumentName1 = %initialValue1)
        List<String> initialValues = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                .collect(Collectors.toList());
        String whileReturnValues = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarToBeAssignedTo(lvalues().lvalue(x)))
                .collect(Collectors.joining(", "));
        ssaValueNumberingStack().newBlock();
        List<String> argumentNames = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarToBeAssignedTo(lvalues().lvalue(x)))
                .collect(Collectors.toList());
        // The argument names and the initial values assigned
        String inputToArgumentString = "";
        for (int i = 0; i < initialValues.size(); i++) {
            inputToArgumentString = inputToArgumentString + argumentNames.get(i) + " = " + initialValues.get(i) + ", ";
        }
        if (!inputToArgumentString.equals("")) {
            inputToArgumentString = inputToArgumentString.substring(0, inputToArgumentString.length() - 2);
        }

        if (whileReturnValues.isEmpty()) {
            emitter().emit("scf.while(%s) : (%s) -> (%s) {", inputToArgumentString, returnValuesTypes,
                    returnValuesTypes);
        } else {
            emitter().emit("%s = scf.while(%s) : (%s) -> (%s) {", whileReturnValues, inputToArgumentString,
                    returnValuesTypes, returnValuesTypes);
        }
        emitter().increaseIndentation();
        emitter().emit("// While Statement: Condition Check");
        String conditionVar = expressioneval().evaluate(stmt.getCondition());

        // The scf.condition requires a list of the SSA values to transfer to the while body.
        // These are the latest version of the argument names generated within the while before block
        String beforeRegionReturn = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                .collect(Collectors.joining(", "));
        emitter().emit("scf.condition(%%%s) %s : %s", conditionVar, beforeRegionReturn, returnValuesTypes);
        ssaValueNumberingStack().blockDone();
        emitter().decreaseIndentation();
        emitter().emit("} do {");
        // 2. Main body of the loop


        // 2.1 Basic block that receives the arguments
        emitter().emit("\t// While Statement: Body Execution");
        ssaValueNumberingStack().newBlock();
        String basicBlockName = ssaValueNumberingStack().getNewTempVar();
        // The basic block takes a list of argument which should be the same variables as in the assignedVars array.
        String basicBlockArguments = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarToBeAssignedTo(lvalues().lvalue(x)) + ": " + typeseval().type(types().type(x)))
                .collect(Collectors.joining(", "));
        emitter().emit("^%s(%s):", basicBlockName, basicBlockArguments);
        emitter().increaseIndentation();

        // 2.2 Generate the statements in the body
        stmt.getBody().forEach(this::execute);

        // 2.3 Yield the basic block - returns back the before block which checks the condition again
        String yieldReturn = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                .collect(Collectors.joining(", "));
        emitter().emit("scf.yield %s: %s", yieldReturn, returnValuesTypes);
        ssaValueNumberingStack().blockDone();
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("// While Statement: End");
    }

    void forEach(Expression collection, List<GeneratorVarDecl> varDecls, Runnable action);

    default void forEach(ExprBinaryOp binOp, List<GeneratorVarDecl> varDecls, Runnable action) {
        System.out.println("forEach ExprBinaryOp");
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
                    initialiseWithExpression(t, declarationName, decl.getValue());
                }
            } else {
                // 2. Assign the declaration expression to the value
                initialiseWithExpression(t, declarationName, decl.getValue());
            }
        } else {
            // 3. Assign the default value to the variable
            defaultInitialise(t, declarationName); //
        }
    }

    /**
     * Assign an expression to an lvalue (or an mlir operand) and ensure that the types are consistent
     * This expression will alias the lvalue SSA to the rvalue SSA
     *
     * @param lvalueType   The type of the lvalue
     * @param lvalueString The name of the operand the expression is assigned to (can be aliased)
     * @param expr         The expression to assign to the operand
     */
    default void initialiseWithExpression(Type lvalueType, String lvalueString, Expression expr) {
        Type inputType = types().type(expr);
        Type outputType = lvalueType;
        String rvalueTemp = expressioneval().evaluate(expr);
        String rvalueSSA = typeseval().castType(inputType, outputType, rvalueTemp);
        assignInitialValueToSSAOperand(lvalueString, rvalueSSA);
    }

    /**
     * Initialise a variable. This method is overidden for the different types that are default initialised
     *
     * @param lvalueType The type of the lvalue that is default initialised
     * @param lvalueString The name of the operand the expression is assigned to. (can be aliased)
     */
    default void defaultInitialise(Type lvalueType, String lvalueString) {
        throw new UnsupportedOperationException(lvalueType.getClass().toString() + " has no defaultInitialise " +
                "function set.");
    }

    default void defaultInitialise(IntType lvalueType, String lvalueString) {
        String typeString = typeseval().type(lvalueType);
        String lvalueSSA = ssaValueNumberingStack().getVarToBeAssignedTo(lvalueString);
        emitter().emit("%%%s = arith.constant 0 : %s", lvalueSSA, typeString);
    }

    default void defaultInitialise(ListType lvalueType, String lvalueString) {
        String typeString = typeseval().type(lvalueType);
        String lvalueSSA = ssaValueNumberingStack().getVarToBeAssignedTo(lvalueString);
        emitter().emit("%%%s = memref.alloca() : %s", lvalueSSA, typeString);
    }

    /**
     * Alias an ssa operand to another operand.
     *
     * @param lvalueString The SSA operand we want to alias to.
     * @param rvalueSSA The SSA operand that currently holds the operand
     */
    default void assignInitialValueToSSAOperand(String lvalueString, String rvalueSSA) {
        String lvalueSSA = ssaValueNumberingStack().getVarToBeAssignedTo(lvalueString);
        ssaValueNumberingStack().aliasSSA(rvalueSSA, lvalueSSA);
        emitter().emit("// %s aliased to %s", lvalueSSA, rvalueSSA);
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

    default Set<LValue> getNestedAssignments(StmtWhile stmt) {
        Set<LValue> mergedSet = stmt.getBody().stream()
                .flatMap(x -> getNestedAssignments(x).stream())
                .collect(Collectors.toSet());
        //mergedSet.addAll(getNestedAssignments(stmt.getThenBranch()));
        //mergedSet.addAll(getNestedAssignments(stmt.getElseBranch()));
        return mergedSet;
    }

    default Set<LValue> getNestedAssignments(StmtAssignment stmt) {
        return Collections.singleton(stmt.getLValue());
    }

    default Set<LValue> getNestedAssignments(StmtForeach stmt) {
        Set<LValue> mergedSet = stmt.getBody().stream()
                .flatMap(x -> getNestedAssignments(x).stream())
                .collect(Collectors.toSet());
        return mergedSet;
    }


    /**
     * Get a list of all variables (in the form lvalues) that are assigned to in this branch of the AST.
     *
     * @param stmt The top statement node in the AST where assignments are to be searched
     * @return A alphabetically sorted list of lvalues that are present in the AST branch
     */
    default List<LValue> getConditionalReturnLvalues(Statement stmt) {
        // 1. Get all the lvalues in the tree recursively
        Set<LValue> lvaluesOriginal = getNestedAssignments(stmt);

        // 2. The Lvalues returned by getNestedAssignments can be different, but sometimes they point to the same
        // varDecl, in this step we remove values that point to the same var. I want to do this with the distinct()
        // func in the streams class, but I would need to have my own comparator that overrides the equals method.
        // I am not sure how to do that.
        List<LValue> lvaluesToReturn = new ArrayList<>(lvaluesOriginal.size());
        Set<String> lvaluesNames = new HashSet<>();
        for (LValue lVal : lvaluesOriginal) {
            String lValueName = lvalues().lvalue(lVal);
            if (lvaluesNames.add(lValueName)) {
                lvaluesToReturn.add(lVal);
            }
        }

        // 3. Arrange these varDecls alphabetically to make the tests neater
        Collections.sort(lvaluesToReturn, (o1, o2) -> (lvalues().lvalue(o1).compareTo(lvalues().lvalue(o2))));

        return lvaluesToReturn;
    }
}
