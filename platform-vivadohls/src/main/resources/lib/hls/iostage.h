/*
 * Copyright (c) EPFL VLSC, 2019
 * Author:  Mahyar Emami (mahyar.emami@epfl.ch)
 *          Endri Bezati (endri.bezati@epfl.ch)
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

#ifndef __IOSTAGE_H__
#define __IOSTAGE_H__

// -- Actor Return values
#define RETURN_IDLE 0
#define RETURN_WAIT 1
#define RETURN_TEST 2
#define RETURN_EXECUTED 3

#include <cstring>
#include <hls_stream.h>
#include <stdint.h>

#define MIN(A, B) ((A > B) ? B : A)

namespace iostage {

constexpr size_t log2(size_t n) { return ((n < 2) ? 1 : 1 + log2(n / 2)); }
using const_t = unsigned int;
// using bus_t = ap_uint<BUS_BIT_WIDTH>;
using ret_t = uint32_t;
/*
   A memory buffer object

 */
template <typename T> struct CircularBuffer {
  T *data_buffer;
  T *meta_buffer;
  uint32_t alloc_size; // size of the buffer in tokens, limited to 3 Gi Tokens
                       // and must be a power of 2
  uint32_t head;       // the head pointer, the next token to write to
  uint32_t tail;       // the tail pointer, the next token to read from
};

template <typename T, uint32_t FIFO_SIZE> struct BusInterface {
  static constexpr const_t BUS_BYTE_WIDTH = sizeof(T);
  static constexpr const_t BUS_BIT_WIDTH = BUS_BYTE_WIDTH << 3;
  // Maximum number of bytes that can be transferred in a burst
  static constexpr const_t MAX_BURST_TX = 4096;
  // Maximum number of transfers in a single burst, i.e., words transferred
  // in a single burst read or write
  static constexpr const_t MAX_BURST_LINES =
      MIN(256, MAX_BURST_TX / BUS_BYTE_WIDTH);

  // maximum number of single bursts in function call
  // since each burst transfers up to MAX_BURST_LINES tokens, then the
  // maximum number of such bursts is FIFO_SIZE / MAX_BURST_LINES
  static constexpr const_t MAX_NUMBER_OF_BURSTS = FIFO_SIZE / MAX_BURST_LINES;
  virtual uint32_t tokensToProcess(uint32_t fifo_count) = 0;

  virtual uint32_t
  operator()(T *ocl_buffer_data_buffer, T *ocl_buffer_meta_buffer,
             uint32_t ocl_buffer_alloc_size, uint32_t ocl_buffer_head,
             uint32_t ocl_buffer_tail, uint32_t fifo_count,
             hls::stream<T> &data_stream, hls::stream<bool> &meta_stream) = 0;
};

template <typename T, uint32_t FIFO_SIZE>
class InputMemoryStage : public BusInterface<T, FIFO_SIZE> {
private:
  uint32_t head, tail, alloc_size;

public:
  InputMemoryStage() {
    alloc_size = 0;
    head = 0;
    tail = 0;
  }

