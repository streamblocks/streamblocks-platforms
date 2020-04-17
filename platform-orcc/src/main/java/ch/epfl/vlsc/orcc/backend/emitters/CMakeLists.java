package ch.epfl.vlsc.orcc.backend.emitters;

import ch.epfl.vlsc.orcc.backend.OrccBackend;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;

@Module
public interface CMakeLists {

    @Binding(BindingKind.INJECTED)
    OrccBackend backend();

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

        // -- Configure output
        emitter().emit("# -- Configure output folder for generated binary");
        emitter().emit("set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)");
        emitter().emitNewLine();

        // -- Definitions
        emitter().emit("# -- Definitions configured and used in subdirectories");
        emitter().emit("set(extra_definitions)");
        emitter().emit("set(extra_includes)");
        emitter().emit("set(extra_libraries)");
        emitter().emitNewLine();

        // -- Runtime libraries
        emitter().emit("# -- Runtime libraries inclusion");
        emitter().emit("include_directories(");
        {
            emitter().increaseIndentation();

            emitter().emit("${PROJECT_BINARY_DIR}/libs # to find config.h");
            emitter().emit("libs/orcc-native/include");
            emitter().emit("libs/orcc-runtime/include");

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emitNewLine();

        // -- Sub-directories
        emitter().emit("# -- Compile required libs");
        emitter().emit("add_subdirectory(libs)");
        emitter().emitNewLine();
        emitter().emit("# -- Compile application");
        emitter().emit("add_subdirectory(code-gen)");
        emitter().emitNewLine();

        // -- EOF
        emitter().close();
    }

    default void codegenCMakeLists(){
        emitter().open(PathUtils.getTargetCodeGen(backend().context()).resolve("CMakeLists.txt"));
        emitter().emit("# -- Generated from %s", backend().task().getIdentifier());
        emitter().emitNewLine();

        String taskId = backend().task().getIdentifier().getLast().toString();

        // -- Source files
        // -- CodeGen sources
        emitter().emit("# -- Generated code source files");
        emitter().emit("set(filenames");
        emitter().increaseIndentation();

        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                String filename = backend().instaceQID(instance.getInstanceName(), "_") + ".c";
                emitter().emit("src/%s", filename);
            }
        }

        // -- Add main
        emitter().emit("src/globals.c");
        emitter().emit("src/%s.c", taskId);

        emitter().decreaseIndentation();
        emitter().emit(")");
        emitter().emitNewLine();

        // -- Include
        emitter().emit("include_directories(include)");
        emitter().emitNewLine();

        // -- OpenCV
        emitter().emit("# -- [MSVC] Ensure OpenCV imported targets are reachable in this file");
        emitter().emit("# -- They may be imported in ${extra_libraries}");
        emitter().emit("find_package(OpenCV QUIET)");
        emitter().emitNewLine();

        // -- Directories, definitions, executable
        emitter().emit("include_directories(${extra_includes})");
        emitter().emit("add_definitions(${extra_definitions})");
        emitter().emit("add_executable(%s ${filenames})", taskId);
        emitter().emitNewLine();

        // -- Link libraries
        emitter().emit("# -- Build library without any external library required");
        emitter().emit("target_link_libraries(%s orcc-native orcc-runtime ${extra_libraries})", taskId);
        emitter().emitNewLine();

        // -- EOF
        emitter().close();
    }
}
