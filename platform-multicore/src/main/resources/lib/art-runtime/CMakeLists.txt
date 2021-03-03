# ART Runtime sources
set(art_runtime_sources
        src/options.c
        src/util.c
        src/actors-rts.c
        src/termination-report.c
        src/xmlParser.c
        src/xmlTrace.c
        src/jsonTrace.c
        src/estimated_frequency.c
        )

# ART Runtime includes
set(art_runtime_header
        include/options.h
        include/util.h
        include/actors-config.h
        include/actors-fifo.h
        include/actors-rts.h
        include/actors_execute_network.h
        include/dll.h
        include/internal.h
        include/natives.h
        include/xmlParser.h
        include/xmlTrace.h
        include/jsonTrace.h
        src/estimated_frequency.c)

include_directories(${extra_includes})
add_definitions(${extra_definitions})

add_library(art-runtime STATIC ${art_runtime_sources} ${art_runtime_header})

target_link_libraries(art-runtime art-native ${extra_libraries})

install(TARGETS art-runtime
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)