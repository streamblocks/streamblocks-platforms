cmake_minimum_required(VERSION 3.10)

project(art-lib)

option(DISPLAY "Display on the screen." OFF)

find_package(Threads QUIET)
if(THREADS_FOUND)
    list(APPEND extra_libraries ${CMAKE_THREAD_LIBS_INIT})
    list(APPEND extra_definitions -DTHREADS_ENABLE)
else()
    message(STATUS "Cannot find Threads")
endif()


include_directories(
        art-node/include
)


add_subdirectory(art-node)



set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)

