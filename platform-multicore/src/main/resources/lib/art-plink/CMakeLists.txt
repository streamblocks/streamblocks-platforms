# ART PLink sources
set(art_plink_source
        src/device-handle.cc
        src/xcl2.cc
)

# # ART PLink includes
# set(art_plink_header
#         include/device-handle.h
#         include/xcl2.h
# )



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

option(OPENCL_VERBOSE "Diagnostic messages for OpenCL calls and events")


add_library(art-plink ${art_plink_source})
target_include_directories(art-plink PUBLIC ${SDACCEL_INCLUDE_DIRS} include)
target_link_libraries(art-plink ${SDACCEL_LIBRARIES})
set_target_properties(art-plink PROPERTIES CXX_STANDARD 14)



if (OPENCL_VERBOSE)
        target_compile_definitions(art-plink PUBLIC OCL_VERBOSE=1)
else()
        target_compile_definitions(art-plink PUBLIC OCL_VERBOSE=0)
endif()


install(TARGETS art-plink
        ARCHIVE DESTINATION lib)
