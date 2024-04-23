/*
 * Actor art_Display2_yuv (ActorClass_art_Display2_yuv)
 * Generated on Tue Nov 02 14:19:15 CET 2010 from Display_yuv.xlim
 * by xlim2c version 0.6 (June 3, 2009)
 */

#include <stdlib.h>
#include <time.h>
#include <sys/timeb.h>

#include "actors-rts.h"
#include "display.h"

#define IN0_In ART_INPUT(0)
#define IN1_WIDTH ART_INPUT(1)
#define IN2_HEIGHT ART_INPUT(2)
#define OUT0_Out ART_OUTPUT(0)

typedef struct {
    AbstractActorInstance base;
    const char *title;
    int32_t maxWidth;
    int32_t maxHeight;
    yuv_sample_t macroBlock[MB_SIZE];
    int32_t currWidth;
    int32_t currHeight;
    int32_t lastWidth;
    int32_t lastHeight;
    int32_t panX;
    int32_t panY;
    int32_t mbx;
    int32_t mby;
    int32_t count;
    int32_t startTime;
    int32_t partialFrames;
    int32_t displayedFrames;
    FrameBuffer frameBuffer;
} ActorInstance_art_Display_yuv_width_height;


ART_ACTION_CONTEXT(3, 1)

ART_ACTION_SCHEDULER(art_Display_yuv_width_height_action_scheduler);

static void art_Display_yuv_width_height_constructor(AbstractActorInstance *);

void art_Display_yuv_width_height_destructor(AbstractActorInstance *pBase);

void art_Display_yuv_width_height_setParam(AbstractActorInstance *pBase,
                                           const char *paramName,
                                           const char *value);

static const PortDescription inputPortDescriptions[] = {
        {0, "In",     sizeof(int32_t)},
        {0, "WIDTH",  sizeof(int32_t)},
        {0, "HEIGHT", sizeof(int32_t)}
};

static const PortDescription outputPortDescriptions[] = {
        {0, "Out", sizeof(int32_t)}
};

static const int portRate_0_1_1[] = {
        0, 1, 1
};

static const int portRate_0[] = {
        0
};

static const int portRate_64_0_0[] = {
        64, 0, 0
};

// -- State Variables Description
static const StateVariableDescription stateVariableDescription[] = {};

// -- Uses by Transition
static const int uses_in_startFrame[] = {};
static const int uses_in_read[] = {};

// -- Defines by Transition
static const int defines_in_startFrame[] = {};
static const int defines_in_read[] = {};

// -- Action Description
static const ActionDescription actionDescriptions[] = {
        {"startFrame", "startFrame", portRate_0_1_1,  portRate_0, uses_in_startFrame, defines_in_startFrame},
        {"read", "read", portRate_64_0_0, portRate_0, uses_in_read, defines_in_read}
};


// -- Condition Description
static const ConditionDescription conditionDescription[] = {};

ActorClass ActorClass_art_Display_yuv_width_height = INIT_ActorClass(
        "ART.art_Display_yuv_width_height",
        ActorInstance_art_Display_yuv_width_height,
        art_Display_yuv_width_height_constructor,
        art_Display_yuv_width_height_setParam,
        art_Display_yuv_width_height_action_scheduler,
        art_Display_yuv_width_height_destructor,
        3, inputPortDescriptions,
        0, 0, // if Out-port: 1, outputPortDescriptions,
        2, actionDescriptions,
        0, conditionDescription,
        0, stateVariableDescription
);

static const int exitcode_block_WIDTH_1[] = {
        EXITCODE_BLOCK(1), 1, 1
};

static const int exitcode_block_HEIGHT_1[] = {
        EXITCODE_BLOCK(1), 2, 1
};

static const int exitcode_block_In_64[] = {
        EXITCODE_BLOCK(1), 0, 64
};

