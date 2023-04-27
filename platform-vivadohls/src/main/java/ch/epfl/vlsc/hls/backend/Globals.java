package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.meta.interp.Environment;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.value.Value;
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

        // -- Includes
        emitter().emit("#include <string>");
        emitter().emit("#include <iostream>");
        emitter().emit("#include <stdint.h>");
        emitter().emit("#include \"ap_int.h\"");
        emitter().emit("#include \"hls_math.h\"");
        emitter().emitNewLine();

        // -- Return
        emitter().emit("#include \"actor-machine.h\"");
        emitter().emitNewLine();

        // -- Types
        if (backend().algebraicTypes().types().count() != 0) {
            emitter().emit("// -- User Types");
            backend().algebraicTypes().declareAlgebraicTypes();
        }

        // -- Pins
        emitter().emit("// -- Pins");

        // -- PinPeekFront
        emitter().emit("#define pinPeekFront(NAME, value) value = io.NAME ## _peek");
        emitter().emitNewLine();

        // -- PinPeekRepeat
        emitter().emit("#define pinPeekRepeat(NAME, value, d) \\\n" +
                " {\\\n" +
                "    int tmp = __consume_ ## NAME;\\\n" +
                "    for(int i = tmp; i < d; i++){\\\n" +
                "        value[i] = NAME.read();\\\n" +
                "        __consume_ ## NAME++; \\\n" +
                "    }\\\n" +
                "}\n");

        // -- PinRead
        emitter().emit("#define pinReadComplex(NAME, value) \\");
        emitter().emit("\tNAME.read_nb(value);\\");
        emitter().emit("\t__consume_ ## NAME++;\\");
        emitter().emitNewLine();

        emitter().emit("#define pinRead(NAME, value) \\");
        emitter().emit("\tNAME.read_nb(value);\\");
        emitter().emitNewLine();


        // -- PinReadBlocking
        emitter().emit("#define pinReadBlocking(NAME, value) value = NAME.read()");
        emitter().emitNewLine();

        // -- PinReadRepeat
        emitter().emitRawLine("#define pinReadRepeat(NAME, value, d) \\\n" +
                "{\\\n" +
                "    for(int i = 0; i < d; i++){\\\n" +
                "        NAME.read_nb(value[i]);\\\n" +
                "    }\\\n" +
                "}");
        emitter().emitNewLine();

        // -- PinReadRepeat
        emitter().emitRawLine("#define pinReadRepeatComplex(NAME, value, d) \\\n" +
                "{\\\n" +
                "    for(int i = 0; i < d; i++){\\\n" +
                "        NAME.read_nb(value[i]);\\\n" +
                "        __consume_ ## NAME++; \\\n" +
                "    }\\\n" +
                "}");
        emitter().emitNewLine();

        // -- PinReadRepeatBlocking
        emitter().emitRawLine("#define pinReadRepeatBlocking(NAME, value, d) \\\n" +
                "{\\\n" +
                "\tfor(int i = 0; i < d; i++){\\\n" +
                "\t\tvalue[i] = NAME.read();\\\n" +
                "\t}\\\n" +
                "}");
        emitter().emitNewLine();

        // -- PinWrite
        emitter().emit("#define pinWrite(NAME, value) NAME.write_nb(value)");
        emitter().emitNewLine();

        // -- PinWriteBlocking
        emitter().emit("#define pinWriteBlocking(NAME, value) NAME.write(value)");
        emitter().emitNewLine();

        // -- PinWriteRepeat
        emitter().emitRawLine("#define pinWriteRepeat(NAME, value, d) \\\n" +
                "{\\\n" +
                "\tfor(int i = 0; i < d; i++){\\\n" +
                "\t\tNAME.write_nb(value[i]);\\\n" +
                "\t}\\\n" +
                "}");
        emitter().emitNewLine();

        // -- PinWriteRepeatBlocking
        emitter().emitRawLine("#define pinWriteRepeatBlocking(NAME, value, d) \\\n" +
                "{\\\n" +
                "\tfor(int i = 0; i < d; i++){\\\n" +
                "\t\tNAME.write(value[i]);\\\n" +
                "\t}\\\n" +
                "}");
        emitter().emitNewLine();

        // -- PinAvailIn
        emitter().emit("#define pinAvailIn(NAME, IO) IO.NAME ## _count");
        emitter().emitNewLine();

        // -- PinAvailOut
        emitter().emit("#define pinAvailOut(NAME, IO) IO.NAME ## _size - IO.NAME ## _count");
        emitter().emitNewLine();

        // -- PinConsume
        emitter().emitRawLine("#define pinConsume(NAME, TYPE) \\\n" +
                "{\\\n" +
                "        TYPE tmp; \\\n" +
                "        NAME.read_nb(tmp); \\\n" +
                "}");
        emitter().emitNewLine();


        // -- PinConsume
        emitter().emitRawLine("#define pinConsumeComplex(NAME) \\\n" +
                "{\\\n" +
                "    if(__consume_ ## NAME == 0) {\\\n" +
                "        NAME.read(); \\\n" +
                "    }\\\n" +
                "    __consume_ ## NAME = 0; \\\n" +
                "}");
        emitter().emitNewLine();


        emitter().emit("#define pinConsumeRepeatComplex(NAME, d) \\\n" +
                "{\\\n" +
                "    int tmp = __consume_ ## NAME;\\\n" +
                "    if(__consume_ ## NAME == 0 ){ \\\n" +
                "         for(int i = tmp; i < d; i++){\\\n" +
                "            NAME.read(); \\\n" +
                "        }\\\n" +
                "    } else {\\\n" +
                "        __consume_ ## NAME-=d; \\\n" +
                "    }\\\n" +
                "}\n");
        emitter().emitNewLine();


        // -- Headers
        backend().includeSystem("ap_int.h");
        backend().includeSystem("stdint.h");
        emitter().emitNewLine();

        // -- Global Variables Declaration
        emitter().emit("// -- External Callables Definition");
        backend().task().walk().forEach(backend().callables()::externalCallableDefinition);
        emitter().emitNewLine();

        emitter().emit("// -- External Callables Declaration");
        backend().task().walk().forEach(backend().callables()::externalCallableDeclaration);
        emitter().emitNewLine();

        emitter().emit("// -- Global variable prototypes");
        globalVariableDeclarations(getGlobalVarDecls());

        emitter().emit("// -- Global variable definition");
        globalVariableDefinition(getGlobalVarDecls());

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
                                backend().callables().callablePrototypes(backend().variables().declarationName(decl), expr);
                                emitter().emitNewLine();
                            }
                        }
                    } else {
                        if (decl.getValue() instanceof ExprComprehension) {
                            Interpreter interpreter = backend().interpreter();
                            Environment environment = new Environment();
                            Value value = interpreter.eval((ExprComprehension) decl.getValue(), environment);
                            Expression expression = backend().converter().apply(value);
                            String d = backend().declarations().declaration(backend().types().declaredType(decl), backend().variables().declarationName(decl));
                            emitter().emit("const %s = %s;", d, backend().expressioneval().evaluateWithoutTemp((ExprList) expression));
                        } else if (decl.getValue() instanceof ExprList) {
                            String d = backend().declarations().declaration(backend().types().declaredType(decl), backend().variables().declarationName(decl));
                            emitter().emit("const %s = %s;", d, backend().expressioneval().evaluateWithoutTemp((ExprList) decl.getValue()));
                        } else {
                            String d = backend().declarations().declaration(backend().types().declaredType(decl), backend().variables().declarationName(decl));
                            emitter().emit("static %s = %s;", d, backend().expressioneval().evaluate(decl.getValue()));
                            emitter().emitNewLine();
                        }
                    }
                }
        );
    }


    default void globalVariableDefinition(Stream<VarDecl> varDecls) {
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
            }
        });
    }

}
