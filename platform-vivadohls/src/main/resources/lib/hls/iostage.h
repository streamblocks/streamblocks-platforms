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

#include "globals.h"
#include <ap_int.h>
#include <cstring>
#include <hls_stream.h>
#include <stdint.h>

#define MIN(A, B) ((A > B) ? B : A)

namespace iostage {
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

template <typename T> struct BusInterface {
  static constexpr const_t BUS_BYTE_WIDTH = sizeof(T);
  static constexpr const_t BUS_BIT_WIDTH = BUS_BYTE_WIDTH << 3;
  static constexpr const_t MAX_BURST_TX = 4096;
  static constexpr const_t MAX_BURST_LINES =
      MIN(256, MAX_BURST_TX / BUS_BYTE_WIDTH);
};

template <typename T> class InputMemoryStage : public BusInterface<T> {

public:
  InputMemoryStage() {
    alloc_size = 0;
    head = 0;
    tail = 0;
  }

  uint32_t operator()(CircularBuffer<T> ocl_buffer, uint32_t fifo_count,
                      uint32_t fifo_size, hls::stream<T> &data_stream,
                      hls::stream<bool> &meta_stream) {
#pragma HLS INLINE
    uint32_t return_code = RETURN_WAIT;

    bool should_init = !meta_stream.empty();

    if (should_init) {
      // read the init token
      meta_stream.read();

      this->alloc_size = ocl_buffer.alloc_size;
      this->tail = ocl_buffer.tail;
      this->head = ocl_buffer.head;

      return_code = RETURN_EXECUTED;
    } else {

      uint32_t fifo_space = fifo_count > fifo_size ? 0 : fifo_size - fifo_count;
      uint32_t tokens_in_mem = tokenCount();
      uint32_t tokens_to_read = MIN(fifo_space, tokens_in_mem);

      if (tokens_to_read > 0) {

        // There could be two read loop depending on the value of head and tail
        // pointers if (tail < head) then we read from data_buffer[tail] up
        // data_buffer[head - 1] but if (tail > head) then we first read from
        // the data_buffer[tail] to data_buffer[alloc_size - 1] and then from
        // data_buffer[0] to data_buffer[head - 1].
        if (this->tail < this->head) {

          readLoop(ocl_buffer.data_buffer, tail, tokens_to_read, data_stream);
          this->tail += tokens_to_read;

        } else {
          uint32_t tail_to_end_tokens = this->alloc_size - this->tail;
          uint32_t read_size = MIN(tail_to_end_tokens, tokens_to_read);
          bool wraps_around = (tail_to_end_tokens < tokens_to_read);
          readLoop(ocl_buffer.data_buffer, tail, read_size, data_stream);
          if (wraps_around) {
            uint32_t tokens_left_to_read = tokens_to_read - tail_to_end_tokens;
            read_size = MIN(this->head, tokens_left_to_read);
            readLoop(ocl_buffer.data_buffer, 0, read_size, data_stream);
            tail = read_size;
          } else {
            tail += read_size;
          }
        }
        // meta_buffer[0] = tail;
        // writeMeta(ocl_buffer.meta_buffer);
        return_code = RETURN_EXECUTED;
      } else {
        return_code = RETURN_WAIT;
      }
    }
    if (return_code == RETURN_EXECUTED) {
      writeMeta(ocl_buffer.meta_buffer);
    }
    return return_code;
  }

private:
  void readLoop(T *data_buffer, const uint32_t start_ix,
                const uint32_t read_size, hls::stream<T> &data_stream) {
#pragma HLS INLINE
    uint32_t current_ix = start_ix;
    for (uint32_t burst_ix = 0; burst_ix < read_size;
         burst_ix += this->MAX_BURST_LINES) {

      uint32_t chunk_size = ((burst_ix + this->MAX_BURST_LINES) >= read_size)
                                ? (read_size - burst_ix)
                                : this->MAX_BURST_LINES;
    read_loop:
      for (uint32_t ix = 0; ix < chunk_size; ix++) {
#pragma HLS pipeline
#pragma HLS loop_tripcount min = 0 max = this->MAX_BURST_LINES
        T token = data_buffer[current_ix];
        data_stream.write_nb(token);
        current_ix++;
      }
    }
  }
  inline void writeMeta(T *meta_buffer) {
#pragma HLS INLINE
    uint32_t *dest = reinterpret_cast<uint32_t *>(meta_buffer);
    // std::cout << "Writing tail at " << uintptr_t(meta_buffer) << std::endl;
    // std::cout << "tail: " << this->tail << std::endl;
    dest[0] = this->tail;
    // std::memcpy(meta_buffer, &this->tail, sizeof(uint32_t));
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
  uint32_t head, tail, alloc_size;
};

template <typename T> class OutputMemoryStage : public BusInterface<T> {

public:
  OutputMemoryStage() {
    tail = 0;
    head = 0;
    alloc_size = 0;
  }

  uint32_t operator()(CircularBuffer<T> ocl_buffer, uint32_t fifo_count,
                      hls::stream<T> &data_stream,
                      hls::stream<bool> &meta_stream) {
#pragma HLS INLINE
    bool should_init = !meta_stream.empty();
    uint32_t return_code = RETURN_WAIT;

    if (should_init) {
      meta_stream.read();
      this->tail = ocl_buffer.tail;
      this->head = ocl_buffer.head;
      this->alloc_size = ocl_buffer.alloc_size;
      return_code = RETURN_EXECUTED;
    } else {

      // space left on the DDR buffer
      uint32_t space_left = getSpaceLeft();

      uint32_t tokens_to_write = MIN(space_left, fifo_count);

      if (tokens_to_write > 0) {

        // There are two scenarios to write to the DDR buffer:
        // (i) head < tail: then write from buffer[head] to buffer[tail - 1]
        // (ii) tail < head: then write from buffer[head] to buffer[alloc_size -
        // 1] and from buffer[0] to buffer[tail - 1]
        if (this->head < this->tail) {
          // we have to assert that this->head + tokens_to_write <= this->tail
          writeLoop(ocl_buffer.data_buffer, this->head, tokens_to_write,
                    data_stream);
          this->head += tokens_to_write;
        } else { // this->

          // in this case we may have to wrap-around in the write
          uint32_t space_before_wrapping = this->alloc_size - this->head;

          uint32_t write_size = MIN(tokens_to_write, space_before_wrapping);

          writeLoop(ocl_buffer.data_buffer, this->head, write_size,
                    data_stream);

          if (space_before_wrapping < tokens_to_write) {
            uint32_t tokens_to_wrap = tokens_to_write - space_before_wrapping;
            writeLoop(ocl_buffer.data_buffer, 0, tokens_to_wrap, data_stream);
            this->head = tokens_to_wrap;
          } else {
            this->head += write_size;
          }
        }

        return_code = RETURN_EXECUTED;
      } else {
        return_code = RETURN_WAIT;
      }
    }

    if (return_code == RETURN_EXECUTED) {
      writeMeta(ocl_buffer.meta_buffer);
    }

    return return_code;
  }

private:
  // each OutputMemoryStage writes at the head of a circular buffer and the
  // software plink will read from the tail.
  inline uint32_t getSpaceLeft() {
#pragma HLS INLINE
    if (this->tail == this->head) {
      return this->alloc_size;
    } else if (this->head < this->tail) {
      return this->tail - this->head;
    } else { // this->tail < this->head
      return (this->alloc_size - this->head) + this->tail;
    }
  }

  void writeLoop(T *data_buffer, uint32_t start_ix, uint32_t write_size,
                 hls::stream<T> &data_stream) {
#pragma HLS INLINE
    uint32_t current_ix = start_ix;
    for (uint32_t burst_ix = 0; burst_ix < write_size;
         burst_ix += this->MAX_BURST_LINES) {

      uint32_t chunk_size = ((burst_ix + this->MAX_BURST_LINES) >= write_size)
                                ? (write_size - burst_ix)
                                : this->MAX_BURST_LINES;
    write_loop:
      for (uint32_t ix = 0; ix < chunk_size; ix++) {
#pragma HLS pipeline
#pragma HLS loop_tripcount min = 0 max = this->MAX_BURST_LINES
        T token;
        // implicit assertion, data_stream.empty() == false
        data_stream.read_nb(token);
        // std::cout << "writing : " << uint64_t(token) << std::endl;
        data_buffer[current_ix] = token;
        current_ix++;
      }
    }
  }

  void writeMeta(T *meta_buffer) {
#pragma HLS INLINE
    uint32_t *dest = reinterpret_cast<uint32_t *>(meta_buffer);

    dest[0] = this->head;
  }
  uint32_t tail, head, alloc_size;
};

} // namespace iostage

#endif // __IOSTAGE_H__
