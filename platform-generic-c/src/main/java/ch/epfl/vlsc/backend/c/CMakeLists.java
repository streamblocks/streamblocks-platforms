package ch.epfl.vlsc.backend.c;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import ch.epfl.vlsc.platformutils.PathUtils;
import se.lth.cs.tycho.ir.network.Instance;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.multij.BindingKind.LAZY;

@Module
public interface CMakeLists {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateTopCmakeLists() {
        emitter().open(PathUtils.getTarget(backend().context()).resolve("CMakeLists.txt"));
        emitter().emit("cmake_minimum_required (VERSION 3.11)");
        emitter().emit("");
        emitter().emit("project (%s)", backend().task().getIdentifier().getLast().toString());

        emitter().emit("");

        emitter().emit("# Place the executable on bin directory");
        emitter().emit("set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)");

        emitter().emitRawLine("include_directories(\n" +
                "\tlib/include\n" +
                ")");

        emitter().emit("");
        emitter().emit("# Add Library");
        emitter().emit("# add_subdirectory(lib)");

        emitter().emit("");
        emitter().emit("# Add Code-gen");
        emitter().emit("add_subdirectory(code-gen)");

        emitter().close();
    }

    default void generateLibCmakeLists() {
        emitter().open(PathUtils.getTargetLib(backend().context()).resolve("CMakeLists.txt"));
        emitter().emit("# -- StreamBlocks Library");
        emitter().emit("");

        emitter().emit("set(runtime_sources");
        emitter().increaseIndentation();
        emitter().emit("src/global.c");
        emitter().decreaseIndentation();
        emitter().emit(")");
        emitter().emit("");

        emitter().emit("set(runtime_headers");
        emitter().increaseIndentation();
        emitter().emit("include/prelude.h");
        emitter().emit("include/fifo.h");
        emitter().emit("include/global.h");
        emitter().decreaseIndentation();
        emitter().emit(")");
        emitter().emit("");

        emitter().emit("add_library(streamblocks-c-runtime STATIC ${runtime_sources} ${runtime_header})");

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


    default void actor(Instance instance) {
        String fileNameBase = actorFileName(instance.getInstanceName());
        String fileName = fileNameBase + ".c";
        emitter().emit("src/%s", fileName);
    }

    default void actors() {
        backend().task().getNetwork().getInstances().forEach(this::actor);
    }

    default void generateCodeGenCmakeLists() {
        emitter().open(PathUtils.getTargetCodeGen(backend().context()).resolve("CMakeLists.txt"));
        emitter().emit("# Generated from : %s", backend().task().getIdentifier());
        emitter().emit("");

        emitter().emit("set(filenames");
        emitter().increaseIndentation();
        actors();
        emitter().emit("src/global.c");
        emitter().emit("src/main.c");
        emitter().decreaseIndentation();
        emitter().emit(")");
        emitter().emit("");

        emitter().emit("include_directories(./include)");
        emitter().emit("");

        emitter().emit("add_executable(%s ${filenames})", backend().task().getIdentifier().getLast());
        emitter().emit("");

        emitter().emit("set(libraries streamblocks-c-runtime)");
        emitter().emit("");

        emitter().emit("# target_link_libraries(%s ${libraries})", backend().task().getIdentifier().getLast());


        emitter().close();
    }


}
