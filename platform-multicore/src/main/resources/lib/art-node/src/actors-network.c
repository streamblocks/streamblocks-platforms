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

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>

#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <unistd.h>

#include "actors-network.h"
#include "actors-teleport.h"
#include "actors-rts.h"

/* ------------------------------------------------------------------------- */

/*
 * When instances are created, they are placed in 'disabled_instances'.
 * When they are later enabled, they are moved to 'instances'. Only the
 * latter list is used for execution.
 */
static dllist_head_t instances;   /* active, executing instances */
static dllist_head_t disabled_instances;/* disabled, non-executing instances */

/* TODO: make it possible to configure this on a per-connection basis */
#define FIFO_CAPACITY  (4096)

/* ------------------------------------------------------------------------- */

/** Set this to enable power-saving idle mode */
#define CALVIN_BLOCK_ON_IDLE

/* ------------------------------------------------------------------------- */

/*
 * Synchronization state for worker thread
 */
static struct {
  /* taken by worker thread while running an actor */
  pthread_mutex_t execution_mutex;

#ifdef CALVIN_BLOCK_ON_IDLE
  pthread_mutex_t signaling_mutex;

  pthread_cond_t idle_cond;         /* signaled by thread when it goes idle */
  pthread_cond_t wakeup_cond;       /* signaled by parser to wake up thread */

  enum {
    ACTOR_THREAD_LOCKED_BUSY,       /* executing actors and continue polling them even when none fired */
    ACTOR_THREAD_BUSY,              /* executing actors */
    ACTOR_THREAD_IDLE               /* waiting for wakeup_cond */
  } state;
#endif
} thread_state;

/* ------------------------------------------------------------------------- */

