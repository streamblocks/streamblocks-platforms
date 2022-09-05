package ch.epfl.vlsc.cpp.backend.emitters;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.NamespaceDecl;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.expr.ExprVariable;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.LambdaType;
import se.lth.cs.tycho.type.ProcType;
import ch.epfl.vlsc.cpp.backend.CppBackend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Module
public interface Callables {
    @Binding(BindingKind.INJECTED)
    CppBackend backend();

    /**
     * Function Name
     *
     * @param instanceName
     * @param callable
     * @return
     */

    default String functionName(String instanceName, Expression callable, boolean withInstanceName) {
        assert callable instanceof ExprLambda || callable instanceof ExprProc;
        IRNode parent = backend().tree().parent(callable);
        if (parent instanceof VarDecl) {
            VarDecl decl = (VarDecl) parent;
            IRNode declParent = backend().tree().parent(decl);
            if (declParent instanceof NamespaceDecl) {
                return decl.getName();
            } else {
                if (withInstanceName) {
                    return instanceName + "::" + decl.getName();
                } else {
                    return decl.getName();
                }
            }
        } else {
            throw new UnsupportedOperationException("Unsupported Callable");
        }
    }


    default void callableDefinition(String instanceName, IRNode callable) {
    }

    /**
     * Callable definition for ExprLambda
     *
     * @param instanceName
     * @param lambda
     */
    default void callableDefinition(String instanceName, ExprLambda lambda) {
        backend().emitter().emit("inline %s {", lambdaHeader(instanceName, lambda,true));
        backend().emitter().increaseIndentation();
        backend().emitter().emit("return %s;", backend().expressions().evaluate(lambda.getBody()));
        backend().emitter().decreaseIndentation();
        backend().emitter().emit("}");
    }

    /**
     * Callable definition for ExprProc
     *
     * @param instanceName
     * @param proc
     */

    default void callableDefinition(String instanceName, ExprProc proc) {
        backend().emitter().emit("inline %s {", procHeader(instanceName, proc,true));
        backend().emitter().increaseIndentation();
        proc.getBody().forEach(backend().statements()::execute);
        backend().emitter().decreaseIndentation();
        backend().emitter().emit("}");
    }


    default void callablePrototypes(String instanceName, IRNode callable) {
    }

    default void callablePrototypes(String instanceName, ExprLambda lambda) {
        backend().emitter().emit("%s;", lambdaHeader(instanceName, lambda, false));
    }

    default void callablePrototypes(String instanceName, ExprProc proc) {
        backend().emitter().emit("%s;", procHeader(instanceName, proc, false));
    }

    /**
     * Callable Header
     *
     * @param instanceName
     * @param name
     * @param type
     * @param parameterNames
     * @return
     */
    default String callableHeader(String instanceName, String name, CallableType type, List<String> parameterNames) {
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
     * @param instanceName
     * @param lambda
     * @return
     */
    default String lambdaHeader(String instanceName, ExprLambda lambda, boolean withInstanceName) {
        String name = functionName(instanceName, lambda, withInstanceName);
        LambdaType type = (LambdaType) backend().types().type(lambda);
        ImmutableList<String> parameterNames = lambda.getValueParameters().map(backend().variables()::declarationName);
        return callableHeader(instanceName, name, type, parameterNames);
    }

    /**
     * ExprProc Header
     *
     * @param instanceName
     * @param proc
     * @return
     */
    default String procHeader(String instanceName, ExprProc proc, boolean withInstanceName) {
        String name = functionName(instanceName, proc, withInstanceName);
        ProcType type = (ProcType) backend().types().type(proc);
        ImmutableList<String> parameterNames = proc.getValueParameters().map(backend().variables()::declarationName);
        return callableHeader(instanceName, name, type, parameterNames);
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

}