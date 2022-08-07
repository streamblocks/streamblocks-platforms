#include "plink.h"
#include "simulation-kernel.h"
#include <systemc>

namespace sim_device {

PLink::PLink(const std::vector<PortInfo<LocalInputPort>> &input_info,
             const std::vector<PortInfo<LocalOutputPort>> &output_info,
             const uint32_t num_mems, const std::string kernel_name,
             char *profile_file_name, const int vcd_trace_level)
    : profile_file_name(profile_file_name) {

  OCL_MSG("Constructing simulation device\n");
  const sc_core::sc_time clk_period(3.3, sc_core::SC_NS);
  kernel = std::make_unique<ap_rtl::SimulationKernel>(
      kernel_name.c_str(), clk_period, 0, vcd_trace_level,
      profile_file_name != NULL);
  kernel->reset();

  OCL_MSG("Starting simulation thread\n");
  kernel_thread = std::thread(&PLink::kernelRunner, this);

  OCL_MSG("plink::Building %lu input ports\n", input_info.size());
  for (auto &inpt : input_info) {
    inputs.emplace_back(inpt);
  }
  OCL_MSG("plink::Building %lu output ports\n", output_info.size());
  for (auto &otpt : output_info) {
    outputs.emplace_back(otpt);
  }

  call_index = 0;
  should_retry = false;
  plink_state = State::INIT;
}

void PLink::kernelRunner() {

  while (true) {
    while (command_queue.empty())
      ;
    auto command = command_queue.peek();
    while (command_queue.try_pop() == false)
      ;
    if (command == SimCommand::SIM_START) {
      OCL_MSG("plink::kernelRunner::SIM_START\n");
      kernel->simulate();
      if (profile_file_name != NULL) {
        std::ofstream ofs(profile_file_name, std::ios::out);
        OCL_MSG("plink::kernelRunner::dumping profile into %s\n",
                profile_file_name);
        kernel->dumpStats(ofs);
        ofs.close();
      }
      while (response_queue.try_push(true) != true)
        ;
      OCL_MSG("plink::kernelRunner::SIM_FINISHED\n");
    } else {
      OCL_MSG("plink::kernelRunner::SIM_TERMINATE\n");
      break;
    }
  }
}

void PLink::allocateOutput(const sim_device::PortAddress &name,
                           const std::size_t size) {

  for (auto &output : outputs) {
    if (output.getAddress() == name) {
      output.hw.allocate(size, output.token_size);
      break;
    }
  }
}

void PLink::allocateInput(const sim_device::PortAddress &name,
                          const std::size_t size) {

  for (auto &input : inputs) {
    if (input.getAddress() == name) {
      input.hw.allocate(size, input.token_size);
      break;
    }
  }
}

void PLink::pinWriteRepeat(OutputPort &port, char *buff, int n) {
  OCL_ASSERT(pinAvailOut(&port.sw) >= n, "Invalid software write in port %s\n",
             port.getAddress().toString().c_str());

  port.sw.available -= n;

  int token_size = port.token_size;

  if (port.sw.pos + n >= 0) {
    auto dest =
        reinterpret_cast<char *>(port.sw.buffer) + (port.sw.pos * token_size);

    std::memcpy(dest, buff, -(port.sw.pos * token_size));

    buff += -(port.sw.pos * token_size);
    n -= -(port.sw.pos);
    port.sw.pos = -(port.sw.capacity);
  }
  if (n) {
    auto dest =
        reinterpret_cast<char *>(port.sw.buffer) + port.sw.pos * token_size;
    std::memcpy(dest, buff, n * token_size);
    port.sw.pos += n;
  }
}

void PLink::pinReadRepeat(InputPort &port, char *buff, int n) {
  OCL_ASSERT(pinAvailIn(&port.sw) >= n, "Invalid software read in port %s\n",
             port.getAddress().toString().c_str());
  port.sw.available -= n;

  int token_size = port.token_size;
  char *source =
      reinterpret_cast<char *>(port.sw.buffer) + port.sw.pos * token_size;

  if (port.sw.pos + n >= 0) {
    char *source =
        reinterpret_cast<char *>(port.sw.buffer) + port.sw.pos * token_size;
    std::memcpy(buff, source, -(port.sw.pos * token_size));
    buff += -(port.sw.pos * token_size);
    n -= -(port.sw.pos);
    port.sw.pos = -(port.sw.capacity);
  }
  if (n) {
    char *source =
        reinterpret_cast<char *>(port.sw.buffer) + port.sw.pos * token_size;
    std::memcpy(buff, source, n * token_size);
    port.sw.pos += n;
  }
}

void PLink::actionFreeUpOutputBuffer() {

  // -- make sure that head and tails are consistent, i.e., no operation is in
  // flight
  OCL_MSG("plink::actionFreeUpOutputBuffer\n");

  for (auto &output : outputs) {

    auto available = pinAvailOut(&output.sw);

    auto unwritten_tokens = 0;
    int alloc_cap = output.hw.user_alloc_size;
    int tail = output.hw.tail;
    int head = output.hw.head;

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
        auto *buff = output.hw.getHostBufferAtTail();
        pinWriteRepeat(output, buff, alloc_cap - tail);

        to_write -= (alloc_cap - tail);
        output.hw.tail = 0;
      }

      if (to_write > 0) {
        auto *buff = output.hw.getHostBufferAtTail();
        pinWriteRepeat(output, buff, to_write);

        output.hw.tail += to_write;
      }
    }

