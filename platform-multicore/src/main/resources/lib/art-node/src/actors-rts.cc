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

#include <libgen.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "actors-debug.h"
#include "actors-registry.h"
#include "actors-network.h"
#include "actors-parser.h"
#include "actors-rts.h"



/* ------------------------------------------------------------------------- */

extern const ActorClass ActorClass_art_Sink_bin;
extern const ActorClass ActorClass_art_Sink_real;
extern const ActorClass ActorClass_art_Sink_txt;

extern const ActorClass ActorClass_art_Source_bin;
extern const ActorClass ActorClass_art_Source_real;
extern const ActorClass ActorClass_art_Source_txt;
extern const ActorClass ActorClass_art_Source_byte;

extern const ActorClass ActorClass_art_Display_yuv;
extern const ActorClass ActorClass_art_Display_yuv_width_height;


// ============================================================================

/*
 * Exit codes (returned from action schedulers)
 */

const int exit_code_terminate[] = {-1};
const int exit_code_yield[] = {-2};

/* ------------------------------------------------------------------------- */

static void usage(const char *bname)
{
  fail("usage: %s [-i] [-v] [script1.cs script2.cs ... scriptN.cs]\n"
       "  Named scripts are executed in sequence. Other options:\n"
       "  -i:   start interactive interpreter\n"
       "  -s N: start command server at port N\n",
       bname);
}

// ============================================================================

void fail(const char *fmt, ...)
{
  va_list args;

  va_start(args, fmt);
  fprintf(stderr, "failed: ");
  vfprintf(stderr, fmt, args);
  va_end(args);

  fprintf(stderr, "\n");

  exit(1);
}

/* ------------------------------------------------------------------------- */

void warn(const char *fmt, ...)
{
  va_list args;

  va_start(args, fmt);
  fprintf(stderr, "warning: ");
  vfprintf(stderr, fmt, args);
  va_end(args);

  fprintf(stderr, "\n");
}

// ============================================================================

/*
 * Error reporting
 */

void runtimeError(AbstractActorInstance *pInst, const char *format,...) {
  va_list ap;
  va_start(ap,format);
  vfprintf(stderr,format,ap);
  fprintf(stderr,"\n");
  va_end(ap);
  exit(1);
}

// ============================================================================

int rangeError(int x, int y, const char *filename, int line) {
  runtimeError(NULL, "Range check error: %d %d %s(%d)\n",
               x, y, filename, line);
  return 0;
}

// ============================================================================

int main(int argc, char **argv)
{
  int i;
  int keep_other_threads = 0; /* Allow other pthreads to stay on exit? */
  int port = 0;

  /* find our port number if we have any */
  for (i = 1; i < argc; i++) {
    if (strcmp(argv[i], "-s") == 0) {
      if ((i+1) < argc) {
        port = atoi(argv[i+1]);
        break;
      }
    }
  }

  createDebugFile(port);

  registryInit();
  initActorNetwork();

  /* Built-in system actors */

  registryAddClass(&ActorClass_art_Sink_bin);
  registryAddClass(&ActorClass_art_Sink_real);
  registryAddClass(&ActorClass_art_Sink_txt);

  registryAddClass(&ActorClass_art_Source_bin);
  registryAddClass(&ActorClass_art_Source_real);
  registryAddClass(&ActorClass_art_Source_txt);
  registryAddClass(&ActorClass_art_Source_byte);

  registryAddClass(&ActorClass_art_Display_yuv);
  registryAddClass(&ActorClass_art_Display_yuv_width_height);


  /* execute each named file in non-interactive mode */
  for (i = 1; i < argc; i++) {
    if (argv[i][0] == '-') {
      if (strcmp(argv[i], "-i") == 0) {
        parseInteractively();
      } else if (strcmp(argv[i], "-s") == 0) {
        i++;
        if (i >= argc) {
          usage(basename(argv[0]));
        }
        spawnServer(atoi(argv[i]));
        keep_other_threads = 1;
      } else {
        usage(basename(argv[0]));
      }
    } else {
      parseFile(argv[i]);
    }
  }

  if (keep_other_threads) {
    /* Just exit the main thread, rather than the entire process */
    pthread_exit(NULL);
  }
  closeDebugFile();
  return 0;
}
