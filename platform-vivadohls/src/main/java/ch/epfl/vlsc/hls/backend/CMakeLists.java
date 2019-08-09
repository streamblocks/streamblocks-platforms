package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;

import java.util.stream.Collectors;

@Module
public interface CMakeLists {
    @Binding(BindingKind.INJECTED)
    VivadoHLSBackend backend();

    default Emitter emitter() {
        return backend().emitter();
    }

    default void projectCMakeLists() {
        // -- Network
        Network network = backend().task().getNetwork();

        emitter().open(PathUtils.getTarget(backend().context()).resolve("CMakeLists.txt"));

        // -- CMake Minimal version
        emitter().emit("# -- Generated from %s", backend().task().getIdentifier());
        emitter().emit("cmake_minimum_required(VERSION 3.3)");
        emitter().emitNewLine();

        // -- Project name
        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emit("project (%s)", identifier);
        emitter().emitNewLine();

        // -- SDAccel Kernel Option
        emitter().emit("option(SDACCEL_KERNEL \"Build an RTL OpenCL Kernel for SDAccel\" OFF)");

        // -- Set FPGA name
        emitter().emit("set(FPGA_NAME \"xcku115-flvb2104-2-e\" CACHE STRING \"Name of Xilinx FPGA\")");
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

        // -- Find Vivado
        emitter().emit("find_package(Vivado REQUIRED)");
        emitter().emit("if (NOT VIVADO_FOUND)");
        emitter().increaseIndentation();
        emitter().emit("message(FATAL_ERROR \"Vivado not found, source Vivado settings.sh\")");
        emitter().decreaseIndentation();
        emitter().emit("endif()");
        emitter().emitNewLine();

        // -- Find SDAccel
        emitter().emit("if (SDACCEL_KERNEL)");
        {
            emitter().emit("find_package(SDAccel REQUIRED)");
            emitter().emit("if (NOT SDACCEL_FOUND)");
            emitter().increaseIndentation();
            emitter().emit("message(FATAL_ERROR \"SDAccel is not found, source SDx settings.sh\")");
            emitter().decreaseIndentation();
            emitter().emit("else()");
            emitter().emit("\tfile(MAKE_DIRECTORY ${CMAKE_SOURCE_DIR}/bin)");
            emitter().emit("endif()");
            emitter().emitNewLine();
        }
        emitter().emit("endif()");


        // -- Include directories
        emitter().emit("include_directories(${CMAKE_BINARY_DIR} ${CMAKE_SOURCE_DIR}/code-gen/include ${VIVADO_HLS_INCLUDE_DIRS})");
        emitter().emitNewLine();

        // -- Synthesis configure file
        emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/Synthesis.tcl.in Synthesis.tcl)");
        emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/%s.tcl.in %1$s.tcl @ONLY)", identifier);
        emitter().emit("if(SDACCEL_KERNEL)");
        {
            emitter().increaseIndentation();

            emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/package_kernel.tcl.in package_kernel.tcl @ONLY)");
            emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/gen_xo.tcl.in gen_xo.tcl @ONLY)");
            emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/sdaccel.ini.in ${CMAKE_SOURCE_DIR}/bin/sdaccel.ini @ONLY)");

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");
        emitter().emitNewLine();

        // -- Source and Include folders
        emitter().emit("set(_srcpath ${CMAKE_CURRENT_SOURCE_DIR}/code-gen/src)");
        emitter().emit("set(_incpath ${CMAKE_CURRENT_SOURCE_DIR}/code-gen/include)");
        emitter().emitNewLine();


        // -- Custom commands
        emitter().emit("## -- Custom commands");
        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                String instanceName = backend().instaceQID(instance.getInstanceName(), "_");
                String filename = instanceName + ".cpp";
                entityCustomCommand(instanceName, filename);
            }
        }

        // -- Input/Output Stages
        emitter().emit("if(SDACCEL_KERNEL)");
        {
            emitter().increaseIndentation();

            for(PortDecl port : network.getInputPorts()){
                String topName = port.getName() + "_input_stage";
                String filename = topName + ".cpp";
                entityStageCustomCommand(topName, filename, true);
            }

            for(PortDecl port : network.getOutputPorts()){
                String topName = port.getName() + "_output_stage";
                String filename = topName + ".cpp";
                entityStageCustomCommand(topName, filename, false);
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");

        // -- Vivado Custom command
        emitter().emit("add_custom_command(");
        {
            emitter().increaseIndentation();

            emitter().emit("OUTPUT ${CMAKE_SOURCE_DIR}/output/%s/%1$s.xpr", identifier);
            emitter().emit("COMMAND ${VIVADO_BINARY} -mode batch -source %s.tcl  > %1$s.log", identifier);
            String verilogInstances = String.join(" ", network.getInstances()
                    .stream().map(n -> backend().instaceQID(n.getInstanceName(), "_"))
                    .collect(Collectors.toList()));
            emitter().emit("DEPENDS %s", verilogInstances);
            emitter().decreaseIndentation();
        }
        emitter().emit(")");


        emitter().emit("## -- Custom targets");
        // -- Instance custom targets
        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                String instanceName = backend().instaceQID(instance.getInstanceName(), "_");
                emitter().emit("add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)", instanceName);
            }
        }

        // -- Input/Output Stage targets
        emitter().emit("if(SDACCEL_KERNEL)");
        {
            emitter().increaseIndentation();

            for(PortDecl port : network.getInputPorts()){
                String topName = port.getName() + "_input_stage";
                emitter().emit("add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)", topName);
            }

            for(PortDecl port : network.getOutputPorts()){
                String topName = port.getName() + "_output_stage";
                emitter().emit("add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)", topName);
            }

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");

        // -- Top, Vivado custom target
        String xprProject = String.format("${CMAKE_SOURCE_DIR}/output/%s/%1$s.xpr", identifier);

        emitter().emit("add_custom_target(%s ALL DEPENDS %s)", identifier, xprProject);

        emitter().close();

    }

    default void entityCustomCommand(String topName, String filename){
        emitter().emit("add_custom_command(");
        {
            emitter().increaseIndentation();

            emitter().emit("OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/%s/solution/syn/verilog/%1$s.v", topName);
            emitter().emit("COMMAND ${VIVADO_HLS_BINARY} -f Synthesis.tcl -tclargs \\\"%s\\\" \\\"%s\\\"  > %1$s.log", topName, filename);
            emitter().emit("DEPENDS ${_incpath}/%s.h ${_srcpath}/%1$s.cpp", topName);

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emitNewLine();
    }

    default void entityStageCustomCommand(String topName, String filename, boolean isInput){
        emitter().emit("add_custom_command(");
        {
            emitter().increaseIndentation();

            emitter().emit("OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/%s/solution/syn/verilog/%1$s.v", topName);
            emitter().emit("COMMAND ${VIVADO_HLS_BINARY} -f Synthesis.tcl -tclargs \\\"%s\\\" \\\"%s\\\"  > %1$s.log", topName, filename);
            emitter().emit("DEPENDS ${_incpath}/%s.h ${_srcpath}/%s.cpp", isInput ? "input_stage" : "output_stage", topName);

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emitNewLine();
    }

}
