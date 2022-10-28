/*
 * Copyright (c) Ericsson AB, 2009-2013
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

#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <arpa/inet.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include "actors-network.h"
#include "actors-teleport.h"
#include "dllist.h"
#include "io-port.h"
#include "logging.h"

/* maximal size of generated class names */
#define GENERATED_CLASS_NAME_MAX (32)

extern const ActorClass ActorClass_art_SocketSender;
extern const ActorClass ActorClass_art_SocketReceiver;

/*
 * Cached, previously created classes
 */
static dllist_head_t sender_classes;
static dllist_head_t receiver_classes;

/**
 * Simple monitor for producing/consuming a single token. Used for
 * synchronization between socket thread and actor.
 */
struct TokenMonitor {
  pthread_mutex_t lock;
  pthread_cond_t available; /* only used by SocketSender */
  pthread_cond_t empty;
  int full; /* 0 or 1 */
  char *tokenBuffer;
  size_t serializeBufferSize;
  char *serializeBuffer;
  int die;
  pthread_t pid;
};

/**
 * The SocketReceiver is a server, waiting for incoming tokens
 */
typedef struct {
  AbstractActorInstance base;
  pthread_t pid;

  int server_socket; /* socket we're listening on */
  int client_socket; /* listener, if there is one, or 0 */
  int port;          /* port, for the listener to find us */
  size_t bytesRead;  /* to handle incomplete reads */

  uint32_t hashId; /* A unique hashid used to identify with the action
                      scheduler's locked busy mechanism */
  struct TokenMonitor tokenMon;
} ActorInstance_art_SocketReceiver;

/**
 * The SocketSender is a client, pushing tokens to the receiver
 */
typedef struct {
  AbstractActorInstance base;
  pthread_t pid;

  /* Info on how to find the peer (SocketReceiver) */
  char *remoteHost;
  int remotePort;

  int socket; /* socket to peer */

  uint32_t hashId; /* A unique hashid used to identify with the action
                      scheduler's locked busy mechanism */
  int lockedBusy;  /* local sender state of locked/unlocked busy scheduler loop
                    */
  struct TokenMonitor tokenMon;
} ActorInstance_art_SocketSender;

/**
 * An instance of this struct is created for each sender/receiver
 * class
 */
struct extended_class {
  dllist_element_t elem;
  ActorClass actorClass;
  PortDescription portDescription;
  char className[GENERATED_CLASS_NAME_MAX];
};

/* ------------------------------------------------------------------------- */

static void ensureInitialized(void) {
  static int initialized = 0;
  if (!initialized) {
    dllist_create(&sender_classes);
    dllist_create(&receiver_classes);

    initialized = 1;
  }
}

/* ------------------------------------------------------------------------- */

static void initTokenMonitor(struct TokenMonitor *mon, int tokenSize) {
  pthread_mutex_init(&mon->lock, NULL);
  pthread_cond_init(&mon->available, NULL);
  pthread_cond_init(&mon->empty, NULL);
  mon->full = 0;

  mon->tokenBuffer = static_cast<char *>(calloc(1, tokenSize));

  mon->serializeBufferSize = 0;
  mon->serializeBuffer = NULL;
}

/* ------------------------------------------------------------------------- */

static void destroyTokenMonitor(struct TokenMonitor *mon) {
  pthread_mutex_lock(&mon->lock);

  mon->die = 1;
  pthread_cond_broadcast(&mon->available);
  pthread_cond_broadcast(&mon->empty);

  pthread_mutex_unlock(&mon->lock);
  m_message("Waiting for thread");
  pthread_join(mon->pid, NULL);
  m_message("Thread exited");

  pthread_cond_destroy(&mon->empty);
  pthread_cond_destroy(&mon->available);

  pthread_mutex_destroy(&mon->lock);

  free(mon->tokenBuffer);
  if (mon->serializeBuffer != NULL && mon->serializeBufferSize > 0) {
    free(mon->serializeBuffer);
    mon->serializeBufferSize = 0;
  }
}

/* ------------------------------------------------------------------------- */

