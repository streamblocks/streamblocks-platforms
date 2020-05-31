#ifndef __TRIGGER_H__
#define __TRIGGET_H__
#include "systemc.h"

namespace ap_rtl {
template <int SZ> class Trigger : public sc_module {
public:
  // Module interface
  sc_in_clk ap_clk;
  sc_in<sc_logic> ap_rst_n;
  sc_in<sc_logic> ap_start;
  sc_out<sc_logic> ap_done;
  sc_out<sc_logic> ap_idle;
  sc_out<sc_logic> ap_ready;

  sc_in<sc_logic> external_enqueue;
  sc_in<sc_logic> all_sync;
  sc_in<sc_logic> all_sync_wait;
  sc_in<sc_logic> all_sleep;
  sc_in<sc_logic> all_waited;

  sc_out<sc_logic> sleep;
  sc_out<sc_logic> sync_wait;
  sc_out<sc_logic> sync_exec;
  sc_out<sc_logic> waited;

  sc_in<sc_lv<32>> actor_return;
  sc_in<sc_logic> actor_done;
  sc_in<sc_logic> actor_ready;
  sc_in<sc_logic> actor_idle;
  sc_out<sc_logic> actor_start;

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
    std::vector<unsigned int> clock_count;

  public:
    ProfileStat(){

    };

    void setId(unsigned int id) { action_id = id; };

    void register_stat(unsigned long int count) {
      clock_count.push_back(count);
    }
  };

  std::array<ProfileStat, SZ> stats;
  sc_signal<unsigned long int> clock_counter;

  // Trigget state variable
  sc_signal<sc_lv<3>> state;
  sc_signal<sc_lv<3>> next_state;

  sc_trace_file *vcd_dump;

  SC_HAS_PROCESS(Trigger);

  /**
   * Proccess for setting the waited signal
   **/
  void lastWait() {
    while (true) {
      wait(); // wait for a positvie edge of the clock
      if (ap_rst_n.read() == SC_LOGIC_0) {
        waited.write(SC_LOGIC_0);
      } else if (actor_done.read() == SC_LOGIC_1) {
        if (actor_return.read() == ReturnStatus::WAIT ||
            actor_return.read() == ReturnStatus::IDLE) {
          waited.write(SC_LOGIC_1);
        } else {
          waited.write(SC_LOGIC_0);
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
      if (ap_rst_n.read() == SC_LOGIC_0) {
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

    sc_lv<32> return_code = actor_return.read().range(1, 0);

    switch (state.read().to_uint()) {
    case State::IDLE_STATE:
      if (ap_start.read() == SC_LOGIC_1)
        next_state = State::LAUNCH;
      else
        next_state = State::IDLE_STATE;
      break;

    case State::LAUNCH:
    case State::CHECK:
      if (actor_done.read() == SC_LOGIC_1)
        if (return_code == ReturnStatus::EXECUTED ||
            return_code == ReturnStatus::TEST ||
            external_enqueue.read() == SC_LOGIC_1)
          next_state = State::LAUNCH;
        else
          next_state = State::SLEEP;
      else
        next_state = State::CHECK;
      break;
    case State::SLEEP:
      if (all_sleep.read() == SC_LOGIC_1)
        next_state = State::SYNC_LAUNCH;
      else if (all_waited.read() == SC_LOGIC_0)
        next_state = State::LAUNCH;
      else
        next_state = State::SLEEP;
      break;

    case State::SYNC_LAUNCH:
    case State::SYNC_CHECK:
      if (actor_done.read() == SC_LOGIC_1)
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
      if (all_sync.read() == SC_LOGIC_1)
        if (all_sync_wait.read() == SC_LOGIC_1)
          next_state = State::IDLE_STATE;
        else
          next_state = State::LAUNCH;
      else
        next_state = State::SYNC_WAIT;
      break;

    case State::SYNC_EXEC:
      if (all_sync.read() == SC_LOGIC_1)
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
      actor_start.write(SC_LOGIC_1);
    else
      actor_start.write(SC_LOGIC_0);

    // ap_idle
    if (state.read() == State::IDLE_STATE)
      ap_idle.write(SC_LOGIC_1);
    else
      ap_idle.write(SC_LOGIC_0);

    // sleep
    if (state.read() == State::SLEEP || state.read() == State::IDLE_STATE)
      sleep.write(SC_LOGIC_1);
    else
      sleep.write(SC_LOGIC_0);

    // sync_wait
    if (state.read() == State::SYNC_WAIT || state.read() == State::IDLE_STATE)
      sync_wait.write(SC_LOGIC_1);
    else
      sync_wait.write(SC_LOGIC_0);

    // sync_exec
    if (state.read() == State::SYNC_EXEC)
      sync_exec.write(SC_LOGIC_1);
    else
      sync_exec.write(SC_LOGIC_0);

    // ap_done
    // ap_ready
    if (next_state.read() == State::IDLE_STATE) {
      ap_done.write(SC_LOGIC_1);
      ap_ready.write(SC_LOGIC_1);
    } else {
      ap_done.write(SC_LOGIC_0);
      ap_ready.write(SC_LOGIC_0);
    }
  }

  bool profileAction() {

    sc_lv<15> action_id = actor_return.read().range(31, 17);
    sc_lv<2> return_code = actor_return.read().range(1, 0);
    sc_lv<15> action_count = actor_return.read().range(16, 2);

    if (actor_done.read() == SC_LOGIC_1 &&
        return_code == ReturnStatus::EXECUTED) {

      std::cout << "@ " << sc_time_stamp() << " "<< this->name() << "::action[" << action_id.to_uint()
                << "] took " << clock_counter.read() + 1 << " cycles" << std::endl;
      stats[action_id.to_uint()].register_stat(clock_counter.read() + 1);
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
      }
      else
        clock_counter = 0;
    }
  }
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

    ;
  }

  ~Trigger(){};
};
} // namespace ap_rtl
#endif // __TRIGGER_H__