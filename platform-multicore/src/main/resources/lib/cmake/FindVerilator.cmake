# To specify the path to the Verilator installation, provide:
#   -DVERILATOR_ROOT_DIR=<installation directory>

cmake_minimum_required(VERSION 3.3)
find_path(VERILATOR_PATH
  NAMES verilated.h
  PATHS ${VERILATOR_ROOT_DIR} ENV VERILATOR_HOME
  PATH_SUFFIXES include
)

if (NOT EXISTS ${VERILATOR_PATH})
  message(FATAL_ERROR "Verilator header files not found. Try setting the env variabel VERILATOR_HOME")
else()

  get_filename_component(VERILATOR_ROOT_DIR ${VERILATOR_PATH} DIRECTORY)
  set(VERILATOR_INCLUDE_DIR ${VERILATOR_ROOT_DIR}/include)
  set(VERILATOR_STD_INCLUDE_DIR ${VERILATOR_ROOT_DIR}/include/vltstd)

endif()
