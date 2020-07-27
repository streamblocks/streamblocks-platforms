#include "network_tester.h"
#include <memory>
#include <sstream>
#include <string>

struct Options {
  int trace_level;
  double period;
  unsigned int queue_capacity;
  std::size_t alloc_size;
  std::size_t report_every;
  std::string profile_dump;
};
static void displayUsage(char *binary_name) {

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
  std::cout << "\t\t--buffer-size       the size of device memory buffers for "
               "network ports, default is 4096 tokens."
            << std::endl;
  std::cout << "\t\t--queue-depth       the internal fifo capacity, " << std::endl;
  std::cout << "\t\t                    does not override the 'bufferSize' " << std::endl;
  std::cout << "\t\t                    attribute from the CAL source files" << std::endl;
  std::cout << "\t\t                    default value is 512" << std::endl;
  std::cout << "\t\t--report-every      intervals between report before each "
               "simulation call returns"
            << std::endl;
  std::cout << "\t\t--profile           profile xml dump file" << std::endl;
  std::cout << "\t\t--help              print this message and exit."
            << std::endl;
}

static Options parseArgs(int argc, char *argv[]) {

  int arg_ix = 1;
  Options opts;
  opts.period = 10.0;
  opts.trace_level = 0;
  opts.alloc_size = 4096;
  opts.queue_capacity = 512;
  opts.report_every = 100000;
  opts.profile_dump = "";

  while (arg_ix < argc) {
    const std::string opt = argv[arg_ix++];

    if (opt == "--trace-level") {
      std::stringstream string_convert;
      if (arg_ix >= argc) {
        displayUsage(argv[0]);
        std::cerr << "Missing option value for " << opt << std::endl;
        std::exit(EXIT_FAILURE);
      }
      const std::string val = argv[arg_ix++];
      string_convert << val;
      string_convert >> opts.trace_level;

    } else if (opt == "--clock-period") {
      std::stringstream string_convert;
      if (arg_ix >= argc) {
        displayUsage(argv[0]);
        std::cerr << "Missing option value for " << opt << std::endl;
        std::exit(EXIT_FAILURE);
      }
      const std::string val = argv[arg_ix++];
      string_convert << val;
      string_convert >> opts.period;
    } else if (opt == "--buffer-size") {
      std::stringstream string_convert;
      if (arg_ix >= argc) {
        displayUsage(argv[0]);
        std::cerr << "Missing option value for " << opt << std::endl;
        std::exit(EXIT_FAILURE);
      }
      const std::string val = argv[arg_ix++];
      string_convert << val;
      string_convert >> opts.alloc_size;
    } else if (opt == "--queue-depth") {
       std::stringstream string_convert;
       if (arg_ix >= argc) {
         displayUsage(argv[0]);
         std::cerr << "Missing option value for " << opt << std::endl;
         std::exit(EXIT_FAILURE);
       }
       const std::string val = argv[arg_ix++];
       string_convert << val;
       string_convert >> opts.queue_capacity;
    } else if (opt == "--report-every") {

      std::stringstream string_convert;
      if (arg_ix >= argc) {
        displayUsage(argv[0]);
        std::cerr << "Missing option value for " << opt << std::endl;
        std::exit(EXIT_FAILURE);
      }
      const std::string val = argv[arg_ix++];
      string_convert << val;
      string_convert >> opts.report_every;
    } else if (opt == "--profile") {

      if (arg_ix >= argc) {
        displayUsage(argv[0]);
        std::cerr << "Missing option value for " << opt << std::endl;
        std::exit(EXIT_FAILURE);
      }
      opts.profile_dump = argv[arg_ix++];

    } else if (opt == "--help") {
      displayUsage(argv[0]);
      std::exit(EXIT_SUCCESS);
    } else {
      displayUsage(argv[0]);
      std::cerr << "Invalid option " << opt << std::endl;
      std::exit(EXIT_FAILURE);
    }
  }

  std::cout << "trace-level: " << opts.trace_level << std::endl;
  std::cout << "clock-period: " << opts.period << " ns" << std::endl;
  return opts;
}

class ReaderWriter {
protected:
  std::ifstream istream;
  const std::string port_name;
  std::size_t index;

public:
  ReaderWriter(std::string port_name)
      : port_name(port_name),
        istream(std::string(port_name + ".txt"), std::ios::in) {
    index = 0;
    if (!istream.is_open()) {
      PANIC("Could not open %s.txt\n", port_name.c_str());
    }
  }
};

template <typename T> class Writer : public ReaderWriter {
private:
  std::size_t offset;
  std::size_t count;
  std::vector<T> shadow_buffer;

public:
  Writer(std::size_t alloc_size, std::string port_name)
      : ReaderWriter(port_name), shadow_buffer(alloc_size) {
    offset = 0;
    count = 0;
    ASSERT(
        sizeof(T) <= sizeof(uint64_t),
        "%s::Unsupported token size %lu, maximum token size is %lu bytes\n",
        this->port_name.c_str(), sizeof(T), sizeof(uint64_t));
  }

  std::size_t fillBuffer(std::vector<T> &host_buffer) {
    ASSERT(host_buffer.size() >= this->shadow_buffer.size(),
           "%s::badly allocated host buffer\n", this->port_name.c_str());
    std::size_t copied = 0;
    for (std::size_t i = this->offset; i < this->count; i++) {
      T token = this->shadow_buffer[i];
      host_buffer[i - this->offset] = token;
      this->shadow_buffer[i - this->offset] = token;
    }

    copied += this->count - this->offset;
    uint64_t token = 0;
    std::size_t read_from_file = 0;
    for (std::size_t i = copied;
         i < host_buffer.size() && (this->istream >> token); i++) {
      this->shadow_buffer[i] = token;
      host_buffer[i] = token;
      read_from_file++;
    }
    this->offset = 0;
    this->count = copied + read_from_file;
    return (this->count);
  }
  void consumeTokens(std::size_t n) {
    ASSERT(this->offset + n <= this->count,
           "%s::invalid consume, offset: %lu, count: %lu, n: %lu\n",
           this->port_name.c_str(), this->offset, this->count, n);
    this->index += n;
    this->offset += n;
    STATUS_REPORT("%s::consumed %lu tokens, total consumed %lu\n",
                  this->port_name.c_str(), n, this->index);
  }
};

template <typename T> class Reader : public ReaderWriter {
public:
  Reader(std::size_t alloc_size, std::string file_name)
      : ReaderWriter(file_name) {
    index = 0;
  }
  void verify(std::vector<T> &host_buffer, std::size_t n) {
    ASSERT(n <= host_buffer.size(), "%s::host buffer smaller than n\n",
           this->port_name.c_str());
    for (std::size_t i = 0; i < n; i++) {
      uint64_t token;
      if (this->istream >> token) {
        ASSERT(T(token) == host_buffer[i],
               "%s::Expected %lu but received %lu (%lu)\n",
               this->port_name.c_str(), token, host_buffer[i], this->index);
        this->index++;
      } else {
        ASSERT(false, "Could not read more tokens from file\n");
      }
    }
    STATUS_REPORT("%s::Verified %lu tokens, total verified so far %lu\n",
                  this->port_name.c_str(), n, this->index);
  }
};