static int token_monitor_wait_until_available(struct TokenMonitor *self,
                                              const char *instance_name) {
  int keep_going = 1;
  pthread_mutex_lock(&self->lock);
  while (self->full) {
    pthread_cond_wait(&self->empty, &self->lock);
    if (self->die) {
      m_message("Noticed death of token monitor for %s", instance_name);
      keep_going = 0;
      break;
    }
  }
  pthread_mutex_unlock(&self->lock);
  return keep_going;
}

static int
receiver_read_simple_data(ActorInstance_art_SocketReceiver *instance) {
  const char *instance_name = ((AbstractActorInstance *)instance)->instanceName;
  int status = 1;
  int bytes_read;
  int tokenSize =
      instance->base.actorClass->outputPortDescriptions[0].tokenSize;

  /**************************************************
   * Read simple tokens that don't need serialization
   **************************************************/
  do {
    bytes_read = read(instance->client_socket,
                      instance->tokenMon.tokenBuffer + instance->bytesRead,
                      tokenSize - instance->bytesRead);

    if (instance->tokenMon.die) {
      m_message("Receiver %s noticed death of token monitor", instance_name);
      status = -1;
      goto out;
    }

    if (bytes_read > 0) {
      instance->bytesRead += bytes_read;
      assert(instance->bytesRead <= tokenSize);
    } else {
      /* on general read failures, assume client disconnected and wait for
       * another one */
      instance->bytesRead = 0;
      m_message("read() failed: %s", strerror(errno));
      status = 0;
      goto out;
    }
  } while (instance->bytesRead != tokenSize);

  if (instance->tokenMon.die) {
    m_warning("receiver thread %s exiting", instance_name);
    status = -1;
    goto out;
  }

  /* if we failed reading, break out of this loop to and wait for
     another client */
  if (instance->bytesRead != tokenSize) {
    status = 0;
  }
out:
  return status;
}

static int
receiver_read_serialized_data(ActorInstance_art_SocketReceiver *instance) {
  /************************************************
   * Read complex tokens that do need serialization
   ************************************************/
  int status = 1;
  int32_t sz = 0;
  int total_bytes_read = 0;
  int bytes_read;
  tokenFn *functions =
      instance->base.actorClass->outputPortDescriptions[0].functions;

  const char *instance_name = ((AbstractActorInstance *)instance)->instanceName;

  // Read serialization token size
  do {
    bytes_read = read(instance->client_socket, &sz + total_bytes_read,
                      sizeof(int32_t) - total_bytes_read);
    if (instance->tokenMon.die) {
      m_message("Receiver %s noticed death of token monitor", instance_name);
      status = -1;
      goto out;
    }

    if (bytes_read >= 0) {
      total_bytes_read += bytes_read;
    } else {
      total_bytes_read = 0;
      m_message("read() failed: %s", strerror(errno));
      status = 0;
      goto out;
    }
  } while (total_bytes_read != sizeof(int32_t));

  // Read serialized token data

  if (total_bytes_read > 0 && sz > 0) {
    total_bytes_read = 0;
    if (instance->tokenMon.serializeBufferSize < sz) {
      // Need more memory to handle the serialization
      instance->tokenMon.serializeBuffer =
          static_cast<char *>(realloc(instance->tokenMon.serializeBuffer, sz));
      instance->tokenMon.serializeBufferSize = sz;
    }

    do {
      m_message("Receiver %s reading", instance_name);
      bytes_read = read(instance->client_socket,
                        instance->tokenMon.serializeBuffer + total_bytes_read,
                        sz - total_bytes_read);
      m_message("Receiver %s read %d bytes", instance_name, bytes_read);

      if (instance->tokenMon.die) {
        m_message("Receiver %s noticed death of token monitor", instance_name);
        status = -1;
        goto out;
      }

      if (bytes_read > 0) {
        total_bytes_read += bytes_read;
      } else {
        m_message("read() failed: %s", strerror(errno));
        status = 0;
        goto out;
      }
    } while (total_bytes_read != sz);

    if (total_bytes_read != sz) {
      /*
       * If we got here it is a failure, since
       * we have a serialization size but can't get all
       * the data. Anyway break out of here and wait
       * for a new sender to connect.
       */
      status = 0;
      goto out;
    } else {
      // Deserialize the incoming token (will allocate it on the heap) and write
      // the reference to the tokenBuffer Also assert that the deserialization
      // used all the data
      assert((functions->deserialize((void **)instance->tokenMon.tokenBuffer,
                                     instance->tokenMon.serializeBuffer, sz) -
              instance->tokenMon.serializeBuffer) == sz);
    }
  } else {
    /* if we failed reading, break out of this loop to and wait for
       another client */
    status = 0;
  }

out:

  return status;
}

