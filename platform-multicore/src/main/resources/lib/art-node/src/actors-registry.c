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

#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>

#include "actors-registry.h"
#include "dllist.h"

/*
 * Simple array of loaded classes: linear time for lookup.  However,
 * lookup is only made during network construction time, not runtime,
 * so this is not expected to be a problem until we deploy a _really_
 * large network onto a single node.
 */
#define MAX_CLASSES (10000)
static const ActorClass **classes;
static unsigned int nbr_classes;

/* ------------------------------------------------------------------------- */

void registryInit(void)
{
  classes = malloc(MAX_CLASSES * sizeof(classes[0]));
  nbr_classes = 0;
}

/* ------------------------------------------------------------------------- */

const ActorClass * registryLoadClass(const char *filename)
{
  char filenameext[256];
  void *lib_handle;
  const ActorClass *klass;

  sprintf(filenameext,"%s.%s",filename,CALVIN_LIBEXT);
  
  lib_handle = dlopen(filenameext, RTLD_LAZY);

  if (! lib_handle) {
    fail("failed loading %s: %s\n", filename, dlerror());
  }
  klass = dlsym(lib_handle, "klass");
  if (! klass) {
    fail("failed accessing class in %s: %s\n",
         filename, dlerror());
  }

  /*
   * Changes in major version require re-compilation of actors.
   * Changes in minor version are backwards compatible within the
   * current major version.
   *
   * Example: a runtime of version 7.3 will accept actors compiled for
   * versions 7.3 and 7.2, but not 6.3, 7.4 or 8.2.
   */
  if (klass->majorVersion != ACTORS_RTS_MAJOR
      || klass->minorVersion > ACTORS_RTS_MINOR)
  {
    fail("incompatible object '%s' "
         "(expects runtime %u.%u, current is %u.%u).\n",
         filename, /* cannot access '->name' -- struct layout is unknown! */
         klass->majorVersion, klass->minorVersion,
         ACTORS_RTS_MAJOR, ACTORS_RTS_MINOR);
  }

  return klass;
}

/* ------------------------------------------------------------------------- */

void registryAddClass(const ActorClass *klass)
{
  classes[nbr_classes++] = klass;
}

/* ------------------------------------------------------------------------- */

const ActorClass * registryGetClass(const char *name)
{
  unsigned int i;
  for (i = 0; i < nbr_classes; i++) {
    if(strcmp(classes[i]->name, name) == 0) {
      return classes[i];
    }
  }

  fail("failed looking up actor class '%s'\n", name);
  return NULL; /* won't be executed, but keeps compiler happy */
}

/* ------------------------------------------------------------------------- */

void registryList(FILE *out)
{
  unsigned int i;
  for (i = 0; i < nbr_classes; i++) {
    fprintf(out, " %s", classes[i]->name);
  }
}

/* ------------------------------------------------------------------------- */

