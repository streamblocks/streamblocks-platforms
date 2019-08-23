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

#ifndef _INPUT_STAGE_MEM_H
#define _INPUT_STAGE_MEM_H


#define RETURN_IDLE 0
#define RETURN_WAIT_PREDICATE 1
#define RETURN_WAIT_INPUT 2
#define RETURN_WAIT_OUTPUT 3
#define RETURN_WAIT_GUARD 4
#define RETURN_EXECUTED 5

#include <stdint.h>
#include <hls_stream.h>
#include <string.h>

#define MAX_BUFFER_SIZE 4096

template<typename T>
class class_input_stage_mem {
private:
	uint64_t pointer = 0;
	const int c_size = MAX_BUFFER_SIZE;
public:
	uint32_t operator()(uint32_t requested_size, uint32_t *size,
			T *input, uint32_t fifo_count, uint32_t fifo_size,  hls::stream<T> &STREAM);
};

template<typename T>
uint32_t class_input_stage_mem<T>::operator()(uint32_t requested_size, uint32_t *size,
		T *input, uint32_t fifo_count, uint32_t fifo_size, hls::stream<T> &STREAM) {
#pragma HLS INLINE

	uint32_t available_size = fifo_size - fifo_count;
	uint64_t rest = requested_size - pointer;
	uint64_t to_read = rest > available_size ?  available_size : rest;

	if(requested_size == 0){
		size[0] = pointer;
		pointer = 0;
		return RETURN_IDLE;
	}


	if(requested_size == pointer){
		size[0] = pointer;
		pointer = 0;
		return RETURN_IDLE;
	}

	mem_rd: for(uint64_t i = 0; i < to_read; i++){
#pragma HLS pipeline
#pragma HLS LOOP_TRIPCOUNT min=c_size max=c_size
		STREAM << input[i + pointer];
	}

	pointer+= to_read;
	if (requested_size == pointer) {
		pointer = 0;
		return RETURN_IDLE;
	}
	return RETURN_EXECUTED;
}

#endif //_INPUT_STAGE_MEM_H
