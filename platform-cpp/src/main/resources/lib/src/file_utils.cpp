#include "file_utils.h"
#include <fstream>
#include <string>

int file_get_lines(char* file) {
	int numLines = 0;
	std::ifstream in(file);
	std::string unused;
	while (std::getline(in, unused)) {
		numLines++;
	}
	return numLines;
}
