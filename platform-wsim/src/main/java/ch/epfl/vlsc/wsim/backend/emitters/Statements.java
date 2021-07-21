package ch.epfl.vlsc.wsim.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.ExprBinaryOp;
import se.lth.cs.tycho.ir.expr.ExprInput;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.stmt.Statement;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtBlock;
import se.lth.cs.tycho.ir.stmt.StmtCall;
import se.lth.cs.tycho.ir.stmt.StmtCase;
import se.lth.cs.tycho.ir.stmt.StmtConsume;
import se.lth.cs.tycho.ir.stmt.StmtForeach;
import se.lth.cs.tycho.ir.stmt.StmtIf;
import se.lth.cs.tycho.ir.stmt.StmtWhile;
import se.lth.cs.tycho.ir.stmt.StmtWrite;
import se.lth.cs.tycho.ir.stmt.lvalue.LValue;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueDeref;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueField;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueIndexer;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueNth;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.AliasType;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.MapType;
import se.lth.cs.tycho.type.SetType;
import se.lth.cs.tycho.type.StringType;
import se.lth.cs.tycho.type.TupleType;
import se.lth.cs.tycho.type.Type;
import ch.epfl.vlsc.wsim.backend.WSimBackend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Module
public interface Statements {

    @Binding(BindingKind.INJECTED)
    WSimBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default Variables variables() {
        return backend().variables();
    }

    default Types types() {
        return backend().types();
    }

    default Declarations declarations() {
        return backend().declarations();
    }

    default TypesEvaluator typeseval() {
        return backend().typeseval();
    }

    default Expressions expressions() {
        return backend().expressions();
    }

    void execute(Statement stmt);

    default void execute(StmtConsume consume) {

        if (consume.getNumberOfTokens() == 1) {
            emitter().emit("%s.%s->consume(%s);",
                    backend().instance().inputsStruct(),
                    consume.getPort().getName(),
                    backend().controllers().currentLvt());
        } else {
            emitter().emit("%s.%s->consumeRepeat(%d, %s);",
                    backend().instance().outputStruct(),
                    consume.getPort().getName(),
                    consume.getNumberOfTokens(),
                    backend().controllers().currentLvt());
        }
    }

    default void execute(StmtWrite write) {
        String portName = write.getPort().getName();
        if (write.getRepeatExpression() == null) {
            String tmp = variables().generateTemp();
            emitter().emit("%s;", declarations().declaration(types().portType(write.getPort()), tmp));
            for (Expression expr : write.getValues()) {
                emitter().emit("%s = %s;", tmp, expressions().evaluate(expr));
                emitter().emit("%s.%s->write(%s, %s);",
                        backend().instance().outputStruct(),
                        portName, tmp, backend().controllers().currentLvt());
            }
        } else if (write.getValues().size() == 1) {
            String value = expressions().evaluate(write.getValues().get(0));
            String repeat = expressions().evaluate(write.getRepeatExpression());

            String tmp = variables().generateTemp();
            emitter().emit("for(int %s=0; %1$s < %s; %1$s++){", tmp, repeat);
            {
                emitter().increaseIndentation();
                emitter().emit("%s.%s->write(%s[%s], %s);",
                        backend().instance().outputStruct(),
                        portName, value, tmp, backend().controllers().currentLvt());

                emitter().decreaseIndentation();
            }
            emitter().emit("}");
        } else {
            throw new Error("not implemented");
        }
    }

    default void execute(StmtAssignment assign) {
        Type type = types().type(assign.getLValue());
        String lvalue = lvalue(assign.getLValue());
        copy(type, lvalue, types().type(assign.getExpression()), expressions().evaluate(assign.getExpression()));
    }

