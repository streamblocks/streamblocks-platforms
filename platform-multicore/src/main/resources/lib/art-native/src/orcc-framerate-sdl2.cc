/*
 * Copyright (c) 2009, IETR/INSA of Rennes
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   * Neither the name of the IETR/INSA of Rennes nor the names of its
 *     contributors may be used to endorse or promote products derived from this
 *     software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#include <SDL.h>

static unsigned int startTime;
static unsigned int mappingTime;
static unsigned int relativeStartTime;
static int lastNumPic;
static int numPicturesDecoded;
static int numAlreadyDecoded;
static int partialNumPicturesDecoded;

static void print_fps_avg() {
    unsigned int endTime = SDL_GetTicks();

    float decodingTime = (endTime - startTime) / 1000.0f;
    float framerate = numPicturesDecoded / decodingTime;

    printf("%i images in %f seconds: %f FPS\n",
                     numPicturesDecoded, decodingTime, framerate);
}

static void print_fps_mapping() {
    unsigned int endTime = SDL_GetTicks();

    int numPicturesDecodedMapping = numPicturesDecoded - numAlreadyDecoded;
    float decodingTime = (endTime - mappingTime) / 1000.0f;
    float framerate = numPicturesDecodedMapping / decodingTime;

    printf( "PostMapping : %i images in %f seconds: %f FPS\n",
                     numPicturesDecodedMapping, decodingTime, framerate);
}

void fpsPrintInit() {
    startTime = SDL_GetTicks();
    numPicturesDecoded = 0;
    partialNumPicturesDecoded = 0;
    lastNumPic = 0;
    atexit(print_fps_avg);
    relativeStartTime = startTime;
}

void fpsPrintInit_mapping() {
    mappingTime = SDL_GetTicks();
    numAlreadyDecoded = numPicturesDecoded;
    atexit(print_fps_mapping);
}


void fpsPrintNewPicDecoded(void) {
    unsigned int endTime;
    float relativeTime;

    numPicturesDecoded++;
    partialNumPicturesDecoded++;
    endTime = SDL_GetTicks();

    relativeTime = (endTime - relativeStartTime) / 1000.0f;

    if(relativeTime >= 5) {
        float framerate = (numPicturesDecoded - lastNumPic) / relativeTime;
        printf("%f images/sec\n", framerate);

        relativeStartTime = endTime;
        lastNumPic = numPicturesDecoded;
    }
}

int get_partialNumPicturesDecoded() {
    return partialNumPicturesDecoded;
}

void reset_partialNumPicturesDecoded() {
    partialNumPicturesDecoded = 0;
}
