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

#ifndef TELEPORT_INCLUSION_GUARD
#define TELEPORT_INCLUSION_GUARD

#include "actors-rts.h"

const ActorClass *getReceiverClass(int tokenSize, tokenFn* functions);
const ActorClass *getSenderClass(int tokenSize, tokenFn* functions);

/**
 * Get the port a receiver is listening to. The indicated instance
 * must be an instance of a class returned by getReceiverClass.
 */
int getReceiverPort(AbstractActorInstance *pBase);

/**
 * Assign a remote peer to a sender.  The indicated instance
 * must be an instance of a class returned by getSenderClass.
 */
void setSenderRemoteAddress(AbstractActorInstance *pBase,
                            const char *host,
                            int port);

/**
 * Inspect an actor instance. Returns non-zero if it is an instance of
 * a socket receiver class, zero otherwise.
 */
int instanceIsReceiver(AbstractActorInstance *pBase);

/**
 * Inspect an actor instance. Returns non-zero if it is an instance of
 * a socket sender class, zero otherwise.
 */
int instanceIsSender(AbstractActorInstance *pBase);

/* FIXME: need some way of purging/gc'ing unused classes */

#endif /* TELEPORT_INCLUSION_GUARD */