    default void execute(StmtBlock block) {
        emitter().emit("{");
        emitter().increaseIndentation();

        //backend().callables().declareEnvironmentForCallablesInScope(block);
        for (VarDecl decl : block.getVarDecls()) {
            Type t = types().declaredType(decl);
            String declarationName = variables().declarationName(decl);
            String d = declarations().declaration(t, declarationName);

            if (decl.getValue() != null) {
                if(decl.getValue() instanceof ExprInput){
                    emitter().emit("%s;", d);
                    backend().expressions().evaluate((ExprInput) decl.getValue());
                } else {
                    emitter().emit("%s = %s;", d, backend().defaultValues().defaultValue(t));
                    copy(t, declarationName, types().type(decl.getValue()), expressions().evaluate(decl.getValue()));
                }
            } else{
                emitter().emit("%s = %s;", d, backend().defaultValues().defaultValue(t));
            }
        }
        block.getStatements().forEach(this::execute);
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void execute(StmtIf stmt) {
        emitter().emit("if (%s) {", expressions().evaluate(stmt.getCondition()));
        emitter().increaseIndentation();
        stmt.getThenBranch().forEach(this::execute);
        emitter().decreaseIndentation();
        if (stmt.getElseBranch() != null) {
            emitter().emit("} else {");
            emitter().increaseIndentation();
            stmt.getElseBranch().forEach(this::execute);
            emitter().decreaseIndentation();
        }
        emitter().emit("}");
    }

    void forEach(Expression collection, List<GeneratorVarDecl> varDecls, Runnable action);

    default void forEach(ExprBinaryOp binOp, List<GeneratorVarDecl> varDecls, Runnable action) {
        emitter().emit("{");
        emitter().increaseIndentation();
        if (binOp.getOperations().equals(Collections.singletonList(".."))) {
            Type type = types().declaredType(varDecls.get(0));
            for (VarDecl d : varDecls) {
                emitter().emit("%s;", declarations().declaration(type, variables().declarationName(d)));
            }
            String temp = variables().generateTemp();
            emitter().emit("%s = %s;", declarations().declaration(type, temp), expressions().evaluate(binOp.getOperands().get(0)));
            emitter().emit("while (%s <= %s) {", temp, expressions().evaluate(binOp.getOperands().get(1)));
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

    default void execute(StmtForeach foreach) {
        forEach(foreach.getGenerator().getCollection(), foreach.getGenerator().getVarDecls(), () -> {
            for (Expression filter : foreach.getFilters()) {
                emitter().emit("if (%s) {", expressions().evaluate(filter));
                emitter().increaseIndentation();
            }
            foreach.getBody().forEach(this::execute);
            for (Expression filter : foreach.getFilters()) {
                emitter().decreaseIndentation();
                emitter().emit("}");
            }
        });
    }


    default void execute(StmtCall call) {
        List<String> parameters = new ArrayList<>();

        String name = expressions().evaluate(call.getProcedure());
        String proc = name;
        for (Expression parameter : call.getArgs()) {
            String param = expressions().evaluate(parameter);
            Type type = types().type(parameter);
            parameters.add(passByValue(param, type));
        }
        emitter().emit("%s(%s);", proc, String.join(", ", parameters));
    }

    default void execute(StmtWhile stmt) {
        emitter().emit("while (true) {");
        emitter().increaseIndentation();
        emitter().emit("if (!%s) break;", expressions().evaluate(stmt.getCondition()));
        stmt.getBody().forEach(this::execute);
        emitter().decreaseIndentation();
        emitter().emit("}");
    }

    default void execute(StmtCase caseStmt) {
        backend().patmat().execute(caseStmt);
    }

    String lvalue(LValue lvalue);

    default String lvalue(LValueVariable var) {
        return variables().name(var.getVariable());
    }

    default String lvalue(LValueDeref deref) {
        return lvalue(deref.getVariable());
    }

    default String lvalue(LValueIndexer indexer) {
        return lvalueIndexing(types().type(indexer.getStructure()), indexer);
    }

    String lvalueIndexing(Type type, LValueIndexer indexer);

    default String lvalueIndexing(ListType type, LValueIndexer indexer) {
        return String.format("%s[%s]", lvalue(indexer.getStructure()), expressions().evaluate(indexer.getIndex()));
    }

    default String lvalueIndexing(MapType type, LValueIndexer indexer) {
        String index = variables().generateTemp();
        String map = lvalue(indexer.getStructure());
        String key = expressions().evaluate(indexer.getIndex());
        emitter().emit("size_t %s;", index);
        emitter().emit("for (%1$s = 0; %1$s < %2$s->size; %1$s++) {", index, map);
        emitter().increaseIndentation();
        emitter().emit("if (%s) break;", compare(type.getKeyType(), key, type.getValueType(), String.format("%s.data[%s]->key", map, index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return String.format("%s->data[%s].value", map, index);
    }

    default String lvalueIndexing(StringType type, LValueIndexer indexer) {
        return String.format("%s[%s]", lvalue(indexer.getStructure()), expressions().evaluate(indexer.getIndex()));
    }

    default String lvalue(LValueField field) {
        return String.format("%s->%s", lvalue(field.getStructure()), field.getField().getName());
    }

    default String lvalue(LValueNth nth) {
        return String.format("%s->%s", lvalue(nth.getStructure()), "_" + nth.getNth().getNumber());
    }

    default String passByValue(String param, Type type) {
        return param;
    }

    default String passByValue(String param, AlgebraicType type) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
        copy(type, tmp, type, param);
        return tmp;
    }

    default String passByValue(String param, SetType type) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
        copy(type, tmp, type, param);
        return tmp;
    }

    default String passByValue(String param, MapType type) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
        copy(type, tmp, type, param);
        return tmp;
    }

    default String passByValue(String param, StringType type) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declarations().declaration(type, tmp));
        copy(type, tmp, type, param);
        return tmp;
    }

    default String passByValue(String param, ListType type) {
        if (!typeseval().isAlgebraicTypeList(type)) {
            return param;
        }
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
        copy(type, tmp, type, param);
        return tmp;
    }

    default String passByValue(String param, TupleType type) {
        return passByValue(param, backend().tuples().convert().apply(type));
    }

    default String passByValue(String param, AliasType type) {
        return passByValue(param, type.getConcreteType());
    }

    default String returnValue(String result, Type type) {
        return result;
    }

    default String returnValue(String result, AlgebraicType type) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
        copy(type, tmp, type, result);
        return tmp;
    }