static void clearRect(int x0, int y0,
                      int x1, int y1,
                      FrameBuffer *frameBuffer) {
    static yuv_sample_t blackMacroBlock[MB_SIZE];
    int x, y;

    for (y = y0; y < y1; y += 16)
        for (x = x0; x < x1; x += 16)
            frameBuffer->display_yuv(x, y, blackMacroBlock, frameBuffer);
}

ART_ACTION_SCHEDULER(art_Display_yuv_width_height_action_scheduler) {
    const int *exitCode = EXIT_CODE_YIELD;
    ActorInstance_art_Display_yuv_width_height *thisActor =
            (ActorInstance_art_Display_yuv_width_height *) pBase;
    int32_t count = thisActor->count;

    ART_ACTION_SCHEDULER_ENTER(3, 0);

    ART_ACTION_SCHEDULER_LOOP {
        ART_ACTION_SCHEDULER_LOOP_TOP;

        if (thisActor->mby >= thisActor->currHeight) {
            if (pinAvailIn_int16_t(IN1_WIDTH) >= 1)
                if (pinAvailIn_int16_t(IN2_HEIGHT) >= 1) {
                    ART_ACTION_ENTER(startFrame, 0);
                    int32_t lastWidth = thisActor->currWidth;
                    int32_t lastHeight = thisActor->currHeight;

                    thisActor->currWidth = 16 * pinRead_int16_t(IN1_WIDTH);
                    thisActor->currHeight = 16 * pinRead_int16_t(IN2_HEIGHT);
                    // Change of VOP dimension?
                    if (thisActor->currWidth != lastWidth
                        || thisActor->currHeight != lastHeight) {
                        uint32_t panX = (thisActor->maxWidth - thisActor->currWidth) / 2;
                        uint32_t panY = (thisActor->maxHeight - thisActor->currHeight) / 2;
                        uint32_t lastPanX = (thisActor->maxWidth - lastWidth) / 2;
                        uint32_t lastPanY = (thisActor->maxHeight - lastHeight) / 2;

                        // Need to clear "mattes" around frame?
                        if (thisActor->currHeight < lastHeight) {
                            clearRect(lastPanX, lastPanY,
                                      lastPanX + lastWidth, panY,
                                      &thisActor->frameBuffer);
                            clearRect(lastPanX, panY + thisActor->currHeight,
                                      lastPanX + lastWidth, lastPanY + lastHeight,
                                      &thisActor->frameBuffer);
                        }
                        if (thisActor->currWidth < lastWidth) {
                            clearRect(lastPanX, panY,
                                      panX, panY + thisActor->currHeight,
                                      &thisActor->frameBuffer);
                            clearRect(panX + thisActor->currWidth, panY,
                                      lastPanX + lastWidth, panY + thisActor->currHeight,
                                      &thisActor->frameBuffer);
                        }

                        thisActor->panX = panX;
                        thisActor->panY = panY;
                        thisActor->lastWidth = lastWidth;
                        thisActor->lastHeight = lastHeight;
                    }
                    thisActor->mby = 0;
                    thisActor->partialFrames++;
                    ART_ACTION_EXIT(startFrame, 0);
                } else {
                    exitCode = exitcode_block_HEIGHT_1;
                    goto action_scheduler_exit;
                }
            else {
                exitCode = exitcode_block_WIDTH_1;
                goto action_scheduler_exit;
            }
        } else {

            do {
                int32_t avail = pinAvailIn_int32_t(IN0_In);
                yuv_sample_t *macroBlock = thisActor->macroBlock;
                yuv_sample_t *ptr = macroBlock + count;
                yuv_sample_t *end = macroBlock;

                // Update count to a multiple of 64
                // not larger than 384, diff mustn't be larger than avail
                count = (count + avail >= 384) ? 384 : ((count + avail) & 0xfc0);
                end = macroBlock + count;

                while (ptr < end) {
                    /*** read action ***/

                    ART_ACTION_ENTER(read, 1);
                    pinReadRepeat_int32_t(IN0_In, ptr, 64);
                    ART_ACTION_EXIT(read, 1);
                    ptr += 64;
                }

                if (count < 384) {
                    exitCode = exitcode_block_In_64;
                    goto action_scheduler_exit;
                }

                /*** done.comp action ***/
                count = 0;

                /*** done.mb action ***/
                if (thisActor->mbx < thisActor->maxWidth
                    && thisActor->mby < thisActor->maxHeight) {
                    thisActor->frameBuffer.display_yuv(thisActor->mbx + thisActor->panX,
                                                       thisActor->mby + thisActor->panY,
                                                       thisActor->macroBlock,
                                                       &thisActor->frameBuffer);
                }
                thisActor->mbx += 16;
            } while (thisActor->mbx < thisActor->currWidth);

            thisActor->mbx = 0;
            thisActor->mby += 16;
            if (thisActor->mby >= thisActor->currHeight) {
                thisActor->frameBuffer.frame_done(&thisActor->frameBuffer);
                thisActor->displayedFrames++;
            }
        }
        ART_ACTION_SCHEDULER_LOOP_BOTTOM;
    }
    action_scheduler_exit:
    ART_ACTION_SCHEDULER_EXIT(3, 0);

    thisActor->count = count;
    return exitCode;
}

