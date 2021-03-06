#ifndef __TRIGGER_H__
#define __TRIGGER_H__
#include "debug_macros.h"
#include "systemc.h"
#include <string>
namespace ap_rtl {

enum ReturnStatus { IDLE = 0, WAIT = 1, TEST = 2, EXECUTED = 3 };

class Trigger : public sc_module {
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

  enum State {
    IDLE_STATE = 0,
    LAUNCH = 1,
    CHECK = 2,
    SLEEP = 3,
    SYNC_LAUNCH = 4,
    SYNC_CHECK = 5,
    SYNC_WAIT = 6,
    SYNC_EXEC = 7
  };

  class ProfileStat {
  private:
    const unsigned int action_ix;
    const std::string action_id;
    uint64_t total_ticks;
    uint64_t fire_counts;
    uint64_t min_ticks;
    uint64_t max_ticks;

  public:
    ProfileStat(unsigned int ix, std::string id)
        : action_ix(ix), action_id(id) {
      fire_counts = 0;
      total_ticks = 0;
      min_ticks = -1;
      max_ticks = 0;
    };

    std::string getActionId() const { return action_id; }
    void register_stat(unsigned long int count) {
      // clock_count.push_back(count);
      total_ticks += count;
      if (count < min_ticks)
        min_ticks = count;
      if (count > max_ticks)
        max_ticks = count;
      fire_counts++;
    }

    uint64_t getFirings() const { return fire_counts; }
    uint64_t getTicks() const { return total_ticks; }
    uint64_t getMinTicks() const { return min_ticks; }
    uint64_t getMaxTicks() const { return max_ticks; }
  };

  const std::string actor_id;
  std::vector<ProfileStat> stats;

  // Action clock counter (TEST)*(EXEC) cycles
  sc_signal<unsigned long int> clock_counter;

  // Counters to count the number of clocks spent in each state
  std::array<unsigned long int, State::SYNC_EXEC - State::IDLE_STATE + 1>
      state_cycles;

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

    next_state.write(State::IDLE_STATE);

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
          next_state.write(State::LAUNCH);
        else
          next_state.write(State::SLEEP);
      else
        next_state.write(State::CHECK);
      break;
    case State::SLEEP:
      if (all_sleep.read() == true)
        next_state.write(State::SYNC_LAUNCH);
      else if (all_waited.read() == false)
        next_state.write(State::LAUNCH);
      else
        next_state.write(State::SLEEP);
      break;

    case State::SYNC_LAUNCH:
    case State::SYNC_CHECK:
      if (actor_done.read() == true)
        if (return_code == ReturnStatus::EXECUTED)
          next_state.write(State::SYNC_EXEC);
        else if (return_code == ReturnStatus::TEST)
          next_state.write(State::SYNC_LAUNCH);
        else
          next_state.write(State::SYNC_WAIT);
      else
        next_state.write(State::SYNC_CHECK);
      break;

    case State::SYNC_WAIT:
      if (all_sync.read() == true)
        if (all_sync_wait.read() == true)
          next_state.write(State::IDLE_STATE);
        else
          next_state.write(State::LAUNCH);
      else
        next_state.write(State::SYNC_WAIT);
      break;

    case State::SYNC_EXEC:
      if (all_sync.read() == true)
        next_state.write(State::LAUNCH);
      else
        next_state.write(State::SYNC_EXEC);
      break;
    default:
      PANIC("Invalid trigger state!");
      next_state.write(State::IDLE_STATE);
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

    if (actor_done.read() == true && return_code == ReturnStatus::EXECUTED &&
        action_id < stats.size()) {
      auto _c = clock_counter.read();
      stats[action_id].register_stat(_c == 0 ? 1 : _c);
      return true;
    }
    return false;
  }

  void countClocks() {
    while (true) {
      wait();
      state_cycles[state.read()]++;
      // Profile an action
      if (state.read() == State::CHECK || state.read() == State::SYNC_CHECK ||
          state.read() == State::LAUNCH || state.read() == State::SYNC_LAUNCH) {

        if (profileAction())
          clock_counter = 0;
        else
          clock_counter = clock_counter + 1;
      } else {
        clock_counter = 0;
      }
    }
  }

