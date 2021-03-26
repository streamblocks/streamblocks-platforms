#include <iostream>
#include <chrono>

#include "sb_native_counters.h"

using Clock = std::chrono::high_resolution_clock;

using Ms = std::chrono::milliseconds;
using Sec = std::chrono::milliseconds;


using namespace std::counters;

float std::counters::timeMSec() {
    return std::chrono::duration_cast<Ms>(Clock::now().time_since_epoch()).count();
}

float std::counters::timeSec() {
    return std::chrono::duration_cast<Sec>(Clock::now().time_since_epoch()).count();
}