/*
 * Copyright (c) Ericsson AB, 2009-2013, EPFL VLSC, 2019
 * Author: Endri Bezati (endri.bezati@epfl.ch)
 * Author: Charles Chen Xu (charles.chen.xu@ericsson.com)
 * Author: Patrik Persson (patrik.j.persson@ericsson.com)
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

#ifndef ACTORS_RTS_H
#define ACTORS_RTS_H

#define CAL_RT_CALVIN

#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <assert.h>
#include "actors-typedefs.h"
#include "dllist.h"

/* make the header usable from C++ */
#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define COPY(a)       (a)
#define MEMCPY(d,s,c) (memcpy(d,s,c))

#define RANGECHK(X,B) ((unsigned)(X)<(unsigned)(B)?(X):RANGEERR(X,B))
#define RANGEERR(X,B) (rangeError((X),(B),__FILE__,__LINE__))

  /*
   * These versioning constants are compiled into actors, and then checked
   * by the runtime.
   */
#define ACTORS_RTS_MAJOR     2
#define ACTORS_RTS_MINOR     0

  void fail(const char *fmt, ...) __attribute__ ((noreturn));
  void warn(const char *fmt, ...);

  int rangeError(int x, int y, const char *filename, int line);
  void runtimeError(AbstractActorInstance*, const char *format,...);

  // bool_t represents the CAL-type bool
  typedef int32_t        bool_t;

  // FIXME: temporary workaround
  typedef void art_action_context_t;

  typedef struct LocalOutputPort LocalOutputPort;
  typedef struct LocalInputPort LocalInputPort;

  /*
   * ActorClass and related descriptions
   */

  typedef struct {
    const char  *name;          // action tag
    const int   *consumption;   // token rates of input 0,...,numInputs-1
    const int   *production;    // token rates of output 0,...,numOutputs-1
  } ActionDescription;

  typedef struct {
    char * (*serialize)(void *, char*);
    char * (*deserialize)(void **, char*);
    long (*size)(void *);
    int  (*free)(void *, int);
  } tokenFn;
  
  typedef struct {
    int dummy;                  // FIXME: remove this from generated code
    const char  *name;          // port name
    int          tokenSize;     // sizeof token (in bytes)
    tokenFn* functions;
  } PortDescription;

  struct ActorClass {
    unsigned int majorVersion;               // runtime version
    unsigned int minorVersion;

    char *name;                              // name of actor class
    int sizeActorInstance;                   // size to be allocated for instance

    int numInputPorts;
    const PortDescription *inputPortDescriptions;

    int numOutputPorts;
    const PortDescription *outputPortDescriptions;

    int numActions;
    const ActionDescription *actionDescriptions;

    const int* (*action_scheduler)(AbstractActorInstance*);
    void (*constructor)(AbstractActorInstance*);
    void (*destructor)(AbstractActorInstance*);
    void (*set_param)(AbstractActorInstance*,const char*, const char*);
  };

  // Creates an ActorClass initializer

#define INIT_ActorClass(aClassName,              \
instance_t,              \
ctor,                    \
setParam,                \
sched,                   \
dtor,                    \
nInputs, inputDescr,   \
nOutputs, outputDescr,   \
nActions, actionDescr) { \
.majorVersion=ACTORS_RTS_MAJOR,              \
.minorVersion=ACTORS_RTS_MINOR,              \
.name=aClassName,                            \
.sizeActorInstance=sizeof(instance_t),       \
.numInputPorts=nInputs,                      \
.inputPortDescriptions=inputDescr,           \
.numOutputPorts=nOutputs,                    \
.outputPortDescriptions=outputDescr,         \
.numActions=nActions,                        \
.actionDescriptions=actionDescr,             \
.action_scheduler=sched,                     \
.constructor=ctor,                           \
.destructor=dtor,                            \
.set_param=setParam                          \
}


  /*
   * LocalInputPort (used by FIFO operations)
   */
  struct LocalInputPort {
    const void *bufferStart;          // Start of cyclic buffer
    const void *bufferEnd;            // One past end of cyclic buffer
    const void *readPtr;              // position in cyclic buffer
    unsigned available;               // number of available tokens
  };

  /*
   * LocalOutputPort (used by FIFO operations)
   */

  struct LocalOutputPort {
    void *bufferStart;          // Start of cyclic buffer
    void *bufferEnd;            // One past end of cyclic buffer
    void *writePtr;             // position in cyclic buffer
    unsigned spaceLeft;         // number of available tokens
  };

  /*
   * InputPort
   * Extends LocalInputPort, computes available tokens in pre-fire step,
   * updates tokensConsumed in post-fire step
   */
  struct InputPort {
    dllist_element_t asConsumer;        // member of producer's 'consumers' list

    OutputPort *producer;

    LocalInputPort localInputPort;
    unsigned tokensConsumed;          // number of tokens consumed
    unsigned drainedAt;               // point at which all tokensConsumed
    unsigned capacity;                // minimum capacity of buffer (in tokens)

    tokenFn functions;                // functions to handle structured tokens
  };

  /*
   * OutputPort
   * Extends LocalOutputPort: computes spaceLeft in pre-fire step,
   * updates tokensProduced in post-fire step
   */
  struct OutputPort {
    LocalOutputPort localOutputPort;
    unsigned capacity;                   // capacity of buffer (in tokens)
    unsigned tokensProduced;             // number of tokens produced
    unsigned fullAt;                     // tokensProduced when FIFO is full
    
    tokenFn functions;                   // functions to handle structured tokens

    dllist_head_t consumers;
  };

  /*
   * AbstractActorInstance, the "base class" which is common
   * to all actor instances
   */

  struct AbstractActorInstance {
    dllist_element_t listEntry;           // to keep instance in list

    const ActorClass *actorClass;
    const char       *instanceName;

    int numInputPorts;
    InputPort *inputPort;

    int numOutputPorts;
    OutputPort *outputPort;

    const int* (*action_scheduler)(AbstractActorInstance*);
  };

