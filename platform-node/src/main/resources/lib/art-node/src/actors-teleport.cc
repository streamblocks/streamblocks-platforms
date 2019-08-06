/*
 * Copyright (c) EPFL VLSC 2019, Ericsson AB, 2009-2013
 * Authors: Mahyar Emami (mahyar.emami@epfl.ch) 
 *          Endri Bezati (endri.bezati@epfl.ch)
 *          Patrik Persson (patrik.j.persson@ericsson.com)
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

#include <fcntl.h>
#include <sys/types.h>
#if (defined(_WIN32)) || (defined(_WIN64))
#include <ws2tcpip.h>
#pragma comment(lib, "Ws2_32.lib")
#else
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <unistd.h>

#endif

#include <iostream>
#include <stdio.h>
#include <stdlib.h>

#include <condition_variable>
#include <mutex>
#include <thread>

#include "BufferPool.h"
#include "actors-network.h"
#include "actors-teleport.h"
#include "dllist.h"
/* maximal size of generated class names */
#define GENERATED_CLASS_NAME_MAX (64)
#define MAX_BUFFER_SIZE 4096
using byte_t = std::uint8_t;

extern const ActorClass ActorClass_art_SocketSender;
extern const ActorClass ActorClass_art_SocketReceiver;

/*
 * Cached, previously created classes
 */
static dllist_head_t sender_classes;
static dllist_head_t receiver_classes;

/**
 * The SocketReceiver is a server, waiting for incoming tokens
 */
struct ActorInstance_art_SocketReceiver {
  AbstractActorInstance base;
  // std::unique_ptr<std::thread> pid;
#if (defined(_WIN32)) || (defined(_WIN64))
  SOCKET server_socket; /* socket we're listening on */
  SOCKET client_socket; /* listener, if there is one, or 0 */
#else
  int server_socket; /* socket we're listening on */
  int client_socket; /* listener, if there is one, or 0 */
#endif

  int port;         /* port, for the listener to find us */
  size_t bytesRead; /* to handle incomplete reads */

  int buf_pointer;

  int rest;

  int program_counter;

  uint32_t hashId; /* A unique hashid used to identify with the action
                      scheduler's locked busy mechanism */

  BufferPool<byte_t> *bufferPool;
  TokenizedBuffer<byte_t> currBuffer;
  bool partialRead;
  std::size_t offset;
};

/**
 * The SocketSender is a client, pushing tokens to the receiver
 */
struct ActorInstance_art_SocketSender {
  AbstractActorInstance base;
  // std::unique_ptr<std::thread> pid;

  /* Info on how to find the peer (SocketReceiver) */
  const char *remoteHost;
  int remotePort;
#if (defined(_WIN32)) || (defined(_WIN64))
  SOCKET socket;
#else
  int socket;        /* socket to peer */
#endif
  uint32_t hashId; /* A unique hashid used to identify with the action
                      scheduler's locked busy mechanism */
  int lockedBusy;  /* local sender state of locked/unlocked busy scheduler loop
                    */

  BufferPool<byte_t> *bufferPool;
  TokenizedBuffer<byte_t> currBuffer;
};

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

static void *receiver_thread(void *arg) {
  ActorInstance_art_SocketReceiver *instance =
      (ActorInstance_art_SocketReceiver *)arg;
  int tokenSize =
      instance->base.actorClass->outputPortDescriptions[0].tokenSize;
  tokenFn *functions =
      instance->base.actorClass->outputPortDescriptions[0].functions;
  int needSerialization = functions != NULL;
  int status;

#if (defined(_WIN32)) || (defined(_WIN64))
  // -- Initialize Winsock
  WSADATA wsaData;
  int iResult;
  iResult = WSAStartup(MAKEWORD(2, 2), &wsaData);
  if (iResult != 0) {
    printf("WSAStartup failed: %d\n", iResult);
    return NULL;
  }
#endif

  while (1) { /* one iteration per client */
    struct sockaddr client_addr;
    static socklen_t client_addr_size = sizeof(client_addr);

    status = accept(instance->server_socket, &client_addr, &client_addr_size);
    if (status >= 0) {
      instance->client_socket = status;
    } else {
      warn("accept() failed: %s", strerror(errno));
      return NULL;
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

      TokenizedBuffer<byte_t> stagingBuffer;
      // Try to get a free buffer.. if not poll until one is available
      if (!instance->bufferPool->try_dequeue_free_buffer(stagingBuffer))
        continue;

      // We want to make sure we got a correctly sized buffer!
      assert(stagingBuffer.get_capacity() == MAX_BUFFER_SIZE * tokenSize);

      lockedBusyNetworkToggle(instance->hashId);

      /**************************************************
       * Read simple tokens that don't need serialization
       **************************************************/
      std::size_t bytesRead = 0;

      auto buffer = stagingBuffer.to_c_buffer();

      status = recv(instance->client_socket, buffer + bytesRead,
                    tokenSize * MAX_BUFFER_SIZE - bytesRead, 0);

      if (status >= 0) {
        bytesRead = status;
      } else {
        /* on general read failures, assume client disconnected and wait for
         * another one */
        warn("read() failed: %s", strerror(errno));
        break;
      }

      stagingBuffer.set_count(bytesRead);
      // Move the buffer to the full queue
      while (!instance->bufferPool->try_enqueue_full_buffer(
          std::move(stagingBuffer)))
        ;
    }
  }
}

