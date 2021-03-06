#ifndef __DEVICE_HANDLE_H__
#define __DEVICE_HANDLE_H__

#include <cstdlib>
#include <fstream>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <unistd.h>

#include "xcl2.h"
// #include <CL/cl_ext.h>
#include <CL/cl_ext_xilinx.h>
#include "ocl-macros.h"

namespace ocl_device {

struct TimePoints{
  cl_ulong queued;
  cl_ulong submit;
  cl_ulong start;
  cl_ulong end;
  TimePoints(cl::Event& event) {
    event.getProfilingInfo<cl_ulong>(CL_PROFILING_COMMAND_QUEUED, &queued);
    event.getProfilingInfo<cl_ulong>(CL_PROFILING_COMMAND_SUBMIT, &submit);
    event.getProfilingInfo<cl_ulong>(CL_PROFILING_COMMAND_START, &start);
    event.getProfilingInfo<cl_ulong>(CL_PROFILING_COMMAND_END, &end);
  }
};


struct EventInfo {
  std::size_t counter;
  bool active;
  std::string message;
  void init(std::string msg) {
    active = false;
    counter = 0;
    message = msg;
  }
};
void CL_CALLBACK callback_handler(cl_event event, cl_int cmd_status,
                                  void *info);
class PortAddress {
public:
  PortAddress(std::string port_name) : name(port_name){};
  std::string getName() const { return name; }
  std::string toString() const { return getName(); }
  friend bool operator==(const PortAddress &a1, const PortAddress &a2) {
    return a1.name == a2.name;
  }

private:
  std::string name;
};
class DevicePort {

public:
  DevicePort(PortAddress address);
  DevicePort(const cl::Context &context, PortAddress address,
             cl_mem_flags flags, cl::size_type size, cl_int bank_id);
  cl_int allocate(const cl::Context &context, cl_mem_flags flags,
                  cl::size_type size, cl_int bank_id);

  /**
   * Set the usable space in bytes
   */
  template <typename T> void setUsableSpace(const cl::size_type usable_size) {

    OCL_ASSERT(usable_size % sizeof(T) == 0,
               "Usable space %lu is not aligned to tokens size %d\n",
               usable_size, sizeof(T));
    token_size = sizeof(T);
    if (usable_size > host_buffer.size()) {
      OCL_ERR("usable_size (%llu) should be <= capacity (%llu) in %s\n",
              usable_size, host_buffer.size(), address.toString().c_str());
    } else {
      usable = usable_size;
      usable_tokens = usable_size / sizeof(T);
    }
  }

  uint32_t getUsedTokens();

  cl::size_type &getUsableSpace() { return usable; }
  uint32_t getUsableTokenSpace() { return usable_tokens; }
  PortAddress &getAddress() { return address; }

  std::string toString() { return address.toString(); }

  /**
   * Get the host buffer
   */

  char *getHostBufferRaw() { return host_buffer.data(); }

  void writeToDeviceBuffer(const cl::CommandQueue &q);

  void readFromDeviceBuffer(const cl::CommandQueue &q,
                            const std::vector<cl::Event> *events = NULL);

  void equeueReadSize(const cl::CommandQueue &q,
                      const std::vector<cl::Event> *events = NULL);

  EventInfo getBufferEventInfo() { return buffer_event_info; }

  EventInfo getSizeEventInfo() { return size_event_info; }

  cl::Event &getBufferEvent() { return buffer_event; }

  cl::Event &getSizeEvent() { return buffer_size_event; }

  cl::Buffer &getBufferRef() { return device_buffer; }
  cl::Buffer &getSizeRef() { return device_size_buffer; }

  void updateConsumption();

  void report();

  void releaseEvents() {
    buffer_event_info.active = false;
    size_event_info.active = false;
  }

  void registerStat(const std::size_t call_index);

  std::string serializeStats(const int indent);



private:
  struct Stats{

    const std::size_t bytes_usable;
    const std::size_t bytes_used;
    const TimePoints time_log;
    const std::size_t index;
    Stats(const std::size_t index,
          const std::size_t bytes_usable,
          const std::size_t bytes_used, cl::Event& event):
          bytes_usable(bytes_usable),
          bytes_used(bytes_used), index(index),
          time_log(event) {
    }

