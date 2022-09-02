
set(CPP_SRC_FILES 
  src/allocate.cpp
  src/basicparallel.cpp
  src/blocked.cpp
  src/common.cpp
  src/dynalloc.cpp
  src/fifo.cpp
  src/graphtools.cpp
  src/kernel.cpp
  src/kernel_all.cpp
  src/kernelexception.cpp
  src/kernel_pair_t.cpp
  src/kernel_wrapper.cpp
  src/kpair.cpp
  src/leastusedfirst.cpp
  src/makedot.cpp
  src/mapbase.cpp
  src/map.cpp
  src/mapexception.cpp
  src/noparallel.cpp
  src/parallelk.cpp
  src/partition_basic.cpp
  src/partition_dummy.cpp
  src/partition_scotch.cpp
  src/pointer.cpp
  src/poolschedule.cpp
  src/port.cpp
  src/port_info.cpp
  src/portexception.cpp
  src/portiterator.cpp
  src/porttemplate.cpp
  src/raftexception.cpp
  src/roundrobin.cpp
  src/schedule.cpp
  src/signal.cpp
  src/signaldata.cpp
  src/simpleschedule.cpp
  src/stdalloc.cpp
  src/submap.cpp
  src/systemsignalhandler.cpp
)

include_directories(./include ./include/raftic)

add_library( sb-raft ${CPP_SRC_FILES} )
target_compile_definitions(sb-raft PRIVATE -DLIBCOMPILE=1 -DRDTSCP=1 L1D_CACHE_LINE_SIZE=64 STRING_NAMES=1)

target_include_directories(sb-raft 
PUBLIC
include
include/raftic 
${CMAKE_SCOTCH_INCS}
${CMAKE_QTHREAD_INCS}
)

target_link_libraries( 
  sb-raft 
  ${CMAKE_SCOTCH_LIBS}
  ${CMAKE_QTHREAD_LIBS} 
  demangle
  affinity
)
set_property(TARGET sb-raft  PROPERTY CXX_STANDARD 14)



# Enable warnings if using clang or gcc.
if ( "${CMAKE_CXX_COMPILER_ID}" MATCHES "Clang" 
OR "${CMAKE_CXX_COMPILER_ID}" MATCHES "GNU" )

target_compile_options( sb-raft
                    PUBLIC
                    "-W" "-Wall" "-Wextra" "-Wpedantic" )

endif()

