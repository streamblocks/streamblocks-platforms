#pragma once
#include <chrono>
#include <iostream>
#include <thread>
#include <map>

#include "actor.h"
#include "get_opt.h"
#include "mapping_parser.h"

// -- Chrono
using Clock = std::chrono::high_resolution_clock;
using Ms = std::chrono::milliseconds;
template <class Duration>
using TimePoint = std::chrono::time_point<Clock, Duration>;

class Partition {

private:
  MappingParser parser;
  std::vector<Actor *> actors;

  void partition_singlecore() {
    bool run = false;
    EStatus status = None;
    do {
      run = false;
      for (Actor *actor : actors) {
        run |= actor->action_selection(status);
      }
    } while (run);
  }

  static void partition(std::string name, std::vector<Actor *> actors) {
    EStatus status = None;
    Clock::time_point _start = Clock::now();
    bool stop = false;

    do {
      for (Actor *actor : actors) {
        actor->action_selection(status);
      }

      if (status == None) {
        stop = TimePoint<Ms>(std::chrono::duration_cast<Ms>(
                   Clock::now() - _start)) > TimePoint<Ms>(Ms(1000));
        std::this_thread::yield();

        if (stop) {
          std::cout << "Time out occurred on partition " << name << "!"
                    << std::endl;
        }
      } else {
        _start = Clock::now();
      }
    } while (!stop);
  }

public:
  Partition(MappingParser &parser, std::vector<Actor *> &actors)
      : parser(parser), actors(actors) {}

  void run() {
    auto partitions = parser.getPartitions();

    int partitions_size = partitions.size();
    if (partitions_size > 1) {
      std::vector<std::thread> workers(partitions_size);

      int i = 0;
      for (std::map<std::string, std::vector<Actor *>>::iterator it =
               partitions.begin();
           it != partitions.end(); ++it) {
        workers[i] = std::thread(partition, it->first, it->second);
        i++;
      }

      for (int i = 0; i < partitions_size; i++) {
        workers[i].join();
      }
    } else {
      partition_singlecore();
    }
  }
};