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

#include <hls_stream.h>
#include <string.h>

#define MAX_BUFFER_SIZE 4096
#define BURST_SIZE 256

template<typename T>
class class_output_stage{
private :
	int token_counter = 0;

	int burst_counter = 0;

	int program_counter = 0;

	T buffer[BURST_SIZE];

public :
	void operator()(hls::stream< T > &STREAM, bool core_done, int *size,
			T *output);
};


template<typename T>
void class_output_stage< T >::operator()(hls::stream< T > &STREAM, bool core_done, int *size,
		T *output) {
#pragma HLS INLINE


	switch (program_counter) {
	case 0:
		goto CHECK_DONE;
	case 3:
		goto READ;
	}

	CHECK_DONE: {
		if (core_done) {
			goto DONE;
		} else {
			goto READ;
		}
	}

	READ: {
		if (burst_counter < BURST_SIZE) {
			buffer[burst_counter] = STREAM.read();
			burst_counter++;
			program_counter = 3;
			goto OUT;
		} else {
			goto WRITE_TO_MEMORY;
		}

	}

	WRITE_TO_MEMORY: {
		if (token_counter < MAX_BUFFER_SIZE) {
			memcpy((T *) output, buffer, sizeof( T ) * BURST_SIZE);
			token_counter += BURST_SIZE;
			goto OUT;
			program_counter = 0;
		} else {
			goto DONE;
		}

	}

	DONE: {
		*size = token_counter;
		program_counter = 0;
		goto OUT;
	}

	OUT: return;
}

#endif //_OUTPUT_STAGE_H