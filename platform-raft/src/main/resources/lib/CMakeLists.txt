cmake_minimum_required(VERSION 3.10)

project(sb-lib)

# -- Library options
option(DISPLAY "Display on the screen." OFF)

option(USE_QTHREAD "Use QThread." OFF)

find_package(Threads QUIET)
if(THREADS_FOUND)
    list(APPEND extra_libraries ${CMAKE_THREAD_LIBS_INIT})
    list(APPEND extra_definitions -DTHREADS_ENABLE)
else()
    message(STATUS "Cannot find Threads")
endif()

if(USE_QTHREAD)
    find_package(QThread QUIET)
    if(QTHREAD_FOUND)
        list(APPEND extra_libraries ${CMAKE_QTHREAD_LIBS})
        message(STATUS "sdd find QThread")
    else()
        message(STATUS "Cannot find QThread")
    endif()
endif()


#list(APPEND extra_includes  )

# -- Add sb native
add_subdirectory(helpers)
add_subdirectory(demangle)
add_subdirectory(affinity)
add_subdirectory(shm)
add_subdirectory(cmdargs)
add_subdirectory(sb-raft)
add_subdirectory(sb-native)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)