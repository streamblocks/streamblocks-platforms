/*
 * Copyright (c) EPFL VLSC
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

#include <errno.h>
#include <zlib.h>

#include "actors-rts.h"
#include "kseq.h"
#include "art_genomic_constant.h"

KSEQ_INIT(gzFile, gzread)

#define OUT0_SEQ ART_OUTPUT(0)


static const uint8_t nucleotides_to_uint4[] = {0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, DNA_A /* A */, 0x0, DNA_C /* C */, 0x0, 0x0, 0x0, DNA_G /* G */, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, DNA_T /* T */, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, DNA_A /* a */, 0x0, DNA_C /* c */,
                                                0x0,
                                                0x0,
                                                0x0, DNA_G /* g */, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                DNA_T /* t */, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
                                                0x0,
                                                0x0,
                                                0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0};

typedef struct{
    AbstractActorInstance base;
    char *filename;
    gzFile fp;
    kseq_t *seq;
    int length;
    int program_counter;
    int remaining_data;
    int has_send;
} ActorInstance_art_Fasta;

static const int exitcode[] = {EXITCODE_BLOCK(1), 0, 1};

ART_ACTION_CONTEXT(0, 1);

ART_ACTION_SCHEDULER(art_Fasta_scheduler) {
    const int *result = EXIT_CODE_YIELD;
     ActorInstance_art_Fasta *thisActor = (ActorInstance_art_Fasta *) pBase;

     ART_ACTION_SCHEDULER_ENTER(0, 1)

     switch (thisActor->program_counter) {
         case 0 :
             goto S0;
         case 3 :
             goto S3;
     }

     S0:
         thisActor->length = kseq_read(thisActor->seq);
         goto S1;

     S1:
         if(thisActor->length >= 0 ){
             goto S2;
         }else{
             result = EXITCODE_TERMINATE;
             goto out;
         }

     S2:
         {
             int avail = pinAvailOut_int8_t(OUT0_SEQ);
             if ( avail >= (thisActor->seq->seq.l + 1) ){
                 ART_ACTION_ENTER(action1, 0);

                 for(size_t i = 0; i < thisActor->seq->seq.l; i++){
                    pinWrite_uint8_t(OUT0_SEQ, nucleotides_to_uint4[thisActor->seq->seq.s[i]]);
                 }

                 // -- Write EOS
                 pinWrite_uint8_t(OUT0_SEQ, DNA_EOS);

                 ART_ACTION_EXIT(action1, 0);
                 goto S0;
             } else{
                 ART_ACTION_ENTER(action1, 0);

                 for(size_t i = 0; i < avail -1; i++){
                    pinWrite_uint8_t(OUT0_SEQ, nucleotides_to_uint4[thisActor->seq->seq.s[i]]);
                 }

                 ART_ACTION_EXIT(action1, 0);
                 thisActor->remaining_data = thisActor->seq->seq.l - avail;
                 thisActor->has_send = avail;
                 result = exitcode;
                 thisActor->program_counter = 3;
                 goto out;
             }
         }

     S3:
         {
             int avail = pinAvailOut_int8_t(OUT0_SEQ);
             if( avail >= (thisActor->remaining_data + 1)){
                 ART_ACTION_ENTER(action1, 0);
                 for(size_t i = 0; i < thisActor->remaining_data; i++){
                     pinWrite_int8_t(OUT0_SEQ, nucleotides_to_uint4[thisActor->seq->seq.s[thisActor->has_send + i]]);
                 }

                 // -- Write EOS
                 pinWrite_uint8_t(OUT0_SEQ, DNA_EOS);

                 ART_ACTION_EXIT(action1, 0);
                 goto S0;
             }else {
                 ART_ACTION_ENTER(action1, 0);
                 for (size_t i = 0; i < avail; i++) {
                     pinWrite_int8_t(OUT0_SEQ, nucleotides_to_uint4[thisActor->seq->seq.s[thisActor->has_send + i]]);
                 }
                 ART_ACTION_EXIT(action1, 0);
                 thisActor->has_send += avail;
                 thisActor->remaining_data -= avail;
                 result = exitcode;
                 thisActor->program_counter = 3;
                 goto out;
             }
         }

     out:
     ART_ACTION_SCHEDULER_EXIT(0, 1)
     return result;
}

static void constructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Fasta *thisActor = (ActorInstance_art_Fasta *) pBase;
    if (thisActor->filename == NULL) {
        runtimeError(pBase, "Parameter not set: fileName");
    } else {
        printf("Open %s\n", thisActor->filename);
        thisActor->fp = gzopen(thisActor->filename, "r");
        if(thisActor->fp == NULL){
            runtimeError(pBase, "Cannot open file for output: %s: %s",
                         thisActor->filename, strerror(errno));
        }
        thisActor->seq = kseq_init(thisActor->fp);
        thisActor->program_counter = 0;
        thisActor->remaining_data = 0;
    }

}

static void destructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Fasta *thisActor = (ActorInstance_art_Fasta *) pBase;
    if(thisActor->seq != NULL){
        kseq_destroy(thisActor->seq);
    }
    gzclose(thisActor->fp);
}

static void setParam(AbstractActorInstance *pBase, const char *paramName,
                     const char *value) {
    ActorInstance_art_Fasta *thisActor = (ActorInstance_art_Fasta *) pBase;
    if (strcmp(paramName, "fileName") == 0) {
        thisActor->filename = strdup(value);
    } else {
        runtimeError(pBase, "No such parameter: %s", paramName);
    }
}

static const PortDescription outputPortDescriptions[] = {{0, "SEQ",
                                                                 sizeof(int8_t)}};

static const int portRate_1[] = {1};

// -- State Variables Description
static const StateVariableDescription stateVariableDescription[] = {};

// -- Uses by Transition
static const int uses_in_action[] = {};

// -- Defines by Transition
static const int defines_in_action[] = {};

static const ActionDescription actionDescriptions[] = {{"action", "action", 0,
                                                               portRate_1,uses_in_action, defines_in_action}};

// -- Condition Description
static const ConditionDescription conditionDescription[] = {};

ActorClass ActorClass_art_Fasta = INIT_ActorClass(
        "ART.art_Fasta",
        ActorInstance_art_Fasta,
        constructor,
        setParam,
        art_Fasta_scheduler,
        destructor,
        0, 0,
        1, outputPortDescriptions,
        1, actionDescriptions,
        0, conditionDescription,
        0, stateVariableDescription
);
