
# CMakeLists to generate Systemc simulator


# Keep this off for now, still not implemented
option(VERILATOR_TRACES "Turn on Verilator traces" OFF)

set(VERILATOR_GEN_DIR ${CMAKE_CURRENT_BINARY_DIR}/verilated)

# Create the output directory for the Verilator generated sources
file(MAKE_DIRECTORY ${VERILATOR_GEN_DIR})

find_package(verilator HINTS $ENV{VERILATOR_ROOT} ${VERILATOR_ROOT} REQUIRED)

set(VERILATOR_ARGS
    --sc
    --clk ap_clk
    -Wno-fatal
    --make cmake
)

if (VERILATOR_TRACES)
  set(VERILATOR_ARGS
    ${VERILATOR_ARGS}
    --trace
  )
endif()


macro(verilate_actor ACTOR)

  # invisible to the file scope
  set(${ACTOR}_VERILATED_SOURCES
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule.cpp"
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__1.cpp"
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__2.cpp"
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__3.cpp"
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__Slow.cpp"
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__1__Slow.cpp"
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__Syms.cpp"
  )

  set(${ACTOR}_VERILATED_HEADERS
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule.h"
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__Slow.h"
    "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__Syms.h"
  )

  if (VERILATOR_TRACES)

    set(${ACTOR}_VERILATED_SOURCES
      ${${ACTOR}_VERILATED_SOURCES}
      "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__Trace__Slow.cpp"
      "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__Trace.cpp"
    )

    set(${ACTOR}_VERILATED_HEADERS
      ${${ACTOR}_VERILATED_HEADERS}
      "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__Trace__Slow.h"
      "${VERILATOR_GEN_DIR}/${ACTOR}_verimodule__Trace.h"
    )
  endif()


  # -- Phony target. A hack to get around the fact that we do not know what files verilator is gonna generate
  add_custom_target(
    ${ACTOR}_verimodule_phony
    DEPENDS ${ACTOR}
    COMMENT "Phony Verilating ${ACTOR} "
  )

  add_custom_command(
    TARGET ${ACTOR}_verimodule_phony PRE_BUILD
    COMMAND touch ${${ACTOR}_VERILATED_SOURCES} ${ACTOR}_phony_file.phony
    DEPENDS ${ACTOR}
  )

  # -- Real targets
  add_custom_command(
    OUTPUT ${${ACTOR}_VERILATED_SOURCES} ${${ACTOR}_VERILATED_HEADERS}
    COMMAND ${VERILATOR_BIN}
      ${VERILATOR_ARGS}
      --Mdir ${VERILATOR_GEN_DIR}
      --prefix ${ACTOR}_verimodule
      -y ${VERILOG_GEN_DIR}/${ACTOR}/solution/syn/verilog
      ${${ACTOR}_VERILOG_SOURCE} 2> ${ACTOR}_verimodule.log
    DEPENDS ${ACTOR} ${ACTOR}_verimodule_phony
    COMMENT "Verilating ${ACTOR}"
    VERBATIM
  )

  add_custom_target(
    ${ACTOR}_verimodule ALL
    DEPENDS ${${ACTOR}_VERILATED_SOURCES} ${${ACTOR}_VERILATED_HEADERS} ${ACTOR}_verimodule_phony
  )



endmacro()


function(copy_dat_ram_files ACTOR)
  add_custom_command(
    TARGET simulate art-systemc PRE_BUILD
    COMMAND cp
      ${VERILOG_GEN_DIR}/${ACTOR}/solution/syn/verilog/*.dat
      ${EXECUTABLE_OUTPUT_PATH} 2> /dev/null || true
    DEPENDS ${ACTOR}
    COMMENT "Copying ram files for ${ACTOR}"
  )
endfunction()


set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)

set(GLOBAL_VERILATED_SOURCES ${VERILATOR_ROOT}/include/verilated.cpp)

if(VERILATOR_TRACES)

  set(GLOBAL_VERILATED_SOURCES
    ${GLOBAL_VERILATED_SOURCES}
    ${VERILATOR_ROOT}/include/verilated_vcd_sc.cpp
    ${VERILATOR_ROOT}/include/verilated_vcd_c.cpp
  )

endif()


# Append the global sources
set(ALL_VERILATED_SOURCES
  ${GLOBAL_VERILATED_SOURCES}
)
# Append global headers
set (ALL_VERILATED_HEADERS
  ${GLOBAL_VERILATED_HEADERS}
)
foreach(__ACTOR__ ${__ACTORS_IN_NETWORK__})
  verilate_actor(${__ACTOR__})

  # Append actors sources
  set(ALL_VERILATED_SOURCES
    ${ALL_VERILATED_SOURCES}
    ${${__ACTOR__}_VERILATED_SOURCES}
  )

  # Append actor headers
  set(ALL_VERILATED_HEADERS
    ${ALL_VERILATED_HEADERS}
    ${${__ACTOR__}_VERILATED_HEADERS}
  )

endforeach()


set(ART_SYSTEMC_HEADERS
  ${VIVADO_HLS_INCLUDE_DIRS}
  ${PROJECT_SOURCE_DIR}/code-gen/include
  ${PROJECT_SOURCE_DIR}/systemc/include
  ${VERILATOR_ROOT}/include
  ${VERILATOR_ROOT}/include/vlstd
  ${VERILATOR_GEN_DIR}
)


add_library(art-systemc STATIC ${ALL_VERILATED_SOURCES})


target_include_directories(art-systemc PRIVATE ${ART_SYSTEMC_HEADERS})
set_target_properties(art-systemc PROPERTIES
  CXX_STANDARD 14
  CXX_STANDARD_REQUIRED YES
  CXX_EXTENSIONS NO
)

if (VERILATOR_TRACES)
  target_compile_definitions(art-systemc PUBLIC -DVM_TRACE=1)
endif()


verilator_link_systemc(art-systemc)

add_executable(simulate src/simulate.cpp)

target_include_directories(simulate PRIVATE ${ART_SYSTEMC_HEADERS})

target_link_libraries(simulate art-systemc)

set_target_properties(simulate PROPERTIES
		CXX_STANDARD 14
		CXX_STANDARD_REQUIRED YES
		CXX_EXTENSIONS NO
)



foreach(__ACTOR__ ${__ACTORS_IN_NETWORK__})

  copy_dat_ram_files(${__ACTOR__})

endforeach()





