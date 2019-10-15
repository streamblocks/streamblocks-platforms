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
        emitter().emit("option(SDACCEL_KERNEL \"Build an RTL OpenCL Kernel for SDAccel\" ON)");
        emitter().emit("option(SDACCEL_HOST \"Build an example OpenCL Host executable for SDAccel\" OFF)");

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
            {
                emitter().increaseIndentation();
                emitter().emit("file(MAKE_DIRECTORY ${CMAKE_SOURCE_DIR}/bin)");
                emitter().emit("file(MAKE_DIRECTORY ${CMAKE_SOURCE_DIR}/bin/xclbin)");
                emitter().emit("file(MAKE_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/xclbin)");
                emitter().emit("set(TARGET \"hw_emu\" CACHE STRING \"SDAccel TARGET : hw_emu, hw\")");
                emitter().emit(
                        "set(DEVICE \"xilinx_kcu1500_dynamic_5_0\" CACHE STRING \"SDAccel supported device name\")");
                emitter().decreaseIndentation();
            }
            emitter().emit("endif()");
            emitter().emitNewLine();
        }
        emitter().emit("endif()");

        // -- Include directories
        emitter().emit(
                "include_directories(${CMAKE_BINARY_DIR} ${CMAKE_SOURCE_DIR}/code-gen/include ${VIVADO_HLS_INCLUDE_DIRS})");
        emitter().emitNewLine();

        // -- Synthesis configure file
        emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/Synthesis.tcl.in Synthesis.tcl)");
        emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/%s.tcl.in %1$s.tcl @ONLY)", identifier);
        emitter().emit("if(SDACCEL_KERNEL)");
        {
            emitter().increaseIndentation();

            configurePackageFile("input");
            configurePackageFile("core");
            configurePackageFile("output");

            emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/gen_xo.tcl.in gen_xo.tcl @ONLY)");
            emitter().emit(
                    "configure_file(${CMAKE_SOURCE_DIR}/scripts/sdaccel.ini.in ${CMAKE_SOURCE_DIR}/bin/sdaccel.ini @ONLY)");

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
                entityCustomCommand(instanceName, instanceName, filename);
            }
        }

        // -- Input/Output Stages
        emitter().emit("if(SDACCEL_KERNEL)");
        {
            emitter().increaseIndentation();

            emitter().emit("add_custom_command(");
            {
                emitter().increaseIndentation();

                emitter().emit("OUTPUT ${CMAKE_SOURCE_DIR}/bin/emconfig.json");
                emitter().emit(
                        "COMMAND ${SDACCEL_EMCONFIGUTIL} --nd 1 --platform ${DEVICE} --od ${CMAKE_SOURCE_DIR}/bin > emconfigutil.log");
                emitter().decreaseIndentation();
            }
            emitter().emit(")");

            for (PortDecl port : network.getInputPorts()) {
                String topName = port.getName() + "_input_stage_mem";
                String filename = topName + ".cpp";
                entityCustomCommand(topName, "input_stage_mem", filename);
                topName = port.getName() + "_stage_pass";
                filename = topName + ".cpp";
                entityCustomCommand(topName, "stage_pass", filename);
            }

            for (PortDecl port : network.getOutputPorts()) {
                String topName = port.getName() + "_output_stage_mem";
                String filename = topName + ".cpp";
                entityCustomCommand(topName, "output_stage_mem", filename);
                topName = port.getName() + "_stage_pass";
                filename = topName + ".cpp";
                entityCustomCommand(topName, "stage_pass", filename);
            }

            // -- xo custom commands
            addXoCustomCommand("input");
            addXoCustomCommand("core");
            ;
            addXoCustomCommand("output");

            // -- xclbin custom commands
            addXclbinCustomCommand("input");
            addXclbinCustomCommand("core");
            addXclbinCustomCommand("output");

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");

        emitter().emit("add_custom_command(");
        {
            emitter().increaseIndentation();

            emitter().emit("OUTPUT ${CMAKE_SOURCE_DIR}/output/%s/%1$s.xpr", identifier);
            emitter().emit("COMMAND ${VIVADO_BINARY} -mode batch -source %s.tcl  > %1$s.log", identifier);
            String verilogInstances = String.join(" ", network.getInstances().stream()
                    .map(n -> backend().instaceQID(n.getInstanceName(), "_")).collect(Collectors.toList()));

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
                emitter().emit(
                        "add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)",
                        instanceName);
            }
        }

        // -- Input/Output Stage targets
        emitter().emit("if(SDACCEL_KERNEL)");
        {
            emitter().increaseIndentation();

            emitter().emit("add_custom_target(emconfig ALL DEPENDS ${CMAKE_SOURCE_DIR}/bin/emconfig.json)");

            for (PortDecl port : network.getInputPorts()) {
                emitter().emit(
                        "add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)",
                        port.getName() + "_input_stage_mem");
                emitter().emit(
                        "add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)",
                        port.getName() + "_stage_pass");
            }

            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit(
                        "add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)",
                        port.getName() + "_output_stage_mem");
                emitter().emit(
                        "add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)",
                        port.getName() + "_stage_pass");
            }

            // -- Generate XO custom target
            addXoCustomTarget("input");
            addXoCustomTarget("core");
            addXoCustomTarget("output");
            // -- Generate XCLBIN custom target
            addXclbinCustomTarget("input");
            addXclbinCustomTarget("core");
            addXclbinCustomTarget("output");

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");

        // -- Top, Vivado custom target
        String xprProject = String.format("${CMAKE_SOURCE_DIR}/output/%s/%1$s.xpr", identifier);

        emitter().emit("add_custom_target(%s ALL DEPENDS %s)", identifier, xprProject);
        // -- Host example

        emitter().emit("if (SDACCEL_HOST)");
        {
            emitter().increaseIndentation();
            emitter().emit("set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)");
            emitter().emit("set(host_filenames\n\tcode-gen/host/Host.cpp\n\tcode-gen/host/device_handle.cpp\n)");

            emitter().emit("add_executable(Host ${host_filenames})");

            emitter().emit("target_include_directories(Host PRIVATE code-gen/host)");
            emitter().emit("target_link_directories(Host PRIVATE ${SDACCEL_LIBRARY_DIR})");

            emitter().emit("set_target_properties(Host PROPERTIES");
            emitter().emit("\tCXX_STANDARD 11");
            emitter().emit("\tCXX_STANDARD_REQUIRED YES");
            emitter().emit("\tCXX_EXTENSIONS NO)");

            emitter().emit("target_link_libraries(Host xilinxopencl rt stdc++)");
            emitter().decreaseIndentation();

        }
        emitter().emit("endif()");
        emitter().close();

    }

    default void entityCustomCommand(String topName, String headerName, String filename) {
        emitter().emit("add_custom_command(");
        {
            emitter().increaseIndentation();

            emitter().emit("OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/%s/solution/syn/verilog/%1$s.v", topName);
            emitter().emit("COMMAND ${VIVADO_HLS_BINARY} -f Synthesis.tcl -tclargs \\\"%s\\\" \\\"%s\\\"  > %1$s.log",
                    topName, filename);
            emitter().emit("DEPENDS ${_incpath}/%s.h ${_srcpath}/%s.cpp", headerName, topName);

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emitNewLine();
    }

    default void addCustomCommand(String Output, String Command, String Depends) {
        emitter().emit("add_custom_command(");
        {
            emitter().increaseIndentation();

            emitter().emit("OUTPUT " + Output);
            emitter().emit("COMMAND " + Command);
            emitter().emit("DEPENDS " + Depends);

            emitter().decreaseIndentation();
        }
        emitter().emit(")");
        emitter().emitNewLine();
    }

    default void addXoCustomCommand(String kernelType) {

        String output = String.format(
                "${CMAKE_CURRENT_BINARY_DIR}/xclbin/${CMAKE_PROJECT_NAME}_%s_kernel.${TARGET}.${DEVICE}.xo", kernelType);
        String command = String.format(
                "${VIVADO_BINARY} -mode batch -source gen_xo.tcl -tclargs ${CMAKE_CURRENT_BINARY_DIR}/xclbin/${CMAKE_PROJECT_NAME}_%s_kernel.${TARGET}.${DEVICE}.xo ${CMAKE_PROJECT_NAME}_%1$s_kernel ${TARGET} ${DEVICE}  > ${CMAKE_PROJECT_NAME}_%1$s_kernel_xo.log",
                kernelType);
        Network network = backend().task().getNetwork();
        String depends;
        if (kernelType == "input") {
            depends = String.join(" ",
                    network.getInputPorts().stream()
                            .map(p -> p.getName() + "_input_stage_mem " + p.getName() + "_stage_pass")
                            .collect(Collectors.toList()));
        } else if (kernelType == "output") {

            depends = String.join(" ",
                    network.getOutputPorts().stream()
                            .map(p -> p.getName() + "_output_stage_mem " + p.getName() + "_stage_pass")
                            .collect(Collectors.toList()));
        } else if (kernelType == "core") {
            depends = String.join(" ", network.getInstances().stream()
                    .map(n -> backend().instaceQID(n.getInstanceName(), "_")).collect(Collectors.toList()));
        } else {
            throw new Error("unknow kernel type " + kernelType);
        }
        addCustomCommand(output, command, depends + " emconfig");
    }

    default void addXoCustomTarget(String kernelType) {
        emitter().emit(
                "add_custom_target(${CMAKE_PROJECT_NAME}_%s_kernel_xo ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/xclbin/${CMAKE_PROJECT_NAME}_%1$s_kernel.${TARGET}.${DEVICE}.xo)",
                kernelType);
    }

    default void addXclbinCustomCommand(String kernelType) {
        String output = "${CMAKE_SOURCE_DIR}/bin/xclbin/${CMAKE_PROJECT_NAME}_" + kernelType
                + "_kernel.${TARGET}.${DEVICE}.xclbin";
        String command = String.format(
                "${SDACCEL_XOCC} -g -t ${TARGET} --platform ${DEVICE} --save-temps  -lo ${CMAKE_SOURCE_DIR}/bin/xclbin/${CMAKE_PROJECT_NAME}_%s_kernel.${TARGET}.${DEVICE}.xclbin ${CMAKE_CURRENT_BINARY_DIR}/xclbin/${CMAKE_PROJECT_NAME}_%1$s_kernel.${TARGET}.${DEVICE}.xo  > ${CMAKE_PROJECT_NAME}_%1$s_kernel_xclbin.log",
                kernelType);

        String depends = "${CMAKE_PROJECT_NAME}_" + kernelType + "_kernel_xo";
        addCustomCommand(output, command, depends);
    }

    default void addXclbinCustomTarget(String kernelType) {
        emitter().emit(
                "add_custom_target(${CMAKE_PROJECT_NAME}_%s_kernel_xclbin ALL DEPENDS ${CMAKE_SOURCE_DIR}/bin/xclbin/${CMAKE_PROJECT_NAME}_%1$s_kernel.${TARGET}.${DEVICE}.xclbin)",
                kernelType);
    }

    default void configurePackageFile(String kernelType) {
        emitter().emit(
                "configure_file(${CMAKE_SOURCE_DIR}/scripts/%s.tcl.in %1$s.tcl @ONLY)",
                backend().packagekernels().getPackageName(kernelType));
    }
}
