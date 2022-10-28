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

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <fcntl.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include "actors-network.h"
#include "actors-rts.h"
#include "actors-teleport.h"
#include "io-port.h"
#include "logging.h"

/* ------------------------------------------------------------------------- */

/*
 * When instances are created, they are disabled.
 */
static slist instances;

static pthread_mutex_t instance_lock = PTHREAD_MUTEX_INITIALIZER;

#define LOCK_INSTANCES(...)                                                    \
  do {                                                                         \
    pthread_mutex_lock(&instance_lock);                                        \
  } while (0)

#define UNLOCK_INSTANCES(...)                                                  \
  do {                                                                         \
    pthread_mutex_unlock(&instance_lock);                                      \
  } while (0)

struct mainloop_params {
  int delay;
};

/* TODO: make it possible to configure this on a per-connection basis */
#define FIFO_CAPACITY (4096)

/* ------------------------------------------------------------------------- */

/** Set this to enable power-saving idle mode */
#define CALVIN_BLOCK_ON_IDLE

/* ------------------------------------------------------------------------- */

/*
 * Synchronization state for worker thread
 */

enum state{
  ACTOR_THREAD_LOCKED_BUSY, /* executing actors and continue polling them even
                               when none fired */
  ACTOR_THREAD_BUSY,        /* executing actors */
  ACTOR_THREAD_IDLE         /* waiting for wakeup_cond */
};

static struct {
  /* taken by worker thread while running an actor */
  pthread_mutex_t execution_mutex;

#ifdef CALVIN_BLOCK_ON_IDLE
  pthread_mutex_t signaling_mutex;

  pthread_cond_t idle_cond;   /* signaled by thread when it goes idle */
  pthread_cond_t wakeup_cond; /* signaled by parser to wake up thread */
  enum state state;
#endif
} thread_state;

/* ------------------------------------------------------------------------- */

/** Thread-safe generation of integers in sequence. */
static unsigned long get_unique_counter(void) {
  static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
  static unsigned long n = 0;
  unsigned long result;

  {
    pthread_mutex_lock(&mutex);
    result = n++;
    pthread_mutex_unlock(&mutex);
  }

  return result;
}

/* ------------------------------------------------------------------------- */

static AbstractActorInstance *lookupActor(const char *actorName) {
  AbstractActorInstance *result = NULL;
  /* FIXME: linear time for each lookup --> quadratic initialization time */

  LOCK_INSTANCES();
  slist_node *elem = slist_first(&instances);
  while (elem != NULL) {
    AbstractActorInstance *actor = (AbstractActorInstance *)elem;
    if (strcmp(actor->instanceName, actorName) == 0) {
      result = actor;
      goto out;
    }
    elem = slist_next(&instances, elem);
  }

  /* Is there any particular reason for failing here? */
  m_message("lookupActor: no such actor '%s'", actorName);

out:
  UNLOCK_INSTANCES();

  return result;
}

/* ------------------------------------------------------------------------- */

/* optionally returns token size, if pointer 'pTokenSize' != NULL */
static OutputPort *lookupOutput(const AbstractActorInstance *instance,
                                const char *portName, int *pTokenSize,
                                tokenFn *pFunctions) {
  const ActorClass *actorClass = instance->actorClass;
  unsigned int i;

  for (i = 0; i < actorClass->numOutputPorts; ++i) {
    const PortDescription *descr = &actorClass->outputPortDescriptions[i];

    if (strcmp(descr->name, portName) == 0) {
      if (pTokenSize) {
        *pTokenSize = descr->tokenSize;
      }
      if (pFunctions) {
        if (descr->functions != NULL)
          memcpy(pFunctions, descr->functions, sizeof(tokenFn));
        else
          *pFunctions = (tokenFn){NULL, NULL, NULL, NULL};
      }
      return output_port_array_get(instance->outputPort, i);
    }
  }

  m_message("lookupOutput: no such port '%s.%s'", actorClass->name, portName);
  return NULL;
}

