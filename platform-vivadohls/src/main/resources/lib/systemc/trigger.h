#ifndef __TRIGGER_H__
#define __TRIGGER_H__

#include "profiler.h"
#include <array>
#include <systemc>

namespace streamblocks_rtl {
class AbstractTrigger : public sc_core::sc_module {
public:
  sc_core::sc_in_clk ap_clk;
  sc_core::sc_in<bool> ap_rst_n;
  sc_core::sc_in<bool> ap_start;
  sc_core::sc_out<bool> ap_done;
  sc_core::sc_out<bool> ap_idle;
  sc_core::sc_out<bool> ap_ready;

  sc_core::sc_in<bool> all_sleep;
  sc_core::sc_in<bool> all_sync_sleep;
  sc_core::sc_in<bool> all_waited;

  sc_core::sc_out<bool> sleep;
  sc_core::sc_out<bool> sync_sleep;
  sc_core::sc_out<bool> waited;

  sc_core::sc_in<uint32_t> actor_return;
  sc_core::sc_in<bool> actor_done;
  sc_core::sc_in<bool> actor_ready;
  sc_core::sc_in<bool> actor_idle;
  sc_core::sc_out<bool> actor_start;

protected:
  enum State : int {
    IDLE_STATE = 0,
    LAUNCH = 1,
    FLUSH = 2,
    SLEEP = 3,
    SYNC_LAUNCH = 4,
    SYNC_FLUSH = 5,
    SYNC_SLEEP = 6
  };

  // a clock value tagged with return code
  struct TaggedClock {
    sc_core::sc_signal<uint32_t> return_code;
    sc_core::sc_signal<bool> start;
  };

  TaggedClock tagged_clock;

  const std::string actor_id;

  using StateProfile =
      std::array<uint64_t, State::SYNC_SLEEP - State::IDLE_STATE + 1>;
  std::unique_ptr<ActorProfiler> actor_profiler;

  const bool enable_profiler;

  void profileActor();

public:
  // internal signals

  sc_core::sc_signal<uint64_t> clock_counter;

  sc_core::sc_signal<uint8_t> state,
      next_state; // type of state should not be State, otherwise the waveforms
                  // would be weired.

  SC_HAS_PROCESS(AbstractTrigger);
  AbstractTrigger(sc_core::sc_module_name name, const std::string actor_id,
                  const bool enable_profiler = true);

  /**
   * @brief Get the return code value
   *
   * @return uint32_t
   */
  inline uint32_t getReturnCode();

  /**
   * @brief @c [SC_CTHREAD] Set the Last Wait output
   * The @c waited output signal indicates the value of the last finished
   * invocation
   */

  void setLastWait();
  /**
   * @brief @c [SC_CTHREAD] Set the state
   *
   */
  void setState();

  /**
   * @brief @c [SC_METHOD] Set the next state signal
   *
   */
  virtual void setNextState() = 0;

  /**
   * @brief @c [SC_METHOD] Set the output wires
   *
   */
  void setWires();

  /**
   * @brief enable vcd traces
   *
   * @param vcd @c sc_trace_file pointer to the vcd object
   */
  virtual void enableTrace(sc_core::sc_trace_file *vcd);
  /**
   * @brief Register and action for profiling
   *
   * @param ix action index
   * @param action_id  action h
   */
  void registerAction(const unsigned int ix, const std::string &action_id);

  void dumpStats(std::ofstream &ofs, int indent = 1);
};

class Trigger : public AbstractTrigger {

public:
  SC_HAS_PROCESS(Trigger);
  Trigger(sc_core::sc_module_name name, const std::string actor_id,
          const bool enable_profiler = true);
  /**
   * @brief Sets the next state signal
   *
   */
  void setNextState() override;
};

class PipelinedTrigger : public AbstractTrigger {
public:
  SC_HAS_PROCESS(PipelinedTrigger);
  PipelinedTrigger(sc_core::sc_module_name name, const std::string actor_id,
                   const bool enable_profiler = true,
                   const uint32_t max_outstanding = 1);

  sc_core::sc_signal<uint32_t> outstanding_invocations, next_outstanding;

private:
  const uint32_t max_outstanding;

public:
  void enableTrace(sc_core::sc_trace_file *vcd_dump) override;

  /**
   * @brief @c [SC_METHOD] Set the Next State object
   *
   */
  void setNextState() override;

  /**
   * @brief @c [SC_CTHREAD] Set the outstanding count
   *
   */
  void setOutstanding();
};
} // namespace streamblocks_rtl
#endif // __TRIGGER_H__