#include "plink.h"
#include "xcl2.h"
namespace ocl_device {
PLink::PLink(const std::vector<PortInfo<LocalInputPort>> &input_info,
             const std::vector<PortInfo<LocalOutputPort>> &output_info,
             const uint32_t num_mems, const std::string kernel_name,
             const std::string dir, const bool enable_stats) {
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

  OCL_MSG("plink::Building %lu input ports\n", input_info.size());
  for (auto &inpt : input_info) {
    inputs.emplace_back(
        inpt.name,
        ocl_device::PortType(ocl_device::IOType::INPUT, inpt.token_size));
  }
  OCL_MSG("plink::Building %lu output ports\n", output_info.size());
  for (auto &otpt : output_info) {
    outputs.emplace_back(
        otpt.name,
        ocl_device::PortType(ocl_device::IOType::OUTPUT, otpt.token_size));
  }

  OCL_MSG("plink::Constructed with xclbin %s\n", xclbin_name.c_str());

  call_index = 0;
}

void PLink::allocateOutput(const ocl_device::PortAddress &name,
                           const cl::size_type size) {
  int bank_ix = 0;
  for (auto &output : outputs) {
    if (output.hw.getAddress() == name) {
      const cl_int bank_id = banks[bank_ix];
      output.hw.allocate(context, size, bank_id);
      break;
    }
    bank_ix = (bank_ix + 1) % 4;
  }
}

void PLink::allocateInput(const ocl_device::PortAddress &name,
                          const cl::size_type size) {
  int bank_ix = 0;
  for (auto &input : inputs) {
    if (input.hw.getAddress() == name) {
      const cl_int bank_id = banks[bank_ix];
      input.hw.allocate(context, size, bank_id);
      break;
    }
    bank_ix = (bank_ix + 1) % 4;
  }
}

void PLink::pinWriteRepeat(OutputPort &port, char *buff, int n) {
  OCL_ASSERT(pinAvailOut(port.sw) >= n, "Invalid software write in port %s\n",
             port.hw.getAddress().toString().c_str());

  port.sw->available -= n;

  int token_size = port.hw.getTokenSize();

  if (port.sw->pos + n >= 0) {
    auto dest =
        reinterpret_cast<char *>(port.sw->buffer) + (port.sw->pos * token_size);

    std::memcpy(dest, buff, -(port.sw->pos * token_size));

    buff += -(port.sw->pos * token_size);
    n -= (port.sw->pos);
    port.sw->pos = -(port.sw->capacity);
  }
  if (n) {
    auto dest =
        reinterpret_cast<char *>(port.sw->buffer) + port.sw->pos * token_size;
    std::memcpy(dest, buff, n * token_size);
    port.sw->pos += n;
  }
}

void PLink::pinReadRepeat(InputPort &port, char *buff, int n) {
  OCL_ASSERT(pinAvailIn(port.sw) >= n, "Invalid software read in port %s\n",
             port.hw.getAddress().toString().c_str());
  port.sw->available -= n;

  int token_size = port.hw.getTokenSize();
  char *source =
      reinterpret_cast<char *>(port.sw->buffer) + port.sw->pos * token_size;

  if (port.sw->pos + n >= 0) {
    char *source =
        reinterpret_cast<char *>(port.sw->buffer) + port.sw->pos * token_size;
    std::memcpy(buff, source, -(port.sw->pos * token_size));
    buff += -(port.sw->pos * token_size);
    n -= -(port.sw->pos);
    port.sw->pos = -(port.sw->capacity);
  }
  if (n) {
    char *source =
        reinterpret_cast<char *>(port.sw->buffer) + port.sw->pos * token_size;
    std::memcpy(buff, source, n * token_size);
    port.sw->pos += n;
  }
}

void PLink::actionFreeUpOutputBuffer() {

  // -- make sure that head and tails are consistent, i.e., no operation is in
  // flight

  for (auto &output : outputs) {
    OCL_ASSERT(output.hw.device_buffer.head == output.hw.host_buffer.head,
               "Incosistent head in port %s\n",
               output.hw.address.toString().c_str());
    OCL_ASSERT(output.hw.device_buffer.tail == output.hw.host_buffer.tail,
               "Inconsisten tail in port %s\n",
               output.hw.address.toString().c_str());

    auto available = pinAvailOut(output.sw);

    auto unwritten_tokens = 0;
    int alloc_cap = output.hw.device_buffer.user_alloc_size;
    int tail = output.hw.host_buffer.tail;
    int head = output.hw.host_buffer.head;
    if (tail == head) {
      unwritten_tokens = 0;
    } else if (tail < head) {
      unwritten_tokens = head - tail;
    } else { // tail > head
      unwritten_tokens = alloc_cap - tail + head;
    }

    int to_write =
        (unwritten_tokens < available) ? unwritten_tokens : available;

    if (to_write > 0) {
      // -- starting from host_buffer[tail], write to_write tokens to the ouptut

      if (to_write + tail >= alloc_cap) {
        // should wrap around
        char *buff = output.hw.getHostBufferAtTail();
        pinWriteRepeat(output, buff, alloc_cap - tail);

        to_write -= (alloc_cap - tail);
        output.hw.host_buffer.tail = 0;
      }

      if (to_write > 0) {
        char *buff = output.hw.getHostBufferAtTail();
        pinWriteRepeat(output, buff, to_write);

        output.hw.host_buffer.tail += to_write;
      }
    }
    output.hw.device_buffer.tail = output.hw.host_buffer.tail;

    if (output.hw.host_buffer.tail == output.hw.host_buffer.head)
      OCL_MSG("plink::%s::freed\n", output.hw.address.toString().c_str());
  }
}

void PLink::actionUpdateIndices() {

  for (auto &input : inputs) {

    int new_tail = input.hw.host_buffer.meta_buffer[0];
    int old_tail = input.hw.host_buffer.tail;
    int alloc_cap = input.hw.device_buffer.user_alloc_size;

    // whatever is between the old tail and the new tail is what has been
    // consumed by the hardware
    int consumed = 0;
    if (old_tail < new_tail) {
      consumed = new_tail - old_tail;
    } else if (new_tail < old_tail) {
      // hardware has wrapped around
      consumed = alloc_cap - old_tail + new_tail;
    }
    OCL_MSG("plink::%s::consumed %u tokens\n",
            input.hw.address.toString().c_str(), consumed);

    int new_head = input.hw.host_buffer.head;
    input.hw.device_buffer.tail = new_tail;
    input.hw.host_buffer.tail = new_tail;
    input.hw.device_buffer.head = new_head;
  }

  for (auto &output : outputs) {

    int new_head = output.hw.host_buffer.meta_buffer[0];
    int old_head = output.hw.device_buffer.head;

    // whatever is between the new_head and the old_head is what has been
    // produced by the hardware.
    int alloc_cap = output.hw.device_buffer.user_alloc_size;

    int produced = 0;

    if (old_head < new_head) {
      produced = new_head - old_head;
    } else if (old_head > new_head) {
      // hardware has wrapped around
      produced = alloc_cap - old_head + new_head;
    }
    OCL_MSG("%s produced %u tokens\n", output.hw.address.toString().c_str(),
            produced);

    int new_tail = output.hw.device_buffer.tail;

    // -- enqueue read back from hardware

    output.hw.device_buffer.head = new_head;
    output.hw.readFromDeviceBuffer(command_queue);
    output.hw.host_buffer.head =
        new_head; // do not move this line before the function call

    output.hw.host_buffer.tail = new_tail;

    bool buffer_is_full = ((new_tail > 0) && (new_head == new_tail - 1)) ||
                          ((new_tail == 0) && (new_head == alloc_cap - 1));
    if (buffer_is_full)
      should_retry = true;
  }
}

void PLink::actionStartKernel() {

  should_retry = false;

  cl_uint arg_ix = 0;
  std::vector<cl::Event> active_events;
  cl_int err;

  for (auto &input : inputs) {
    OCL_ASSERT(input.hw.host_buffer.head == input.hw.device_buffer.head &&
                   input.hw.host_buffer.tail == input.hw.device_buffer.tail,
               "Inconsistent head or tails for port %s\n",
               input.hw.address.toString().c_str());
    int available = pinAvailIn(input.sw);
    auto old_head = input.hw.host_buffer.head;
    auto tail = input.hw.host_buffer.tail;
    auto cap = input.hw.device_buffer.user_alloc_size;
    auto free_space = computeFreeSpace(old_head, tail, cap);

    auto tokens_to_write = (available < free_space) ? available : free_space;

    // read from the software fifos into the host buffer

    if (tokens_to_write + old_head >= cap) {
      // buffer wraps around in the read
      char *buff = input.hw.getHostBufferAtHead();
      pinReadRepeat(input, buff, cap - old_head);
      tokens_to_write -= cap - old_head;
      input.hw.host_buffer.head = 0;
    }
    if (tokens_to_write) {
      char *buff = input.hw.getHostBufferAtHead();
      pinReadRepeat(input, buff, tokens_to_write);
      input.hw.host_buffer.head += tokens_to_write;
    }

    // do not change the device_buffer.head before this call
    // change the host_buffer.head
    input.hw.writeToDeviceBuffer(command_queue);
    // no change the device_buffer.head to point to the new head
    input.hw.device_buffer.head = input.hw.host_buffer.head;

    if (input.hw.buffer_event_info[0].active == true)
      active_events.push_back(input.hw.buffer_event[0]);
    if (input.hw.buffer_event_info[1].active == true)
      active_events.push_back(input.hw.buffer_event[1]);
    // -- set the arguments
    kernel.setArg(arg_ix, input.hw.device_buffer.data_buffer);
    arg_ix++;
    kernel.setArg(arg_ix, input.hw.device_buffer.meta_buffer);
    arg_ix++;
    kernel.setArg(arg_ix, input.hw.device_buffer.user_alloc_size);
    arg_ix++;
    kernel.setArg(arg_ix, input.hw.device_buffer.head);
    arg_ix++;
    kernel.setArg(arg_ix, input.hw.device_buffer.tail);
    arg_ix++;
  }

  for (auto &output : outputs) {
    kernel.setArg(arg_ix, output.hw.device_buffer.data_buffer);
    arg_ix++;
    kernel.setArg(arg_ix, output.hw.device_buffer.meta_buffer);
    arg_ix++;
    kernel.setArg(arg_ix, output.hw.device_buffer.user_alloc_size);
    arg_ix++;
    kernel.setArg(arg_ix, output.hw.device_buffer.head);
    arg_ix++;
    kernel.setArg(arg_ix, output.hw.device_buffer.tail);
    arg_ix++;
  }

  // all the arguements are set and input buffer transfers have been enqueued
  // ready to start the kernel

  kernel_wait_events = active_events;

  OCL_CHECK(err,
            err = command_queue.enqueueTask(
                kernel,              // the kernel object
                &kernel_wait_events, // the list of events that should complete
                &kernel_event[0] // the event generated by the kernel execution
                ));

  OCL_MSG("plink::Kernel enqueued\n");
  call_index++;
}

inline uint32_t PLink::computeFreeSpace(const uint32_t head,
                                        const uint32_t tail,
                                        const uint32_t cap) const {

  uint32_t free_space = 0;
  if (head == tail)
    free_space = cap - 1;
  else if (head < tail)
    free_space = tail - 1 - head;
  else // head > tail
    free_space = cap - head + tail - 1;
  return free_space;
}

bool PLink::checkCanWriteToOutput() const {

  for (auto &output : outputs) {
    if (output.hw.host_buffer.tail != output.hw.host_buffer.head) {
      // the host buffer has pending tokens
      if (pinAvailOut(output.sw) > 0)
        return true;
    }
  }
  return false;
}

bool PLink::checkCanSendInput() const {

  for (auto &input : inputs) {
    auto head = input.hw.host_buffer.head;
    auto tail = input.hw.host_buffer.tail;
    auto cap = input.hw.device_buffer.user_alloc_size;
    auto available = pinAvailIn(input.sw);
    auto free_space = computeFreeSpace(head, tail, cap);
    auto to_write = available < free_space ? available : free_space;
    if (to_write > 0)
      return true;
  }
  return false;
}

bool PLink::checkHardwareOutputSpace() const {

  for (auto &output : outputs) {
    auto head = output.hw.device_buffer.head;
    auto tail = output.hw.device_buffer.tail;
    auto cap = output.hw.device_buffer.user_alloc_size;
    auto free_space = computeFreeSpace(head, tail, cap);
    if (free_space > 0)
      return true;
  }
  return false;
}

bool PLink::checkShouldRetry() const { return should_retry; }

bool PLink::checkKernelFinished() const {

  for (auto &input : inputs) {
    if (input.hw.size_event_info.active == true) {
      if (!eventComplete(input.hw.buffer_size_event))
        return false;
    }
  }

  for (auto &output : outputs) {
    if (output.hw.size_event_info.active == true) {
      if (!eventComplete(output.hw.buffer_size_event))
        return false;
    }
  }

  return true;
}

bool PLink::checkReadFinished() const {

  for (auto &output : outputs) {
    if (output.hw.buffer_event_info[0].active == true) {
      if (!eventComplete(output.hw.buffer_event[0]))
        return false;
    }
    if (output.hw.buffer_event_info[1].active == true) {
      if (!eventComplete(output.hw.buffer_event[1]))
        return false;
    }
  }

  return true;
}

bool PLink::eventComplete(const cl::Event &event) const {
  cl_int state = 0;
  cl_int err = 0;
  OCL_CHECK(err, err = event.getInfo<cl_int>(CL_EVENT_COMMAND_EXECUTION_STATUS,
                                             &state));
  return state == CL_COMPLETE;
}

PLink::Action PLink::actionScheduler(AbstractActorInstance *base) {

  // --update the "local" software ports

  int num_inputs = inputs.size();
  for (int i = 0; i < num_inputs; i++) {
    int available = base->input[i].local->available;
    if (base->cpu_index == base->input[i].writer->cpu) {
      available += base->input[i].writer->local->count;
      base->input[i].local->available = available;
    }

    inputs[i].sw->available = available;
    inputs[i].sw->pos = base->input[i].local->pos;
    inputs[i].sw->buffer = base->input[i].buffer;
    inputs[i].sw->capacity = base->input[i].capacity;
  }

  for (int i = 0; i < outputs.size(); i++) {

    outputs[i].sw->pos = base->output[i].local->pos;
    outputs[i].sw->available = base->output[i].local->available;
    outputs[i].sw->buffer = base->output[i].buffer;
    outputs[i].sw->capacity = base->output[i].capacity;
  }
  Action action_performed = Action::StartKernel;
  switch (plink_state) {
  case State::INIT:
    // check if pending tokens can be pushed to the output
    if (checkCanWriteToOutput()) {
      actionFreeUpOutputBuffer();
      action_performed = Action::FreeUpOutptuBuffer;
    } else if (checkCanSendInput() || checkHardwareOutputSpace() ||
               checkShouldRetry()) {
      actionStartKernel();
      plink_state = State::POLL_KERNEL;
      action_performed = Action::StartKernel;
    }

    break;
  case State::POLL_KERNEL:
    if (checkKernelFinished()) {
      actionUpdateIndices();
      plink_state = State::POLL_READ;
      action_performed = Action::UpdateIndices;
    }
    break;
  case State::POLL_READ:
    if (checkReadFinished()) {
      actionFreeUpOutputBuffer();
      plink_state = State::INIT;
      action_performed = Action::FreeUpOutptuBuffer;
    }
  default:
    OCL_ASSERT(false, "Invalid entry point for plink!\n");
  }

  if (action_performed != Action::NoAction) {

    base->fired = 1;
    for (int i = 0; i < inputs.size(); i++) {
      base->input[i].local->pos = inputs[i].sw->pos;
      base->input[i].local->count =
          base->input[i].local->available - inputs[i].sw->available;
      base->input[i].local->available = inputs[i].sw->available;
    }

    for (int i = 0; i < outputs.size(); i++) {
      base->output[i].local->pos = outputs[i].sw->pos;
      base->output[i].local->count =
          base->output[i].local->available - outputs[i].sw->available;
      base->output[i].local->available = outputs[i].sw->available;
    }
  }
  return action_performed;
}
}; // namespace ocl_device
