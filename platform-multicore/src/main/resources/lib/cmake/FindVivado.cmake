# To specify the path to the Vivado HLS installation, provide:
#   -DVIVADO_ROOT_DIR=<installation directory>
# If successful, this script defines:
#   VIVADO_FOUND
#   VIVADO_BINARY

cmake_minimum_required(VERSION 3.3)

find_path(VIVADO_PATH
  NAMES vivado
  PATHS ${VIVADO_ROOT_DIR} ENV XILINX_VIVADO
  PATH_SUFFIXES bin
)

if(NOT EXISTS ${VIVADO_PATH})
  message(WARNING "Vivado not found.")
else()

  get_filename_component(VIVADO_ROOT_DIR ${VIVADO_PATH} DIRECTORY)

  set(VIVADO_FOUND TRUE)
  set(VIVADO_BINARY ${VIVADO_ROOT_DIR}/bin/vivado)

  message(STATUS "Found Vivado at ${VIVADO_ROOT_DIR}.")

endif()