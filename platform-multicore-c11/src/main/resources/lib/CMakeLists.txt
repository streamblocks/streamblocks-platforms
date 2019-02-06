cmake_minimum_required(VERSION 3.10)

project(art-lib)

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

include_directories(
        art-runtime/include
        art-native/include
)

add_subdirectory(art-runtime)
add_subdirectory(art-native)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)

