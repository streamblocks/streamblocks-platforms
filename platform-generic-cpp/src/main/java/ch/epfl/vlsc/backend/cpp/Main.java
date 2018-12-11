package ch.epfl.vlsc.backend.cpp;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import ch.epfl.vlsc.platformutils.PathUtils;
import se.lth.cs.tycho.compiler.CompilationTask;
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

    default Preprocessor preprocessor(){ return backend().preprocessor();}

    default Channels channels() {
        return backend().channels();
    }

    default MainNetwork mainNetwork() {
        return backend().mainNetwork();
    }


    default void generateMain() {
        Path mainTarget = PathUtils.getTargetCodeGenSource(backend().context()).resolve("main.cpp");
        emitter().open(mainTarget);
        CompilationTask task = backend().task();
        preprocessor().systemInclude("iostream");
        includeSystem("stdlib.h");
        includeSystem("stdio.h");
        includeSystem("stdbool.h");
        includeSystem("stdint.h");
        includeSystem("signal.h");
        includeSystem("string.h");
        includeUser("fifo.h");
        includeUser("prelude.h");
        includeUser("global.h");
        actorHeaders();
        include();
        channels().inputActorCode();
        channels().outputActorCode();
        mainNetwork().main(task.getNetwork());
        emitter().close();
    }

    default void actorHeaders() {
        backend().task().getNetwork().getInstances().forEach(this::actorHeader);
    }

    default void actorHeader(Instance instance){
        String fileNameBase = actorFileName(instance.getInstanceName());
        String headerFileName = fileNameBase + ".h";
        includeUser(headerFileName);
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


    default void include() {
        try (InputStream in = ClassLoader.getSystemResourceAsStream("c_backend_code/included.c")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            reader.lines().forEach(emitter()::emitRawLine);
        } catch (IOException e) {
            throw CompilationException.from(e);
        }
    }
}
