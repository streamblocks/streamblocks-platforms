# To specify the path to the SDAccel installation, provide:
#   -DXRT_ROOT_DIR=<installation directory>
# If successful, this script defines:
#   XRT_FOUND
#   XRT_INCLUDE_DIRS
#   XRT_LIBRARIES

cmake_minimum_required(VERSION 2.8.12)

find_path(XRT_PATH
        NAMES xbutil
        PATHS ${XRT_ROOT_DIR} ENV XILINX_XRT
        PATH_SUFFIXES bin
        )

if(NOT EXISTS ${XRT_PATH})
    message(WARNING "XRT not found.")
else()

    get_filename_component(XRT_ROOT_DIR ${XRT_PATH} DIRECTORY)

    set(XRT_FOUND TRUE)
    set(XRT_INCLUDE_DIRS ${XRT_ROOT_DIR}/include/ ${XRT_ROOT_DIR}/include/xrt)

    # Runtime includes
    file(GLOB SUBDIRECTORIES ${XRT_ROOT_DIR}/runtime/include/
            ${XRT_ROOT_DIR}/runtime/include/*)
    foreach(SUBDIRECTORY ${SUBDIRECTORIES})
        if(IS_DIRECTORY ${SUBDIRECTORY})
            set(XRT_INCLUDE_DIRS ${XRT_INCLUDE_DIRS} ${SUBDIRECTORY})
        endif()
    endforeach()

    # OpenCL libraries
    if (CMAKE_SYSTEM_PROCESSOR MATCHES "(x86)|(X86)|(amd64)|(AMD64)")
        set(XRT_LIBRARY_DIR ${XRT_ROOT_DIR}/lib/)
        file(GLOB XRT_LIBRARIES ${XRT_ROOT_DIR}/lib/
                ${XRT_ROOT_DIR}/lib/*.so)
    endif()

    message(STATUS "Found XRT at ${XRT_ROOT_DIR}.")

endif()