/* ------------------------------------------------------------------------- */

/* optionally returns token size, if pointer 'pTokenSize' != NULL */
static InputPort *lookupInput(const AbstractActorInstance *instance,
                              const char *portName, int *pTokenSize,
                              tokenFn *pFunctions) {
  const ActorClass *actorClass = instance->actorClass;
  unsigned int i;

  for (i = 0; i < actorClass->numInputPorts; ++i) {
    const PortDescription *descr = &actorClass->inputPortDescriptions[i];

    if (strcmp(descr->name, portName) == 0) {
      if (pTokenSize) {
        *pTokenSize = descr->tokenSize;
      }
      if (pFunctions) {
        if (descr->functions != NULL)
          memcpy(pFunctions, descr->functions, sizeof(tokenFn));
        else
          *pFunctions = (tokenFn){NULL, NULL, NULL, NULL};
      }
      return input_port_array_get(instance->inputPort, i);
    }
  }

  m_message("lookupInput: no such port '%s.%s'", actorClass->name, portName);
  return NULL;
}

/* ------------------------------------------------------------------------- */

static void disconnectInput(InputPort *consumer, OutputPort *producer) {
  output_port_input_port_disconnect(producer, consumer);
}

static void connectInput(InputPort *consumer, OutputPort *producer) {
  output_port_input_port_connect(producer, consumer);
}

/* ------------------------------------------------------------------------- */

/**
 * Prepares the "local" FIFOs by computing available tokens (inputs)
 * and space left (outputs). This simplifies the FIFO operations used
 * by the actor.
 *
 * @param actor  an actor, whose "local" input/output ports are prepared
 */

static inline void preFire(AbstractActorInstance *actor) {
  int i;

  for (i = 0; i < actor->numInputPorts; ++i) {
    InputPort *input = input_port_array_get(actor->inputPort, i);

    if (input_port_has_producer(input)) {
      input_port_update_available(input);
      input_port_update_drained_at(input);
    }
  }

  for (i = 0; i < actor->numOutputPorts; ++i) {
    OutputPort *output = output_port_array_get(actor->outputPort, i);
    output_port_update_full_at(output);
  }
}

/* ------------------------------------------------------------------------- */

/**
 * Updates the InputPorts and OutputPorts after possibly firing the actor.
 *
 * @param actor  an actor, whose InputPorts/OutputPorts are updated
 * @return true if the actor consumed or produced at least one token
 */
static inline int postFire(AbstractActorInstance *actor) {
  int i;
  int fired = 0;

  // Update tokensConsumed counter of each InputPort:
  // Counter at which the FIFO is drained less tokens still available
  for (i = 0; i < actor->numInputPorts; ++i) {
    InputPort *input = input_port_array_get(actor->inputPort, i);
    if (input_port_update_counter(input)) {
      fired = 1;
    }
  }

  // Update tokensProduced counter of each OutputPort:
  // Counter at which the FIFO is full less space still left
  for (i = 0; i < actor->numOutputPorts; ++i) {
    OutputPort *output = output_port_array_get(actor->outputPort, i);
    if (output_port_update_tokens_produced(output)) {
      fired = 1;
    }
  }

  return fired;
}

/* ------------------------------------------------------------------------- */

static void *workerThreadMain(void *params_ptr) {
  struct mainloop_params *params = static_cast<mainloop_params *>(params_ptr);

  while (1) {
    int fired = 1;

    while (fired) {
      int enabled_actors = 0;
      int disabled_actors = 0;

      LOCK_INSTANCES();
      fired = 0;
      slist_node *node = slist_first(&instances);
      while (node) {
        AbstractActorInstance *actor = (AbstractActorInstance *)node;
        if (actor->enabled) {
          AbstractActorInstance *actor = (AbstractActorInstance *)node;
          enabled_actors++;
          preFire(actor);
          actor->action_scheduler(actor);
          fired = postFire(actor);
        } else {
          disabled_actors++;
        }
        node = slist_next(&instances, node);
      }
      UNLOCK_INSTANCES();
      usleep(1000 * params->delay);
    }
  }

  return NULL; /* won't happen, just here for the 'void *' return type */
}

