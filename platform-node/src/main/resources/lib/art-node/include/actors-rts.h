/*
 * Copyright (c) Ericsson AB, 2009-2013
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

#include "actors-coder.h"
#include "actors-typedefs.h"
#include "dllist.h"
#include "io-port.h"
#include "logging.h"
#include "slist.h"
#include "prelude.h"
#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#ifdef USE_TORCH
#include <torch/torch.h>
#include "serialization.h"

typedef torch::Tensor Tensor;
#endif

/* make the header usable from C++ */
#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define COPY(a) (a)
#define MEMCPY(d, s, c) (memcpy(d, s, c))

/*
 * These versioning constants are compiled into actors, and then checked
 * by the runtime.
 */
#define ACTORS_RTS_MAJOR 2
#define ACTORS_RTS_MINOR 0

// bool_t represents the CAL-type bool
typedef int32_t bool_t;

// FIXME: temporary workaround
typedef void art_action_context_t;

/*
 * ActorClass and related descriptions
 */

typedef struct {
  const char *name; // action tag
  const char *originalName;
  const int *consumption; // token rates of input 0,...,numInputs-1
  const int *production;  // token rates of output 0,...,numOutputs-1
  const int *uses;
  const int *defines;
} ActionDescription;

typedef struct {
  int dummy;        // FIXME: remove this from generated code
  const char *name; // port name
  int tokenSize;    // sizeof token (in bytes)
  tokenFn *functions;
} PortDescription;

enum ConditionKind { INPUT_KIND, OUTPUT_KIND, PREDICATE_KIND };


/*!\struct ConditionDescription
 * \brief actor machine condition
 *
 * Describes an actor mahcine condition.
 */
typedef struct {
    const char *name;
    const enum ConditionKind kind;
    const int port;
    const int count;
    const int *stateVariables;
} ConditionDescription;

typedef struct {
  const char *name;
  const char *originalName;
  int variableSize;
} StateVariableDescription;

struct ActorClass {
  unsigned int majorVersion; // runtime version
  unsigned int minorVersion;

  char *name;            // name of actor class
  int sizeActorInstance; // size to be allocated for instance

  int numInputPorts;
  const PortDescription *inputPortDescriptions;

  int numOutputPorts;
  const PortDescription *outputPortDescriptions;

  int numActions;
  const ActionDescription *actionDescriptions;

  const int *(*action_scheduler)(AbstractActorInstance *);
  void (*constructor)(AbstractActorInstance *);
  void (*destructor)(AbstractActorInstance *);
  void (*set_param)(AbstractActorInstance *, const char *, const char *);
  void (*serialize)(AbstractActorInstance *, ActorCoder *);
  void (*deserialize)(AbstractActorInstance *, ActorCoder *);
};

// Creates an ActorClass initializer

#define INIT_ActorClass(aClassName, instance_t, ctor, setParam, serlize,       \
                        deserlize, sched, dtor, nInputs, inputDescr, nOutputs, \
                        outputDescr, nActions, actionDescr)                    \
  {                                                                            \
    .majorVersion = ACTORS_RTS_MAJOR, .minorVersion = ACTORS_RTS_MINOR,        \
    .name = aClassName, .sizeActorInstance = sizeof(instance_t),               \
    .numInputPorts = nInputs, .inputPortDescriptions = inputDescr,             \
    .numOutputPorts = nOutputs, .outputPortDescriptions = outputDescr,         \
    .numActions = nActions, .actionDescriptions = actionDescr,                 \
    .action_scheduler = sched, .constructor = ctor, .destructor = dtor,        \
    .set_param = setParam, .serialize = serlize, .deserialize = deserlize      \
  }

/*
 * AbstractActorInstance, the "base class" which is common
 * to all actor instances
 */

struct AbstractActorInstance {
  slist_node listEntry; // to keep instance in list

  const ActorClass *actorClass;
  char *instanceName;

  int numInputPorts;
  InputPort *inputPort;

  int numOutputPorts;
  OutputPort *outputPort;

  const int *(*action_scheduler)(AbstractActorInstance *);
  int enabled;
  char *sender_name;
  char *receiver_name;
};

// #define ART_INPUT(index)
// input_port_local_port(input_port_array_get(thisActor->base.inputPort, index))
#define ART_INPUT(index) input_port_array_get(thisActor->base.inputPort, index)
// #define ART_OUTPUT(index)
// output_port_local_port(output_port_array_get(thisActor->base.outputPort,
// index))
#define ART_OUTPUT(index)                                                      \
  output_port_array_get(thisActor->base.outputPort, index)

// Action-scheduler exit code (first element of array)
// EXITCODE_TERMINATE = actor is dead
// EXITCODE_BLOCK(n)  = actor blocks on either of n ports
// EXITCODE_YIELD     = actor yielded, but may be fireable