/* ------------------------------------------------------------------------- */

static void receiver_constructor(AbstractActorInstance *pBase) {
  ActorInstance_art_SocketReceiver *instance =
      (ActorInstance_art_SocketReceiver *)pBase;
  struct sockaddr_in server_addr;

  instance->server_socket = socket(AF_INET, SOCK_STREAM, 0);
  if (instance->server_socket < 0) {
    warn("could not open server socket: %s", strerror(errno));
    return;
  }

  static const int one = 1;
  setsockopt(instance->server_socket, SOL_SOCKET, SO_REUSEADDR, (char *)&one,
             sizeof(one));

  server_addr.sin_family = AF_INET;
  server_addr.sin_addr.s_addr = INADDR_ANY;
  server_addr.sin_port = 0; /* ask OS to select a port for us */

  if (bind(instance->server_socket, (struct sockaddr *)&server_addr,
           sizeof(server_addr)) < 0) {
    warn("could not bind: %s", strerror(errno));
    instance->server_socket = -1;
    return;
  }

  if (listen(instance->server_socket, 1 /* max one client */) < 0) {
    warn("could not listen: %s", strerror(errno));
    instance->server_socket = -1;
    return;
  }

  static socklen_t sockaddr_size = sizeof(struct sockaddr);
  if (getsockname(instance->server_socket, (struct sockaddr *)&server_addr,
                  &sockaddr_size) < 0) {
    warn("could not getsockname: %s", strerror(errno));
    instance->server_socket = -1;
    return;
  }
  assert(server_addr.sin_family == AF_INET);
  instance->port = ntohs(server_addr.sin_port);

  instance->bytesRead = 0;

  instance->hashId = getUniqueHashid();

  instance->buf_pointer = 0;

  instance->program_counter = 0;

  instance->rest = 0;

  int tokenSize = pBase->actorClass->outputPortDescriptions[0].tokenSize;
  instance->bufferPool = new BufferPool<byte_t>(7, MAX_BUFFER_SIZE * tokenSize);
  instance->partialRead = false;
  instance->offset = 0;

  std::thread *pid = new std::thread(receiver_thread, pBase);
  pid->detach();
}

/* ------------------------------------------------------------------------- */

ART_ACTION_SCHEDULER(receiver_action_scheduler) {
  ActorInstance_art_SocketReceiver *instance =
      (ActorInstance_art_SocketReceiver *)pBase;
  int tokenSize = pBase->actorClass->outputPortDescriptions[0].tokenSize;

  ART_ACTION_SCHEDULER_ENTER(0, 1);

  LocalOutputPort *output = &pBase->outputPort[0].localOutputPort;
  int avail = pinAvailOut_dyn(output);
  bool canFire = true;
  if (avail > 0) {
    TokenizedBuffer<byte_t> stagingBuffer;
    std::size_t readBySocket;
    if (instance->partialRead) {
      stagingBuffer = std::move(instance->currBuffer);
      readBySocket = (stagingBuffer.get_count() - instance->offset) / tokenSize;

    } else if (instance->bufferPool->try_dequeue_full_buffer(stagingBuffer)) {

      readBySocket = stagingBuffer.get_count() / tokenSize;
      instance->offset = 0;
      // instance->partialRead = false;

    } else {
      canFire = false;
    }
    if (canFire) {
      auto buffer = stagingBuffer.to_c_buffer();
      buffer += instance->offset;

      if (avail >= readBySocket) {

        pinWrite_dynRepeat(output, buffer, tokenSize, readBySocket);
        instance->partialRead = false;

        stagingBuffer.clear_count();
        // Move the tokenizedBuffer to the free queue
        while (!instance->bufferPool->try_enqueue_free_buffer(
            std::move(stagingBuffer)))
          ;

      } else {

        pinWrite_dynRepeat(output, buffer, tokenSize, avail);
        instance->partialRead = true;

        instance->offset += avail * tokenSize;
        instance->currBuffer = std::move(stagingBuffer);
      }
    }
  }
  ART_ACTION_SCHEDULER_EXIT(0, 1);
  return EXIT_CODE_YIELD;
}

