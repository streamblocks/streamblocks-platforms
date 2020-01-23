/*
 * Copyright (c) EPFL VLSC, 2019
 * Author: Endri Bezati (endri.bezati@epfl.ch)
 *         Mahyar Emami (mahyar.emami@epfl.ch)
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

#ifndef _OUTPUT_STAGE_MEM_H
#define _OUTPUT_STAGE_MEM_H

#include <globals.h>
#include <hls_stream.h>
#include <stdint.h>

#define MAX_BUFFER_SIZE 4096
#define MIN(A, B) ((A > B) ? B : A)

template <typename T> class class_output_stage_mem {
private:
  uint64_t offset = 0;
  uint64_t written_size = 0;
  const int c_size = MAX_BUFFER_SIZE;

public:
  uint32_t operator()(uint32_t buffer_capacity, uint32_t *size, T *output,
                      uint32_t fifo_count, hls::stream<T> &STREAM,
                      hls::stream<uint64_t> &OFFSET) {
#pragma HLS INLINE
    uint32_t available_buffers = (buffer_capacity - offset);
    uint32_t to_write = MIN(available_buffers, fifo_count);

    // Conditions
    bool should_start = !OFFSET.empty();
    bool can_write = (to_write != 0) && (!STREAM.empty());

    uint32_t return_code = 0;
    if (should_start) { // Init action
      offset = OFFSET.read();
      written_size = 0;
      size[0] = 0;
      return_code = RETURN_EXECUTED;
    } else if (can_write) { // Write action
    mem_wr:
      for (uint64_t i = 0; i < to_write; i++) {
#pragma HLS pipeline
#pragma HLS LOOP_TRIPCOUNT min = c_size max = c_size
        output[i + offset] = STREAM.read();
      }
      offset += to_write;
      written_size += to_write;
      size[0] = written_size;
      return_code = RETURN_EXECUTED;
    } else {
      return_code = RETURN_WAIT;
    }

    return return_code;
  }
};

#endif //_OUTPUT_STAGE_MEM_H
