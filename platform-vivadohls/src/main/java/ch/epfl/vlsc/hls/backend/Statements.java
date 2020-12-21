package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.cal.CalActor;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.*;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueIndexer;
import se.lth.cs.tycho.ir.stmt.lvalue.LValuePortIndexer;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Module
public interface Statements {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default ExpressionEvaluator expressioneval() {
        return backend().expressioneval();
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

    /*
     * Statement Consume
     */

    default void execute(StmtConsume consume) {

        if (consume.getNumberOfTokens() > 1) {
            if (backend().complexReadBox().get())
                emitter().emit("pinConsumeRepeatComplex(%s, %d);", consume.getPort().getName(), consume.getNumberOfTokens());
        } else {
            if (backend().complexReadBox().get()) {
                emitter().emit("pinConsumeComplex(%s);", consume.getPort().getName());
            } else {
                // -- Check if the consume is needed nevertheless
                if (!backend().instance().hasRead().get(consume.getPort())) {
                    emitter().emit("pinConsume(%s, %s);", consume.getPort().getName(), backend().channelsutils().inputPortTypeSize(consume.getPort()));
                }
            }
        }
    }


    /*
     * Statement Read
     */

    default void execute(StmtRead read) {
        boolean isProcess = false;
        Entity entity = backend().entitybox().get();
        if (entity instanceof CalActor) {
            if (((CalActor) entity).getProcessDescription() != null) {
                isProcess = true;
            }
        }
        if (read.getRepeatExpression() == null) {
            for (LValue lvalue : read.getLValues()) {
                String l = backend().lvalues().lvalue(lvalue);
                if (!isProcess) {
                    emitter().emit("pinRead(%s, %s);", read.getPort().getName(), l);
                } else {
                    emitter().emit("pinReadBlocking(%s, %s);", read.getPort().getName(), l);
                }
            }
        } else {
            for (LValue lvalue : read.getLValues()) {
                String l = backend().lvalues().lvalue(lvalue);
                String repeat = expressioneval().evaluate(read.getRepeatExpression());
                if (!isProcess) {
                    emitter().emit("pinReadRepeat(%s, %s, %s);", read.getPort().getName(), l, repeat);
                } else {
                    emitter().emit("pinReadRepeatBlocking(%s, %s, %s);", read.getPort().getName(), l, repeat);
                }
            }
        }
    }


    default void exprInputRead(String lvalue, ExprInput input) {
        if (input.hasRepeat()) {
            if (input.getOffset() == 0) {
                if (backend().complexReadBox().get())
                    emitter().emit("pinReadRepeatComplex(%s, %s, %s);", channelsutils().definedInputPort(input.getPort()), lvalue, input.getRepeat());
                else
                    emitter().emit("pinReadRepeat(%s, %s, %s);", channelsutils().definedInputPort(input.getPort()), lvalue, input.getRepeat());
            } else {
                throw new RuntimeException("not implemented");
            }
        } else {
            if (backend().complexReadBox().get()) {
                emitter().emit("pinReadComplex(%s, %s);", channelsutils().definedInputPort(input.getPort()), lvalue);
            } else {
                emitter().emit("pinRead(%s, %s);", channelsutils().definedInputPort(input.getPort()), lvalue);
                backend().instance().hasRead().put(input.getPort(), true);
            }
            /*
            if (input.getOffset() == 0) {
                emitter().emit("pinRead(%s, %s);", channelsutils().definedInputPort(input.getPort()), lvalue);
            } else {
                emitter().emit("pinRead(%s, %d, %s);", channelsutils().definedInputPort(input.getPort()), input.getOffset(), lvalue);
            }
            */
        }
    }


    /*
     * Statement Write
     */