static void *receiver_thread(void *arg) {
  ActorInstance_art_SocketReceiver *instance =
      (ActorInstance_art_SocketReceiver *)arg;
  int is_serialized = 0;

  int status;
  const char *instance_name = ((AbstractActorInstance *)instance)->instanceName;

  if (instance->base.actorClass->outputPortDescriptions[0].functions != NULL) {
    is_serialized = 1;
  }
  while (1) { /* one iteration per client */
    struct sockaddr client_addr;
    static socklen_t client_addr_size = sizeof(client_addr);

    instance->client_socket =
        accept(instance->server_socket, &client_addr, &client_addr_size);
    m_message("Receiver %s accepting", instance_name);
    if (instance->client_socket < 0) {
      m_warning("accept() failed: %s", strerror(errno));
      goto out;
    }

    /* Don't buffer, send tokens asap */
    static const int one = 1;
    (void)setsockopt(instance->client_socket, IPPROTO_TCP, TCP_NODELAY,
                     (char *)&one, sizeof(one));

    while (1) { /* one iteration per token */

      /* Enable locked busy execution during period of token monitor full,
       * otherwise we might be waiting forever. Matched below.
       */
      lockedBusyNetworkToggle(instance->hashId);

      /* Block until the monitor has space for a token */
      if (!token_monitor_wait_until_available(&instance->tokenMon,
                                              instance_name)) {
        goto out;
      }
      /* Disable locked busy execution. Matched above.
       */
      lockedBusyNetworkToggle(instance->hashId);

      if (is_serialized) {
        status = receiver_read_serialized_data(instance);
      } else {
        status = receiver_read_simple_data(instance);
      }

      if (status == -1 || status == 0) {
        goto out;
      } else if (status == 0) {
        break;
      } else {
        /* Proceed */
      }

      instance->bytesRead = 0;

      /* Update the monitor and notify actor */
      assert(!instance->tokenMon.full);
      {
        pthread_mutex_lock(&instance->tokenMon.lock);
        instance->tokenMon.full = 1;
        pthread_mutex_unlock(&instance->tokenMon.lock);
      }
    }
  }
out:
  m_message("Receiver %s exiting", instance_name);
  return NULL;
}

/* ------------------------------------------------------------------------- */

static void receiver_constructor(AbstractActorInstance *pBase) {
  ActorInstance_art_SocketReceiver *instance =
      (ActorInstance_art_SocketReceiver *)pBase;
  struct sockaddr_in server_addr;

  instance->server_socket = socket(AF_INET, SOCK_STREAM, 0);
  if (instance->server_socket < 0) {
    m_warning("could not open server socket: %s", strerror(errno));
    return;
  }

  static const int one = 1;
  setsockopt(instance->server_socket, SOL_SOCKET, SO_REUSEADDR, &one,
             sizeof(one));

  server_addr.sin_family = AF_INET;
  server_addr.sin_addr.s_addr = INADDR_ANY;
  server_addr.sin_port = 0; /* ask OS to select a port for us */

  if (bind(instance->server_socket, (struct sockaddr *)&server_addr,
           sizeof(server_addr)) < 0) {
    m_warning("could not bind: %s", strerror(errno));
    instance->server_socket = -1;
    return;
  }

  if (listen(instance->server_socket, 1 /* max one client */) < 0) {
    m_warning("could not listen: %s", strerror(errno));
    instance->server_socket = -1;
    return;
  }

  static socklen_t sockaddr_size = sizeof(struct sockaddr);
  if (getsockname(instance->server_socket, (struct sockaddr *)&server_addr,
                  &sockaddr_size) < 0) {
    m_warning("could not getsockname: %s", strerror(errno));
    instance->server_socket = -1;
    return;
  }
  assert(server_addr.sin_family == AF_INET);
  instance->port = ntohs(server_addr.sin_port);
  m_message("Listen: %d", instance->port);

  instance->bytesRead = 0;

  instance->hashId = getUniqueHashid();

  /* since we only have the class here, not the extended class, we need
   * to take the long way to find the port description */
  initTokenMonitor(&instance->tokenMon,
                   pBase->actorClass->outputPortDescriptions[0].tokenSize);

  pthread_create(&instance->pid, NULL, &receiver_thread, pBase);
  instance->tokenMon.pid = instance->pid;
}