/* ========================================================================= */

void initActorNetwork(int delay) {
  pthread_t pid;
  struct mainloop_params *params =
      static_cast<mainloop_params *>(malloc(sizeof *params));

  params->delay = delay;
  LOCK_INSTANCES();
  slist_create(&instances);
  UNLOCK_INSTANCES();
  pthread_mutex_init(&thread_state.execution_mutex, NULL);

  pthread_create(&pid, NULL, &workerThreadMain, params);
}

/* ------------------------------------------------------------------------- */

AbstractActorInstance *createActorInstance(const ActorClass *actorClass,
                                           const char *actor_name,
                                           char **params) {
  AbstractActorInstance *instance = static_cast<AbstractActorInstance *>(
      calloc(1, actorClass->sizeActorInstance));
  unsigned int i;

  instance->actorClass = actorClass;
  instance->instanceName = strdup(actor_name);
  instance->inputPort = input_port_array_new(actorClass->numInputPorts);
  instance->outputPort = output_port_array_new(actorClass->numOutputPorts);
  instance->action_scheduler = actorClass->action_scheduler;

  instance->numInputPorts = actorClass->numInputPorts;
  instance->numOutputPorts = actorClass->numOutputPorts;
  instance->enabled = 0;

  slist_init_node(&instance->listEntry);

  {
    /* already exist? */
    AbstractActorInstance *exist = lookupActor(actor_name);
    if (exist) {
      m_message("Actor already exists - destroying");
      if (exist->enabled) {
        disableActorInstance(actor_name);
      }
      destroyActorInstance(actor_name);
    }
  }
  /* create outputs */
  for (i = 0; i < actorClass->numOutputPorts; ++i) {
    const PortDescription *descr = &actorClass->outputPortDescriptions[i];
    OutputPort *output = output_port_array_get(instance->outputPort, i);

    /*
     * FIFOs are associated with an output (except for the
     * consumer's state, which obviously is associated with the
     * corresponding input)
     */
    static const unsigned int capacity = FIFO_CAPACITY;

    output_port_setup_buffer(output, capacity, descr->tokenSize);
    output_port_set_functions(output, descr->functions);
    output_port_init_consumer_list(output);
  }

  /* Set parameters (if any) */
  for (int i = 0; params && params[2 * i] && i < MAX_PARAMS; i++) {
    setActorParam(instance, params[2 * i], params[2 * i + 1]);
  }

  if (instance->actorClass->constructor) {
    instance->actorClass->constructor(instance);
  }

  /* Add it to the list of instances */
  LOCK_INSTANCES();
  slist_append(&instances, &instance->listEntry);
  UNLOCK_INSTANCES();
  return instance;
}

/* ------------------------------------------------------------------------- */

void setActorParam(AbstractActorInstance *instance, const char *key,
                   const char *value) {
  const ActorClass *actorClass = instance->actorClass;
  if (actorClass->set_param) {
    actorClass->set_param(instance, key, value);
    actorClass->set_param(instance, key, value);
  } else {
    m_warning("parameter (%s) not supported by actor %s",
              instance->instanceName, key);
  }
}

/* ------------------------------------------------------------------------- */

void disableActorInstance(const char *actor_name) {
  m_message("looking up actor %s", actor_name);
  AbstractActorInstance *instance = lookupActor(actor_name);

  if (instance == NULL) {
    m_critical("instance %s already destroyed", actor_name);
  }

  m_message("Disabling %s", actor_name);
  LOCK_INSTANCES();
  if (instance->enabled) {
    instance->enabled = 0;
  }
  UNLOCK_INSTANCES();

  /* Disable receivers/senders */
  if (instance->sender_name) {
    disableActorInstance(instance->sender_name);
  }
  if (instance->receiver_name != NULL) {
    disableActorInstance(instance->receiver_name);
  }
  // wakeUpNetwork();
}

