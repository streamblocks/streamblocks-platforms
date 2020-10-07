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

#ifndef xmlTrace_H_INCLUDED
#define xmlTrace_H_INCLUDED

#include <stdio.h>

#include "actors-rts.h"


/*
 * create file and generate header
 */
FILE *xmlCreateTrace(const char *fileName);

/*
 * complete trace document and close file
 */
void xmlCloseTrace(FILE *f);

/*
 * declare the network, its actor instances,
 * their ports and actions
 */
void xmlDeclareNetwork(FILE *f,
                       const char *networkName,
                       AbstractActorInstance *actors[],
                       int numActors);
/*
 * declare the state variable of the  network, its actor instances
 *
 */
void xmlDeclareStateDep(FILE *f,
                        const char *networkName,
                        AbstractActorInstance *actors[],
                        int numActors);


/*
 * Generate trace of one action firing
 */
void xmlTraceAction(FILE *f, unsigned int t,  int actionIndex, int factor);


/*
 * Generate trace of one condition
 */
void xmlTraceCondition(FILE *f, unsigned int t, int conditionIndex);

/*
 * Generate CPU status 0-goto sleep 1-wakeup
 */
void xmlTraceStatus(FILE *f, int status);

/*
 * wakeup to by from
 */
void xmlTraceWakeup(FILE *f, int whom);

#endif /* xmlTrace_H_INCLUDED */

