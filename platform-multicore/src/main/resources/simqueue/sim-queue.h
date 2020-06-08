#ifndef __SIM_QUEUE_H__
#define __SIM_QUEUE_H__

#include "debug_macros.h"
#include <sstream>
#include <vector>
#include <memory>

namespace SimQueue {

template <typename T> class BaseQueue {
protected:
  std::unique_ptr<std::vector<T>> buffer;
  std::size_t head;
  std::size_t tail;
  std::string queue_name;

public:
  BaseQueue(const std::string &queue_name)
      : queue_name(queue_name) {
    head = 0;
    tail = 0;
  }
  void allocate(std::size_t size) {
    buffer = std::make_unique<std::vector<T>>(size);
  }

  T* data() {
    return buffer->data();
  }

  std::size_t get_begin() const {
    return head;
  }
  std::size_t get_end() const {
    return tail;
  }

  void set_begin(const std::size new_head) {

    this->head = new_head;
  }

  void set_end(const std::size new_tail) {
    this->tail = new_tail;
  }


};
template <typename T> class InputQueue : public BaseQueue<T> {

public:
  InputQueue(const std::string &queue_name)
      : BaseQueue<T>(queue_name){};

  T dequeue() {

    T token = peek();
    this->head ++;
    return token;
  }

  bool empty_n() const{
    return (this->get_end() > 0 && this->get_begin() < this->get_end());
  }

  T peek() const{
    DEBUG_ASSERT(this->get_begin() <= this->get_end(),
                 "Buffer read overflow in %s\n", this->queue_name.c_str());
    if (this->get_begin() == this->get_end()) {
      std::cout << "warning, bad peek" << std::endl;
      return this->buffer->at[this->get_end())];
    } else {

      return this->buffer->at[this->get_begin()];
    }
  };
};

template <typename T> class OutputQueue : public BaseQueue<T> {

public:
  OutputQueue(const std::string &queue_name)
      : BaseQueue<T>(queue_name) {}

  bool enqueue(T token) {

    bool match = true;
    DEBUG_ASSERT(this->get_begin() < this->get_end(),
                 "Buffer write overflow in %s\n", this->queue_name.c_str());

    this->tail ++;
    return match;
  }
  bool full_n() { return (this->get_begin() < this->get_end(); }
};

} // namespace SimQueue

#endif
