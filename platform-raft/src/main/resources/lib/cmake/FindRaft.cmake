##
# start check for c libs
# var CMAKE_RAFT_LIBS will default to "" on non-unix platforms
##

find_library( RAFT_LIBRARY
        NAMES raft
        PATHS
        ${CMAKE_LIBRARY_PATH}
        $ENV{RAFT_PATH}/lib
        /usr/lib
        /usr/local/lib
        /opt/local/lib
        /usr/local/lib/raft
        /usr/local/raft
        /usr/lib/raft )


if( RAFT_LIBRARY )
    ##find path
    find_path( RAFT_INCLUDE
            NAMES raft
            PATHS
            ${CMAKE_INCLUDE_PATH}
            /usr/include/
            /usr/local/include/
            /opt/include/
            /opt/local/include/
            /usr/local/include/raft
            /usr/include/raft
            )
    if( NOT RAFT_INCLUDE )
        message( FATAL_ERROR "User selected to use Raft library, but not found in path" )
    endif( NOT RAFT_INCLUDE )
    get_filename_component( RAFT_LIBRARY ${RAFT_LIBRARY} DIRECTORY )
    set( CMAKE_RAFT_LDFLAGS "-L${RAFT_LIBRARY}" )
    link_directories( ${RAFT_LIBRARY} )
    set( CMAKE_RAFT_LIBS  "-lraft" )
    set( CMAKE_RAFT_INCS "-I${RAFT_INCLUDE}" )
    #set compilation to actually compile raft
    add_definitions( ${CMAKE_RAFT_FLAGS} )
    set(RAFT_FOUND TRUE)
    message( STATUS "Using Raft threading library" )
    message( STATUS "LIBRARY: ${RAFT_LIBRARY}" )
    message( STATUS "INCLUDE: ${RAFT_INCLUDE}" )
else( RAFT_LIBRARY )
    set( CMAKE_RAFT_LIBS "" )
    message( WARNING "Couldn't find Raft library" )
endif( RAFT_LIBRARY  )

mark_as_advanced( RAFT_LIBRARY )
