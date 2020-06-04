#ifndef __QUEUE_H__
#define __QUEUE_H__

#include "debug_macros.h"
#include "systemc.h"
#include <assert.h>
#include <fstream>
#include <iostream>
#include <vector>

namespace ap_rtl {

template <typename T, int LOG2_DEPTH> class Queue : public sc_module {
public:
  static const unsigned int ADDR_WIDTH = LOG2_DEPTH;
  static const unsigned int FIFO_DEPTH = 1 << LOG2_DEPTH;
  sc_core::sc_in_clk ap_clk;
  sc_core::sc_in<bool> ap_rst_n;
  sc_core::sc_out<bool> empty_n;

  sc_core::sc_in<bool> read;
  sc_core::sc_out<T> dout;

  sc_core::sc_out<bool> full_n;

  sc_core::sc_in<bool> write;
  sc_core::sc_in<T> din;

  // -- Extended io
  sc_core::sc_out<uint32_t> count;
  sc_core::sc_out<uint32_t> size;
  sc_core::sc_out<T> peek;

  sc_core::sc_signal<bool> internal_empty_n;
  sc_core::sc_signal<bool> internal_full_n;

  sc_core::sc_signal<T> mStorage[FIFO_DEPTH];
  sc_core::sc_signal<sc_dt::sc_uint<ADDR_WIDTH>> mInPtr;
  sc_core::sc_signal<sc_dt::sc_uint<ADDR_WIDTH>> mOutPtr;
  sc_core::sc_signal<sc_dt::sc_uint<1>> mFlag_nEF_hint;

  SC_HAS_PROCESS(Queue);
  Queue(sc_module_name name)
      : sc_module(name), ap_clk("ap_clk"), ap_rst_n("rst_n"),
        empty_n("empty_n"), read("read"), dout("dout"), full_n("full_n"),
        write("write"), din("din"), count("count"), size("size"), peek("peek") {

    mInPtr = 0;
    mOutPtr = 0;
    mFlag_nEF_hint = 0;

    SC_METHOD(proc_read_write);
    sensitive << ap_clk.pos();

    SC_METHOD(proc_dout);
    sensitive << mOutPtr;
    for (unsigned i = 0; i < FIFO_DEPTH; i++) {
      sensitive << mStorage[i];
    }

    SC_METHOD(proc_ptr);
    sensitive << mInPtr << mOutPtr << mFlag_nEF_hint;

    SC_METHOD(proc_status);
    sensitive << internal_empty_n << internal_full_n;
  }

  void proc_status() {

    empty_n.write(internal_empty_n.read());
    full_n.write(internal_full_n.read());
  }

  void proc_read_write() {
    size.write(1 << LOG2_DEPTH);
    if (ap_rst_n.read() == false) {
      mInPtr.write(0);
      mOutPtr.write(0);
      mFlag_nEF_hint.write(0);
      count.write(0);
    } else {
      uint32_t new_count = count.read();

      DEBUG_ASSERT(!(read.read() == true && internal_empty_n.read() == false),
                   "@ %s trying to read from an empty fifo %s\n",
                   sc_time_stamp().to_string().c_str(), this->name());

      if (read.read() == true && internal_empty_n.read() == true) {
        sc_dt::sc_uint<ADDR_WIDTH> ptr;
        if (mOutPtr.read().to_uint() == (FIFO_DEPTH - 1)) {
          ptr = 0;
          mFlag_nEF_hint.write(~mFlag_nEF_hint.read());
        } else {
          ptr = mOutPtr.read();
          ptr++;
        }

        new_count--;

        DEBUG_ASSERT(ptr.to_uint() < FIFO_DEPTH,
                     "@ %s fifo pointer overflow in %s\n",
                     sc_time_stamp().to_string().c_str(), this->name());

        mOutPtr.write(ptr);
      }

      DEBUG_ASSERT(!(write.read() == true && internal_full_n.read() == false),
                   "@ %s trying to write to a full fifo %s\n",
                   sc_time_stamp().to_string().c_str(), this->name());

      if (write.read() == true && internal_full_n.read() == true) {
        sc_dt::sc_uint<ADDR_WIDTH> ptr;
        ptr = mInPtr.read();
        mStorage[ptr.to_uint()].write(din.read());
        if (ptr.to_uint() == (FIFO_DEPTH - 1)) {
          ptr = 0;
          mFlag_nEF_hint.write(~mFlag_nEF_hint.read());
        } else {
          ptr++;
          DEBUG_ASSERT(ptr.to_uint() < FIFO_DEPTH,
                       "@ %s fifo pointer overflow in %s\n",
                       sc_time_stamp().to_string().c_str(), this->name());
        }

        new_count++;

        mInPtr.write(ptr);
      }
      count.write(new_count);
    }
  }

  void proc_dout() {
    sc_dt::sc_uint<ADDR_WIDTH> ptr = mOutPtr.read();
    if (ptr.to_uint() > FIFO_DEPTH) {
      dout.write(0);
      peek.write(0);
    } else {
      dout.write(mStorage[ptr.to_uint()]);
      peek.write(mStorage[ptr.to_uint()]);
    }
  }

  void proc_ptr() {
    if (mInPtr.read() == mOutPtr.read() &&
        mFlag_nEF_hint.read().to_uint() == 0) {
      internal_empty_n.write(false);
    } else {
      internal_empty_n.write(true);
    }
    if (mInPtr.read() == mOutPtr.read() &&
        mFlag_nEF_hint.read().to_uint() == 1) {
      internal_full_n.write(false);
    } else {
      internal_full_n.write(true);
    }
  }
};

} // namespace ap_rtl
#endif // __QUEUE_H__
