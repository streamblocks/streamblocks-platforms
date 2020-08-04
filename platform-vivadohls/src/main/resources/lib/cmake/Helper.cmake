

set(__NETWORK_NAME__ @__NETWORK_TOP_NAME__@)
set(VERILOG_GEN_DIR ${CMAKE_CURRENT_BINARY_DIR})
message(STATUS "Top network ${__NETWORK_NAME__}")
# -- CMake Options
option(USE_VITIS "Build an RTL OpenCL Kernel for Vitis" OFF)
option(USE_SDACCEL "Build an RTL OpenCL Kernel for SDAccel" OFF)


# -- --------------------------------------------------------------------------
# -- Profile and Debug Options
# -- --------------------------------------------------------------------------
option(HW_PROFILE "Profile the DDR traffic" OFF)
option(HW_DEBUG "Add protocol checker debug cores" OFF)
if (HW_PROFILE)
	set(XOCC_PROFILE "--profile_kernel\tdata:all:all:all")
else()
	set(XOCC_PROFILE "")
endif()
if (HW_DEBUG)
	set(XOCC_DEBUG "--dk\tprotocol:${CMAKE_PROJECT_NAME}_kernel_1:all")
else()
	set(XOCC_DEBUG "")
endif()
# -- CMake Variables
set(FPGA_NAME "xcku115-flvb2104-2-e" CACHE STRING "Name of Xilinx FPGA, e.g \"xcku115-flvb2104-2-e\", \"xczu3eg-sbva484-1-e\",..")
set(HLS_CLOCK_PERIOD "10" CACHE STRING "Clock period in ns")
set(KERNEL FALSE)

# -- Set CMake module path, for finding the necessary tools
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${PROJECT_SOURCE_DIR}/cmake)

# -- --------------------------------------------------------------------------
# -- Minimal external tools requirement for the generated code
# -- --------------------------------------------------------------------------
find_package(VivadoHLS REQUIRED)
if (NOT VIVADO_HLS_FOUND)
	message(FATAL_ERROR "Vivado HLS not found, source Vivado settings.sh")
endif()

find_package(Vivado REQUIRED)
if (NOT VIVADO_FOUND)
	message(FATAL_ERROR "Vivado not found, source Vivado settings.sh")
endif()

# -- Use Vitis
if(USE_VITIS)
	if(USE_SDACCEL)
		message(FATAL_ERROR "You can use either Vitis or SDAccel")
	endif()

	find_package(Vitis REQUIRED)
	if (NOT VITIS_FOUND)
		message(FATAL_ERROR "Vitis is not found, source Vitis settings.sh")
	endif()

	find_package(XRT REQUIRED)
	if (NOT XRT_FOUND)
		message(FATAL_ERROR "XRT is not found, source XRT settings.sh")
	endif()

	set(KERNEL TRUE)
endif()

# -- Use SDAccel
if(USE_SDACCEL)
	if(USE_VITIS)
		message(FATAL_ERROR "You can use either SDAccle or Vitis")
	endif()

	find_package(SDAccel REQUIRED)
	if (NOT SDACCEL_FOUND)
		message(FATAL_ERROR "SDAccel is not found, source SDx settings.sh")
	endif()

	set(KERNEL TRUE)
endif()

# -- --------------------------------------------------------------------------
# -- Kernel configuration
# -- --------------------------------------------------------------------------
if (KERNEL)
	file(MAKE_DIRECTORY ${CMAKE_SOURCE_DIR}/bin)
	file(MAKE_DIRECTORY ${CMAKE_SOURCE_DIR}/bin/xclbin)
	file(MAKE_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/xclbin)
	set(TARGET "hw" CACHE STRING "Vitis/SDAccel TARGET : hw_emu, hw")


	if(USE_VITIS)
		set(FPGA_NAME "xczu3eg-sbva484-1-e" CACHE STRING "Name of Xilinx FPGA, e.g \"xcku115-flvb2104-2-e\", \"xczu3eg-sbva484-1-e\",..")
		set(PLATFORM "ultra96_base" CACHE STRING "Supported platform name, e.g \"xilinx_kcu1500_dynamic_5_0\", \"zcu102_base\", \"ultra96_base\",... ")
		set(HLS_CLOCK_PERIOD "6.667" CACHE STRING "Clock period in ns")
		set(KERNEL_FREQ "150" CACHE STRING "Clock frequency in MHz.")
	else()
		set(FPGA_NAME "xcku115-flvb2104-2-e" CACHE STRING "Name of Xilinx FPGA, e.g \"xcku115-flvb2104-2-e\", \"xczu3eg-sbva484-1-e\",..")
		set(PLATFORM "xilinx_kcu1500_dynamic_5_0" CACHE STRING "Supported platform name, e.g \"xilinx_kcu1500_dynamic_5_0\", \"zcu102_base\", \"ultra96_base\",... ")
		set(HLS_CLOCK_PERIOD "4" CACHE STRING "Clock period in ns")
		set(KERNEL_FREQ "300" CACHE STRING "Clock frequency in MHz.")
	endif()