  void actionSet() { action_sig.write(actor_return.read() >> 17); }

  Trigger(sc_module_name name, std::string actor_id)
      : sc_module(name), actor_id(actor_id), state("state", State::IDLE_STATE),
        next_state("next_state", State::IDLE_STATE) {
    // initialize the state counters
    for (auto &counter : state_cycles) {
      counter = 0;
    }
    clock_counter = 0;

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

    SC_METHOD(actionSet);
    sensitive << actor_return;
  }
  void registerAction(unsigned int ix, std::string action_id) {
    ASSERT(ix <= stats.size(), "Error registering action");
    stats.push_back(ProfileStat(ix, action_id));
    // stats[ix].setId(ix, action_id);
  }

  /**
   * get total ticks of actor, which is the sum of the total ticks of its
   * actions
   */
  uint64_t getTotalTicks() {

    uint64_t total_ticks = 0;

    for (const auto &stat : stats) {
      total_ticks += stat.getTicks();
    }
    return total_ticks;
  }
  /**
   * get total firings of the actor, which is the sum of firings of its actions
   */
  uint64_t getTotalFirings() {
    uint64_t total_firings = 0;
    for (const auto &stat : stats) {
      total_firings += stat.getFirings();
    }
    return total_firings;
  }

  void dumpStats(std::ofstream &ofs, int indent = 1) {
    std::string indent_str = "";
    for (int i = 0; i < indent; i++)
      indent_str = indent_str + "\t";

    auto total_actor_ticks = this->getTotalTicks();
    auto total_actor_firings = this->getTotalFirings();
    double actor_average_ticks =
        double(total_actor_ticks) / double(total_actor_firings);
    // Log the actor cycle stats
    ofs << indent_str << "<actor id=\"" << this->actor_id << "\" clockcycles=\""
        << actor_average_ticks << "\" clockcycles-total=\"" << total_actor_ticks
        << "\" firings=\"" << total_actor_firings << "\" >" << std::endl;
    // Log the trigger cycle count
    ofs << indent_str << "\t<trigger ";
    ofs << "IDLE_STATE=\"" << this->state_cycles[State::IDLE_STATE] << "\" ";
    ofs << "LAUNCH=\"" << this->state_cycles[State::LAUNCH] << "\" ";
    ofs << "CHECK=\"" << this->state_cycles[State::CHECK] << "\" ";
    ofs << "SLEEP=\"" << this->state_cycles[State::SLEEP] << "\" ";
    ofs << "SYNC_LAUNCH=\"" << this->state_cycles[State::SYNC_LAUNCH] << "\" ";
    ofs << "SYNC_CHECK=\"" << this->state_cycles[State::SYNC_CHECK] << "\" ";
    ofs << "SYNC_WAIT=\"" << this->state_cycles[State::SYNC_WAIT] << "\" ";
    ofs << "SYNC_EXEC=\"" << this->state_cycles[State::SYNC_EXEC] << "\" />"
        << std::endl;
    // Log action cycle
    for (const auto &stat : this->stats) {
      auto total_ticks = stat.getTicks();
      auto min_ticks = stat.getMinTicks() != -1 ? stat.getMinTicks() : 0;
      auto max_ticks = stat.getMaxTicks();
      auto firings = stat.getFirings();
      double average_ticks =
          firings > 0 ? double(total_ticks) / double(firings) : 0;
      auto action_id = stat.getActionId();
      ofs << indent_str << "\t<action id=\"" << action_id << "\" clockcycles=\""
          << average_ticks << "\" clockcycles-min=\"" << min_ticks
          << "\" clockcycles-max=\"" << max_ticks << "\" clockcycles-total=\""
          << total_ticks << "\" firings=\"" << firings << "\"/>" << std::endl;
    }

    ofs << indent_str << "</actor>" << std::endl;
  }

  ~Trigger(){};
};
} // namespace ap_rtl
#endif // __TRIGGER_H__