#ifndef __DEVICE_HANDLE_H__
#define __DEVICE_HANDLE_H__

#include <cstdlib>
#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <unistd.h>

#include "xcl2.hpp"
#define OCL_ERROR true

#ifdef OCL_CHECK
#undef OCL_CHEKC
#endif

#include "ocl-macros.h"

namespace ocl_device {

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
                                  void *info) {

  cl_command_type command;
  clGetEventInfo(event, CL_EVENT_COMMAND_TYPE, sizeof(cl_command_type),
                 &command, NULL);
  const char *command_str;
  EventInfo *event_info = (EventInfo *)info;

  switch (command) {
  case CL_COMMAND_NDRANGE_KERNEL:
    command_str = "kernel";
    break;
  case CL_COMMAND_MIGRATE_MEM_OBJECTS:
    command_str = "migrate";
    break;
  case CL_COMMAND_WRITE_BUFFER:
    command_str = "write";
    break;
  case CL_COMMAND_READ_BUFFER:
    command_str = "read";
    break;
  default:
    command_str = "unsupported";
  }
  char callback_msg[2048];

  std::string msg;
  if (OCL_VERBOSE) {
    std::stringstream msg_builder;
    msg_builder << "Completed " << command_str << " (" << event_info->counter
                << "): " << event_info->message << std::endl;
    msg = msg_builder.str();
  }
  event_info->counter++;
  OCL_MSG("%s", msg.c_str());
  fflush(stdout);
}
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
  DevicePort(PortAddress address) : address(address) {

    std::stringstream builder;
    builder << address.getName() << " buffer event";
    buffer_event_info.init(builder.str());

    std::stringstream builder2;
    builder2 << address.getName() << " buffer size event";
    size_event_info.init(builder2.str());

    usable = 0;
    usable_tokens = 0;
  };

  cl_int allocate(const cl::Context &context, cl_mem_flags flags,
                  cl::size_type size) {
    cl_int err;
    device_buffer = cl::Buffer(context, flags, size, NULL, &err);

    host_buffer.resize(size);

    cl_int err2;
    device_size_buffer =
        cl::Buffer(context, CL_MEM_WRITE_ONLY, sizeof(uint32_t));

    host_size_buffer.resize(1);

    return err;
  }

  /**
   * Set the usable space in bytes
   */
  template <typename T> void setUsableSpace(const cl::size_type usable_size) {

    OCL_ASSERT(usable_size % sizeof(T) == 0,
               "Usable space %lu is not aligned to tokens size %d\n",
               usable_size, sizeof(T));

    if (usable_size > host_buffer.size()) {
      OCL_ERR("usable_size (%llu) should be <= capacity (%llu) in %s\n",
              usable_size, host_buffer.size(), address.toString().c_str());
    } else {
      usable = usable_size;
      usable_tokens = usable_size / sizeof(T);
    }
  }

  uint32_t getUsedTokens() {
    if (host_size_buffer.size() == 0)
      OCL_ERR("Host buffer not allocated %s\n", address.toString().c_str());
    return host_size_buffer[0];
  }

  cl::size_type &getUsableSpace() { return usable; }
  uint32_t getUsableTokenSpace() { return usable_tokens; }
  PortAddress &getAddress() { return address; }

  std::string toString() { return address.toString(); }

  /**
   * Get the host buffer
   */

  char *getHostBufferRaw() { return host_buffer.data(); }

  std::vector<char, aligned_allocator<char>> &getHostBuffer() {
    return host_buffer;
  }

  void writeToDeviceBuffer(const cl::CommandQueue &q) {
    cl_int err;

    OCL_ASSERT(buffer_event_info.active == false,
               "Illegal write buffer for %s\n", address.toString().c_str());
    if (getUsableSpace() > 0) {
      OCL_MSG("Enqueue write buffer %s\n", address.toString().c_str());
      OCL_CHECK(err, err = q.enqueueWriteBuffer(device_buffer, false, 0, usable,
                                                host_buffer.data(), NULL,
                                                &buffer_event));
      buffer_event_info.active = true;
      buffer_event.setCallback(CL_COMPLETE, callback_handler,
                               &buffer_event_info);
    }
  }

  void readFromDeviceBuffer(const cl::CommandQueue &q,
                            const std::vector<cl::Event> *events = NULL) {

    OCL_ASSERT(buffer_event_info.active == false,
               "Illegal read buffer for %s\n", address.toString().c_str());
    auto usedSpace = getUsedTokens();
    if (usedSpace > 0) {
      cl_int err;
      OCL_MSG("Enqeeue read buffer %s\n", address.toString().c_str());
      OCL_CHECK(err, err = q.enqueueReadBuffer(device_buffer, CL_FALSE, 0,
                                               usedSpace, host_buffer.data(),
                                               events, &buffer_event));
      buffer_event_info.active = true;
      buffer_event.setCallback(CL_COMPLETE, callback_handler,
                               &buffer_event_info);
    }
  }

  void equeueReadSize(const cl::CommandQueue &q,
                      const std::vector<cl::Event> *events = NULL) {

    cl_int err;
    OCL_ASSERT(size_event_info.active == false, "Illegal read size for %s\n",
               address.toString().c_str());
    OCL_MSG("Enqueue read size %s\n", address.toString().c_str());
    OCL_CHECK(err, err = q.enqueueReadBuffer(
                       device_size_buffer,      // device buffer object
                       CL_FALSE,                // blocking = false
                       0,                       // offset, unsed
                       sizeof(uint32_t),        // size of read transfer
                       host_size_buffer.data(), // pointer to host memory
                       events,                  // events to wait on
                       &buffer_size_event       // generated event
                       ));
    size_event_info.active = true;
    buffer_size_event.setCallback(CL_COMPLETE, callback_handler,
                                  &size_event_info);
  }

  EventInfo getBufferEventInfo() { return buffer_event_info; }

  EventInfo getSizeEventInfo() { return size_event_info; }

  cl::Event &getBufferEvent() { return buffer_event; }

  cl::Event &getSizeEvent() { return buffer_size_event; }

  cl::Buffer &getBufferRef() { return device_buffer; }
  cl::Buffer &getSizeRef() { return device_size_buffer; }

  void releaseEvents() {
    buffer_event_info.active = false;
    size_event_info.active = false;
  }