else()
		set(FPGA_NAME "xcku115-flvb2104-2-e" CACHE STRING "Name of Xilinx FPGA, e.g \"xcku115-flvb2104-2-e\", \"xczu3eg-sbva484-1-e\",..")
		set(HLS_CLOCK_PERIOD "10" CACHE STRING "Clock period in ns")
endif()

# -- --------------------------------------------------------------------------
# -- Configure files for Vivado HLS
# -- --------------------------------------------------------------------------
configure_file(${PROJECT_SOURCE_DIR}/scripts/Synthesis.tcl.in Synthesis.tcl)
configure_file(${PROJECT_SOURCE_DIR}/scripts/${__NETWORK_NAME__}.tcl.in ${__NETWORK_NAME__}.tcl @ONLY)

# -- --------------------------------------------------------------------------
# -- Configure files for Vitis or SDAccel Kernel
# -- --------------------------------------------------------------------------
if(KERNEL)
	configure_file(${PROJECT_SOURCE_DIR}/scripts/package_kernel.tcl.in package_kernel.tcl @ONLY)
	configure_file(${PROJECT_SOURCE_DIR}/scripts/gen_xo.tcl.in gen_xo.tcl @ONLY)

	if(USE_VITIS)
		configure_file(${PROJECT_SOURCE_DIR}/scripts/sdaccel.ini.in ${CMAKE_SOURCE_DIR}/bin/xrt.ini @ONLY)
	else()
		configure_file(${PROJECT_SOURCE_DIR}/scripts/sdaccel.ini.in ${CMAKE_SOURCE_DIR}/bin/sdaccel.ini @ONLY)
	endif()
endif()

# -- --------------------------------------------------------------------------
# -- Source and Include folders for the generated code
# -- --------------------------------------------------------------------------


set(HLS_SOURCE_PATH ${PROJECT_SOURCE_DIR}/code-gen/src)
set(HLS_HEADER_PATH ${PROJECT_SOURCE_DIR}/code-gen/include)
set(HLS_TESTER_SOURCE_PATH ${PROJECT_SOURCE_DIR}/code-gen/src-tb)
set(HLS_TESTER_HEADER_PATH ${PROJECT_SOURCE_DIR}/code-gen/include-tb)

# -- --------------------------------------------------------------------------
# -- Helper macro to synthesize actor using vivado hls
# -- --------------------------------------------------------------------------

macro(synthesize_actor ACTOR)

	# This is visible in the file scope
	set(${ACTOR}_VERILOG_SOURCE "${VERILOG_GEN_DIR}/${ACTOR}/solution/syn/verilog/${ACTOR}.v")

	add_custom_command(
		OUTPUT ${${ACTOR}_VERILOG_SOURCE}
		COMMAND ${VIVADO_HLS_BINARY} -f Synthesis.tcl -tclargs ${ACTOR} ${ACTOR}.cpp > ${ACTOR}.log
		DEPENDS ${HLS_HEADER_PATH}/${ACTOR}.h ${HLS_SOURCE_PATH}/${ACTOR}.cpp
		COMMENT	"CSynthesizing ${ACTOR}"

	)

	add_custom_target(
		${ACTOR} ALL DEPENDS ${${ACTOR}_VERILOG_SOURCE}
	)

endmacro()