    if (output.hw.tail == output.hw.head) {
      OCL_MSG("plink::%s::freed\n", output.address.toString().c_str());
    }
  }
}

void PLink::actionUpdateIndices() {

  OCL_MSG("plink::actionUpdateIndices\n");
  for (auto &input : inputs) {

    int new_tail = input.hw.meta_buffer[0];
    int old_tail = input.hw.tail;
    int alloc_cap = input.hw.user_alloc_size;

    // whatever is between the old tail and the new tail is what has been
    // consumed by the hardware
    int consumed = 0;
    if (old_tail < new_tail) {
      consumed = new_tail - old_tail;
    } else if (new_tail < old_tail) {
      // hardware has wrapped around
      consumed = alloc_cap - old_tail + new_tail;
    }
    OCL_MSG("plink::%s::consumed %u tokens\n", input.address.toString().c_str(),
            consumed);

    input.hw.tail = new_tail;
  }

  for (auto &output : outputs) {

    int alloc_cap = output.hw.user_alloc_size;
    int tail = output.hw.tail;

    int new_head = output.hw.meta_buffer[0];
    int old_head = output.hw.head;

    OCL_ASSERT(new_head < alloc_cap,
               "Invalid head generated by hardware on port %s!",
               output.getAddress().toString().c_str());

    auto produced = output.hw.readFromDeviceBuffer(old_head, new_head);

    OCL_MSG("%s produced %u tokens\n", output.getAddress().toString().c_str(),
            produced);

    output.hw.head = new_head;

    auto space_left = computeFreeSpace(new_head, tail, alloc_cap);
    if (space_left == 0) {
      // the hardware is back pressured and might have leftover data in its
      // internal FIFOs
      should_retry = true;
    }
  }
}

void PLink::actionStartKernel() {

  OCL_MSG("plink::actionStartKernel\n");
  should_retry = false;
  int arg_ix = 0;
  for (auto &input : inputs) {

    int available = pinAvailIn(&input.sw);
    auto old_head = input.hw.head;

    auto tail = input.hw.tail;
    auto cap = input.hw.user_alloc_size;
    auto free_space = computeFreeSpace(old_head, tail, cap);

    OCL_MSG("%s::available=%u free_space=%u\n",
            input.getAddress().toString().c_str(), available, free_space);
    auto tokens_to_write = (available < free_space) ? available : free_space;

    // read from the software fifos into the host buffer
    auto new_head = old_head;

    if (tokens_to_write + old_head >= cap) {
      // buffer wraps around in the read
      char *buff = input.hw.getHostBufferAtHead();
      pinReadRepeat(input, buff, cap - old_head);
      tokens_to_write -= cap - old_head;
      new_head = 0;
      input.hw.head = new_head;
    }
    if (tokens_to_write) {
      char *buff = input.hw.getHostBufferAtHead();
      pinReadRepeat(input, buff, tokens_to_write);
      new_head += tokens_to_write;
      input.hw.head = new_head;
    }

    auto bytes_transferred = input.hw.writeToDeviceBuffer(old_head, new_head);

    // -- set the arguments
    kernel->setArg(arg_ix++, input.hw.asArgument());
  }

  for (auto &output : outputs) {
    kernel->setArg(arg_ix++, output.hw.asArgument());
  }

  // all the arguements are set and input buffer transfers have been enqueued
  // ready to start the kernel

  while (!command_queue.try_push(SimCommand::SIM_START))
    ;

  OCL_MSG("plink::Kernel enqueued\n");
  call_index++;
}

