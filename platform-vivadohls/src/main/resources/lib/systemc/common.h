#ifndef __TRIGGER_COMMON_H__
#define __TRIGGER_COMMON_H__
#include "debug_macros.h"
#define TRACE_SIGNAL(VCD_DUMP, SIGNAL)                                         \
  sc_core::sc_trace(VCD_DUMP, SIGNAL, std::string(this->name()) + "." #SIGNAL)
#define TRACE_ARR(VCD_DUMP, X, I)                                              \
  sc_core::sc_trace(                                                           \
      VCD_DUMP, X[I],                                                          \
      (std::string(this->name()) + "." #X "(" + std::to_string(I) + ")")       \
          .c_str());
#define CTOR_PORT(P) P(#P)
#define CTOR_SIG(S, v) S(#S, v)

namespace streamblocks_rtl {
enum ReturnStatus { IDLE = 0, WAIT = 1, TEST = 2, EXECUTED = 3 };
}
#endif // __TRIGGER_COMMON_H__