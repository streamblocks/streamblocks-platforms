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

#include "hls_stream.h"
#include "iostage.h"
#include <cstddef>
#include <iostream>
#include <random>
#include <sstream>
#include <stdint.h>
#include <stdio.h>
#include <string>
#include <thread>

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

class MemoryBuffer {
public:
  MemoryBuffer(size_t num_lines) : buffer(num_lines) {
    // std::cout << "num_lines " << num_lines << " buffer.size() " <<
    // buffer.size
    for (auto &val : buffer)
      val = 0;
  };
  template <typename T> std::vector<T> randomize() {
    std::vector<T> tokens;
    size_t num_words = buffer.size() * iostage::BUS_BYTE_WIDTH / sizeof(T);
    for (size_t ix = 0; ix < num_words; ix++) {
      T rand_token = randint(0, 1 << (sizeof(T) * 8 - 2));
      tokens.push_back(rand_token);
      writeWord<T>(rand_token, ix);
      if (readWord<T>(ix) != rand_token) {
        std::cerr << "Memory write failed: buffer[" << ix << "]=" << rand_token
                  << " but read " << readWord<T>(ix) << std::endl;
      }
    }

    return tokens;
  }
  template <typename T> T readWord(size_t address) {
    size_t line_number = address / (iostage::BUS_BYTE_WIDTH / sizeof(T));
    size_t col_number = address % (iostage::BUS_BYTE_WIDTH / sizeof(T));
    iostage::bus_t bus_line = buffer[line_number];
    size_t low_range = col_number * sizeof(T) * 8;
    size_t high_range = (col_number + 1) * sizeof(T) * 8 - 1;
    T value = bus_line.range(high_range, low_range);
    return value;
  }

  template <typename T> void writeWord(T value, size_t address) {
    size_t line_number = address / (iostage::BUS_BYTE_WIDTH / sizeof(T));
    size_t col_number = address % (iostage::BUS_BYTE_WIDTH / sizeof(T));

    size_t low_range = col_number * sizeof(T) * 8;
    size_t high_range = (col_number + 1) * sizeof(T) * 8 - 1;
    // std::cout << "writing buffer[" << line_number << "].range(" << high_range
    //           << ", " << low_range << ") = " << value << std::endl;
    buffer[line_number].range(high_range, low_range) = value;
  }
  iostage::bus_t *data() { return buffer.data(); }

private:
  std::vector<iostage::bus_t> buffer;
};

