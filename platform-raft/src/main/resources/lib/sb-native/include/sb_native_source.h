#pragma once

#include <sys/select.h>
#include <iostream>
#include <fstream>
#include <string>
#include <vector>
#include <stdlib.h>
#include <stdio.h>
#include "options.h"

namespace std {
    namespace io {
        namespace source {
            static std::ifstream file;

            static int loopsCount;
            static std::string input_file;

            char getLastUserChar();

            void source_init();

            int source_sizeOfFile();

            void source_rewind();

            unsigned int source_readByte();

            void source_readNBytes(unsigned char outTable[], unsigned int nbTokenToRead);

            unsigned int source_getNbLoop(void);

            void source_decrementNbLoops();

            bool source_isMaxLoopsReached();

            void source_exit(int exitCode);
        }
    }
}

