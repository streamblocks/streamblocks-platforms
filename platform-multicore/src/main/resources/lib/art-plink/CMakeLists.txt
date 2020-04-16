# ART PLink sources
set(art_plink_source
        device-handle.c
        )

# ART PLink includes
set(art_runtime_header
        device-handle.h
        )

# -- Set CMake module path, for finding the necessary tools
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${CMAKE_SOURCE_DIR}/cmake)
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

add_library(art-plink STATIC ${art_plink_source} ${art_plink_header})

install(TARGETS art-plink
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)