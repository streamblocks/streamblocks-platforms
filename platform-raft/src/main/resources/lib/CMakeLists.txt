cmake_minimum_required(VERSION 3.10)

project(sb-lib)

# -- Library options
option(DISPLAY "Display on the screen." ON)

option(USE_QTHREAD "Use QThread." ON)

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

# -- Includes
include_directories(sb-native/include)

# -- Add sb native
add_subdirectory(sb-native)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)