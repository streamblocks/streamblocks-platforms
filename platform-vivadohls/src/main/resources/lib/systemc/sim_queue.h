#ifndef __SIM_QUEUE_H__
#define __SIM_QUEUE_H__

#include <assert.h>
#include <fstream>
#include <iostream>
#include <vector>

namespace SimQueue {

template <typename T> class BaseQueue {
protected:
  std::vector<T> buffer;
  unsigned long int ix;
  std::string queue_name;

public:
  BaseQueue(const std::string &queue_name, const std::string &path_prefix) {
    std::string file_name = path_prefix + queue_name + std::string(".txt");
    std::fstream ifs(file_name, std::ios::in);
    if (!ifs.is_open()) {
      std::cerr << "Failed to open " << file_name << std::endl;
      std::exit(EXIT_FAILURE);

    } else {
      T token;
      while (ifs >> token) {
        buffer.push_back(token);
      }
    }
    std::cout << "reference queue \"" << queue_name << "\" contains "
              << buffer.size() << " tokens." << std::endl;
    ifs.close();
  }
};
template <typename T> class InputQueue : public BaseQueue<T> {

public:
  InputQueue(const std::string &queue_name, const std::string &path_prefix)
      : BaseQueue<T>(queue_name, path_prefix){};
  ~InputQueue() {
    if (this->ix < this->buffer.size())
      std::cerr << "Only " << this->ix << " tokens out of "
                << this->buffer.size()
                << " were consumed in the input sim queue " << this->queue_name
                << std::endl;
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
    assert(this->ix <= this->buffer.size());
    T token = this->buffer[this->ix];
    return token;
  };
};

template <typename T> class OutputQueue : public BaseQueue<T> {

public:
  OutputQueue(const std::string &queue_name, const std::string &path_prefix)
      : BaseQueue<T>(queue_name, path_prefix) {}
  ~OutputQueue() {
    if (this->ix != this->buffer.size()) {
      std::cerr << "Error! expected " << this->buffer.size()
                << " tokens but received " << this->ix
                << " tokens in the output sim queue " << this->queue_name << std::endl;
    }
  }
  bool enqueue(T token) {

    bool match = true;
    assert(this->ix < this->buffer.size());
    if (this->buffer[this->ix] != token) {
      std::cerr << "@index = " << this->buffer.size() << ": Expected "
                << this->buffer[this->ix] << " but got " << token << std::endl;
      match = false;
    }
    this->ix++;
    return match;
  }
  bool full_n() { return (this->ix < this->buffer.size()); }
};

} // namespace SimQueue

#endif
