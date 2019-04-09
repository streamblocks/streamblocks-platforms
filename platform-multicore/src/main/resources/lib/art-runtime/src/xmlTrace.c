/* 
 * Copyright (c) Ericsson AB, 2009
 * Author: Carl von Platen (carl.von.platen@ericsson.com)
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

    fprintf(f, "<network name=\"%s\">\n", networkName);

    for (i = 0; i < numActors; ++i) {
        const AbstractActorInstance *instance = actors[i];
        const ActorClass *actorClass = instance->actor;
        int numInputs = actorClass->numInputPorts;
        const InputPort *inputs = instance->input;
        int numOutputs = actorClass->numOutputPorts;
        const OutputPort *outputs = instance->output;
        int numActions = actorClass->numActions;
        const ActionDescription *actions = actorClass->actionDescriptions;
        int firstAction = instance->firstActionIndex;
        int j;

        fprintf(f, "  <actor id=\"%d\" class=\"%s\" instance-name=\"%s\">\n",
                i, actorClass->name, instance->name);

        // <input>
        // id is a running index 0,1,2... (unique among input ports)
        // name is the name used in the source
        // source is the identity of the source (output port)
        for (j = 0; j < numInputs; ++j) {
            const char *name = actorClass->inputPortDescriptions[j].name;

            fprintf(f, "    <input id=\"%d\" name=\"%s\" source=\"%d\"/>\n",
                    firstInput + j, name, inputs[j].writer->index);
        }

        // <output>
        // id is unique among output ports
        // name is the name used in the source
        for (j = 0; j < numOutputs; ++j) {
            const char *name = actorClass->outputPortDescriptions[j].name;

            fprintf(f, "    <output id=\"%d\" name=\"%s\"/>\n",
                    outputs[j].index, name);
        }

        // <action>
        // id is unique among all actions of all actor instances
        // name is not necessarily unique (not even within an actor)
        for (j = 0; j < numActions; ++j) {
            int p;
            const char *name = actions[j].name;

            if (name)
                fprintf(f, "    <action id=\"%d\" name=\"%s\">\n",
                        firstAction + j, actions[j].name);
            else
                fprintf(f, "    <action id=\"%d\">\n", firstAction + j);

            // <consumes>
            // count=number of tokens consumed
            // port=reference to input (id)
            for (p = 0; p < numInputs; ++p) {
                int cns = actions[j].consumption[p];
                if (cns)
                    fprintf(f, "      <consumes count=\"%d\" port=\"%d\"/>\n",
                            cns, firstInput + p);
            }

            // <produces>
            // count=number of tokens consumed
            // port=reference to output (id)
            for (p = 0; p < numOutputs; ++p) {
                int prd = actions[j].production[p];
                if (prd)
                    fprintf(f, "      <produces count=\"%d\" port=\"%d\"/>\n",
                            prd, outputs[p].index);
            }

            fprintf(f, "    </action>\n");
        }

        fprintf(f, "  </actor>\n");
        firstInput += numInputs;
    }
    fprintf(f, "</network>\n");
}

extern unsigned int timestamp();

void xmlTraceAction(FILE *f, int actionIndex) {
    static unsigned int step = 0;
    int localstep;
    pthread_mutex_lock(&xmlMutex);
    localstep = step++;
    pthread_mutex_unlock(&xmlMutex);
    fprintf(f, "<trace timestamp=\"%u\" action=\"%d\" step=\"%u\"/>\n", timestamp(0), actionIndex, localstep);
}

void xmlTraceStatus(FILE *f, int status) {

    fprintf(f, "<cpu timestamp=\"%u\" status=\"%d\"/>\n", timestamp(0), status);
}

void xmlTraceWakeup(FILE *f, int whom) {

    fprintf(f, "<cpu timestamp=\"%u\" wakeup=\"%d\"/>\n", timestamp(0), whom);
}