template <typename T, int FIFO_SIZE> class IOTester {

public:
  IOTester(const uint32_t alloc_size, const uint32_t test_index = 0,
           std::string name = "IOTester")
      : alloc_size(alloc_size), test_index(test_index),
        memory_buffer(alloc_size * sizeof(T) / iostage::BUS_BYTE_WIDTH),
        size_buffer(1), offset_stream(std::string("offset_stream_") + name),
        fifo_stream(std::string("fifo_stream_") + name), name(name),
        verbose(1) {}
  void setVerbose(unsigned int level) { verbose = level; }
  virtual void test() = 0;

protected:
  unsigned int verbose;
  const std::string name;
  const uint32_t test_index;
  const uint32_t alloc_size;
  MemoryBuffer memory_buffer;
  std::vector<T> golden_tokens;
  std::vector<iostage::bus_t> size_buffer;

  hls::stream<uint64_t> offset_stream;
  hls::stream<T> fifo_stream;
  virtual void initialize() = 0;

  void assert_check(bool cond, std::string msg) {
    if (cond == false) {
      TextColor red(TextColor::Color::RED);
      TextColor no_color(TextColor::Color::NO_COLOR);
      std::cerr << red << "Assertion faild! "
                << "Test: " << name << "  Id: " << test_index << no_color
                << std::endl;

      std::cerr << "\t\t" << msg << std::endl;
      fail_test();
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

  void fail_test() { std::exit(1); }
  auto randomize() { return memory_buffer.randomize<T>(); }

  T readWord(size_t ix) { return memory_buffer.readWord<T>(ix); }
  void writeWord(T value, size_t ix) { memory_buffer.writeWord<T>(value, ix); }
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

    this->offset_stream.write(0);
  }

public:
  void test() {

    initialize();

    using InputStage = iostage::class_input_stage_mem<T>;
    InputStage input_stage;

    iostage::ret_t return_val = RETURN_EXECUTED;
    size_t token_index = 0;
    size_t call_index = 0;
    do {

      auto pre_count = this->fifo_stream.size();
      return_val =
          input_stage(this->alloc_size, this->size_buffer.data(),
                      this->memory_buffer.data(), this->fifo_stream.size(),
                      FIFO_SIZE, this->fifo_stream, this->offset_stream);
      auto post_count = this->fifo_stream.size();

      auto enqueued = post_count - pre_count;

      auto dequeue_count = randint(0, post_count);
      {
        std::stringstream msg_builder;
        msg_builder << "InputStage Production " << enqueued << " size "
                    << pre_count;
        msg_builder << "\tActor Consumption " << dequeue_count << " size "
                    << post_count - dequeue_count;
        this->show_info(msg_builder.str(), 1);
      }

      for (int i = 0; i < dequeue_count; i++) {

        {
          std::stringstream msg_builder;
          msg_builder << "attempted to read from an empty stream";
          this->assert_check(this->fifo_stream.empty() == false,
                             msg_builder.str());
        }
        auto token = this->fifo_stream.read();
        {
          std::stringstream msg_builder;
          msg_builder << "Golden result mismatch! Expected "
                      << this->golden_tokens[token_index] << " got " << token
                      << " at " << token_index;
          this->assert_check(token == this->golden_tokens[token_index],
                             msg_builder.str());
        }
        token_index++;
      }

      call_index++;

      if (return_val == RETURN_WAIT) {
        this->show_info("Returned WAIT", 1);
      }

    } while (this->alloc_size != this->size_buffer[0].range(63, 0));

    // INFO("Reading leftover %lu tokens\n", stream.size());
    while (this->fifo_stream.empty() == false) {

      {
        std::stringstream msg_builder;
        msg_builder << "attempted to read from an empty stream";
        this->assert_check(this->fifo_stream.empty() == false,
                           msg_builder.str());
      }
      auto token = this->fifo_stream.read();
      {
        std::stringstream msg_builder;
        msg_builder << "Golden result mismatch! Expected "
                    << this->golden_tokens[token_index] << " got " << token
                    << " at " << token_index;
        this->assert_check(token == this->golden_tokens[token_index],
                           msg_builder.str());
      }

      token_index++;
    }

    {
      size_t size_0 = this->size_buffer[0].range(63, 0);
      auto cond = size_0 == this->alloc_size;
      std::stringstream msg_builder;
      msg_builder
          << "Not all tokens have been read from the memory! alloc_size = "
          << this->alloc_size << " but size[0] = " << size_0;
    }
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
      this->writeWord(0, ix);
    }
    {
      std::stringstream msg_builder;
      msg_builder << "failed generating tokens, " << this->alloc_size
                  << " != " << this->golden_tokens.size();
      this->assert_check(this->alloc_size == this->golden_tokens.size(),
                         msg_builder.str());
    }
    this->offset_stream.write(0);
  }

public:
  void test() {
    this->initialize();
    using OutputStage = iostage::class_output_stage_mem<T>;
    OutputStage output_stage;
    size_t token_index = 0;
    size_t stream_index = 0;
    size_t call_index = 0;
    do {

      auto pre_count = this->fifo_stream.size();
      output_stage(this->alloc_size, this->size_buffer.data(),
                   this->memory_buffer.data(), pre_count, this->fifo_stream,
                   this->offset_stream);
      auto post_count = this->fifo_stream.size();

      // -- check the tokens written to memory
      size_t size_0 = this->size_buffer[0].range(63, 0);
      auto space_left = FIFO_SIZE - post_count;
      auto tokens_left = this->alloc_size - stream_index;
      auto enqueue_count =
          randint(0, tokens_left > space_left ? space_left : tokens_left);

      {
        std::stringstream msg_builder;
        msg_builder << "OutputStage consumption " << pre_count - post_count
                    << " size " << pre_count;
        msg_builder << "\tActor production " << enqueue_count << " size "
                    << post_count + enqueue_count;
        this->show_info(msg_builder.str(), 1);
      }

      for (; token_index < size_0; token_index++) {
        T token = this->readWord(token_index);
        std::stringstream msg_builder;
        msg_builder << "Expected " << (uint64_t) this->golden_tokens[token_index]
                    << " but got " << (uint64_t) token;

        this->assert_check(token == this->golden_tokens[token_index],
                           msg_builder.str());
      }

      for (auto i = 0; i < enqueue_count; i++) {
        this->fifo_stream.write(this->golden_tokens[stream_index++]);
      }
      call_index++;
    } while (this->alloc_size != this->size_buffer[0].range(63, 0));
    {
      std::stringstream msg_builder;
      msg_builder << "Could not verify all tokens! Produced: "
                  << this->size_buffer[0].range(63, 0)
                  << " Verified: " << token_index;
      this->assert_check(this->size_buffer[0].range(63, 0) == token_index,
                         msg_builder.str());
    }
    {
      std::stringstream msg_builder;
      msg_builder << "Test passed afer " << call_index << " calls";
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
                                     "output stage tester " + msg);
  otester.setVerbose(0);
  otester.test();
  return start_id + 2;
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
      std::cout << "Thread " << std::this_thread::get_id() << " progress "
                << progress << "%" << std::endl;
  }
}

int main() {

  const size_t count = 1000;
  const size_t alloc_size = 1 << 16;
  const auto thread_count = std::thread::hardware_concurrency() / 2;
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
}