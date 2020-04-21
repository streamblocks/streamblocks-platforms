package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

@Module
public interface Globals {
    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void globalSource() {
        Path globalSourcePath = PathUtils.getTargetCodeGenSource(backend().context()).resolve("globals.c");

        emitter().open(globalSourcePath);

        backend().includeUser("globals.h");
        emitter().emitNewLine();

        emitter().emit("// -- Type helper function Definitions");
        backend().algebraic().defineAlgebraicTypeHelperFunctions();

        emitter().emit("// -- Global variables");
        globalVariableDefinition(getGlobalVarDecls());
        emitter().emitNewLine();

        emitter().emit("// -- External Callables Definition");
        backend().task().walk().forEach(backend().callablesInActors()::externalCallableDefinition);
        emitter().emitNewLine();

        emitter().emit("// -- Glabal Variable Initialization");
        globalCallables(getGlobalVarDecls());
        emitter().close();


    }

    default void globalHeader() {

        Path globalHeaderPath = PathUtils.getTargetCodeGenInclude(backend().context()).resolve("globals.h");

        emitter().open(globalHeaderPath);

        emitter().emit("#ifndef __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
        emitter().emit("#define __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
        emitter().emitNewLine();

        // -- Headers
        backend().includeSystem("stdint.h");
        backend().includeSystem("stdbool.h");
        backend().includeSystem("stdlib.h");
        backend().includeSystem("string.h");
        backend().includeUser("types.h");
        emitter().emitNewLine();

        emitter().emit("// -- Type declarations");
        backend().algebraic().declareAlgebraicTypes();

        emitter().emit("// -- External Callables Declaration");
        backend().task().walk().forEach(backend().callablesInActors()::externalCallableDeclaration);
        emitter().emitNewLine();
        emitter().emit(" // -- Global Variables Declaration");
        globalVariableDeclarations(getGlobalVarDecls());

        emitter().emitNewLine();
        emitter().emit("#endif // __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());


        emitter().close();
    }

    default Stream<VarDecl> getGlobalVarDecls() {
        return backend().task()
                .getSourceUnits().stream()
                .flatMap(unit -> unit.getTree().getVarDecls().stream());
    }

    default void globalVariableDeclarations(Stream<VarDecl> varDecls) {
        varDecls.forEach(decl -> {
            Type type = backend().types().declaredType(decl);
            if (type instanceof CallableType) {
                if (decl.getValue() != null) {
                    Expression expr = decl.getValue();
                    if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                        emitter().emit("extern %s;", backend().callablesInActors().callablePrototypes(expr));
                        emitter().emitNewLine();
                    }
                }
            } else {
                if (decl.getValue() instanceof ExprList) {
                    String d = backend().declarations().declaration(type, backend().variables().declarationName(decl));
                    emitter().emit("extern const %s;", d);
                } else {
                    emitter().emit("#define %s  %s", backend().variables().declarationName(decl), backend().expressionEval().evaluate(decl.getValue()));
                }
            }
        });
    }


    default void globalCallables(Stream<VarDecl> varDecls) {
        varDecls.forEach(decl -> {
            Type type = backend().types().declaredType(decl);
            if (type instanceof CallableType) {
                if (decl.getValue() != null) {
                    Expression expr = decl.getValue();
                    if (expr instanceof ExprLambda || expr instanceof ExprProc) {
                        backend().callablesInActors().callableDefinition(expr);
                        emitter().emitNewLine();
                    }
                }
            }
        });
    }

    default void globalVariableDefinition(Stream<VarDecl> varDecls) {
        varDecls.forEach(decl -> {
            Type type = backend().types().declaredType(decl);
            if (!(type instanceof CallableType)) {
                if (decl.getValue() instanceof ExprList) {
                    String d = backend().declarations().declaration(backend().types().declaredType(decl), backend().variables().declarationName(decl));
                    emitter().emit("const %s = {%s};", d, backend().expressionEval().evaluateExprList(decl.getValue()));
                }
                // emitter().emit("%s;", d);
            }
        });
    }
}
