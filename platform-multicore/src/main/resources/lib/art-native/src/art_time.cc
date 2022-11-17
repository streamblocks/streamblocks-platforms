#include "art_time.h"
#include <chrono>
#include <iostream>

using std::chrono::duration_cast;
using std::chrono::milliseconds;
using std::chrono::seconds;
using std::chrono::system_clock;


long art::time::timeMSec() {
  auto t = duration_cast<milliseconds>(system_clock::now().time_since_epoch())
               .count();
  return t;
}

long timeMSec() {
  return art::time::timeMSec();
}