package com.streamgenomics.backend.cpp;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import platformutils.PathUtils;

@Module
public interface CMakeLists {

    @Binding(BindingKind.INJECTED)
    Backend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void generateTopCmakeLists(){
        emitter().open(PathUtils.getTarget(backend().context()).resolve("CMakeLists.txt"));
        emitter().emit("cmake_minimum_required (VERSION 3.11)");
        emitter().emit("");
        emitter().emit("project (%s)",backend().task().getIdentifier().getLast().toString());

        emitter().emit("");

        emitter().emitRawLine("include_directories(\n" +
                "\tlib/include\n" +
                ")");

        emitter().emit("");
        emitter().emit("# Add Library");
        emitter().emit("add_subdirectory(lib)");

        emitter().emit("");
        emitter().emit("# Add Code-gen");
        emitter().emit("add_subdirectory(code-gen)");

        emitter().close();
    }

    default void generateLibCmakeLists(){

    }

    default void generateCodeGenCmakeLists(){

    }

}
