#ifndef __SIM_QUEUE_H__
#define __SIM_QUEUE_H__

#include "debug_macros.h"
#include <assert.h>
#include <fstream>
#include <iostream>
#include <sstream>
#include <vector>

namespace SimQueue {

template <typename T> class BaseQueue {
protected:
  std::vector<T> buffer;
  unsigned long int ix;
  std::string queue_name;

public:
  BaseQueue(const std::string &queue_name, const std::string &path_prefix)
      : queue_name(queue_name) {
    std::string file_name = path_prefix + queue_name + std::string(".txt");
    std::fstream ifs(file_name, std::ios::in);
    if (!ifs.is_open()) {
      PANIC("Could not open %s. Make sure the files is placed in the "
            "work directory.",
            file_name.c_str());
    } else {
      uint32_t token;
      std::string line;
      while (std::getline(ifs, line)) {
        // std::cout << line << std::endl;
        std::stringstream convert;
        convert << line;
        convert >> token;
        // std::cout << token << std::endl;
        buffer.push_back(token);
      }
    }
    STATUS_REPORT("\nReference queue %s contains %lu tokens.\n\n",
                  queue_name.c_str(), buffer.size());

    ifs.close();
    ix = 0;
  }
};
template <typename T> class InputQueue : public BaseQueue<T> {

public:
  InputQueue(const std::string &queue_name, const std::string &path_prefix)
      : BaseQueue<T>(queue_name, path_prefix){};
  ~InputQueue() {
    if (this->ix < this->buffer.size())
      WARNING("\nOnly %lu tokens out %lu of were consumed in %s\n\n", this->ix,
              this->buffer.size(), this->queue_name.c_str());
  }
  T dequeue() {

    T token = peek();
    this->ix++;
    return token;
  }

  bool empty_n() {
    return (this->buffer.size() > 0 && this->ix < this->buffer.size());
  }

  T peek() {
    DEBUG_ASSERT(this->ix <= this->buffer.size(),
                 "Buffer read overflow in %s\n", this->queue_name.c_str());
    if (this->ix == this->buffer.size()) {
      std::cout << "warning, bad peek" << std::endl;
      return this->buffer[this->ix - 1];
    } else {

      return this->buffer[this->ix];
    }
  };
};

template <typename T> class OutputQueue : public BaseQueue<T> {

public:
  OutputQueue(const std::string &queue_name, const std::string &path_prefix)
      : BaseQueue<T>(queue_name, path_prefix) {}
  ~OutputQueue() {
    if (this->ix != this->buffer.size()) {
      WARNING("\nExpected %lu tokens but received %lu in %s\n\n",
              this->buffer.size(), this->ix, this->queue_name.c_str());
    }
  }
  bool enqueue(T token) {

    bool match = true;
    DEBUG_ASSERT(this->ix < this->buffer.size(),
                 "Buffer write overflow in %s\n", this->queue_name.c_str());
    if (this->buffer[this->ix] != token) {
      WARNING("\n@ index = %lu expected %lu but received %lu\n", this->ix,
              this->buffer[this->ix], token);

      match = false;
    }
    this->ix++;
    return match;
  }
  bool full_n() { return (this->ix < this->buffer.size()); }
};

} // namespace SimQueue

#endif
