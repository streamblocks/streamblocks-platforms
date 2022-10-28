/*
 * Copyright (c) Ericsson AB, 2009-2013
 * Author: Charles Chen Xu (charles.chen.xu@ericsson.com)
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

#include "io-port.h"
#include <assert.h>

#ifdef REF
#undef FIFO_NAME
#define FIFO_NAME(f) f##_##ref
#define FIFO_TYPE void*
#else
#define FIFO_NAME_3(f,t) f##_##t
#define FIFO_NAME_2(f,t) FIFO_NAME_3(f, t)
#define FIFO_NAME(f) FIFO_NAME_2(f, FIFO_TYPE)
#endif

/*
 * Number of tokens available on InputPort p
 */
static inline unsigned FIFO_NAME(pinAvailIn)(const InputPort *p)
{
  return input_port_available(p);
}

/*
 * Number of (additional) tokens that would fit into OutputPort p 
 */
static inline unsigned FIFO_NAME(pinAvailOut)(const OutputPort *p) 
{
  return output_port_space_left(p);
}

static inline void FIFO_NAME(pinWrite)(OutputPort *p, FIFO_TYPE token) 
{
  output_port_write(p, sizeof token, &token);
}

static inline void FIFO_NAME(pinWriteRepeat)(OutputPort *p,
                                             FIFO_TYPE *buf,
                                             int n) 
{
  int i = 0;
  for (; i < n ; ++i) {
    assert(output_port_space_left(p) > 0);
    output_port_write(p, sizeof *buf, buf+i);
  }
}


static inline FIFO_TYPE FIFO_NAME(pinRead)(InputPort *p) 
{
  FIFO_TYPE token;
  input_port_read(p, sizeof token, &token);
  return token;
}

static inline void FIFO_NAME(pinReadRepeat)(InputPort *p,
                                            FIFO_TYPE *buf,
                                            int n) 
{
  int i;
  for (i = 0; i < n; ++i) {
    input_port_read(p, sizeof *buf, buf+i);
  }
}

static inline FIFO_TYPE FIFO_NAME(pinPeekFront)(const InputPort *p)
{
  FIFO_TYPE token;
  input_port_peek(p, 0, sizeof token, &token);
  return token;
}

static inline FIFO_TYPE FIFO_NAME(pinPeek)(const InputPort *p, 
    int offset)
{
  FIFO_TYPE token;
  input_port_peek(p, offset, sizeof token, &token);
  return token;
}

static inline void FIFO_NAME(pinConsume)(InputPort *p)
{
  input_port_consume(p);
}
