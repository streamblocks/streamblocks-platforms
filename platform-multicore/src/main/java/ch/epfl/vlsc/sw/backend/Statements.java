package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.*;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueIndexer;
import se.lth.cs.tycho.type.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Module
public interface Statements {
    @Binding(BindingKind.INJECTED)
    MulticoreBackend backend();

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

    void execute(Statement stmt);

    @Binding(BindingKind.LAZY)
    default List profilingOp() {
        return new ArrayList<String>();
    }


    /*
     * Statement Consume
     */

    default void execute(StmtConsume consume) {
        Type type = channelsutils().inputPortType(consume.getPort());
        if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), consume.getPort().getName())) {
            if (consume.getNumberOfTokens() > 1) {
                emitter().emit("pinConsumeRepeat_%s(%s, %d);", channelsutils().inputPortTypeSize(consume.getPort()), channelsutils().definedInputPort(consume.getPort()), consume.getNumberOfTokens());
                backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_LIST_LOAD += 1;");
                if (!backend().typeseval().isScalar(type)) {
                    String tmp = variables().generateTemp();
                    emitter().emit("for(size_t %s = 0; %1$s < %d; %1$s++){", tmp, consume.getNumberOfTokens());
                    if(type instanceof ListType){
                        emitter().emit("\tdelete[] (%s*) %s[%s];", typeseval().type(typeseval().innerType(type)), channelsutils().definedInputPort(consume.getPort()) + "_peek", tmp);
                    }else{
                        emitter().emit("\tdelete (%s*) %s[%s];", typeseval().type(typeseval().innerType(type)), channelsutils().definedInputPort(consume.getPort()) + "_peek", tmp);
                    }
                    emitter().emit("}");
                }
            } else {
                emitter().emit("pinConsume_%s(%s);", channelsutils().inputPortTypeSize(consume.getPort()), channelsutils().definedInputPort(consume.getPort()));
                backend().statements().profilingOp().add("__opCounters->prof_DATAHANDLING_LOAD += 1;");

                if (!backend().typeseval().isScalar(type)) {
                    if(type instanceof ListType){
                        emitter().emit("delete[] %s;", channelsutils().definedInputPort(consume.getPort()) + "_peek");
                    }else{
                        emitter().emit("delete %s;", channelsutils().definedInputPort(consume.getPort()) + "_peek");
                    }
                }
            }
        }
    }

    /*
     * Statement Write
     */

    default void execute(StmtWrite write) {
        if (backend().channelsutils().isSourceConnected(backend().instancebox().get().getInstanceName(), write.getPort().getName())) {
            emitter().emitNewLine();
            if (write.getRepeatExpression() == null) {
                Type type = types().portType(write.getPort());
                String portType;
                if (!backend().typeseval().isScalar(type)) {
                    portType = "ref";
                } else {
                    portType = typeseval().type(type);
                }
                String tmp = variables().generateTemp();

                if (!backend().typeseval().isScalar(type)) {
                    if (type instanceof ListType) {
                        String maxIndex = backend().typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
                        emitter().emit("%s* %s = new %1$s[%s];", typeseval().type(typeseval().innerType(type)), tmp, maxIndex);
                    } else {
                        emitter().emit("%s* %s = nullptr;", typeseval().type(typeseval().innerType(type)), tmp);
                    }
                } else {
                    emitter().emit("%s;", declarartions().declaration(types().portType(write.getPort()), tmp));
                }

                for (Expression expr : write.getValues()) {
                    if (type instanceof ListType) {
                        String maxIndex = backend().typeseval().sizeByDimension((ListType) type).stream().map(Object::toString).collect(Collectors.joining("*"));
                        emitter().emit("std::memcpy(%s,%s, sizeof(%s) * %s);", tmp, expressioneval().evaluate(expr), typeseval().type(typeseval().innerType(type)), maxIndex);
                    } else {
                        emitter().emit("%s = %s;", tmp, expressioneval().evaluate(expr));
                    }
                    emitter().emit("pinWrite_%s(%s, %s);", portType, channelsutils().definedOutputPort(write.getPort()), tmp);
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
                        emitter().emit("\tpinWrite_%s(%s, %s[%s]);", channelsutils().outputPortTypeSize(write.getPort()), channelsutils().definedOutputPort(write.getPort()), value, index);
                        profilingOp().add("__opCounters->prof_DATAHANDLING_STORE += 1;");
                        emitter().emit("}");
                    } else {
                        emitter().emit("pinWriteRepeat_%s(%s, %s, %s);", channelsutils().outputPortTypeSize(write.getPort()), channelsutils().definedOutputPort(write.getPort()), value, repeat);
                        profilingOp().add("__opCounters->prof_DATAHANDLING_LIST_STORE += 1;");
                    }
                } else {
                    emitter().emit("pinWriteRepeat_%s(%s, %s, %s);", channelsutils().outputPortTypeSize(write.getPort()), channelsutils().definedOutputPort(write.getPort()), value, repeat);
                    profilingOp().add("__opCounters->prof_DATAHANDLING_LIST_STORE += 1;");
                }

            } else {
                throw new Error("not implemented");
            }
        }
    }

    /*
     * Statement Assign
     */

    default void execute(StmtAssignment assign) {
        Type type = types().type(assign.getLValue());
        String lvalue = lvalues().lvalue(assign.getLValue());
        //if ((type instanceof ListType && assign.getLValue() instanceof LValueVariable) && !(assign.getExpression() instanceof ExprList)) {
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
                    copy(type, lvalue, types().type(assign.getExpression()), expressioneval().evaluate(assign.getExpression()));
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
                copy(type, lvalue, types().type(assign.getExpression()), expressioneval().evaluate(assign.getExpression()));
            }
        }
        //}
        profilingOp().add("__opCounters->prof_DATAHANDLING_ASSIGN += 1;");
    }

    default void copy(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        emitter().emit("%s = %s;", lvalue, rvalue);
    }

    default void copy(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue) {
        //if (!lvalueType.equals(rvalueType)) {
        String maxIndex = typeseval().sizeByDimension(lvalueType).stream().map(Object::toString).collect(Collectors.joining(" * "));
        String index = variables().generateTemp();
        emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, maxIndex);
        emitter().increaseIndentation();
        emitter().emit("%s[%s] = %s[%2$s];", lvalue, index, rvalue);
        emitter().decreaseIndentation();
        emitter().emit("}");
        //}
    }

    default void copySubAccess(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue, String
            singleDimIndex) {
        //if (!lvalueType.equals(rvalueType)) {
        String maxIndex = typeseval().sizeByDimension(lvalueType).stream().map(Object::toString).collect(Collectors.joining(" * "));
        String index = variables().generateTemp();
        emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, maxIndex);
        emitter().increaseIndentation();
        emitter().emit("%1$s[%2$s + %3$s] = %4$s[%3$s];", lvalue, singleDimIndex, index, rvalue);
        emitter().decreaseIndentation();
        emitter().emit("}");
        //}
    }


    default void copy(SetType lvalueType, String lvalue, SetType rvalueType, String rvalue) {
        emitter().emit("delete %s;", lvalue);
        emitter().emit("%s = new %s(%s);", lvalue, typeseval().type(lvalueType), rvalue);
    }

    default void copy(MapType lvalueType, String lvalue, MapType rvalueType, String rvalue) {
        emitter().emit("copy_%1$s(&(%2$s), %3$s);", typeseval().type(lvalueType), lvalue, rvalue);
    }

    default void copy(StringType lvalueType, String lvalue, StringType rvalueType, String rvalue) {
        emitter().emit("%2$s = %3$s;", typeseval().type(lvalueType), lvalue, rvalue);
    }

    default void copy(AlgebraicType lvalueType, String lvalue, AlgebraicType rvalueType, String rvalue) {
        emitter().emit("copy_%s(&(%s), %s);", backend().algebraic().utils().name(lvalueType), lvalue, rvalue);
    }

    default void copy(AliasType lvalueType, String lvalue, AliasType rvalueType, String rvalue) {
        copy(lvalueType.getType(), lvalue, rvalueType.getType(), rvalue);
    }

    default void copy(TupleType lvalueType, String lvalue, TupleType rvalueType, String rvalue) {
        emitter().emit("%2$s = %3$s;", typeseval().type(lvalueType), lvalue, rvalue);
    }

    /*
     * Statement Call
     */

    default void execute(StmtCall call) {
        String proc;
        List<String> parameters = new ArrayList<>();
        boolean directlyCallable = backend().callablesInActor().directlyCallable(call.getProcedure());
/*
        if (directlyCallable.isPresent()) {
            proc = directlyCallable.get();
            parameters.add("NULL");
        } else {
            String name = expressioneval().evaluate(call.getProcedure());
            proc = name + ".f";
            parameters.add(name + ".env");
        }*/

        if (!directlyCallable) {
            parameters.add("thisActor");
        }
        proc = expressioneval().evaluateCall(call.getProcedure());

        for (Expression parameter : call.getArgs()) {
            parameters.add(expressioneval().evaluate(parameter));
        }

        if (!backend().profilingbox().isEmpty()) {
            boolean isExternal = false;
            if (call.getProcedure() instanceof ExprGlobalVariable) {
                VarDecl declaration = backend().varDecls().declaration((ExprGlobalVariable) call.getProcedure());
                isExternal = declaration.isExternal();
            }

            if (!isExternal)
                parameters.add("__opCounters");
        }
        emitter().emit("%s(%s);", proc, String.join(", ", parameters));
        profilingOp().add("__opCounters->prof_DATAHANDLING_CALL += 1;");
    }

    /*
     * Statement Block
     */
    default void execute(StmtBlock block) {
        emitter().emit("{");
        emitter().increaseIndentation();
        for (VarDecl decl : block.getVarDecls()) {
            if (!backend().profilingbox().isEmpty()) {
                profilingOp().clear();
            }
            String declarationName = variables().declarationName(decl);

            if (decl.getValue() != null) {
                if (decl.getValue() instanceof ExprInput) {
                    ExprInput input = (ExprInput) decl.getValue();
                    Type pType = types().portType(input.getPort());
                    Type t = types().declaredType(decl);


                    if (pType instanceof ListType) {
                        String maxIndex = backend().typeseval().sizeByDimension((ListType) pType).stream().map(Object::toString).collect(Collectors.joining("*"));

                        if (input.hasRepeat()) {
                            emitter().emit("%s* %s[%s];", typeseval().type(typeseval().innerType(pType)), declarationName, input.getRepeat());
                        } else {
                            emitter().emit("%s* %s;", typeseval().type(typeseval().innerType(pType)), declarationName, maxIndex);
                        }
                    } else {
                        String d = declarartions().declarationTemp(t, declarationName);
                        emitter().emit("%s;", d);
                    }

                    if (backend().channelsutils().isTargetConnected(backend().instancebox().get().getInstanceName(), input.getPort().getName())) {
                        expressioneval().evaluateWithLvalue(backend().variables().declarationName(decl), (ExprInput) decl.getValue());
                    } else {
                        copy(t, declarationName, types().type(decl.getValue()), expressioneval().evaluate(decl.getValue()));
                    }
                } else {
                    Type t = types().declaredType(decl);
                    copy(t, declarationName, types().type(decl.getValue()), expressioneval().evaluate(decl.getValue()));
                }
                emitter().emitNewLine();
            } else {
                Type t = types().declaredType(decl);
                String d = declarartions().declarationTemp(t, declarationName);
                emitter().emit("%s;", d);
            }
            if (!backend().profilingbox().isEmpty()) {
                profilingOp().forEach(s -> emitter().emit((String) s));
            }
        }

        if (backend().profilingbox().isEmpty()) {
            block.getStatements().forEach(this::execute);
        } else {
            for (Statement stmt : block.getStatements()) {
                profilingOp().clear();
                execute(stmt);
                profilingOp().forEach(s -> emitter().emit((String) s));
            }
        }

        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    /*
     * Statement If
     */

    default void execute(StmtIf stmt) {
        emitter().emit("if (%s) {", expressioneval().evaluate(stmt.getCondition()));
        emitter().increaseIndentation();
        stmt.getThenBranch().forEach(this::execute);
        emitter().decreaseIndentation();
        if (stmt.getElseBranch() != null) {
            if (stmt.getElseBranch().size() > 0) {
                emitter().emit("} else {");
                emitter().increaseIndentation();
                stmt.getElseBranch().forEach(this::execute);
                emitter().decreaseIndentation();
            }
        }
        emitter().emit("}");
        if (!backend().profilingbox().isEmpty()) {
            profilingOp().add("__opCounters->prof_FLOWCONTROL_IF += 1;");
        }
    }

    /*
     * Statement Foreach
     */

    default void execute(StmtForeach foreach) {
        forEach(foreach.getGenerator().getCollection(), foreach.getGenerator().getVarDecls(), () -> {
            for (Expression filter : foreach.getFilters()) {
                emitter().emit("if (%s) {", expressioneval().evaluate(filter));
                emitter().increaseIndentation();
            }
            foreach.getBody().forEach(this::execute);
            for (Expression filter : foreach.getFilters()) {
                emitter().decreaseIndentation();
                emitter().emit("}");
            }
        });
        if (!backend().profilingbox().isEmpty()) {
            profilingOp().add("__opCounters->prof_FLOWCONTROL_WHILE += 1;");
        }
    }

    /*
     * Statement While
     */

    default void execute(StmtWhile stmt) {
        emitter().emit("while (true) {");
        emitter().increaseIndentation();
        emitter().emit("if (!%s) break;", expressioneval().evaluate(stmt.getCondition()));
        stmt.getBody().forEach(this::execute);
        emitter().decreaseIndentation();
        emitter().emit("}");
        if (!backend().profilingbox().isEmpty()) {
            profilingOp().add("__opCounters->prof_FLOWCONTROL_WHILE += 1;");
        }
    }

    void forEach(Expression collection, List<GeneratorVarDecl> varDecls, Runnable action);

    default void forEach(ExprBinaryOp binOp, List<GeneratorVarDecl> varDecls, Runnable action) {
        emitter().emit("{");
        emitter().increaseIndentation();
        if (binOp.getOperations().equals(Collections.singletonList(".."))) {
            Type type = types().declaredType(varDecls.get(0));
            for (VarDecl d : varDecls) {
                emitter().emit("%s;", declarartions().declaration(type, variables().declarationName(d)));
            }
            String temp = variables().generateTemp();
            emitter().emit("%s = %s;", declarartions().declaration(type, temp), expressioneval().evaluate(binOp.getOperands().get(0)));
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
        emitter().emit("}");
    }

    default void forEach(ExprVariable var, List<GeneratorVarDecl> varDecls, Runnable action) {
        emitter().emit("{");
        emitter().increaseIndentation();
        String access = ".";
        if (variables().isScopeVariable(var.getVariable())) {
            access = "->";
        }
        Type type = types().declaredType(varDecls.get(0));
        for (VarDecl d : varDecls) {
            emitter().emit("%s::iterator it_%s = %s%sbegin();", typeseval().type(types().type(var)), variables().declarationName(d), variables().name(var.getVariable()), access);
            emitter().emit("%s;", declarartions().declaration(type, variables().declarationName(d)));
        }

        action.run();
        emitter().decreaseIndentation();
        emitter().emit("}");


        emitter().decreaseIndentation();
        emitter().emit("}");
    }

}
