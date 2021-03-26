set(sb_native_sources
        src/sb_native_source.cpp
        src/sb_native_counters.cpp
        src/sb_native_compareyuv.cpp src/options.cpp)

set(sb_native_headers
        include/sb_native_source.h
        include/sb_native_display.h
        include/sb_native_framerate.h
        include/sb_native_counters.h
        include/sb_native_compareyuv.h include/options.h)

include_directories(${extra_includes})
add_definitions(${extra_definitions})


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

# Compile libraries
add_library(sb-native STATIC ${sb_native_sources} ${sb_native_headers})

# Do the linking
target_link_libraries(sb-native raft demangle affinity cmdargs ${extra_libraries})

install(TARGETS sb-native
        ARCHIVE DESTINATION lib
        )

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)
