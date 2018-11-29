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

import java.io.*;
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
      //  actors();
        main();
    }

    default void global() {
        emitter().open(targetLibInclude().resolve("global.h"));
        backend().global().generateGlobalHeader();
        emitter().close();
        emitter().open(targetLibSrc().resolve("global.c"));
        backend().global().generateGlobalCode();
        emitter().close();
    }

    default void fifo() {
        emitter().open(targetLibInclude().resolve("fifo.h"));
        channels().fifo_h();
        emitter().close();
    }

    default void main() {
        Path mainTarget = targetCodeGenSrc().resolve("main.c");
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


    default Path targetLibSrc(){
        String includeDirectory = "lib" + File.separator + "src";
        File directory = new File(backend().context().getConfiguration().get(Compiler.targetPath).toFile(), includeDirectory);
        return directory.toPath();
    }

    default Path targetLibInclude(){
        String includeDirectory = "lib" + File.separator + "include";
        File directory = new File(backend().context().getConfiguration().get(Compiler.targetPath).toFile(), includeDirectory);
        return directory.toPath();
    }

    default Path targetCodeGenSrc(){
        String includeDirectory = "code-gen" + File.separator + "src";
        File directory = new File(backend().context().getConfiguration().get(Compiler.targetPath).toFile(), includeDirectory);
        return directory.toPath();
    }

    default Path targetCodeGenInclude(){
        String includeDirectory = "code-gen" + File.separator + "include";
        File directory = new File(backend().context().getConfiguration().get(Compiler.targetPath).toFile(), includeDirectory);
        return directory.toPath();
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