void PLink::actionCleanUp() {
  while (response_queue.try_pop() == false)
    ;
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
    if (output.hw.tail != output.hw.head) {
      // the host buffer has pending tokens
      if (pinAvailOut(&output.sw) > 0)
        return true;
    }
  }
  return false;
}

bool PLink::checkCanSendInput() const {

  for (auto &input : inputs) {
    auto head = input.hw.head;
    auto tail = input.hw.tail;
    auto cap = input.hw.user_alloc_size;
    auto available = pinAvailIn(&input.sw);
    auto free_space = computeFreeSpace(head, tail, cap);
    auto to_write = available < free_space ? available : free_space;
    if (to_write > 0)
      return true;
  }
  return false;
}

bool PLink::checkHardwareOutputSpace() const {

  for (auto &output : outputs) {
    auto head = output.hw.head;
    auto tail = output.hw.tail;
    auto cap = output.hw.user_alloc_size;
    auto free_space = computeFreeSpace(head, tail, cap);
    if (free_space > 0)
      return true;
  }
  return false;
}

bool PLink::checkShouldRetry() const { return should_retry; }

bool PLink::checkKernelFinished() const {

  bool has_response = response_queue.empty() == false;
  return has_response;
}

bool PLink::checkReadFinished() const {
  // read happens atomically
  return true;
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

    inputs[i].sw.available = available;
    inputs[i].sw.pos = base->input[i].local->pos;
    inputs[i].sw.buffer = base->input[i].buffer;
    inputs[i].sw.capacity = base->input[i].capacity;
  }

  for (int i = 0; i < outputs.size(); i++) {

    outputs[i].sw.pos = base->output[i].local->pos;
    outputs[i].sw.available = base->output[i].local->available;
    outputs[i].sw.buffer = base->output[i].buffer;
    outputs[i].sw.capacity = base->output[i].capacity;
  }
  Action action_performed = Action::NoAction;
  switch (plink_state) {
  case State::INIT:
    // check if pending tokens can be pushed to the output
    if (checkCanWriteToOutput()) {
      actionFreeUpOutputBuffer();

      action_performed = Action::FreeUpOutputBuffer;
      plink_state = State::INIT;
    } else if (checkCanSendInput() && checkHardwareOutputSpace()) {

      actionStartKernel();

      plink_state = State::POLL_KERNEL;

      action_performed = Action::StartKernel;
    } else if (checkShouldRetry()) {
      OCL_MSG("plink::retry\n");
      actionStartKernel();
      plink_state = State::POLL_KERNEL;
      action_performed = Action::StartKernel;
    }

    break;
  case State::POLL_KERNEL:
    if (checkKernelFinished()) {

      actionUpdateIndices();
      plink_state = State::POLL_READ;
    }
    action_performed = Action::UpdateIndices;
    break;
  case State::POLL_READ:
    if (checkReadFinished()) {
      actionCleanUp();
      actionFreeUpOutputBuffer();
      plink_state = State::INIT;
    }
    action_performed = Action::FreeUpOutputBuffer;
    break;
  default:
    OCL_ASSERT(false, "Invalid entry point for plink!\n");
  }

  if (action_performed != Action::NoAction) {

    base->fired = 1;
    for (int i = 0; i < inputs.size(); i++) {
      base->input[i].local->pos = inputs[i].sw.pos;
      base->input[i].local->count =
          base->input[i].local->available - inputs[i].sw.available;
      base->input[i].local->available = inputs[i].sw.available;
    }

    for (int i = 0; i < outputs.size(); i++) {
      base->output[i].local->pos = outputs[i].sw.pos;
      base->output[i].local->count =
          base->output[i].local->available - outputs[i].sw.available;
      base->output[i].local->available = outputs[i].sw.available;
    }
  } else {
    OCL_MSG("plink::NoAction\n");
  }
  return action_performed;
}
void PLink::terminate() {
  OCL_MSG("plink::Terminating the simulation\n");
  while (!command_queue.try_push(SimCommand::SIM_TERMINATE))
    ;
}
}; // namespace sim_device
