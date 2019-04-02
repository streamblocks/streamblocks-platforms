package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.ExprLambda;
import se.lth.cs.tycho.ir.expr.ExprProc;
import se.lth.cs.tycho.ir.expr.Expression;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;

import java.nio.file.Path;
import java.util.stream.Stream;

@Module
public interface Globals {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void globalHeader() {
        Path globalSourcePath = PathUtils.getTargetCodeGenInclude(backend().context()).resolve("globals.h");
        emitter().open(globalSourcePath);

        emitter().emit("#ifndef __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
        emitter().emit("#define __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
        emitter().emitNewLine();

        // -- Pins
        emitter().emit("// -- Pins");

        // -- PinPeekFront
        emitter().emit("#define pinPeekFront(NAME, value) value = io.NAME ## _peek");
        emitter().emitNewLine();

        // -- PinRead
        emitter().emit("#define pinRead(NAME, value) NAME.read_nb(value)");
        emitter().emitNewLine();

        // -- PinReadRepeat
        emitter().emitRawLine("#define pinReadRepeat(NAME, value, d) \\\n" +
                "{\\\n" +
                "\tfor(int i = 0; i < d; i++){\\\n" +
                "\t\tNAME.read_nb(value[i]);\\\n" +
                "\t}\\\n" +
                "}");
        emitter().emitNewLine();


        // -- PinWrite
        emitter().emit("#define pinWrite(NAME, value) NAME.write_nb(value)");
        emitter().emitNewLine();

        // -- PinWriteRepeat
        emitter().emitRawLine("#define pinWriteRepeat(NAME, value, d) \\\n" +
                "{\\\n" +
                "\tfor(int i = 0; i < d; i++){\\\n" +
                "\t\tNAME.write_nb(value[i]);\\\n" +
                "\t}\\\n" +
                "}");
        emitter().emitNewLine();

        // -- PinAvailIn
        emitter().emit("#define pinAvailIn(NAME, IO) IO.NAME ## _count");
        emitter().emitNewLine();

        // -- PinAvailOut
        emitter().emit("#define pinAvailOut(NAME, IO) IO.NAME ## _size - IO.NAME ## _count");
        emitter().emitNewLine();

        // -- Headers
        backend().includeSystem("ap_int.h");
        backend().includeSystem("stdint.h");
        emitter().emitNewLine();

        // -- Global Variables Declaration
        emitter().emit("// -- Global variable declaration");
        globalVariableDeclarations(getGlobalVarDecls());

        emitter().emit("// -- External Callables Declaration");
        backend().task().walk().forEach(backend().callables()::externalCallableDefinition);
        emitter().emitNewLine();

        emitter().emit("#endif // __GLOBALS_%s__", backend().task().getIdentifier().getLast().toString().toUpperCase());
        emitter().emitNewLine();
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
                        backend().callables().callableDefinition(backend().variables().declarationName(decl), expr);
                        emitter().emitNewLine();
                    }
                }
            } else {
                String d = backend().declarations().declaration(backend().types().declaredType(decl), backend().variables().declarationName(decl));
                emitter().emit("static %s = %s;", d, backend().expressioneval().evaluate(decl.getValue()));
                emitter().emitNewLine();
            }
        });
    }

}
