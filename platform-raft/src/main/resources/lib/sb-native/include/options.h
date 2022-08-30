#pragma once

#include <string>
#include <cmd>

namespace sb {
    namespace options{
        extern bool help;
        extern bool disable_display;
        extern std::string input_file;

        extern int loops;
        extern int frames;

        void add_options(CmdArgs &args);
    }
}