extern const int exit_code_terminate[];
extern const int exit_code_yield[];

#define EXITCODE_TERMINATE exit_code_terminate
#define EXITCODE_BLOCK(n) (n)
#define EXIT_CODE_YIELD exit_code_yield
#define EXITCODE_PREDICATE(n) (n)
#define EXIT_CODE_PREDICATE -2

/*
 * The following macros provide a mechanism that allows
 * for local copies of Input/Output ports (not used in present
 * implementation)
 */
#define ART_ACTION_CONTEXT(numInputs, numOutputs)

#define ART_ACTION_SCHEDULER(name)                                             \
  static const int *name(AbstractActorInstance *pBase)

#define ART_ACTION_SCHEDULER_ENTER(numInputs, numOutputs)                      \
  void *context = NULL;                                                        \
  context = context;

#define ART_ACTION_SCHEDULER_EXIT(numInputs, numOutputs)

/*
 * The following macros provide a mechanism that allow
 * the loop within the action scheduler to be customized
 * (not used in the present implementation)
 */
#define ART_ACTION_SCHEDULER_LOOP while (1)
#define ART_ACTION_SCHEDULER_LOOP_TOP
#define ART_ACTION_SCHEDULER_LOOP_BOTTOM

/*
 * The following macros allow the declaration of actions
 * to be customized
 */

#define ART_ACTION(name, thistype) static void name(thistype *thisActor)

#define ART_FIRE_ACTION(name) name(thisActor)

#define ART_EXEC_TRANSITION(name)        \
  name(thisActor)

#ifdef TRACE
#define ART_ACTION_ENTER(name, index)                                          \
  actionTrace((AbstractActorInstance *)thisActor, index, #name)
#else
#define ART_ACTION_ENTER(name, index)
#endif

#define ART_ACTION_EXIT(name, index)


#define ART_INIT_SCOPE(name, thistype)                    \
    static void name(thistype *thisActor)

#define ART_SCOPE(name, thistype)                    \
    static void name(art_action_context_t *context, thistype *thisActor)

#define ART_CONDITION(name, thistype)            \
    static bool name(art_action_context_t *context, thistype *thisActor)

#define ART_CONDITION_ENTER(name, index)        \


#define ART_CONDITION_EXIT(name, index) \


#define ART_TEST_CONDITION(name)         \
  name(context, thisActor)


//#define dprint1(x,y)
//#define dprint2(x,y,z)

// FIXME: workarounds to handle System.bitops in RVC
static inline int32_t _bitand(int32_t x, int32_t y) { return x & y; }
static inline int32_t _bitor(int32_t x, int32_t y) { return x | y; }
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

#define FIFO_TYPE int8_t
#include "actors-fifo.h"
#undef FIFO_TYPE

#define FIFO_TYPE bool_t
#include "actors-fifo.h"
#undef FIFO_TYPE

#define FIFO_TYPE double
#include "actors-fifo.h"
#undef FIFO_TYPE

#define REF
#include "actors-fifo.h"
#undef FIFO_TYPE
#undef REF

// Define uint32_t FIFO operations in terms of int32_t operations
#define pinAvailIn_uint32_t(port) pinAvailIn_int32_t(port)
#define pinAvailOut_uint32_t(port) pinAvailOut_int32_t(port)

#define pinWrite_uint32_t(port, token) pinWrite_int32_t(port, (int32_t)(token))
#define pinWriteRepeat_uint32_t(port, buf, n)                                  \
  pinWriteRepeat_int32_t(port, (int32_t *)(buf), n)

#define pinRead_uint32_t(port) ((uint32_t)pinRead_int32_t(port))
#define pinReadRepeat_uint32_t(port, buf, n)                                   \
  pinReadRepeat_int32_t(port, (int32_t *)buf, n)

#define pinPeekFront_uint32_t(port) ((uint32_t)pinPeekFront_int32_t(port))
#define pinPeek_uint32_t(port, offset) ((uint32_t)pinPeek_int32_t(port, offset))

/*
 * Operations for dynamically sized tokens (socket
 * sender/receiver). This is _not_ to support tokens of different
 * sizes in the same FIFO. Rather, this is to push tokens to a FIFO
 * with a fixed token size, but that token size is determined at
 * runtime.
 */

static inline unsigned pinAvailIn_dyn(const InputPort *p) {
  return input_port_available(p);
}

static inline void pinRead_dyn(InputPort *p, void *token, /* output */
                               size_t tokenSize) {
  input_port_read(p, tokenSize, token);
}

static inline unsigned pinAvailOut_dyn(const OutputPort *p) {
  return output_port_space_left(p);
}

static inline void pinWrite_dyn(OutputPort *p, const void *token,
                                size_t tokenSize) {
  output_port_write(p, tokenSize, token);
}

#ifdef __cplusplus
}
#endif

#endif