/* ------------------------------------------------------------------------- */

void enableActorInstance(const char *actor_name) {
  AbstractActorInstance *instance = lookupActor(actor_name);

  LOCK_INSTANCES();
  if (!instance->enabled) {
    instance->enabled = 1;
  }
  UNLOCK_INSTANCES();

  /* Enabled senders/receivers */
  if (instance->sender_name != NULL) {
    enableActorInstance(instance->sender_name);
  }
  if (instance->receiver_name != NULL) {
    enableActorInstance(instance->receiver_name);
  }
  // wakeUpNetwork();
}

/* ------------------------------------------------------------------------- */

void destroyActorInstance(const char *actor_name) {
  AbstractActorInstance *instance = lookupActor(actor_name);
  const ActorClass *actorClass;

  if (instance == NULL) {
    m_critical("Instance %s already destroyed", actor_name);
  }

  m_message("Destroying %s", actor_name);
  assert(!instance->enabled);

  {
    pthread_mutex_lock(&thread_state.execution_mutex);
    LOCK_INSTANCES();
    slist_remove(&instances, &instance->listEntry);

    actorClass = instance->actorClass;
    /* for each input, disconnect from the producer */
    for (int i = 0; i < actorClass->numInputPorts; i++) {
      InputPort *input = input_port_array_get(instance->inputPort, i);
      OutputPort *producer = input_port_producer(input);
      if (producer) {
        output_port_input_port_disconnect(producer, input);
      }
    }

    /* for each output, disconnect all consumers */
    for (int i = 0; i < actorClass->numOutputPorts; i++) {
      OutputPort *output = output_port_array_get(instance->outputPort, i);
      output_port_disconnect_consumers(output);

      output_port_buffer_start_free(output);
    }

    UNLOCK_INSTANCES();
    pthread_mutex_unlock(&thread_state.execution_mutex);
  }

  input_port_array_free(instance->inputPort);
  output_port_array_free(instance->outputPort);

  if (actorClass->destructor) {
    actorClass->destructor(instance);
  }

  if (instance->sender_name != NULL) {
    destroyActorInstance(instance->sender_name);
    free(instance->sender_name);
    instance->sender_name = NULL;
  }
  if (instance->receiver_name != NULL) {
    destroyActorInstance(instance->receiver_name);
    free(instance->receiver_name);
    instance->receiver_name = NULL;
  }
}

/* ------------------------------------------------------------------------- */

void dropLocalConnection(const char *src_actor, const char *src_port,
                         const char *dst_actor, const char *dst_port) {
  InputPort *input = lookupInput(lookupActor(dst_actor), dst_port, NULL, NULL);
  OutputPort *output =
      lookupOutput(lookupActor(src_actor), src_port, NULL, NULL);
  disconnectInput(input, output);
}

void createLocalConnection(const char *src_actor, const char *src_port,
                           const char *dst_actor, const char *dst_port) {
  AbstractActorInstance *src = lookupActor(src_actor);
  AbstractActorInstance *dst = lookupActor(dst_actor);
  InputPort *input = lookupInput(dst, dst_port, NULL, NULL);
  OutputPort *output = lookupOutput(src, src_port, NULL, NULL);

  /* olaan: meh */
  src->sender_name = NULL;
  src->receiver_name = NULL;

  connectInput(input, output);

  wakeUpNetwork();
}

/* ------------------------------------------------------------------------- */

