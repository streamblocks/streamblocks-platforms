#ifndef __SIMULATION_IOSTAGE_H__
#define __SIMULATION_IOSTAGE_H__
#include "debug_macros.h"
#include "iostage.h"
#include <memory>
#include <systemc>
#include "common.h"

namespace ap_rtl {

/**
 * @brief An astract class defining a simulated iostage
 *
 * @tparam T type of the underlying atomic (hls) implementation of the input
 * or output stage buffers (same as template paramter T for @c BusInterface<T>
 * in @c iostage.h )
 * @tparam FIFO_SIZE size of the connected fifo
 */
template <typename T, int FIFO_SIZE>
class SimulatedBusInterface : public sc_core::sc_module {
public:
  // -- systemc model interfaces
  sc_core::sc_in_clk ap_clk;

  sc_core::sc_in<bool> kernel_start;

  sc_core::sc_in<bool> ap_rst_n;
  sc_core::sc_out<bool> ap_done;
  sc_core::sc_out<bool> ap_idle;
  sc_core::sc_out<bool> ap_ready;
  sc_core::sc_in<bool> ap_start;
  sc_core::sc_out<uint32_t> ap_return;

  // -- C model interfaces
  sc_core::sc_in<uintptr_t> data_buffer;
  sc_core::sc_in<uintptr_t> meta_buffer;
  sc_core::sc_in<uint32_t> alloc_size;
  sc_core::sc_in<uint32_t> head;
  sc_core::sc_in<uint32_t> tail;

  hls::stream<bool> meta_stream;
  hls::stream<T> data_stream;
  // fifo intrefaces are input or output specific and are included in
  // the derived class
  sc_core::sc_in<uint32_t> fifo_count;

  sc_core::sc_signal<uint32_t> tokens_processed;
  // -- the underlying implementation (functional) in C++, which is
  // also used with Vivado HLS to generate the actual RTL implementation
  std::unique_ptr<iostage::BusInterface<T, FIFO_SIZE>> atomic_implementation;
  uint32_t return_code;
  enum State {
    BUSIF_IDLE = 0,
    BUSIF_CALL_FUNCTION = 1,
    BUSIF_STARTUP_DELAY = 2,
    BUSIF_STREAM_CHUNK = 3,
    BUSIF_END_DELAY = 4,
    BUSIF_DONE = 5
  };

  /**
   * @brief Checks whether the internal state is s
   *
   * @param s state to check
   * @return true if this->state == s
   * @return false if this->state != s
   */
  bool atState(State s) { return this->state.read() == s; }

  /**
   * @brief Set the state object
   *
   * @param s new value of the state
   */
  void setState(State s) { this->state.write(s); }

  // a delay counter

  sc_core::sc_signal<State> state, next_state;

  SC_HAS_PROCESS(SimulatedBusInterface);
  SimulatedBusInterface(sc_core::sc_module_name name)
      : sc_core::sc_module(name), state("state"),
        tokens_processed("tokens_processes", 0) {
    // -- register combinational blocks

    SC_METHOD(wireEvaluator);
    this->sensitive << this->state;

    // -- register the sequential block
    SC_CTHREAD(stateMachineExecutor, this->ap_clk.pos());
  }

  /**
   * @brief This function enqueus a single token to the meta_stream and
   * should be called inside the stateMachineExecutor by a derived class
   *
   */
  void enqueueMetaStream() {

    ASSERT(meta_stream.size() == 0,
           "Bad kernel start, meta stream in input stage %s is not empty "
           "at start!\n",
           this->name());
    meta_stream.write(true);
  }