    std::string serialize(const int indent) {
      std::stringstream ss;
      std::string indent_str = "";
      for (int i = 0; i < indent; i++)
        indent_str = indent_str + "\t";
      ss << indent_str << "{" << std::endl;
      {
        auto inner_indent = indent_str + "\t";
        ss << inner_indent << "\"call_index\":" << index << "," << std::endl;
        ss << inner_indent << "\"queue\":" << time_log.queued << "," << std::endl;
        ss << inner_indent << "\"submit\":" << time_log.submit << "," << std::endl;
        ss << inner_indent << "\"start\":"  << time_log.start << "," << std::endl;
        ss << inner_indent << "\"end\":" << time_log.end << "," << std::endl;
        ss << inner_indent << "\"bytes_usable\":" << bytes_usable << "," << std::endl;
        ss << inner_indent << "\"bytes_used\":" << bytes_used << "" << std::endl;
      }
      ss << indent_str << "}";
      return ss.str();
    }
  };
  // -- token size
  cl::size_type token_size;
  // -- usable space of the port buffer
  cl::size_type usable;
  // -- number of usable tokens, depends on type of the port
  uint32_t usable_tokens;
  // -- address of the port
  PortAddress address;
  // -- device memory buffer
  cl::Buffer device_buffer;
  // -- host memory buffer
  std::vector<char, aligned_allocator<char>> host_buffer;
  // -- device size buffer
  cl::Buffer device_size_buffer;
  // -- host size buffer
  std::vector<uint32_t, aligned_allocator<uint32_t>> host_size_buffer;
  // -- buffer read/write event;
  cl::Event buffer_event;
  // -- buffer size read event;
  cl::Event buffer_size_event;
  EventInfo buffer_event_info;
  EventInfo size_event_info;

  cl_mem_ext_ptr_t extensions;


  std::vector<Stats> stats;


};

struct PLinkPort {

  PLinkPort(std::string port_name, uint32_t capacity)
      : port(port_name), capacity(capacity) {
    usable = capacity;
    used = 0;
  };
  PortAddress port;
  uint32_t usable;
  uint32_t used;
  uint32_t capacity;
};

class DeviceMemory {

public:
  DeviceMemory();
  cl_int allocate(const cl::Context &context, cl_mem_flags flags,
                  cl::size_type size);

private:
  cl::Buffer device_buffer;
};

class DeviceHandle {
public:
  DeviceHandle(int num_inputs, int num_outputs, int num_mems,
               const std::string kernel_name, const std::string dir, const bool enable_stats = false);

  void buildPorts(const std::vector<PLinkPort> &inputs,
                  const std::vector<PLinkPort> &outputs) {
    OCL_ASSERT(inputs.size() == NUM_INPUTS, "Invalid number of input ports!\n");
    OCL_ASSERT(outputs.size() == NUM_OUTPUTS, "Invalid number of output ports!\n");

    cl_int banks[4] = {XCL_MEM_DDR_BANK0, XCL_MEM_DDR_BANK1, XCL_MEM_DDR_BANK2, XCL_MEM_DDR_BANK3};

    int bank_index = 0;
    for (auto &input : inputs) {
      OCL_MSG("constructing input port %s (%llu bytes)\n",
              input.port.toString().c_str(), input.capacity);
      input_ports.emplace_back(context, input.port, CL_MEM_READ_ONLY,
                               input.capacity, banks[bank_index]);
      bank_index = (bank_index + 1) % 4;
    }
    for (auto &output : outputs) {
      OCL_MSG("constructing output port %s (%llu bytes)\n",
              output.port.toString().c_str(), output.capacity);
      output_ports.emplace_back(context, output.port, CL_MEM_WRITE_ONLY,
                                output.capacity, banks[bank_index]);
      bank_index = (bank_index + 1) % 4;
    }
  }
  void buildPorts(const std::vector<PortAddress> &inputs,
                  const std::vector<PortAddress> &outputs) {

    OCL_ASSERT(inputs.size() == NUM_INPUTS, "Invalid number of input ports!\n");
    OCL_ASSERT(outputs.size() == NUM_OUTPUTS, "Invalid number of output ports!\n");
    for (auto &input : inputs) {
      OCL_MSG("Constructing input port %s\n", input.toString().c_str());
      input_ports.emplace_back(input);
    }
    for (auto &output : outputs) {
      OCL_MSG("Constructing output port %s\n", output.toString().c_str());
      output_ports.emplace_back(output);
    }
  }