#define ART_INPUT(index) &(thisActor->base.inputPort[index].localInputPort)

#define ART_OUTPUT(index) &(thisActor->base.outputPort[index].localOutputPort)


  // Action-scheduler exit code (first element of array)
  // EXITCODE_TERMINATE = actor is dead
  // EXITCODE_BLOCK(n)  = actor blocks on either of n ports
  // EXITCODE_YIELD     = actor yielded, but may be fireable

extern const int exit_code_terminate[];
extern const int exit_code_yield[];

//const int exit_code_terminate[] = {-1};
//const int exit_code_yield[] = {-2};

#define EXITCODE_TERMINATE exit_code_terminate
#define EXITCODE_BLOCK(n)  (n)
#define EXIT_CODE_YIELD    exit_code_yield

  /*
   * The following macros provide a mechanism that allows
   * for local copies of Input/Output ports (not used in present
   * implementation)
   */
#define ART_ACTION_CONTEXT(numInputs, numOutputs)

#define ART_ACTION_SCHEDULER(name)        \
static const int *name(AbstractActorInstance *pBase)

#define ART_ACTION_SCHEDULER_ENTER(numInputs, numOutputs) \
  void *context = NULL; context = context;

#define ART_ACTION_SCHEDULER_EXIT(numInputs, numOutputs)

  /*
   * The following macros provide a mechanism that allow
   * the loop within the action scheduler to be customized
   * (not used in the present implementation)
   */
#define ART_ACTION_SCHEDULER_LOOP          while (1)
#define ART_ACTION_SCHEDULER_LOOP_TOP
#define ART_ACTION_SCHEDULER_LOOP_BOTTOM

  /*
   * The following macros allow the declaration of actions
   * to be customized
   */

#define ART_ACTION(name, thistype)          \
static void name(thistype *thisActor)


#define ART_INIT_SCOPE(name, thistype)                    \
    static void name(thistype *thisActor)

#define ART_SCOPE(name, thistype)                    \
    static void name(art_action_context_t *context, thistype *thisActor)

#define ART_CONDITION(name, thistype)            \
  static _Bool name(art_action_context_t *context, thistype *thisActor)

#define ART_FIRE_ACTION(name)      \
  name(thisActor)


#define ART_EXEC_TRANSITION(name)        \
  name(thisActor)

#define ART_TEST_CONDITION(name)         \
  name(context, thisActor)