  /**
   * @brief [SC_SCTHREAD] Executes the finite state machine emulating
   * the behvaior of the iostage by calling the underlying atomic (single-cyle)
   * implementation.
   *
   */
  virtual void stateMachineExecutor() {
    while (true) {

      this->waitCycles(1); // for clock
      State next_state = State::BUSIF_IDLE;

      if (this->ap_rst_n.read() == false) {
        this->state.write(State::BUSIF_IDLE);
      } else {
        switch (this->state.read()) {
        case State::BUSIF_IDLE:
          ASSERT(!(this->kernel_start.read() == true &&
                   this->ap_start.read() == true),
                 "Both kernel and ap start are high!\n");
          if (this->kernel_start.read() == true) {
            this->enqueueMetaStream();
          }
          next_state = this->nextState();
          this->state.write(next_state);
          break;
        case State::BUSIF_CALL_FUNCTION:
          //          ASSERT(
          //              this->data_stream.size() == fifo_count.read(),
          //              "data_stream.size() != fifo_count in input stage
          //              %s\n", this->name);
          this->return_code = this->evaluateAtomically();
          next_state = this->nextState();
          this->state.write(next_state);
          break;
        case State::BUSIF_STARTUP_DELAY:
          // wait a specified number of cycles to emulate startup cost of the
          // bus interface
          this->waitCycles(8);
          next_state = this->nextState();
          this->state.write(next_state);
          break;
        case State::BUSIF_STREAM_CHUNK: {

          this->streamChunks();
          next_state = this->nextState();
          this->state.write(next_state);

          break;
        }
        case State::BUSIF_END_DELAY:
          this->waitCycles(40);
          next_state = this->nextState();
          this->state.write(next_state);
          break;

        case State::BUSIF_DONE:
          next_state = this->nextState();
          this->state.write(next_state);

          break;
        default:
          PANIC("Invalid state reached in input stage!\n");
          break;
        }
      }
    }
  }

  /**
   * @brief [SC_METHOD] Evalutes the value of wires based on the state
   * sensitive << state
   */
  void wireEvaluator() {
    // ap_idle
    this->ap_idle.write(this->state.read() == State::BUSIF_IDLE);

    // ap_done and ap_ready have the same logic
    this->ap_done.write(this->state.read() == State::BUSIF_DONE);
    this->ap_ready.write(this->state.read() == State::BUSIF_DONE);
    // ap_return
    this->ap_return.write(
        this->state.read() == State::BUSIF_DONE ? this->return_code : -1);
  }

  /**
   * @brief Waits for the specified amount of cycles
   *
   * @param n number of cycles to wait
   */
  inline void waitCycles(const int n) {
    for (int i = 0; i < n; i++)
      wait();
  }

  /**
   * @brief Atomically (single-cycle) evalutes the bus interface (input or
   * output) implementation. Should be called inside the stateMachineExecotr
   * SC_CTHREAD
   *
   */
  inline virtual uint32_t evaluateAtomically() = 0;

  /**
   * @brief streams the burst chunks to or from the fifos
   * This function is meant to be overriden by the input and output
   * derived classes
   *
   */
  virtual void streamChunks() = 0;

  /**
   * @brief Computes the next state
   *
   * @return State the next state
   */
  virtual State nextState() = 0;

  T *asPointer(const uintptr_t ptr) { return reinterpret_cast<T *>(ptr); }

