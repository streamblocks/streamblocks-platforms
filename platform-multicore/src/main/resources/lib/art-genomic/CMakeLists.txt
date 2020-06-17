set(art_genomic_source
        src/art_Fasta.cc
        )

set(art_genomic_header
        include/kseq.h)


find_package(ZLIB REQUIRED)
if(ZLIB_FOUND)
    list(APPEND extra_includes ${ZLIB_INCLUDE_DIRS})
    list(APPEND extra_libraries ${ZLIB_LIBRARIES})
endif()

include_directories(${extra_includes})
add_definitions(${extra_definitions})

add_library(art-genomic STATIC ${art_genomic_source} ${art_genomic_header})
IF(ART_NODE)
    target_link_libraries(art-genomic art-node ${extra_libraries})
else()
    target_link_libraries(art-genomic art-runtime ${extra_libraries})
endif()
install(TARGETS art-genomic
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)