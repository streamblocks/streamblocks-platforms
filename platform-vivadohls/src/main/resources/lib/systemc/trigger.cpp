#include "trigger.h"
#include "common.h"
#include <fstream>

namespace streamblocks_rtl {

AbstractTrigger::AbstractTrigger(sc_core::sc_module_name name,
                                 const std::string actor_id,
                                 const bool enable_profiler)
    : sc_module(name), CTOR_PORT(ap_clk), CTOR_PORT(ap_rst_n),
      CTOR_PORT(ap_start), CTOR_PORT(ap_done), CTOR_PORT(ap_idle),
      CTOR_PORT(ap_ready), CTOR_PORT(all_sleep), CTOR_PORT(all_sync_sleep),
      CTOR_PORT(all_waited), CTOR_PORT(sleep), CTOR_PORT(sync_sleep),
      CTOR_PORT(waited), CTOR_PORT(actor_return), CTOR_PORT(actor_ready),
      CTOR_PORT(actor_idle), CTOR_PORT(actor_done), CTOR_PORT(actor_start),
      actor_id(actor_id), CTOR_SIG(clock_counter, 0),
      CTOR_SIG(state, AbstractTrigger::State::IDLE_STATE),
      CTOR_SIG(next_state, AbstractTrigger::State::IDLE_STATE),
      enable_profiler(enable_profiler) {

  if (enable_profiler)
    actor_profiler = std::make_unique<ActorProfiler>(actor_id);
  SC_METHOD(setWires);
  sensitive << state;

  SC_CTHREAD(setLastWait, ap_clk.pos());

  SC_CTHREAD(setState, ap_clk.pos());
}
uint32_t AbstractTrigger::getReturnCode() {
  return actor_return.read() & 0x00000003;
}
void AbstractTrigger::setLastWait() {
  while (true) {
    wait(); // wait for clock
    if (ap_rst_n.read() == false) {
      waited.write(false);
    } else if (actor_done.read() == true) {

      if (actor_return.read() == ReturnStatus::WAIT) {
        waited.write(true);
      } else {
        waited.write(false);
      }
    }
  }
}

void AbstractTrigger::setState() {
  while (true) {
    wait();
    if (ap_rst_n.read() == false) {
      state.write(State::IDLE_STATE);
      clock_counter.write(0);
    } else {
      state.write(next_state);
      clock_counter.write(clock_counter.read() + 1);
      profileActor();
    }
  }
}

void AbstractTrigger::profileActor() {
  if (actor_profiler) {

    // profile the actions and wait
    if (tagged_clock.start) {
      actor_profiler->start(clock_counter.read());
    }

    uint32_t ret_val = tagged_clock.return_code;

    uint32_t mask = (1 << 15) - 1;
    uint16_t action_id = (ret_val >> 17) & mask;
    uint8_t return_code = ret_val & 0x00000003;
    uint16_t action_count = (ret_val >> 2) & mask;

    if (return_code == ReturnStatus::EXECUTED) {
      actor_profiler->end(action_id, clock_counter.read() - 1);

    } else if (return_code == ReturnStatus::WAIT) {
      actor_profiler->discard(clock_counter.read() - 1);
    }

    // syncronization profile
    bool kernel_start = (state.read() == State::IDLE_STATE &&
                         next_state.read() == State::LAUNCH);

    if (kernel_start) {
      actor_profiler->kernelStart(clock_counter.read());
    }

    const uint64_t current_ticks = clock_counter.read();

    bool sleep_start =
        state.read() == State::LAUNCH && next_state.read() == State::SLEEP;
    bool sleep_end = state.read() == State::SLEEP &&
                     (next_state.read() == State::SYNC_LAUNCH ||
                      next_state.read() == State::LAUNCH);
    if (sleep_start) {
      actor_profiler->syncStart(current_ticks, true);
    }
    if (sleep_end) {
      actor_profiler->syncEnd(current_ticks, true);
    }

    bool sync_start = (state.read() == State::SLEEP &&
                       next_state.read() == State::SYNC_LAUNCH);
    bool sync_end = (state.read() == State::SYNC_SLEEP &&
                     (next_state.read() == State::IDLE_STATE ||
                      next_state.read() == State::LAUNCH));
    if (sync_start) {
      actor_profiler->syncStart(clock_counter.read(), false);
    }


    if (sync_end) {
      actor_profiler->syncEnd(clock_counter.read(), false);
    }

    bool kernel_end = (state.read() == State::SYNC_SLEEP &&
                       next_state.read() == State::IDLE_STATE);
    if (kernel_end) {
      actor_profiler->kernelEnd(clock_counter.read());
    }
  }
}
void AbstractTrigger::setWires() {

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

  if (state.read() == State::SYNC_SLEEP || state.read() == State::IDLE_STATE)
    sync_sleep.write(true);
  else
    sync_sleep.write(false);

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

void AbstractTrigger::enableTrace(sc_core::sc_trace_file *vcd_dump) {
  TRACE_SIGNAL(vcd_dump, ap_clk);
  TRACE_SIGNAL(vcd_dump, ap_rst_n);
  TRACE_SIGNAL(vcd_dump, ap_start);
  TRACE_SIGNAL(vcd_dump, ap_done);
  TRACE_SIGNAL(vcd_dump, ap_idle);
  TRACE_SIGNAL(vcd_dump, ap_ready);
  TRACE_SIGNAL(vcd_dump, all_sleep);
  TRACE_SIGNAL(vcd_dump, all_sync_sleep);
  TRACE_SIGNAL(vcd_dump, all_waited);
  TRACE_SIGNAL(vcd_dump, sleep);
  TRACE_SIGNAL(vcd_dump, sync_sleep);
  TRACE_SIGNAL(vcd_dump, waited);
  TRACE_SIGNAL(vcd_dump, actor_return);
  TRACE_SIGNAL(vcd_dump, actor_ready);
  TRACE_SIGNAL(vcd_dump, actor_idle);
  TRACE_SIGNAL(vcd_dump, actor_done);
  TRACE_SIGNAL(vcd_dump, actor_start);

  TRACE_SIGNAL(vcd_dump, clock_counter);
  TRACE_SIGNAL(vcd_dump, state);
  TRACE_SIGNAL(vcd_dump, next_state);
  TRACE_SIGNAL(vcd_dump, tagged_clock.return_code);
  TRACE_SIGNAL(vcd_dump, tagged_clock.start);
}

void AbstractTrigger::registerAction(const unsigned int ix,
                                     const std::string &action_id) {
  if (actor_profiler) {
    actor_profiler->registerAction(ix, action_id);
  }
}

void AbstractTrigger::dumpStats(std::ofstream &ofs, int indent) {

  if (actor_profiler) {
    ofs << actor_profiler->serialized(indent);
  }
}

Trigger::Trigger(sc_core::sc_module_name name, const std::string actor_id,
                 const bool enable_profiler)
    : AbstractTrigger(name, actor_id, enable_profiler) {
  SC_METHOD(setNextState);
  sensitive << state << clock_counter << ap_start << actor_start << actor_done
            << actor_ready << actor_return << all_sync_sleep << all_sleep
            << all_waited;
}
void Trigger::setNextState() {

  next_state.write(State::IDLE_STATE);
  tagged_clock.return_code = ReturnStatus::IDLE;
  tagged_clock.start = false;

  uint8_t return_code = actor_return.read() & 0x00000003;

  switch (state.read()) {
  case State::IDLE_STATE:
    if (ap_start.read() == true) {
      next_state = State::LAUNCH;

      tagged_clock.start = true;

    } else
      next_state = State::IDLE_STATE;
    break;

  case State::LAUNCH:
    if (actor_done.read() == true) {
      tagged_clock.return_code = actor_return.read();

      if (return_code != ReturnStatus::WAIT || all_waited.read() == false) {
        next_state.write(State::LAUNCH);

        tagged_clock.start = true;
      } else {

        next_state.write(State::SLEEP);
      }
    } else {
      next_state.write(State::LAUNCH);
    }
    break;
  case State::SLEEP:
    if (all_sleep.read() == true) {
      next_state.write(State::SYNC_LAUNCH);

      tagged_clock.start = true;

    } else if (all_waited.read() == false) {
      next_state.write(State::LAUNCH);

      tagged_clock.start = true;

    } else {
      next_state.write(State::SLEEP);
    }
    break;

  case State::SYNC_LAUNCH:
    if (actor_done.read() == true) {
      tagged_clock.return_code = actor_return.read();

      next_state.write(State::SYNC_SLEEP);

    } else {
      next_state.write(State::SYNC_LAUNCH);
    }
    break;

  case State::SYNC_SLEEP:
    if (all_sync_sleep.read() == true)
      if (all_waited.read() == true) {

        next_state.write(State::IDLE_STATE);

      } else {
        tagged_clock.start = true;

        next_state.write(State::LAUNCH);
      }
    else {
      next_state.write(State::SYNC_SLEEP);
    }
    break;
  default:
    PANIC("Invalid trigger state!");
    next_state.write(State::IDLE_STATE);
    break;
  }
}

PipelinedTrigger::PipelinedTrigger(sc_core::sc_module_name name,
                                   const std::string actor_id,
                                   const bool enable_profiler,
                                   const uint32_t max_outstanding)
    : AbstractTrigger(name, actor_id, enable_profiler),
      CTOR_SIG(outstanding_invocations, 1), max_outstanding(max_outstanding) {
  SC_CTHREAD(setOutstanding, ap_clk.pos());

  SC_METHOD(setNextState);
  sensitive << state << clock_counter << ap_start << actor_start << actor_done
            << actor_ready << actor_return << all_sync_sleep << all_sleep
            << all_waited << outstanding_invocations;
}

void PipelinedTrigger::enableTrace(sc_core::sc_trace_file *vcd_dump) {
  AbstractTrigger::enableTrace(vcd_dump);
  TRACE_SIGNAL(vcd_dump, outstanding_invocations);
  TRACE_SIGNAL(vcd_dump, next_outstanding);
}

void PipelinedTrigger::setNextState() {
  next_state.write(State::IDLE_STATE);
  next_outstanding.write(-1);
  uint8_t return_code = actor_return.read() & 0x00000003;
  tagged_clock.return_code = ReturnStatus::IDLE;
  tagged_clock.start = false;

  switch (state.read()) {
  case State::IDLE_STATE:
    next_outstanding.write(1);
    if (ap_start.read() == true) {

      next_state = State::LAUNCH;
      tagged_clock.start = true;

    } else
      next_state = State::IDLE_STATE;
    break;

  case State::LAUNCH:
    if (actor_done.read() == true) {
      tagged_clock.return_code = return_code;
      // ASSERT(first_invocation.read() == false,
      //        "Unexpected actor_done pulse in %s!\n", this->name());
      if (return_code == ReturnStatus::WAIT && all_waited.read() == true) {

        next_outstanding.write(outstanding_invocations.read() - 1);
        if (outstanding_invocations.read() == 1) {
          next_state.write(State::SLEEP);
        } else {
          next_state.write(State::FLUSH);
        }
      } else {
        next_state.write(State::LAUNCH);

        if (actor_ready.read() == true) {
          tagged_clock.start = true;
          next_outstanding.write(outstanding_invocations.read());
        } else {
          next_outstanding.write(outstanding_invocations.read() - 1);
        }
      }
    } else { // actor_done.read() == false
      next_state.write(State::LAUNCH);
      if (actor_ready.read() == true) {
        tagged_clock.start = true;
        next_outstanding.write(outstanding_invocations.read() + 1);
      } else {
        next_outstanding.write(outstanding_invocations.read());
      }
    }

    break;
  case State::FLUSH:
    if (actor_done.read() == true) {
      /**
       * At the FLUSH state, we can only go to LAUNCH if (all_waited == false
       * || returnc_code != WAIT), now in this case, we also have to make sure
       * if outstanding count is 1, then it should not be decremented to 0.
       *
       */
      tagged_clock.return_code = return_code;
      bool should_relauch =
          (all_waited.read() == false || return_code != ReturnStatus::WAIT);
      if (outstanding_invocations.read() == 1) {
        // we have just received the last invocation
        if (should_relauch) {
          next_state.write(State::LAUNCH);
          tagged_clock.start = true;
        } else {
          next_state.write(State::SLEEP);
        }
        next_outstanding.write(1);
      } else { // outstanding_invocations > 1
        if (should_relauch) {
          tagged_clock.start = true;
          next_state.write(State::LAUNCH);
          next_outstanding.write(outstanding_invocations.read());
        } else {
          next_state.write(State::FLUSH);
          next_outstanding.write(outstanding_invocations.read() - 1);
        }
      }
    } else {
      next_state.write(State::FLUSH);
      next_outstanding.write(outstanding_invocations.read());
    }
    break;
  case State::SLEEP:
    // ASSERT(outstanding_invocations.read() == 0, "Outstanding invok")
    if (all_sleep.read() == true) {
      next_state.write(State::SYNC_LAUNCH);
      tagged_clock.start = true;
    } else if (all_waited.read() == false) {
      tagged_clock.start = true;
      next_state.write(State::LAUNCH);
    } else
      next_state.write(State::SLEEP);
    next_outstanding.write(1);
    break;

  case State::SYNC_LAUNCH:
  case State::SYNC_FLUSH:
    if (actor_done.read() == true) {
      tagged_clock.return_code = return_code;
      next_state.write(State::SYNC_SLEEP);
    } else {
      next_state.write(State::SYNC_FLUSH);
    }
    next_outstanding.write(1);
    break;
  case State::SYNC_SLEEP:
    if (all_sync_sleep.read() == true) {
      if (all_waited.read() == true)
        next_state.write(State::IDLE_STATE);
      else {
        tagged_clock.start = true;
        next_state.write(State::LAUNCH);
      }
    } else {
      next_state.write(State::SYNC_SLEEP);
    }
    next_outstanding.write(1);
    break;
  default:
    PANIC("Invalid trigger state!");
    next_state.write(State::IDLE_STATE);
    next_outstanding.write(1);
    break;
  }
}

void PipelinedTrigger::setOutstanding() {
  while (true) {
    wait();
    outstanding_invocations.write(next_outstanding.read());
  }
}

}; // namespace streamblocks_rtl