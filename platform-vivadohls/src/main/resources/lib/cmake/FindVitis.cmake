# To specify the path to the Vitis installation, provide:
#   -DVITIS_ROOT_DIR=<installation directory>
# If successful, this script defines:
#   VITIS_FOUND
#   VITIS_INCLUDE_DIRS
#   VITIS_LIBRARIES
#   VITIS_VPP
#   VITIS_VIVADO_HLS

cmake_minimum_required(VERSION 2.8.12)

find_path(VPP_PATH
  NAMES v++
  PATHS ${VITIS_ROOT_DIR} ENV XILINX_VITIS
  PATH_SUFFIXES bin
)

if(NOT EXISTS ${VPP_PATH})

  message(WARNING "Vitis not found.")

else()

  get_filename_component(VITIS_ROOT_DIR ${VPP_PATH} DIRECTORY)

  set(VITIS_FOUND TRUE)
  set(VITIS_INCLUDE_DIRS ${VITIS_ROOT_DIR}/include/)
  set(VITIS_EMCONFIGUTIL ${VITIS_ROOT_DIR}/bin/emconfigutil)
  set(VITIS_VPP ${VITIS_ROOT_DIR}/bin/v++)
  set(VITIS_VIVADO_HLS ${VITIS_ROOT_DIR}/Vivado_HLS/bin/vivado_hls)

  message(STATUS "Found SDAccel at ${VITIS_ROOT_DIR}.")

endif()
