#ifndef __SIM_QUEUE_H__
#define __SIM_QUEUE_H__

#include "debug_macros.h"
#include <sstream>
#include <vector>
#include <memory>

namespace SimQueue {

template <typename T> class BaseQueue {
public:
  std::unique_ptr<std::vector<T>> buffer;
  uint32_t head;
  uint32_t tail;
  std::string queue_name;

public:
  BaseQueue(const std::string &queue_name)
      : queue_name(queue_name) {
    buffer = std::make_unique<std::vector<T>>();
    head = 0;
    tail = 0;
  }
  void allocate(uint32_t size=4096) {
    buffer->resize(size);
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

  void set_begin(const uint32_t new_head) {

    this->head = new_head;
  }

  void set_end(const uint32_t new_tail) {
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
      WARNING("Bad peek in %s\n", this->queue_name.c_str());
      return this->buffer->at(this->get_end());
    } else {

      return this->buffer->at(this->get_begin());
    }
  };
};

template <typename T> class OutputQueue : public BaseQueue<T> {

public:
  OutputQueue(const std::string &queue_name)
      : BaseQueue<T>(queue_name) {}

  bool enqueue(T token) {

    bool match = true;
    DEBUG_ASSERT(this->full_n(),
                 "Buffer write overflow in %s\n", this->queue_name.c_str());

    this->buffer->at(this->tail) = token;
    this->tail ++;
    return match;
  }
  bool full_n() { return (this->get_end() < this->buffer->size()); }
};

} // namespace SimQueue

#endif
