package ch.epfl.vlsc.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.network.Instance;

@Module
public interface CMakeLists {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void projectCMakeLists(){
        emitter().open(PathUtils.getTarget(backend().context()).resolve("CMakeLists.txt"));
        // -- CMake Minimal version
        emitter().emit("# -- Generated from %s", backend().task().getIdentifier());
        emitter().emit("cmake_minimum_required(VERSION 3.10)");
        emitter().emitNewLine();

        // -- Project name
        emitter().emit("project (%s)", backend().task().getIdentifier().getLast().toString());
        emitter().emitNewLine();

        // -- C Standard
        emitter().emit("set(CMAKE_C_STANDARD 11)");
        emitter().emitNewLine();

        // -- Default C Flags
        emitter().emit("# -- Default C Flags");
        emitter().emit("set(CMAKE_C_FLAGS \"-Wall -Wno-unused-variable -Wno-missing-braces\")");
        emitter().emitNewLine();

        // -- Binary output folder
        emitter().emit("# -- Configure output Folder for generated binary");
        emitter().emit("set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)");
        emitter().emitNewLine();

        // -- Definitions used in sub directories
        emitter().emit("# -- Definitions used in sub directories");
        emitter().emit("set(extra_definitions)");
        emitter().emit("set(extra_includes)");
        emitter().emitNewLine();

        // -- Include directories
        emitter().emit("# -- Include directories");
        emitter().emit("include_directories(");
        emitter().increaseIndentation();
        emitter().emit("lib/art-runtime/include");
        emitter().emit("lib/art-native/include");
        emitter().decreaseIndentation();
        emitter().emit(")");
        emitter().emitNewLine();

        // -- Add sub directories
        emitter().emit("# -- Add sub directories ");
        emitter().emit("add_subdirectory(lib)");
        emitter().emit("add_subdirectory(code-gen)");
        emitter().emitNewLine();

        // -- EOF
        emitter().close();
    }

    default void codegenCMakeLists(){
        emitter().open(PathUtils.getTargetCodeGen(backend().context()).resolve("CMakeLists.txt"));
        emitter().emit("# -- Generated from %s", backend().task().getIdentifier());

        // -- CodeGen sources
        emitter().emit("set(filenames");
        emitter().increaseIndentation();

        for(Instance instance: backend().task().getNetwork().getInstances()){
            String filename = backend().instaceQID(instance.getInstanceName(), "_") + ".c";
            emitter().emit("src/%s",filename);
        }

        // -- Add main
        emitter().emit("src/main.c");


        emitter().decreaseIndentation();
        emitter().emit(")");
        emitter().emitNewLine();

        // -- Add executable
        emitter().emit("add_executable(%s ${filenames})", backend().task().getIdentifier().getLast().toString());

        // -- Libraries
        emitter().emit("set(libraries art-runtime)");

        // -- Target link libraries
        emitter().emit("target_link_libraries(%s ${libraries})", backend().task().getIdentifier().getLast().toString());

        // -- EOF
        emitter().close();
    }

}
