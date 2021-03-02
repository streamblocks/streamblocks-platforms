#ifndef __SIMULATE_H__
#define __SIMULATE_H__
#include "simulation-kernel.h"
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
  std::cout << "\t\t--queue-depth       the internal fifo capacity, does not "
            << std::endl;
  std::cout << "\t\t                    affect input or output stage fifos and "
            << std::endl;
  std::cout << "\t\t                    does not override the 'bufferSize' "
            << std::endl;
  std::cout << "\t\t                    attribute from the CAL source files"
            << std::endl;
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
  opts.queue_capacity = 4096;
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

/**
 * @brief Simulation buffer object that represents both the device and host
 * buffer
 *
 * @tparam T type of tokens in the buffer
 */
template <typename T> struct SimulationBuffer {
  T *const data_buffer;
  T *const meta_buffer;
  const uint32_t alloc_size;
  uint32_t head;
  uint32_t tail;

  /**
   * @brief Construct a new Simulation Buffer object
   *
   * @param alloc_size size of the buffer in tokens
   */
  explicit SimulationBuffer(const uint32_t alloc_size)
      : data_buffer(new T[alloc_size]),
        meta_buffer(new T[sizeof(T) > sizeof(uint32_t) ? 1 : sizeof(uint32_t)]),
        alloc_size(alloc_size), head(0), tail(0) {}
  ~SimulationBuffer() {
    delete[] data_buffer;
    delete[] meta_buffer;
  }

  ap_rtl::Argument asArgument() {
    return ap_rtl::Argument(
        reinterpret_cast<ap_rtl::Argument::sim_ptr_t>(data_buffer),
        reinterpret_cast<ap_rtl::Argument::sim_ptr_t>(meta_buffer), alloc_size,
        head, tail);
  }

  uint32_t freeSpace() {
    if (head == tail) {
      return alloc_size - 1;
    } else if (head > tail) {
      return alloc_size - 1 + head + tail;
    } else {
      return tail - 1 - head;
    }
  }

  void writeToken(const T token) {

    ASSERT(freeSpace() > 0,
           "Attempted to write to a full simulation buffer!\n");
    data_buffer[head] = token;
    head = (head + 1) % alloc_size;
  }

  T readToken() {
    ASSERT(freeSpace() < alloc_size - 1,
           "Attempted to read from an empty simulation buffer!\n");
    T token = data_buffer[tail];
    tail = (tail + 1) % alloc_size;
    return token;
  }
};
uint32_t distance(const uint32_t start, const uint32_t end,
                  const uint32_t buffer_size) {
  if (start < end) {
    return end - start;
  } else {
    return buffer_size - start + end;
  }
}
/**
 * @brief abstract class for a input/output writer/reader object
 *
 */
template<typename T>
class ReaderWriter {
protected:
  std::ifstream istream;
  const std::string port_name;
  std::size_t index;

public:
  /**
   * @brief Construct a new Reader Writer object
   *
   * @param port_name a human readable name (for debug)
   */
  ReaderWriter(std::string port_name)
      : port_name(port_name),
        istream(std::string(port_name + ".txt"), std::ios::in) {
    index = 0;
    if (!istream.is_open()) {
      PANIC("Could not open %s.txt\n", port_name.c_str());
    }
  }
  virtual uint32_t update(SimulationBuffer<T> *host_buffer) = 0;
  ~ReaderWriter() {
    uint64_t token;
    if (!(istream >> token)) {
      WARNING("There are unused tokens on port %s", port_name.c_str());
    }
  }
};

/**
 * @brief Simulation writer class, writes tokens from file to the kernel
 *
 * @tparam T token type
 */
template <typename T> class Writer : public ReaderWriter<T> {
public:
  /**
   * @brief Construct a new Writer object
   *
   * @param alloc_size size of the host/device buffer in tokens
   * @param port_name name of the ports (for debug)
   */
  Writer(std::size_t alloc_size, std::string port_name)
      : ReaderWriter<T>(port_name) {

    ASSERT(sizeof(T) <= sizeof(uint64_t),
           "%s::Unsupported token size %lu, maximum token size is %lu bytes\n",
           this->port_name.c_str(), sizeof(T), sizeof(uint64_t));
  }

  /**
   * @brief fills up a host buffer with content from file
   *
   * @param host_buffer host buffer
   * @return uint32_t number of tokens read from file
   */
  uint32_t update(SimulationBuffer<T> *host_buffer) override {

    const auto start_ix = this->index;

    auto free_space = host_buffer->freeSpace();
    auto head = host_buffer->head;
    uint64_t token;
    STATUS_REPORT("Updating input stream %s", this->port_name.c_str());
    uint32_t count = 0;
    while (this->istream >> token && free_space > 0) {
      host_buffer->writeToken(token);
      free_space = host_buffer->freeSpace();

      this->index++;
    }
    return this->index - start_ix;
  }
};

/**
 * @brief
 *
 * @tparam T
 */
template <typename T> class Reader : public ReaderWriter<T> {
public:
  Reader(std::size_t alloc_size, std::string file_name)
      : ReaderWriter<T>(file_name) {

  }
  /**
   * @brief verifies and consumes the tokens in the host buffer
   *
   * @param host_buffer
   * @return uint32_t number of tokens verified
   */
  uint32_t update(SimulationBuffer<T> *host_buffer) override {

    // -- verify tokens from the head to the tail, essentilly free up the buffer
    const auto start_ix = this->index;

    while (host_buffer->head != host_buffer->tail) {
      uint64_t expected_token = 0;
      if (this->istream >> expected_token) {
        auto token = host_buffer->readToken();
        ASSERT(T(expected_token) == token,
               "%s::Expected %lu but received %lu (%lu)\n",
               this->port_name.c_str(), expected_token, token, this->index);
        this->index++;
      } else {
        PANIC("Could not verify all tokens on %s! Simulation has produced more "
              "tokens than available in the fifo trace.",
              this->port_name.c_str());
      }
    }
    return this->index - start_ix;
  }
};

#endif // __SIMULATE_H__
