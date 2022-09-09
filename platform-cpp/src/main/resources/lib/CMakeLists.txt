cmake_minimum_required(VERSION 3.10)

project(sb-cpp-runtime)

# -- Library options
option(DISPLAY "Display on the screen." OFF)
option(TRACE_TURNUS "Generate execution trace for TURNUS." ON)

if(TRACE_TURNUS)
    list(APPEND extra_definitions "-DTRACE_TURNUS")
endif()

set(sb_native_sources
	src/sb_native_source.cpp
	src/sb_native_counters.cpp
	src/sb_native_compareyuv.cpp 
	src/get_opt.cpp
	src/mapping_parser.cpp
	src/tinyxml2.cpp
)

set(sb_native_headers
	include/sb_native_source.h
	include/sb_native_display.h
	include/sb_native_framerate.h
	include/sb_native_counters.h
	include/sb_native_compareyuv.h
	include/actor.h
	include/fifo.h
	
	include/get_opt.h
	include/mapping_parser.h
	
	include/tinyxml2.h
	include/scheduling_miss_logger.h
)


# Try to find SDL on the user system
find_package(SDL2 QUIET)
if (DISPLAY)
    if (SDL2_FOUND)
        list(APPEND extra_includes ${SDL2_INCLUDE_DIRS})
        string(STRIP "${SDL2_LIBRARIES}" SDL2_LIBRARIES)
        list(APPEND extra_libraries ${SDL2_LIBRARIES})
        list(APPEND extra_definitions -DDISPLAY_ENABLE)

        list(APPEND sb_native_sources
                src/sb_native_display_sdl2.cpp
                src/sb_native_framerate_sdl.cpp
                )
    else ()
        list(APPEND sb_native_sources
                src/sb_native_no_display.cpp
                src/sb_native_framerate.cpp
                )
    endif ()
else ()
    list(APPEND sb_native_sources
            src/sb_native_no_display.cpp
            src/sb_native_framerate.cpp
            )
endif ()

include_directories(${extra_includes})
add_definitions(${extra_definitions})

add_library(sb-cpp-runtime STATIC ${sb_native_sources} ${sb_native_headers})

# Do the linking
target_link_libraries(sb-cpp-runtime ${extra_libraries})

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)