static void art_Display_yuv_width_height_constructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Display_yuv_width_height *thisActor =
            (ActorInstance_art_Display_yuv_width_height *) pBase;

    struct timeb tb;
    int maxWidth = thisActor->maxWidth;
    int maxHeight = thisActor->maxHeight;

    if (maxWidth == 0 || maxHeight == 0) {
        runtimeError(pBase, "Width and/or height parameter not set\n");
    }
    ftime(&tb);
    memset(thisActor->macroBlock, 0, MB_SIZE);
    thisActor->currWidth = 0;
    thisActor->currHeight = 0;
    thisActor->lastWidth = 0;
    thisActor->lastHeight = 0;
    thisActor->panX = 0;
    thisActor->panY = 0;
    thisActor->mbx = 0;
    thisActor->mby = 0;
    thisActor->count = 0;
    thisActor->partialFrames = 0;
    thisActor->displayedFrames = 0;
    thisActor->startTime = tb.time * 1000 + tb.millitm;
    if (allocate_display(maxWidth,
                         maxHeight,
                         thisActor->title,
                         &thisActor->frameBuffer)) {
        exit(1);
    }
}

void art_Display_yuv_width_height_destructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Display_yuv_width_height *thisActor =
            (ActorInstance_art_Display_yuv_width_height *) pBase;

    struct timeb tb;
    int totTime;
    ftime(&tb);
    totTime = tb.time * 1000 + tb.millitm - thisActor->startTime;
    thisActor->frameBuffer.free_display(&thisActor->frameBuffer);
    if (thisActor->partialFrames != thisActor->displayedFrames) {
        printf("Number of frames: %d (+1 partial frame)\n",
               thisActor->displayedFrames);
        printf("%d total frames in %f seconds (%f fps)\n", thisActor->displayedFrames,
               (double) totTime / 1000,
               (double) (thisActor->displayedFrames) * 1000 / totTime);
    } else {
        printf("Number of frames: %d\n", thisActor->displayedFrames);
        printf("%d total frames in %f seconds (%f fps)\n", thisActor->displayedFrames,
               (double) totTime / 1000,
               (double) (thisActor->displayedFrames) * 1000 / totTime);
    }
}


void art_Display_yuv_width_height_setParam(AbstractActorInstance *pBase,
                                           const char *paramName,
                                           const char *value) {
    ActorInstance_art_Display_yuv_width_height *thisActor =
            (ActorInstance_art_Display_yuv_width_height *) pBase;

    if (strcmp(paramName, "title") == 0)
        thisActor->title = value;
    else if (strcmp(paramName, "height") == 0) {
        thisActor->maxHeight = atoi(value);
    } else if (strcmp(paramName, "width") == 0) {
        thisActor->maxWidth = atoi(value);
    }
}
