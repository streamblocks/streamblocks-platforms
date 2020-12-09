package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.hls.platform.VivadoHLS;
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
import se.lth.cs.tycho.type.*;

import java.util.*;

@Module
public interface CallablesInActors {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();


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
                return instanceName;
            } else {
                if (withInstanceName) {
                    String className = "class_" + instanceName;
                    return className + "::" + decl.getName();
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
        backend().emitter().emit("inline %s {", lambdaHeader(instanceName, lambda, true));
        backend().emitter().emit("#pragma HLS INLINE");
        backend().emitter().increaseIndentation();
        backend().emitter().emit("return %s;", backend().expressioneval().evaluate(lambda.getBody()));
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
        backend().emitter().emit("inline %s {", procHeader(instanceName, proc, true));
        backend().emitter().emit("#pragma HLS INLINE");
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


    default void externalCallableDeclaration(IRNode varDecl) {
    }

    default void externalCallableDeclaration(VarDecl varDecl) {
        if (varDecl.isExternal()) {
            if (!VivadoHLS.externalsToIgnore.contains(varDecl.getName())) {
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
    }

    default void externalCallableDefinition(IRNode node) {
    }

    default void externalCallableDefinition(VarDecl varDecl) {
        if (varDecl.isExternal()) {
            if (!VivadoHLS.externalsToIgnore.contains(varDecl.getName())) {
                Type type = backend().types().declaredType(varDecl);
                assert type instanceof CallableType : "External declaration must be function or procedure";
                CallableType callable = (CallableType) type;
                List<String> parameterNames = new ArrayList<>();
                for (int i = 0; i < callable.getParameterTypes().size(); i++) {
                    parameterNames.add("p_" + i);
                }
                String name = externalWrapperFunctionName(varDecl);
                backend().emitter().emit("static %s {", externalCallableHeader(name, callable, parameterNames));
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
    }

    default String externalWrapperFunctionName(VarDecl external) {
        assert external.isExternal();
        return backend().variables().declarationName(external);
    }


}
