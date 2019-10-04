/* 
 * Copyright (c) Ericsson AB, 2010
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

#include <stdlib.h>
#include <stdio.h>
#include <limits.h>

#include "actors-rts.h"
#include "internal.h"

typedef struct {
    AbstractActorInstance **stack;    // stack used for post-order traversal
    AbstractActorInstance **leader;  // class leaders for SCCs
    int depth;                        // depth of stack
    int rpoIndex;                     // index in rPostorder
    int numActors;
} ComponentInfo;


static void blocking_report(AbstractActorInstance *actor);

static const char *findPortName(AbstractActorInstance *actor,
                                const void *port);

static ComponentInfo allocateComponentInfo(int numActors);

static void deallocateComponentInfo(ComponentInfo info);

static int visit(AbstractActorInstance *, ComponentInfo *);

static const int *getExitCode(AbstractActorInstance *actor);

static int visitNeighbor(AbstractActorInstance *actor,
                         int portIndex,
                         int reqSpace,
                         ComponentInfo *info);

/*
 * Reports buffer status at termination:
 * any non-empty buffers are reported on stdout
 *
 * returns TRUE if there was at least one non-empty buffer
 */

int buffer_report(cpu_runtime_data_t *cpu) {
    int i;
    int nonEmptyFifos = 0;

    // Report non-empty FIFOs
    for (i = 0; i < cpu->cpu_count; i++) {
        int j;

        for (j = 0; j < cpu[i].actors; j++) {
            AbstractActorInstance *consumer = cpu[i].actor[j];
            int k;

            for (k = 0; k < consumer->inputs; ++k) {
                InputPort *input = consumer->input + k;
                const OutputPort *output = input->writer;
                int balance = atomic_get(&output->shared->count)
                              - atomic_get(&input->shared->count);
                int capacity = input->capacity;

                if (balance != 0) {
                    AbstractActorInstance *producer = output->actor;
                    const char *outputPortName = "<unknown port>";
                    const char *inputPortName =
                            consumer->actor->inputPortDescriptions[k].name;
                    int s;

                    if (nonEmptyFifos == 0)
                        printf("\nNot all fifos are empty at exit:\n");
                    ++nonEmptyFifos;

                    for (s = 0; s < producer->outputs; ++s) {
                        if (producer->output + s == output) {
                            outputPortName = producer->actor->outputPortDescriptions[s].name;
                            break;
                        }
                    }

                    if (consumer->actor->inputPortDescriptions[k].isBytes) {
                        /*
                         * "isBytes" means struct tokens:
                         * capacity and balance measured in bytes,
                         * but we need to report number of tokens
                         */
                        int tokenSize = consumer->actor->inputPortDescriptions[k].tokenSize;

                        balance /= tokenSize;
                        capacity /= tokenSize;
                    }

                    printf("%s.%s to %s.%s contains %u tokens (capacity: %u)\n",
                           producer->name, outputPortName,
                           consumer->name, inputPortName,
                           balance, capacity);
                }
            }
        }
    }

    return nonEmptyFifos;
}

/*
 * Analyzes the pattern of blocked actors
 * Reports the following on stdout:
 * 1) Actors that wait for each other in a cyclic fashion (deadlock?)
 * 2) Actors that block on an output (deadlock due to insufficient capacity?)
 * 3) Optionally reports the blocking status of all actors
 *    (if reportAllActors is TRUE)
 *
 * Returns true if either of the cases (1) and (2) -deadlock- is found
 */