    default String returnValue(String result, SetType type) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
        copy(type, tmp, type, result);
        return tmp;
    }

    default String returnValue(String result, MapType type) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
        copy(type, tmp, type, result);
        return tmp;
    }

    default String returnValue(String result, StringType type) {
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
        copy(type, tmp, type, result);
        return tmp;
    }

    default String returnValue(String result, ListType type) {
        if (!typeseval().isAlgebraicTypeList(type)) {
            return result;
        }
        String tmp = variables().generateTemp();
        emitter().emit("%s = %s;", declarations().declaration(type, tmp), backend().defaultValues().defaultValue(type));
        copy(type, tmp, type, result);
        return tmp;
    }

    default String returnValue(String result, TupleType type) {
        return returnValue(result, backend().tuples().convert().apply(type));
    }

    default String returnValue(String result, AliasType type) {
        return returnValue(result, type.getConcreteType());
    }


    default void copy(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        emitter().emit("%s = %s;", lvalue, rvalue);
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
        emitter().emit("copy_%1$s(&(%2$s), %3$s);", backend().typeseval().type(lvalueType), lvalue, rvalue);
    }

    default void copy(MapType lvalueType, String lvalue, MapType rvalueType, String rvalue) {
        emitter().emit("copy_%1$s(&(%2$s), %3$s);", backend().typeseval().type(lvalueType), lvalue, rvalue);
    }

    default void copy(StringType lvalueType, String lvalue, StringType rvalueType, String rvalue) {
        emitter().emit("%1$s = %2$s;", lvalue, rvalue);
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

    default String compare(Type lvalueType, String lvalue, Type rvalueType, String rvalue) {
        return String.format("(%s == %s)", lvalue, rvalue);
    }

    default String compare(ListType lvalueType, String lvalue, ListType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        String index = variables().generateTemp();
        emitter().emit("%s = true;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("for (size_t %1$s = 0; (%1$s < %2$s) && %3$s; %1$s++) {", index, lvalueType.getSize().getAsInt(), tmp);
        emitter().increaseIndentation();
        emitter().emit("%s &= %s;", tmp, compare(lvalueType.getElementType(), String.format("%s[%s]", lvalue, index), rvalueType.getElementType(), String.format("%s[%s]", rvalue, index)));
        emitter().decreaseIndentation();
        emitter().emit("}");
        return tmp;
    }

    default String compare(SetType lvalueType, String lvalue, SetType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = compare_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(MapType lvalueType, String lvalue, MapType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = compare_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lvalueType), lvalue, rvalue);
        return tmp;
    }

    default String compare(StringType lvalueType, String lvalue, StringType rvalueType, String rvalue) {
        String tmp = variables().generateTemp();
        emitter().emit("%s;", declarations().declaration(BoolType.INSTANCE, tmp));
        emitter().emit("%1$s = compare_%2$s(%3$s, %4$s);", tmp, backend().typeseval().type(lvalueType), lvalue, rvalue);
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

    default String compare(TupleType lvalueType, String lvalue, TupleType rvalueType, String rvalue) {
        return compare(backend().tuples().convert().apply(lvalueType), lvalue, backend().tuples().convert().apply(rvalueType), rvalue);
    }

}
