package ch.epfl.vlsc.backend.cpp;

import ch.epfl.vlsc.backend.cpp.util.NameExpression;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.Variable;
import se.lth.cs.tycho.ir.decl.ParameterVarDecl;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.am.Scope;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.StmtBlock;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.type.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Module
public interface Callables {
	@Binding(BindingKind.INJECTED)
	Backend backend();
	/*
	Global scope:
	- typedef fat pointers for all different ExprLambda and ExprProc types
	- declare prototype for all ExprLambda and ExprProc
	- define function for all external VarDecls of callable type containing the extern function declaration.
	- typedef environment for all ExprLambda and ExprProc
	- define function for all ExprLambda and ExprProc

	Where declared:
	- create fat function pointer with null environment for all external declaration.
	- create fat function pointer with envorinment for all ExprLambda and ExprProc.
	 */

	default void declareCallables() {
		backend().emitter().emit("// FUNCTION AND PROCEDURE FAT POINTER TYPES");
		declareCallableFatPointerTypes();
		backend().emitter().emit("");
		backend().emitter().emit("// FUNCTION AND PROCEDURE PROTOTYPES");
		backend().task().walk().forEach(this::callablePrototype);
		backend().emitter().emit("");
		backend().emitter().emit("// EXTERNAL FUNCTION AND PROCEDURE DECLARATIONS");
		backend().task().walk().forEach(this::externalCallableDeclaration);
		backend().emitter().emit("");
	}

    // -----------------------------------------------------------------------------------------------------------------
	// typedef for fat function pointer with environment pointer
	default void declareCallableFatPointerTypes() {
		Set<CallableType> visited = new LinkedHashSet<>();
		backend().task().walk().forEach(node -> collectCallableTypes(node, visited::add));
		visited.forEach(this::declareCallableFatPointerType);
	}

	default void collectCallableTypes(IRNode node, Consumer<CallableType> collector) {}
	default void collectCallableTypes(ExprLambda lambda, Consumer<CallableType> collector) {
		collector.accept((LambdaType) backend().types().type(lambda));
	}

	default void collectCallableTypes(ExprProc proc, Consumer<CallableType> collector) {
		collector.accept((ProcType) backend().types().type(proc));
	}

	default void collectCallableTypes(VarDecl varDecl, Consumer<CallableType> collector) {
		if (varDecl.isExternal()) {
			Type type = backend().types().declaredType(varDecl);
			if (type instanceof CallableType) {
				collector.accept((CallableType) type);
			}
		}
	}

	default void declareEnvironmentForCallablesInScope(IRNode scope) {
		for (Expression callable : callablesInScope(scope)) {
			String functionName = functionName(callable);
			backend().emitter().emit("envt_%s env_%s;", functionName, functionName);
		}
	}

	default Set<Expression> callablesInScope(IRNode scope) {
		Set<Expression> result = new LinkedHashSet<>();
		collectCallablesInScope(scope, result::add);
		return result;
	}

	default void collectCallablesInScope(IRNode node, Consumer<Expression> collector) {
		node.forEachChild(child -> collectCallablesInScope(child, collector));
	}

	default void collectCallablesInScope(ExprLambda lambda, Consumer<Expression> collector) {
		collector.accept(lambda);
	}

	default void collectCallablesInScope(ExprProc proc, Consumer<Expression> collector) {
		collector.accept(proc);
	}

	default void collectCallablesInScope(ExprLet let, Consumer<Expression> collector) { }
	default void collectCallablesInScope(StmtBlock block, Consumer<Expression> collector) { }
	default void collectCallablesInScope(Entity entity, Consumer<Expression> collector) { }