# -- --------------------------------------------------------------------------
# -- Helper macro to synthesize io stage actors using vivado hls
# -- --------------------------------------------------------------------------
macro(synthesize_io ACTOR)

	# This is visible in the file scope
	set(${ACTOR}_VERILOG_SOURCE "${VERILOG_GEN_DIR}/${ACTOR}/solution/syn/verilog/${ACTOR}.v")

	add_custom_command(
		OUTPUT ${${ACTOR}_VERILOG_SOURCE}
		COMMAND ${VIVADO_HLS_BINARY} -f Synthesis.tcl -tclargs ${ACTOR} ${ACTOR}.cpp > ${ACTOR}.log
		DEPENDS ${HLS_HEADER_PATH}/iostage.h ${HLS_SOURCE_PATH}/${ACTOR}.cpp
		COMMENT	"CSynthesizing ${ACTOR}"

	)

	add_custom_target(
		${ACTOR} ALL DEPENDS ${${ACTOR}_VERILOG_SOURCE}
	)

endmacro()

# -- --------------------------------------------------------------------------
# -- Helper macro to for C++ simulation
# -- --------------------------------------------------------------------------

function(actor_tester ACTOR)

    add_executable(${ACTOR}_tester ${HLS_SOURCE_PATH}/${ACTOR}.cpp ${HLS_TESTER_SOURCE_PATH}/tb_${ACTOR}.cpp)
    target_include_directories(${ACTOR}_tester PRIVATE ${HLS_HEADER_PATH} ${HLS_TESTER_HEADER_PATH})

endfunction()

# -- get tester for each actor and then the network
set(__NETWORK_TESTER_SOURCES__
        ${HLS_TESTER_SOURCE_PATH}/tb_${__NETWORK_NAME__}.cpp
)
foreach(__ACTOR__ ${__ACTORS_IN_NETWORK__})
    actor_tester(${__ACTOR__})

    set(__NETWORK_TESTER_SOURCES__
        ${__NETWORK_TESTER_SOURCES__}
        ${HLS_SOURCE_PATH}/${__ACTOR__}.cpp
    )
endforeach()

add_executable(${__NETWORK_NAME__}_tester ${__NETWORK_TESTER_SOURCES__})
target_include_directories(${__NETWORK_NAME__}_tester PRIVATE ${HLS_HEADER_PATH} ${HLS_TESTER_HEADER_PATH})


# -- --------------------------------------------------------------------------
# -- Synthesis targets for actors
# -- --------------------------------------------------------------------------
foreach(__ACTOR__ ${__ACTORS_IN_NETWORK__})
	synthesize_actor(${__ACTOR__})
endforeach()


if (KERNEL)

# -- --------------------------------------------------------------------------
# -- Synthesis targets for input and output stages
# -- --------------------------------------------------------------------------

	foreach(__ACTOR__ ${__INPUT_STAGE_ACTORS__} ${__OUTPUT_STAGE_ACTORS__})
		synthesize_io(${__ACTOR__})
	endforeach()

# -- --------------------------------------------------------------------------
# -- Emulation config util
# -- --------------------------------------------------------------------------

	if (USE_VITIS)
		set(EMCONFIGUTIL ${VITIS_EMCONFIGUTIL})
		set(XCLBIN_COMPILER ${VITIS_VPP})
	else()
		set(EMCONFIGUTIL ${SDACCEL_EMCONFIGUTIL})
		set(XCLBIN_COMPILER ${SDACCEL_XOCC})
	endif()

	add_custom_command(
		OUTPUT ${CMAKE_SOURCE_DIR}/bin/emconfig.json
		COMMAND ${EMCONFIGUTIL} --nd 1 --platform ${PLATFORM} --od ${CMAKE_SOURCE_DIR}/bin > emconfigutil.log
		COMMENT "Genrating emconfig for hw emulation"
	)
	add_custom_target(emconfig ALL DEPENDS ${CMAKE_SOURCE_DIR}/bin/emconfig.json)