  void enableTrace(sc_core::sc_trace_file* vcd_dump) {

    TRACE_SIGNAL(vcd_dump, data_buffer);
    TRACE_SIGNAL(vcd_dump, meta_buffer);
    TRACE_SIGNAL(vcd_dump, alloc_size);
    TRACE_SIGNAL(vcd_dump, head);
    TRACE_SIGNAL(vcd_dump, tail);
    TRACE_SIGNAL(vcd_dump, tokens_processed);

  }
};

/**
 * @brief A simulated input memory stage. Uses and atomic implementation of the
 * input memory stage with virtual delays.
 *
 * @tparam T_SC Type of the systemc queue
 * @tparam T_CPP Type of the input memory stage, should be the same as the
 * software side type
 * @tparam FIFO_SIZE Size of the connected fifo
 */
template <typename T_SC, typename T_CPP, int FIFO_SIZE>
class SimulatedInputMemoryStage
    : public SimulatedBusInterface<T_CPP, FIFO_SIZE> {
public:
  sc_core::sc_in<bool> fifo_full_n;
  sc_core::sc_out<bool> fifo_write;
  sc_core::sc_out<T_SC> fifo_din;
  sc_core::sc_in<uint32_t> fifo_size;
  using State = typename SimulatedBusInterface<T_CPP, FIFO_SIZE>::State;

  SimulatedInputMemoryStage(sc_core::sc_module_name name)
      : SimulatedBusInterface<T_CPP, FIFO_SIZE>(name) {
    // -- power on initializations
    static_assert(sizeof(T_SC) >= sizeof(T_CPP),
                  "Token size in systemc should be larger than c++ tokens");
    this->atomic_implementation =
        std::make_unique<iostage::InputMemoryStage<T_CPP, FIFO_SIZE>>();
  }

  inline uint32_t evaluateAtomically() {

    return this->atomic_implementation->operator()(
        this->asPointer(this->data_buffer.read()), // The device buffer pointer
                                                   // (allocated else where)
        this->asPointer(
            this->meta_buffer.read()), // The device meta buffer pointer
                                       // (allocated else where)
        this->alloc_size.read(),       // Allocated size in words
        this->head.read(),             // head index of the data buffer
        this->tail.read(),             // tail index of the deta buffer
        this->fifo_count.read(), // number of elements in the attached fifo
        this->data_stream,       // atomic data stream
        this->meta_stream        // atomic meta stream
    );
  }
  /**
   * @brief executes an state machine representing the input stage
   *
   */
  void streamChunks() override {

    const auto MAX_BURST_LINES =
        iostage::BusInterface<T_CPP, FIFO_SIZE>::MAX_BURST_LINES;
    const auto MAX_NUMBER_OF_BURST =
        iostage::BusInterface<T_CPP, FIFO_SIZE>::MAX_NUMBER_OF_BURSTS;
    // The number of tokens that are read from the memory is obtained

    auto tokens_to_stream = this->data_stream.size();

    // Note that we do not model the delay caused by the wrap around

    // Enqueue the tokens on hls stream to the systemc fifos using the
    // using burst of chunks of maximum MAX_BURST_LINES enqueued in
    // a burst
    for (uint32_t burst_ix = 0; burst_ix < tokens_to_stream;
         burst_ix += MAX_BURST_LINES) {
      uint32_t chunk_size = ((burst_ix + MAX_BURST_LINES) >= tokens_to_stream)
                                ? (tokens_to_stream - burst_ix)
                                : MAX_BURST_LINES;
      for (uint32_t ix = 0; ix < chunk_size; ix++) {
        ASSERT(this->fifo_full_n.read() == true,
               "Attempted to write to a full fifo in an input stage!\n");
        ASSERT(this->data_stream.size() > 0,
               "Attempted to read from an empty hls::stream!\n");
        T_CPP token = this->data_stream.read();
        this->tokens_processed.write(this->tokens_processed.read() + 1);
        this->waitCycles(1);
        this->fifo_din.write(token);
        this->fifo_write.write(true);
      }

      this->waitCycles(1);

      this->fifo_write.write(false);
      this->waitCycles(10);
    }
  }

  State nextState() override {
    switch (this->state.read()) {
    case State::BUSIF_IDLE:
      if (this->ap_start.read() == true)
        return State::BUSIF_CALL_FUNCTION;
      else
        return State::BUSIF_IDLE;
    case State::BUSIF_CALL_FUNCTION:
      return State::BUSIF_STARTUP_DELAY;
    case State::BUSIF_STARTUP_DELAY:
      return State::BUSIF_STREAM_CHUNK;
    case State::BUSIF_STREAM_CHUNK:
      return State::BUSIF_END_DELAY;
    case State::BUSIF_END_DELAY:
      return State::BUSIF_DONE;
    case State::BUSIF_DONE:
      return State::BUSIF_IDLE;
      break;
    default:
      PANIC("Invalid state reached in input stage!\n");
      break;
    }
  }
};

/**
 * @brief A simulated output memory stage. Uses and atomic implementation of the
 * output memory stage with virtual delays.
 *
 * @tparam T_SC Type of the systemc queue
 * @tparam T_CPP Type of the output memory stage, should be the same as the
 * software side type and it should be case that @c sizeof(T_SC) >= @c
 * sizeof(T_CPP)
 * @tparam FIFO_SIZE Size of the connected fifo
 */
template <typename T_SC, typename T_CPP, int FIFO_SIZE>
class SimulatedOutputMemoryStage
    : public SimulatedBusInterface<T_CPP, FIFO_SIZE> {
public:
  sc_core::sc_in<bool> fifo_empty_n;
  sc_core::sc_out<bool> fifo_read;
  sc_core::sc_in<T_SC> fifo_dout;
  sc_core::sc_in<T_SC> fifo_peek;
  using State = typename SimulatedBusInterface<T_CPP, FIFO_SIZE>::State;

  SimulatedOutputMemoryStage(sc_core::sc_module_name name)
      : SimulatedBusInterface<T_CPP, FIFO_SIZE>(name) {
    // -- power on initializations
    static_assert(sizeof(T_SC) >= sizeof(T_CPP),
                  "Token size in systemc should be larger than c++ tokens");

    this->atomic_implementation =
        std::make_unique<iostage::OutputMemoryStage<T_CPP, FIFO_SIZE>>();
  }

  /**
   * @brief executes an state machine representing the input stage
   *
   */
  void streamChunks() override {

    if (this->meta_stream.size() != 0) {
      // don't read from the sc fifos if we are going to initialize the
      // output stage
      return;
    }
    ASSERT(this->data_stream.size() == 0,
           "Data stream should be empty in output stage!\n");

    const auto MAX_BURST_LINES =
        iostage::BusInterface<T_CPP, FIFO_SIZE>::MAX_BURST_LINES;
    const auto MAX_NUMBER_OF_BURST =
        iostage::BusInterface<T_CPP, FIFO_SIZE>::MAX_NUMBER_OF_BURSTS;
    // The number of tokens that are read from the memory is obtained
    auto old_fifo_count = this->fifo_count.read();
    auto tokens_to_stream =
        this->atomic_implementation->tokensToProcess(old_fifo_count);

    // Note that we do not model the delay caused by the wrap around

    // Enqueue the tokens on hls stream to the systemc fifos using the
    // using burst of chunks of maximum MAX_BURST_LINES enqueued in
    // a burst
    for (uint32_t burst_ix = 0; burst_ix < tokens_to_stream;
         burst_ix += MAX_BURST_LINES) {
      uint32_t chunk_size = ((burst_ix + MAX_BURST_LINES) >= tokens_to_stream)
                                ? (tokens_to_stream - burst_ix)
                                : MAX_BURST_LINES;
      for (uint32_t ix = 0; ix < chunk_size; ix++) {

        ASSERT(this->fifo_empty_n.read() == true,
               "Attempted to read from an empty fifo in an output stage\n");
        this->fifo_read.write(true);
        this->tokens_processed.write(this->tokens_processed.read() + 1);
        this->waitCycles(1);
        T_CPP token = this->fifo_peek.read();
        this->data_stream.write(token);
      }

      this->fifo_read.write(false);
      this->waitCycles(1);
    }
  }

  inline uint32_t evaluateAtomically() {

    return this->atomic_implementation->operator()(
        this->asPointer(this->data_buffer.read()), // The device buffer pointer
                                                   // (allocated else where)
        this->asPointer(
            this->meta_buffer.read()), // The device meta buffer pointer
                                       // (allocated else where)
        this->alloc_size.read(),       // Allocated size in words
        this->head.read(),             // head index of the data buffer
        this->tail.read(),             // tail index of the deta buffer
        this->data_stream.size(), // number of elements in the attached fifo
        this->data_stream,        // atomic data stream
        this->meta_stream         // atomic meta stream
    );
  }

  State nextState() override {
    switch (this->state.read()) {
    case State::BUSIF_IDLE:
      if (this->ap_start.read() == true)
        return State::BUSIF_STARTUP_DELAY;
      else
        return State::BUSIF_IDLE;
    case State::BUSIF_STARTUP_DELAY:
      return State::BUSIF_STREAM_CHUNK;
    case State::BUSIF_STREAM_CHUNK:
      return State::BUSIF_CALL_FUNCTION;
    case State::BUSIF_CALL_FUNCTION:
      return State::BUSIF_END_DELAY;
    case State::BUSIF_END_DELAY:
      return State::BUSIF_DONE;
    case State::BUSIF_DONE:
      return State::BUSIF_IDLE;
      break;
    default:
      PANIC("Invalid state reached in input stage!\n");
      break;
    }
  }
};
};     // namespace ap_rtl
#endif // __SIMULATION_IOSTAGE_H__