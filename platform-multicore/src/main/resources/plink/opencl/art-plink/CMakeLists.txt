option(MPSOC "Is MPSoC" OFF)
option(OPENCL_VERBOSE "Diagnostic messages for OpenCL calls and events")
# ART PLink sources
set(art_plink_source
        src/device-port.cc
        src/plink.cc
        src/xcl2.cc
)


# -- Set CMake module path, for finding the necessary tools
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${PROJECT_SOURCE_DIR}/cmake)

find_package(OpenCL REQUIRED)
if(OpenCL_FOUND)
    list(APPEND extra_includes ${OpenCL_INCLUDE_DIRS})
    list(APPEND extra_libraries ${OpenCL_LIBRARIES})
else()
    message(FATAL_ERROR "OpenCL is not found, source SDx/Vitis settings.sh")
endif()

find_package(XRT REQUIRED)
if(XRT_FOUND)
    list(APPEND extra_includes ${XRT_INCLUDE_DIRS})
    list(APPEND extra_libraries ${XRT_LIBRARIES})
    if(MPSOC)
	list(APPEND extra_definitions "-DMPSOC")
    endif()
else()
    message(FATAL_ERROR "XRT is not found, source SDx/Vitis settings.sh")
endif()





add_library(art-plink ${art_plink_source})
target_include_directories(art-plink PUBLIC  ${XRT_INCLUDE_DIRS} include)
target_link_libraries(art-plink ${OpenCL_LIBRARIES} ${XRT_LIBRARIES})
set_target_properties(art-plink PROPERTIES CXX_STANDARD 14)

message(STATUS "${OpenCL_LIBRARIES}")

if (OPENCL_VERBOSE)
        target_compile_definitions(art-plink PUBLIC OCL_VERBOSE=1)
else()
        target_compile_definitions(art-plink PUBLIC OCL_VERBOSE=0)
endif()


if (MPSOC)
    target_compile_definitions(art-plink PUBLIC PLINK_MPSOC)
endif()

install(TARGETS art-plink
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)