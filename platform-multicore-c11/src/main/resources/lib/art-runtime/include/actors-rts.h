/* 
 * Copyright (c) Ericsson AB, 2009
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

#ifndef _ACTORS_RTS_H
#define _ACTORS_RTS_H

#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
#include <pthread.h>
#include <stdatomic.h>


#include <assert.h>

/* make the header usable from C++ */
#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

// LEGACY
#define INPUT_PORT(ignore, index) ART_INPUT(index)
#define OUTPUT_PORT(ignore, index) ART_OUTPUT(index)
// ENDLEGACY

#if defined(__i386__)

#define CACHE_LINE_SIZE 64

#define mb()    asm volatile("mfence":::"memory")
#define rmb()   asm volatile("lfence":::"memory")
#define wmb()   asm volatile("sfence" ::: "memory")

#elif defined(__x86_64__)

#define CACHE_LINE_SIZE 64

#define mb()    asm volatile("mfence":::"memory")
#define rmb()   asm volatile("lfence":::"memory")
#define wmb()   asm volatile("sfence" ::: "memory")

#elif defined(__arm__)

#define CACHE_LINE_SIZE 64

//#error Not implemented yet
#define mb()
#define rmb()
#define wmb()

#endif

#define COPY(a)       (a)
#define MEMCPY(d, s, c) (memcpy(d,s,c))

#define RANGECHK(X, B) ((unsigned)(X)<(unsigned)(B)?(X):RANGEERR(X,B))
#define RANGEERR(X, B) (rangeError((X),(B),__FILE__,__LINE__))

typedef int32_t bool_t;

typedef struct {
    char *key;
    char *value;
} ActorParameter;

typedef struct ActorClass ActorClass;
typedef struct OutputPort OutputPort;
typedef struct InputPort InputPort;
typedef struct AbstractActorInstance AbstractActorInstance;

typedef struct LocalContext {
    int pos;
    int count;
    int available;
} LocalContext;

typedef struct {
    atomic_int value;
} atomic_value_t;
#define atomic_get(a) ((a)->value)
#define atomic_set(a, v) (((a)->value) = (v))

typedef struct SharedContext {
    atomic_value_t count;
} SharedContext;

struct InputPort {
    // Struct accessed between cores, only semi-constant data
    int index;     // index into shared/local vectors
    int cpu;       // The CPU where this port currently resides
    AbstractActorInstance *actor;
    SharedContext *shared;
    LocalContext *local;
    void *buffer;
    unsigned capacity;
    const OutputPort *writer;
};

struct OutputPort {
    // Struct accessed between cores, only semi-constant data
    int index;     // index into shared/local vectors
    int cpu;       // The CPU where this port currently resides
    AbstractActorInstance *actor;
    SharedContext *shared;
    LocalContext *local;
    void *buffer;
    unsigned capacity;
    int readers;
    InputPort **reader;
};

typedef struct {
    int pos;
    int available;
    void *buffer;
    unsigned capacity;
} LocalInputPort;

typedef struct {
    int pos;
    int available;
    void *buffer;
    unsigned capacity;
} LocalOutputPort;

struct AbstractActorInstance {
    ActorClass *actor;                    //actor
    char *name;
    int cpu_index;       // The CPU where this actor currently resides
    int outputs;
    OutputPort *output;
    int inputs;
    InputPort *input;
    int fired;
    int terminated;
    long long nloops;
    unsigned long long total;
    int firstActionIndex;
    FILE *traceFile;
    int *cpu; // For active actor to wakeup the sleeping thread
};

typedef struct {
    const char *name;
    const int *consumption;
    const int *production;
} ActionDescription;

typedef struct {
    int isBytes;
    const char *name;
    int tokenSize;
} PortDescription;

struct ActorClass {
    char *name;
    int numInputPorts;
    int numOutputPorts;
    int sizeActorInstance;

    const int *(*action_scheduler)(AbstractActorInstance *, int);

    void (*constructor)(AbstractActorInstance *);

    void (*destructor)(AbstractActorInstance *);

    void (*set_param)(AbstractActorInstance *, const char *, const char *);

    const PortDescription *inputPortDescriptions;
    const PortDescription *outputPortDescriptions;
    int actorExecMode;
    int numActions;
    const ActionDescription *actionDescriptions;
};

// Creates an ActorClass initializer

#define INIT_ActorClass(aClassName, \
                        instance_t, \
                        ctor, \
                        setParam, \
                        sched, \
                        dtor, \
                        nInputs, inputDescr, \
                        nOutputs, outputDescr, \
                        nActions, actionDescr) { \
    .name=aClassName,                            \
    .numInputPorts=nInputs,                      \
    .numOutputPorts=nOutputs,                    \
    .sizeActorInstance=sizeof(instance_t),       \
    .action_scheduler=sched,                     \
    .constructor=ctor,                           \
    .destructor=dtor,                            \
    .set_param=setParam,                         \
    .inputPortDescriptions=inputDescr,           \
    .outputPortDescriptions=outputDescr,         \
    .actorExecMode=0,                            \
    .numActions=nActions,                        \
    .actionDescriptions=actionDescr              \
  }

// Action-scheduler exit code (first element of array)
// EXITCODE_TERMINATE = actor is dead
// EXITCODE_BLOCK(n)  = actor blocks on either of n ports
// EXITCODE_YIELD     = actor yielded, but may be fireable

extern const int exit_code_terminate[];
extern const int exit_code_yield[];

