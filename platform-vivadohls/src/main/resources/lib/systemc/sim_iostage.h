#ifndef __SIM_IOSTAGE_H__
#define __SIM_IOSTAGE_H__
#include "debug_macros.h"
#include "systemc.h"
#include "trigger.h"
#include <memory>
namespace ap_rtl {

template <typename T> class AbstractSimulationPort : public sc_module {
public:
  sc_in_clk ap_clk;

  sc_in<bool> init;

  sc_in<bool> ap_rst_n;
  sc_out<bool> ap_done;
  sc_out<bool> ap_idle;
  sc_out<bool> ap_ready;
  sc_in<bool> ap_start;
  sc_out<uint32_t> ap_return;

  enum State {
    WaitForStartOrInit = 0,
    Initialize = 1,
    BeginLoop = 2,
    Loop = 3,
    EndLoop = 4
  };

  AbstractSimulationPort(sc_module_name name)
      : sc_module(name), tokens_processed("tokens_processed"),
        tokens_to_process("tokens_to_process"), state("state"),
        next_state("next_state") {}

  void allocateDeviceMemory(std::size_t buffer_size) {
    mem.buffer.resize(buffer_size);
  }

  void writeDeviceMemory(std::vector<T> &host_buffer, const std::size_t n = 0) {
    ASSERT(n <= mem.buffer.size(),
           "bad write to device memory, allocated size "
           "is %lu but the write size is %lu\n",
           mem.buffer.size(), n);
    ASSERT(n <= host_buffer.size(),
           "bad write to memory, host buffer size is %lu but the write size is "
           "%lu\n",
           host_buffer.size(), n);
    if (n == 0)
      std::copy(host_buffer.begin(), host_buffer.end(), mem.buffer.begin());
    else
      std::copy(host_buffer.begin(), host_buffer.begin() + n,
                mem.buffer.begin());
  };

  void readDeviceMemory(std::vector<T> &host_buffer,
                        const std::size_t n = 0) const {
    ASSERT(n <= host_buffer.size(),
           "bad read from device memory, host buffer size is %lu bu the read "
           "size is %lu\n",
           host_buffer.size(), n);
    ASSERT(n <= mem.buffer.size(),
           "bad read from device memory, allocated size is %lu but read size "
           "is %lu\n",
           mem.buffer.size(), n);
    if (n == 0)
      std::copy(mem.buffer.begin(), mem.buffer.end(), host_buffer.begin());
    else
      std::copy(mem.buffer.begin(), mem.buffer.begin() + n,
                host_buffer.begin());
  };

  virtual void setArg(std::size_t arg) = 0;
  std::size_t querySize() const { return tokens_processed.read(); };

protected:
  struct MemoryBuffer {
    std::vector<T> buffer;
  };

  MemoryBuffer mem;
  inline void memoryWrite(std::size_t address, T token) {
    mem.buffer[address] = token;
  }

  inline T memoryRead(std::size_t address) const { return mem.buffer[address]; }
  std::size_t getProcessedCount() { return tokens_processed.read(); }

  inline std::size_t getMemoryCapacity() { return mem.buffer.size(); }

  inline bool compareState(State _state) { return state.read() == _state; }

public:
  sc_signal<uint8_t> state;
  sc_signal<uint8_t> next_state;

  sc_signal<std::size_t> tokens_to_process;
  sc_signal<std::size_t> tokens_processed;

  void setFSM() {
    next_state = State::WaitForStartOrInit;
    switch (state.read()) {
    case State::WaitForStartOrInit:
      ASSERT(getInit() & getStart() == false,
             "Both init and ap_start are asserted!");
      if (getInit() == true) {
        next_state.write(State::Initialize);
      } else if (getStart() == true) {
        next_state.write(State::BeginLoop);
      }
      break;
    case State::Initialize:
      if (getStart() == true) {
        next_state.write(State::BeginLoop);
      } else {
        next_state.write(State::WaitForStartOrInit);
      }
      break;
    case State::BeginLoop:
      if (numIterations() > 0)
        next_state.write(State::Loop);
      else
        next_state.write(State::EndLoop);
      break;

    case State::Loop:
      if (tokens_to_process.read() == 1)
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

  void executeFSM() {
    while (true) {
      wait();
      if (getReset() == true) {
        tokens_to_process.write(0);
        tokens_processed.write(0);
        state.write(State::WaitForStartOrInit);

      } else {
        switch (state.read()) {
        case State::WaitForStartOrInit:
          // Do nothing

          break;
        case State::Initialize: {

          tokens_processed.write(0);
          break;
        }
        case State::BeginLoop: {

          std::size_t num_iter = numIterations();
          if (num_iter > 0)
            setReturn(ReturnStatus::EXECUTED);
          else
            setReturn(ReturnStatus::WAIT);
          tokens_to_process.write(num_iter);
          break;
        }
        case State::Loop: {
          doLoopBody();
          tokens_processed.write(tokens_processed.read() + 1);
          tokens_to_process.write(tokens_to_process.read() - 1);

          break;
        }
        case State::EndLoop:
          // Do nothing here
          break;
        default:
          PANIC("Invalid state reached!");
          break;
        }
        state.write(next_state);
      }
    }
  }

  // sensitive to state
  void setApControl() {
    if (this->compareState(
            AbstractSimulationPort<T>::State::WaitForStartOrInit)) {
      ap_idle.write(true);
    } else {
      ap_idle.write(false);
    }
    if (this->compareState(AbstractSimulationPort<T>::State::EndLoop)) {
      ap_ready.write(true);
      ap_done.write(true);
    } else {
      ap_ready.write(false);
      ap_done.write(false);
    }
  }

  virtual void doLoopBody() = 0;
  virtual std::size_t numIterations() const = 0;

  bool getStart() { return ap_start.read(); }
  bool getInit() { return init.read(); }
  bool getReset() { return ap_rst_n.read() == false; }
  void setReturn(uint32_t return_val) { ap_return.write(return_val); }
};

template <typename T> class InputStage : public AbstractSimulationPort<T> {
public:
  // FIFO interface
  sc_out<T> fifo_din;
  sc_out<bool> fifo_write;
  sc_in<uint32_t> fifo_count;
  sc_in<uint32_t> fifo_size;
  sc_in<bool> fifo_full_n;

  // argument
  std::size_t request_size;

  SC_HAS_PROCESS(InputStage);
  InputStage(sc_module_name name) : AbstractSimulationPort<T>(name) {

    request_size = 0;

    SC_THREAD(setWriteSignal);
    this->sensitive << this->ap_clk.pos();

    SC_THREAD(executeFSM);
    this->sensitive << this->ap_clk.pos();

    SC_METHOD(setFSM);

    this->sensitive << this->state << this->init << this->ap_start << fifo_count
                    << fifo_size << this->tokens_processed;

    SC_METHOD(setApControl);
    this->sensitive << this->state;
  }

  void setArg(std::size_t arg) { request_size = arg; }

  std::size_t numIterations() const {
    uint32_t fifo_space = fifo_count.read() > fifo_size.read()
                              ? 0
                              : fifo_size.read() - fifo_count.read();
    std::size_t tokens_left = request_size - this->tokens_processed.read();
    return tokens_left > fifo_space ? fifo_space : tokens_left;
  }

  void doLoopBody() {

    std::size_t address = this->tokens_processed.read();
    ASSERT(address < request_size, "Request size violation\n");

    T token = this->memoryRead(address);
    fifo_din.write(token);
  }

  // SC_THREAD
  void setWriteSignal() {
    while (true) {
      wait();
      if (this->compareState(AbstractSimulationPort<T>::State::Loop)) {
        fifo_write.write(true);
      } else {
        fifo_write.write(false);
      }
    }
  }
};

template <typename T> class OutputStage : public AbstractSimulationPort<T> {
public:
  sc_in<uint32_t> fifo_count;
  sc_in<T> fifo_dout;
  sc_in<bool> fifo_empty_n;
  sc_out<bool> fifo_read;
  sc_in<T> fifo_peek;

  std::size_t buffer_capacity;

  SC_HAS_PROCESS(OutputStage);
  OutputStage(sc_module_name name) : AbstractSimulationPort<T>(name) {

    buffer_capacity = 0;
    SC_METHOD(setReadSignal);
    this->sensitive << this->state;

    SC_THREAD(executeFSM);
    this->sensitive << this->ap_clk.pos();

    SC_METHOD(setFSM);
    this->sensitive << this->state << this->init << this->ap_start << fifo_count
                    << this->tokens_processed;

    SC_METHOD(setApControl);
    this->sensitive << this->state;
  }

  void setArg(std::size_t arg) {
    ASSERT(arg <= this->getMemoryCapacity(),
           "bad setArg, arg is %lu but memory capacity is %lu\n", arg,
           this->getMemoryCapacity());
    buffer_capacity = arg;
  }

  void doLoopBody() {
    std::size_t address = this->tokens_processed.read();
    ASSERT(address < buffer_capacity, "Buffer capacity violation\n");
    T token = fifo_dout.read();
    this->memoryWrite(address, token);
  }
  std::size_t numIterations() const {
    std::size_t buffer_space = buffer_capacity - this->tokens_processed.read();
    std::size_t fifo_tokens = fifo_count.read();
    return (buffer_space > fifo_tokens ? fifo_tokens : buffer_space);
  }

  // SC_METHOD
  void setReadSignal() {

    if (this->compareState(AbstractSimulationPort<T>::State::Loop)) {
      fifo_read.write(true);
    } else {
      fifo_read.write(false);
    }
  }
};
} // namespace ap_rtl
#endif // __SIM_IOSTAGE_H__