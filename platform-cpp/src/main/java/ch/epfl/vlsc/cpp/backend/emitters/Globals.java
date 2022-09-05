package ch.epfl.vlsc.cpp.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.compiler.SourceFile;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.decl.VarDecl;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.Type;
import ch.epfl.vlsc.cpp.backend.CppBackend;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Module
public interface Globals {

    @Binding(BindingKind.INJECTED)
    CppBackend backend();


    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateHeader() {
        Path mainTarget = PathUtils.getTargetCodeGenInclude(backend().context()).resolve("globals.h");
        emitter().open(mainTarget);
        emitter().emit("#ifndef __GLOBAL__");
        emitter().emit("#define __GLOBAL__");
        emitter().emitNewLine();
        emitter().emit("#include  <cstdint>");
        emitter().emitNewLine();


        Map<QID, List<SourceUnit>> sourceunitbyQID = getSourceUnitbyQid();

        namespaceDeclaration(sourceunitbyQID);

        emitter().emit("#endif // __GLOBALS__");
        emitter().emitNewLine();

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
            if (!decl.isExternal()) {
                Type type = backend().types().declaredType(decl);
                if (type instanceof CallableType) {
                    backend().callables().callableDefinition("", decl.getValue());
                    //emitter().emitNewLine();
                } else {
                    String d = backend().declarations().declaration(type, backend().variables().declarationName(decl));
                    String v = backend().expressions().evaluate(decl.getValue());
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


}
