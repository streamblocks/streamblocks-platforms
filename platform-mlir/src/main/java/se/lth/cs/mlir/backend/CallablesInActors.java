package se.lth.cs.mlir.backend;

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
    String[] mathIgnoreExternals = {"cos", "sin", "sqrt", "fabs"};

    @Binding(BindingKind.INJECTED)
    MlirBackend backend();

    /**
     * Function Name
     *
     * @param instanceName
     * @param callable
     * @return
     */

    default String functionName(String instanceName, Expression callable) {
        assert callable instanceof ExprLambda || callable instanceof ExprProc;
        IRNode parent = backend().tree().parent(callable);
        if (parent instanceof VarDecl) {
            VarDecl decl = (VarDecl) parent;
            IRNode declParent = backend().tree().parent(decl);
            if (declParent instanceof NamespaceDecl) {
                return instanceName;
            } else {
                return instanceName + "_" + decl.getName();
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
        backend().ssaValueNumberingStack().newBlock();

        String name = functionName(instanceName, lambda);
        LambdaType returnType = (LambdaType) backend().types().type(lambda);
        String returnTypeString = backend().typeseval().type(returnType);

        // Get every input parameter, put it on the SSA stack, format it with "ssaName: type" as required by MLIR
        // This line is an utter mess and hard to read - sorry
        List<String> parameterNames = lambda.getValueParameters()
                .map(x ->
                        "%" + backend().ssaValueNumberingStack().getVarToBeAssignedTo(backend().variables().declarationName(x)) + " : " + // Get the ssa name
                                backend().typeseval().type(backend().types().declaredType(x))); // get the SSA type
        // Then join these into a string
        String parameterNamesString = String.join(", ", parameterNames);

        backend().emitter().emit("func.func @%s(%s) -> %s {", name, parameterNamesString, returnTypeString);
        backend().emitter().increaseIndentation();

        Type inputType = backend().types().type(lambda.getBody());
        Type outputType = returnType.getReturnType();
        String returnSSA = backend().expressionEval().evaluate(lambda.getBody());
        String returnSSAConverted = backend().typeseval().castType(inputType, outputType, returnSSA);
        backend().emitter().emit("func.return %%%s : %s", returnSSAConverted, returnTypeString);

        backend().emitter().decreaseIndentation();
        backend().emitter().emit("}", instanceName);

        backend().ssaValueNumberingStack().blockDone();
    }

    /**
     * Callable definition for ExprProc
     *
     * @param instanceName
     * @param proc
     */

    default void callableDefinition(String instanceName, ExprProc proc) {
        throw new UnsupportedOperationException("Procedures not yet supported in MLIR backend");
        /*backend().emitter().emit("%s {", procHeader(instanceName, proc));
        backend().emitter().increaseIndentation();
        proc.getBody().forEach(backend().statements()::execute);
        backend().emitter().decreaseIndentation();
        backend().emitter().emit("}");*/
    }

    default void callablePrototypes(String instanceName, IRNode callable) {
    }

    default void callablePrototypes(String instanceName, ExprLambda lambda) {
        backend().emitter().emit("%s;", lambdaHeader(instanceName, lambda));
    }

    default void callablePrototypes(String instanceName, ExprProc proc) {
        backend().emitter().emit("%s;", procHeader(instanceName, proc));
    }

    /**
     * Callable Header
     *
     * @param instanceName
     * @param name
     * @param type
     * @param parameterNames
     * @param withEnv
     * @return
     */
    default String callableHeader(String instanceName, String name, CallableType type, List<String> parameterNames,
                                  boolean withEnv) {
        List<String> parameters = new ArrayList<>();
        if (withEnv) {
            parameters.add(String.format("%s *thisActor", "ActorInstance_" + instanceName));
        }
        assert parameterNames.size() == type.getParameterTypes().size();
        for (int i = 0; i < parameterNames.size(); i++) {
            parameters.add(backend().declarations().declarationParameter(type.getParameterTypes().get(i),
                    parameterNames.get(i)));
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
            parameters.add(backend().declarations().declarationParameter(type.getParameterTypes().get(i),
                    parameterNames.get(i)));
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
    default String lambdaHeader(String instanceName, ExprLambda lambda) {
        String name = functionName(instanceName, lambda);
        LambdaType type = (LambdaType) backend().types().type(lambda);
        ImmutableList<String> parameterNames = lambda.getValueParameters().map(backend().variables()::declarationName);
        return callableHeader(instanceName, name, type, parameterNames, !directlyCallable(lambda));
    }

    /**
     * ExprProc Header
     *
     * @param instanceName
     * @param proc
     * @return
     */
    default String procHeader(String instanceName, ExprProc proc) {
        String name = functionName(instanceName, proc);
        ProcType type = (ProcType) backend().types().type(proc);
        ImmutableList<String> parameterNames = proc.getValueParameters().map(backend().variables()::declarationName);
        return callableHeader(instanceName, name, type, parameterNames, !directlyCallable(proc));
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
            if (!Arrays.stream(mathIgnoreExternals).anyMatch(s -> varDecl.getOriginalName().equals(s))) {
                backend().emitter().emit("extern %s;", externalCallableHeader(varDecl.getOriginalName(), callable,
                        parameterNames));
            }
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