#ifdef TRACE
#define ART_ACTION_ENTER(name, index)   \
  actionTrace((AbstractActorInstance*)thisActor,index,#name)
#else
#define ART_ACTION_ENTER(name, index)
#endif

#define ART_ACTION_EXIT(name, index)

//#define dprint1(x,y)
//#define dprint2(x,y,z)

// FIXME: workarounds to handle System.bitops in RVC
  static inline int32_t bitand(int32_t x, int32_t y) { return x & y; }
  static inline int32_t bitor(int32_t x, int32_t y) { return x | y; }
  static inline int32_t bitxor(int32_t x, int32_t y) { return x ^ y; }
  static inline int32_t bitnot(int32_t x) { return ~x; }
  static inline int32_t lshift(int32_t x, int32_t n) { return x << n; }
  static inline int32_t rshift(int32_t x, int32_t n) { return x >> n; }

  /*
   * FIFO operations:
   *
   * pinAvailIn_<FIFO_TYPE>(inputPort)
   * pinRead_<FIFO_TYPE>(inputPort)
   * pinReadRepeat_<FIFO_TYPE>(inputPort, buffer, numTokens)
   * pinPeekFront_<FIFO_TYPE>(inputPort)
   * pinPeek_<FIFO_TYPE>(inputPort, index)
   *
   * pinAvailOut_<FIFO_TYPE>(outputPort)
   * pinWrite_<FIFO_TYPE>(outputPort, token)
   * pinWriteRepeat_<FIFO_TYPE>(outputPort, buffer, numTokens)
   */

#define FIFO_TYPE int32_t

#include "actors-fifo.h"

#undef FIFO_TYPE
#define FIFO_TYPE int16_t

#include "actors-fifo.h"

#undef FIFO_TYPE
#define FIFO_TYPE _Bool

#include "actors-fifo.h"

#undef FIFO_TYPE
#define FIFO_TYPE double

#include "actors-fifo.h"

#undef FIFO_TYPE
#define FIFO_TYPE int8_t
#define BYTES

#include "actors-fifo.h"

#undef FIFO_TYPE
#undef BYTES
#define REF

#include "actors-fifo.h"

#undef FIFO_TYPE
#undef REF

// Define uint32_t FIFO operations in terms of int32_t operations
#define pinAvailIn_uint32_t(port)   pinAvailIn_int32_t(port)
#define pinAvailOut_uint32_t(port)  pinAvailOut_int32_t(port)

#define pinWrite_uint32_t(port, token) pinWrite_int32_t(port,(int32_t)(token))
#define pinWriteRepeat_uint32_t(port, buf, n) \
                                 pinWriteRepeat_int32_t(port,(int32_t*)(buf),n)

#define pinRead_uint32_t(port)        ((uint32_t) pinRead_int32_t(port))
#define pinReadRepeat_uint32_t(port, buf, n)  \
                                 pinReadRepeat_int32_t(port,(int32_t*)buf,n)

#define pinPeekRepeat_uint32_t(port, buf, n) \
                                 pinPeekRepeat_int32_t(port,(int32_t*)buf,n)
#define pinPeekFront_uint32_t(port)   ((uint32_t) pinPeekFront_int32_t(port))
#define pinPeek_uint32_t(port, offset) ((uint32_t) pinPeek_int32_t(port,offset))

#define pinConsume_uint32_t(port)   pinConsume_int32_t(port)
#define pinConsumeRepeat_uint32_t(port, n)   pinConsumeRepeat_int32_t(port, n)


// Define uint16_t FIFO operations in terms of int16_t operations
#define pinAvailIn_uint16_t(port)   pinAvailIn_int16_t(port)
#define pinAvailOut_uint16_t(port)  pinAvailOut_int16_t(port)
#define pinWrite_uint16_t(port, token) pinWrite_int16_t(port,(int16_t)(token))
#define pinWriteRepeat_uint16_t(port, buf, n) \
                                 pinWriteRepeat_int16_t(port,(int16_t*)(buf),n)

#define pinRead_uint16_t(port)        ((uint16_t) pinRead_int16_t(port))
#define pinReadRepeat_uint16_t(port, buf, n)  \
                                 pinReadRepeat_int16_t(port,(int16_t*)buf,n)

#define pinPeekRepeat_uint16_t(port, buf, n) \
                                 pinPeekRepeat_int16_t(port,(int16_t*)buf,n)
#define pinPeekFront_uint16_t(port)   ((uint16_t) pinPeekFront_int16_t(port))
#define pinPeek_uint16_t(port, offset) ((uint16_t) pinPeek_int16_t(port,offset))

#define pinConsume_uint16_t(port)   pinConsume_int16_t(port)
#define pinConsumeRepeat_uint16_t(port, n)   pinConsumeRepeat_int16_t(port, n)

// Define uint8_t FIFO operations in terms of int8_t operations
#define pinAvailIn_uint8_t(port)   pinAvailIn_int8_t(port)
#define pinAvailOut_uint8_t(port)  pinAvailOut_int8_t(port)
#define pinWrite_uint8_t(port, token) pinWrite_int8_t(port,(int8_t)(token))
#define pinWriteRepeat_uint8_t(port, buf, n) \
                                 pinWriteRepeat_int8_t(port,(int8_t*)(buf),n)

#define pinRead_uint8_t(port)        ((uint8_t) pinRead_int8_t(port))
#define pinReadRepeat_uint8_t(port, buf, n)  \
                                 pinReadRepeat_int8_t(port,(int8_t*)buf,n)

#define pinPeekRepeat_uint8_t(port, buf, n) \
                                 pinPeekRepeat_int8_t(port,(int8_t*)buf,n)
#define pinPeekFront_uint8_t(port)   ((uint8_t) pinPeekFront_int8_t(port))
#define pinPeek_uint8_t(port, offset) ((uint8_t) pinPeek_int8_t(port,offset))

#define pinConsume_uint8_t(port)   pinConsume_int8_t(port)
#define pinConsumeRepeat_uint8_t(port, n)   pinConsumeRepeat_int8_t(port, n)
  /*
   * Operations for dynamically sized tokens (socket
   * sender/receiver). This is _not_ to support tokens of different
   * sizes in the same FIFO. Rather, this is to push tokens to a FIFO
   * with a fixed token size, but that token size is determined at
   * runtime.
   */

  static inline unsigned pinAvailIn_dyn(const LocalInputPort *p)
  {
    return p->available;
  }

  static inline void pinRead_dyn(LocalInputPort *p,
                                 void *token,       /* output */
                                 size_t tokenSize)
  {
    const char * readPtr = p->readPtr;
    assert(p->available > 0);

    memcpy(token, readPtr, tokenSize);
    readPtr += tokenSize;

    if (readPtr >= (char *) p->bufferEnd)
      readPtr = p->bufferStart;
    p->readPtr = readPtr;
    p->available --;
  }


static inline void pinRead_dynRepeat(LocalInputPort *p,
                                     void *token,       /* output */
                                     size_t tokenSize,
                                     int n)
{
    const char *startPtr = (char *) p->readPtr;
    const char *endPtr = (char *) ((char *) startPtr + n*tokenSize);
    const char *bufferEnd = (char *) p->bufferEnd;

    assert(p->available >= n);
    p->available -= n;

    if (endPtr >= bufferEnd) {
        // Buffer wrap
        int numBytes = bufferEnd - startPtr;
        memcpy(token, startPtr, numBytes);
        token = (char *) ((char *) token + numBytes);
        startPtr = p->bufferStart;
        endPtr = startPtr + (endPtr - bufferEnd);
    }
    memcpy(token, startPtr, endPtr - startPtr);
    p->readPtr = endPtr;
}


  static inline unsigned pinAvailOut_dyn(const LocalOutputPort *p)
  {
    return p->spaceLeft;
  }

  static inline void pinWrite_dyn(LocalOutputPort *p,
                                  const void *token,
                                  size_t tokenSize)
  {
    char *writePtr = p->writePtr;
    assert(p->spaceLeft > 0);

    /* FIXME: this is not terribly efficient for small tokens */
    memcpy(writePtr, token, tokenSize);
    writePtr += tokenSize;

    if (writePtr >= (char *) p->bufferEnd)
      writePtr = p->bufferStart;
    p->writePtr = writePtr;
    p->spaceLeft --;
  }


static inline void pinWrite_dynRepeat(LocalOutputPort *p,
                                      const void *token,
                                      size_t tokenSize,
                                      unsigned int n)
{
    char *startPtr = (char *) p->writePtr;
    char *endPtr = (char *) ((char *) startPtr + n*tokenSize);
    char *bufferEnd = (char *) p->bufferEnd;

    assert(p->spaceLeft >= n);

    if (endPtr >= bufferEnd) {
        // Buffer wrap
        int numBytes = bufferEnd - startPtr;
        memcpy(startPtr, token, numBytes);
        token = (char *) ((char *) token + numBytes);
        startPtr = p->bufferStart;
        endPtr = startPtr + (endPtr - bufferEnd);
    }

    memcpy(startPtr, token, endPtr - startPtr);
    p->writePtr = endPtr;
    p->spaceLeft-=n;
}

static inline void pinWrite_dynRepeat_offset(LocalOutputPort *p,
                                             const void *token,
                                             size_t tokenSize,
                                             int offset,
                                             unsigned int n)
{
    char *startPtr = (char *) p->writePtr;
    char *endPtr = (char *) ((char *) startPtr + n*tokenSize);
    char *bufferEnd = (char *) p->bufferEnd;

    char *t = (char*) token;

    if (endPtr >= bufferEnd) {
        // Buffer wrap
        int numBytes = bufferEnd - startPtr;
        memcpy(startPtr, &t[offset*tokenSize], numBytes);
        t = (char *) ((char *) token + numBytes);
        startPtr = p->bufferStart;
        endPtr = startPtr + (endPtr - bufferEnd);
    }

    memcpy(startPtr, &t[offset*tokenSize], endPtr - startPtr);
    p->writePtr = endPtr;
    p->spaceLeft-=n;
}



#ifdef __cplusplus
}
#endif

#endif
