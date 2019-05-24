/*
 * Copyright (c) Ericsson AB, 2009-2013, EPFL VLSC 2019
 * Author: Endri Bezati (endri.bezati@epfl.ch)
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

#ifndef REGISTRY_INCLUSION_GUARD
#define REGISTRY_INCLUSION_GUARD

#include "actors-rts.h"

#ifdef _WIN64
#define CALVIN_LIBEXT "dll"
#elif _WIN32
#define CALVIN_LIBEXT "dll"
#elif __APPLE__
#define CALVIN_LIBEXT "bundle"
#elif __linux
#define CALVIN_LIBEXT "so"
#elif __unix // all unices not caught above
#define CALVIN_LIBEXT "so"
#elif __posix
#define CALVIN_LIBEXT "so"
#endif

/**
 * Initialize registry: called once on startup.
 */
void registryInit(void);

/**
 * Load an actor class (from a shared object)
 */
const ActorClass * registryLoadClass(const char *filename);

/**
 * Register an actor class
 */
void registryAddClass(const ActorClass *klass);

/**
 * Guaranteed to return non-NULL. If the class is not found,
 * fail() will be called.
 */
const ActorClass * registryGetClass(const char *name);

/**
 * List classes, one per line, to a stream. Called by command parser.
 */
void registryList(FILE *out);

#endif /* REGISTRY_INCLUSION_GUARD */