/* ------------------------------------------------------------------------- */

static void receiver_destructor(AbstractActorInstance *pBase) {
  ActorInstance_art_SocketReceiver *instance =
      (ActorInstance_art_SocketReceiver *)pBase;

  if (instance->server_socket > 0) {
#if (defined(_WIN32)) || (defined(_WIN64))
    closesocket(instance->server_socket);
    WSACleanup();
#else
    close(instance->server_socket);
#endif

    if (instance->client_socket > 0) {
#if (defined(_WIN32)) || (defined(_WIN64))
      closesocket(instance->client_socket);
      WSACleanup();
#else
      close(instance->client_socket);
#endif
    }
  }

  delete instance->bufferPool;
  /*
   * Special case: normal classes have their name as a read-only string
   * somewhere in the binary's read-only segment. Here the name is instead
   * allocated on the heap, in createSocketReceiver() using strdup().
   * Hence, we need to free() it here.
   */
  free((void *)pBase->instanceName);
}

/* ------------------------------------------------------------------------- */

static void *sender_thread(void *arg) {
  ActorInstance_art_SocketSender *instance =
      (ActorInstance_art_SocketSender *)arg;
  int tokenSize = instance->base.actorClass->inputPortDescriptions[0].tokenSize;
  tokenFn *functions =
      instance->base.actorClass->inputPortDescriptions[0].functions;
  int needSerialization = functions != NULL;

#if (defined(_WIN32)) || (defined(_WIN64))
  // -- Initialize Winsock
  WSADATA wsaData;
  int iResult;
  iResult = WSAStartup(MAKEWORD(2, 2), &wsaData);
  if (iResult != 0) {
    printf("WSAStartup failed: %d\n", iResult);
    return NULL;
  }
#endif
  /*
          struct sockaddr_in server_addr;

          server_addr.sin_family = AF_INET;

      if (!inet_pton(AF_INET, instance->remoteHost, &server_addr.sin_addr)) {
          warn("could not parse host specification '%s'", instance->remoteHost);
          instance->socket = -1;
          return NULL;
      }

      server_addr.sin_port = htons(instance->remotePort);

      printf("IP address is: %s\n", inet_ntoa(server_addr.sin_addr));
      printf("port is: %d\n", (int) ntohs(server_addr.sin_port));
          if (connect(instance->socket, (struct sockaddr*) &server_addr,
                  sizeof(server_addr)) < 0) {
          printf("IP address is: %s\n", inet_ntoa(server_addr.sin_addr));
          printf("port is: %d\n", (int) ntohs(server_addr.sin_port));
                  warn("could not connect: %s", strerror(errno));
                  instance->socket = -1;
                  return NULL;
          }
  */

  struct sockaddr_in server_addr;

  server_addr.sin_family = AF_INET;

  if (!inet_aton(instance->remoteHost, &server_addr.sin_addr)) {
    warn("could not parse host specification '%s'", instance->remoteHost);
    instance->socket = -1;
    return NULL;
  }

  server_addr.sin_port = htons(instance->remotePort);

  if (connect(instance->socket, (struct sockaddr *)&server_addr,
              sizeof(server_addr)) < 0) {
    warn("could not connect: %s", strerror(errno));
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

    TokenizedBuffer<byte_t> stagingBuffer;
    if (!instance->bufferPool->try_dequeue_full_buffer(stagingBuffer))
      continue;
    assert(stagingBuffer.get_capacity() == tokenSize * MAX_BUFFER_SIZE);
    std::size_t bytesWritten = 0;

    /**************************************************
     * Write simple tokens that don't need serialization
     **************************************************/
    auto bytesToWrite = stagingBuffer.get_count();
    do {
      auto buffer = stagingBuffer.to_c_buffer();

      int status = send(instance->socket, buffer + bytesWritten,
                        bytesToWrite - bytesWritten, 0);

      if (status >= 0) {
        bytesWritten += status;
        assert(bytesWritten <= bytesToWrite);
      } else {
        /* on general write failures, bail out*/
        warn("write() failed: %s", strerror(errno));
        return NULL;
      }
    } while (bytesWritten != bytesToWrite);

    bytesWritten = 0;
    stagingBuffer.clear_count();
    while (!instance->bufferPool->try_enqueue_free_buffer(
        std::move(stagingBuffer)))
      ;
  }
}

