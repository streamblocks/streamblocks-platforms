/*
 * Copyright (c) EPFL VLSC, 2019
 * Author:  Mahyar Emami (mahyar.emami@epfl.ch)
 *
 *
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

/**
 * Test bench for the iostage actors described in C++
 * The tester classed InputTester and OutputTester try (?) to emulate
 * the concurrent access to their output or input fifos by randomly reading
 * or writing from them.
 * Tests runs on as many threads as are available on a machine
 * No command line arguments though, should I add them?
 **/

#include "iostage.h"
#include <cstddef>
#include <iostream>
#include <random>
#include <sstream>
#include <stdint.h>
#include <stdio.h>
#include <string>
#include <thread>
#include <vector>

// -- utility functions
int randint(int min, int max) {

  static std::random_device rd;
  static std::mt19937 gen(rd());
  std::uniform_int_distribution<> distrib(min, max);
  return distrib(gen);
}

class TextColor {
public:
  enum Color { RED = 31, GREEN = 32, YELLOW = 33, NO_COLOR = 39 };

  TextColor(Color color_code, bool bold = false)
      : color_code(color_code), bold(bold){};
  friend std::ostream &operator<<(std::ostream &os, const TextColor &color) {

    return os << "\033[" << (color.isBold() ? "1;" : "0;") << color.getCode()
              << "m";
  }
  Color getCode() const { return color_code; }
  bool isBold() const { return bold; }

private:
  Color color_code;
  bool bold;
};

template <typename T> class MemoryBuffer {
public:
  MemoryBuffer(uint32_t alloc_size) : buffer(alloc_size) {
    for (auto &v : buffer)
      v = 0;
  }

  inline T read(uint32_t address) { return buffer[address]; }

  inline void write(T value, uint32_t address) { buffer[address] = value; }

  std::vector<T> randomize() {
    std::vector<T> tokens;
    size_t num_words = buffer.size();
    for (size_t ix = 0; ix < num_words; ix++) {
      T rand_token = randint(0, 1 << (sizeof(T) * 8 - 2));
      tokens.push_back(rand_token);
      write(rand_token, ix);
      if (read(ix) != rand_token) {
        std::cerr << "Memory write failed: buffer[" << ix << "]=" << rand_token
                  << " but read " << read(ix) << std::endl;
      }
    }

    return tokens;
  }
  inline T *data() { return buffer.data(); }

private:
  std::vector<T> buffer;
};

template <typename T, int FIFO_SIZE> class IOTester {

public:
  IOTester(const uint32_t alloc_size, const uint32_t test_index = 0,
           std::string name = "IOTester")
      : alloc_size(alloc_size), test_index(test_index),
        memory_buffer(alloc_size),
        meta_buffer(
            sizeof(T) > sizeof(uint32_t) ? 1 : sizeof(uint32_t) / sizeof(T)),
        meta_stream(std::string("meta_stream_") + name),
        data_stream(std::string("data_stream_") + name), name(name),
        verbose(1) {}
  void setVerbose(unsigned int level) { verbose = level; }
  virtual void test() = 0;

protected:
  unsigned int verbose;
  const std::string name;
  const uint32_t test_index;
  const uint32_t alloc_size;
  MemoryBuffer<T> memory_buffer;
  std::vector<T> golden_tokens;
  std::vector<T> meta_buffer;

  hls::stream<bool> meta_stream;
  hls::stream<T> data_stream;
  virtual void initialize() = 0;

  void assert_check(bool cond, std::string msg) {
    if (cond == false) {
      TextColor red(TextColor::Color::RED);
      TextColor no_color(TextColor::Color::NO_COLOR);
      std::cerr << red << "Assertion faild! "
                << "Test: " << name << "  Id: " << test_index << no_color
                << std::endl;

      std::cerr << "\t\t" << msg << std::endl;
      throw std::runtime_error(msg.c_str());
      // fail_test();
    }
  }

  void show_info(std::string msg, unsigned int level = 1) {

    if (verbose >= level) {

      TextColor green(TextColor::GREEN);
      TextColor no_color(TextColor::NO_COLOR);
      std::cerr << green << "Info " << name << "  Id: " << test_index
                << no_color << std::endl;

      std::cout << "\t\t" << msg << std::endl;
    }
  }

  inline void fail_test() { std::exit(1); }
  auto randomize() { return memory_buffer.randomize(); }

  inline T read(size_t ix) { return memory_buffer.read(ix); }
  inline void write(T value, size_t ix) { memory_buffer.write(value, ix); }
};