	default void declareCallableFatPointerType(CallableType type) {
		String name = mangle(type).encode();
		String returnType = backend().code().type(type.getReturnType());
		Stream<String> parameterStream = type.getParameterTypes().stream()
				.map(backend().code()::type);
		String parameters = Stream.concat(Stream.of("void *restrict"), parameterStream).collect(Collectors.joining(", "));
		backend().emitter().emit("typedef struct {");
		backend().emitter().increaseIndentation();
		backend().emitter().emit("%s (*f)(%s);", returnType, parameters);
		backend().emitter().emit("void *env;");
		backend().emitter().decreaseIndentation();
		backend().emitter().emit("} %s;", name);
		backend().emitter().emit("");
	}

	// -----------------------------------------------------------------------------------------------------------------
    // -- Mangle


	NameExpression mangle(Type t);

	default NameExpression mangle(RefType type) {
		return NameExpression.seq(NameExpression.name("ref"), mangle(type.getType()));
	}

	default NameExpression mangle(CallableType type) {
		/*
		NameExpression kind = NameExpression.name("fn");
		NameExpression returnType = mangle(type.getReturnType());
		List<NameExpression> parameterTypes = type.getParameterTypes().stream().map(this::mangle).collect(Collectors.toList());
		return new NameExpression.Seq(ImmutableList.<NameExpression> builder().add(kind).add(returnType).addAll(parameterTypes).build());
*/
		return mangle(type.getReturnType());
	}

	default NameExpression mangle(UnitType type) {
		return NameExpression.name("void");
	}

	default NameExpression mangle(StringType type) {
		return NameExpression.name("str");
	}

	default NameExpression mangle(BoolType type) {
		return NameExpression.name("bool");
	}

	default NameExpression mangle(ListType type) {
		String size = type.getSize().isPresent() ? Integer.toString(type.getSize().getAsInt()) : "X";
		return NameExpression.seq(NameExpression.name("list"), mangle(type.getElementType()), NameExpression.name(size));
	}

	default NameExpression mangle(IntType type) {
		String kind;
		if (type.isSigned()) {
			kind = "i";
		} else {
			kind = "u";
		}
		String size;
		if (type.getSize().isPresent()) {
			size = Integer.toString(type.getSize().getAsInt());
		} else {
			size = "X";
		}
		return NameExpression.name(kind + size);
	}

	default NameExpression mangle(RealType type) {
		return NameExpression.seq(NameExpression.name("real"), NameExpression.name(Integer.toString(type.getSize())));
	}


    // -----------------------------------------------------------------------------------------------------------------
    // -- function prototype

	default ImmutableList<VarDecl> closure(IRNode node) {
		return backend().freeVariables().freeVariables(node).stream()
				.map(backend().varDecls()::declaration)
                .distinct()
				.collect(ImmutableList.collector());
	}
	default void  callablePrototype(IRNode callable) {};

	default void callablePrototype(ExprLambda lambda) {
		String name = functionName(lambda);
		//closureTypedef(closure(lambda), name);
		backend().emitter().emit("%s;", lambdaHeader(lambda));
	}

	default void callablePrototype(ExprProc proc) {
		String name = functionName(proc);
		//closureTypedef(closure(proc), name);
		backend().emitter().emit("%s;", procHeader(proc));
	}

	default void closureTypedef(ImmutableList<VarDecl> closure, String name) {
		backend().emitter().emit("typedef struct {");
		backend().emitter().increaseIndentation();
		for (VarDecl var : closure) {
			Type type = new RefType(backend().types().declaredType(var));
			String varName = backend().variables().declarationName(var);
			backend().emitter().emit("%s;", backend().code().declaration(type, varName));
		}
		backend().emitter().decreaseIndentation();
		backend().emitter().emit("} envt_%s;", name);
	}


    // -----------------------------------------------------------------------------------------------------------------
	// function definition (matching function prototype)
	// with environment typedef
	default void callableDefinition(String name, IRNode callable) {}

	default void callableDefinition(String name, ExprLambda lambda) {
        backend().emitter().emit("%s {", classLambdaHeader(name, lambda));
        backend().emitter().increaseIndentation();
		lambda.forEachChild(this::declareEnvironmentForCallablesInScope);
		backend().emitter().emit("return %s;", backend().code().evaluate(lambda.getBody()));
		backend().emitter().decreaseIndentation();
		backend().emitter().emit("}");
	}

