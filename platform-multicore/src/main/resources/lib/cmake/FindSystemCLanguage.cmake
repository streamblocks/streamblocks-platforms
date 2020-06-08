# To specify the path to the SystemC installation, provide:
#   -DSYSTEMC_HOME=<installation directory>
# If you have used the CMAKE build process of SystemC library to build it then most
# probably the library is at /usr/local/systemc-2.3.3, Note that we recommend using CMAKE to build
# SystemC, using the configuration script is not supported.
# If successful, this script defines:
#   SYSTEMC_FOUND
#   SYSTEMC_LIB_DIR
#   SYSTEMC_INCLUDE_DIR


cmake_minimum_required(VERSION 3.3)
find_path(SYSTEMC_PATH
  NAMES systemc
  PATHS ${SYSTEMC_ROOT_DIR} ENV SYSTEMC_HOME
  PATH_SUFFIXES include
)

if (NOT EXISTS ${SYSTEMC_PATH})
  message(WARNING "SystemC library not found. Try setting the env variabel SYSTEMC_HOME")
else()

  get_filename_component(SYSTEMC_ROOT_DIR ${SYSTEMC_PATH} DIRECTORY)

  set(SYSTEMC_FOUND TRUE)
  set(SYSTEMC_LIB_DIR ${SYSTEMC_ROOT_DIR}/lib) # modify lib to lib-linux64 if you are using the configure script to build SystemC
  set(SYSTEMC_INCLUDE_DIR ${SYSTEMC_ROOT_DIR}/include)
  find_library(SYSTEMC_LIBRARY
        systemc
        PATHS ${SYSTEMC_LIB_DIR}
  )
  message(STATUS "Found SystemC library at ${SYSTEMC_ROOT_DIR}")


endif()
