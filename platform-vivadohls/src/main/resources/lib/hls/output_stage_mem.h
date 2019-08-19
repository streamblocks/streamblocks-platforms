/*
 * Copyright (c) EPFL VLSC, 2019
 * Author: Endri Bezati (endri.bezati@epfl.ch)
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

#define DONE_RECEIVING 2
#define FINISH 4

#include <stdint.h>
#include <hls_stream.h>

#define MAX_BUFFER_SIZE 4096

template<typename T>
class class_output_stage_mem {
private:
	uint64_t pointer = 0;
	const int c_size = MAX_BUFFER_SIZE;
public:
	uint32_t operator()(hls::stream<T> &STREAM, uint32_t fifo_count,
			uint32_t available_size, uint32_t *size, T *output);
};

template<typename T>
uint32_t class_output_stage_mem<T>::operator()(hls::stream<T> &STREAM,
		uint32_t fifo_count, uint32_t available_size, uint32_t *size,
		T *output) {
#pragma HLS INLINE

	uint32_t rest = available_size - pointer;
	uint64_t to_send =
			rest > fifo_count ?
					fifo_count : rest;

	if (available_size == 0) {
		return DONE_RECEIVING;
	}

	if (available_size == pointer) {
		size[0] = pointer;
		pointer = 0;
		return FINISH;
	}

	mem_wr: for (uint64_t i = 0; i < to_send; i++) {
#pragma HLS pipeline
#pragma HLS LOOP_TRIPCOUNT min=c_size max=c_size
		output[i + pointer] = STREAM.read();
	}

	pointer += to_send;

	return DONE_RECEIVING;
}

#endif //_OUTPUT_STAGE_MEM_H