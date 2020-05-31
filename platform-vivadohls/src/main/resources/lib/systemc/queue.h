#ifndef __QUEUE_H__
#define __QUEUE_H__

#include "systemc.h"
#include <assert.h>
#include <fstream>
#include <iostream>
#include <vector>

namespace ap_rtl {

template <int WIDTH, int LOG2_DEPTH> class Queue : public sc_module {
public:
  static const unsigned int DATA_WIDTH = WIDTH;
  static const unsigned int ADDR_WIDTH = LOG2_DEPTH;
  static const unsigned int FIFO_DEPTH = 1 << LOG2_DEPTH;
  sc_core::sc_in_clk ap_clk;
  sc_core::sc_in<sc_dt::sc_logic> ap_rst_n;
  sc_core::sc_out<sc_dt::sc_logic> empty_n;

  sc_core::sc_in<sc_dt::sc_logic> read;
  sc_core::sc_out<sc_dt::sc_lv<DATA_WIDTH>> dout;

  sc_core::sc_out<sc_dt::sc_logic> full_n;

  sc_core::sc_in<sc_dt::sc_logic> write;
  sc_core::sc_in<sc_dt::sc_lv<DATA_WIDTH>> din;

  // -- Extended io
  sc_core::sc_out<sc_dt::sc_lv<32>> count;
  sc_core::sc_out<sc_dt::sc_lv<32>> size;
  sc_core::sc_out<sc_dt::sc_lv<DATA_WIDTH>> peek;

  sc_core::sc_signal<sc_dt::sc_logic> internal_empty_n;
  sc_core::sc_signal<sc_dt::sc_logic> internal_full_n;

  sc_core::sc_signal<sc_dt::sc_lv<DATA_WIDTH>> mStorage[FIFO_DEPTH];
  sc_core::sc_signal<sc_dt::sc_uint<ADDR_WIDTH>> mInPtr;
  sc_core::sc_signal<sc_dt::sc_uint<ADDR_WIDTH>> mOutPtr;
  sc_core::sc_signal<sc_dt::sc_uint<1>> mFlag_nEF_hint;

  sc_core::sc_trace_file *mTrace;

  SC_HAS_PROCESS(Queue);
  Queue(sc_module_name name)
      : sc_module(name), ap_clk("ap_clk"), ap_rst_n("rst_n"),
        empty_n("empty_n"), read("read"), dout("dout"), full_n("full_n"),
        write("write"), din("din"), count("count"), size("size"), peek("peek"),
        mTrace(0) {

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

  ~Queue() {
    if (mTrace)
      sc_core::sc_close_vcd_trace_file(mTrace);
  }

  void proc_status() {

    empty_n.write(internal_empty_n.read());
    full_n.write(internal_full_n.read());
  }

  void proc_read_write() {
    size.write(FIFO_DEPTH);
    if (ap_rst_n.read() == sc_dt::SC_LOGIC_0) {
      mInPtr.write(0);
      mOutPtr.write(0);
      mFlag_nEF_hint.write(0);
      count.write(0);
    } else {
      if (read.read() == sc_dt::SC_LOGIC_1 &&
          internal_empty_n.read() == sc_dt::SC_LOGIC_1) {
        sc_dt::sc_uint<ADDR_WIDTH> ptr;
        if (mOutPtr.read().to_uint() == (FIFO_DEPTH - 1)) {
          ptr = 0;
          mFlag_nEF_hint.write(~mFlag_nEF_hint.read());
        } else {
          ptr = mOutPtr.read();
          ptr++;
        }
        assert(count.read().to_uint() > 0);
        sc_dt::sc_lv<32> old_count = count.read();
        sc_dt::sc_lv<32> new_count = old_count.to_uint() - 1;
        count.write(new_count);

        assert(ptr.to_uint() < FIFO_DEPTH);
        mOutPtr.write(ptr);
      }
      if (write.read() == sc_dt::SC_LOGIC_1 &&
          internal_full_n.read() == sc_dt::SC_LOGIC_1) {
        sc_dt::sc_uint<ADDR_WIDTH> ptr;
        ptr = mInPtr.read();
        mStorage[ptr.to_uint()].write(din.read());
        if (ptr.to_uint() == (FIFO_DEPTH - 1)) {
          ptr = 0;
          mFlag_nEF_hint.write(~mFlag_nEF_hint.read());
        } else {
          ptr++;
          assert(ptr.to_uint() < FIFO_DEPTH);
        }
        assert(count.read().to_uint() < FIFO_DEPTH);
        sc_dt::sc_lv<32> old_count = count.read();
        sc_dt::sc_lv<32> new_count = old_count.to_uint() + 1;
        count.write(new_count);
        mInPtr.write(ptr);
      }
    }
  }

  void proc_dout() {
    sc_dt::sc_uint<ADDR_WIDTH> ptr = mOutPtr.read();
    if (ptr.to_uint() > FIFO_DEPTH) {
      dout.write(sc_dt::sc_lv<DATA_WIDTH>());
      peek.write(sc_dt::sc_lv<DATA_WIDTH>());
    } else {
      dout.write(mStorage[ptr.to_uint()]);
      peek.write(mStorage[ptr.to_uint()]);
    }
  }

  void proc_ptr() {
    if (mInPtr.read() == mOutPtr.read() &&
        mFlag_nEF_hint.read().to_uint() == 0) {
      internal_empty_n.write(sc_dt::SC_LOGIC_0);
    } else {
      internal_empty_n.write(sc_dt::SC_LOGIC_1);
    }
    if (mInPtr.read() == mOutPtr.read() &&
        mFlag_nEF_hint.read().to_uint() == 1) {
      internal_full_n.write(sc_dt::SC_LOGIC_0);
    } else {
      internal_full_n.write(sc_dt::SC_LOGIC_1);
    }
  }
};

} // namespace ap_rtl
#endif // __QUEUE_H__
