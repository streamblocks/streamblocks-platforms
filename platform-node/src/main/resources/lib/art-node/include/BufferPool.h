/*
 * Copyright (c) Ericsson AB, 2009-2013, EPFL VLSC, 2019
 * Author: Mahyar Emami (mahyar.emami@epfl.ch)
 * All rights reserved.
 *
 * License terms:
 *
 * Redistribution and use in source and binary forms,
 * with or without modification, are permitted provided
 * that the following conditions are met:
 *     * Redistributions of source code must retain the above
 *       copyright notice, this list of conditions and the
 *       following disclaimer.
 *     * Redistributions in binary form must reproduce the
 *       above copyright notice, this list of conditions and
 *       the following disclaimer in the documentation and/or
 *       other materials provided with the distribution.
 *     * Neither the name of the copyright holder nor the names
 *       of its contributors may be used to endorse or promote
 *       products derived from this software without specific
 *       prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#pragma once
#include "readerwriterqueue.h"

#define DEBUGPRINT 0
#define DPRINTF(fmt, args...)                                                  \
  do {                                                                         \
    if (DEBUGPRINT)                                                            \
      fprintf(stderr, "%s():%d: " fmt, __func__, __LINE__, ##args);            \
  } while (0)

using namespace moodycamel;
template <typename T> class TokenizedBuffer {

public:
  TokenizedBuffer() {
    buffer = nullptr;
    count = 0;
    capacity = 0;
    id = id_gen();
    DPRINTF("Default Constructor %d\n", id);
  }
  TokenizedBuffer(std::size_t capacity) : capacity(capacity) {
    buffer = new T[capacity];
    count = 0;
    id = id_gen();
    DPRINTF("Constructor %d\n", id);
  }
  // Copy ctor
  TokenizedBuffer(const TokenizedBuffer &obj){
    
    id = id_gen();
    DPRINTF("Copy constructor %d\n", id);

    capacity = obj.capacity;
    count = obj.count;
    buffer = new T[capacity];
    
    for (std::size_t i = 0; i < count; i++)
      buffer[i] = obj.buffer[i];
  }
  // Move ctor
  TokenizedBuffer(TokenizedBuffer &&obj){

    id = id_gen();
    DPRINTF("Move constructor %d\n", id);

    buffer = obj.buffer;
    capacity = obj.capacity;
    count = obj.count;
    

    obj.buffer = nullptr;
    obj.count = 0;
    obj.capacity = 0;
  }
  // Copy assignment
  TokenizedBuffer &operator=(const TokenizedBuffer &obj) {
    DPRINTF("Copy Assignment %d --> %d\n", obj.id, id);
    if (this != &obj) {
      if (buffer != nullptr && capacity != 0)
        delete[] buffer;

      capacity = obj.capacity;
      count = obj.count;
      buffer = new T[capacity];
      for (std::size_t i = 0; i < count; i++)
        buffer[i] = obj.buffer[i];
    }

    return *this;
  }
  // Move assignment
  TokenizedBuffer &operator=(TokenizedBuffer &&obj) {
    DPRINTF("Move Assignment %d --> %d\n", obj.id, id);
    if (buffer != nullptr && capacity != 0)
      delete[] buffer;

    capacity = obj.capacity;
    count = obj.count;
    buffer = obj.buffer;

    obj.buffer = nullptr;
    obj.count = 0;
    obj.capacity = 0;
    return *this;
  }
  T &operator[](const std::size_t idx) {
    assert(buffer != nullptr && "null buffer, are you the owner?");
    assert(idx < capacity && "Index out of bounds");
    return buffer[idx];
  }
  void updata_count(const std::size_t inc = 1) { count += inc; }
  void set_count(const std::size_t val) { count = val; }
  bool is_empty() { return (capacity == 0 && buffer == nullptr); }
  std::size_t get_count() { return count; }
  void clear_count() { count = 0; }
  std::size_t get_capacity() { return capacity; }
  // Does not respect move and ownership semantics, use with care
  T *to_c_buffer() { return buffer; }

  ~TokenizedBuffer() {
    DPRINTF("Destructor %d\n", id);
    if (buffer != nullptr)
      delete[] buffer;
  }

private:
  static std::uint32_t id_gen() {
    static std::uint32_t IDGen;
    return IDGen++;
  }
  // static std::uint32_t IDgen;
  T *buffer;
  std::size_t count;
  std::size_t capacity;
  std::uint32_t id;
 
};

// template <typename T>
// std::uint32_t TokenizedBuffer<T>:: IDgen = 0;

template <typename T>
inline TokenizedBuffer<T>
TokenizedBufferFactory(std::uint32_t capacity = 4096) {
  return TokenizedBuffer<T>(capacity);
}

template <class T> class BufferPool {
public:
  using Buffer = TokenizedBuffer<T>;

  explicit BufferPool(std::size_t size, std::size_t capacity)
      : fullQueue(size - 1), freeQueue(size - 1), _size(size) {
    std::size_t actualSize = 0;

    while (freeQueue.try_enqueue(TokenizedBufferFactory<T>(capacity))) {
      actualSize++;
    };
    DPRINTF("Actual size %lu\n", actualSize);
    
  }

  // Move constructor
  BufferPool(BufferPool &&obj) {
    DPRINTF("Move Constructor\n");
    this->operator=(std::move(obj));
  }
  // Move assignment
  BufferPool &operator=(BufferPool &&obj) {
    DPRINTF("Move Assignment\n");
    fullQueue = std::move(obj.fullQueue);
    freeQueue = std::move(obj.freeQueue);
    _size = obj._size;
    actualSize = obj.actualSize;
    return *this;
  }

  // Copy constructor 
  BufferPool(const BufferPool& obj) {
    throw std::runtime_error("BufferPool::Copy assignment not supported\nuse std::move()");
  }
  // Copy assignment

  BufferPool &operator=(const BufferPool& obj) {
    throw std::runtime_error("BufferPool::Copy assignment not supported\nuse std::move()");
  }

  // Enqueue with move semantics
  inline bool try_enqueue_full_buffer(Buffer &&buffer) {
    DPRINTF("Enqueue with move semantics\n");
    return fullQueue.try_enqueue(std::move(buffer));
  }
  inline bool try_enqueue_free_buffer(Buffer &&buffer) {
    DPRINTF("Enqueue with move semantics\n");
    return freeQueue.try_enqueue(std::move(buffer));
  }
  // Enqueue with copy semantics
  inline bool try_enqueue_full_buffer(const Buffer &buffer) {
    DPRINTF("Enqueue with copy semantics\n");
    return fullQueue.try_enqueue(buffer);
  }
  inline bool try_enqueue_free_buffer(const Buffer &buffer) {
    DPRINTF("Enqueue with copy semantics\n");
    return freeQueue.try_enqueue(buffer);
  }
  // Dequeue with move assignment
  inline bool try_dequeue_full_buffer(Buffer &buffer) {
    DPRINTF("Dequeue with move assignment\n");
    return fullQueue.try_dequeue(buffer);
  }
  inline bool try_dequeue_free_buffer(Buffer &buffer) {
    DPRINTF("Dequeue with move assignment\n");
    return freeQueue.try_dequeue(buffer);
  }

  inline std::size_t approx_size_full_queue() const {
    return fullQueue.size_approx();
  }
  inline std::size_t approx_size_free_queue() const {
    return freeQueue.size_approx();
  }
  inline std::size_t size() const { return actualSize; }

private:
  ReaderWriterQueue<Buffer> fullQueue;
  ReaderWriterQueue<Buffer> freeQueue;

  std::size_t _size;
  std::size_t actualSize;
};

template <typename T>
inline BufferPool<T> BufferPoolFactory(std::size_t size = 7,
                                       std::size_t capacity = 4096) {
  return BufferPool<T>(size, capacity);
}