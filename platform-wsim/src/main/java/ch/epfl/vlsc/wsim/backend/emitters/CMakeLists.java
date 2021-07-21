package ch.epfl.vlsc.wsim.backend.emitters;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.network.Instance;
import ch.epfl.vlsc.wsim.backend.WSimBackend;

@Module
public interface CMakeLists {

    @Binding(BindingKind.INJECTED)
    WSimBackend backend();

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
        emitter().emit("set(CMAKE_CXX_STANDARD 14)");
        emitter().emitNewLine();

        // -- Module Path
        emitter().emit("set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${PROJECT_SOURCE_DIR}/lib/cmake)");
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

        // -- Definitions L1D_CACHE_LINE_SIZE
        emitter().emit("list(APPEND extra_definitions -DL1D_CACHE_LINE_SIZE=64)");
        emitter().emitNewLine();

        // -- Include directories
        emitter().emit("# -- Include directories");

        emitter().emit("include_directories(");
        emitter().increaseIndentation();
        emitter().emit("lib/sb-native/include");
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
        emitter().emit("set(sources");
        emitter().increaseIndentation();

        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                String filename = instance.getInstanceName() + ".cpp";
                emitter().emit("src/%s", filename);
            }
        }

        // -- Add main
        emitter().emit("src/main.cpp");

        emitter().decreaseIndentation();
        emitter().emit(")");
        emitter().emitNewLine();

        // -- Generated code headers
        emitter().emit("# -- Generated code headers");
        emitter().emit("set(headers");
        emitter().increaseIndentation();
        emitter().emit("include/globals.h");
        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                String filename = instance.getInstanceName() + ".h";
                emitter().emit("include/%s", filename);
            }
        }
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
        emitter().emit("add_executable(%s ${sources})", backend().task().getIdentifier().getLast().toString());
        emitter().emitNewLine();

        // -- Target link libraries
        emitter().emit("# -- Target link libraries");
        emitter().emit("target_link_libraries(%s sb-native ${extra_libraries})",
                backend().task().getIdentifier().getLast().toString());

        // -- EOF
        emitter().close();
    }


}
