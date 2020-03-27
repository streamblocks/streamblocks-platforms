package ch.epfl.vlsc.sw.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.GeneratorVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.stmt.StmtAssignment;
import se.lth.cs.tycho.ir.stmt.StmtCall;
import se.lth.cs.tycho.ir.stmt.lvalue.LValueVariable;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.Type;

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

    default MemoryStack memoryStack() {
        return backend().memoryStack();
    }

    // -- Evaluate Expressions

    default String evaluateCall(Expression expression) {
        return evaluate(expression);
    }

    default String evaluateCall(ExprVariable variable) {
        IRNode parent = backend().tree().parent(variable);

        if (parent instanceof StmtCall || parent instanceof ExprApplication) {
            VarDecl decl = backend().varDecls().declaration(variable.getVariable());
            IRNode parentDecl = backend().tree().parent(decl);
            Instance instance = backend().instancebox().get();
            String name = instance.getInstanceName();
            return name + "_" + variable.getVariable().getName();
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
        IRNode parent = backend().tree().parent(variable);
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
        } else {
            return "(&" + variables().name(ref.getVariable()) + ")";
        }
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
        emitter().emit("%s = %s;", declarations().declarationTemp(types().type(input), tmp), backend().defaultValues().defaultValue(types().type(input)));
        //}
        if (type instanceof AlgebraicType) {
           // memoryStack().trackPointer(tmp, type);
        }


        if (input.hasRepeat()) {
            if (input.getOffset() == 0) {
                emitter().emit("pinPeekRepeat_%s(%s, %s, %d);", channelsutils().inputPortTypeSize(input.getPort()), channelsutils().definedInputPort(input.getPort()), tmp, input.getRepeat());
            } else {
                throw new RuntimeException("not implemented");
            }
        } else {
            if (input.getOffset() == 0) {
                emitter().emit("%s = pinPeekFront_%s(%s);", tmp, channelsutils().inputPortTypeSize(input.getPort()), channelsutils().definedInputPort(input.getPort()));
            } else {
                emitter().emit("%s = pinPeek_%s(%s, %d);", tmp, channelsutils().inputPortTypeSize(input.getPort()), channelsutils().definedInputPort(input.getPort()), input.getOffset());
            }
        }

        return tmp;
    }

    void evaluateWithLvalue(String lvalue, Expression expr);

    default void evaluateWithLvalue(String lvalue, ExprInput input) {
        String type = channelsutils().inputPortTypeSize(input.getPort());
        if (input.hasRepeat()) {
            if (input.getOffset() == 0) {
                emitter().emit("pinPeekRepeat_%s(%s, %s, %d);", type, channelsutils().definedInputPort(input.getPort()), lvalue, input.getRepeat());
            } else {
                throw new RuntimeException("not implemented");
            }
        } else {
            if (input.getOffset() == 0) {
                emitter().emit("%s = pinPeekFront_%s(%s);", lvalue, type, channelsutils().definedInputPort(input.getPort()));
            } else {
                emitter().emit("%s = pinPeek_%s(%s, %d);", lvalue, type, channelsutils().definedInputPort(input.getPort()), input.getOffset());
            }
        }
    }


    /**
     * Evaluate binary expression
     *
     * @param binaryOp
     * @return
     */

    default String evaluate(ExprBinaryOp binaryOp) {
        assert binaryOp.getOperations().size() == 1 && binaryOp.getOperands().size() == 2;
        String operation = binaryOp.getOperations().get(0);
        Expression left = binaryOp.getOperands().get(0);
        Expression right = binaryOp.getOperands().get(1);
        switch (operation) {
            case "+":
            case "-":
            case "*":
            case "/":
            case "<":
            case "<=":
            case ">":
            case ">=":
            case "==":
            case "!=":
            case "<<":
            case ">>":
            case "&":
            case "|":
            case "^":
                return String.format("(%s %s %s)", evaluate(left), operation, evaluate(right));
            case "=":
                return String.format("%s == %s", evaluate(left), evaluate(right));
            case "mod":
                return String.format("(%s %% %s)", evaluate(left), evaluate(right));
            case "and":
            case "&&":
                String andResult = variables().generateTemp();
                emitter().emit("_Bool %s;", andResult);
                emitter().emit("if (%s) {", evaluate(left));
                emitter().increaseIndentation();
                memoryStack().enterScope();
                emitter().emit("%s = %s;", andResult, evaluate(right));
                memoryStack().exitScope();
                emitter().decreaseIndentation();
                emitter().emit("} else {");
                emitter().increaseIndentation();
                memoryStack().enterScope();
                emitter().emit("%s = false;", andResult);
                memoryStack().exitScope();
                emitter().decreaseIndentation();
                emitter().emit("}");
                return andResult;
            case "||":
            case "or":
                String orResult = variables().generateTemp();
                emitter().emit("_Bool %s;", orResult);
                emitter().emit("if (%s) {", evaluate(left));
                emitter().increaseIndentation();
                emitter().emit("%s = true;", orResult);
                emitter().decreaseIndentation();
                emitter().emit("} else {");
                emitter().increaseIndentation();
                memoryStack().enterScope();
                emitter().emit("%s = %s;", orResult, evaluate(right));
                memoryStack().exitScope();
                emitter().decreaseIndentation();
                emitter().emit("}");
                return orResult;
            default:
                throw new UnsupportedOperationException(operation);
        }
    }

    /**
     * Evaluate unary expression
     *
     * @param unaryOp
     * @return
     */
    default String evaluate(ExprUnaryOp unaryOp) {
        switch (unaryOp.getOperation()) {
            case "-":
            case "~":
                return String.format("%s(%s)", unaryOp.getOperation(), evaluate(unaryOp.getOperand()));
            case "not":
                return String.format("!%s", evaluate(unaryOp.getOperand()));
            default:
                throw new UnsupportedOperationException(unaryOp.getOperation());
        }
    }

    /**
     * Evaluate comprehensoin expression
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
        list.getElements().forEach(element ->
                emitter().emit("%s[%s++] = %s;", result, index, evaluate(element))
        );
    }

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
                memoryStack().enterScope();
            }
            action.run();
            List<VarDecl> reversed = new ArrayList<>(varDecls);
            Collections.reverse(reversed);
            for (VarDecl d : reversed) {
                emitter().emit("%s++;", variables().declarationName(d));
                memoryStack().exitScope();
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


    /**
     * Evaluate an indexer expression
     *
     * @param indexer
     * @return
     */
    default String evaluate(ExprIndexer indexer) {
        VarDecl varDecl = evalExprIndexVar(indexer);

        Optional<String> str = Optional.empty();
        String ind;
        if (indexer.getStructure() instanceof ExprIndexer) {
            ListType type = (ListType) backend().types().declaredType(varDecl);

            List<Integer> sizeByDim = typeseval().sizeByDimension((ListType) type.getElementType());
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

    /**
     * Evaluate expression if
     *
     * @param expr
     * @return
     */
    default String evaluate(ExprIf expr) {
        Type type = types().type(expr);
        String temp = variables().generateTemp();
        if (type instanceof AlgebraicType) {
            memoryStack().trackPointer(temp, type);
        }
        String decl = declarations().declarationTemp(type, temp);
        emitter().emit("%s = %s;", decl, backend().defaultValues().defaultValue(type));
        emitter().emit("if (%s) {", evaluate(expr.getCondition()));
        emitter().increaseIndentation();
        memoryStack().enterScope();
        Type thenType = types().type(expr.getThenExpr());
        String thenValue = evaluate(expr.getThenExpr());
        backend().statements().copy(type, temp, thenType, thenValue);
        memoryStack().exitScope();
        emitter().decreaseIndentation();
        emitter().emit("} else {");
        emitter().increaseIndentation();
        memoryStack().enterScope();
        Type elseType = types().type(expr.getElseExpr());
        String elseValue = evaluate(expr.getElseExpr());
        backend().statements().copy(type, temp, elseType, elseValue);
        memoryStack().exitScope();
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
        for (Expression parameter : apply.getArgs()) {
            parameters.add(evaluate(parameter));
        }


        fn = evaluateCall(apply.getFunction());
        Type type = types().type(apply);
        String result = variables().generateTemp();
        String decl = declarations().declarationTemp(types().type(apply), result);
        if ((type instanceof AlgebraicType) || (type instanceof ListType && backend().typeseval().isAlgebraicTypeList((ListType) type))) {
            memoryStack().trackPointer(result, type);
        }
        emitter().emit("%s = %s(%s);", decl, fn, String.join(", ", parameters));
        return result;
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
            backend().memoryStack().trackPointer(name, type);
            emitter().emit("%s = %s;", declarations().declaration(type, name),  backend().defaultValues().defaultValue(type));
            backend().statements().copy(type, name, types().type(decl.getValue()), evaluate(decl.getValue()));
        }
        return evaluate(let.getBody());
    }

    default String evaluate(ExprTypeConstruction construction) {
        String fn = backend().algebraic().constructor(construction.getConstructor());
        List<String> parameters = new ArrayList<>();

        String result = variables().generateTemp();
        parameters.add(String.format("&%s", result));
        for (Expression parameter : construction.getArgs()) {
            parameters.add(evaluate(parameter));
        }

        String decl = declarations().declaration(types().type(construction), result);

        emitter().emit("%s = %s;", decl, backend().defaultValues().defaultValue(types().type(construction)));
        emitter().emit("%s(%s);", fn, String.join(", ", parameters));
        memoryStack().trackPointer(result, types().type(construction));
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
