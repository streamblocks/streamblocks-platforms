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

#include <ap_int.h>
#include <globals.h>
#include <hls_stream.h>
#include <stdint.h>
#define MIN(A, B) ((A > B) ? B : A)

namespace iostage {

/**
 * Constants
 * Do not change these constants if you do not know where they are used
 **/
using const_t = unsigned int;
/* Width of the AXI bus in bytes */
const const_t BUS_BYTE_WIDTH = 64;
/* Width of the AXI bus in bits */
const const_t BUS_WIDTH = (BUS_BYTE_WIDTH << 3);
/* The maximum number of bytes that can be transferred in a single burst, this
 * is 4KiB according to AXI4 spec*/
const const_t MAX_BURST_TX = 4096;
/* Maximum number of bus lines that can be transferred in a burst), the maximum
   number of beats/lines
   in a burst is 256
*/
const const_t MAX_BURST_LINES = MIN(256, MAX_BURST_TX / BUS_BYTE_WIDTH);

/* The type aliasing for a bus line */
using bus_t = ap_uint<BUS_WIDTH>;

using ret_t = uint32_t;

struct partial_bus_t {
  bus_t bus_line;
  uint8_t len;
};

/**
 * Input stage template class
 **/
template <typename T> class class_input_stage_mem {

private:
  const const_t token_size = sizeof(T);
  const const_t tokens_per_bus_line = BUS_BYTE_WIDTH / sizeof(T);

  bus_t burst_buffer[MAX_BURST_LINES];
  partial_bus_t partial_bus_line;
  uint64_t tokens_read = 0; // the number of tokens read and streamed out
  uint64_t bus_lines_read = 0;

public:
  /**
   * The input stage operation. Read from the memory and writes it as a stream
   * to the output stream.
   * @param requested_size the size of the memory block to read and stream
   * @param size the actual size of the memory block read and streamed. The
   *             input stage may not be able to read the whole requested_size
   *             block due to back-pressure
   * @param input the pointer to the base of the memory block
   * @param fifo_count the number of existing tokens in the output fifo
   * @param fifo_size the capacity of the output fifo
   * @param STREAM the output stream where the input stage streams the memory
   *               block
   * @param OFFSET the offset of the memory block. A token on the OFFSET stream
   *               also indicates that the input stage should be initialized.
   * @return the return code status of the operation code be
   *         RETURN_EXECUTED: The input stage has streamed some data to STREAM
   *         RETURN_WAIT: The input stage has not been able to stream anything
   **/
  ret_t operator()(uint32_t requested_size, bus_t *size, bus_t *input,
                   uint32_t fifo_count, uint32_t fifo_size,
                   hls::stream<T> &STREAM, hls::stream<uint64_t> &OFFSET) {
#pragma HLS INLINE
    // Our FIFO IPs can overflow by one token and they still work. So we need
    // to make sure the fifo_space variable does not underflow
    uint32_t fifo_space = fifo_count > fifo_size ? 0 : fifo_size - fifo_count;
    uint32_t tokens_left = requested_size - this->tokens_read;
    uint32_t tokens_to_read = MIN(tokens_left, fifo_space);

    bool can_read = tokens_to_read != 0;
    bool should_start = !OFFSET.empty();

    uint32_t return_code = 0;

    if (should_start) {

      this->tokens_read = OFFSET.read();
      uint8_t partial_tokens = this->tokens_read % this->tokens_per_bus_line;
      uint32_t bus_lines_to_skip = 0;
      if (this->tokens_read != 0)
        bus_lines_to_skip =
            (this->tokens_read - 1) / this->tokens_per_bus_line + 1;

      if (partial_tokens != 0) {
        this->partial_bus_line.bus_line = input[bus_lines_to_skip - 1];
      }
      this->partial_bus_line.len = partial_tokens;
      this->bus_lines_read = bus_lines_to_skip;

      size[0] = tokens_read;

      return_code = RETURN_EXECUTED;

    } else if (can_read) {

      uint64_t base_address = this->bus_lines_read;
      // Prologue of the stream, the bus line may be present on the device
      // already
      uint32_t tokens_on_device = 0;
      if (this->partial_bus_line.len != 0)
        tokens_on_device =
            this->tokens_per_bus_line - this->partial_bus_line.len;

      uint32_t tokens_in_memory = tokens_to_read;
      uint32_t stream_prologue_size = tokens_on_device;
      if (this->partial_bus_line.len != 0) {
        if (tokens_to_read < tokens_on_device) {
          stream_prologue_size = tokens_to_read;
          tokens_in_memory = 0;
        } else {
          stream_prologue_size = tokens_on_device;
          tokens_in_memory = tokens_to_read - tokens_on_device;
        }
      }

      // Epilogue of the stream, the last bus line may only be partially
      // streamed
      uint32_t stream_epilogue_size = tokens_in_memory % tokens_per_bus_line;

      // Body of the stream, need to read bus line from the memory
      uint32_t bus_lines_to_read = 0; // if lucky, we already have the bus line
      if (tokens_in_memory != 0)
        bus_lines_to_read =
            (tokens_in_memory - 1) / this->tokens_per_bus_line + 1;
      uint32_t full_bus_lines_to_read;

      if (stream_epilogue_size != 0)
        full_bus_lines_to_read = bus_lines_to_read - 1;
      else
        full_bus_lines_to_read = bus_lines_to_read;

      /**
       * Prologue, we already have the bus line on the device
       * Note that we may not be able to stream all the tokens that are
       * already on the device
       **/
      if (this->partial_bus_line.len != 0) {
      stream_prologue:
        for (uint16_t k = this->partial_bus_line.len;
             k < this->partial_bus_line.len + stream_prologue_size; k++) {
#pragma HLS pipeline
          uint16_t token_bits = this->token_size * 8;
          uint16_t high_range = (k + 1) * token_bits - 1;
          uint16_t low_range = k * token_bits;
          T tmp_val =
              this->partial_bus_line.bus_line.range(high_range, low_range);
          STREAM.write(tmp_val);
        }

        this->partial_bus_line.len =
            (this->partial_bus_line.len + stream_prologue_size) %
            this->tokens_per_bus_line;
      }

    /**
     * The body of stream, streams full bus lines
     **/
    read_and_stream:
      for (uint32_t i = 0; i < full_bus_lines_to_read; i += MAX_BURST_LINES) {

        uint32_t chunk_size = MAX_BURST_LINES;
        if ((i + MAX_BURST_LINES) >=
            full_bus_lines_to_read) // last chunk may not be a full burst
          chunk_size = full_bus_lines_to_read - i;
        uint64_t chunk_base_address = i + base_address;
      // Burst read from gmem to local burst buffer
      burst_rd:
        for (uint32_t j = 0; j < chunk_size; j++) {
#pragma HLS pipeline
          burst_buffer[j] = input[chunk_base_address + j];
        }

      stream_chunk:
        for (uint32_t j = 0; j < chunk_size; j++) {
#pragma HLS pipeline
          bus_t bus_line = burst_buffer[j];
        stream_body:
          for (uint16_t k = 0; k < tokens_per_bus_line; k++) {
#pragma HLS pipeline
            uint16_t token_bits = this->token_size * 8;
            uint16_t high_range = (k + 1) * token_bits - 1;
            uint16_t low_range = k * token_bits;
            T tmp_val = bus_line.range(high_range, low_range);
            STREAM.write(tmp_val);
          }
        }
      }

      /**
       * Epilogue stream. Read the last bus line, stream it partially and save
       * it on the device
       **/
      if (stream_epilogue_size != 0) {
        uint64_t last_address = base_address + full_bus_lines_to_read;
        this->partial_bus_line.bus_line = input[last_address];
        this->partial_bus_line.len = stream_epilogue_size;
        bus_t last_bus_line = this->partial_bus_line.bus_line;
      stream_epilogue:
        for (uint16_t k = 0; k < stream_epilogue_size; k++) {
#pragma HLS pipeline
          uint16_t token_bits = this->token_size * 8;
          uint16_t high_range = (k + 1) * token_bits - 1;
          uint16_t low_range = k * token_bits;
          T tmp_val = last_bus_line.range(high_range, low_range);
          STREAM.write(tmp_val);
        }
      }

      this->tokens_read += tokens_to_read;
      this->bus_lines_read += bus_lines_to_read;
      size[0] = this->tokens_read;
      return_code = RETURN_EXECUTED;

    } else {
      return_code = RETURN_WAIT;
    }
    return return_code;
  }
};

/**
 * Output stage template class
 **/
template <typename T> class class_output_stage_mem {
private:
  const const_t token_size = sizeof(T);
  const const_t tokens_per_bus_line = BUS_BYTE_WIDTH / sizeof(T);

  uint64_t tokens_written = 0;
  uint64_t full_bus_lines_written = 0;
  partial_bus_t partial_bus_line;
  bus_t burst_buffer[MAX_BURST_LINES];
  uint64_t partial_writes = 0;

public:
  /**
   * The output stage operation. Reads the input stream and streams it to the
   * memory.
   * @param buffer_capacity The capacity of the memory buffer
   * @param size The actual capacity of the memory used
   * @param output The memory buffer pointer
   * @param fifo_count The number of tokens in the input stream STREAM
   * @param STREAM the input stream
   * @param OFFST the offset of the memory block. A token on the OFFSET stream
   *              also indicates that the output stage should be initialized.
   * @return the return code status of the operation code be
   *         RETURN_EXECUTED: The input stage has streamed some data to STREAM
   *         RETURN_WAIT: The input stage has not been able to stream anything
   **/
  ret_t operator()(uint32_t buffer_capacity, bus_t *size, bus_t *output,
                   uint32_t fifo_count, hls::stream<T> &STREAM,
                   hls::stream<uint64_t> &OFFSET) {
#pragma HLS INLINE

    uint32_t buffer_space = (buffer_capacity - this->tokens_written);
    uint32_t tokens_to_write = MIN(buffer_space, fifo_count);

    uint32_t return_code = 0;
    bool should_start = !OFFSET.empty();
    bool can_write = tokens_to_write != 0;
    if (should_start) {
      this->tokens_written = OFFSET.read();

      uint16_t partial_tokens =
          this->tokens_written % this->tokens_per_bus_line;
      this->partial_bus_line.bus_line = 0;
      this->partial_bus_line.len = partial_tokens;

      this->full_bus_lines_written =
          (this->tokens_written / this->tokens_per_bus_line);
      size[0] = tokens_written;
      return_code = RETURN_EXECUTED;

    } else if (can_write) {

      uint32_t burst_buffer_index = 0;
      uint32_t extended_tokens_to_write =
          tokens_to_write + (uint32_t) this->partial_bus_line.len;
      uint32_t bus_lines_to_write =
          (extended_tokens_to_write - 1) / this->tokens_per_bus_line + 1;

      uint32_t last_partial_tokens =
          extended_tokens_to_write % this->tokens_per_bus_line;
      uint32_t full_bus_lines_to_write = bus_lines_to_write;
      if (last_partial_tokens != 0)
        full_bus_lines_to_write = bus_lines_to_write - 1;

      if (this->partial_bus_line.len != 0) {

        uint16_t tokens_to_fill_bus_line =
            MIN(this->tokens_per_bus_line - this->partial_bus_line.len,
                tokens_to_write);
      fill_partial:
        for (uint16_t i = 0; i < tokens_to_fill_bus_line; i++) {
#pragma HLS pipeline
          T tmp_token = STREAM.read();
          uint16_t token_bits = this->token_size * 8;
          uint16_t high_range =
              (this->partial_bus_line.len + i + 1) * token_bits - 1;
          uint16_t low_range = (this->partial_bus_line.len + i) * token_bits;
          this->partial_bus_line.bus_line.range(high_range, low_range) =
              tmp_token;
        }

        this->partial_bus_line.len =
            (this->partial_bus_line.len + tokens_to_fill_bus_line) %
            this->tokens_per_bus_line;

        burst_buffer[burst_buffer_index++] = this->partial_bus_line.bus_line;
      }

    chunck_wr:
      for (uint32_t i = 0; i < bus_lines_to_write; i += MAX_BURST_LINES) {

        uint32_t chunk_size = MAX_BURST_LINES;
        bool last_chunk = false;
        if ((i + MAX_BURST_LINES) >= bus_lines_to_write) {
          chunk_size = bus_lines_to_write - i;
          last_chunk = true;
        }

        uint32_t full_chunk_size = chunk_size;
        bool has_partial_last_line = last_chunk && last_partial_tokens != 0;
        if (has_partial_last_line)
          full_chunk_size = chunk_size - 1;

      // Read from the tokens, concatenate them and put them in the burst buffer
      fill_burst_buffer:
        for (; burst_buffer_index < chunk_size; burst_buffer_index++) {
#pragma HLS pipeline
          bus_t tmp_bus_line = 0;
          uint16_t j_end = this->tokens_per_bus_line;
          bool last_line = burst_buffer_index == chunk_size - 1;
          if (last_line && has_partial_last_line) {
            j_end = last_partial_tokens;
            this->partial_writes++;
          }
        collect_tokens:
          for (uint16_t j = 0; j < j_end; j++) {
#pragma HLS pipeline
            uint16_t token_bits = this->token_size * 8;
            uint16_t high_range = (j + 1) * token_bits - 1;
            uint16_t low_range = j * token_bits;
            T tmp_token = STREAM.read();
            tmp_bus_line.range(high_range, low_range) = tmp_token;
          }
          if (last_line) {
            if (has_partial_last_line) {
              this->partial_bus_line.len = last_partial_tokens;
              this->partial_bus_line.bus_line = tmp_bus_line;
            } else {
              // This is the last line and its full so reset the
              // partial_bus_line
              this->partial_bus_line.len = 0;
              this->partial_bus_line.bus_line = 0;
            }
          }
          burst_buffer[burst_buffer_index] = tmp_bus_line;
        }

        burst_buffer_index = 0;

      // Now burst the burst_buffer to memory
      burst_wr:
        uint64_t local_base_address = i + this->full_bus_lines_written;
        for (uint32_t j = 0; j < chunk_size; j++) {
#pragma HLS pipeline
          output[j + local_base_address] = burst_buffer[j];
        }
      }

      this->tokens_written += tokens_to_write;
      this->full_bus_lines_written += full_bus_lines_to_write;
      size[0] = this->tokens_written;
      return_code = RETURN_EXECUTED;
    } else {
      return_code = RETURN_WAIT;
    }
    return return_code;
  }
};
}

#endif // __IOSTAGE_H__
