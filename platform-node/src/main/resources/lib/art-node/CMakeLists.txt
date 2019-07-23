# ART Runtime sources
set(art_node_sources
        src/actors-debug.cc
        src/actors-network.cc
        src/actors-parser.cc
        src/actors-registry.cc
        src/actors-teleport.cc
        src/main.cc
)

# ART Runtime includes
set(art_node_header
        include/actors-debug.h
        include/actors-fifo.h
        include/actors-network.h
        include/actors-parser.h
        include/actors-registry.h
        include/actors-rts.h
        include/actors-teleport.h
        include/actors-typedefs.h
        include/dllist.h
        include/natives.h
)

include_directories(${extra_includes})
add_definitions(${extra_definitions})

add_library(art-node STATIC ${art_node_sources} ${art_node_header})
#

if(MSVC)
  set_target_properties(art-node PROPERTIES COMPILE_FLAGS "/std:c++latest")
else()
  set_target_properties(art-node PROPERTIES COMPILE_FLAGS "-Wall -fPIC -fsanitize=address")
endif()

target_link_libraries(art-node art-native ${extra_libraries})

install(TARGETS art-node
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)



add_executable(streamblocks ${art_node_sources})
if (MSVC) 
  target_link_options(streamblocks PRIVATE $<$<CONFIG:RELWITHDEBINFO>:/PROFILE>)
endif()
target_include_directories(streamblocks PRIVATE ./include)
if(UNIX)
  target_link_libraries(streamblocks art-native dl stdc++fs ${extra_libraries})
else()
target_link_libraries(streamblocks art-native ${extra_libraries})
endif()