private:
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
};

class DeviceMemory {

public:
  DeviceMemory();
  cl_int allocate(size_t size);

private:
  cl::Buffer device_buffer;
};

template <int NUM_INPUTS, int NUM_OUTPUTS, int NUM_MEMS> class DeviceHandle {
public:
  DeviceHandle(const std::string kernel_name, const std::string dir,
               const std::string target_device_name = "") {

    cl_int error;
    OCL_MSG("Initializing the device\n");

    // get all devices
    std::vector<cl::Device> devices = xcl::get_xil_devices();
    cl::Device device = devices[0];

    // Creating Context and Command Queue for selected Device
    context = cl::Context(device);
    OCL_CHECK(error, command_queue = cl::CommandQueue(
                         context, device,
                         CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE, &error));
    std::string device_name = device.getInfo<CL_DEVICE_NAME>();

    std::string xclbin_name;
    {
      std::stringstream builder;

      builder << dir << "/" << kernel_name;
      auto emu_mode = std::getenv("XCL_EMULATION_MODE");
      if (emu_mode == NULL)
        builder << ".hw.";
      else {
        if (strcmp(emu_mode, "hw_emu") == 0)
          builder << ".hw_emu.";
        else
          OCL_ERR("Unsupported emulation mode %s\n", emu_mode);
      }

      builder << target_device_name << ".xclbin";

      xclbin_name = builder.str();
    }

    auto bins = xcl::import_binary_file(xclbin_name);
    devices.resize(1);
    OCL_CHECK(error,
              program = cl::Program(context, devices, bins, NULL, &error));

    // -- creat the kernel
    kernel = cl::Kernel(program, kernel_name.c_str());

    // -- init kernel event
    kernel_event_info.init(std::string("kernel event"));
    kernel_event.emplace_back();
  }

  void buildPorts(const std::vector<PortAddress> &inputs,
                  const std::vector<PortAddress> &outputs) {

    OCL_ASSERT(inputs.size() == NUM_INPUTS, "Invalid number of input ports!");
    OCL_ASSERT(outputs.size() == NUM_INPUTS, "Invalid number of output ports!");
    for (auto &input : inputs) {
      OCL_MSG("Constructing input port %s\n", input.toString().c_str());
      input_ports.emplace_back(input);
    }
    for (auto &output : outputs) {
      OCL_MSG("Constructing output port %s\n", output.toString().c_str());
      output_ports.emplace_back(output);
    }
  }

  /**
   * allocate input buffer
   */
  void allocateInputBuffer(const PortAddress &port, const cl::size_type size) {

    for (auto &input : input_ports)
      if (port == input.getAddress()) {
        cl_int err;
        OCL_MSG("Allocating %s port buffer (%llu bytes) \n",
                port.toString().c_str(), size);
        OCL_CHECK(err, input.allocate(context, CL_MEM_READ_ONLY, size));
        return;
      }
    OCL_ERR("Invalid input port %s\n", port.toString());
  }

  /**
   * allocate output buffers
   */
  void allocateOutputBuffer(const PortAddress &port, const cl::size_type size) {

    for (auto &output : output_ports) {
      if (port == output.getAddress()) {
        cl_int err;
        OCL_MSG("Allocating %s port buffer (%llu bytes)\n",
                port.toString().c_str(), size);
        OCL_CHECK(err, output.allocate(context, CL_MEM_WRITE_ONLY, size));
        return;
      }
    }
    OCL_ERR("Invalid output port %s\n", port.toString());
  }

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

  void enqueueWriteBuffers() {
    for (auto &input : input_ports) {
      input.writeToDeviceBuffer(command_queue);
    }
  }

  void enqueueReadBuffers() {
    for (auto &output : output_ports) {
      output.readFromDeviceBuffer(command_queue, &kernel_event);
    }
  }

  void enqueueReadSize() {

    for (auto &input : input_ports) {
      input.equeueReadSize(command_queue, &kernel_event);
    }

    for (auto &output : output_ports) {
      output.equeueReadSize(command_queue, &kernel_event);
    }
  }

  void waitForReadBuffers() {

    std::vector<cl::Event> activeEvents;
    for (auto &output : output_ports) {
      if (output.getBufferEventInfo().active)
        activeEvents.push_back(output.getBufferEvent());
    }
    if (activeEvents.size() > 0) {
      OCL_MSG("Waiting for %d active read events\n", activeEvents.size());
      cl::WaitForEvents(activeEvents);
    } else {
      OCL_MSG("No active read events\n");
    }
  }

  void waitForSize() {
    std::vector<cl::Event> activeEvents;
    for (auto &input : input_ports) {
      if (input.getSizeEventInfo().active)
        activeEvents.push_back(input.getSizeEvent());
    }
    for (auto &output : output_ports) {
      if (output.getSizeEventInfo().active)
        activeEvents.push_back(output.getSizeEvent());
    }
    if (activeEvents.size() > 0) {
      OCL_MSG("Waiting on %d size events\n", activeEvents.size());
      cl::WaitForEvents(activeEvents);
    }
  }

  void setArgs() {
    cl_uint arg_ix = 0;

    cl_uint input_ix = 0;
    for (auto &input : input_ports) {

      request_size[input_ix] = input.getUsableTokenSpace();
      OCL_MSG("%s request size = %d\n", input.toString().c_str(),
              request_size[input_ix]);
      kernel.setArg(arg_ix++, request_size[input_ix++]);
    }

    cl_uint output_ix = 0;
    for (auto &output : output_ports) {
      available_size[output_ix] = output.getUsableTokenSpace();
      OCL_MSG("%s available size = %d\n", output.toString().c_str(),
              available_size[output_ix]);
      kernel.setArg(arg_ix++, available_size[output_ix++]);
    }

    for (auto &input : input_ports) {
      kernel.setArg(arg_ix++, input.getSizeRef());
      kernel.setArg(arg_ix++, input.getBufferRef());
    }

    for (auto &output : output_ports) {
      kernel.setArg(arg_ix++, output.getSizeRef());
      kernel.setArg(arg_ix++, output.getBufferRef());
    }

    kernel.setArg(arg_ix++, kernel_command);
  }

  void releaseEvents() {

    for (auto &input : input_ports) {
      input.releaseEvents();
    }

    for (auto &output : output_ports) {
      output.releaseEvents();
    }
    kernel_event_info.active = false;
  }

  void enqueueExecution() {

    cl_int err;
    std::vector<cl::Event> activeEvents;
    for (auto &input : input_ports) {
      if (input.getBufferEventInfo().active)
        activeEvents.push_back(input.getBufferEvent());
    }

    kernel_wait_events = activeEvents;
    OCL_MSG("Enqueuing kernel\n");
    OCL_CHECK(err, err = command_queue.enqueueTask(
                       kernel,              // the kernel object
                       &kernel_wait_events, // events to wait on
                       &kernel_event[0]     // generated event
                       ));
  }

  char *getInputHostBuffer(const PortAddress &port) {

    for (auto &input : input_ports) {

      if (input.getAddress() == port)
        return input.getHostBufferRaw();
    }
    OCL_ERR("Invalid input port %s\n", port.toString().c_str());
    return nullptr;
  }

  char *getOutputHostBuffer(const PortAddress &port) {

    for (auto &output : output_ports) {

      if (output.getAddress() == port)
        return output.getHostBufferRaw();
    }
    OCL_ERR("Invalid output port %s\n", port.toString().c_str());
    return nullptr;
  }

  void termiate() {}

private:
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

  uint32_t request_size[NUM_INPUTS];
  uint32_t available_size[NUM_OUTPUTS];
};
};

#endif