template <typename T, int FIFO_SIZE>
class InputTester : public IOTester<T, FIFO_SIZE> {

public:
  InputTester(const uint32_t alloc_size, const uint32_t test_index = 0,
              std::string name = "InputTester")
      : IOTester<T, FIFO_SIZE>(alloc_size, test_index, name) {}

private:
  void initialize() {
    this->show_info(std::string("Starting test"));

    this->golden_tokens = this->randomize();
    {
      bool cond = this->alloc_size == this->golden_tokens.size();
      std::stringstream msg_builder;
      msg_builder << "Memory randomization failed with reandom size "
                  << this->golden_tokens.size() << " and alloc size "
                  << this->alloc_size;

      auto msg = msg_builder.str();
      this->assert_check(cond, msg);
    }

    this->meta_stream.write(true);
  }

public:
  void test() {

    initialize();

    using InputStage = iostage::InputMemoryStage<T>;
    InputStage input_stage;

    iostage::ret_t return_val = RETURN_EXECUTED;
    size_t token_index = 0;
    size_t call_index = 0;
    // randomize the circular buffer configuration
    uint32_t tail = randint(0, this->alloc_size - 1);

    uint32_t head = randint(0, this->alloc_size - 1);
    // randomize the starting fifo configuration;
    uint32_t init_fifo_count = randint(0, FIFO_SIZE);
    for (int ix = 0; ix < init_fifo_count; ix++) {
      T token = randint(0, 1 << (sizeof(T) * 8 - 2));
      {
        std::stringstream msg_builder;
        msg_builder << "Enqueue(" << uint64_t(token) << ")" << std::endl;
        this->show_info(msg_builder.str(), 9);
      }
      this->data_stream.write(token);
    }
    uint32_t read_from_stream = 0;
    {
      std::stringstream msg_builder;
      msg_builder << "Initial configuration: head = " << head
                  << " tail = " << tail << " fifo size = " << init_fifo_count
                  << " meta_buffer = " << uintptr_t(this->meta_buffer.data())
                  << std::endl;
      this->show_info(msg_builder.str(), 1);
    }
    token_index = tail;
    uint32_t tokens_to_read =
        (tail < head) ? (head - tail) : (this->alloc_size - tail + head);
    do {

      auto pre_count = this->data_stream.size();

      iostage::CircularBuffer<T> ocl_buff = {
          .data_buffer = this->memory_buffer.data(),
          .meta_buffer = this->meta_buffer.data(),
          .alloc_size = this->alloc_size,
          .head = head,
          .tail = tail};
      return_val = input_stage(ocl_buff, this->data_stream.size(), FIFO_SIZE,
                               this->data_stream, this->meta_stream);

      auto post_count = this->data_stream.size();

      auto enqueued = post_count - pre_count;

      auto dequeue_count = randint(0, post_count);
      {
        std::stringstream msg_builder;
        msg_builder << "InputStage Production: " << enqueued
                    << " fifo size: " << pre_count;
        msg_builder << "\tActor Consumption: " << dequeue_count
                    << " fifo size: " << post_count - dequeue_count
                    << "\ttoken index: " << token_index;
        this->show_info(msg_builder.str(), 1);
      }

      for (int i = 0; i < dequeue_count; i++) {

        {
          std::stringstream msg_builder;
          msg_builder << "attempted to read from an empty stream";
          this->assert_check(this->data_stream.empty() == false,
                             msg_builder.str());
        }
        T token = this->data_stream.read();
        read_from_stream++;
        if (read_from_stream > init_fifo_count) {
          {
            std::stringstream msg_builder;
            T golden = this->golden_tokens[token_index];
            msg_builder << "Golden result mismatch! Expected "
                        << uint64_t(golden) << " got " << uint64_t(token)
                        << " at " << token_index
                        << " with token size = " << sizeof(T);
            this->assert_check(token == golden, msg_builder.str());
          }
          token_index = (token_index + 1) % this->alloc_size;
        }
      }

      call_index++;

      if (return_val == RETURN_WAIT) {
        this->show_info("Returned WAIT", 1);
      }

    } while (token_index != head);

    {
      uint32_t *tail_ptr =
          reinterpret_cast<uint32_t *>(this->meta_buffer.data());
      std::stringstream msg_builder;
      msg_builder << "Invalid tail value " << tail_ptr[0] << " should be "
                  << token_index << std::endl;

      this->assert_check(tail_ptr[0] == token_index, msg_builder.str());
    }
    // INFO("Reading leftover %lu tokens\n", stream.size());
    while (this->data_stream.empty() == false) {

      {
        std::stringstream msg_builder;
        msg_builder << "attempted to read from an empty stream";
        this->assert_check(this->data_stream.empty() == false,
                           msg_builder.str());
      }
      auto token = this->data_stream.read();
      read_from_stream++;
      if (read_from_stream > init_fifo_count) {
        {
          std::stringstream msg_builder;
          uint64_t golden = this->golden_tokens[token_index];
          printf("Expected %d got %d at %d\n", golden, token, token_index);
          msg_builder << "Golden result mismatch! Expected " << golden
                      << " got " << uint64_t(token) << " at " << token_index;
          this->assert_check(token == this->golden_tokens[token_index],
                             msg_builder.str());
        }
        token_index = (token_index + 1) % this->alloc_size;
      }
    }

    // {
    //   size_t size_0 = this->meta_buffer[0];
    //   auto cond = size_0 == this->alloc_size;
    //   std::stringstream msg_builder;
    //   msg_builder
    //       << "Not all tokens have been read from the memory! alloc_size = "
    //       << this->alloc_size << " but size[0] = " << size_0;
    // }
    {
      std::stringstream msg_builder;
      msg_builder << "Test passed after " << call_index << " calls";

      this->show_info(msg_builder.str(), 1);
    }
  }
};