  inline uint32_t tokensToProcess(uint32_t fifo_count) override {
    uint32_t fifo_space = fifo_count > FIFO_SIZE ? 0 : FIFO_SIZE - fifo_count;
    uint32_t tokens_in_mem = tokenCount();
    uint32_t tokens_to_read = MIN(fifo_space, tokens_in_mem);
    return tokens_to_read;
  }
  uint32_t operator()(T *ocl_buffer_data_buffer, T *ocl_buffer_meta_buffer,
                      uint32_t ocl_buffer_alloc_size, uint32_t ocl_buffer_head,
                      uint32_t ocl_buffer_tail, uint32_t fifo_count,
                      hls::stream<T> &data_stream,
                      hls::stream<bool> &meta_stream) override {

#pragma HLS INLINE
    uint32_t return_code = RETURN_WAIT;

    bool should_init = !meta_stream.empty();

    if (should_init) {
      // read the init token
      meta_stream.read();

      this->alloc_size = ocl_buffer_alloc_size;
      this->tail = ocl_buffer_tail;
      this->head = ocl_buffer_head;

      return_code = RETURN_EXECUTED;
    } else {

      uint32_t tokens_to_read = tokensToProcess(fifo_count);

      if (tokens_to_read > 0) {

        // There could be two read loop depending on the value of head and tail
        // pointers if (tail < head) then we read from data_buffer[tail] up
        // data_buffer[head - 1] but if (tail > head) then we first read from
        // the data_buffer[tail] to data_buffer[alloc_size - 1] and then from
        // data_buffer[0] to data_buffer[head - 1].

        if (this->tail + tokens_to_read >= this->alloc_size) {
          readLoop(&ocl_buffer_data_buffer[this->tail],
                   this->alloc_size - this->tail, data_stream);
          tokens_to_read -= this->alloc_size - this->tail;
          this->tail = 0;
        }

        if (tokens_to_read > 0) {
          readLoop(&ocl_buffer_data_buffer[this->tail], tokens_to_read,
                   data_stream);
          this->tail += tokens_to_read;
        }

        return_code = RETURN_EXECUTED;
      } else {
        return_code = RETURN_WAIT;
      }
    }
    if (return_code == RETURN_EXECUTED) {
      writeMeta(ocl_buffer_meta_buffer);
    }
    return return_code;
  }

private:
  void readLoop(T *mem_data, const uint32_t read_size,
                hls::stream<T> &data_stream) {
#pragma HLS INLINE
    // uint32_t current_ix = start_ix;
    // T *mem_data = &data_buffer[start_ix];
    uint32_t tag = 0;
    for (uint32_t burst_ix = 0; burst_ix < read_size;
         burst_ix += this->MAX_BURST_LINES) {
#pragma HLS loop_tripcount min = 1 max = this->MAX_NUMBER_OF_BURSTS
      uint32_t chunk_size = ((burst_ix + this->MAX_BURST_LINES) >= read_size)
                                ? (read_size - burst_ix)
                                : this->MAX_BURST_LINES;

    read_loop:
      for (uint32_t ix = 0; ix < chunk_size; ix++) {
#pragma HLS pipeline II = 1
#pragma HLS loop_tripcount min = 1 max = this->MAX_BURST_LINES

        // Read the data from the memory, tag it and store it in the burst
        // buffer
        T token = mem_data[burst_ix + ix];
        data_stream.write_nb(token);
      }
    }
  }
  inline void writeMeta(T *meta_buffer) {
#pragma HLS INLINE
    // meta_buffer[0] = this->tail;
    if (sizeof(T) >= sizeof(uint32_t)) {
      meta_buffer[0] = this->tail;
    } else {
      uint32_t current_tail = this->tail;
      for (int ix = 0; ix < sizeof(uint32_t) / sizeof(T); ix++) {
#pragma HLS unroll
        meta_buffer[ix] = current_tail;
        current_tail = current_tail >> (sizeof(T) << 3);
      }
    }
  }
  inline uint32_t tokenCount() const {
#pragma HLS INLINE
    if (this->head == this->tail) {
      return 0;
    } else if (this->head > this->tail) {
      return this->head - this->tail;
    } else { // this->this->head < this->tail
      return this->alloc_size - this->tail + this->head;
    }
  }

  // local copies of head, tail and alloc_size
};

template <typename T, uint32_t FIFO_SIZE>
class OutputMemoryStage : public BusInterface<T, FIFO_SIZE> {
public:
  OutputMemoryStage() {
    tail = 0;
    head = 0;
    alloc_size = 0;
  }
  inline uint32_t tokensToProcess(uint32_t fifo_count) override {
    uint32_t space_left = getSpaceLeft();
    uint32_t tokens_to_write = MIN(space_left, fifo_count);
    return tokens_to_write;
  }

