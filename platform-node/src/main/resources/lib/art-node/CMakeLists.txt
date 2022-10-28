# ART Runtime sources
set(art_node_sources
  src/actors-coder.cc
  src/actors-debug.cc
  src/actors-network.cc
  src/actors-parser.cc
  src/actors-registry.cc
  src/actors-rts.cc
  src/actors-teleport.cc
  src/cJSON.cc
  src/io-port.cc
  src/logging.cc
  src/main.cc
)

# ART Runtime includes
set(art_node_header
  include/actors-coder.h
  include/actors-debug.h
  include/actors-fifo.h
  include/actors-network.h
  include/actors-parser.h
  include/actors-registry.h
  include/actors-rts.h
  include/actors-teleport.h
  include/actors-typedefs.h
  include/cJSON.h
  include/dllist.h
  include/io-port.h
  include/logging.h
  include/natives.h
  include/slist.h
)

if(TORCH)
  list(APPEND art_node_header include/serialization.h)
  list(APPEND art_node_sources src/serialization.cc)
endif()


include_directories(${extra_includes})
add_definitions(${extra_definitions})

add_library(art-node STATIC ${art_node_sources} ${art_node_header})


if(MSVC)
  set_target_properties(art-node PROPERTIES COMPILE_FLAGS "/std:c++latest")
else()
  set_target_properties(art-node PROPERTIES COMPILE_FLAGS "-Wall -fPIC")
endif()

target_link_libraries(art-node ${extra_libraries})

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
if(APPLE)
  target_link_libraries(streamblocks  ${extra_libraries})
else()
  target_link_libraries(streamblocks dl stdc++fs ${extra_libraries})
endif()