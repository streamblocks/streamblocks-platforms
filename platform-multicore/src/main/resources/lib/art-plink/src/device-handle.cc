#include "device-handle.h"

namespace ocl_device {

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
/******************************************************************************
 *
 *
 *
 *****************************************************************************/
/*****************************************************************************
 *
 *                                Device Port
 *
 *****************************************************************************/
DevicePort::DevicePort(PortAddress address) : address(address) {

  std::stringstream builder;
  builder << address.getName() << " buffer event";
  buffer_event_info.init(builder.str());

  std::stringstream builder2;
  builder2 << address.getName() << " buffer size event";
  size_event_info.init(builder2.str());

  usable = 0;
  usable_tokens = 0;
}

DevicePort::DevicePort(const cl::Context &context, PortAddress address,
                       cl_mem_flags flags, cl::size_type size)
    : DevicePort(address) {
  allocate(context, flags, size);
}

cl_int DevicePort::allocate(const cl::Context &context, cl_mem_flags flags,
                            cl::size_type size) {
  cl_int err;
  device_buffer = cl::Buffer(context, flags, size, NULL, &err);

  host_buffer.resize(size);

  cl_int err2;
  device_size_buffer = cl::Buffer(context, CL_MEM_WRITE_ONLY, sizeof(uint32_t));

  host_size_buffer.resize(1);

  return err;
}

uint32_t DevicePort::getUsedTokens() {
  if (host_size_buffer.size() == 0)
    OCL_ERR("Host buffer not allocated %s\n", address.toString().c_str());
  return host_size_buffer[0];
}

void DevicePort::writeToDeviceBuffer(const cl::CommandQueue &q) {
  cl_int err;

  OCL_ASSERT(buffer_event_info.active == false, "Illegal write buffer for %s\n",
             address.toString().c_str());
  auto usable_space = getUsableSpace();
  if (usable_space > 0) {
    OCL_MSG("Enqueue write buffer %s (%lu bytes)\n", address.toString().c_str(),
            usable_space);
    OCL_CHECK(err, err = q.enqueueWriteBuffer(device_buffer, false, 0,
                                              usable_space, host_buffer.data(),
                                              NULL, &buffer_event));
    buffer_event_info.active = true;
    buffer_event.setCallback(CL_COMPLETE, callback_handler, &buffer_event_info);
  }
}

void DevicePort::readFromDeviceBuffer(const cl::CommandQueue &q,
                                      const std::vector<cl::Event> *events) {

  OCL_ASSERT(buffer_event_info.active == false, "Illegal read buffer for %s\n",
             address.toString().c_str());
  auto usedSpace = getUsedTokens() * token_size;
  if (usedSpace > 0) {
    cl_int err;
    OCL_MSG("Enqueue read buffer %s (%lu bytes)\n", address.toString().c_str(),
            usedSpace);
    OCL_CHECK(err, err = q.enqueueReadBuffer(device_buffer, CL_FALSE, 0,
                                             usedSpace, host_buffer.data(),
                                             events, &buffer_event));
    buffer_event_info.active = true;
    buffer_event.setCallback(CL_COMPLETE, callback_handler, &buffer_event_info);
  }
}

void DevicePort::equeueReadSize(const cl::CommandQueue &q,
                                const std::vector<cl::Event> *events) {

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

/*****************************************************************************
 *
 *                                Device Handle
 *
 *****************************************************************************/

DeviceHandle::DeviceHandle(int num_inputs, int num_outputs, int num_mems,
                           const std::string kernel_name, const std::string dir)
    : NUM_INPUTS(num_inputs), NUM_OUTPUTS(num_outputs), NUM_MEMS(num_mems),
      request_size(num_inputs), available_size(num_outputs) {

  cl_int error;
  OCL_MSG("Initializing the device\n");

  // get all devices
  std::vector<cl::Device> devices = xcl::get_xil_devices();
  cl::Device device = devices[0];

  // Creating Context and Command Queue for selected Device
  context = cl::Context(device);
  OCL_CHECK(error, command_queue = cl::CommandQueue(
                       context, device, CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE,
                       &error));
  std::string device_name = device.getInfo<CL_DEVICE_NAME>();

  std::string xclbin_name;
  {
    std::stringstream builder;

    builder << dir << "/" << kernel_name;
    auto emu_mode = std::getenv("XCL_EMULATION_MODE");
    if (emu_mode == NULL)
      builder << ".hw";
    else {
      if (strcmp(emu_mode, "hw_emu") == 0)
        builder << ".hw_emu";
      else
        OCL_ERR("Unsupported emulation mode %s\n", emu_mode);
    }

    builder << ".xclbin";

    xclbin_name = builder.str();
  }

  auto bins = xcl::import_binary_file(xclbin_name);
  devices.resize(1);
  OCL_CHECK(error, program = cl::Program(context, devices, bins, NULL, &error));

  // -- creat the kernel
  kernel = cl::Kernel(program, kernel_name.c_str());

  // -- init kernel event
  kernel_event_info.init(std::string("kernel event"));
  kernel_event.emplace_back();
}

void DeviceHandle::allocateInputBuffer(const PortAddress &port,
                                       const cl::size_type size) {

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

void DeviceHandle::allocateOutputBuffer(const PortAddress &port,
                                        const cl::size_type size) {

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

void DeviceHandle::enqueueWriteBuffers() {
  for (auto &input : input_ports) {
    input.writeToDeviceBuffer(command_queue);
  }
}

void DeviceHandle::enqueueReadBuffers() {
  for (auto &output : output_ports) {
    output.readFromDeviceBuffer(command_queue, &kernel_event);
  }
}

void DeviceHandle::enqueueReadSize() {

  for (auto &input : input_ports) {
    input.equeueReadSize(command_queue, &kernel_event);
  }

  for (auto &output : output_ports) {
    output.equeueReadSize(command_queue, &kernel_event);
  }
}

void DeviceHandle::waitForReadBuffers() {

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

void DeviceHandle::waitForSize() {
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

void DeviceHandle::setArgs() {
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

  // -- external memories

  for (auto &mem : external_memories) {
    kernel.setArg(arg_ix++, mem);
  }
}

void DeviceHandle::releaseEvents() {

  for (auto &input : input_ports) {
    input.releaseEvents();
  }

  for (auto &output : output_ports) {
    output.releaseEvents();
  }
  kernel_event_info.active = false;
}

void DeviceHandle::enqueueExecution() {

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

char *DeviceHandle::getInputHostBuffer(const PortAddress &port) {

  for (auto &input : input_ports) {

    if (input.getAddress() == port)
      return input.getHostBufferRaw();
  }
  OCL_ERR("Invalid input port %s\n", port.toString().c_str());
  return nullptr;
}

char *DeviceHandle::getOutputHostBuffer(const PortAddress &port) {

  for (auto &output : output_ports) {

    if (output.getAddress() == port)
      return output.getHostBufferRaw();
  }
  OCL_ERR("Invalid output port %s\n", port.toString().c_str());
  return nullptr;
}

void DeviceHandle::terminate() {}

void DeviceHandle::allocateExternals(std::vector<cl::size_type> size_bytes) {

  OCL_ASSERT(size_bytes.size() == NUM_MEMS,
             "invalid number of external memories\n");

  for (auto &sz : size_bytes) {
    OCL_MSG("Allocating %lu bytes of external memories\n", sz);
    external_memories.emplace_back(context, CL_MEM_READ_WRITE, sz);
  }
}
};