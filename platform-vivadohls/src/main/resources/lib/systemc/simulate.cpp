#include "network_tester.h"
#include <memory>
#include <sstream>
#include <string>

struct Options {
  int trace_level;
  double period;
};
static void display_usage(char *binary_name) {

  std::cout << "Usage: " << binary_name << "  [<options>]" << std::endl;
  std::cout << "The program expects a set of files with .txt extension\n"
               "that contain the tokens values as input, e.g. if there\n"
               "is an input with name actor1_actor2 then token values \n"
               "will be read from actor1_actor2.txt"
            << std::endl;

  std::cout << "\toptions:" << std::endl;
  std::cout << "\t\t--trace-level       specify the level of detail of the vcd "
               "signal traces:"
            << std::endl;
  std::cout << "\t\t                    0: no traces" << std::endl;
  std::cout << "\t\t                    1: Top level network IO traces"
            << std::endl;
  std::cout << "\t\t                    2: + network queue traces" << std::endl;
  std::cout << "\t\t                    3: + trigger signal traces"
            << std::endl;
  std::cout << "\t\t--clock-period      the period of clock as a floating "
               "point number in nanoseconds, e.g. --clock-period 3.3"
            << std::endl;
  std::cout << "\t\t--help              print this message and exit."
            << std::endl;
}

static Options parse_args(int argc, char *argv[]) {

  int arg_ix = 1;
  Options opts;
  opts.period = 10.0;
  opts.trace_level = 0;

  while (arg_ix < argc) {
    const std::string opt = argv[arg_ix++];

    if (opt == "--trace-level") {
      std::stringstream string_convert;
      if (arg_ix >= argc) {
        display_usage(argv[0]);
        std::cerr << "Missing option value for " << opt << std::endl;
        std::exit(EXIT_FAILURE);
      }
      const std::string val = argv[arg_ix++];
      string_convert << val;
      string_convert >> opts.trace_level;

    } else if (opt == "--clock-period") {
      std::stringstream string_convert;
      if (arg_ix >= argc) {
        display_usage(argv[0]);
        std::cerr << "Missing option value for " << opt << std::endl;
        std::exit(EXIT_FAILURE);
      }
      const std::string val = argv[arg_ix++];
      string_convert << val;
      string_convert >> opts.period;

    } else if (opt == "--help") {
      display_usage(argv[0]);
      std::exit(EXIT_SUCCESS);
    } else {
      display_usage(argv[0]);
      std::cerr << "Invalid option " << opt << std::endl;
      std::exit(EXIT_FAILURE);
    }
  }

  std::cout << "trace-level: " << opts.trace_level << std::endl;
  std::cout << "clock-period: " << opts.period << " ns" << std::endl;
  return opts;
}

int sc_main(int argc, char *argv[]) {

  Options opts = parse_args(argc, argv);
  const sc_time period(opts.period, SC_NS);
  std::unique_ptr<ap_rtl::network_tester> mut(
      new ap_rtl::network_tester("network", period, opts.trace_level));
  mut->reset();
  mut->simulate();
}