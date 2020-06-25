#ifndef __SIM_IOSTAGE_H__
#define __SIM_IOSTAGE_H__
#include "debug_macros.h"
#include "systemc.h"
#include "trigger.h"
#include <memory>
namespace ap_rtl {
namespace iostage {
template <typename T> struct MemoryBuffer { std::vector<T> buffer; };

enum State {
  WaitForStartOrInit = 0,
  Initialize = 1,
  BeginLoop = 2,
  Loop = 3,
  EndLoop = 4
};

template <typename T> class InputStage : public sc_module {
public:
  sc_in_clk ap_clk;

  sc_in<bool> init;

  sc_in<bool> ap_rst_n;
  sc_out<bool> ap_done;
  sc_out<bool> ap_idle;
  sc_out<bool> ap_ready;
  sc_in<bool> ap_start;
  sc_out<uint32_t> ap_return;

  // FIFO interface
  sc_out<T> fifo_din;
  sc_out<bool> fifo_write;
  sc_in<uint32_t> fifo_count;
  sc_in<uint32_t> fifo_size;
  sc_in<bool> fifo_full_n;



  MemoryBuffer<T> mem;

  sc_signal<uint32_t> state;
  sc_signal<uint32_t> next_state;

  // internal counter, needs to be initialized
  std::size_t request_size;
  sc_signal<std::size_t> tokens_read;
  sc_signal<std::size_t> tokens_to_read;

  // sc_signal<std::size_t> size_register;

  // Interface to C++ code
  void allocateDeviceMemory(std::size_t buffer_size) {

    mem.buffer.resize(buffer_size);
    mem.buffer_size = buffer_size;
  }
  void writeDeviceMemory(std::vector<T> &host_buffer) {

    ASSERT(host_buffer.size() <= mem.buffer.size(),
           "bad write to memory, allocated size "
           "is %llu but the host buffer is %llu\n",
           mem.buffer.size(), host_buffer.size());
    std::copy(host_buffer.begin(), host_buffer.end(), mem.buffer.begin());
  }
  std::size_t querySize() { return tokens_read.read(); }
  void setRequest(std::size_t req_sz) {
    ASSERT(req_sz <= mem.buffer.size(),
           "Request size (%llu) larger than device buffer capacity (%llu)",
           req_sz, mem.buffer.size());
    request_size = req_sz;
  }
  // SystemC implementations

  // SC_THREAD
  void executeFSM() {
    while (true) {
      wait();
      if (ap_rst_n.read() == false) {
        tokens_read.write(0);
        tokens_to_read.write(0);
        state.write(State::WaitForStartOrInit);

      } else {
        switch (state.read()) {
        case State::WaitForStartOrInit:
          // Do nothing

          break;
        case State::Initialize: {

          tokens_read.write(0);
          break;
        }
        case State::BeginLoop: {

          std::size_t num_iter = NumWriteIterations();
          if (num_iter > 0)
            ap_return.write(ReturnStatus::EXECUTED);
          else
            ap_return.write(ReturnStatus::WAIT);
          tokens_to_read.write(num_iter);
          break;
        }
        case State::Loop: {
          // Do writes
          std::size_t address = tokens_read.read();
          ASSERT(address < request_size, "Request size violation");
          T token = mem.buffer[address];
          fifo_din.write(token);
          tokens_read.write(tokens_read.read() + 1);
          tokens_to_read.write(tokens_to_read.read() - 1);
          break;

        }
        case State::EndLoop:
          // Do nothing here
          break;
        default:
          PANIC("Invalid state reached!");
          break;
        }
      }
    }
  }
  // SC_THREAD
  void setWriteSignal() {
    while (true) {
      wait();
      if (state.read() == State::Loop) {
        fifo_write.write(true);
      } else {
        fifo_write.write(false);
      }
    }
  }

  // Normal C++ function
  std::size_t NumWriteIterations() {
    uint32_t fifo_space = fifo_count.read() > fifo_size.read()
                              ? 0
                              : fifo_size.read() - fifo_count.read();
    std::size_t tokens_left = request_size - tokens_read.read();
    return tokens_left > fifo_space ? fifo_space : tokens_left;
  }

  // sensitive to state
  void setApControl() {
    if (state.read() == State::WaitForStartOrInit) {
      ap_idle.write(true);
    } else {
      ap_idle.write(false);
    }
    if (state.read() == State::EndLoop) {
      ap_ready.write(true);
      ap_done.write(true);
    } else {
      ap_ready.write(false);
      ap_done.write(false);
    }
  }

  // sensitive to state, init, ap_start, fifo_count, fifo_size, tokens_read
  void setFSM() {
    next_state = State::WaitForStartOrInit;
    switch (state.read()) {
    case State::WaitForStartOrInit:
      ASSERT(init.read() & ap_start.read() == false,
             "Both init and ap_start are asserted!");
      if (init.read() == true) {
        next_state.write(State::Initialize);
      } else if (ap_start.read() == true) {
        next_state.write(State::BeginLoop);
      }
      break;
    case State::Initialize:
      if (ap_start.read() == true) {
        next_state.write(State::BeginLoop);
      } else {
        next_state.write(State::WaitForStartOrInit);
      }
      break;
    case State::BeginLoop:
      if (NumWriteIterations() > 0)
        next_state.write(State::Loop);
      else
        next_state.write(State::EndLoop);
      break;

    case State::Loop:
      if (tokens_to_read.read() == 1)
        next_state.write(State::EndLoop);
      else
        next_state.write(State::Loop);
      break;
    case State::EndLoop:
      next_state.write(State::WaitForStartOrInit);
      break;
    default:
      PANIC("Invalid state reached!");
      break;
    }
  }

  SC_HAS_PROCESS(InputStage);
  InputStage(sc_module_name name)
      : sc_module(name), state("state"), next_state("next_state"),
        tokens_read("tokens_read"), tokens_to_read("tokens_to_read") {

    SC_THREAD(setWriteSignal);
    sensitive << ap_clk.pos();

    SC_THREAD(executeFSM);
    sensitive << ap_clk.pos();

    SC_METHOD(setFSM);
    sensitive << state << init << ap_start << fifo_count << fifo_size
              << tokens_read;

    SC_METHOD(setApControl);
    sensitive << state;
  }
};

template <typename T> class OutputStage : public sc_module {
public:
  sc_in_clk ap_clk;

  sc_in<bool> init;

  sc_in<bool> ap_rst_n;
  sc_out<bool> ap_done;
  sc_out<bool> ap_idle;
  sc_out<bool> ap_ready;
  sc_in<bool> ap_start;
  sc_out<uint32_t> ap_return;

  sc_in<uint32_t> fifo_count;
  sc_in<T> fifo_dout;
  sc_in<bool> fifo_empty_n;
  sc_in<uint32_t> fifo_size;
  sc_out<bool> fifo_read;

  MemoryBuffer<T> mem;

  std::size_t buffer_capacity;
  sc_signal<std::size_t> tokens_written;
  sc_signal<std::size_t> tokens_to_write;

  sc_signal<uint32_t> state;
  sc_signal<uint32_t> next_state;

  // C++ interface
  void allocateDeviceMemory(std::size_t buffer_size) {

    mem.buffer.resize(buffer_size);
    mem.buffer_size = buffer_size;
  }
  void readDeviceMemory(std::vector<T> &host_buffer) {

    ASSERT(host_buffer.size() <= mem.buffer.size(),
           "bad read from device memory, allocated size "
           "is %llu but the host buffer is %llu\n",
           mem.buffer.size(), host_buffer.size());
    std::copy(mem.buffer.begin(), mem.buffer.end(), host_buffer.begin());
  }
  std::size_t querySize() { return tokens_written.read(); }
  void setCapacity(std::size_t cap) {
    ASSERT(cap <= mem.buffer.size(),
           "Capacity (%llu) larger than device buffer (%llu) !", cap,
           mem.buffer.size());
    buffer_capacity = cap;
  }

  // SystemC implementations

  // SC_THREAD
  void executeFSM() {
    while (true) {
      wait();
      if (ap_rst_n.read() == false) {
        tokens_written.write(0);
        tokens_to_write.write(0);
        state.write(State::WaitForStartOrInit);

      } else {
        switch (state.read()) {
        case State::WaitForStartOrInit:
          // Do nothing
          break;
        case State::Initialize:
          tokens_written.write(0);
          break;
        case State::BeginLoop: {

          std::size_t num_iter = NumReadIterations();
          if (num_iter > 0)
            ap_return.write(ReturnStatus::EXECUTED);
          else
            ap_return.write(ReturnStatus::WAIT);
          tokens_to_write.write(num_iter);
          break;
        }
        case State::Loop: {

          // Do writes
          std::size_t address = tokens_written.read();
          ASSERT(address < buffer_capacity, "Capacity violation");
          mem.buffer[address] = fifo_dout.read();
          tokens_written.write(tokens_written.read() + 1);
          tokens_to_write.write(tokens_to_write.read() - 1);
          break;
        }
        case State::EndLoop:
          // Do nothing here
          break;
        default:
          PANIC("Invalid state reached!");
          break;
        }
      }
    }
  }
  // SC_THREAD
  void setReadSignal() {
    while (true) {
      wait();
      if (state.read() == State::Loop) {
        fifo_read.write(true);
      } else {
        fifo_read.write(false);
      }
    }
  }

  // Normal C++ function
  std::size_t NumReadIterations() {
    std::size_t buffer_space = buffer_capacity - tokens_written.read();
    std::size_t fifo_tokens = fifo_count.read();
    return (buffer_space > fifo_tokens ? fifo_tokens : buffer_space);
  }

  // sensitive to state
  void setApControl() {
    if (state.read() == State::WaitForStartOrInit) {
      ap_idle.write(true);
    } else {
      ap_idle.write(false);
    }
    if (state.read() == State::EndLoop) {
      ap_ready.write(true);
      ap_done.write(true);
    } else {
      ap_ready.write(false);
      ap_done.write(false);
    }
  }

  // sensitive to state, init, ap_start, fifo_count, tokens_written
  void setFSM() {
    next_state = State::WaitForStartOrInit;
    switch (state.read()) {
    case State::WaitForStartOrInit:
      ASSERT(init.read() & ap_start.read() == false,
             "Both init and ap_start are asserted!");
      if (init.read() == true) {
        next_state.write(State::Initialize);
      } else if (ap_start.read() == true) {
        next_state.write(State::BeginLoop);
      }
      break;
    case State::Initialize:
      if (ap_start.read() == true) {
        next_state.write(State::BeginLoop);
      } else {
        next_state.write(State::WaitForStartOrInit);
      }
      break;
    case State::BeginLoop:
      if (NumReadIterations() > 0)
        next_state.write(State::Loop);
      else
        next_state.write(State::EndLoop);
      break;

    case State::Loop:
      if (tokens_to_write.read() == 1)
        next_state.write(State::EndLoop);
      else
        next_state.write(State::Loop);
      break;
    case State::EndLoop:
      next_state.write(State::WaitForStartOrInit);
      break;
    default:
      PANIC("Invalid state reached!");
      break;
    }
  }

  SC_HAS_PROCESS(OutputStage);
  OutputStage(sc_module_name name)
      : sc_module(name), state("state"), next_state("next_state"),
        tokens_written("tokens_written"), tokens_to_write("tokens_to_write") {

    SC_THREAD(setReadSignal);
    sensitive << ap_clk.pos();

    SC_THREAD(executeFSM);
    sensitive << ap_clk.pos();

    SC_METHOD(setFSM);
    sensitive << state << init << ap_start << fifo_count << tokens_written;

    SC_METHOD(setApControl);
    sensitive << state;
  }
};
} // namespace iostage
} // namespace ap_rtl

#endif // __SIM_IOSTAGE_H__