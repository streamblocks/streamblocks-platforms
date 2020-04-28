package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.PathUtils;
import ch.epfl.vlsc.settings.PlatformSettings;

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
        emitter().emitSharpBlockCommentStart();
        emitter().emitSharpComment("StremBlocks Vivado HLS Code Generation");
        emitter().emitSharpComment("Generated from: " + backend().task().getIdentifier());
        emitter().emitSharpBlockCommentEnd();

        emitter().emit("cmake_minimum_required(VERSION 3.3)");
        emitter().emitNewLine();

        // -- Project name
        String identifier = backend().task().getIdentifier().getLast().toString();
        emitter().emitSharpBlockComment("CMake Project");
        emitter().emit("project (%s)", identifier);
        emitter().emitNewLine();


        // -- Vivado HLS CMake Options
        emitter().emitSharpComment("CMake Options");
        emitter().emit("option(USE_VITIS \"Build an RTL OpenCL Kernel for Vitis\" OFF)");
        emitter().emit("option(USE_SDACCEL \"Build an RTL OpenCL Kernel for SDAccel\" OFF)");
        emitter().emit("option(OPENCL_HOST \"Build an example OpenCL Host executable for Vitis orSDAccel\" OFF)");
        emitter().emitNewLine();


        // -- hardware debug and profile options
        emitter().emitSharpBlockComment("Profile and Debug Options");
        emitter().emit("option(HW_PROFILE \"Profile the DDR traffic\" OFF)");
        emitter().emit("option(HW_DEBUG \"Add protocol checker debug cores\" OFF)");

        // -- set the values for profile and debug conditionally
        emitter().emit("if (HW_PROFILE)");
        emitter().emit("\tset(XOCC_PROFILE \"--profile_kernel\\tdata:all:all:all\")");
        emitter().emit("else()");
        emitter().emit("\tset(XOCC_PROFILE \"\")");
        emitter().emit("endif()");

        emitter().emit("if (HW_DEBUG)");
        emitter().emit("\tset(XOCC_DEBUG \"--dk\\tprotocol:${CMAKE_PROJECT_NAME}_kernel_1:all\")");
        emitter().emit("else()");
        emitter().emit("\tset(XOCC_DEBUG \"\")");
        emitter().emit("endif()");

        // -- Vivado HLS Clock and FPGA CMake Variables
        emitter().emitSharpComment("CMake Variables");
        emitter().emit("set(FPGA_NAME \"xczu3eg-sbva484-1-e\" CACHE STRING \"Name of Xilinx FPGA, e.g \\\"xcku115-flvb2104-2-e\\\", \\\"xczu3eg-sbva484-1-e\\\",..\")");
        emitter().emit("set(HLS_CLOCK_PERIOD \"10\" CACHE STRING \"Clock period in ns\")");
        emitter().emit("set(KERNEL FALSE)");
        emitter().emitNewLine();

        // -- Cmake module path for finding the tools
        emitter().emitSharpComment("Set CMake module path, for finding the necessary tools");
        emitter().emit("set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${CMAKE_SOURCE_DIR}/cmake)");
        emitter().emitNewLine();

        // -- Required tools for this code-generator
        emitter().emitSharpBlockComment("Minimal external tools requirement for the generated code");

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

        // -- Find Vitis or SDAccel
        emitter().emitSharpBlockComment("Find and use Vitis or SDAccel");
        emitter().emitNewLine();

        // -- Use Vitis
        emitter().emitSharpComment("Use Vitis");
        emitter().emit("if(USE_VITIS)");
        {
            emitter().increaseIndentation();

            emitter().emit("if(USE_SDACCEL)");
            emitter().emit("\tmessage(FATAL_ERROR \"You can use either Vitis or SDAccel\")");
            emitter().emit("endif()");
            emitter().emitNewLine();

            emitter().emit("find_package(Vitis REQUIRED)");
            emitter().emit("if (NOT VITIS_FOUND)");
            emitter().emit("\tmessage(FATAL_ERROR \"Vitis is not found, source Vitis settings.sh\")");
            emitter().emit("endif()");
            emitter().emitNewLine();

            emitter().emit("find_package(XRT REQUIRED)");
            emitter().emit("if (NOT XRT_FOUND)");
            emitter().emit("\tmessage(FATAL_ERROR \"XRT is not found, source XRT settings.sh\")");
            emitter().emit("endif()");
            emitter().emitNewLine();

            emitter().emit("set(KERNEL TRUE)");

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");
        emitter().emitNewLine();

        // -- Use SDAccel
        emitter().emitSharpComment("Use SDAccel");
        emitter().emit("if(USE_SDACCEL)");
        {
            emitter().increaseIndentation();

            emitter().emit("if(USE_VITIS)");
            emitter().emit("\tmessage(FATAL_ERROR \"You can use either SDAccle or Vitis\")");
            emitter().emit("endif()");
            emitter().emitNewLine();

            emitter().emit("find_package(SDAccel REQUIRED)");
            emitter().emit("if (NOT SDACCEL_FOUND)");
            emitter().emit("\tmessage(FATAL_ERROR \"SDAccel is not found, source SDx settings.sh\")");
            emitter().emit("endif()");
            emitter().emitNewLine();

            emitter().emit("set(KERNEL TRUE)");

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");
        emitter().emitNewLine();

        // -- Kernel configuration
        emitter().emitSharpBlockComment("Kernel configuration");
        emitter().emit("if (KERNEL)");
        {
            emitter().increaseIndentation();

            emitter().emit("file(MAKE_DIRECTORY ${CMAKE_SOURCE_DIR}/bin)");
            emitter().emit("file(MAKE_DIRECTORY ${CMAKE_SOURCE_DIR}/bin/xclbin)");
            emitter().emit("file(MAKE_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/xclbin)");
            emitter().emit("set(TARGET \"hw\" CACHE STRING \"Vitis/SDAccel TARGET : hw_emu, hw\")");
            emitter().emitNewLine();

            emitter().emit("if(USE_VITIS)");
            emitter().emit("\tset(FPGA_NAME \"xczu3eg-sbva484-1-e\" CACHE STRING \"Name of Xilinx FPGA, e.g \\\"xcku115-flvb2104-2-e\\\", \\\"xczu3eg-sbva484-1-e\\\",..\")");
            emitter().emit("\tset(PLATFORM \"ultra96_base\" CACHE STRING \"Supported platform name, e.g \\\"xilinx_kcu1500_dynamic_5_0\\\", \\\"zcu102_base\\\", \\\"ultra96_base\\\",... \")");
            emitter().emit("\tset(HLS_CLOCK_PERIOD \"6.667\" CACHE STRING \"Clock period in ns\")");
            emitter().emit("\tset(KERNEL_FREQ \"150\" CACHE STRING \"Clock frequency in MHz.\")");
            emitter().emit("else()");
            emitter().emit("\tset(FPGA_NAME \"xcku115-flvb2104-2-e\" CACHE STRING \"Name of Xilinx FPGA, e.g \\\"xcku115-flvb2104-2-e\\\", \\\"xczu3eg-sbva484-1-e\\\",..\")");
            emitter().emit("\tset(PLATFORM \"xilinx_kcu1500_dynamic_5_0\" CACHE STRING \"Supported platform name, e.g \\\"xilinx_kcu1500_dynamic_5_0\\\", \\\"zcu102_base\\\", \\\"ultra96_base\\\",... \")");
            emitter().emit("\tset(HLS_CLOCK_PERIOD \"4\" CACHE STRING \"Clock period in ns\")");
            emitter().emit("\tset(KERNEL_FREQ \"300\" CACHE STRING \"Clock frequency in MHz.\")");
            emitter().emit("endif()");

            emitter().decreaseIndentation();
        }
        emitter().emit("else()");
        {
            emitter().increaseIndentation();

            emitter().emit("\tset(FPGA_NAME \"xczu3eg-sbva484-1-e\" CACHE STRING \"Name of Xilinx FPGA, e.g \\\"xcku115-flvb2104-2-e\\\", \\\"xczu3eg-sbva484-1-e\\\",..\")");
            emitter().emit("\tset(HLS_CLOCK_PERIOD \"10\" CACHE STRING \"Clock period in ns\")");

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");
        emitter().emitNewLine();

        // -- Configure file for Vivado HLS
        emitter().emitSharpBlockComment("Configure files for Vivado HLS");
        emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/Synthesis.tcl.in Synthesis.tcl)");
        emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/%s.tcl.in %1$s.tcl @ONLY)", identifier);
        emitter().emitNewLine();

        // -- Configure files for Vitis or SDAccel
        emitter().emitSharpBlockComment("Configure files for Vitis or SDAccel Kernel");
        emitter().emit("if(KERNEL)");
        {
            emitter().increaseIndentation();

            emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/package_kernel.tcl.in package_kernel.tcl @ONLY)");
            emitter().emit("configure_file(${CMAKE_SOURCE_DIR}/scripts/gen_xo.tcl.in gen_xo.tcl @ONLY)");
            emitter().emitNewLine();

            emitter().emit("if(USE_VITIS)");
            emitter().emit("\tconfigure_file(${CMAKE_SOURCE_DIR}/scripts/sdaccel.ini.in ${CMAKE_SOURCE_DIR}/bin/xrt.ini @ONLY)");
            emitter().emit("else()");
            emitter().emit("\tconfigure_file(${CMAKE_SOURCE_DIR}/scripts/sdaccel.ini.in ${CMAKE_SOURCE_DIR}/bin/sdaccel.ini @ONLY)");
            emitter().emit("endif()");


            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");
        emitter().emitNewLine();

        // -- Include directories
        emitter().emitSharpBlockComment("Include directories for Vivado HLS");
        emitter().emit("include_directories(${CMAKE_BINARY_DIR} ${CMAKE_SOURCE_DIR}/code-gen/include ${VIVADO_HLS_INCLUDE_DIRS})");
        emitter().emitNewLine();

        // -- Source and Include folders
        emitter().emitSharpBlockComment("Source and Include folders for the generated code");
        emitter().emit("set(_srcpath ${CMAKE_CURRENT_SOURCE_DIR}/code-gen/src)");
        emitter().emit("set(_incpath ${CMAKE_CURRENT_SOURCE_DIR}/code-gen/include)");
        emitter().emitNewLine();

        // -- Custom commands
        emitter().emitSharpBlockComment("Custom commands");
        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                String instanceName = backend().instaceQID(instance.getInstanceName(), "_");
                String filename = instanceName + ".cpp";
                entityCustomCommand(instanceName, instanceName, filename);
            }
        }

        // -- Input/Output Stages
        emitter().emit("if(KERNEL)");
        {
            emitter().increaseIndentation();

            emitter().emit("if(USE_VITIS)");
            {
                emitter().increaseIndentation();

                emitter().emit("add_custom_command(");
                {
                    emitter().increaseIndentation();

                    emitter().emit("OUTPUT ${CMAKE_SOURCE_DIR}/bin/emconfig.json");
                    emitter().emit(
                            "COMMAND ${VITIS_EMCONFIGUTIL} --nd 1 --platform ${PLATFORM} --od ${CMAKE_SOURCE_DIR}/bin > emconfigutil.log");
                    emitter().decreaseIndentation();
                }
                emitter().emit(")");

                emitter().decreaseIndentation();
            }
            emitter().emit("else()");
            {
                emitter().increaseIndentation();

                emitter().emit("add_custom_command(");
                {
                    emitter().increaseIndentation();

                    emitter().emit("OUTPUT ${CMAKE_SOURCE_DIR}/bin/emconfig.json");
                    emitter().emit(
                            "COMMAND ${SDACCEL_EMCONFIGUTIL} --nd 1 --platform ${PLATFORM} --od ${CMAKE_SOURCE_DIR}/bin > emconfigutil.log");
                    emitter().decreaseIndentation();
                }
                emitter().emit(")");

                emitter().decreaseIndentation();
            }
            emitter().emit("endif()");
            emitter().emitNewLine();

            for (PortDecl port : network.getInputPorts()) {
                String topName = port.getName() + "_input_stage_mem";
                String filename = topName + ".cpp";
                entityCustomCommand(topName, "input_stage_mem", filename);
            }

            for (PortDecl port : network.getOutputPorts()) {
                String topName = port.getName() + "_output_stage_mem";
                String filename = topName + ".cpp";
                entityCustomCommand(topName, "output_stage_mem", filename);
            }

            emitter().emit("add_custom_command(");
            {
                emitter().increaseIndentation();

                emitter().emit(
                        "OUTPUT  ${CMAKE_CURRENT_BINARY_DIR}/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xo");
                emitter().emit(
                        "COMMAND ${VIVADO_BINARY} -mode batch -source gen_xo.tcl -tclargs ${CMAKE_CURRENT_BINARY_DIR}/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xo ${CMAKE_PROJECT_NAME}_kernel ${TARGET} ${PLATFORM}  > ${CMAKE_PROJECT_NAME}_kernel_xo.log");
                String verilogInstances = String.join(" ", network.getInstances().stream()
                        .map(n -> backend().instaceQID(n.getInstanceName(), "_")).collect(Collectors.toList()));
                String inputStages = String.join(" ",
                        network.getInputPorts().stream()
                                .map(p -> p.getName() + "_input_stage_mem ")
                                .collect(Collectors.toList()));
                String outputStages = String.join(" ",
                        network.getOutputPorts().stream()
                                .map(p -> p.getName() + "_output_stage_mem ")
                                .collect(Collectors.toList()));
                emitter().emit("DEPENDS %s %s %s emconfig", verilogInstances, inputStages, outputStages);
                emitter().decreaseIndentation();
            }
            emitter().emit(")");
            emitter().emitNewLine();

            emitter().emit("if(USE_VITIS)");
            {
                emitter().increaseIndentation();

                emitter().emit("add_custom_command(");
                {
                    emitter().increaseIndentation();

                    emitter().emit(
                            "OUTPUT  ${CMAKE_SOURCE_DIR}/bin/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xclbin");
                    emitter().emit(
                            "COMMAND ${VITIS_VPP} -g -t ${TARGET} --platform ${PLATFORM} --kernel_frequency ${KERNEL_FREQ}  --save-temps  ${XOCC_DEBUG} ${XOCC_PROFILE} -lo ${CMAKE_SOURCE_DIR}/bin/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xclbin ${CMAKE_CURRENT_BINARY_DIR}/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xo  > ${CMAKE_PROJECT_NAME}_kernel_xclbin.log");
                    emitter().emit("DEPENDS ${CMAKE_PROJECT_NAME}_kernel_xo");

                    emitter().decreaseIndentation();
                }
                emitter().emit(")");

                emitter().decreaseIndentation();
            }
            emitter().emit("else()");
            {
                emitter().increaseIndentation();

                emitter().emit("add_custom_command(");
                {
                    emitter().increaseIndentation();

                    emitter().emit(
                            "OUTPUT  ${CMAKE_SOURCE_DIR}/bin/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xclbin");
                    emitter().emit(
                            "COMMAND ${SDACCEL_XOCC} -g -t ${TARGET} --platform ${PLATFORM} --kernel_frequency ${KERNEL_FREQ} --save-temps ${XOCC_DEBUG} ${XOCC_PROFILE} -lo ${CMAKE_SOURCE_DIR}/bin/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xclbin ${CMAKE_CURRENT_BINARY_DIR}/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xo  > ${CMAKE_PROJECT_NAME}_kernel_xclbin.log");
                    emitter().emit("DEPENDS ${CMAKE_PROJECT_NAME}_kernel_xo");

                    emitter().decreaseIndentation();
                }
                emitter().emit(")");

                emitter().decreaseIndentation();
            }
            emitter().emit("endif()");

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");
        emitter().emitNewLine();

        // -- Vivado XO Custom command
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
        emitter().emitNewLine();

        emitter().emitSharpBlockComment("Custom targets");
        emitter().emitNewLine();

        // -- Instance custom targets
        emitter().emitSharpComment("Instances custom target(s)");
        for (Instance instance : backend().task().getNetwork().getInstances()) {
            GlobalEntityDecl entityDecl = backend().globalnames().entityDecl(instance.getEntityName(), true);
            if (!entityDecl.getExternal()) {
                String instanceName = backend().instaceQID(instance.getInstanceName(), "_");
                emitter().emit(
                        "add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)",
                        instanceName);
            }
        }
        emitter().emitNewLine();

        // -- Input/Output Stage targets
        emitter().emitSharpComment("Kernel custom target(s)");
        emitter().emit("if(KERNEL)");
        {
            emitter().increaseIndentation();

            emitter().emit("add_custom_target(emconfig ALL DEPENDS ${CMAKE_SOURCE_DIR}/bin/emconfig.json)");

            for (PortDecl port : network.getInputPorts()) {
                emitter().emit(
                        "add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)",
                        port.getName() + "_input_stage_mem");
            }

            for (PortDecl port : network.getOutputPorts()) {
                emitter().emit(
                        "add_custom_target(%s ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/%1$s/solution/syn/verilog/%1$s.v)",
                        port.getName() + "_output_stage_mem");
            }

            // -- Generate XO custom target
            emitter().emit(
                    "add_custom_target(${CMAKE_PROJECT_NAME}_kernel_xo ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xo)");
            // -- Generate XCLBIN custom target
            emitter().emit(
                    "add_custom_target(${CMAKE_PROJECT_NAME}_kernel_xclbin ALL DEPENDS ${CMAKE_SOURCE_DIR}/bin/xclbin/${CMAKE_PROJECT_NAME}_kernel.${TARGET}.${PLATFORM}.xclbin)");

            emitter().decreaseIndentation();
        }
        emitter().emit("endif()");
        emitter().emitNewLine();

        // -- Top, Vivado custom target
        emitter().emitSharpComment("Vivado custom target");
        String xprProject = String.format("${CMAKE_SOURCE_DIR}/output/%s/%1$s.xpr", identifier);

        emitter().emit("add_custom_target(%s ALL DEPENDS %s)", identifier, xprProject);
        emitter().emitNewLine();

        // -- Host example
        emitter().emitSharpComment("Host Example Code");
        emitter().emit("if (OPENCL_HOST)");
        {
            emitter().increaseIndentation();
            emitter().emit("set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)");
            emitter().emit("set(host_filenames\n\tcode-gen/host/Host.%s\n\tcode-gen/host/device_handle.%1$s\n)",
                    backend().context().getConfiguration().get(PlatformSettings.C99Host) ? "c" : "cpp");

            emitter().emit("add_executable(Host ${host_filenames})");

            emitter().emit("target_include_directories(Host PRIVATE code-gen/host)");
            emitter().emit("target_link_directories(Host PRIVATE ${SDACCEL_LIBRARY_DIR})");
            if (!backend().context().getConfiguration().get(PlatformSettings.C99Host)) {
                emitter().emit("set_target_properties(Host PROPERTIES");
                emitter().emit("\tCXX_STANDARD 11");
                emitter().emit("\tCXX_STANDARD_REQUIRED YES");
                emitter().emit("\tCXX_EXTENSIONS NO)");

                emitter().emit("target_link_libraries(Host xilinxopencl pthread rt dl crypt stdc++)");
            } else {
                emitter().emit("target_link_libraries(Host xilinxopencl pthread rt dl crypt)");
            }
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

}
