#cpp CPP library files
if(NO_DISPLAY)
	add_definitions(-DNO_DISPLAY)
else()
	find_package(SDL)
	include_directories(
		${SDL_INCLUDE_DIR} 
	)
endif()


set(cpp_runtime_sources
	src/file_utils.cpp
	src/file_read.cpp
	src/file_write.cpp
	src/get_opt.cpp
	src/mapping_parser.cpp
	src/sdl_display.cpp
	src/tinyxml2.cpp
)

set(cpp_runtime_header
	include/actor.h
	include/fifo.h
	include/file_read.h
	include/file_write.h
	include/file_utils.h
	include/get_opt.h
	include/mapping_parser.h
	include/sdl_display.h
	include/tinyxml2.h
	include/scheduling_miss_logger.h
)

add_library(cpp-runtime STATIC ${cpp_runtime_sources} ${cpp_runtime_header})