template <typename T, int FIFO_SIZE>
class OutputTester : public IOTester<T, FIFO_SIZE> {
public:
  OutputTester(const uint32_t alloc_size, const uint32_t test_index = 0,
               std::string name = "OutputTester")
      : IOTester<T, FIFO_SIZE>(alloc_size, test_index, name) {}

private:
  void initialize() {
    this->show_info(std::string("Starting test"));

    for (size_t ix = 0; ix < this->alloc_size; ix++) {
      this->golden_tokens.push_back(randint(0, 1 << (sizeof(T) * 8 - 2)));
      this->write(0, ix);
    }
    {
      std::stringstream msg_builder;
      msg_builder << "failed generating tokens, " << this->alloc_size
                  << " != " << this->golden_tokens.size();
      this->assert_check(this->alloc_size == this->golden_tokens.size(),
                         msg_builder.str());
    }
    this->meta_stream.write(true);
  }

public:
  void test() {

    /* randomize the memory content before activating the output stage */
    this->randomize();

    /* generate randome golden tokens and enqueue some of them already on
       the data_stream
    */
    uint32_t init_fifo_count = randint(0, FIFO_SIZE);
    uint32_t gold_index = 0;
    for (uint32_t ix = 0; ix < init_fifo_count; ix++) {
      T token = randint(0, 1 << (sizeof(T) * 8 - 2));
      {
        std::stringstream msg_builder;
        msg_builder << "Enqueue(" << uint64_t(token) << ")" << std::endl;
        this->show_info(msg_builder.str(), 9);
      }
      this->golden_tokens.push_back(token);
      this->data_stream.write(token);
    }

    this->meta_stream.write(true);

    /* create a random circular buffer configuration */

    uint32_t tail = randint(0, this->alloc_size - 1);
    uint32_t head = randint(0, this->alloc_size - 1);

    /* create the ciruclar buffer handle */
    iostage::CircularBuffer<T> ocl_buff = {
        .data_buffer = this->memory_buffer.data(),
        .meta_buffer = this->meta_buffer.data(),
        .alloc_size = this->alloc_size,
        .head = head,
        .tail = tail};
    /* activate the input stage in a loop, by doing the following steps:
       1. activate input stage, let it read from the data_stream and write to
       memory
       2. produce a random number of tokens on the data_stream
       3. check whether the data_buffer has become full (i.e., tail == head)
          and stop the loop
    */
    iostage::OutputMemoryStage<T> output_stage;
    auto pre_head = head;
    auto post_head = head;
    uint32_t validatation_index = 0;
    {
      std::stringstream msg_builder;
      msg_builder << "Initial configuration: head = " << head
                  << " tail = " << tail << " fifo size = " << init_fifo_count
                  << std::endl;
      this->show_info(msg_builder.str(), 1);
    }
    uint32_t call_index = 0;
    uint32_t space_left = 0;
    do {

      /* 1. activate the output stage */
      auto pre_count = this->data_stream.size();
      pre_head = post_head;
      auto return_val = output_stage(ocl_buff, pre_count, this->data_stream,
                                     this->meta_stream);
      auto post_count = this->data_stream.size();
      post_head = ((uint32_t *)this->meta_buffer.data())[0];
      call_index++;

      if (return_val == RETURN_WAIT) {
        this->show_info("Returned WAIT", 1);
      }

      // validate output stage operation
      uint32_t curr_ix = pre_head;
      while (curr_ix != post_head) {

        {
          std::stringstream msg_builder;
          msg_builder << "Invalid validation index " << validatation_index
                      << " >= " << this->golden_tokens.size();
          this->assert_check(validatation_index < this->golden_tokens.size(),
                             msg_builder.str());
        }
        T token = this->read(curr_ix);
        T gold_token = this->golden_tokens[validatation_index];
        {
          std::stringstream msg_builder;
          msg_builder << "Expected " << uint64_t(gold_token) << " but got "
                      << uint64_t(token) << " tokens size = " << sizeof(T)
                      << std::endl;

          this->assert_check(token == gold_token, msg_builder.str());
        }
        curr_ix = (curr_ix + 1) % ocl_buff.alloc_size;

        validatation_index++;
      }
      /* 2. decide how much an actor produces */
      uint32_t enqueue_count = randint(0, FIFO_SIZE - post_count);
      for (uint32_t ix = 0; ix < enqueue_count; ix++) {
        T token = randint(0, 1 << (sizeof(T) * 8 - 2));
        this->golden_tokens.push_back(token);
        this->data_stream.write(token);
      }
      {
        std::stringstream msg_builder;
        msg_builder << "OutputStage consumption: " << pre_count - post_count
                    << " pre_count " << pre_count;
        msg_builder << "\tActor production: " << enqueue_count << " post_count "
                    << post_count + enqueue_count;
        msg_builder << "\t\t head: " << pre_head << " -> " << post_head
                    << "\t\t tail: " << tail;
        this->show_info(msg_builder.str(), 1);
      }

      if (post_head == tail) {
        space_left = this->alloc_size - 1;
      } else if (post_head < tail) {
        space_left = tail - 1 - post_head;
      } else {
        space_left = this->alloc_size - post_head + tail - 1;
      }
    } while (space_left != 0);

    if (validatation_index < this->golden_tokens.size()) {
      {
        std::stringstream msg_builder;
        msg_builder << "output stage could not write all the tokens in the "
                       "data_stream. Produced: "
                    << this->golden_tokens.size()
                    << "\tWritten: " << validatation_index;
        this->show_info(msg_builder.str(), 1);
      }
      T token;
      while (this->data_stream.read_nb(token))
        ;
    }
    {
      std::stringstream msg_builder;
      msg_builder << "Test passed after " << call_index << " calls";
      this->show_info(msg_builder.str(), 1);
    }
  }
};

