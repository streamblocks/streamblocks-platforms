set(art_native_source
        src/art_Display_yuv.c
        src/art_Display_yuv_width_height.c
        src/art_Sink_bin.c
        src/art_Sink_real.c
        src/art_Sink_txt.c
        src/art_Source_bin.c
        src/art_Source_byte.c
        src/art_Source_real.c
        src/art_Source_txt.c
        src/display.c)

set(art_native_header
        include/display.h)

find_package(SDL QUIET)
if(SDL_FOUND)
    list(APPEND art_native_source src/display-sdl.c)
    list(APPEND extra_includes ${SDL_INCLUDE_DIR})
    list(APPEND extra_libraries ${SDL_LIBRARY})
else()
    list(APPEND art_native_source src/display-null.c)
endif()

include_directories(${extra_includes})
add_definitions(${extra_definitions})

add_library(art-native STATIC ${art_native_source} ${art_native_header})
target_link_libraries(art-native art-runtime ${extra_libraries})

install(TARGETS art-native
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)