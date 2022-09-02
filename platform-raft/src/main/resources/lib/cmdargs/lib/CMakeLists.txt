set( LIBSRC command_arguments.cpp command_option_base.cpp )

include_directories( ${PROJECT_SOURCE_DIR}/include  
                     ${PROJECT_SOURCE_DIR}/include/cmdargs ) 

add_library( cmdargs ${LIBSRC} )

# Enable warnings if using clang or gcc.
if ( "${CMAKE_CXX_COMPILER_ID}" MATCHES "Clang" 
  OR "${CMAKE_CXX_COMPILER_ID}" MATCHES "GNU" )

target_compile_options( cmdargs
                        PUBLIC
                        "-W" "-Wall" "-Wextra" "-Wpedantic" "-Wno-unused-parameter" )

endif()

install( TARGETS cmdargs
         ARCHIVE DESTINATION ${CMAKE_INSTALL_PREFIX}/lib )