    default void execute(StmtWrite write) {
        boolean isProcess = false;
        Entity entity = backend().entitybox().get();
        if (entity instanceof CalActor) {
            if (((CalActor) entity).getProcessDescription() != null) {
                isProcess = true;
            }
        }
        if (write.getRepeatExpression() == null) {
            String tmp = variables().generateTemp();
            emitter().emit("%s;", declarartions().declaration(types().portType(write.getPort()), tmp));
            for (Expression expr : write.getValues()) {
                emitter().emit("%s = %s;", tmp, expressioneval().evaluate(expr));
                if (!isProcess) {
                    emitter().emit("pinWrite(%s, %s);", write.getPort().getName(), tmp);
                } else {
                    emitter().emit("pinWriteBlocking(%s, %s);", write.getPort().getName(), tmp);
                }
            }
        } else if (write.getValues().size() == 1) {
            String value = expressioneval().evaluate(write.getValues().get(0));
            String repeat = expressioneval().evaluate(write.getRepeatExpression());
            if (!isProcess) {
                emitter().emit("pinWriteRepeat(%s, %s, %s);", channelsutils().definedOutputPort(write.getPort()), value, repeat);
            } else {
                emitter().emit("pinWriteRepeatBlocking(%s, %s, %s);", channelsutils().definedOutputPort(write.getPort()), value, repeat);
            }
        } else {
            throw new Error("not implemented");
        }
    }

    /*
     * Statement Assign
     */

    default void execute(StmtAssignment assign) {
        Type type = types().type(assign.getLValue());

        if (assign.getLValue() instanceof LValuePortIndexer) {
            LValuePortIndexer indexer = (LValuePortIndexer) assign.getLValue();
            String eval = expressioneval().evaluate(assign.getExpression());
            emitter().emit("pinWrite(%s, %s);", indexer.getPort().getName(), eval);
        } else {
            String lvalue = lvalues().lvalue(assign.getLValue());
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
        }
        //}
    }

    default void copySubAccess(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue, String singleDimIndex) {
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

    default void copy(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        emitter().emit("%s = %s;", lvalue, rvalue);
    }

    default void copy(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue) {
        if (!lvalue.equals(rvalue)) {
            String maxIndex = typeseval().sizeByDimension(lvalueType).stream().map(Object::toString).collect(Collectors.joining(" * "));
            String index = variables().generateTemp();
            emitter().emit("for (size_t %1$s = 0; %1$s < (%2$s); %1$s++) {", index, maxIndex);
            emitter().increaseIndentation();
            emitter().emit("%s[%s] = %s[%2$s];", lvalue, index, rvalue);
            emitter().decreaseIndentation();
            emitter().emit("}");
        }
    }

    default String compare(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        return String.format("(%s == %s)", lvalue, rvalue);
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
            emitter().emit("%s;", d);
            if (decl.getValue() != null) {
                if (decl.getValue() instanceof ExprInput) {
                    // -- Do nothing
                    exprInputRead(backend().variables().declarationName(decl), (ExprInput) decl.getValue());
                } else {
                    if (backend().context().getConfiguration().get(PlatformSettings.arbitraryPrecisionIntegers)) {
                        if (decl.getValue() instanceof ExprLiteral) {
                            ExprLiteral literal = (ExprLiteral) decl.getValue();
                            if (literal.getKind() == ExprLiteral.Kind.Integer) {
                                int radix = literal.intRadix().getAsInt();
                                String value = radix != 8 ? expressioneval().evaluate(decl.getValue()) : expressioneval().evaluate(decl.getValue()).substring(1);
                                emitter().emit("%s = %s(\"%s\", %d);", declarationName, typeseval().type(t), value, radix);
                            } else {
                                copy(t, declarationName, types().type(decl.getValue()), expressioneval().evaluate(decl.getValue()));
                            }
                        } else {
                            copy(t, declarationName, types().type(decl.getValue()), expressioneval().evaluate(decl.getValue()));
                        }
                    } else {
                        copy(t, declarationName, types().type(decl.getValue()), expressioneval().evaluate(decl.getValue()));
                    }
                }
            }

        }
        //block.getStatements().forEach(this::execute);

        for (Statement stmt : block.getStatements()) {
            execute(stmt);
        }
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    /*
     * Statement Case
     */
    default void execute(StmtCase stmt) {
        backend().patternMatching().execute(stmt);
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