#define EXITCODE_TERMINATE exit_code_terminate
#define EXITCODE_BLOCK(n)  (n)
#define EXIT_CODE_YIELD    exit_code_yield


extern AbstractActorInstance *actorInstance[];
extern int log_level;

extern void trace(int level, const char *, ...);

extern int rangeError(int x, int y, const char *filename, int line);

extern void runtimeError(AbstractActorInstance *, const char *format, ...);

extern void actionTrace(AbstractActorInstance *instance,
                        int localActionIndex,
                        char *actionName);

extern AbstractActorInstance *createActorInstance(ActorClass *actorClass);

extern OutputPort *createOutputPort(AbstractActorInstance *pInstance,
                                    const char *portName,
                                    int numberOfReaders);

extern InputPort *createInputPort(AbstractActorInstance *pInstance,
                                  const char *portName,
                                  int capacity);

extern void connectPorts(OutputPort *outputPort, InputPort *inputPort);

extern int executeNetwork(int argc, char *argv[], AbstractActorInstance **instances, int numInstances);

extern void setParameter(AbstractActorInstance *pInstance,
                         const char *key,
                         const char *value);

extern void setParameterBytes(AbstractActorInstance *pInstance,
                              const char *key,
                              const void *value,
                              int size);


#define ART_INPUT(index) &(context->input[index])
#define ART_OUTPUT(index) &(context->output[index])

#define ART_ACTION_CONTEXT(numInputs, numOutputs)    \
  typedef struct art_action_context {            \
    int fired;                        \
    int loop;                        \
    LocalInputPort input[numInputs];            \
    LocalOutputPort output[numOutputs];            \
  } art_action_context_t;


#define ART_SCOPE(name, thistype)                    \
    static void name(thistype *thisActor)

#define ART_CONDITION(name, thistype)            \
  static _Bool name(art_action_context_t *context, thistype *thisActor)

#define ART_ACTION(name, thistype)                    \
  static void name(art_action_context_t *context, thistype *thisActor)

#define ART_ACTION_SCHEDULER(name)                \
  static const int *name(AbstractActorInstance *pBase,        \
             int maxloops)

#define ART_ACTION_SCHEDULER_ENTER(numInputs, numOutputs)        \
  art_action_context_t theContext;                    \
  art_action_context_t *context = &theContext;                \
  context->fired = 0;                            \
  {                                    \
    int i;                                \
    for (i = 0 ; i < numInputs ; i++) {                    \
      /* cpu is not used in actors */                    \
      /* shared is not used in actors */                \
      InputPort *input=pBase->input+i;                                  \
      int available=input->local->available;                            \
      if (pBase->cpu_index==input->writer->cpu) {                       \
        /* make use of latest local production when on same CPU */      \
        available += input->writer->local->count;                       \
        input->local->available=available;                              \
      }                                                                 \
      context->input[i].available = available;                          \
      context->input[i].pos = input->local->pos;                \
      context->input[i].buffer = input->buffer;                        \
      context->input[i].capacity = input->capacity;                \
      /* writer is not used in actors */                \
    }                                    \
    for (i = 0 ; i < numOutputs ; i++) {                \
      /* cpu is not used in actors */                    \
      /* shared is not used in actors */                \
      context->output[i].pos = pBase->output[i].local->pos;        \
      context->output[i].available = pBase->output[i].local->available;    \
      context->output[i].buffer = pBase->output[i].buffer;        \
      context->output[i].capacity = pBase->output[i].capacity;            \
      /* readers is not used in actors */                \
      /* reader is not used in actors */                \
    }                                    \
  }

#define ART_ACTION_SCHEDULER_LOOP                    \
  for (context->loop = 0 ; context->loop < maxloops ; context->loop++)

#define ART_ACTION_SCHEDULER_LOOP_TOP

#define ART_ACTION_SCHEDULER_LOOP_BOTTOM

#define ART_ACTION_SCHEDULER_EXIT(numInputs, numOutputs)        \
  if (context->fired){                            \
    int i;                                \
    pBase->fired = context->fired;                    \
    for (i = 0 ; i < numInputs ; i++) {                    \
      pBase->input[i].local->pos = context->input[i].pos;        \
      pBase->input[i].local->count =                    \
    pBase->input[i].local->available - context->input[i].available;    \
      pBase->input[i].local->available = context->input[i].available;    \
    }                                    \
    for (i = 0 ; i < numOutputs ; i++) {                \
      pBase->output[i].local->pos = context->output[i].pos;        \
      pBase->output[i].local->count =                    \
    pBase->output[i].local->available - context->output[i].available; \
      pBase->output[i].local->available = context->output[i].available; \
    }                                    \
  }

#define ART_FIRE_ACTION(name)            \
  name(context, thisActor)

#define ART_EXEC_TRANSITION(name)        \
  name(context, thisActor)

#define ART_TEST_CONDITION(name)         \
  name(context, thisActor)

#ifdef TRACE
#define ART_ACTION_ENTER(name, index)   \
  context->fired++; \
  actionTrace((AbstractActorInstance*)thisActor,index,#name)
#else
#define ART_ACTION_ENTER(name, index)		\
  context->fired++
#endif

#define ART_ACTION_EXIT(name, index)

#define FIFO_TYPE int32_t

#include "actors-fifo.h"

#undef FIFO_TYPE
#define FIFO_TYPE int16_t

#include "actors-fifo.h"

#undef FIFO_TYPE
#define FIFO_TYPE bool_t

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

#ifdef __cplusplus
}
#endif

#endif
