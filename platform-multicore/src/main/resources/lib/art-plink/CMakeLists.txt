# ART PLink sources
set(art_plink_source
        device-handle.cc
        )

# ART PLink includes
set(art_plink_header
        device-handle.h
        )



# -- Set CMake module path, for finding the necessary tools
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${PROJECT_SOURCE_DIR}/cmake)
find_package(Vivado REQUIRED)
find_package(VivadoHLS REQUIRED)
find_package(SDAccel REQUIRED)
if(SDACCEL_FOUND)
    list(APPEND extra_includes ${SDACCEL_INCLUDE_DIRS})
    list(APPEND extra_libraries ${SDACCEL_LIBRARIES})
else()
    message(FATAL_ERROR "SDAccel is not found, source SDx settings.sh")
endif()

include_directories(${extra_includes})
add_definitions(${extra_definitions})


option(OPENCL_VERBOSE "Prints messages for OpenCL calls and events")
add_library(art-plink STATIC ${art_plink_source} ${art_plink_header})
set_target_properties(art-plink PROPERTIES COMPILE_FLAGS "-Wall -fPIC"
)
if (OPENCL_VERBOSE)
        target_compile_definitions(art-plink PRIVATE OCL_VERBOSE=1)
else()
        target_compile_definitions(art-plink PRIVATE OCL_VERBOSE=0)
endif()

install(TARGETS art-plink
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)