	default void callableDefinition(String name, ExprProc proc) {
		backend().emitter().emit("%s {", classProcHeader(name, proc));
		backend().emitter().increaseIndentation();
		proc.getBody().forEach(backend().code()::execute);
		backend().emitter().decreaseIndentation();
		backend().emitter().emit("}");
	}

	default String envField(VarDecl decl) {
		String name = backend().variables().declarationName(decl);
		Type originalType = new RefType(backend().types().declaredType(decl));
		return backend().code().declaration(originalType, name) + ";";
	}

    // -----------------------------------------------------------------------------------------------------------------
	// closure creation
	String evaluate(Expression callable);

	@Binding(BindingKind.LAZY)
	default Map<Expression, String> callablesNames() { return new HashMap<>(); }

	@Binding(BindingKind.LAZY)
	default Map<VarDecl, String> externalNames() { return new HashMap<>(); } // TODO: persist between backends?

	@Binding(BindingKind.LAZY)
	default Set<String> usedNames() { return new HashSet<>(); }

	/** The name of the C-function */
	default String functionName(Expression callable) {
		assert callable instanceof ExprLambda || callable instanceof ExprProc;
		if (!callablesNames().containsKey(callable)) {
			IRNode parent = backend().tree().parent(callable);
			String candidate;
			if (parent instanceof VarDecl) {
				VarDecl decl = (VarDecl) parent;
				candidate = "f_" + decl.getName();
			} else {
				candidate = "f_anon";
			}
			usedNames().add(candidate);
			callablesNames().put(callable, candidate);
		}
		return callablesNames().get(callable);
	}

	default String functionNameDefinition(Expression callable){
		assert callable instanceof ExprLambda || callable instanceof ExprProc;
		IRNode parent = backend().tree().parent(callable);
		String name = "" + functionName(callable);


		return name;
	}

    // -----------------------------------------------------------------------------------------------------------------
    // -- Environment Scope

	default IRNode environmentScope(IRNode callable) {
		return backend().tree().parent(callable);
	}
	default IRNode environmentScope(ExprLambda lambda) {
		return lambda;
	}
	default IRNode environmentScope(ExprProc proc) {
		return proc;
	}
	default IRNode environmentScope(Scope scope) {
		return scope;
	}
	default IRNode environmentScope(ExprLet let) {
		return let;
	}
	default IRNode environmentScope(StmtBlock block) {
		return block;
	}
	default String environmentName(Expression callable) {
		IRNode scope = environmentScope(backend().tree().parent(callable));
		String functionName = functionName(callable);
		if (scope instanceof Scope) {
			return "self->env_" + functionName;
		} else {
			return "env_" + functionName;
		}
	}

    // -----------------------------------------------------------------------------------------------------------------
    // -- Externals

	default String externalWrapperFunctionName(VarDecl external) {
		assert external.isExternal();
		if (!externalNames().containsKey(external)) {
			int i = 0;
			String name;
			do {
				name = "external_" + i;
				i++;
			} while (usedNames().contains(name));
			externalNames().put(external, name);
			usedNames().add(name);
		}
		return externalNames().get(external);
	}

	default void externalCallableDeclaration(IRNode varDecl) { }

	default void externalCallableDeclaration(VarDecl varDecl) {
		if (varDecl.isExternal()) {
			Type type = backend().types().declaredType(varDecl);
			assert type instanceof CallableType : "External declaration must be function or procedure";
			CallableType callable = (CallableType) type;
			List<String> parameterNames = new ArrayList<>();
			for (int i = 0; i < callable.getParameterTypes().size(); i++) {
				parameterNames.add("p_" + i);
			}
			backend().emitter().emit("%s;", callableHeader(varDecl.getOriginalName(), callable, parameterNames, false));
			String name = externalWrapperFunctionName(varDecl);
			backend().emitter().emit("%s;", callableHeader(name, callable, parameterNames, true));
		}
	}

