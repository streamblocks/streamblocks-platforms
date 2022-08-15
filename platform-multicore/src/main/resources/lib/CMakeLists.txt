cmake_minimum_required(VERSION 3.3)

project(art-lib)

# -- Options
option(DISPLAY "Display on the screen." ON)
option(DISPLAY_ACCELERATED "Display on the screen." OFF)
option(TRACE "Generate execution trace." OFF)
option(TRACE_TURNUS "Generate execution trace for TURNUS." OFF)

find_package(Threads QUIET)
if(THREADS_FOUND)
    list(APPEND extra_libraries ${CMAKE_THREAD_LIBS_INIT})
    list(APPEND extra_definitions -DTHREADS_ENABLE)
else()
    message(STATUS "Cannot find Threads")
endif()

find_package(LibXml2 REQUIRED)
if(LIBXML2_FOUND)
   list(APPEND extra_libraries ${LIBXML2_LIBRARIES})
   list(APPEND extra_definitions ${LIBXML2_DEFINITIONS})
   list(APPEND extra_includes ${LIBXML2_INCLUDE_DIR})
else()
    message(STATUS "Cannot find LibXml2")
endif()

if(TRACE)
    list(APPEND extra_definitions "-DTRACE")
endif()

if(TRACE_TURNUS)
    list(APPEND extra_definitions "-DTRACE_TURNUS")
endif()

if(DISPLAY_ACCELERATED)
    list(APPEND extra_definitions "-DDISPLAY_ACCELERATED")
endif()

if(ART_NODE)
    include_directories(
            art-node/include
            art-native/include
            art-genomic/include
    )
    add_subdirectory(art-node)
else()
    include_directories(
            art-runtime/include
            art-native/include
            art-genomic/include
    )
    add_subdirectory(art-runtime)
endif()


add_subdirectory(art-native)
add_subdirectory(art-genomic)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)

