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
        emitter().emit("if(MSVC)");
        {
            emitter().increaseIndentation();

            emitter().emit("if (MSVC_VERSION GREATER_EQUAL \"1900\")");
            {
                emitter().increaseIndentation();

                emitter().emit("include(CheckCXXCompilerFlag)");
                emitter().emit("CHECK_CXX_COMPILER_FLAG(\"/std:c++latest\" _cpp_latest_flag_supported)");
                emitter().emit("if (_cpp_latest_flag_supported)");
                {
                    emitter().increaseIndentation();
                    emitter().emit("add_compile_options(\"/std:c++latest\")");
                    emitter().decreaseIndentation();
                }
                emitter().emit("endif()");

                emitter().decreaseIndentation();
            }
            emitter().emit("endif()");

            emitter().decreaseIndentation();
        }
        emitter().emit("else()");
        {
            emitter().increaseIndentation();
            emitter().emit("set (CMAKE_CXX_STANDARD 17)");
            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");
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

        emitter().emit("include_directories(");
        {
            emitter().increaseIndentation();
            emitter().emit("lib/art-node/include");
            emitter().emit("lib/art-native/include");
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
        emitter().emit("# add_subdirectory(hls)");
        emitter().emitNewLine();

        emitter().close();
    }

}