	default void externalCallableDefinition(IRNode node) { }

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
			backend().emitter().emit("%s {", callableHeader(name, callable, parameterNames, true));
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

    // -----------------------------------------------------------------------------------------------------------------
    // -- Headers

	default String lambdaHeader(ExprLambda lambda) {
		String name = functionName(lambda);
		LambdaType type = (LambdaType) backend().types().type(lambda);
		ImmutableList<String> parameterNames = lambda.getValueParameters().map(backend().variables()::declarationName);
		return callableHeader(name, type, parameterNames, false);
	}

    default String classLambdaHeader(String className, ExprLambda lambda) {
        String name = functionName(lambda);
        name = className + "::" + name;
        LambdaType type = (LambdaType) backend().types().type(lambda);
        ImmutableList<String> parameterNames = lambda.getValueParameters().map(backend().variables()::declarationName);
        return callableHeader(name, type, parameterNames, false);
    }

	default String procHeader(ExprProc proc) {
		String name = functionName(proc);
		ProcType type = (ProcType) backend().types().type(proc);
		ImmutableList<String> parameterNames = proc.getValueParameters().map(backend().variables()::declarationName);
		return callableHeader(name, type, parameterNames, false);
	}

	default String classProcHeader(String className, ExprProc proc) {
		String name = functionName(proc);
		name = className + "::" + name;
		ProcType type = (ProcType) backend().types().type(proc);
		ImmutableList<String> parameterNames = proc.getValueParameters().map(backend().variables()::declarationName);
		return callableHeader(name, type, parameterNames, false);
	}

	default String callableHeader(String name, CallableType type, List<String> parameterNames, boolean withEnv) {
		List<String> parameters = new ArrayList<>();
		if (withEnv) {
			parameters.add("void *e");
		}
		assert parameterNames.size() == type.getParameterTypes().size();
		for (int i = 0; i < parameterNames.size(); i++) {
			parameters.add(backend().code().declaration(type.getParameterTypes().get(i), parameterNames.get(i)));
		}
		String result = backend().code().type(type.getReturnType());
		result += " ";
		result += name;
		result += "(";
		result += String.join(", ", parameters);
		result += ")";
		return result;
	}

	String resultFromType(Type type);

	default String resultFromType(CallableType type) {
		return backend().code().type(type.getReturnType());
	}

	Stream<String> parametersFromType(Type type);

	default Stream<String> parametersFromType(CallableType type) {
		return type.getParameterTypes().stream().map(backend().code()::type);
	}

	Stream<String> parametersFromValue(Expression expr);

	default Stream<String> parametersFromValue(ExprProc proc) {
		return proc.getValueParameters().stream()
				.map(this::parameterDeclaration);
	}

	default Stream<String> parametersFromValue(ExprLambda lambda) {
		return lambda.getValueParameters().stream()
				.map(this::parameterDeclaration);
	}

	default String parameterDeclaration(ParameterVarDecl decl) {
		Type type = backend().types().declaredType(decl);
		String name = backend().variables().declarationName(decl);
		return backend().code().declaration(type, name);
	}

	default Optional<String> directlyCallableName(Expression expression) {
		return Optional.empty();
	}

	default Optional<String> directlyCallableName(ExprLambda lambda) {
		Set<Variable> closure = backend().freeVariables().freeVariables(lambda);
		if (closure.isEmpty()) {
			return Optional.of(functionName(lambda));
		} else {
			return Optional.empty();
		}
	}

	default Optional<String> directlyCallableName(ExprVariable var) {
		VarDecl declaration = backend().varDecls().declaration(var.getVariable());
		return directlyCallableName(declaration);
	}

	default Optional<String> directlyCallableName(VarDecl declaration) {
		if (declaration.isConstant() && declaration.getValue() != null) {
			return directlyCallableName(declaration.getValue());
		} else if (declaration.isExternal()) {
			return Optional.of(externalWrapperFunctionName(declaration));
		} else {
			return Optional.empty();
		}
	}

}
