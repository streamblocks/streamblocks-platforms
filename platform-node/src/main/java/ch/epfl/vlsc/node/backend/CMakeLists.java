package ch.epfl.vlsc.node.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;

@Module
public interface CMakeLists {

    @Binding(BindingKind.INJECTED)
    NodeBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void projectCMakeLists() {
        emitter().open(PathUtils.getTarget(backend().context()).resolve("CMakeLists.txt"));

        // -- CMake Minimal version
        emitter().emit("# -- Generated from %s", backend().task().getIdentifier());
        emitter().emit("cmake_minimum_required(VERSION 3.10)");
        emitter().emitNewLine();


        // -- Project name
        emitter().emit("project (%s)", backend().task().getIdentifier().getLast().toString());
        emitter().emitNewLine();


        // -- Set C++ Standard
        emitter().emit("set (CMAKE_CXX_STANDARD 14)");


        // -- Binary output folder
        emitter().emit("# -- Configure output Folder for generated binary");
        emitter().emit("set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)");
        emitter().emitNewLine();

        // -- Definitions used in subdirectories
        emitter().emit("# -- Definitions used in sub directories");
        emitter().emit("set(extra_definitions)");
        emitter().emit("set(extra_includes)");
        emitter().emitNewLine();
        emitter().emit("list(APPEND extra_definitions \"-DCAL_RT_CALVIN\")");
        emitter().emitNewLine();


        // -- Torch
        emitter().emit("# -- Activate TORCH");
        emitter().emit("option(TORCH \"Link and include libtorch\" ON)");
        emitter().emitNewLine();

        emitter().emit("if(TORCH)");
        emitter().increaseIndentation();

        emitter().emit("find_package(Torch REQUIRED)");
        emitter().emit("list(APPEND extra_definitions \"-DUSE_TORCH\")");
        emitter().emit("list(APPEND extra_libraries \"${TORCH_LIBRARIES}\")");
        emitter().emit("list(APPEND CMAKE_CXX_FLAGS \"${TORCH_CXX_FLAGS}\")");

        emitter().decreaseIndentation();
        emitter().emit("endif()");
        emitter().emitNewLine();

        emitter().emit("include_directories(");
        {
            emitter().increaseIndentation();
            emitter().emit("lib/art-node/include");
            emitter().emit("#lib/art-native/include");
            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emitNewLine();

        // -- Add sub directories
        emitter().emit("# -- Add sub directories ");
        emitter().emit("add_subdirectory(lib)");
        emitter().emit("add_subdirectory(code-gen)");
        emitter().emitNewLine();


        emitter().close();
    }

    default void projectCodegenNodeCMakeLists(){
        emitter().open(PathUtils.getTargetCodeGen(backend().context()).resolve("CMakeLists.txt"));

        // -- Add sub directories
        emitter().emit("# -- Add sub directories ");
        emitter().emit("add_subdirectory(cc)");
        emitter().emit("# add_subdirectory(acc)");
        emitter().emitNewLine();

        emitter().close();
    }

}
