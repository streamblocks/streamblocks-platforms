/*
 * Actor art_Sink_bin (ActorClass_art_Sink_bin)
 * Generated on Wed Jun 03 11:04:36 CEST 2009 from sysactors/art_Sink_bin.xlim
 * by xlim2c version 0.6 (June 3, 2009)
 */

#include "actors-rts.h"
#include <stdio.h>
#include <errno.h>

typedef struct {
    AbstractActorInstance base;
    char *filename;
    FILE *file;
} ActorInstance_art_Sink;

static const int exitcode_block_In_1[] = {
        EXITCODE_BLOCK(1), 0, 1
};

ART_ACTION_CONTEXT(1, 0);

ART_ACTION_SCHEDULER(art_Sink_bin_action_scheduler) {
    ActorInstance_art_Sink *thisActor = (ActorInstance_art_Sink *) pBase;
    const int *result = EXIT_CODE_YIELD;
    int numTokens;

    ART_ACTION_SCHEDULER_ENTER(1, 0);
    numTokens = pinAvailIn_uint8_t(ART_INPUT(0));
    ART_ACTION_SCHEDULER_LOOP {
        ART_ACTION_SCHEDULER_LOOP_TOP;

        if (numTokens > 0) {
            numTokens--;
            ART_ACTION_ENTER(action1, 0);
            uint8_t token = pinRead_uint8_t(ART_INPUT(0));
            fputc(token, thisActor->file);
            ART_ACTION_EXIT(action1, 0);
        } else {
            result = exitcode_block_In_1;
            goto out;
        }
        ART_ACTION_SCHEDULER_LOOP_BOTTOM;
    }
    out:
    ART_ACTION_SCHEDULER_EXIT(1, 0)
    return result;
}

static void constructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Sink *thisActor = (ActorInstance_art_Sink *) pBase;

    if (thisActor->filename == NULL) {
        runtimeError(pBase, "Parameter not set: fileName");
    } else {
        thisActor->file = fopen(thisActor->filename, "wb");
        if (thisActor->file == NULL) {
            runtimeError(pBase, "Cannot open file for output: %s: %s",
                         thisActor->filename, strerror(errno));
        }
    }
}

static void destructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Sink *thisActor = (ActorInstance_art_Sink *) pBase;
    if (thisActor->file != NULL && thisActor->file != stdout) {
        fclose(thisActor->file);
    }
}

static void setParam(AbstractActorInstance *pBase,
                     const char *paramName,
                     const char *value) {
    ActorInstance_art_Sink *thisActor = (ActorInstance_art_Sink *) pBase;
    if (strcmp(paramName, "fileName") == 0) {
        thisActor->filename = strdup(value);
    } else {
        runtimeError(pBase, "No such parameter: %s", paramName);
    }
}

static const PortDescription inputPortDescriptions[] = {
        {0, "In", sizeof(uint8_t)}
};


static const int portRate_1[] = {
        1
};

// -- State Variables Description
static const StateVariableDescription stateVariableDescription[] = {};

// -- Uses by Transition
static const int uses_in_action1[] = {};

// -- Defines by Transition
static const int defines_in_action1[] = {};

static const ActionDescription actionDescriptions[] = {
        {"action1", "action1", portRate_1, 0, uses_in_action1, defines_in_action1}
};

// -- Condition Description
static const ConditionDescription conditionDescription[] = {};

ActorClass ActorClass_art_Sink_bin = INIT_ActorClass(
        "ART.art_Sink_bin",
        ActorInstance_art_Sink,
        constructor,
        setParam,
        art_Sink_bin_action_scheduler,
        destructor,
        1, inputPortDescriptions,
        0, 0,
        1, actionDescriptions,
        0, conditionDescription,
        0, stateVariableDescription
);
