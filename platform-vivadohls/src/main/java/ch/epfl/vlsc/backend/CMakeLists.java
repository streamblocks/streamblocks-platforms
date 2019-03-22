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
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void projectCMakeLists() {
        emitter().open(PathUtils.getTarget(backend().context()).resolve("CMakeLists.txt"));

        // -- CMake Minimal version
        emitter().emit("# -- Generated from %s", backend().task().getIdentifier());
        emitter().emit("cmake_minimum_required(VERSION 3.3)");
        emitter().emitNewLine();

        // -- Project name
        emitter().emit("project (%s)", backend().task().getIdentifier().getLast().toString());
        emitter().emitNewLine();

        // -- Set FPGA name
        emitter().emit("set(FPGA_NAME \"xc7z020clg484-1\" CACHE STRING \"Name of Xilinx FPGA\")");
        emitter().emitNewLine();

        // -- Cmake module path for finding the tools
        emitter().emit("set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${CMAKE_SOURCE_DIR}/cmake)");
        emitter().emitNewLine();

        // -- Find Vivado HLS
        emitter().emit("find_package(VivadoHLS REQUIRED)");
        emitter().emit("if (NOT VIVADO_HLS_FOUND)");
        emitter().increaseIndentation();
        emitter().emit("message(FATAL_ERROR \"Vivado HLS not found, source Vivado settings.sh\")");
        emitter().decreaseIndentation();
        emitter().emit("endif()");
        emitter().emitNewLine();

        // -- Include directories
        emitter().emit("include_directories(${CMAKE_BINARY_DIR} ${CMAKE_SOURCE_DIR}/code-gen/include ${VIVADO_HLS_INCLUDE_DIRS})");
        emitter().emitNewLine();

        // -- Synthesis configure file
        emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/Synthesis.tcl.in Synthesis.tcl)");
        emitter().emitNewLine();

        // -- Custom commands & targets
        emitter().emit("## -- Custom commands");
        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                String instanceName = backend().instaceQID(instance.getInstanceName(), "_");
                String filename = instanceName + ".cpp";
                emitter().emit("add_custom_command(");
                emitter().increaseIndentation();

                emitter().emit("OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/%s/solution/syn/verilog/%1$s.v", instanceName);
                emitter().emit("COMMAND ${VIVADO_HLS_BINARY} -f Synthesis.tcl -tclargs \\\"%s\\\" \\\"%s\\\"", instanceName, filename);

                emitter().decreaseIndentation();
                emitter().emit(")");
                emitter().emitNewLine();
            }
        }

        // -- Custom targets
        emitter().emit("## -- Custom targets");
        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                String instanceName = backend().instaceQID(instance.getInstanceName(), "_");
                emitter().emit("add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)", instanceName);
            }
        }

        emitter().close();

    }

}