template <typename T, int FIFO_SIZE>
size_t runTest(size_t start_id, size_t alloc_size, std::string msg) {
  InputTester<T, FIFO_SIZE> itester(alloc_size, start_id,
                                    "input stage tester " + msg);

  itester.setVerbose(0);
  itester.test();

  OutputTester<T, FIFO_SIZE> otester(alloc_size, start_id + 1,
                                     "output stage tester" + msg);

  otester.setVerbose(0);
  otester.test();

  return start_id + 1;
}

void runTests(size_t start_id, size_t count, size_t alloc_size) {
  using namespace std::chrono_literals;
  std::this_thread::sleep_for(1ms);
  for (auto id = 0; id < count; id++) {
    start_id = runTest<uint8_t, 4096>(start_id, alloc_size, "uint8_t");
    start_id = runTest<uint16_t, 4096>(start_id, alloc_size, "uint16_t");
    start_id = runTest<uint32_t, 4096>(start_id, alloc_size, "uint32_t");
    start_id = runTest<uint64_t, 4096>(start_id, alloc_size, "uint64_t");
    double progress = id * 100.0 / count * 1.0;
    if (id % (count / 10) == 0)
      std::cout << "Thread " << start_id / count << " progress " << progress
                << "%" << std::endl;
  }
}

int main() {

  const size_t count = 10000;
  const size_t alloc_size = 1 << 14;
  const auto thread_count = std::thread::hardware_concurrency() / 2;
  // const auto thread_count = 1;
  std::vector<std::thread> threads;
  const size_t count_per_thread = count / thread_count;
  std::cout << "Starting tests on " << thread_count << " threads" << std::endl;
  for (int tid = 0; tid < thread_count; tid++) {

    threads.emplace_back(runTests, tid * count_per_thread, count_per_thread,
                         alloc_size);
  }
  for (auto &thread : threads) {
    thread.join();
  }
  std::cout << "Tests finished" << std::endl;
}