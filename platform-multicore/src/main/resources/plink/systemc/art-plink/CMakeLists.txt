option(MPSOC "Is MPSoC" OFF)

# ART PLink sources
set(art_plink_source
        src/plink.cc
)


option(OPENCL_VERBOSE "Diagnostic messages for OpenCL calls and events")

add_library(art-plink ${art_plink_source})
target_include_directories(art-plink PUBLIC ./include)
target_link_libraries(art-plink art-systemc)
set_target_properties(art-plink PROPERTIES CXX_STANDARD 14)


if (OPENCL_VERBOSE)
        target_compile_definitions(art-plink PUBLIC OCL_VERBOSE=1)
else()
        target_compile_definitions(art-plink PUBLIC OCL_VERBOSE=0)
endif()


install(TARGETS art-plink
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)