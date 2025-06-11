package se.lth.cs.mlir.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.StackSSA;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.mlir.backend.util.DeferredPortOperationContainer;
import se.lth.cs.mlir.backend.util.PrintStringResult;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.IRNode;
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

    default ListGenerator lists() {
        return backend().lists();
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
        emitter().emit("// StmtConsume not implemented: consume happens on peaking right now");
    }

    /*
     * Statement Write - writes expressions to output ports
     */

    default void execute(StmtWrite write) {
        emitter().emit("// Stmt Write Preprocessing: Begin");
        if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), write
                .getPort().getName())) {
            if (write.getRepeatExpression() != null) {
                // 1. Output ports with repeat keywords need to have a CAL list (converted to MLIR memref) passed to
                // them through "write.getValues().get(0)".
                if (write.getValues().size() != 1) {
                    throw new Error("Output expressions with the repeat keyword only support a single expression.");
                }

                ListType listType = (ListType) types().type(write.getValues().get(0));
                String listSSA = expressioneval().evaluate(write.getValues().get(0));
                Type portType = types().portType(write.getPort());
                String portName = write.getPort().getName();
                if (!listType.getSize().isPresent()) {
                    throw new Error("List types in repeat statements should always have a size, if this error is " +
                            "thrown, this is a compiler bug");
                }
                int numRepeats = listType.getSize().orElse(0);
                List<String> tempSSAs = new ArrayList<>(numRepeats);
                // 1.1 For every value of repeat we need to pull a token from the buffer convert it to the correct type
                // and then defer to actual dfg.push operation to the end of the action (information relevant for
                // deferring stored in the deferredPortOperationsBox())
                for (int i = 0; i < numRepeats; i++) {
                    String indexSSA = ssaValueNumberingStack().getNewTempVar();
                    emitter().emit("%%%s = arith.constant %d: index", indexSSA, i);
                    String destSSA = ssaValueNumberingStack().getNewTempVar();
                    lists().load(listSSA, destSSA, Collections.singletonList(indexSSA), listType);
                    String convertedSSA = typeseval().castType(listType.getElementType(), portType, destSSA);
                    tempSSAs.add(convertedSSA);
                }
                backend().deferredPortOperationsBox().get().addPort(listSSA, tempSSAs, listType, portName);
            } else {
                // 2. Push a standard variable (i.e not a container) to a channel. This is relatively simple. However
                // we still need to defer the dfg.push to the end.
                Type portType = types().portType(write.getPort());
                String portName = write.getPort().getName();
                List<String> tempSSAs = new ArrayList<>();
                for (Expression expr : write.getValues()) {
                    String evalSSA = expressioneval().evaluate(expr);
                    String convertedSSA = typeseval().castType(types().type(expr), portType, evalSSA);
                    tempSSAs.add(convertedSSA);
                    //emitter().emit("dfg.push(%%%s) %%%s : %s", tempVar, portName, portType);
                }
                backend().deferredPortOperationsBox().get().addPort("", tempSSAs, new ListType(portType,
                        OptionalInt.of(1)), portName);
            }
        }
        emitter().emit("// Stmt Write Preprocessing: End");

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
     *
     * @param assign Assignment statement from which MLIR is generated.
     */
    default void execute(StmtAssignment assign) {
        emitter().emit("// Assignment Statement: Start");

        if (assign.getLValue() instanceof LValueIndexer) {
            // Assigning values to containers
            LValueIndexer indexer = (LValueIndexer) assign.getLValue();

            // 1. Get the list SSA name and the indices
            Type listType = lvalues().getListIndexerType(indexer);
            String typeString = typeseval().type(listType);
            String listName = variables().name(lvalues().evalLValueIndexerVar(indexer));
            boolean assignToGlobalState = ssaValueNumberingStack().hasStateVar(listName);
            String listSSA;
            if (assignToGlobalState) {
                listSSA = ssaValueNumberingStack().getNewTempVar();
                emitter().emit("%%%s = cal.get(%%%s: !cal.state_ref<%s>) : %s", listSSA, listName, typeString,
                        typeString);
            } else {
                listSSA = ssaValueNumberingStack().getVarName(listName);
            }
            List<String> indices = lvalues().getListIndexes(indexer);

            // 2. Get value to assign to the container
            Type inputType = types().type(assign.getExpression());
            Type outputType = types().type(indexer);
            String rvalueSSATemp = expressioneval().evaluate(assign.getExpression());
            String rvalueSSA = typeseval().castType(inputType, outputType, rvalueSSATemp);
            String rvalueSSAResized = typeseval().castType(outputType, typeseval().resizeInnerType(outputType), rvalueSSA);

            // 3. Emit the operation that stores the value in the memref
            lists().store(listSSA, rvalueSSAResized, indices, listType);
        } else if (assign.getExpression() instanceof ExprComprehension) {
            throw new Error("ExprComprehension functionality not implemented in execute(StmtAssignment)");
        } else {
            // Standard assignment to a variable
            String lvalue = lvalues().lvalue(assign.getLValue());
            Type stateType = types().type(assign.getLValue());
            String stateTypeString = typeseval().type(stateType);

            boolean assignToGlobalState = ssaValueNumberingStack().hasStateVar(lvalue);
            if (assignToGlobalState) {
                String resultSSA = expressioneval().evaluate(assign.getExpression());
                Type inputType = types().type(assign.getExpression());
                String resultSSACast = typeseval().castType(inputType , stateType, resultSSA);
                emitter().emit("cal.set(%%%s: !cal.state_ref<%s>, %%%s: %s)", lvalue, stateTypeString, resultSSACast,
                        stateTypeString);
            } else {
                initialiseWithExpression(stateType, lvalue, assign.getExpression());
            }
        }

        emitter().emit("// Assignment Statement: End");
    }

    default void copy(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        String mlirOp = backend().typeseval().mlirTypeConstantInstruction(lvalueType, rvalue);
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


    /**
     * Generates MLIR for a call statement
     * <p>
     * This implementation only handles calls to the special built-in procedures `println` and `print`.
     * - If the procedure is `println`, it emits a print instruction followed by a newline.
     * - If the procedure is `print`, it emits a print instruction without a newline.
     * <p>
     * Any other procedure call will result in an UnsupportedOperationException.
     */
    default void execute(StmtCall call) {

        if (call.getProcedure() instanceof ExprGlobalVariable) {
            ExprGlobalVariable variable = (ExprGlobalVariable) call.getProcedure();
            VarDecl decl = backend().varDecls().declaration(variable);
            if (decl.getName().equals("println")) {
                generatePrint(call.getArgs().get(0), true);
            } else if (decl.getName().equals("print")) {
                generatePrint(call.getArgs().get(0), false);
            }
        } else {
            throw new UnsupportedOperationException("StmtCall not implemented in MLIR.");
        }

        //backend().callablesInActor().procHeader(instanceName, call.getProcedure());
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

    /**
     * Emits MLIR code to print an expression, formatted as a string with optional arguments.
     * If newLine is true, a newline is appended to the format string.
     */
    default void generatePrint(Expression arg, boolean newLine) {
        PrintStringResult toPrint = generatePrintString(arg);

        String formatString = toPrint.formatString;
        if (newLine) {
            formatString += "\\n";
        }

        String ssaArgs = toPrint.ssaValues.isEmpty() ? "" :
                ", %" + String.join(", %", toPrint.ssaValues);

        String typeSignature = toPrint.types.isEmpty() ? "" :
                ": (" + String.join(", ", toPrint.types) + ")";

        emitter().emit("fifo.print(\"%s\\00\"%s) %s", formatString, ssaArgs, typeSignature);
    }


    default PrintStringResult generatePrintString(Expression expr) {
        throw new UnsupportedOperationException("Cannot print string containing node of type: " + expr.getClass());
    }

    default PrintStringResult generatePrintString(ExprBinaryOp binaryExpr) {
        PrintStringResult ret = new PrintStringResult();
        for (Expression expr : binaryExpr.getOperands()) {
            ret = PrintStringResult.merge(ret, generatePrintString(expr));
        }
        return ret;
    }

    default PrintStringResult generatePrintString(ExprLiteral expr) {
        String raw = expr.getText();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }
        return new PrintStringResult(raw);
    }

    default PrintStringResult generatePrintString(ExprVariable variable) {
        VarDecl decl = backend().varDecls().declaration(variable);
        Type type = types().declaredType(decl);
        String typeString = typeseval().type(type);
        String ssaName = expressioneval().getVariableSSANameOrLoadFromState(variables().name(variable.getVariable()),
                typeString);
        return new PrintStringResult("%" + printFormat(type), ssaName, typeString);
    }

    default PrintStringResult generatePrintString(ExprIndexer expr) {
        Type type = types().type(expr);
        String typeString = typeseval().type(type);
        String ssaName = expressioneval().evaluate(expr);
        return new PrintStringResult("%" + printFormat(type), ssaName, typeString);
    }

    default PrintStringResult generatePrintString(ExprGlobalVariable expr) {
        OptionalLong value = backend().constants().intValue(expr);
        if (value.isPresent()) {
            return new PrintStringResult("" + value.getAsLong());
        }
        Type varType = types().type(expr);
        String typeString = typeseval().type(varType);
        String ssaName = ssaValueNumberingStack().getVarName(expr.getGlobalName().toString());
        return new PrintStringResult("%" + printFormat(varType), ssaName, typeString);
    }

    String printFormat(Type type);

    default String printFormat(BoolType type) {
        return "i";
    }

    default String printFormat(IntType type) {
        if (type.getSize().isPresent()) {
            if (type.getSize().getAsInt() <= 32) {
                return type.isSigned() ? "i" : "u";
            } else {
                return type.isSigned() ? "lli" : "llu";
            }
        } else {
            return type.isSigned() ? "i" : "u";
        }
    }

    default String printFormat(RealType type) {
        return "f";
    }

    default String printFormat(StringType type) {
        return "s";
    }


    /*
     * Statement Block
     */
    default void execute(StmtBlock block) {
        emitter().emit("// Block Statement: Begin");
        //emitter().increaseIndentation();
        ssaValueNumberingStack().newBlock();
        // 1. Deal with the block vardecls
        emitter().emit("//     Variable declarations attached to block statement: Begin");

        // 1.1 Create the port box to store deferred MLIR operations
        // In a StmtBlock, the first few VarDecls are variables declared from input ports. These generally consist of
        // a dfg.pull operation but in the case of the repeat keywords other operators then move that data into a
        // memref. In DFG (at least on 2024/05/27), all dfg.pull operations need to appear before any other
        // operations. So we defer the assigning to memref to other operations until all the VarDecls have been
        // declared.
        backend().deferredPortOperationsBox().set(new DeferredPortOperationContainer());

        // 1.2 Emit the dfg.pull part of the VarDecls.
        for (VarDecl decl : block.getVarDecls()) {
            emitVarDecl(decl);

            // 1.3 Emit the deferred port operations. We do this as soon we no longer have VarDecls attached to input
            // ports as once the first one appears we should not recieve any further ones.
            if (!(decl.getValue() instanceof ExprInput)) {
                emitDeferredVarDeclMlir();
            }
        }
        emitDeferredVarDeclMlir(); // Just do this here incase we have no decls that are not attached to input ports
        emitter().emit("//     Variable declarations attached to block statement: End");

        // 2. Generate the mlir for each statement in the block
        backend().deferredPortOperationsBox().set(new DeferredPortOperationContainer());
        block.getStatements().forEach(this::execute);

        // 2.1 Similar to pulling tokens from dfg channels when using the repeat keyword, pushing them requires
        // performing pre-processing on the list and then deferring the dfg.push operation to later. This functions
        // emits the deferred dfg.push operands
        emitter().emit("//     dfg.push operations deferred from StmtWrites: Begin");
        emitDeferredTokenPush();
        emitter().emit("//     dfg.push operations deferred from StmtWrites: End");
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
     * Statement Foreach - this is an absolute mess and could be cleaned up
     */
    default void execute(StmtForeach foreach) {
        emitter().emit("// Foreach Statement: Begin");
        //emitter().emit("//     Variable declarations attached to foreach statement: Begin");
        if (foreach.getGenerator().getVarDecls().size() > 1) {
            throw new UnsupportedOperationException("MLIR backend currently only supports single " +
                    "variables in foreach statements.");
        }
        if (!(foreach.getGenerator().getCollection() instanceof ExprBinaryOp)) {
            throw new UnsupportedOperationException("MLIR backend currently only supports foreach " +
                    "statements over a range, eg: 1..10. Other collections not yet supported.");
        }

        // Generate the loop index variable
        VarDecl loopIndexVariableDeclaration = foreach.getGenerator().getVarDecls().get(0);
        String loopIndexVariableName = variables().declarationName(loopIndexVariableDeclaration);
        String loopIndexVariableSSA = ssaValueNumberingStack().getVarToBeAssignedTo(loopIndexVariableName);
        Type loopIndexVariableType = types().declaredType(loopIndexVariableDeclaration);

        // Generate the loop upper and lower bounds
        ExprBinaryOp rangeExpr = (ExprBinaryOp) foreach.getGenerator().getCollection();
        Type initalValueType = types().type(rangeExpr.getOperands().get(0));
        String initialValue = expressioneval().evaluate(rangeExpr.getOperands().get(0));
        Type signed32Type = new IntType(OptionalInt.of(32), true);
        String initialValueCast_i32 = typeseval().castType(initalValueType,  signed32Type , initialValue);
        String initialValueCast_index = ssaValueNumberingStack().getNewTempVar() + "_lb";
        emitter().emit("%%%s = index.casts %%%s : i32 to index", initialValueCast_index, initialValueCast_i32);

        Type finalValueType = types().type(rangeExpr.getOperands().get(1));
        String finalValue = expressioneval().evaluate(rangeExpr.getOperands().get(1));
        String finalValueCast_i32 = typeseval().castType(finalValueType,  signed32Type , finalValue);
        String finalValueCast_index = ssaValueNumberingStack().getNewTempVar() + "_ub";
        emitter().emit("%%%s = index.casts %%%s : i32 to index", finalValueCast_index, finalValueCast_i32);
        String stepValue = ssaValueNumberingStack().getNewTempVar() + "_step";
        emitter().emit("%%%s = index.constant 1", stepValue);
        String finalValueCast_index_plus1 = ssaValueNumberingStack().getNewTempVar() + "_ub_plus_1";
        emitter().emit("%%%s = arith.addi %%%s, %%%s : index", finalValueCast_index_plus1, finalValueCast_index, stepValue);

        // Generate the return arguments and the arguments passed in and the initial values of the arguments
        List<LValue> assignedVars = getConditionalReturnLvalues(foreach);
        String returnValuesTypes = assignedVars.stream()
                .map(x -> typeseval().type(types().type(x)))
                .collect(Collectors.joining(", "));

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


        if (forReturnValues.isEmpty()) {
            emitter().emit("scf.for %%%s = %%%s to %%%s step %%%s", loopIndexVariableSSA, initialValueCast_index,
                    finalValueCast_index_plus1, stepValue);
        } else {
            emitter().emit("%s = scf.for %%%s = %%%s to %%%s step %%%s", forReturnValues, loopIndexVariableSSA, initialValueCast_index,
                    finalValueCast_index_plus1, stepValue);
        }
        emitter().emit("\t\titer_args(%s) -> (%s) {", inputToArgumentString, returnValuesTypes);
        emitter().increaseIndentation();

        // We need to cast loop index variable to the correct type as it is now currently an index
        String loopIndexVariableSSA_i32 = ssaValueNumberingStack().getVarToBeAssignedTo(loopIndexVariableName);
        emitter().emit("%%%s = arith.index_cast %%%s : index to i32", loopIndexVariableSSA_i32, loopIndexVariableSSA);
        if(!signed32Type.equals(loopIndexVariableType) && typeseval().canCastFromI32(loopIndexVariableType)){
            String loopIndexVariable_correctType = ssaValueNumberingStack().getVarToBeAssignedTo(loopIndexVariableName);
            typeseval().castInt(signed32Type, loopIndexVariableType, loopIndexVariableSSA_i32, loopIndexVariable_correctType);
        }

        // Print out the statements in the loop body
        foreach.getBody().forEach(this::execute);

        // Print out the yeild statement if required
        String returnValuesInYield = assignedVars.stream()
                .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                .collect(Collectors.joining(", "));
        if (!returnValuesInYield.isEmpty()) {
            emitter().emit("scf.yield %s : %s", returnValuesInYield, returnValuesTypes);
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
        emitter().emit("// Foreach Statement: End");
        ssaValueNumberingStack().blockDone();
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
        if (whileReturnValues.isEmpty()) {
            emitter().emit("scf.condition(%%%s)", conditionVar);
        } else {
            String beforeRegionReturn = assignedVars.stream()
                    .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                    .collect(Collectors.joining(", "));
            emitter().emit("scf.condition(%%%s) %s : %s", conditionVar, beforeRegionReturn, returnValuesTypes);
        }
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
        if (whileReturnValues.isEmpty()) {
            emitter().emit("scf.yield");
        } else {
            String yieldReturn = assignedVars.stream()
                    .map(x -> "%" + ssaValueNumberingStack().getVarName(lvalues().lvalue(x)))
                    .collect(Collectors.joining(", "));
            emitter().emit("scf.yield %s: %s", yieldReturn, returnValuesTypes);
        }
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
     * 3. A token from an input port
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

    default void emitStateVarDecl(VarDecl decl) {
        Type t = types().declaredType(decl);
        String declarationName = variables().declarationName(decl);

        //System.out.println(declarationName + " " + t);
        String typeString = typeseval().type(t);
        //String lvalueSSA = ssaValueNumberingStack().getVarToBeAssignedTo(lvalueString);

        emitter().emit("%%%s = cal.create_state_var<%s> : !cal.state_ref<%s>", declarationName, typeString, typeString);
        ssaValueNumberingStack().setStateVar(declarationName, typeString);

        String ssaInitialisedValue;
        if (decl.getValue() != null) {
            Type inputType = types().type(decl.getValue());
            String rvalueTemp = expressioneval().evaluate(decl.getValue());
            ssaInitialisedValue = typeseval().castType(inputType, t, rvalueTemp);
        } else {
            ssaInitialisedValue = defaultStateInitialise(t);
        }
        emitter().emit("cal.set(%%%s: !cal.state_ref<%s>, %%%s: %s)", declarationName, typeString,
                ssaInitialisedValue, typeString);

    }

    default void emitDeferredVarDeclMlir() {
        if (!backend().deferredPortOperationsBox().isEmpty()) {
            for (DeferredPortOperationContainer.SinglePortBuilder singleBuilder :
                    backend().deferredPortOperationsBox().get().getPorts()) {
                String listSSA = ssaValueNumberingStack().getVarToBeAssignedTo(singleBuilder.getListString());
                lists().allocateList(singleBuilder.getListType(), listSSA);
                for (int i = 0; i < singleBuilder.getTempSSAs().size(); i++) {
                    String indexSSA = ssaValueNumberingStack().getNewTempVar();
                    emitter().emit("%%%s = arith.constant %d: index", indexSSA, i);
                    lists().store(listSSA, singleBuilder.getTempSSAs().get(i), Collections.singletonList(indexSSA),
                            singleBuilder.getListType());
                }
            }
            backend().deferredPortOperationsBox().clear();
        }
    }

    default void emitDeferredTokenPush() {
        if (!backend().deferredPortOperationsBox().isEmpty()) {
            for (DeferredPortOperationContainer.SinglePortBuilder singleBuilder :
                    backend().deferredPortOperationsBox().get().getPorts()) {
                for (int i = 0; i < singleBuilder.getTempSSAs().size(); i++) {
                    String portName = singleBuilder.getPortName();
                    String typeString = typeseval().type(singleBuilder.getListType().getElementType());
                    String ssaToSend = singleBuilder.getTempSSAs().get(i);
                    emitter().emit("dfg.push(%%%s) %%%s : %s", ssaToSend, portName, typeString);
                }
            }
            backend().deferredPortOperationsBox().clear();
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
     * @param lvalueType   The type of the lvalue that is default initialised
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
        String lvalueSSA = ssaValueNumberingStack().getVarToBeAssignedTo(lvalueString);
        lists().allocateList(lvalueType, lvalueSSA);
    }

    default String defaultStateInitialise(Type lvalueType) {
        throw new UnsupportedOperationException(lvalueType.getClass().toString() + " has no defaultStateInitialise " +
                "function set.");
    }

    default String defaultStateInitialise(IntType lvalueType) {
        String typeString = typeseval().type(lvalueType);
        String lvalueSSA = ssaValueNumberingStack().getNewTempVar();
        emitter().emit("%%%s = arith.constant 0 : %s", lvalueSSA, typeString);
        return lvalueSSA;
    }

    default String defaultStateInitialise(ListType lvalueType) {
        String lvalueSSA = ssaValueNumberingStack().getNewTempVar();
        lists().allocateList(lvalueType, lvalueSSA);
        return lvalueSSA;
    }

    /**
     * Alias an ssa operand to another operand.
     *
     * @param lvalueString The SSA operand we want to alias to.
     * @param rvalueSSA    The SSA operand that currently holds the operand
     */
    default void assignInitialValueToSSAOperand(String lvalueString, String rvalueSSA) {
        String lvalueSSA = ssaValueNumberingStack().getVarToBeAssignedTo(lvalueString);
        ssaValueNumberingStack().aliasSSA(rvalueSSA, lvalueSSA);
        emitter().emit("// %s aliased to %s", lvalueSSA, rvalueSSA);
    }

    /**
     * Get all the assignment Lvalues underneath this node in the AST.
     * This function executes recursively, terminating when a StmtAssignment is reached or when reaching a node with
     * no children.
     *
     * @param node The starting node in the tree
     * @return A Set of Lvalues where each LValue comes from a different assignment statements
     */
    default Set<LValue> getNestedAssignments(IRNode node) {
        Set<LValue> mergedSet = node.walk()// Get a stream of all child attached to this node. The stream also
                // returns this node which is excluded in the next line
                .flatMap(x -> (x != node) ? getNestedAssignments(x).stream() : Stream.empty()) // Get the nested
                // assignments in the child nodes, exclude this node to prevent infinite recursion
                .collect(Collectors.toSet());
        return mergedSet;
    }

    default Set<LValue> getNestedAssignments(StmtAssignment stmt) {
        // When this is an LValueIndex, this is the assignment to a location in a list is a[1] = 3. In this case, the
        // SSA is not updated, only the internal list data. For our use case, we do not need this non-updating SSA.
        if (stmt.getLValue() instanceof LValueIndexer) {
            return Collections.emptySet();
        } else {
            return Collections.singleton(stmt.getLValue());
        }
    }

    /**
     * Get a list of all variables (in the form lvalues) that are assigned to in this branch of the AST.
     * This list is sorted alphabetically for consistency across unit tests.
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
            if (lvaluesNames.add(lValueName) && !ssaValueNumberingStack().hasStateVar(lValueName)) {
                lvaluesToReturn.add(lVal);
            }
        }

        // 3. Arrange these varDecls alphabetically to make the tests neater
        Collections.sort(lvaluesToReturn, (o1, o2) -> (lvalues().lvalue(o1).compareTo(lvalues().lvalue(o2))));

        return lvaluesToReturn;
    }
}
