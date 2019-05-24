# ART Runtime sources
set(art_node_sources
        src/actors-debug.c
        src/actors-network.c
        src/actors-parser.c
        src/actors-registry.c
        src/actors-teleport.c
        src/actors-rts.c
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
set_target_properties(art-node PROPERTIES COMPILE_FLAGS "-Wall -fPIC")

target_link_libraries(art-node art-native ${extra_libraries})

install(TARGETS art-node
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)

add_executable(streamblocks ${art_node_sources})
target_include_directories(streamblocks PRIVATE ./include)
target_link_libraries(streamblocks art-genomic art-native dl ${extra_libraries})