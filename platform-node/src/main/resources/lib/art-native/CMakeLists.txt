set(art_native_source
    src/art_Display_yuv.cc
    src/art_Display_yuv_width_height.cc
    src/art_Sink_bin.cc
    src/art_Sink_real.cc
    src/art_Sink_txt.cc
    src/art_Source_bin.cc
    src/art_Source_byte.cc
    src/art_Source_real.cc
    src/art_Source_txt.cc
    src/art_print.cc
    src/display.cc)



#string(STRIP "${SDL2_LIBRARIES}" SDL2_LIBRARIES)


set(art_native_header
        include/display.h)

find_package(SDL2 QUIET)
if(SDL2_FOUND)
    if(DISPLAY)
      list(APPEND art_native_source src/display-sdl.cc)
      list(APPEND extra_includes ${SDL2_INCLUDE_DIRS})
      list(APPEND extra_libraries ${SDL2_LIBRARIES})
    else()
      list(APPEND art_native_source src/display-null.cc)
    endif()
else()
    list(APPEND art_native_source src/display-null.cc)
endif()

include_directories(${extra_includes})
add_definitions(${extra_definitions})

add_library(art-native STATIC ${art_native_source} ${art_native_header})
if(MSVC)
    set_target_properties(art-native PROPERTIES COMPILE_FLAGS "/std:c++latest")
else()
    set_target_properties(art-native PROPERTIES COMPILE_FLAGS "-Wall -fPIC")
endif()

target_link_libraries(art-native ${extra_libraries})

install(TARGETS art-native ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)
