/*
 * Copyright (c) Ericsson AB, 2009, EPFL VLSC, 2019
 * Author: Carl von Platen (carl.von.platen@ericsson.com)
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

#include "xmlTrace.h"

static pthread_mutex_t xmlMutex;

FILE *xmlCreateTrace(const char *fileName) {
    FILE *f = fopen(fileName, "w");
    if (f != 0) {
        fprintf(f, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        fprintf(f, "<execution-trace>\n");
    }
    return f;
}

void xmlCloseTrace(FILE *f) {
    fprintf(f, "</execution-trace>\n");
    fclose(f);
}

void xmlDeclareNetwork(FILE *f,
                       const char *networkName,
                       AbstractActorInstance *actors[],
                       int numActors) {
    int i;
    int firstInput = 0;

    fprintf(f, "\t<network name=\"%s\">\n", networkName);

    for (i = 0; i < numActors; ++i) {
        const AbstractActorInstance *instance = actors[i];
        const ActorClass *actorClass = instance->actor;
        int numInputs = actorClass->numInputPorts;
        const InputPort *inputs = instance->input;
        int numOutputs = actorClass->numOutputPorts;
        const OutputPort *outputs = instance->output;
        int numActions = actorClass->numActions;
        int numConditions = actorClass->numConditions;
        int numStateVariables = actorClass->numStateVariables;
        const ActionDescription *actions = actorClass->actionDescriptions;
        const ConditionDescription *conditions = actorClass->conditionDescription;
        const StateVariableDescription *stateVariables = actorClass->stateVariableDescription;
        int firstAction = instance->firstActionIndex;
        int firstCondition = instance->firstConditionIndex;
        int firstStateVariable = instance->firstStateVariableIndex;

        int j;

        fprintf(f, "\t\t<actor id=\"%d\" class=\"%s\" instance-name=\"%s\">\n",
                i, actorClass->name, instance->name);

        // <input>
        // id is a running index 0,1,2... (unique among input ports)
        // name is the name used in the source
        // source is the identity of the source (output port)
        for (j = 0; j < numInputs; ++j) {
            const char *name = actorClass->inputPortDescriptions[j].name;

            fprintf(f, "\t\t\t<input id=\"%d\" name=\"%s\" source=\"%d\"/>\n",
                    firstInput + j, name, inputs[j].writer->index);
        }

        // <output>
        // id is unique among output ports
        // name is the name used in the source
        for (j = 0; j < numOutputs; ++j) {
            const char *name = actorClass->outputPortDescriptions[j].name;

            fprintf(f, "\t\t\t<output id=\"%d\" name=\"%s\"/>\n",
                    outputs[j].index, name);
        }

        // <state-variable>
        // id is unique among state variables
        // name is the name used in the source
        for (j = 0; j < numStateVariables; ++j) {
            const char *name = stateVariables[j].name;
            const char *originalName = stateVariables[j].originalName;
            fprintf(f, "\t\t\t<state-variable id=\"%d\" name=\"%s\" originalName=\"%s\"/>\n",
                    firstStateVariable + j, name, originalName);
        }


        // <condition>
        for (j = 0; j < numConditions; ++j) {
            int p;
            const char *name = conditions[j].name;

            if (name)
                fprintf(f, "\t\t\t<condition id=\"%d\" name=\"%s\">\n",
                        firstCondition + j, conditions[j].name);
            else
                fprintf(f, "\t\t\t<condition id=\"%d\">\n", firstAction + j);

            if (conditions[j].kind == INPUT_KIND) {
                p = conditions[j].port;
                fprintf(f, "\t\t\t\t<input count=\"%d\" port=\"%d\"/>\n",
                        conditions[j].count, firstInput + p);

            } else if (conditions[j].kind == OUTPUT_KIND) {
                p = conditions[j].port;
                fprintf(f, "\t\t\t\t<output count=\"%d\" port=\"%d\"/>\n",
                        conditions[j].count, outputs[p].index);
            } else {
                for (p = 0; p < numStateVariables; p++) {
                    int var = conditions[j].stateVariables[p];
                    if (var)
                        fprintf(f, "\t\t\t\t<predicate variable=\"%d\"/>\n",
                                firstStateVariable + p);
                }
            }

            fprintf(f, "\t\t\t</condition>\n");
        }


        // <action>
        // id is unique among all actions of all actor instances
        // name is not necessarily unique (not even within an actor)
        for (j = 0; j < numActions; ++j) {
            int p;
            const char *name = actions[j].originalName;

            if (name)
                fprintf(f, "\t\t\t<action id=\"%d\" name=\"%s\">\n",
                        firstAction + j, actions[j].originalName);
            else
                fprintf(f, "\t\t\t<action id=\"%d\">\n", firstAction + j);

            // <consumes>
            // count=number of tokens consumed
            // port=reference to input (id)
            for (p = 0; p < numInputs; ++p) {
                int cns = actions[j].consumption[p];
                if (cns)
                    fprintf(f, "\t\t\t\t<consumes count=\"%d\" port=\"%d\"/>\n",
                            cns, firstInput + p);
            }

            // <produces>
            // count=number of tokens consumed
            // port=reference to output (id)
            for (p = 0; p < numOutputs; ++p) {
                int prd = actions[j].production[p];
                if (prd)
                    fprintf(f, "\t\t\t\t<produces count=\"%d\" port=\"%d\"/>\n",
                            prd, outputs[p].index);
            }

            fprintf(f, "\t\t\t</action>\n");
        }

        fprintf(f, "\t\t</actor>\n");
        firstInput += numInputs;
    }
    fprintf(f, "\t</network>\n");
}


void xmlDeclareStateDep(FILE *f,
                        const char *networkName,
                        AbstractActorInstance *actors[],
                        int numActors) {

    fprintf(f, "\t<network name=\"%s\">\n", networkName);

    for (int i = 0; i < numActors; ++i) {
        const AbstractActorInstance *instance = actors[i];
        const ActorClass *actorClass = instance->actor;
        int numActions = actorClass->numActions;
        const ActionDescription *actions = actorClass->actionDescriptions;
        int firstAction = instance->firstActionIndex;
        int numStateVariables = actorClass->numStateVariables;
        const StateVariableDescription *stateVariables = actorClass->stateVariableDescription;
        int firstStateVariable = instance->firstStateVariableIndex;


        int j;

        fprintf(f, "\t\t<actor id=\"%d\" class=\"%s\" instance-name=\"%s\">\n",
                i, actorClass->name, instance->name);


        for (j = 0; j < numActions; ++j) {
            int p;
            const char *name = actions[j].originalName;

            if (name)
                fprintf(f, "\t\t\t<action id=\"%d\" name=\"%s\">\n",
                        firstAction + j, actions[j].originalName);
            else
                fprintf(f, "\t\t\t<action id=\"%d\">\n", firstAction + j);

            for (p = 0; p < numStateVariables; p++) {
                int var = actions[j].uses[p];
                if (var)
                    fprintf(f, "\t\t\t\t<uses id=\"%d\" name=\"%s\"/>\n", firstStateVariable + p,  stateVariables[p].originalName);
            }

            for (p = 0; p < numStateVariables; p++) {
                int var = actions[j].defines[p];
                if (var)
                    fprintf(f, "\t\t\t\t<defines id=\"%d\" name=\"%s\"/>\n", firstStateVariable + p,  stateVariables[p].originalName);
            }

            fprintf(f, "\t\t\t</action>\n");
        }


        fprintf(f, "\t\t</actor>\n");
    }

    fprintf(f, "\t</network>\n");
}


extern unsigned int timestamp();

void xmlTraceAction(FILE *f, unsigned int t, int actionIndex, int factor) {
    static unsigned int step = 0;
    int localstep;
    pthread_mutex_lock(&xmlMutex);
    localstep = step++;
    pthread_mutex_unlock(&xmlMutex);
    fprintf(f, "\t<trace timestamp=\"%u\" action=\"%d\" exectime=\"%d\" step=\"%u\"/>\n", t, actionIndex,
            (timestamp(0) - t)*factor, localstep);
}

void xmlTraceCondition(FILE *f, unsigned int t, int conditionIndex) {
    pthread_mutex_lock(&xmlMutex);
    pthread_mutex_unlock(&xmlMutex);
    fprintf(f, "\t<trace timestamp=\"%u\" condition=\"%d\" exectime=\"%d\"/>\n", t, conditionIndex, timestamp(0) - t);
}

void xmlTraceStatus(FILE *f, int status) {

    fprintf(f, "\t<cpu timestamp=\"%u\" status=\"%d\"/>\n", timestamp(0), status);
}

void xmlTraceWakeup(FILE *f, int whom) {

    fprintf(f, "\t<cpu timestamp=\"%u\" wakeup=\"%d\"/>\n", timestamp(0), whom);
}
