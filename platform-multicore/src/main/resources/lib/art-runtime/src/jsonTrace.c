/*
 * Copyright (c) EPFL VLSC, 2020
 * Author: Endri Bezati (endri.bezati@epfl.ch)
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

#include "jsonTrace.h"

static pthread_mutex_t jsonMutex;

static unsigned long step = 0;

FILE *infoCreateFile(const char *fileName) {
    FILE *f = fopen(fileName, "w");
    return f;
}

void *infoCloseFile(FILE *f) {
    fprintf(f, "firings=%lu", step);
    fclose(f);
}


gzFile *jsonCreateTrace(const char *fileName) {
    gzFile *f = gzopen(fileName, "w");
    if (f == 0) {
        printf("Cannot write to file: %s", fileName);
        exit(0);
    }
    return f;
}

void jsonCloseTrace(gzFile *f) {
    gzclose(f);
}

void jsonTraceFiring(gzFile *f, AbstractActorInstance *instance, unsigned int actionId, OpCounters *opCounters) {
    const ActorClass *actorClass = instance->actor;
    int numInputs = actorClass->numInputPorts;
    int numOutputs = actorClass->numOutputPorts;
    int numStateVariables = actorClass->numStateVariables;
    const ActionDescription *actions = actorClass->actionDescriptions;
    const StateVariableDescription *stateVariables = actorClass->stateVariableDescription;
    int j;

    unsigned long localstep;
    pthread_mutex_lock(&jsonMutex);
    localstep = step++;
    pthread_mutex_unlock(&jsonMutex);

    gzprintf(f, "{");

    // -- Firing information
    gzprintf(f, "\"actor\" : \"%s\",", strtok(instance->name, "/"));
    gzprintf(f, "\"action\" : \"%s\",", actions[actionId].originalName);
    gzprintf(f, "\"firing\" : \"%lu\",", localstep);
    gzprintf(f, "\"fsm\" : true,");

    // -- Consumes
    int consumes = 0;

    for (j = 0; j < numInputs; ++j) {
        int cns = actions[actionId].consumption[j];
        if (cns) {
            consumes = 1;
            break;
        }
    }

    if (consumes) {
        gzprintf(f, "\"consume\" : [");

        int needsComma = 0;

        for (j = 0; j < numInputs; ++j) {
            int cns = actions[actionId].consumption[j];
            if (cns) {
                if (needsComma) {
                    gzprintf(f, ",");
                }
                gzprintf(f, "{");
                gzprintf(f, "\"port\" : \"%s\",", actorClass->inputPortDescriptions[j].name);
                gzprintf(f, "\"count\" : %d", cns);
                gzprintf(f, "}");
                needsComma = 1;
            }
        }

        gzprintf(f, "],");
    }

    // -- Produces
    int produces = 0;

    for (j = 0; j < numOutputs; ++j) {
        int cns = actions[actionId].production[j];
        if (cns) {
            produces = 1;
            break;
        }
    }

    if (produces) {
        gzprintf(f, "\"produce\" : [");

        int needsComma = 0;

        for (j = 0; j < numOutputs; ++j) {
            int cns = actions[actionId].production[j];
            if (cns) {
                if (needsComma) {
                    gzprintf(f, ",");
                }
                gzprintf(f, "{");
                gzprintf(f, "\"port\" : \"%s\",", actorClass->outputPortDescriptions[j].name);
                gzprintf(f, "\"count\" : %d", cns);
                gzprintf(f, "}");
                needsComma = 1;
            }
        }

        gzprintf(f, "],");
    }



    // -- Read
    int hasReads = 0;
    for (int p = 0; p < numStateVariables; p++) {
        int var = actions[actionId].uses[p];
        if (var) {
            hasReads = 1;
            break;
        }
    }

    if (hasReads) {
        gzprintf(f, "\"read\" : [");

        int needsComma = 0;

        for (j = 0; j < numOutputs; ++j) {
            int cns = actions[actionId].uses[j];
            if (cns) {
                if (needsComma) {
                    gzprintf(f, ",");
                }
                gzprintf(f, "{");
                gzprintf(f, "\"var\" : \"%s\",", stateVariables[j].originalName);
                gzprintf(f, "\"count\" : %d", cns);
                gzprintf(f, "}");
                needsComma = 1;
            }
        }

        gzprintf(f, "],");
    }

    // -- Write
    int hasWrites = 0;
    for (int p = 0; p < numStateVariables; p++) {
        int var = actions[actionId].defines[p];
        if (var) {
            hasWrites = 1;
            break;
        }
    }

    if (hasWrites) {
        gzprintf(f, "\"write\" : [");

        int needsComma = 0;

        for (j = 0; j < numOutputs; ++j) {
            int cns = actions[actionId].defines[j];
            if (cns) {
                if (needsComma) {
                    gzprintf(f, ",");
                }
                gzprintf(f, "{");
                gzprintf(f, "\"var\" : \"%s\",", stateVariables[j].originalName);
                gzprintf(f, "\"count\" : %d", cns);
                gzprintf(f, "}");
                needsComma = 1;
            }
        }

        gzprintf(f, "],");
    }
    // -- Op Counters
    gzprintf(f, "\"op\" : [");

    int needsEnding = 0;

    if (opCounters->prof_BINARY_BIT_AND > 0) {
        gzprintf(f, "{");

        gzprintf(f, "\"name\" : \"BINARY_BIT_AND\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_BIT_AND);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_BIT_OR > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_BIT_OR\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_BIT_OR);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_BIT_XOR > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_BIT_XOR\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_BIT_XOR);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_DIV > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_DIV\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_DIV);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_DIV_INT > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_DIV_INT\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_DIV_INT);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_EQ > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_EQ\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_EQ);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_EXP > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_EXP\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_EXP);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_GT > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_GT\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_GT);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_GE > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_GE\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_GE);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_LT > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_LT\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_LT);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_LE > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_LE\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_LE);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_LOGIC_OR > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_LOGIC_OR\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_LOGIC_OR);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_LOGIC_AND > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_LOGIC_AND\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_LOGIC_AND);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_MINUS > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_MINUS\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_MINUS);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_PLUS > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_PLUS\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_PLUS);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_MOD > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_MOD\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_MOD);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_TIMES > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_TIMES\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_TIMES);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_NE > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_NE\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_NE);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_SHIFT_LEFT > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_SHIFT_LEFT\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_SHIFT_LEFT);
        needsEnding = 1;
    }

    if (opCounters->prof_BINARY_SHIFT_RIGHT > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"BINARY_SHIFT_RIGHT\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_BINARY_SHIFT_RIGHT);
        needsEnding = 1;
    }

    if (opCounters->prof_UNARY_BIT_NOT > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"UNARY_BIT_NOT\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_UNARY_BIT_NOT);
        needsEnding = 1;
    }

    if (opCounters->prof_UNARY_LOGIC_NOT > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"UNARY_LOGIC_NOT\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_UNARY_LOGIC_NOT);
        needsEnding = 1;
    }

    if (opCounters->prof_UNARY_MINUS > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"UNARY_MINUS\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_UNARY_MINUS);
        needsEnding = 1;
    }

    if (opCounters->prof_UNARY_NUM_ELTS > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"UNARY_NUM_ELTS\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_UNARY_NUM_ELTS);
        needsEnding = 1;
    }

    if (opCounters->prof_DATAHANDLING_STORE > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"DATAHANDLING_STORE\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_DATAHANDLING_STORE);
        needsEnding = 1;
    }

    if (opCounters->prof_DATAHANDLING_ASSIGN > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"DATAHANDLING_ASSIGN\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_DATAHANDLING_ASSIGN);
        needsEnding = 1;
    }

    if (opCounters->prof_DATAHANDLING_CALL > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"DATAHANDLING_CALL\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_DATAHANDLING_CALL);
        needsEnding = 1;
    }

    if (opCounters->prof_DATAHANDLING_LOAD > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"DATAHANDLING_LOAD\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_DATAHANDLING_LOAD);
        needsEnding = 1;
    }

    if (opCounters->prof_DATAHANDLING_LIST_LOAD > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"DATAHANDLING_LIST_LOAD\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_DATAHANDLING_LIST_LOAD);
        needsEnding = 1;
    }

    if (opCounters->prof_DATAHANDLING_LIST_STORE > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"DATAHANDLING_LIST_STORE\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_DATAHANDLING_LIST_STORE);
        needsEnding = 1;
    }

    if (opCounters->prof_FLOWCONTROL_IF > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"FLOWCONTROL_IF\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_FLOWCONTROL_IF);
        needsEnding = 1;
    }

    if (opCounters->prof_FLOWCONTROL_WHILE > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"FLOWCONTROL_WHILE\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_FLOWCONTROL_WHILE);
        needsEnding = 1;
    }

    if (opCounters->prof_FLOWCONTROL_CASE > 0) {
        if (needsEnding)
            gzprintf(f, "},");

        gzprintf(f, "{");
        gzprintf(f, "\"name\" : \"FLOWCONTROL_CASE\",");
        gzprintf(f, "\"count\" : %d", opCounters->prof_FLOWCONTROL_CASE);
        needsEnding = 1;
    }

    if (needsEnding) {
        gzprintf(f, "}");
    }

    gzprintf(f, "]");

    gzprintf(f, "}");
    gzprintf(f, "\n");
}