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

#ifndef ACTORS_CONFIG_H
#define ACTORS_CONFIG_H

#include "actors-typedefs.h"

/* make the header usable from C++ */
#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

  /**
   * Initialize actor list and launch worker thread
   */
  void initActorNetwork(void);

  /**
   * Called by parser. Creates an actor instance.  The actor will not
   * execute until enableActorInstance() below is called.
   * @param actorClass an ActorClass
   */
  AbstractActorInstance * createActorInstance(const ActorClass *actorClass,
                                              const char *actor_name);

  /**
   * Called by parser. Sets an actor parameter.
   *
   * @param instance   actor instance
   * @param key        a named parameter of the actor
   * @param value      the actual parameter value
   */
  void setActorParam(AbstractActorInstance *instance,
                     const char *key,
                     const char *value);

  /**
   * Called by parser. Runs constructor and enables an actor for
   * execution. If the worker thread is idle, it will be woken up.
   */
  void enableActorInstance(const char *actor_name);

  /**
   * Called by parser. Blocks until the worker thread has finished
   * executing the currently executed actor (if any), then removes the
   * indicated actor, calls its destructor, and releases any memory
   * allocated for it.
   */
  void destroyActorInstance(const char *actor_name);

  /**
   * Called by parser. Create a FIFO between two local ports. If the
   * worker thread is idle, it will be woken up.
   */
  void createLocalConnection(const char *src_actor,
                             const char *src_port,
                             const char *dst_actor,
                             const char *dst_port);

  /**
   * Called by parser. Create a FIFO between a local output and a
   * remote input. If the worker thread is idle, it will be woken up.
   */
  void createRemoteConnection(const char *src_actor,
                              const char *src_port,
                              const char *remote_host,
                              const char *remote_port);

  /**
   * Called by parser. Creates a socket listener for the indicated
   * output port, and returns the associated network port number.
   */
  int createSocketReceiver(const char *actor_name,
                           const char *port);

  /**
   * Called by parser. Blocks calling thread until all actors are
   * idle.
   */
  void waitForIdle(void);

  /**
   * Called by socket threads for receivers/senders. Reactivate
   * network execution, in case it has gone idle.
   */
  void wakeUpNetwork(void);

  /**
   * Called by parser. Lists names of all actors to the given stream.
   */
  void listActors(FILE *out);
  
  /**
   * Called by parser. Displays an actor and its FIFO states to the
   * given stream.
   */
  void showActor(FILE *out, const char *name);
  
  /** Thread-safe generation of unique hash id. */
  uint32_t getUniqueHashid(void);
  
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
  void lockedBusyNetworkToggle(uint32_t hashid);

#ifdef __cplusplus
}
#endif

#endif