# -- --------------------------------------------------------------------------
# -- IP package (XO file)
# -- --------------------------------------------------------------------------

	add_custom_command(
		OUTPUT  ${CMAKE_CURRENT_BINARY_DIR}/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xo
		COMMAND ${VIVADO_BINARY}
			-mode batch -source gen_xo.tcl -tclargs
			${CMAKE_CURRENT_BINARY_DIR}/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xo
			${__NETWORK_NAME__}_kernel ${TARGET} ${PLATFORM}  > ${__NETWORK_NAME__}_kernel_xo.log
		DEPENDS ${__ACTORS_IN_KERNEL__} emconfig
		COMMENT "Packaging kernel into XO"
	)
	add_custom_target(${__NETWORK_NAME__}_kernel_xo ALL
		DEPENDS
		${CMAKE_CURRENT_BINARY_DIR}/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xo
	)

# -- --------------------------------------------------------------------------
# -- Kernel binary (XCLBIN file)

	# -- Get the number of processors
	include(ProcessorCount)
	ProcessorCount(__CORE_COUNT__)
	if(NOT __CORE_COUNT__ EQUAL 0)

		message(STATUS "Detected ${__CORE_COUNT__} cores. ${XCLBIN_COMPILER} will be called with ${XCLBIN_JOBS_FLAG}")
		add_custom_command(
			OUTPUT  ${CMAKE_SOURCE_DIR}/bin/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xclbin
			COMMAND ${XCLBIN_COMPILER} --link -g -t ${TARGET} --platform ${PLATFORM} --kernel_frequency ${KERNEL_FREQ}  --save-temps
				${XOCC_DEBUG} ${XOCC_PROFILE} -j${__CORE_COUNT__}
				-o ${CMAKE_SOURCE_DIR}/bin/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xclbin
				${CMAKE_CURRENT_BINARY_DIR}/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xo
				> ${__NETWORK_NAME__}_kernel_xclbin.log
			DEPENDS ${__NETWORK_NAME__}_kernel_xo
			COMMENT "Generating FPGA binary, if TARGET=hw can take several hours. (-j ${__CORE_COUNT__})"
		)
		set(XCLBIN_JOBS_FLAG -j${__CORE_COUNT__} CACHE STRING "Number of parallel jobs to build the FPGA binary")
	else()
		message(INFO "Could not detect number of cores, omitting -j option to ${XCLBIN_COMPILER}")
		add_custom_command(
			OUTPUT  ${CMAKE_SOURCE_DIR}/bin/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xclbin
			COMMAND ${XCLBIN_COMPILER} --link -g -t ${TARGET} --platform ${PLATFORM} --kernel_frequency ${KERNEL_FREQ}  --save-temps
				${XOCC_DEBUG} ${XOCC_PROFILE}
				-o ${CMAKE_SOURCE_DIR}/bin/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xclbin
				${CMAKE_CURRENT_BINARY_DIR}/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xo
				> ${__NETWORK_NAME__}_kernel_xclbin.log
			DEPENDS ${__NETWORK_NAME__}_kernel_xo
			COMMENT "Generating FPGA binary, if TARGET=hw can take several hours.)"
		)
		set(XCLBIN_JOBS_FLAG "" CACHE STRING "Number of parallel jobs to build the FPGA binary")
	endif()

	add_custom_target(${__NETWORK_NAME__}_kernel_xclbin ALL
		DEPENDS ${CMAKE_SOURCE_DIR}/bin/xclbin/${__NETWORK_NAME__}_kernel.${TARGET}.${PLATFORM}.xclbin
	)


endif()

# -- --------------------------------------------------------------------------
# -- Vivado Project (can be used for RTL simulation)
# -- --------------------------------------------------------------------------
add_custom_command(
	OUTPUT ${PROJECT_SOURCE_DIR}/output/${__NETWORK_NAME__}/${__NETWORK_NAME__}.xpr
	COMMAND ${VIVADO_BINARY} -mode batch -source ${__NETWORK_NAME__}.tcl  > ${__NETWORK_NAME__}.log
	DEPENDS ${__ACTORS_IN_NETWORK__}
	COMMENT "Creating Vivado project for ${__NETWORK_NAME__}"
)
add_custom_target(${__NETWORK_NAME__}_vivado_project ALL DEPENDS ${PROJECT_SOURCE_DIR}/output/${__NETWORK_NAME__}/${__NETWORK_NAME__}.xpr)