/** Thread-safe generation of integers in sequence. */
static unsigned long get_unique_counter(void)
{
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

static AbstractActorInstance * lookupActor(const char *actorName)
{
  /* FIXME: linear time for each lookup --> quadratic initialization time */

  /*
   * The actor could be either in 'disabled_instances' or 'instances'
   */

  dllist_element_t *elem = dllist_first(&disabled_instances);
  while (elem) {
    AbstractActorInstance *actor = (AbstractActorInstance *) elem;
    if (strcmp(actor->instanceName, actorName) == 0) {
      return actor;
    }

    elem = dllist_next(&disabled_instances, elem);
  }

  elem = dllist_first(&instances);
  while (elem) {
    AbstractActorInstance *actor = (AbstractActorInstance *) elem;
    if (strcmp(actor->instanceName, actorName) == 0) {
      return actor;
    }

    elem = dllist_next(&instances, elem);
  }

  fail("lookupActor: no such actor '%s'\n", actorName);
}

/* ------------------------------------------------------------------------- */

/* optionally returns token size, if pointer 'pTokenSize' != NULL */
static OutputPort * lookupOutput(const AbstractActorInstance *instance,
                                 const char *portName,
                                 int *pTokenSize, tokenFn* pFunctions)
{
  const ActorClass *actorClass = instance->actorClass;
  unsigned int i;

  for (i = 0; i < actorClass->numOutputPorts; ++i) {
    const PortDescription *descr = &actorClass->outputPortDescriptions[i];

    if (strcmp(descr->name, portName) == 0) {
      if (pTokenSize) {
        *pTokenSize = descr->tokenSize;
      }
      if (pFunctions) {
        if(descr->functions!=NULL)
          memcpy(pFunctions, descr->functions, sizeof(tokenFn));
        else
          *pFunctions=(tokenFn){NULL,NULL,NULL,NULL};
      }
      return &instance->outputPort[i];
    }
  }

  fail("lookupOutput: no such port '%s.%s'\n",
       actorClass->name, portName);
}

/* ------------------------------------------------------------------------- */

/* optionally returns token size, if pointer 'pTokenSize' != NULL */
static InputPort * lookupInput(const AbstractActorInstance *instance,
                               const char *portName,
                               int *pTokenSize, tokenFn* pFunctions)
{
  const ActorClass *actorClass = instance->actorClass;
  unsigned int i;

  for (i = 0; i < actorClass->numInputPorts; ++i) {
    const PortDescription *descr = &actorClass->inputPortDescriptions[i];

    if (strcmp(descr->name, portName) == 0) {
      if (pTokenSize) {
        *pTokenSize = descr->tokenSize;
      }
      if (pFunctions) {
        if(descr->functions!=NULL)
          memcpy(pFunctions, descr->functions, sizeof(tokenFn));
        else
          *pFunctions=(tokenFn){NULL,NULL,NULL,NULL};
      }
      return &instance->inputPort[i];
    }
  }

  fail("lookupInput: no such port '%s.%s'\n",
       actorClass->name, portName);
}

/* ------------------------------------------------------------------------- */

static void connectInput(AbstractActorInstance *instance,
                         const char *portName,
                         OutputPort *producer)
{
  const ActorClass *actorClass = instance->actorClass;
  unsigned int i;

  for (i = 0; i < actorClass->numInputPorts; ++i) {
    const PortDescription *descr = &actorClass->inputPortDescriptions[i];
    if (strcmp(descr->name, portName) == 0) {
      InputPort *input = &instance->inputPort[i];

      input->producer = producer;

      // copy FIFO pointers from the producer

      input->localInputPort.bufferStart =
      input->localInputPort.readPtr = producer->localOutputPort.bufferStart;
      input->localInputPort.bufferEnd = producer->localOutputPort.bufferEnd;
      input->capacity = producer->capacity;
      memcpy(&input->functions, &producer->functions, sizeof(tokenFn));

      dllist_init_element(&input->asConsumer);
      dllist_append(&producer->consumers, &input->asConsumer);

      return;
    }
  }

  fail("connectInput: no such port '%s.%s'\n",
       actorClass->name, portName);
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
    InputPort *input = &actor->inputPort[i];

    if (input->producer) {
      unsigned int available
      = input->producer->tokensProduced - input->tokensConsumed;

      input->localInputPort.available=available;
      // drainedAt: tokensConsumed counter when zero tokens available
      input->drainedAt=input->tokensConsumed + available;
    }
  }

  for (i = 0; i < actor->numOutputPorts; ++i) {
    OutputPort *output = &actor->outputPort[i];
    unsigned int produced = output->tokensProduced;
    unsigned int maxAvailable = 0;
    unsigned int spaceLeft;

    InputPort *consumer = (InputPort *) dllist_first_lock(&output->consumers);
    while (consumer != NULL) {
      unsigned int available = produced - consumer->tokensConsumed;
      if (available > maxAvailable)
        maxAvailable = available;

      consumer = (InputPort *) dllist_next_locked(&output->consumers,
                                           &consumer->asConsumer);
    }
    dllist_unlock(&output->consumers);
    spaceLeft = output->capacity - maxAvailable;
    output->localOutputPort.spaceLeft = spaceLeft;
    // fullAt: tokensProduced counter when FIFO is full
    output->fullAt = output->tokensProduced + spaceLeft;
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
  int fired=0;

  // Update tokensConsumed counter of each InputPort:
  // Counter at which the FIFO is drained less tokens still available
  for (i=0; i<actor->numInputPorts; ++i) {
    InputPort *input=&actor->inputPort[i];
    unsigned counter = input->drainedAt - input->localInputPort.available;
    if (counter!=input->tokensConsumed) {
      input->tokensConsumed=counter;
      fired=1;
    }
  }

  // Update tokensProduced counter of each OutputPort:
  // Counter at which the FIFO is full less space still left
  for (i=0; i<actor->numOutputPorts; ++i) {
    OutputPort *output=&actor->outputPort[i];
    unsigned counter = output->fullAt - output->localOutputPort.spaceLeft;
    if (counter!=output->tokensProduced) {
      output->tokensProduced=counter;
      fired=1;
    }
  }

  return fired;
}

/* ------------------------------------------------------------------------- */

static void * workerThreadMain(void *unused_arg)
{
  while (1) {
    int fired=1;

    while (fired) {
      {
        pthread_mutex_lock(&thread_state.execution_mutex);
        
        dllist_element_t *elem = dllist_first_lock(&instances);
        fired=0;
        while (elem) {
          AbstractActorInstance *actor = (AbstractActorInstance *) elem;
          
          preFire(actor);
          (void) actor->action_scheduler(actor);
          if (postFire(actor)) {
            fired=1;
          }
          
          elem = dllist_next_locked(&instances, elem);
        }
        dllist_unlock(&instances);

        pthread_mutex_unlock(&thread_state.execution_mutex);
      }
      
    }

#ifdef CALVIN_BLOCK_ON_IDLE
    /* no actor fired on last iteration -- go idle if not locked busy, wait for trigger */
    {
      pthread_mutex_lock(&thread_state.signaling_mutex);
      if(thread_state.state != ACTOR_THREAD_LOCKED_BUSY) {
        thread_state.state = ACTOR_THREAD_IDLE;
        pthread_cond_signal(&thread_state.idle_cond);
        while (thread_state.state == ACTOR_THREAD_IDLE) {
          pthread_cond_wait(&thread_state.wakeup_cond,
                            &thread_state.signaling_mutex);
        }
      }
      pthread_mutex_unlock(&thread_state.signaling_mutex);
    }
#endif
  }

  return NULL; /* won't happen, just here for the 'void *' return type */
}

/* ========================================================================= */

void initActorNetwork(void)
{
  pthread_t pid;

  dllist_create(&instances);
  dllist_create(&disabled_instances);

  pthread_mutex_init(&thread_state.execution_mutex, NULL);

#ifdef CALVIN_BLOCK_ON_IDLE
  pthread_mutex_init(&thread_state.signaling_mutex, NULL);

  pthread_cond_init(&thread_state.idle_cond, NULL);
  pthread_cond_init(&thread_state.wakeup_cond, NULL);

  thread_state.state = ACTOR_THREAD_BUSY;
#endif

  pthread_create(&pid, NULL, &workerThreadMain, NULL);
}

/* ------------------------------------------------------------------------- */

AbstractActorInstance * createActorInstance(const ActorClass *actorClass,
                                            const char *actor_name)
{
  AbstractActorInstance *instance = malloc(actorClass->sizeActorInstance);
  unsigned int i;

  instance->actorClass = actorClass;
  instance->instanceName = strdup(actor_name);
  instance->inputPort = calloc(actorClass->numInputPorts, sizeof(InputPort));
  instance->outputPort = calloc(actorClass->numOutputPorts, sizeof(OutputPort));
  instance->action_scheduler = actorClass->action_scheduler;

  instance->numInputPorts = actorClass->numInputPorts;
  instance->numOutputPorts = actorClass->numOutputPorts;

  dllist_init_element(&instance->listEntry);

  dllist_append(&disabled_instances, &instance->listEntry);

  /* create outputs */
  for (i = 0; i < actorClass->numOutputPorts; ++i) {
    const PortDescription *descr = &actorClass->outputPortDescriptions[i];
    OutputPort *output = &instance->outputPort[i];

    /*
     * FIFOs are associated with an output (except for the
     * consumer's state, which obviously is associated with the
     * corresponding input)
     */
    static const unsigned int capacity = FIFO_CAPACITY;
    unsigned int size = capacity * descr->tokenSize;
    char *buffer = malloc(size);

    output->localOutputPort.bufferStart =
    output->localOutputPort.writePtr = buffer;
    output->localOutputPort.bufferEnd = buffer + size;
    output->capacity = capacity;
    if(descr->functions!=NULL)
      memcpy(&output->functions, descr->functions, sizeof(tokenFn));
    else
      output->functions=(tokenFn){NULL,NULL,NULL,NULL};

    dllist_create(&output->consumers);
  }

  return instance;
}

/* ------------------------------------------------------------------------- */

void setActorParam(AbstractActorInstance *instance,
                   const char *key,
                   const char *value)
{
  const ActorClass *actorClass = instance->actorClass;
  if (actorClass->set_param) {
    actorClass->set_param(instance, key, value);
  }
  else {
    warn("parameter (%s) not supported by actor %s",
	 instance->instanceName, key);
  }
}

/* ------------------------------------------------------------------------- */

void enableActorInstance(const char *actor_name)
{
  AbstractActorInstance *instance = lookupActor(actor_name);

  if (instance->actorClass->constructor) {
    instance->actorClass->constructor(instance);
  }

  dllist_remove(&disabled_instances, &instance->listEntry);
  dllist_append(&instances, &instance->listEntry);

  wakeUpNetwork();
}

/* ------------------------------------------------------------------------- */

void destroyActorInstance(const char *actor_name)
{
  AbstractActorInstance *instance = lookupActor(actor_name);
  const ActorClass *actorClass = instance->actorClass;
  unsigned int i;

  {
    pthread_mutex_lock(&thread_state.execution_mutex);
    dllist_remove(&instances, &instance->listEntry);

    /* for each input, disconnect from the producer */
    for (i = 0; i < actorClass->numInputPorts; i++) {
      InputPort *input = &instance->inputPort[i];
      OutputPort *producer = input->producer;
      if (producer) {
        dllist_remove(&producer->consumers, &input->asConsumer);
      }
    }

    /* for each output, disconnect all consumers */
    for (i = 0; i < actorClass->numOutputPorts; i++) {
      OutputPort *output = &instance->outputPort[i];
      dllist_element_t *elem = dllist_first(&output->consumers);
      while (elem) {
        InputPort *input = (InputPort *) elem;
        input->producer = NULL;
        elem = dllist_next(&output->consumers, elem);
      }

      free(output->localOutputPort.bufferStart);
    }

    pthread_mutex_unlock(&thread_state.execution_mutex);
  }

  free(instance->inputPort);
  free(instance->outputPort);

  if (actorClass->destructor) {
    actorClass->destructor(instance);
  }
}

/* ------------------------------------------------------------------------- */

void createLocalConnection(const char *src_actor,
                           const char *src_port,
                           const char *dst_actor,
                           const char *dst_port)
{
  connectInput(lookupActor(dst_actor),
               dst_port,
               lookupOutput(lookupActor(src_actor), src_port, NULL, NULL));

  wakeUpNetwork();
}

/* ------------------------------------------------------------------------- */

void createRemoteConnection(const char *src_actor,
                            const char *src_port,
                            const char *remote_host,
                            const char *remote_port)
{
  int tokenSize;
  AbstractActorInstance *src = lookupActor(src_actor);
  tokenFn functions;
  (void) lookupOutput(src, src_port, &tokenSize, &functions);
  const ActorClass *klass = getSenderClass(tokenSize, &functions);

#define MAX_SENDER_NAME   (30)
  char sender_name[MAX_SENDER_NAME];

  snprintf(sender_name, MAX_SENDER_NAME, "_sender_%lx",
           get_unique_counter());

  /* name allocated here (using strdup), free'd in destructor */
  AbstractActorInstance *sender
  = createActorInstance(klass, strdup(sender_name));

  setSenderRemoteAddress(sender, remote_host, atoi(remote_port));
  connectInput(sender, "in", lookupOutput(src, src_port, NULL, NULL));
  enableActorInstance(sender_name);

  wakeUpNetwork();
}

/* ------------------------------------------------------------------------- */

int createSocketReceiver(const char *actor_name,
                         const char *port)
{
  int tokenSize;
  AbstractActorInstance *consumer = lookupActor(actor_name);
  tokenFn functions;
  (void) lookupInput(consumer, port, &tokenSize, &functions);

  const ActorClass *klass = getReceiverClass(tokenSize, &functions);
#define MAX_RECEIVER_NAME   (30)
  char receiver_name[MAX_RECEIVER_NAME];

  snprintf(receiver_name, MAX_RECEIVER_NAME, "_receiver_%lx",
           get_unique_counter());

  /* name allocated here (using strdup), free'd in destructor */
  AbstractActorInstance *receiver
  = createActorInstance(klass, strdup(receiver_name));
  connectInput(consumer, port, lookupOutput(receiver, "out", NULL, NULL));
  enableActorInstance(receiver_name);

  return getReceiverPort(receiver);
}

/* ------------------------------------------------------------------------- */

void waitForIdle(void)
{
#ifdef CALVIN_BLOCK_ON_IDLE
  pthread_mutex_lock(&thread_state.signaling_mutex);
  while (thread_state.state != ACTOR_THREAD_IDLE) {
    pthread_cond_wait(&thread_state.idle_cond,
                      &thread_state.signaling_mutex);
  }
  pthread_mutex_unlock(&thread_state.signaling_mutex);
#endif
}

/* ------------------------------------------------------------------------- */

void wakeUpNetwork(void)
{
#ifdef CALVIN_BLOCK_ON_IDLE
  pthread_mutex_lock(&thread_state.signaling_mutex);
  if(thread_state.state == ACTOR_THREAD_IDLE) {
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
void lockedBusyNetworkToggle(uint32_t hashid)
{
#ifdef CALVIN_BLOCK_ON_IDLE
  /* Static status of id prints, only when 0 all locked busy mechanism users have (propably) unlocked */
  static uint32_t idPrints = 0;
  
  pthread_mutex_lock(&thread_state.signaling_mutex);
  /* XOR with the hash id to add/remove id print */
  idPrints ^= hashid;
  if(idPrints == 0) {
    /* Not locked busy, step down to busy, it's for the scheduler loop to evaluate when to go to idle */
    thread_state.state = ACTOR_THREAD_BUSY;
  } else {
    /* Locked busy */
    thread_state.state = ACTOR_THREAD_LOCKED_BUSY;
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
static inline uint32_t hashmurmur3_32(const void *data, size_t nbytes)
{
  if (data == NULL || nbytes == 0) return 0;
  
  const uint32_t c1 = 0xcc9e2d51;
  const uint32_t c2 = 0x1b873593;
  
  const int nblocks = nbytes / 4;
  const uint32_t *blocks = (const uint32_t *)(data);
  const uint8_t *tail = (const uint8_t *)(data + (nblocks * 4));
  
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
uint32_t getUniqueHashid(void)
{
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
  return hashmurmur3_32(&result,4);
}

/* ------------------------------------------------------------------------- */

void listActors(FILE *out)
{
  /* TODO: tag disabled actors as such? */

  dllist_element_t *elem = dllist_first(&disabled_instances);
  while (elem) {
    fprintf(out, " %s", ((AbstractActorInstance *) elem)->instanceName);

    elem = dllist_next(&disabled_instances, elem);
  }

  elem = dllist_first(&instances);
  while (elem) {
    fprintf(out, " %s", ((AbstractActorInstance *) elem)->instanceName);

    elem = dllist_next(&instances, elem);
  }
}

/* ------------------------------------------------------------------------- */

void showActor(FILE *out, const char *name)
{
  AbstractActorInstance *actor = lookupActor(name);
  const ActorClass *klass = actor->actorClass;
  unsigned int i;

  fprintf(out, " %s", klass->name);

  for (i = 0; i < actor->numInputPorts; ++i) {
    InputPort *input = &actor->inputPort[i];
    const PortDescription *descr = &klass->inputPortDescriptions[i];

    if (input->producer) {
      fprintf(out, " i:%s:%d", descr->name,
              input->producer->tokensProduced - input->tokensConsumed);
    } else {
      fprintf(out, " i:%s:-", descr->name);
    }
  }
  
  for (i = 0; i < actor->numOutputPorts; ++i) {
    OutputPort *output = &actor->outputPort[i];
    const PortDescription *descr = &klass->outputPortDescriptions[i];
    
    unsigned int produced = output->tokensProduced;
    
    unsigned int maxAvailable = 0;
    unsigned int spaceLeft;
    InputPort *consumer = (InputPort *) dllist_first(&output->consumers);
    while (consumer != NULL) {
      unsigned int available = produced - consumer->tokensConsumed;
      if (available > maxAvailable)
        maxAvailable = available;
      
      consumer = (InputPort *) dllist_next(&output->consumers,
                                           &consumer->asConsumer);
    }
    spaceLeft = output->capacity - maxAvailable;
    
    fprintf(out, " o:%s:%d", descr->name, spaceLeft);
  }
}