  // /**
  //  * allocate input buffer
  //  */
  // void allocateInputBuffer(const PortAddress &port, const cl::size_type size);
  // /**
  //  * allocate output buffers
  //  */
  // void allocateOutputBuffer(const PortAddress &port, const cl::size_type size);
  /**
   * set the usable size of a port buffer
   */
  template <typename T>
  void setUsableInput(const PortAddress &port, const cl::size_type num_tokens) {

    for (auto &input : input_ports) {
      if (port == input.getAddress()) {
        OCL_MSG("Usable token count for %s set to %llu tokens\n",
                port.toString().c_str(), num_tokens);
        input.setUsableSpace<T>(num_tokens * sizeof(T));
        return;
      }
    }
    OCL_ERR("Invalid input port %s\n", port.toString());
  }

  template <typename T>
  void setUsableOutput(const PortAddress &port,
                       const cl::size_type num_tokens) {
    for (auto &output : output_ports) {
      if (port == output.getAddress()) {
        OCL_MSG("Usable token count for %s set to %llu tokens\n",
                port.toString().c_str(), num_tokens);
        output.setUsableSpace<T>(num_tokens * sizeof(T));
        return;
      }
    }
    OCL_ERR("Invalid ouput port %s\n", port.toString());
  }

  /**
   * get the used space
   */
  template <typename T> uint32_t getUsedInput(const PortAddress &port) {

    uint32_t used = 0;
    for (auto &input : input_ports) {
      if (port == input.getAddress()) {
        used = input.getUsedTokens();
        return used;
      }
    }

    OCL_ERR("Invalid input port %s\n", port.toString());
    return used;
  }

  template <typename T> uint32_t getUsedOutput(const PortAddress &port) {

    uint32_t used = 0;
    for (auto &output : output_ports) {
      if (port == output.getAddress()) {

        used = output.getUsedTokens();
        return used;
      }
    }
    OCL_ERR("Invalid output port %s\n", port.toString());
    return used;
  }

  void enqueueWriteBuffers();
  void enqueueReadBuffers();
  void enqueueReadSize();

  void waitForReadBuffers();

  void waitForSize();

  void setArgs();

  void releaseEvents();

  void enqueueExecution();

  char *getInputHostBuffer(const PortAddress &port);

  char *getOutputHostBuffer(const PortAddress &port);

  void terminate();

  void allocateExternals(std::vector<cl::size_type> size_bytes);

  void dumpStats(const std::string& file_name);

  void allocateInputBuffer(const PortAddress &port,
                                       const cl::size_type size,
                                       const cl_int bank_id);
  void allocateOutputBuffer(const PortAddress &port,
                                       const cl::size_type size,
                                       const cl_int bank_id);

private:
  const int NUM_INPUTS;
  const int NUM_OUTPUTS;
  const int NUM_MEMS;
  cl::Context context;
  cl::Platform platform;
  cl::Device device;
  cl::CommandQueue command_queue;
  cl::Program program;

  cl::Kernel kernel;

  std::vector<cl::Event> kernel_event;
  EventInfo kernel_event_info;

  std::vector<cl::Event> kernel_wait_events;

  const cl_int global = 1;
  const cl_int local = 1;

  std::vector<DevicePort> input_ports;
  std::vector<DevicePort> output_ports;

  cl_ulong kernel_command; // unused

  std::vector<uint32_t> request_size;
  std::vector<uint32_t> available_size;

  std::vector<cl::Buffer> external_memories;

  const bool enable_stats;

  std::size_t call_index;

  // struct KernelStat {
  //   const std::size_t index;
  //   const TimePoints time_log;
  //   KernelStat()
  // };
  // std::vector<TimePoints> kernel_stats;
};
};

#endif