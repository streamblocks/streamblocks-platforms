#include <iostream>
#include <fstream>
#include <string>

#include "file_write.h"
#include "get_opt.h"
#include <stdlib.h>

static std::ofstream file;

void Writer_init() {
	if (write_file.empty()) {
		std::cerr << "No output write file given!" << std::endl;
		exit(1);
	}

	file.open(write_file.c_str(), std::ios::binary);
	if (!file.is_open()) {
		std::cerr << "could not open file " << input_file << std::endl;
		exit(1);
	}

}

void Writer_write(uint8_t byte) {
	file << byte;
	}

void Writer_close() {
	file.close();
}
