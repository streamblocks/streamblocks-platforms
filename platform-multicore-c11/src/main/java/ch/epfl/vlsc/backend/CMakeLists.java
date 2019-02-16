package ch.epfl.vlsc.backend;

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
    Backend backend();

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

    default void codegenCMakeLists() {
        emitter().open(PathUtils.getTargetCodeGen(backend().context()).resolve("CMakeLists.txt"));
        emitter().emit("# -- Generated from %s", backend().task().getIdentifier());
        emitter().emitNewLine();

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
        emitter().emit("src/main.c");

        emitter().decreaseIndentation();
        emitter().emit(")");
        emitter().emitNewLine();

        // -- Generated code headers
        emitter().emit("# -- Generated code headers");
        emitter().emit("set(code_gen_header");
        emitter().increaseIndentation();
        emitter().emit("include/__arrayCopy.h");
        emitter().emit("include/globals.h");
        emitter().decreaseIndentation();
        emitter().emit(")");
        emitter().emitNewLine();

        // -- Include directories
        emitter().emit("# -- Include directories");
        emitter().emit("include_directories(${extra_includes} ./include)");
        emitter().emitNewLine();

        // -- Add definitions
        emitter().emit("add_definitions(${extra_definitions})");
        emitter().emitNewLine();

        // -- Add executable
        emitter().emit("# -- Add executable");
        emitter().emit("add_executable(%s ${filenames})", backend().task().getIdentifier().getLast().toString());
        emitter().emitNewLine();

        // -- Target Include directories
        emitter().emit("# -- Target include directories");
        emitter().emit("target_include_directories(%s PRIVATE ./include)", backend().task().getIdentifier().getLast().toString());
        emitter().emitNewLine();

        // -- Target link libraries
        emitter().emit("# -- Target link libraries");
        emitter().emit("target_link_libraries(%s art-native art-runtime ${extra_libraries})", backend().task().getIdentifier().getLast().toString());
        emitter().emitNewLine();
        // -- EOF
        emitter().close();
    }

}
