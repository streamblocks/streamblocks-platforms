package ch.epfl.vlsc.backend.cpp;

import ch.epfl.vlsc.platformutils.ControllerToGraphviz;
import ch.epfl.vlsc.platformutils.Emitter;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import ch.epfl.vlsc.platformutils.PathUtils;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.multij.BindingKind.LAZY;

@Module
public interface Actor {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    @Binding(LAZY)
    default Set<String> actorFileNames() {
        return new LinkedHashSet<>();
    }

    default String actorFileName(String base) {
        base = "actor_" + base;
        String name = base;
        int i = 1;
        while (actorFileNames().contains(name)) {
            name = base + "_" + i;
        }
        actorFileNames().add(name);
        return name;
    }

    default void emitDefaultHeaders() {
        includeSystem("stdint.h");
        includeSystem("stdbool.h");
        includeSystem("stdlib.h");
        emitter().emit("#pragma clang diagnostic ignored \"-Wparentheses-equality\"");
    }

    default void includeSystem(String h) {
        emitter().emit("#include <%s>", h);
    }

    default void includeUser(String h) {
        emitter().emit("#include \"%s\"", h);
    }

    default String headerGuard(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
    }


    default void generateActor(Instance instance) {
        backend().instance().set(instance);
        GlobalEntityDecl actor = backend().task().getSourceUnits().stream()
                .map(SourceUnit::getTree)
                .filter(ns -> ns.getQID().equals(instance.getEntityName().getButLast()))
                .flatMap(ns -> ns.getEntityDecls().stream())
                .filter(decl -> decl.getName().equals(instance.getEntityName().getLast().toString()))
                .findFirst().get();

        String fileNameBase = actorFileName(instance.getInstanceName());
        String headerFileName = fileNameBase + ".h";
        ControllerToGraphviz dot = new ControllerToGraphviz(actor, fileNameBase, PathUtils.getAuxiliary(backend().context()).resolve(fileNameBase + ".dot"));
        dot.print();
        emitter().open(PathUtils.getTargetCodeGenInclude(backend().context()).resolve(headerFileName));
        String headerGuard = headerGuard(headerFileName);
        emitter().emit("#ifndef %s", headerGuard);
        emitter().emit("#define %s", headerGuard);
        emitDefaultHeaders();
        backend().structure().actorHdr(actor);
        emitter().emit("#endif");
        emitter().close();

        emitter().open(PathUtils.getTargetCodeGenSource(backend().context()).resolve(fileNameBase + ".cpp"));
        emitDefaultHeaders();
        includeUser("fifo.h");
        includeUser("global.h");
        includeUser(headerFileName);
        backend().structure().actorDecl(actor);
        emitter().close();
        backend().instance().clear();
    }

}
