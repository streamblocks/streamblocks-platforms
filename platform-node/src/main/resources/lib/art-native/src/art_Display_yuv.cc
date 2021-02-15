/* 
 * Copyright (c) Ericsson AB, 2009
 * Author: Charles Chen Xu (charles.chen.xu@ericsson.com)
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
#include <time.h>
#include <sys/timeb.h>

#include "actors-rts.h"
#include "display.h"

ART_ACTION_CONTEXT(1, 1);

typedef struct {
    AbstractActorInstance base;
    int height;
    int width;
    const char *title;
    yuv_sample_t macroBlock[MB_SIZE];
    int mbx, mby;
    int count;
    int startTime;
    int totFrames;
    FrameBuffer frameBuffer;
} ActorInstance_art_Display_yuv;

#define IN0_In               ART_INPUT(0)

static const int exitcode_block_In_64[] = {EXITCODE_BLOCK(1), 0, 64};

ART_ACTION_SCHEDULER(art_Display_yuv_action_scheduler) {
    const int *result = EXIT_CODE_YIELD;
    ActorInstance_art_Display_yuv *thisActor =
            (ActorInstance_art_Display_yuv *) pBase;
    int32_t count = thisActor->count;
    ART_ACTION_SCHEDULER_ENTER(1, 0);

    ART_ACTION_SCHEDULER_LOOP {
        ART_ACTION_SCHEDULER_LOOP_TOP;
        do {
            while (count < 384) {
                /*** read action ***/
                int32_t avail = pinAvailIn_int32_t(IN0_In);
                yuv_sample_t *ptr;

                if (avail < 64) {
                    // Save back state
                    thisActor->count = count;
                    result = exitcode_block_In_64;
                    goto out;
                }

                ART_ACTION_ENTER(read, 0);
                ptr = thisActor->macroBlock + count;
                pinReadRepeat_int32_t(IN0_In, ptr, 64);
                ART_ACTION_EXIT(read, 0);
                count += 64;
            }

            /*** done.comp action ***/
            count = 0;

            /*** done.mb action ***/
            thisActor->frameBuffer.display_yuv(thisActor->mbx, thisActor->mby,
                                               thisActor->macroBlock, &thisActor->frameBuffer);
            thisActor->mbx += 16;
        } while (thisActor->mbx < thisActor->width);

        thisActor->mbx = 0;
        thisActor->mby += 16;
        if (thisActor->mby >= thisActor->height) {
            thisActor->totFrames++;
            thisActor->mby = 0;
            thisActor->frameBuffer.frame_done(&thisActor->frameBuffer);
        } ART_ACTION_SCHEDULER_LOOP_BOTTOM;
    }
    out:
    ART_ACTION_SCHEDULER_EXIT(1, 0);
    return result;
}

static void art_Display_yuv_constructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Display_yuv *thisActor =
            (ActorInstance_art_Display_yuv *) pBase;
    struct timeb tb;
    int width = thisActor->width;
    int height = thisActor->height;

    if (width == 0 || height == 0) {
        runtimeError(pBase, "Width and/or height parameter not set\n");
    }
    ftime(&tb);
    memset(thisActor->macroBlock, 0, MB_SIZE);
    thisActor->mbx = 0;
    thisActor->mby = 0;
    thisActor->count = 0;

    thisActor->startTime = tb.time * 1000 + tb.millitm;
    thisActor->totFrames = 0;
    if (allocate_display(thisActor->width, thisActor->height, thisActor->title,
                         &thisActor->frameBuffer)) {
        exit(1);
    }
}

void art_Display_yuv_destructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Display_yuv *thisActor =
            (ActorInstance_art_Display_yuv *) pBase;
    struct timeb tb;
    int totTime;
    ftime(&tb);
    totTime = tb.time * 1000 + tb.millitm - thisActor->startTime;
    printf("%d total frames in %f seconds (%f fps)\n", thisActor->totFrames,
           (double) totTime / 1000,
           (double) (thisActor->totFrames) * 1000 / totTime);
    thisActor->frameBuffer.free_display(&thisActor->frameBuffer);
}

void art_Display_yuv_setParam(AbstractActorInstance *pBase,
                              const char *paramName, const char *value) {
    ActorInstance_art_Display_yuv *thisActor =
            (ActorInstance_art_Display_yuv *) pBase;

    if (strcmp(paramName, "title") == 0)
        thisActor->title = value;
    else if (strcmp(paramName, "height") == 0) {
        thisActor->height = atoi(value);
    } else if (strcmp(paramName, "width") == 0) {
        thisActor->width = atoi(value);
    }
}

static const PortDescription inputPortDescriptions[] = {{0, "In",
                                                                sizeof(int32_t)}};

static const int portRate_64[] = {64};

static const int portRate_0[] = {0};

// TBD: Only used in RM-version (remove?)
static const int portRate_1[] = {1};

// -- State Variables Description
static const StateVariableDescription stateVariableDescription[] = {};

// -- Uses by Transition
static const int uses_in_read[] = {};
static const int uses_in_done_comp[] = {};
static const int uses_in_done_mb[] = {};


// -- Defines by Transition
static const int defines_in_read[] = {};
static const int defines_in_done_comp[] = {};
static const int defines_in_done_mb[] = {};


static const ActionDescription actionDescriptions[] = {
        {"read", "read", portRate_64, 0, uses_in_read, defines_in_read}};

// -- Condition Description
static const ConditionDescription conditionDescription[] = {};

ActorClass ActorClass_art_Display_yuv = INIT_ActorClass(
        "ART.art_Display_yuv",
        ActorInstance_art_Display_yuv,
        art_Display_yuv_constructor,
        art_Display_yuv_setParam,
        art_Display_yuv_action_scheduler,
        art_Display_yuv_destructor,
        1, inputPortDescriptions,
        0, 0,
        1, actionDescriptions,
        0, conditionDescription,
        0, stateVariableDescription
);
