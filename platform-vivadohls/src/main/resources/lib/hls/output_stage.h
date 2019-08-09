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

#ifndef _OUTPUT_STAGE_H
#define _OUTPUT_STAGE_H

#define RECEIVING 0
#define WAITING_INPUT 1
#define WRITING_TO_MEMORY 2
#define BURST_WRITING_TO_MEMORY 3
#define DONE_RECEIVING 4

#include <stdint.h>
#include <hls_stream.h>

#define BURST_SIZE 256

template<typename T>
class class_output_stage {
private:
	int token_counter = 0;

	int burst_counter = 0;

	int program_counter = 0;

	T buffer[BURST_SIZE];

public:
	uint32_t operator()(hls::stream< T > &STREAM, bool core_done, uint32_t available_size, uint32_t *size, uint64_t *output);



};

template<typename T>
uint32_t class_output_stage<T>::operator()(hls::stream< T > &STREAM, bool core_done, uint32_t available_size, uint32_t *size, uint64_t *output) {
#pragma HLS INLINE

	int ret = RECEIVING;

	switch (program_counter) {
	case 0:
		goto CHECK_DONE;
	case 1:
		goto CHECK_INPUT;
	}

	CHECK_DONE: {
		if (core_done) {
			goto DONE_AVAILABLE_DATA;
		} else {
			if (burst_counter == BURST_SIZE - 1) {
				goto BURST_WRITE_TO_MEMORY;
			} else {
				goto CHECK_INPUT;
			}
		}
	}

	DONE_AVAILABLE_DATA: {
		if (STREAM.empty()) {
			goto WRITE_TO_MEMORY;
		} else {
			goto READ;
		}
	}

	CHECK_INPUT: {
		if (STREAM.empty()) {
			program_counter = 0;
			ret = WAITING_INPUT;
			goto OUT;
		} else {
			goto READ;
		}
	}

	READ: {
		buffer[burst_counter] = STREAM.read();
		burst_counter++;
		program_counter = 0;
		ret = RECEIVING;
		goto OUT;
	}

	BURST_WRITE_TO_MEMORY: {
		for (int i = 0; i < BURST_SIZE; i++) {
#pragma HLS PIPELINE
			output[token_counter + i] = buffer[i];
		}
		burst_counter = 0;
		token_counter += BURST_SIZE;
		program_counter = 0;
		ret = BURST_WRITING_TO_MEMORY;
		// -- Buffer is full --> go to DONE
		if (token_counter == available_size - 1) {
			goto DONE;
		} else {
			goto OUT;
		}
	}

	WRITE_TO_MEMORY: {
		for (int i = 0; i < burst_counter; i++) {
#pragma HLS LOOP_TRIPCOUNT min=0 max=255
#pragma HLS PIPELINE
			output[token_counter + i] = buffer[i];
		}
		token_counter += burst_counter;
		burst_counter = 0;
		program_counter = 0;
		ret = WRITING_TO_MEMORY;
		goto DONE;
	}

	DONE: {
		*size = token_counter;
		token_counter = 0;
		burst_counter = 0;
		program_counter = 0;
		ret = DONE_RECEIVING;
		goto OUT;
	}

	OUT: return ret;
}

#endif //_OUTPUT_STAGE_H