/* ------------------------------------------------------------------------- */

ART_ACTION_SCHEDULER(receiver_action_scheduler) {
  ActorInstance_art_SocketReceiver *instance =
      (ActorInstance_art_SocketReceiver *)pBase;
  int tokenSize = pBase->actorClass->outputPortDescriptions[0].tokenSize;

  ART_ACTION_SCHEDULER_ENTER(0, 1);

  // LocalOutputPort *output = &pBase->outputPort[0].localOutputPort;
  OutputPort *output = output_port_array_get(pBase->outputPort, 0);
  if (pinAvailOut_dyn(output) > 0) {
    /* If there is a new token in the monitor, take it */
    {
      pthread_mutex_lock(&instance->tokenMon.lock);
      if (instance->tokenMon.full) {
        pinWrite_dyn(output, instance->tokenMon.tokenBuffer, tokenSize);
        instance->tokenMon.full = 0;
        pthread_cond_signal(&instance->tokenMon.empty);
      }
      pthread_mutex_unlock(&instance->tokenMon.lock);
    }
  }
  ART_ACTION_SCHEDULER_EXIT(0, 1);
  return EXIT_CODE_YIELD;
}

/* ------------------------------------------------------------------------- */

static void receiver_destructor(AbstractActorInstance *pBase) {
  ActorInstance_art_SocketReceiver *instance =
      (ActorInstance_art_SocketReceiver *)pBase;

  m_message("Destroying %s", pBase->instanceName);
  if (instance->server_socket > 0) {
    close(instance->server_socket);
    if (instance->client_socket > 0) {
      close(instance->client_socket);
    }
  }

  destroyTokenMonitor(&instance->tokenMon);

  /*
   * Special case: normal classes have their name as a read-only string
   * somewhere in the binary's read-only segment. Here the name is instead
   * allocated on the heap, in createSocketReceiver() using strdup().
   * Hence, we need to free() it here.
   */
  free(pBase->instanceName);
}

/* ------------------------------------------------------------------------- */

