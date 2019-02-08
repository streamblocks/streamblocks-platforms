package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.ExprBinaryOp;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.*;
import se.lth.cs.tycho.type.Type;

import java.util.Collections;
import java.util.List;

@Module
public interface Statements {
    @Binding(BindingKind.INJECTED)
    Backend backend();

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

    default ChannelsUtils channelsutils() {
        return backend().channelsutils();
    }

    void execute(Statement stmt);

    /*
     * Statement Consume
     */

    default void execute(StmtConsume consume) {
        if (consume.getNumberOfTokens() > 1) {
            emitter().emit("pinConsumeRepeat_%s(%s, %d);", channelsutils().inputPortTypeSize(consume.getPort()), channelsutils().definedInputPort(consume.getPort()), consume.getNumberOfTokens());
        } else {
            emitter().emit("pinConsume_%s(%s);", channelsutils().inputPortTypeSize(consume.getPort()), channelsutils().definedInputPort(consume.getPort()));
        }
    }

    /*
     * Statement Write
     */

    default void execute(StmtWrite write) {

    }

    /*
     * Statement Assign
     */

    default void execute(StmtAssignment assign) {
        Type type = types().lvalueType(assign.getLValue());
        String lvalue = lvalues().lvalue(assign.getLValue());
        copy(type, lvalue, types().type(assign.getExpression()), expressioneval().evaluate(assign.getExpression()));
    }

    default void copy(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        emitter().emit("%s = %s;", lvalue, rvalue);
    }


    /*
     * Statement Call
     */

    default void execute(StmtCall call) {

    }

    /*
     * Statement Block
     */
    default void execute(StmtBlock block) {
        emitter().emit("{");
        emitter().increaseIndentation();
        //backend().callables().declareEnvironmentForCallablesInScope(block);
        for (VarDecl decl : block.getVarDecls()) {

            Type t = types().declaredType(decl);
            String declarationName = variables().declarationName(decl);
            String d = declarartions().declaration(t, declarationName);
            emitter().emit("%s;", d);
            if (decl.getValue() != null) {
                copy(t, declarationName, types().type(decl.getValue()), expressioneval().evaluate(decl.getValue()));
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
        // -- TODO: Implememnt me
    }

}
