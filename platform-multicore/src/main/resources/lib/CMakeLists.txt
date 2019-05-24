cmake_minimum_required(VERSION 3.10)

project(art-lib)

option(DISPLAY "Display on the screen." ON)

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