static void *sender_thread(void *arg) {
  ActorInstance_art_SocketSender *instance =
      (ActorInstance_art_SocketSender *)arg;
  int tokenSize = instance->base.actorClass->inputPortDescriptions[0].tokenSize;
  tokenFn *functions =
      instance->base.actorClass->inputPortDescriptions[0].functions;
  int needSerialization = functions != NULL;
  struct sockaddr_in server_addr;
  // const char *instance_name = ((AbstractActorInstance
  // *)instance)->instanceName;

  server_addr.sin_family = AF_INET;

  assert(instance->remoteHost != NULL);
  if (!inet_aton(instance->remoteHost, &server_addr.sin_addr)) {
    m_warning("could not parse host specification '%s'", instance->remoteHost);
    instance->socket = -1;
    return NULL;
  }

  server_addr.sin_port = htons(instance->remotePort);

  if (connect(instance->socket, (struct sockaddr *)&server_addr,
              sizeof(server_addr)) < 0) {
    m_warning("could not connect: %s", strerror(errno));
    instance->socket = -1;
    return NULL;
  }

  /* Don't buffer, send tokens asap */
  static const int one = 1;
  (void)setsockopt(instance->socket, IPPROTO_TCP, TCP_NODELAY, (char *)&one,
                   sizeof(one));

  while (1) { /* one iteration per token */

    /* Enable execution, in case network has gone idle otherwise we might wait
     * forever */
    wakeUpNetwork();

    /* Block until the monitor holds a token */
    {
      pthread_mutex_lock(&instance->tokenMon.lock);
      while (!instance->tokenMon.full) {
        pthread_cond_wait(&instance->tokenMon.available,
                          &instance->tokenMon.lock);
        if (instance->tokenMon.die) {
          m_message("Noticed death of token monitor for %s",
                    ((AbstractActorInstance *)instance)->instanceName);
          break;
        }
      }
      pthread_mutex_unlock(&instance->tokenMon.lock);
      if (instance->tokenMon.die) {
        m_message("Returning from sender thread");
        return NULL;
      }
    }

    int bytesWritten = 0;
    if (needSerialization) {
      /************************************************
       * Write complex tokens that do need serialization
       ************************************************/
      int32_t sz = functions->size(*((void **)instance->tokenMon.tokenBuffer));
      if (instance->tokenMon.serializeBufferSize < (sz + sizeof(int32_t))) {
        // Need more memory to handle the serialization
        instance->tokenMon.serializeBuffer = static_cast<char *>(
            realloc(instance->tokenMon.serializeBuffer, sz + sizeof(int32_t)));
        instance->tokenMon.serializeBufferSize = sz + sizeof(int32_t);
      }
      // Write the size first
      *((int32_t *)instance->tokenMon.serializeBuffer) = sz;
      // include the size bytes in sz
      sz += sizeof(int32_t);
      // Serialize the outgoing token and write the data into the serialization
      // buffer Also assert that the serialization wrote all the data
      long send_size = (functions->serialize( *((void **)instance->tokenMon.tokenBuffer), instance->tokenMon.serializeBuffer + sizeof(int32_t) ) - instance->tokenMon.serializeBuffer);
      assert(send_size == sz);
      // FIXME we free the token here, but that only works if the port has no
      // fan-out, that limitation also exist in the Caltoopia generated code.
      functions->free(*((void **)instance->tokenMon.tokenBuffer), 1 /*TRUE*/);

      do {
        int status = write(instance->socket,
                           instance->tokenMon.serializeBuffer + bytesWritten,
                           sz - bytesWritten);
        if (status >= 0) {
          bytesWritten += status;
          assert(bytesWritten <= sz);
        } else {
          /* on general write failures, bail out*/
          m_warning("write() failed: %s", strerror(errno));
          return NULL;
        }
      } while (bytesWritten != sz);
    } else {
      /**************************************************
       * Write simple tokens that don't need serialization
       **************************************************/
      do {
        int status = write(instance->socket,
                           instance->tokenMon.tokenBuffer + bytesWritten,
                           tokenSize - bytesWritten);

        if (status >= 0) {
          bytesWritten += status;
          assert(bytesWritten <= tokenSize);
        } else {
          /* on general write failures, bail out*/
          m_warning("write() failed: %s", strerror(errno));
          return NULL;
        }
      } while (bytesWritten != tokenSize);
    }

    bytesWritten = 0;

    /* Update the monitor and notify actor */
    assert(instance->tokenMon.full);
    {
      pthread_mutex_lock(&instance->tokenMon.lock);
      instance->tokenMon.full = 0;
      pthread_cond_signal(&instance->tokenMon.empty);
      pthread_mutex_unlock(&instance->tokenMon.lock);
    }
  }
}

void start_sender_thread(AbstractActorInstance *instance) {
  ActorInstance_art_SocketSender *self =
      (ActorInstance_art_SocketSender *)instance;
  pthread_create(&self->pid, NULL, &sender_thread, instance);
  self->tokenMon.pid = self->pid;
}

/* ------------------------------------------------------------------------- */

static void sender_constructor(AbstractActorInstance *pBase) {
  ActorInstance_art_SocketSender *instance =
      (ActorInstance_art_SocketSender *)pBase;

  instance->socket = socket(AF_INET, SOCK_STREAM, 0);
  if (instance->socket < 0) {
    m_warning("could not open client socket: %s", strerror(errno));
    return;
  }

  instance->hashId = getUniqueHashid();
  instance->lockedBusy = 0;

  /* since we only have the class here, not the extended class, we need
   * to take the long way to find the port description */
  initTokenMonitor(&instance->tokenMon,
                   pBase->actorClass->inputPortDescriptions[0].tokenSize);

  /* not here */
  // pthread_create(&instance->pid, NULL, &sender_thread, pBase);
}

/* ------------------------------------------------------------------------- */

