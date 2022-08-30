#include "options.h"


namespace sb {
    namespace options {
        bool help(false);
        bool disable_display(false);
        std::string input_file;

        int loops = -1;
        int frames = -1;


        void add_options(CmdArgs &cmdargs){

            cmdargs.addOption(new Option<bool>(help,
                                               "-h",
                                               "print this message"));

            cmdargs.addOption(new Option<std::string>(input_file,
                                                      "-i",
                                                      "input file"));

            cmdargs.addOption(new Option<bool>(disable_display,
                                               "-n",
                                               "disable display"));

            cmdargs.addOption(new Option<int>(loops,
                                              "-l",
                                              "number of loops"));

            cmdargs.addOption(new Option<int>(frames,
                                              "-f",
                                              "number of frames to display"));
        }

    }
}

