package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.ChannelUtils;
import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.Port;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.*;
import se.lth.cs.tycho.type.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Module
public interface Statements {
    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

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

    default ChannelUtils channelsutils() {
        return backend().channelUtils();
    }

    default Trackable memoryStack() {
        return backend().memoryStack();
    }

    void execute(Statement stmt);

    /*
     * Statement Consume
     */

    default void execute(StmtConsume consume) {
    }

    /*
     * Statement Write
     */

    default void execute(StmtWrite write) {
        if (backend().channelUtils().isSourceConnected(backend().instancebox().get().getInstanceName(), write.getPort().getName())) {
            if (!backend().instance().portVars().containsKey(write.getPort())) {
                if (write.getRepeatExpression() == null) {
                    Type type = types().portType(write.getPort());
                    String portType;
                    if (type instanceof AlgebraicType) {
                        portType = "ref";
                    } else {
                        portType = typeseval().type(type);
                    }
                    String tmp = variables().generateTemp();
                    emitter().emit("%s = %s;", declarartions().declaration(types().portType(write.getPort()), tmp), backend().defaultValues().defaultValue(type));

                    for (Expression expr : write.getValues()) {
                        emitter().emit("%s = %s;", tmp, expressioneval().evaluate(expr));
                        emitter().emit("tokens_%1$s[(index_%1$s + (%2$d)) %% SIZE_%1$s] = %3$s;", write.getPort().getName(), write.getValues().indexOf(expr), tmp);
                    }
                } else if (write.getValues().size() == 1) {
                    Type valueType = types().type(write.getValues().get(0));
                    String value = expressioneval().evaluate(write.getValues().get(0));

                    boolean isInput = false;
                    Port port = null;
                    if (write.getValues().get(0) instanceof ExprVariable) {
                        ExprVariable var = (ExprVariable) write.getValues().get(0);
                        VarDecl decl = backend().varDecls().declaration(var);

                        if (decl.getValue() != null) {
                            if (decl.getValue() instanceof ExprInput) {
                                ExprInput e = (ExprInput) decl.getValue();
                                if (e.hasRepeat()) {
                                    isInput = true;
                                    port = e.getPort();
                                }
                            }
                        }
                    }


                    String repeat = expressioneval().evaluate(write.getRepeatExpression());

                    // -- Hack type conversion : to be fixed
                    if (valueType instanceof ListType) {
                        if (!(write.getValues().get(0) instanceof ExprComprehension)) {
                            String index = variables().generateTemp();
                            emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, repeat);
                            boolean aligned = backend().alignedBox().isEmpty() ? false : backend().alignedBox().get();
                            if (!isInput) {
                                if (aligned) {
                                    emitter().emit("\ttokens_%1$s[(index_%1$s %% SIZE_%1$s) + %2$s] = %3$s[%4$s];", write.getPort().getName(), index, value, index);
                                } else {
                                    emitter().emit("\ttokens_%1$s[(index_%1$s + (%2$s)) %% SIZE_%1$s] = %3$s[%4$s];", write.getPort().getName(), index, value, index);
                                }
                            } else {
                                if (aligned) {
                                    emitter().emit("\ttokens_%1$s[(index_%1$s  %% SIZE_%1$s) + %2$s] = tokens_%3$s[(index_%3$s %% SIZE_%3$s ) + %4$s];", write.getPort().getName(), index, port.getName(), index);
                                } else {
                                    emitter().emit("\ttokens_%1$s[(index_%1$s + (%2$s)) %% SIZE_%1$s] = tokens_%3$s[(index_%3$s + (%4$s)) %% SIZE_%3$s];", write.getPort().getName(), index, port.getName(), index);
                                }
                            }
                            emitter().emit("}");
                        }
                    } else {
                        emitter().emit("pinWriteRepeat_%s(%s, %s, %s);", channelsutils().outputPortTypeSize(write.getPort()), channelsutils().definedOutputPort(write.getPort()), value, repeat);
                    }
                } else {
                    throw new Error("not implemented");
                }
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

    default void copy(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        emitter().emit("%s = %s;", lvalue, rvalue);
    }

    default boolean isAlgebraicTypeList(Type type) {
        if (!(type instanceof ListType)) {
            return false;
        }
        ListType listType = (ListType) type;
        if (listType.getElementType() instanceof AlgebraicType || backend().alias().isAlgebraicType(listType.getElementType())) {
            return true;
        } else {
            return isAlgebraicTypeList(listType.getElementType());
        }
    }


    default void copy(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue) {

        String index = variables().generateTemp();
        emitter().emit("for (size_t %1$s = 0; %1$s < %2$s; %1$s++) {", index, lvalueType.getSize().getAsInt());
        emitter().increaseIndentation();
        copy(lvalueType.getElementType(), String.format("%s[%s]", lvalue, index), rvalueType.getElementType(), String.format("%s[%s]", rvalue, index));
        emitter().decreaseIndentation();
        emitter().emit("}");

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

    default void copy(TupleType lvalueType, String lvalue, TupleType rvalueType, String rvalue) {
        copy(backend().tuples().convert().apply(lvalueType), lvalue, backend().tuples().convert().apply(rvalueType), rvalue);
    }

    /*
     * Statement Call
     */

    default void execute(StmtCall call) {
        String proc;
        List<String> parameters = new ArrayList<>();

        proc = expressioneval().evaluateCall(call.getProcedure());

        for (Expression parameter : call.getArgs()) {
            parameters.add(expressioneval().evaluate(parameter));
        }
        emitter().emit("%s(%s);", proc, String.join(", ", parameters));
    }

    /*
     * Statement Block
     */
    default void execute(StmtBlock block) {
        emitter().emit("{");
        emitter().increaseIndentation();
        for (VarDecl decl : block.getVarDecls()) {

            Type t = types().declaredType(decl);
            String declarationName = variables().declarationName(decl);

            String d = declarartions().declarationTemp(t, declarationName);
            if (decl.getValue() != null) {
                if (decl.getValue() instanceof ExprInput) {
                    ExprInput e = (ExprInput) decl.getValue();
                    // if (!e.hasRepeat()) {
                    emitter().emit("%s = %s;", d, backend().defaultValues().defaultValue(t));
                    expressioneval().evaluateWithLvalue(backend().variables().declarationName(decl), (ExprInput) decl.getValue());
                    //}
                } else {
                    emitter().emit("%s = %s;", d, backend().defaultValues().defaultValue(t));

                    emitter().emit("{");
                    emitter().increaseIndentation();
                    String eval = expressioneval().evaluate(decl.getValue());
                    copy(t, declarationName, types().type(decl.getValue()), eval);
                    emitter().decreaseIndentation();
                    emitter().emit("}");

                }
            } else {
                emitter().emit("%s = %s;", d, backend().defaultValues().defaultValue(t));
            }

        }

        block.getStatements().forEach(this::execute);
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

}
