package com.streamgenomics.backend.c;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import platformutils.PathUtils;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.stream.Stream;

@Module
public interface Global {
	@Binding(BindingKind.INJECTED)
	Backend backend();

	default Emitter emitter() {
		return backend().emitter();
	}

	default Code code() {
		return backend().code();
	}

	default Types types() {
		return backend().types();
	}

	default void generateGlobalCode(Path path) {
		emitter().open(path);
		backend().main().emitDefaultHeaders();
		emitter().emit("#include \"global.h\"");
		emitter().emit("");
		backend().callables().defineCallables();
		emitter().emit("");
		globalVariableInitializer(getGlobalVarDecls());
		emitter().close();
	}

	default void generateGlobalHeader(Path path) {
		emitter().open(path);
		emitter().emit("#ifndef GLOBAL_H");
		emitter().emit("#define GLOBAL_H");
		emitter().emit("");
		emitter().emit("#include <stdlib.h>");
		emitter().emit("#include <stdint.h>");
		emitter().emit("#include <stdbool.h>");
		emitter().emit("");
		emitter().emit("void init_global_variables(void);");
		emitter().emit("");
		backend().lists().declareListTypes();
		emitter().emit("");
		backend().callables().declareCallables();
		emitter().emit("");
		backend().callables().declareEnvironmentForCallablesInScope(backend().task());
		emitter().emit("");
		globalVariableDeclarations(getGlobalVarDecls());
		emitter().emit("");
		emitter().emit("#endif");
		emitter().close();
	}

	default Stream<VarDecl> getGlobalVarDecls() {
		return backend().task()
					.getSourceUnits().stream()
					.flatMap(unit -> unit.getTree().getVarDecls().stream());
	}

	default void globalVariableDeclarations(Stream<VarDecl> varDecls) {
		varDecls.forEach(decl -> {
			Type type = types().declaredType(decl);
			String d = code().declaration(type, backend().variables().declarationName(decl));
			emitter().emit("%s;", d);
		});
	}

	default void globalVariableInitializer(Stream<VarDecl> varDecls) {
		emitter().emit("void init_global_variables() {");
		emitter().increaseIndentation();
		varDecls.forEach(decl -> {
			Type type = types().declaredType(decl);
			if (decl.isExternal() && type instanceof CallableType) {
				String wrapperName = backend().callables().externalWrapperFunctionName(decl);
				String variableName = backend().variables().declarationName(decl);
				String t = backend().callables().mangle(type).encode();
				emitter().emit("%s = (%s) { *%s, NULL };", variableName, t, wrapperName);
			} else {
				code().copy(type, backend().variables().declarationName(decl), types().type(decl.getValue()), code().evaluate(decl.getValue()));
			}
		});
		emitter().decreaseIndentation();
		emitter().emit("}");
	}
}
