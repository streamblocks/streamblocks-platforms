/*
 * Copyright (c) StreamGenomics Sarl, 2020
 * Author: Endri Bezati (endri.bezati@streamgenomics.com)
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
#ifndef __MEM_WRITER_H
#define __MEM_WRITER_H

#include <stdint.h>
#include "hls_stream.h"
#include "ap_int.h"

class class_mem_writer {
private:
	int32_t* mem;
	const int packet_size;
public:
	class_mem_writer(int packet_size) :
			packet_size(packet_size) {
	}
	int operator()(int32_t* mem, uint32_t size, hls::stream<uint32_t> &STREAM) {
#pragma HLS INLINE
		this->mem = mem;

		int rest = size;
		int written = 0;
		int counter = 0;

		if (size == 0) {
			return 0;
		}

		do {
			if (rest >= packet_size) {
				for (int i = 0; i < packet_size; i++) {
#pragma HLS PIPELINE
					mem[i + counter] = STREAM.read();
					written++;
					rest--;
				}
				counter += packet_size;
			} else {
				int rem = rest & (packet_size - 1);
				for (int i = 0; i < rem; i++) {
#pragma HLS PIPELINE
					mem[i + counter] = STREAM.read();
					written++;
					rest--;
				}
				counter += rem;
				for (int i = rem; i < packet_size; i++) {
#pragma HLS PIPELINE
					STREAM.read();
				}
			}
		} while (rest != 0);

		return written;
	}
};

#endif

