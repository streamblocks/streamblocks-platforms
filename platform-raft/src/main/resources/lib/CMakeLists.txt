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

find_package(Raft REQUIRED)
if(RAFT_FOUND)
    list(APPEND extra_libraries ${CMAKE_RAFT_LDFLAGS})
    list(APPEND extra_includes ${RAFT_INCLUDE})
else()
    message(STATUS "Cannot find Raft")
endif()

# -- Includes
include_directories(sb-native/include)

# -- Add sb native
add_subdirectory(sb-native)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)