 /* Copyright (c) EPFL VLSC, 2019
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

#ifndef _OUTPUT_STAGE_CONTROL_H
#define _OUTPUT_STAGE_CONTROL_H

#define RECEIVING 0
#define WAITING_INPUT 1
#define FINISH 4

#include <stdint.h>
#include <hls_stream.h>

#define MAX_BUFFER_SIZE 4096

template<typename T>
class class_output_stage_control {
public:
	uint32_t operator()(hls::stream<T> &STREAM_IN, hls::stream<T> &STREAM_OUT,
			bool core_done);
};

template<typename T>
uint32_t class_output_stage_control<T>::operator()(hls::stream<T> &STREAM_IN,
		hls::stream<T> &STREAM_OUT, bool core_done) {
#pragma HLS INLINE

	int ret = WAITING_INPUT;

	CHECK_DONE: {
		if (core_done) {
			goto READ_REMAINING_DATA;
		} else {
			goto READ;
		}
	}

	READ: {
		T value;
		if (STREAM_IN.read_nb(value)) {
			STREAM_OUT.write(value);
			ret = RECEIVING;
			goto OUT;
		} else {
			ret = WAITING_INPUT;
			goto OUT;
		}
	}

	READ_REMAINING_DATA: {
		T value;
		if (STREAM_IN.read_nb(value)) {
			STREAM_OUT.write(value);
			ret = RECEIVING;
			goto OUT;
		} else {
			goto DONE;
		}
	}

	DONE: {
		ret = FINISH;
		goto OUT;
	}

	OUT: {
		return ret;
	}

}

#endif //_OUTPUT_STAGE_CONTROL_H