void dropRemoteConnection(const char *src_actor, const char *src_port,
                          const char *remote_host, const char *remote_port) {
  int tokenSize;
  AbstractActorInstance *src = lookupActor(src_actor);
  tokenFn functions;
  OutputPort *output = lookupOutput(src, src_port, &tokenSize, &functions);

  AbstractActorInstance *sender = lookupActor(src->sender_name);

  m_message("Dropping sender %s from instance %s", src->sender_name,
            src->instanceName);
  /* TODO: stop threads? */
  if (sender != NULL) {
    InputPort *input = lookupInput(sender, "in", NULL, NULL);
    disconnectInput(input, output);
    disableActorInstance(src->sender_name);
    destroyActorInstance(src->sender_name);
    free(src->sender_name);
    src->sender_name = NULL;
  } else {
    m_warning("Could not locate sender %s", src->sender_name);
  }
  wakeUpNetwork();
}

void createRemoteConnection(const char *src_actor, const char *src_port,
                            const char *remote_host, const char *remote_port) {
  int tokenSize;
  AbstractActorInstance *src = lookupActor(src_actor);
  tokenFn functions;
  OutputPort *output = lookupOutput(src, src_port, &tokenSize, &functions);
  const ActorClass *klass = getSenderClass(tokenSize, &functions);
  char *sender_name;
  asprintf(&sender_name, "_sender_%02ld", get_unique_counter());

  /* name allocated here (using strdup), free'd in destructor */
  AbstractActorInstance *sender = createActorInstance(klass, sender_name, NULL);
  if (src->sender_name != NULL) {
    m_message("instance %s already has sender %s - freeing", src_actor,
              src->sender_name);
    disableActorInstance(src->sender_name);
    destroyActorInstance(src->sender_name);
    free(src->sender_name);
  }
  /* save name to facilitate dropping connection later */
  m_message("Adding sender %s to instance %s", sender_name, src->instanceName);
  src->sender_name = strdup(sender_name);

  setSenderRemoteAddress(sender, remote_host, atoi(remote_port));
  /* olaan: to avoid race */
  extern void start_sender_thread(AbstractActorInstance *);
  start_sender_thread(sender);

  InputPort *input = lookupInput(sender, "in", NULL, NULL);
  connectInput(input, output);

  enableActorInstance(sender_name);

  wakeUpNetwork();
}

/* ------------------------------------------------------------------------- */

int createSocketReceiver(const char *actor_name, const char *port) {
  int tokenSize;
  AbstractActorInstance *consumer = lookupActor(actor_name);
  tokenFn functions;
  InputPort *input = lookupInput(consumer, port, &tokenSize, &functions);

  const ActorClass *klass = getReceiverClass(tokenSize, &functions);
  char *receiver_name;

  asprintf(&receiver_name, "_receiver_%02ld", get_unique_counter());

  /* name allocated here, free'd in destructor */
  AbstractActorInstance *receiver =
      createActorInstance(klass, receiver_name, NULL);

  m_message("Adding receiver %s to instance %s", receiver_name,
            consumer->instanceName);

  if (consumer->receiver_name != NULL) {
    m_message("instance %s already has receiver %s - freeing", actor_name,
              consumer->receiver_name);
    disableActorInstance(consumer->receiver_name);
    destroyActorInstance(consumer->receiver_name);
    free(consumer->receiver_name);
  }
  consumer->receiver_name = strdup(receiver_name);

  OutputPort *output = lookupOutput(receiver, "out", NULL, NULL);
  connectInput(input, output);

  enableActorInstance(receiver_name);

  return getReceiverPort(receiver);
}

/* ------------------------------------------------------------------------- */

void waitForIdle(void) {
#ifdef CALVIN_BLOCK_ON_IDLE
  pthread_mutex_lock(&thread_state.signaling_mutex);
  while (thread_state.state != ACTOR_THREAD_IDLE) {
    pthread_cond_wait(&thread_state.idle_cond, &thread_state.signaling_mutex);
  }
  pthread_mutex_unlock(&thread_state.signaling_mutex);
#endif
}

