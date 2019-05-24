/*
 * Copyright (c) Ericsson AB, 2014
 * Author: Harald Gustafsson (harald.gustafsson@ericsson.com)
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <errno.h>
#include <stdarg.h>

/** The (debug) log file */
#define DEBUG_FILE_PATH "/tmp/calvin"
static FILE* debug_file;
/* ------------------------------------------------------------------------- */

/* Open debug/log file */
void createDebugFile(int port) {
  char path[128];
  int err = mkdir(DEBUG_FILE_PATH,0777);
  if(err<0) {
    err = errno;
    if(err!=17)
      printf("Can't create calvin log file directory %i %s\n",err,strerror(err));
  }
  sprintf(path,"%s/calvin%i.log",DEBUG_FILE_PATH,port);
  debug_file = fopen(path,"a");
  fprintf(debug_file,"------------------------------------- START SERVER -----------------------------\n");
  fflush(debug_file);
}

void closeDebugFile() {
  if(debug_file!=NULL) {
    fclose(debug_file);
  }
}

void printDebug(const char* format, ...) {
  va_list args;
  if(debug_file!=NULL) {
    va_start(args, format);
    vfprintf(debug_file,format,args);
    va_end(args);
    fflush(debug_file);
  }
}

void printDebugType(const char* type, const char* format, ...) {
  va_list args;
  if(debug_file!=NULL) {
    va_start(args, format);
    fprintf(debug_file,"[%s] ",type);
    vfprintf(debug_file,format,args);
    va_end(args);
    fflush(debug_file);
  }
}
