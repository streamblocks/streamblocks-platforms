/*
 * Actor art_Source_32bit_bin (ActorClass_art_Source_bin)
 * Generated on Wed Jun 03 14:27:41 CEST 2009 from sysactors/art_Source_32bit_bin.xlim
 * by xlim2c version 0.6 (June 3, 2009)
 */

#include "actors-rts.h"
#include <stdio.h>
#include <errno.h>

#define BUF_SIZE 1024
#define MAX_REPETITIONS 20


typedef struct {
    AbstractActorInstance base;
    char *filename;
    FILE *file;
    unsigned char buf[BUF_SIZE];
    int pos;
    int size;
    int max_loops;
    int loops;
} ActorInstance_art_Source;

static const int exitcode_block_Out_1[] = {EXITCODE_BLOCK(1), 0, 1};

ART_ACTION_CONTEXT(0, 1);

ART_ACTION_SCHEDULER(art_Source_bin_action_scheduler) {
    const int *result = EXIT_CODE_YIELD;
    ActorInstance_art_Source *thisActor = (ActorInstance_art_Source*)pBase;
    int n;
    ART_ACTION_SCHEDULER_ENTER(0, 1);

    n = pinAvailOut_int32_t(ART_OUTPUT(0));
    ART_ACTION_SCHEDULER_LOOP {
        ART_ACTION_SCHEDULER_LOOP_TOP;
        if (thisActor->pos >= thisActor->size) {
            thisActor->size = fread(thisActor->buf,
                                    sizeof(thisActor->buf[0]),
                                    BUF_SIZE,
                                    thisActor->file);
            thisActor->pos = 0;
            if (thisActor->size == 0) {
                result = EXITCODE_TERMINATE;
                goto out;
            }
        }
        // Here we are sure that we have data in buffer
        if (n > 0) {
            n--;
            ART_ACTION_ENTER(action1, 0);
            pinWrite_int32_t(ART_OUTPUT(0), thisActor->buf[thisActor->pos]);
            thisActor->pos++;
            ART_ACTION_EXIT(action1, 0);
        } else {
            result = exitcode_block_Out_1;
            goto out;
        }
        ART_ACTION_SCHEDULER_LOOP_BOTTOM;
    }
    out:
    ART_ACTION_SCHEDULER_EXIT(0, 1);
    return result;

}

static void constructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Source *thisActor = (ActorInstance_art_Source *) pBase;

    if (thisActor->filename == NULL) {
        runtimeError(pBase, "Parameter not set: fileName");
    } else {
        printf("Open %s\n", thisActor->filename);
        thisActor->file = fopen(thisActor->filename, "rb");
        if (thisActor->file == NULL) {
            runtimeError(pBase, "Cannot open file for output: %s: %s",
                         thisActor->filename, strerror(errno));
        }
        thisActor->size = 0;
        thisActor->pos = 0;
        thisActor->loops = 0;
    }
    if(thisActor->max_loops == NULL){
        thisActor->max_loops = 0;
    }
}

static void destructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Source *thisActor = (ActorInstance_art_Source *) pBase;
    if (thisActor->file != NULL && thisActor->file != stdin) {
        fclose(thisActor->file);
    }
}

static void setParam(AbstractActorInstance *pBase, const char *paramName,
                     const char *value) {
    ActorInstance_art_Source *thisActor = (ActorInstance_art_Source *) pBase;
    if (strcmp(paramName, "fileName") == 0) {
        thisActor->filename = strdup(value);
    } else if (strcmp(paramName, "loops") == 0) {
        thisActor->max_loops = atoi(value);
    } else {
        runtimeError(pBase, "No such parameter: %s", paramName);
    }
}

static const PortDescription outputPortDescriptions[] = {{0, "Out",
                                                                 sizeof(int32_t)}};

static const int portRate_0[] = {0};

static const int portRate_1[] = {8};

// -- State Variables Description
static const StateVariableDescription stateVariableDescription[] = {};

// -- Uses by Transition
static const int uses_in_actionAtLine_7[] = {};

// -- Defines by Transition
static const int defines_in_actionAtLine_7[] = {};

static const ActionDescription actionDescriptions[] = {{"action", "action", 0,
                                                               portRate_1, uses_in_actionAtLine_7, defines_in_actionAtLine_7}};

// -- Condition Description
static const ConditionDescription conditionDescription[] = {};

ActorClass ActorClass_art_Source_32bit_bin = INIT_ActorClass(
        "ART.art_Source_32bit_bin",
        ActorInstance_art_Source,
        constructor,
        setParam,
        art_Source_bin_action_scheduler,
        destructor,
        0, 0,
        1, outputPortDescriptions,
        1, actionDescriptions,
        0, conditionDescription,
        0, stateVariableDescription
);
