#ifndef __TRIGGER_H__
#define __TRIGGET_H__
#include "debug_macros.h"
#include "systemc.h"
#include <string>
namespace ap_rtl {
template <int SZ> class Trigger : public sc_module {
public:
  // Module interface
  sc_in_clk ap_clk;
  sc_in<bool> ap_rst_n;
  sc_in<bool> ap_start;
  sc_out<bool> ap_done;
  sc_out<bool> ap_idle;
  sc_out<bool> ap_ready;

  sc_in<bool> external_enqueue;
  sc_in<bool> all_sync;
  sc_in<bool> all_sync_wait;
  sc_in<bool> all_sleep;
  sc_in<bool> all_waited;

  sc_out<bool> sleep;
  sc_out<bool> sync_wait;
  sc_out<bool> sync_exec;
  sc_out<bool> waited;

  sc_in<uint32_t> actor_return;
  sc_in<bool> actor_done;
  sc_in<bool> actor_ready;
  sc_in<bool> actor_idle;
  sc_out<bool> actor_start;

  struct State {
    static const unsigned int IDLE_STATE = 0;
    static const unsigned int LAUNCH = 1;
    static const unsigned int CHECK = 2;
    static const unsigned int SLEEP = 3;
    static const unsigned int SYNC_LAUNCH = 4;
    static const unsigned int SYNC_CHECK = 5;
    static const unsigned int SYNC_WAIT = 6;
    static const unsigned int SYNC_EXEC = 7;
  };

  struct ReturnStatus {
    static const unsigned int IDLE = 0;
    static const unsigned int WAIT = 1;
    static const unsigned int TEST = 2;
    static const unsigned int EXECUTED = 3;
  };

  class ProfileStat {
  private:
    unsigned int action_id;
    // std::vector<unsigned int> clock_count;
    uint64_t total_ticks;
    uint64_t fire_counts;

  public:
    ProfileStat(){
      total_ticks = 0;
    };

    void setId(unsigned int id) { action_id = id; };

    void register_stat(unsigned long int count) {
      // clock_count.push_back(count);
      total_ticks += count;
      fire_counts ++;
    }
    uint64_t getTicks() {
      return total_ticks;
    }
    uint64_t getFirings() {
      return fire_counts;
    }

  };

  std::array<ProfileStat, SZ> stats;
  sc_signal<unsigned long int> clock_counter;

  sc_signal<uint16_t> action_sig;
  // Trigget state variable
  sc_signal<uint8_t> state;
  sc_signal<uint8_t> next_state;

  sc_trace_file *vcd_dump;

  SC_HAS_PROCESS(Trigger);

  /**
   * Proccess for setting the waited signal
   **/
  void lastWait() {
    while (true) {
      wait(); // wait for a positvie edge of the clock
      if (ap_rst_n.read() == false) {
        waited.write(false);
      } else if (actor_done.read() == true) {
        if (actor_return.read() == ReturnStatus::WAIT ||
            actor_return.read() == ReturnStatus::IDLE) {
          waited.write(true);
        } else {
          waited.write(false);
        }
      }
    }
  }

  /**
   * Process to reset the state
   */
  void setState() {
    while (true) {
      wait();
      if (ap_rst_n.read() == false) {
        state.write(State::IDLE_STATE);
      } else {
        state.write(next_state);
      }
    }
  }

  /**
   * A combinational block to get the next state
   * Sensitive to
   *  state,
   *  ap_start,
   *  external_enqueue,
   *  actor_done,
   *  actor_return,
   *  all_sync,
   *  all_sync_wait,
   *  all_sleep,
   *  all_waited,
   */
  void setNextState() {

    next_state = State::IDLE_STATE;

    uint8_t return_code = actor_return.read() & 0x00000003;

    switch (state.read()) {
    case State::IDLE_STATE:
      if (ap_start.read() == true)
        next_state = State::LAUNCH;
      else
        next_state = State::IDLE_STATE;
      break;

    case State::LAUNCH:
    case State::CHECK:
      if (actor_done.read() == true)
        if (return_code == ReturnStatus::EXECUTED ||
            return_code == ReturnStatus::TEST ||
            external_enqueue.read() == true)
          next_state = State::LAUNCH;
        else
          next_state = State::SLEEP;
      else
        next_state = State::CHECK;
      break;
    case State::SLEEP:
      if (all_sleep.read() == true)
        next_state = State::SYNC_LAUNCH;
      else if (all_waited.read() == false)
        next_state = State::LAUNCH;
      else
        next_state = State::SLEEP;
      break;

    case State::SYNC_LAUNCH:
    case State::SYNC_CHECK:
      if (actor_done.read() == true)
        if (return_code == ReturnStatus::EXECUTED)
          next_state = State::SYNC_EXEC;
        else if (return_code == ReturnStatus::TEST)
          next_state = State::SYNC_LAUNCH;
        else
          next_state = State::SYNC_WAIT;
      else
        next_state = State::SYNC_CHECK;
      break;

    case State::SYNC_WAIT:
      if (all_sync.read() == true)
        if (all_sync_wait.read() == true)
          next_state = State::IDLE_STATE;
        else
          next_state = State::LAUNCH;
      else
        next_state = State::SYNC_WAIT;
      break;

    case State::SYNC_EXEC:
      if (all_sync.read() == true)
        next_state = State::LAUNCH;
      else
        next_state = State::SYNC_EXEC;
      break;
    default:
      next_state = State::IDLE_STATE;
      break;
    }
  }

  void setWires() {

    // actor_start
    if (state.read() == State::LAUNCH || state.read() == State::SYNC_LAUNCH)
      actor_start.write(true);
    else
      actor_start.write(false);

    // ap_idle
    if (state.read() == State::IDLE_STATE)
      ap_idle.write(true);
    else
      ap_idle.write(false);

    // sleep
    if (state.read() == State::SLEEP || state.read() == State::IDLE_STATE)
      sleep.write(true);
    else
      sleep.write(false);

    // sync_wait
    if (state.read() == State::SYNC_WAIT || state.read() == State::IDLE_STATE)
      sync_wait.write(true);
    else
      sync_wait.write(false);

    // sync_exec
    if (state.read() == State::SYNC_EXEC)
      sync_exec.write(true);
    else
      sync_exec.write(false);

    // ap_done
    // ap_ready
    if (next_state.read() == State::IDLE_STATE) {
      ap_done.write(true);
      ap_ready.write(true);
    } else {
      ap_done.write(false);
      ap_ready.write(false);
    }
  }

  bool profileAction() {

    uint32_t ret_val = actor_return.read();

    uint32_t mask = (1 << 15) - 1;
    uint16_t action_id = (ret_val >> 17) & mask;
    uint8_t return_code = ret_val & 0x00000003;
    uint16_t action_count = (ret_val >> 2) & mask;

    if (actor_done.read() == true && return_code == ReturnStatus::EXECUTED) {

      DEBUG_MESSAGE("@ %s Action %5d took \t %10lu cycles in %s\n",
                    sc_time_stamp().to_string().c_str(), action_id,
                    clock_counter.read(), this->name());

      stats[action_id].register_stat(clock_counter.read() + 1);
      return true;
    }
    return false;
  }

  void countClocks() {
    while (true) {
      wait();
      if (state.read() == State::CHECK || state.read() == State::SYNC_CHECK ||
          state.read() == State::LAUNCH || state.read() == State::SYNC_LAUNCH) {

        if (profileAction())
          clock_counter = 0;
        else
          clock_counter = clock_counter + 1;
      } else
        clock_counter = 0;
    }
  }

  uint64_t getTotalTicks() {

    uint64_t total_ticks = 0;
    for (uint32_t id = 0; id < SZ; id++) {
      total_ticks += stats[id].getTicks();;
    }
    return total_ticks;
  }
  uint64_t getTotalFirings() {
    uint64_t total_firings = 0;
    for (uint32_t id = 0; id < SZ; id++) {
      total_firings += stats[id].getFirings();
    }
    return total_firings;
  }
  void action_set() { action_sig.write(actor_return.read() >> 17); }

  Trigger(sc_module_name name)
      : sc_module(name), state("state", State::IDLE_STATE),
        next_state("next_state", State::IDLE_STATE) {

    for (int i = 0; i < SZ; i++)
      stats[i].setId(i);

    SC_METHOD(setNextState);
    sensitive << state << ap_start << external_enqueue << actor_done
              << actor_return << all_sync << all_sync_wait << all_sleep
              << all_waited;
    SC_METHOD(setWires);
    sensitive << state << next_state;

    SC_THREAD(lastWait);
    sensitive << ap_clk.pos();

    SC_THREAD(setState);
    sensitive << ap_clk.pos();

    SC_THREAD(countClocks);
    sensitive << ap_clk.pos();

    SC_METHOD(action_set);
    sensitive << actor_return;
  }

  ~Trigger(){};
};
} // namespace ap_rtl
#endif // __TRIGGER_H__