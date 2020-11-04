package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.*;

import java.util.*;

@Module
public interface CallablesInActors {
    @Binding(BindingKind.INJECTED)
    OrccBackend backend();


    /**
     * Function Name
     *
     * @param callable
     * @return
     */

    default String functionName(Expression callable) {
        assert callable instanceof ExprLambda || callable instanceof ExprProc;
        IRNode parent = backend().tree().parent(callable);
        if (parent instanceof VarDecl) {
            VarDecl decl = (VarDecl) parent;
            return backend().variables().declarationName(decl);
        } else {
            throw new UnsupportedOperationException("Unsupported Callable");
        }
    }


    default void callableDefinition(IRNode callable) {
    }

    /**
     * Callable definition for ExprLambda
     *
     * @param lambda
     */
    default void callableDefinition(ExprLambda lambda) {
        backend().emitter().emit("%s {", lambdaHeader(lambda));
        backend().emitter().increaseIndentation();
        backend().emitter().emit("return %s;", backend().expressionEval().evaluate(lambda.getBody()));
        backend().emitter().decreaseIndentation();
        backend().emitter().emit("}");
    }

    /**
     * Callable definition for ExprProc
     *
     * @param proc
     */

    default void callableDefinition(ExprProc proc) {
        backend().emitter().emit("%s {", procHeader(proc));
        backend().emitter().increaseIndentation();
        proc.getBody().forEach(backend().statements()::execute);
        backend().emitter().decreaseIndentation();
        backend().emitter().emit("}");
    }


    String callablePrototypes(IRNode callable);

    default String callablePrototypes(ExprLambda lambda) {
        return lambdaHeader(lambda);
    }

    default String callablePrototypes(ExprProc proc) {
        return procHeader(proc);
    }

    /**
     * Callable Header
     *
     * @param name
     * @param type
     * @param parameterNames
     * @return
     */
    default String callableHeader(String name, CallableType type, List<String> parameterNames) {
        List<String> parameters = new ArrayList<>();

        assert parameterNames.size() == type.getParameterTypes().size();
        for (int i = 0; i < parameterNames.size(); i++) {
            parameters.add(backend().declarations().declarationParameter(type.getParameterTypes().get(i), parameterNames.get(i)));
        }
        String result = backend().typeseval().type(type.getReturnType());
        result += " ";
        result += name;
        result += "(";
        result += String.join(", ", parameters);
        result += ")";
        return result;
    }


    default String externalCallableHeader(String name, CallableType type, List<String> parameterNames) {
        List<String> parameters = new ArrayList<>();
        assert parameterNames.size() == type.getParameterTypes().size();
        for (int i = 0; i < parameterNames.size(); i++) {
            parameters.add(backend().declarations().declarationParameter(type.getParameterTypes().get(i), parameterNames.get(i)));
        }
        String result = backend().typeseval().type(type.getReturnType());
        result += " ";
        result += name;
        result += "(";
        result += String.join(", ", parameters);
        result += ")";
        return result;
    }


    /**
     * ExprLambda Header
     *
     * @param lambda
     * @return
     */
    default String lambdaHeader(ExprLambda lambda) {
        String name = functionName(lambda);
        LambdaType type = (LambdaType) backend().types().type(lambda);
        ImmutableList<String> parameterNames = lambda.getValueParameters().map(backend().variables()::declarationName);
        return callableHeader(name, type, parameterNames);
    }

    /**
     * ExprProc Header
     *
     * @param proc
     * @return
     */
    default String procHeader(ExprProc proc) {
        String name = functionName(proc);
        ProcType type = (ProcType) backend().types().type(proc);
        ImmutableList<String> parameterNames = proc.getValueParameters().map(backend().variables()::declarationName);
        return callableHeader(name, type, parameterNames);
    }


    @Binding(BindingKind.LAZY)
    default Map<Expression, String> callablesNames() {
        return new HashMap<>();
    }

    default boolean directlyCallable(Expression expression) {
        return true;
    }

    default boolean directlyCallable(ExprLambda lambda) {
        Set<Variable> closure = backend().freeVariables().freeVariables(lambda);
        if (closure.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    default boolean directlyCallable(ExprProc proc) {
        Set<Variable> closure = backend().freeVariables().freeVariables(proc);
        if (closure.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    default boolean directlyCallable(ExprVariable var) {
        VarDecl declaration = backend().varDecls().declaration(var.getVariable());
        return directlyCallable(declaration);
    }

    default boolean directlyCallable(VarDecl declaration) {
        if (declaration.isConstant() && declaration.getValue() != null) {
            return directlyCallable(declaration.getValue());
        } else if (declaration.isExternal()) {
            return true;
        } else {
            return true;
        }
    }


    @Binding(BindingKind.LAZY)
    default Set<String> usedNames() {
        return new HashSet<>();
    }


    default void externalCallableDeclaration(IRNode varDecl) {
    }

    default void externalCallableDeclaration(VarDecl varDecl) {
        if (varDecl.isExternal()) {
            Type type = backend().types().declaredType(varDecl);
            assert type instanceof CallableType : "External declaration must be function or procedure";
            CallableType callable = (CallableType) type;
            List<String> parameterNames = new ArrayList<>();
            for (int i = 0; i < callable.getParameterTypes().size(); i++) {
                parameterNames.add("p_" + i);
            }
            backend().emitter().emit("%s;", externalCallableHeader(varDecl.getOriginalName(), callable, parameterNames));
            String name = externalWrapperFunctionName(varDecl);
            backend().emitter().emit("%s;", externalCallableHeader(name, callable, parameterNames));
        }
    }

    default void externalCallableDefinition(IRNode node) {
    }

    default void externalCallableDefinition(VarDecl varDecl) {
        if (varDecl.isExternal()) {
            Type type = backend().types().declaredType(varDecl);
            assert type instanceof CallableType : "External declaration must be function or procedure";
            CallableType callable = (CallableType) type;
            List<String> parameterNames = new ArrayList<>();
            for (int i = 0; i < callable.getParameterTypes().size(); i++) {
                parameterNames.add("p_" + i);
            }
            String name = externalWrapperFunctionName(varDecl);
            backend().emitter().emit("%s {", externalCallableHeader(name, callable, parameterNames));
            backend().emitter().increaseIndentation();
            String call = varDecl.getOriginalName() + "(" + String.join(", ", parameterNames) + ")";
            if (callable.getReturnType().equals(UnitType.INSTANCE)) {
                backend().emitter().emit("%s;", call);
            } else {
                backend().emitter().emit("return %s;", call);
            }
            backend().emitter().decreaseIndentation();
            backend().emitter().emit("}");
        }
    }

    default String externalWrapperFunctionName(VarDecl external) {
        assert external.isExternal();
        return backend().variables().declarationName(external);
    }


}
