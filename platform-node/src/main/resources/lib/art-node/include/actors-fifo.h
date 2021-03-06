/*
 * Copyright (c) Ericsson AB, 2009-2013, EPFL VLSC, 2019
 * Author: Endri Bezati (endri.bezati@epfl.ch)
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

#ifdef REF
#undef FIFO_NAME
#define FIFO_NAME(f) f##_##ref
#define FIFO_TYPE void*
#else
#define FIFO_NAME_3(f, t) f##_##t
#define FIFO_NAME_2(f, t) FIFO_NAME_3(f, t)
#define FIFO_NAME(f) FIFO_NAME_2(f, FIFO_TYPE)
#endif

#if (defined(_WIN32)) || (defined(_WIN64))
#define INL __forceinline
#else
#define INL inline
#endif

/*
 * Number of tokens available on InputPort p
 */
INL unsigned FIFO_NAME(pinAvailIn)(const LocalInputPort *p) {
    return p->available;
}

/*
 * Number of (additional) tokens that would fit into OutputPort p 
 */
INL unsigned FIFO_NAME(pinAvailOut)(const LocalOutputPort *p) {
    return p->spaceLeft;
}

INL void FIFO_NAME(pinWrite)(LocalOutputPort *p, FIFO_TYPE token) {
    FIFO_TYPE *writePtr = (FIFO_TYPE *) p->writePtr;
    assert(p->spaceLeft > 0);

    *(writePtr++) = token;

    if (writePtr >= (FIFO_TYPE *) p->bufferEnd)
        writePtr = (FIFO_TYPE *) p->bufferStart;
    p->writePtr = writePtr;
    p->spaceLeft--;
}

INL void FIFO_NAME(pinWriteRepeat)(LocalOutputPort *p,
                                   FIFO_TYPE *buf,
                                   unsigned int n) {
    char *startPtr = (char *) p->writePtr;
    char *endPtr = (char *) ((FIFO_TYPE *) startPtr + n);
    char *bufferEnd = (char *) p->bufferEnd;

    assert(p->spaceLeft >= n);
    p->spaceLeft -= n;

    if (endPtr >= bufferEnd) {
        // Buffer wrap
        size_t numBytes = bufferEnd - startPtr;
        memcpy(startPtr, buf, numBytes);
        buf = (FIFO_TYPE *) ((char *) buf + numBytes);
        startPtr = (char *) p->bufferStart;
        endPtr = startPtr + (endPtr - bufferEnd);
    }

    memcpy(startPtr, buf, endPtr - startPtr);
    p->writePtr = endPtr;
}


INL FIFO_TYPE FIFO_NAME(pinRead)(LocalInputPort *p) {
    const FIFO_TYPE *readPtr = (const FIFO_TYPE *) p->readPtr;
    FIFO_TYPE result;

    assert(p->available > 0);

    result = (FIFO_TYPE) *readPtr++;

    if (readPtr >= (const FIFO_TYPE *) p->bufferEnd)
        readPtr = (const FIFO_TYPE *) p->bufferStart;
    p->readPtr = readPtr;
    p->available--;
    return result;
}

INL void FIFO_NAME(pinConsume)(LocalInputPort *p) {
    const FIFO_TYPE *readPtr = (const FIFO_TYPE *) p->readPtr;

    assert(p->available > 0);

    readPtr++;

    if (readPtr >= (const FIFO_TYPE *) p->bufferEnd)
        readPtr = (const FIFO_TYPE *) p->bufferStart;
    p->readPtr = readPtr;
    p->available--;

}

INL void FIFO_NAME(pinConsumeRepeat)(LocalInputPort *p,
                                     unsigned int n) {
    const char *startPtr = (char *) p->readPtr;
    const char *endPtr = (char *) ((FIFO_TYPE *) startPtr + n);
    const char *bufferEnd = (char *) p->bufferEnd;

    assert(p->available >= n);
    p->available -= n;

    if (endPtr >= bufferEnd) {
        // Buffer wrap
        //int numBytes = bufferEnd - startPtr;
        startPtr = (char *) p->bufferStart;
        endPtr = startPtr + (endPtr - bufferEnd);
    }
    p->readPtr = endPtr;

}

INL void FIFO_NAME(pinReadRepeat)(LocalInputPort *p,
                                  FIFO_TYPE *buf,
                                  unsigned int n) {
    const char *startPtr = (char *) p->readPtr;
    const char *endPtr = (char *) ((FIFO_TYPE *) startPtr + n);
    const char *bufferEnd = (char *) p->bufferEnd;

    assert(p->available >= n);
    p->available -= n;

    if (endPtr >= bufferEnd) {
        // Buffer wrap
        size_t numBytes = bufferEnd - startPtr;
        memcpy(buf, startPtr, numBytes);
        buf = (FIFO_TYPE *) ((char *) buf + numBytes);
        startPtr = (char *) p->bufferStart;
        endPtr = startPtr + (endPtr - bufferEnd);
    }
    memcpy(buf, startPtr, endPtr - startPtr);
    p->readPtr = endPtr;
}

INL FIFO_TYPE FIFO_NAME(pinPeekFront)(const LocalInputPort *p) {
    return *((FIFO_TYPE *) p->readPtr);
}

INL FIFO_TYPE FIFO_NAME(pinPeek)(const LocalInputPort *p,
                                 int offset) {
    FIFO_TYPE *readPtr = ((FIFO_TYPE *) p->readPtr + offset);
    assert(offset >= 0);

    if (readPtr >= (FIFO_TYPE *) p->bufferEnd) {
        // Buffer wrap
        size_t capacityInBytes = (char *) p->bufferEnd - (char *) p->bufferStart;
        readPtr = (FIFO_TYPE *) ((char *) readPtr - capacityInBytes);
    }
    return *readPtr;
}

INL void FIFO_NAME(pinPeekRepeat)(LocalInputPort *p,
                                  FIFO_TYPE *buf,
                                  unsigned int n) {

    const char *startPtr = (char *) p->readPtr;
    const char *endPtr = (char *) ((FIFO_TYPE *) startPtr + n);
    const char *bufferEnd = (char *) p->bufferEnd;

    assert(p->available >= n);

    if (endPtr >= bufferEnd) {
        // Buffer wrap
        size_t numBytes = bufferEnd - startPtr;
        memcpy(buf, startPtr, numBytes);
        buf = (FIFO_TYPE *) ((char *) buf + numBytes);
        startPtr = (char *) p->bufferStart;
        endPtr = startPtr + (endPtr - bufferEnd);
    }
    memcpy(buf, startPtr, endPtr - startPtr);
}