/* ------------------------------------------------------------------------- */

void wakeUpNetwork(void) {
#ifdef CALVIN_BLOCK_ON_IDLE
  pthread_mutex_lock(&thread_state.signaling_mutex);
  if (thread_state.state == ACTOR_THREAD_IDLE) {
    thread_state.state = ACTOR_THREAD_BUSY;
    pthread_cond_signal(&thread_state.wakeup_cond);
  }
  pthread_mutex_unlock(&thread_state.signaling_mutex);
#endif
}

/** Mechanism to toggle action scheduler loop
 * into locked busy state. The user must supply
 * a unique hashid. The user must themself
 * keep track if this is locking or unlocking,
 * either by matched pairs in code or by a seperate
 * variable.
 * This will keep the action scheduler loop in
 * locked busy as long as at least one user has
 * locked it into busy. It will also wakeup the
 * network.
 */
void lockedBusyNetworkToggle(uint32_t hashid) {
#ifdef CALVIN_BLOCK_ON_IDLE
  /* Static status of id prints, only when 0 all locked busy mechanism users
   * have (propably) unlocked */
  static uint32_t idPrints = 0;

  pthread_mutex_lock(&thread_state.signaling_mutex);
  /* XOR with the hash id to add/remove id print */
  idPrints ^= hashid;
  if (idPrints == 0) {
    /* Not locked busy, step down to busy, it's for the scheduler loop to
     * evaluate when to go to idle */
    thread_state.state = state::ACTOR_THREAD_BUSY;
  } else {
    /* Locked busy */
    thread_state.state = state::ACTOR_THREAD_LOCKED_BUSY;
  }
  pthread_cond_signal(&thread_state.wakeup_cond);
  pthread_mutex_unlock(&thread_state.signaling_mutex);
#endif
}

/* ------------------------------------------------------------------------- */

/**
 * Get 32-bit Murmur3 hash. MurmurHash performs well in a random
 * distribution of regular keys.
 *
 * @param data      source data
 * @param nbytes    size of data
 *
 * @return 32-bit unsigned hash value.
 *
 * @code
 *  uint32_t hashval = qhashmurmur3_32((void*)"hello", 5);
 * @endcode
 *
 * @code
 *  MurmurHash3 was created by Austin Appleby  in 2008. The cannonical
 *  implementations are in C++ and placed in the public.
 *
 *    https://sites.google.com/site/murmurhash/
 *
 *  Seungyoung Kim has ported it's cannonical implementation to C language
 *  in 2012 and published it as a part of qLibc component.
 *  qLibc is public domain code.
 * @endcode
 */
static inline uint32_t hashmurmur3_32(const void *data, size_t nbytes) {
  if (data == NULL || nbytes == 0)
    return 0;

  const uint32_t c1 = 0xcc9e2d51;
  const uint32_t c2 = 0x1b873593;

  const int nblocks = nbytes / 4;
  const uint32_t *blocks = (const uint32_t *)(data);
  const uint8_t *tail =
      (const uint8_t *)((const uint8_t *)data + (nblocks * 4));

  uint32_t h = 0;

  int i;
  uint32_t k;
  for (i = 0; i < nblocks; i++) {
    k = blocks[i];

    k *= c1;
    k = (k << 15) | (k >> (32 - 15));
    k *= c2;

    h ^= k;
    h = (h << 13) | (h >> (32 - 13));
    h = (h * 5) + 0xe6546b64;
  }

  k = 0;
  switch (nbytes & 3) {
  case 3:
    k ^= tail[2] << 16;
  case 2:
    k ^= tail[1] << 8;
  case 1:
    k ^= tail[0];
    k *= c1;
    k = (k << 13) | (k >> (32 - 15));
    k *= c2;
    h ^= k;
  };

  h ^= nbytes;

  h ^= h >> 16;
  h *= 0x85ebca6b;
  h ^= h >> 13;
  h *= 0xc2b2ae35;
  h ^= h >> 16;

  return h;
}

