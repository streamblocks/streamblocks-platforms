package ch.epfl.vlsc.backend.cpp;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.compiler.SourceFile;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.ir.type.FunctionTypeExpr;
import se.lth.cs.tycho.ir.type.NominalTypeExpr;
import se.lth.cs.tycho.ir.type.ProcedureTypeExpr;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;

import javax.xml.transform.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

        emitter().close();
    }

    default void generateGlobalHeader(Path path) {
        Map<QID, List<SourceUnit>> sourceunitbyQID = getSourceUnitbyQid();
        emitter().open(path);
        emitter().emit("#ifndef GLOBAL_H");
        emitter().emit("#define GLOBAL_H");
        emitter().emit("");
        emitter().emit("#include <stdlib.h>");
        emitter().emit("#include <stdint.h>");
        emitter().emit("#include <stdbool.h>");
        emitter().emit("");

        backend().lists().declareListTypes();
        emitter().emit("");

        namespaceDeclaration(sourceunitbyQID);

        emitter().emit("#endif");
        emitter().close();
    }


    default void namespaceDeclaration(Map<QID, List<SourceUnit>> sourceUnitByQID) {
        for (QID qid : sourceUnitByQID.keySet()) {
            // -- Starting namespace
            for (String n : qid.parts()) {
                emitter().emit("namespace %s {", n);
                emitter().increaseIndentation();
            }

            // -- Global var declaration
            globalVariableDeclarations(sourceUnitByQID.get(qid).stream().flatMap(unit -> unit.getTree().getVarDecls().stream()));

            //-- Ending namespace
            for (int i = 0; i < qid.getNameCount(); i++) {
                emitter().decreaseIndentation();
                emitter().emit("}");
            }

        }
    }

    default Stream<VarDecl> getGlobalVarDecls() {
        return backend().task()
                .getSourceUnits().stream()
                .flatMap(unit -> unit.getTree().getVarDecls().stream());
    }


    default Map<QID, List<SourceUnit>> getSourceUnitbyQid() {
        Map<QID, List<SourceUnit>> qidUnits = new HashMap<>();
        backend().task().getSourceUnits().forEach(s -> {
            QID qid = s.getTree().getQID();
            if (s instanceof SourceFile)
                if (qidUnits.containsKey(qid)) {
                    qidUnits.get(qid).add(s);
                } else {
                    List<SourceUnit> su = new ArrayList<>();
                    su.add(s);
                    qidUnits.put(qid, su);
                }
        });

        return qidUnits;
    }

    default void globalVariableDeclarations(Stream<VarDecl> varDecls) {
        varDecls.forEach(decl -> {
            if(decl.isExternal()){
                backend().callables().externalCallableDeclaration(decl);
            }else{
                Type type = types().declaredType(decl);
                if (type instanceof CallableType) {
                    backend().callables().callableDefinition("", decl.getValue());
                    //emitter().emitNewLine();
                } else {
                    String d = code().declaration(type, backend().variables().declarationName(decl));
                    String v = code().evaluate(decl.getValue());
                    if (decl.isConstant()) {
                        emitter().emit("const %s = %s;", d, v);
                    } else {
                        emitter().emit("%s;", d);
                    }
                    //emitter().emitNewLine();
                }
            }
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