int deadlock_report(cpu_runtime_data_t *cpu,
                    int numActors,
                    int reportAllActors) {
    int deadlockFound = 0;
    ComponentInfo info = allocateComponentInfo(numActors);
    int i;

    // Visit all actors to identify the SCCs (cyclic blocking patterns)
    // and sort the acyclic condesation topologically

    for (i = 0; i < cpu->cpu_count; i++) {
        int j;

        for (j = 0; j < cpu[i].actors; j++) {
            visit(cpu[i].actor[j], &info);
        }
    }

    if (info.depth != 0 || info.rpoIndex != 0) {
        fprintf(stderr,
                "Internal Error: unvisited actors in deadlock analysis (%d,%d)\n",
                info.depth,
                info.rpoIndex);
        exit(1);
    }

    // First report the deadlocks
    for (i = 0; i < numActors; ++i) {
        AbstractActorInstance *actor = info.stack[i];
        AbstractActorInstance *leader = info.leader[i];

        if (leader != actor) {
            // Found an SCC (cyclic blocking pattern)
            deadlockFound = 1;
            printf("\nCyclic blocking pattern (deadlock?):\n");
            printf("====================================\n");
            while (actor != leader) {
                blocking_report(actor);
                ++i;
                actor = info.stack[i];
            }
            blocking_report(actor);
        } else if (!actor->terminated) {
            const int *exitCode = getExitCode(actor);
            const int *portPtr = exitCode + 1;
            int N = *exitCode;
            int nInputPorts = actor->inputs;
            int p;
            int blockedOnOutput = 0;
            int selfLoop = 0;

            for (p = 0; p < N; ++p, portPtr += 2) {
                if (*portPtr < nInputPorts) {
                    AbstractActorInstance *producer =
                            actor->input[*portPtr].writer->actor;
                    if (producer == actor)
                        selfLoop = 1;
                } else
                    blockedOnOutput = 1;
            }

            if (selfLoop) {
                deadlockFound = 1;
                printf("\nCyclic blocking pattern (deadlock?):\n");
                printf("====================================\n");
                blocking_report(actor);
            } else if (blockedOnOutput) {
                deadlockFound = 1;
                printf("\nInsufficient buffer capacity (artificial deadlock?):\n");
                printf("====================================================\n");
                blocking_report(actor);
            }
        }
    }

    // Optionally create a full termination report with all actors
    if (reportAllActors) {
        printf("\nFull termination report (all actors):\n");
        printf("=====================================\n");
        for (i = numActors - 1; i >= 0; --i) {
            AbstractActorInstance *actor = info.stack[i];
            blocking_report(actor);
        }
    }

    deallocateComponentInfo(info);

    return deadlockFound;
}

/*
 * Single-actor termination report
 */

static void blocking_report(AbstractActorInstance *actor) {
    ActorClass *actorClass = actor->actor;

    if (actor->terminated) {
        printf("%s has terminated\n", actor->name);
    } else {
        const int *exitCode = getExitCode(actor);
        int N;
        int nInputPorts = actor->inputs;
        int p;

        if (exitCode == EXIT_CODE_YIELD)
            printf("%s has yielded\n", actor->name);
        else if (*exitCode == EXIT_CODE_PREDICATE) {
            int C = *++exitCode;
            printf("%s blocked on predicate: %d\n", actor->name, C);
            return;
        } else {
            printf("%s blocked on:\n", actor->name);
            N = *exitCode++;
        }
        for (p = 0; p < N; ++p, exitCode += 2) {
            int portIndex = exitCode[0];
            int nTokens = exitCode[1];

            if (portIndex < nInputPorts) {
                // Input port
                InputPort *input = actor->input + portIndex;
                const char *inputName = actorClass->inputPortDescriptions[portIndex].name;
                const OutputPort *output = input->writer;
                AbstractActorInstance *producer = output->actor;
                const char *outputName = findPortName(producer, output);
                int avail = atomic_get(&output->shared->count) -
                            atomic_get(&input->shared->count);
                printf("  input %s: %d tokens required\n",
                       inputName, nTokens);
                printf("    %d tokens available from producer %s.%s\n",
                       avail, producer->name, outputName);
            } else {
                // It's an output port
                OutputPort *output;
                InputPort **input;
                const char *outputName;
                int r, nReaders;

                portIndex -= nInputPorts;
                output = actor->output + portIndex;
                input = actor->output->reader;
                nReaders = actor->output->readers;
                outputName = actorClass->outputPortDescriptions[portIndex].name;

                printf("  output %s: space for %d tokens required\n",
                       outputName, nTokens);
                for (r = 0; r < nReaders; ++r) {
                    int nTokens = atomic_get(&output->shared->count) -
                                  atomic_get(&input[r]->shared->count);
                    int nSpaces = output->capacity - nTokens;
                    AbstractActorInstance *consumer = input[r]->actor;
                    const char *inputName = findPortName(consumer, input[r]);

                    printf("    %d spaces available in FIFO to consumer %s.%s\n",
                           nSpaces, consumer->name, inputName);
                }
            }
        }
    }
}


/*
 * finds the name of the (input or output) port
 */

static const char *findPortName(AbstractActorInstance *actor,
                                const void *port) {
    ActorClass *actorClass = actor->actor;
    int i;

    for (i = 0; i < actor->inputs; ++i) {
        if (actor->input + i == port) {
            return actorClass->inputPortDescriptions[i].name;
        }
    }

    for (i = 0; i < actor->outputs; ++i) {
        if (actor->output + i == port) {
            return actorClass->outputPortDescriptions[i].name;
        }
    }

    runtimeError(actor, "Internal Error: termination report: port not found\n");
    return 0;
}

/*
 * Allocates the 'stack' and 'leader' arrays
 * and initializes a ComponentInfo struct
 */