  uint32_t operator()(T *ocl_buffer_data_buffer, T *ocl_buffer_meta_buffer,
                      uint32_t ocl_buffer_alloc_size, uint32_t ocl_buffer_head,
                      uint32_t ocl_buffer_tail, uint32_t fifo_count,
                      hls::stream<T> &data_stream,
                      hls::stream<bool> &meta_stream) override {
#pragma HLS INLINE
    bool should_init = !meta_stream.empty();
    uint32_t return_code = RETURN_WAIT;

    if (should_init) {
      meta_stream.read();
      this->tail = ocl_buffer_tail;
      this->head = ocl_buffer_head;
      this->alloc_size = ocl_buffer_alloc_size;
      return_code = RETURN_EXECUTED;
    } else {

      uint32_t tokens_to_write = tokensToProcess(fifo_count);

      if (tokens_to_write > 0) {

        // There are two scenarios to write to the DDR buffer:
        // (i) head < tail: then write from buffer[head] to buffer[tail - 1]
        // (ii) tail < head: then write from buffer[head] to buffer[alloc_size -
        // 1] and from buffer[0] to buffer[tail - 1]

        if (this->head + tokens_to_write >= this->alloc_size) {
          // wrap around
          writeLoop(&ocl_buffer_data_buffer[this->head],
                    this->alloc_size - this->head, data_stream);
          tokens_to_write -= this->alloc_size - this->head;
          this->head = 0;
        }
        if (tokens_to_write > 0) {
          writeLoop(&ocl_buffer_data_buffer[this->head], tokens_to_write,
                    data_stream);

          this->head += tokens_to_write;
        }

        return_code = RETURN_EXECUTED;

      } else {
        return_code = RETURN_WAIT;
      }
    }

    if (return_code == RETURN_EXECUTED) {
      writeMeta(ocl_buffer_meta_buffer);
    }

    return return_code;
  }

private:
  // each OutputMemoryStage writes at the head of a circular buffer and the
  // software plink will read from the tail.
  inline uint32_t getSpaceLeft() {
#pragma HLS INLINE
    if (this->tail == this->head) {
      return this->alloc_size - 1;
    } else if (this->head < this->tail) {
      return this->tail - 1 - this->head;
    } else { // this->tail < this->head
      return (this->alloc_size - this->head) + (this->tail - 1);
    }
  }

  void writeLoop(T *mem_data, uint32_t write_size,
                 hls::stream<T> &data_stream) {
#pragma HLS INLINE

    for (uint32_t burst_ix = 0; burst_ix < write_size;
         burst_ix += this->MAX_BURST_LINES) {
#pragma HLS loop_tripcount min = 1 max = this->MAX_NUMBER_OF_BURSTS

      uint32_t chunk_size = ((burst_ix + this->MAX_BURST_LINES) >= write_size)
                                ? (write_size - burst_ix)
                                : this->MAX_BURST_LINES;
    write_loop:
      for (uint32_t ix = 0; ix < chunk_size; ix++) {
#pragma HLS pipeline II = 1
#pragma HLS loop_tripcount min = 1 max = this->MAX_BURST_LINES
        T token;
        // implicit assertion, data_stream.empty() == false
        data_stream.read_nb(token);
        // std::cout << "writing : " << uint64_t(token) << std::endl;
        mem_data[ix + burst_ix] = token;
      }
    }
  }

  void writeMeta(T *meta_buffer) {
#pragma HLS INLINE

    if (sizeof(T) >= sizeof(uint32_t)) {
      meta_buffer[0] = this->head;
    } else {

      uint32_t current_head = this->head;
      for (int ix = 0; ix < sizeof(uint32_t) / sizeof(T); ix++) {
#pragma HLS unroll
        meta_buffer[ix] = current_head;
        current_head = current_head >> (sizeof(T) << 3);
      }
    }
  }
  uint32_t tail, head, alloc_size;
};

} // namespace iostage

#endif // __IOSTAGE_H__
