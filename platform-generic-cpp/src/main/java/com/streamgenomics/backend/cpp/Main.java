package com.streamgenomics.backend.cpp;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.reporting.CompilationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.multij.BindingKind.LAZY;

@Module
public interface Main {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default Channels channels() {
        return backend().channels();
    }

    default MainNetwork mainNetwork() {
        return backend().mainNetwork();
    }

    default void generateCode() {
        global();
        fifo();
        actors();
        main();
    }

    default void global() {
        emitter().open(target().resolve("global.h"));
        backend().global().generateGlobalHeader();
        emitter().close();
        emitter().open(target().resolve("global.c"));
        backend().global().generateGlobalCode();
        emitter().close();
    }

    default void fifo() {
        emitter().open(target().resolve("fifo.h"));
        channels().fifo_h();
        emitter().close();
    }

    default void main() {
        Path mainTarget = target().resolve("main.c");
        emitter().open(mainTarget);
        CompilationTask task = backend().task();
        includeSystem("stdlib.h");
        includeSystem("stdio.h");
        includeSystem("stdbool.h");
        includeSystem("stdint.h");
        includeSystem("signal.h");
        includeSystem("string.h");
        includeUser("fifo.h");
        includeUser("prelude.h");
        includeUser("global.h");
        include();
        for (String fileNameBase : actorFileNames()) {
            includeUser(fileNameBase + ".h");
        }
        channels().inputActorCode();
        channels().outputActorCode();
        mainNetwork().main(task.getNetwork());
        emitter().close();
    }

    default void actors() {
        backend().task().getNetwork().getInstances().forEach(this::actor);
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

    default void actor(Instance instance) {
        backend().instance().set(instance);
        GlobalEntityDecl actor = backend().task().getSourceUnits().stream()
                .map(SourceUnit::getTree)
                .filter(ns -> ns.getQID().equals(instance.getEntityName().getButLast()))
                .flatMap(ns -> ns.getEntityDecls().stream())
                .filter(decl -> decl.getName().equals(instance.getEntityName().getLast().toString()))
                .findFirst().get();
        String fileNameBase = actorFileName(instance.getInstanceName());
        String headerFileName = fileNameBase + ".h";
        emitter().open(target().resolve(headerFileName));
        String headerGuard = headerGuard(headerFileName);
        emitter().emit("#ifndef %s", headerGuard);
        emitter().emit("#define %s", headerGuard);
        emitDefaultHeaders();
        backend().structure().actorHdr(actor);
        emitter().emit("#endif");
        emitter().close();

        emitter().open(target().resolve(fileNameBase + ".c"));
        emitDefaultHeaders();
        includeUser("fifo.h");
        includeUser("global.h");
        includeUser(headerFileName);
        backend().structure().actorDecl(actor);
        emitter().close();
        backend().instance().clear();
    }

    default String headerGuard(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
    }

    default void emitDefaultHeaders() {
        includeSystem("stdint.h");
        includeSystem("stdbool.h");
        includeSystem("stdlib.h");
        emitter().emit("#pragma clang diagnostic ignored \"-Wparentheses-equality\"");
    }
    default void includeSystem(String h) { emitter().emit("#include <%s>", h); }
    default void includeUser(String h) { emitter().emit("#include \"%s\"", h); }

    default Path target() {
        return backend().context().getConfiguration().get(Compiler.targetPath);
    }

    default void include() {
        try (InputStream in = ClassLoader.getSystemResourceAsStream("c_backend_code/included.c")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            reader.lines().forEach(emitter()::emitRawLine);
        } catch (IOException e) {
            throw CompilationException.from(e);
        }
    }
}