ART_ACTION_SCHEDULER(sender_action_scheduler) {
  ActorInstance_art_SocketSender *instance =
      (ActorInstance_art_SocketSender *)pBase;
  int tokenSize = pBase->actorClass->inputPortDescriptions[0].tokenSize;

  ART_ACTION_SCHEDULER_ENTER(0, 1);

  // LocalInputPort *input = &pBase->inputPort[0].localInputPort;
  InputPort *input = input_port_array_get(pBase->inputPort, 0);
  if (pinAvailIn_dyn(input) > 0) {
    /* We have data to send keep the scheduler loop locked busy, keep track
     * locally of toggling */
    if (!instance->lockedBusy) {
      lockedBusyNetworkToggle(instance->hashId);
      instance->lockedBusy = 1;
    }
    /* If there is room for a new token in the monitor, push one there */
    if (input_port_producer(input) != NULL) {
      pthread_mutex_lock(&instance->tokenMon.lock);
      if (!instance->tokenMon.full) {
        pinRead_dyn(input, instance->tokenMon.tokenBuffer, tokenSize);
        instance->tokenMon.full = 1;
        pthread_cond_signal(&instance->tokenMon.available);
      }
      pthread_mutex_unlock(&instance->tokenMon.lock);
    }
  } else {
    /* We don't have data to send no need to have the scheduler loop locked
     * busy, keep track locally of toggling */
    if (instance->lockedBusy) {
      lockedBusyNetworkToggle(instance->hashId);
      instance->lockedBusy = 0;
    }
  }

  ART_ACTION_SCHEDULER_EXIT(0, 1);
  return EXIT_CODE_YIELD;
}

/* ------------------------------------------------------------------------- */

static void sender_destructor(AbstractActorInstance *pBase) {
  ActorInstance_art_SocketSender *instance =
      (ActorInstance_art_SocketSender *)pBase;

  m_message("Destroying %s", pBase->instanceName);
  if (instance->socket > 0) {
    close(instance->socket);
  }

  destroyTokenMonitor(&instance->tokenMon);

  if (instance->remoteHost) {
    free(instance->remoteHost);
  }

  /*
   * Special case: normal classes have their name as a read-only
   * string somewhere in the binary's read-only segment. Here the name
   * is instead allocated on the heap, in createSocketSender() using
   * strdup(). Hence, we need to free() it here.
   */
  free(pBase->instanceName);
}

/* ========================================================================= */

const ActorClass *getReceiverClass(int tokenSize, tokenFn *functions) {
  ensureInitialized();

  // Detect need for serialization with if such function is provided
  int needSerialization = functions->serialize != NULL;

  /*
   * Linear search should be fine: this list is likely to be small, and
   * this is only done during network construction time, not at runtime.
   */
  dllist_element_t *elem = dllist_first(&receiver_classes);
  while (elem) {
    struct extended_class *xclass = (struct extended_class *)elem;
    if (needSerialization) {
      // If we already have a class pointing to the same serialization function
      // take it
      if (xclass->portDescription.functions->serialize ==
          functions->serialize) {
        return &xclass->actorClass;
      }
    } else {
      if (xclass->portDescription.tokenSize == tokenSize) {
        return &xclass->actorClass;
      }
    }
    elem = dllist_next(&receiver_classes, elem);
  }

  /* no class found -- we need to create one */
  struct extended_class *xclass =
      static_cast<extended_class *>(calloc(1, sizeof(struct extended_class)));

  /* make up a name */
  if (needSerialization) {
    snprintf(xclass->className, GENERATED_CLASS_NAME_MAX, "_receiver_%dB%08lx",
             tokenSize, (intptr_t)functions->serialize);
  } else {
    snprintf(xclass->className, GENERATED_CLASS_NAME_MAX, "_receiver_%dB",
             tokenSize);
  }

  xclass->portDescription.name = "out";
  xclass->portDescription.tokenSize = tokenSize;
  if (needSerialization) {
    xclass->portDescription.functions =
        static_cast<tokenFn *>(calloc(1, sizeof(tokenFn)));
    memcpy(xclass->portDescription.functions, functions, sizeof(tokenFn));
  }
  xclass->actorClass.majorVersion = ACTORS_RTS_MAJOR;
  xclass->actorClass.minorVersion = ACTORS_RTS_MINOR;
  xclass->actorClass.name = xclass->className;
  xclass->actorClass.sizeActorInstance =
      sizeof(ActorInstance_art_SocketReceiver);
  xclass->actorClass.numInputPorts = 0;
  xclass->actorClass.numOutputPorts = 1;
  xclass->actorClass.outputPortDescriptions = &xclass->portDescription;
  xclass->actorClass.action_scheduler = &receiver_action_scheduler;
  xclass->actorClass.constructor = &receiver_constructor;
  xclass->actorClass.destructor = &receiver_destructor;

  dllist_append(&receiver_classes, &xclass->elem);

  return &xclass->actorClass;
}