/* ------------------------------------------------------------------------- */

/** Thread-safe generation of unique hash id. */
uint32_t getUniqueHashid(void) {
  static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
  static uint32_t n = 1;
  uint32_t result;

  {
    pthread_mutex_lock(&mutex);
    result = n++;
    pthread_mutex_unlock(&mutex);
  }

  /*
   * Hash the counter with the 32 bit murmurhash.
   * If ever find that this is not enough bits
   * to have any combination of these XOR:ed
   * giving a zero, switch to the 128 bit version.
   */
  return hashmurmur3_32(&result, 4);
}

/* ------------------------------------------------------------------------- */

void listActors(FILE *out) {
  /* TODO: tag disabled actors as such? */

  LOCK_INSTANCES();
  slist_node *node = slist_first(&instances);
  while (node != NULL) {
    fprintf(out, " %s", ((AbstractActorInstance *)node)->instanceName);
    node = slist_next(&instances, node);
  }

  UNLOCK_INSTANCES();
}

/* ------------------------------------------------------------------------- */

void serializeActor(const char *name, ActorCoder *coder) {
  AbstractActorInstance *actor = lookupActor(name);
  const ActorClass *actorClass = actor->actorClass;

  if (actorClass->serialize) {
    actorClass->serialize(actor, coder);
  }
}

/* ------------------------------------------------------------------------- */

void deserializeActor(const char *name, ActorCoder *coder) {
  AbstractActorInstance *actor = lookupActor(name);
  const ActorClass *actorClass = actor->actorClass;

  if (actorClass->deserialize) {
    actorClass->deserialize(actor, coder);
  }
}

/* ------------------------------------------------------------------------- */

#if 0

/* Hijack 'show' command to test serialization of state:
 *  1) Instantiate a coder (JSON, XML, debug, ...)
 *  2) Tell actor to serialize itself
 *  3) Write human readable content of coder object to <out>
 *  4) Clean up
 */

void showActor(FILE *out, const char *name)
{
  AbstractActorInstance *actor = lookupActor(name);
  const ActorClass *klass = actor->actorClass;

  ActorCoder *coder = newCoder(JSON_CODER);
  const ActorClass *actorClass = actor->actorClass;
  if (actorClass->serialize) {
    actorClass->serialize(actor, coder);
  }

  fprintf(out, "\n====\n%s\n----\n", klass->name);
  fprintf(out, "%s\n", coder->_description(coder));
  fprintf(out, "----\n");

  destroyCoder(coder);
}
#else
void showActor(FILE *out, const char *name) {
  AbstractActorInstance *actor = lookupActor(name);
  const ActorClass *klass = actor->actorClass;

  fprintf(out, " %s", klass->name);

  for (int i = 0; i < actor->numInputPorts; ++i) {
    InputPort *input = input_port_array_get(actor->inputPort, i);
    const PortDescription *descr = &klass->inputPortDescriptions[i];
    OutputPort *producer = input_port_producer(input);
    if (producer) {
      int available;
      available = output_port_tokens_produced(producer) -
                  input_port_tokens_consumed(input);
      fprintf(out, " i:%s:%d", descr->name, available);
    } else {
      fprintf(out, " i:%s:-", descr->name);
    }
  }

  for (int i = 0; i < actor->numOutputPorts; ++i) {
    OutputPort *output = output_port_array_get(actor->outputPort, i);
    const PortDescription *descr = &klass->outputPortDescriptions[i];
    unsigned int maxAvailable = output_port_max_available(output);
    unsigned int spaceLeft = output_port_capacity(output) - maxAvailable;

    spaceLeft = output_port_capacity(output) - maxAvailable;

    fprintf(out, " o:%s:%d", descr->name, spaceLeft);
  }
}
#endif