static ComponentInfo allocateComponentInfo(int numActors) {
    ComponentInfo result;

    result.stack = (AbstractActorInstance **)
            malloc(numActors * sizeof(AbstractActorInstance *));
    result.leader = (AbstractActorInstance **)
            malloc(numActors * sizeof(AbstractActorInstance *));

    result.depth = 0;
    result.rpoIndex = numActors;
    result.numActors = numActors;

    return result;
}

/*
 * Dallocates the 'stack' and 'leader' arrays of a ComponentInfo struct
 */

static void deallocateComponentInfo(ComponentInfo info) {
    free(info.stack);
    free(info.leader);
}


/*
 * Visit actor and any actors, which it's waiting for.
 * Return the index (on the stack) for the "earliest" actor
 * on which the entire search tree is waiting.
 */
static int visit(AbstractActorInstance *actor, ComponentInfo *info) {
    int i;
    int myIndex = info->depth;
    int minIndex = myIndex;

    // Is it on the stack?
    for (i = 0; i < info->depth; ++i) {
        if (info->stack[i] == actor)
            return i;  // return index
    }

    // Has it already been placed in the rPostorder?
    // (which implies no cycle along this path)
    for (i = info->rpoIndex; i < info->numActors; ++i) {
        if (info->stack[i] == actor)
            return info->depth; // larger than index of caller
    }

    // The actor has not yet been visited, push it onto the stack!
    info->stack[myIndex] = actor;
    info->depth++;

    if (!actor->terminated) {
        // Visit the actor(s), on which this actor is waiting
        const int *exitCode = getExitCode(actor);
        int numPorts = *(exitCode++);
        int p;

        for (p = 0; p < numPorts; ++p, exitCode += 2) {
            int index = visitNeighbor(actor, exitCode[0], exitCode[1], info);

            if (index < minIndex)
                minIndex = index;
        }
    }

    if (minIndex == myIndex) {
        // Pop an entire SCC off the stack
        // (possibly a trivial one: just the actor itself)
        int top = info->depth - 1;
        int rpo = info->rpoIndex - 1;
        AbstractActorInstance *classLeader = info->stack[top];

        info->stack[rpo] = classLeader;
        info->leader[rpo] = classLeader;

        while (top > minIndex) {
            info->stack[--rpo] = info->stack[--top];
            info->leader[rpo] = classLeader;
        }

        info->depth = top;
        info->rpoIndex = rpo;
    }
    // else: leave the actor on the stack -it will be removed
    // along with its SCC (minIndex or later).

    return minIndex;
}


/*
 * Try to fire the actor to find out why it's blocked
 * Return the exit code:
 * (N, portIndex1, nTokens1, ... , portIndexN, nTokensN)
 * N=0 means termination, EXIT_CODE_YIELD we shouldn't get
 */

static const int *getExitCode(AbstractActorInstance *actor) {
    const int *result;

    actor->fired = 0;
    result = actor->actor->action_scheduler(actor, 1);

    if (actor->fired || result == EXIT_CODE_YIELD) {
        runtimeError(actor,
                     "Internal error: network terminated, though actor is fireable");
    }

    return result;
}


/*
 * Visit the neighbor(s) we're waiting for via the given port,
 * reqSpace is the space required (OutputPort) as given by the
 * actor scheduler's exit code (not used for InputPorts).
 */

static int visitNeighbor(AbstractActorInstance *actor,
                         int portIndex,
                         int reqSpace,
                         ComponentInfo *info) {
    int Nports = actor->inputs + actor->outputs;

    if (portIndex < 0 || portIndex >= Nports) {
        runtimeError(actor,
                     "Internal error: port index in exit code out of range %d (%d)",
                     portIndex, Nports);
    }

    if (portIndex < actor->inputs) {
        // It's an input port
        AbstractActorInstance *producer = actor->input[portIndex].writer->actor;
        return visit(producer, info);
    } else {
        // It's an output port
        OutputPort *output;
        InputPort **input;
        int i, nReaders;
        int minIndex = INT_MAX;

        portIndex -= actor->inputs;
        output = actor->output + portIndex;
        input = output->reader;
        nReaders = output->readers;

        for (i = 0; i < nReaders; ++i) {
            int nTokens = atomic_get(&output->shared->count) -
                          atomic_get(&input[i]->shared->count);
            int availSpace = output->capacity - nTokens;

            if (availSpace < reqSpace) {
                AbstractActorInstance *consumer = input[i]->actor;
                int index = visit(consumer, info);

                if (index < minIndex)
                    minIndex = index;
            }
            // else: there is enough space (actor is not waiting
            // for this consumer to read!).
        }

        return minIndex;
    }
}