/* ------------------------------------------------------------------------- */

const ActorClass *getSenderClass(int tokenSize, tokenFn *functions) {
  ensureInitialized();

  // Detect need for serialization with if such function is provided
  int needSerialization = functions->serialize != NULL;

  /*
   * Linear search should be fine: this list is likely to be small, and
   * this is only done during network construction time, not at runtime.
   */
  dllist_element_t *elem = dllist_first(&sender_classes);
  while (elem) {
    struct extended_class *xclass = (struct extended_class *)elem;
    if (needSerialization) {
      // If we already have a class pointing to the same serialization function
      // take it
      if (xclass->portDescription.functions->serialize ==
          functions->serialize) {
        return &xclass->actorClass;
      }
    } else {
      if (xclass->portDescription.tokenSize == tokenSize) {
        return &xclass->actorClass;
      }
    }
    elem = dllist_next(&sender_classes, elem);
  }

  /* no class found -- we need to create one */
  struct extended_class *xclass =
      static_cast<extended_class *>(calloc(1, sizeof(struct extended_class)));

  /* make up a name */
  if (needSerialization) {
    snprintf(xclass->className, GENERATED_CLASS_NAME_MAX, "_sender_%dB%08lx",
             tokenSize, (intptr_t)functions->serialize);
  } else {
    snprintf(xclass->className, GENERATED_CLASS_NAME_MAX, "_sender_%dB",
             tokenSize);
  }

  xclass->portDescription.name = "in";
  xclass->portDescription.tokenSize = tokenSize;
  if (needSerialization) {
    xclass->portDescription.functions =
        static_cast<tokenFn *>(calloc(1, sizeof(tokenFn)));
    memcpy(xclass->portDescription.functions, functions, sizeof(tokenFn));
  }
  xclass->actorClass.majorVersion = ACTORS_RTS_MAJOR;
  xclass->actorClass.minorVersion = ACTORS_RTS_MINOR;
  xclass->actorClass.name = xclass->className;
  xclass->actorClass.sizeActorInstance = sizeof(ActorInstance_art_SocketSender);
  xclass->actorClass.numOutputPorts = 0;
  xclass->actorClass.numInputPorts = 1;
  xclass->actorClass.inputPortDescriptions = &xclass->portDescription;
  xclass->actorClass.action_scheduler = &sender_action_scheduler;
  xclass->actorClass.constructor = &sender_constructor;
  xclass->actorClass.destructor = &sender_destructor;

  dllist_append(&sender_classes, &xclass->elem);

  return &xclass->actorClass;
}

/* ------------------------------------------------------------------------- */

int getReceiverPort(AbstractActorInstance *pBase) {
  /* Ensure this is actually an instance of a known receiver class */

  int correct_instance_type = 0;
  dllist_element_t *elem = dllist_first(&receiver_classes);
  while (elem) {
    struct extended_class *xclass = (struct extended_class *)elem;
    if (pBase->actorClass == &xclass->actorClass) {
      correct_instance_type = 1;
    }
    elem = dllist_next(&receiver_classes, elem);
  }
  assert(correct_instance_type);

  return ((ActorInstance_art_SocketReceiver *)pBase)->port;
}

/* ------------------------------------------------------------------------- */

void setSenderRemoteAddress(AbstractActorInstance *pBase, const char *host,
                            int port) {
  /* Ensure this is actually an instance of a known sender class */
  m_message("remote: %s:%d", host, port);
  int correct_instance_type = 0;
  dllist_element_t *elem = dllist_first(&sender_classes);
  while (elem) {
    struct extended_class *xclass = (struct extended_class *)elem;
    if (pBase->actorClass == &xclass->actorClass) {
      correct_instance_type = 1;
    }
    elem = dllist_next(&sender_classes, elem);
  }
  assert(correct_instance_type);

  ActorInstance_art_SocketSender *instance =
      (ActorInstance_art_SocketSender *)pBase;

  instance->remoteHost = strdup(host);
  instance->remotePort = port;
}