/* ------------------------------------------------------------------------- */

static void sender_constructor(AbstractActorInstance *pBase) {
  ActorInstance_art_SocketSender *instance =
      (ActorInstance_art_SocketSender *)pBase;

  instance->socket = socket(AF_INET, SOCK_STREAM, 0);
  if (instance->socket < 0) {
    warn("could not open client socket: %s", strerror(errno));
    return;
  }

  /* don't touch remoteHost here: it was set in
   setSenderRemoteAddress() below */

  instance->hashId = getUniqueHashid();
  instance->lockedBusy = 0;

  /* since we only have the class here, not the extended class, we need
   * to take the long way to find the port description */

  int tokenSize = pBase->actorClass->inputPortDescriptions[0].tokenSize;
  instance->bufferPool = new BufferPool<byte_t>(7, MAX_BUFFER_SIZE * tokenSize);

  std::thread *pid = new std::thread(sender_thread, pBase);
  pid->detach();
}

/* ------------------------------------------------------------------------- */

ART_ACTION_SCHEDULER(sender_action_scheduler) {
  ActorInstance_art_SocketSender *instance =
      (ActorInstance_art_SocketSender *)pBase;
  int tokenSize = pBase->actorClass->inputPortDescriptions[0].tokenSize;

  ART_ACTION_SCHEDULER_ENTER(0, 1);

  LocalInputPort *input = &pBase->inputPort[0].localInputPort;

  int avail = pinAvailIn_dyn(input);
  if (avail > 0) {
    /* We have data to send keep the scheduler loop locked busy, keep track
     * locally of toggling */
    if (!instance->lockedBusy) {
      lockedBusyNetworkToggle(instance->hashId);
      instance->lockedBusy = 1;
    }

    TokenizedBuffer<byte_t> stagingBuffer;
    while (!instance->bufferPool->try_dequeue_free_buffer(stagingBuffer))
      ;
    auto bufferCapacity = stagingBuffer.get_capacity() / tokenSize;
    auto count = (avail <= bufferCapacity) ? avail : bufferCapacity;
    auto buffer = stagingBuffer.to_c_buffer();
    pinRead_dynRepeat(input, buffer, tokenSize, count);

    stagingBuffer.set_count(count * tokenSize);

    // Move the buffer to the pool
    while (!instance->bufferPool->try_enqueue_full_buffer(
        std::move(stagingBuffer)))
      ;

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

  if (instance->socket > 0) {
#if (defined(_WIN32)) || (defined(_WIN64))
    closesocket(instance->socket);
    WSACleanup();
#else
    close(instance->socket);
#endif
  }

  if (instance->remoteHost) {
    free((void *)instance->remoteHost);
  }
  delete instance->bufferPool;
  /*
   * Special case: normal classes have their name as a read-only
   * string somewhere in the binary's read-only segment. Here the name
   * is instead allocated on the heap, in createSocketSender() using
   * strdup(). Hence, we need to free() it here.
   */
  free((void *)pBase->instanceName);
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
  if (needSerialization)
    snprintf(xclass->className, GENERATED_CLASS_NAME_MAX, "_receiver_%dB%8p",
             tokenSize, functions->serialize);
  else
    snprintf(xclass->className, GENERATED_CLASS_NAME_MAX, "_receiver_%dB",
             tokenSize);

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
  struct extended_class *xclass = static_cast<struct extended_class *>(
      calloc(1, sizeof(struct extended_class)));

  /* make up a name */
  if (needSerialization)
    snprintf(xclass->className, GENERATED_CLASS_NAME_MAX, "_sender_%dB%8p",
             tokenSize, functions->serialize);
  else
    snprintf(xclass->className, GENERATED_CLASS_NAME_MAX, "_sender_%dB",
             tokenSize);

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
  xclass->actorClass.numInputPorts = 1;
  xclass->actorClass.inputPortDescriptions = &xclass->portDescription;
  xclass->actorClass.numOutputPorts = 0;
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
