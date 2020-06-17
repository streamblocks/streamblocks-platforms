/* 
 * Copyright (c) Ericsson AB, 2009, EPFL, 2018
 * Author: Charles Chen Xu (charles.chen.xu@ericsson.com)
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

#ifndef _INTERNAL_H
#define _INTERNAL_H

#include <stdio.h>
#include "actors-thread.h"
#include "cycle.h"
#include <zlib.h>

/* make the header usable from C++ */
#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define    LOG_MUST            0            //must log this
#define    LOG_ERROR            1            //only log errors
#define    LOG_WARN            2            //also log warnings
#define    LOG_INFO            3            //also log info
#define    LOG_EXEC            4            //also log func exec
#define    LOG_STOP            (-99)        //disable log file

#define MAX_ACTOR_NUM       256
#define MAX_CONNECTS        1024

typedef struct _ThreadID {
    int id;
    int cpu;
} ThreadID;


typedef ticks art_timer_t;
typedef struct {
    art_timer_t prefire;
    art_timer_t read_barrier;
    art_timer_t fire;
    art_timer_t write_barrier;
    art_timer_t postfire;
    art_timer_t sync_unblocked;
    art_timer_t sync_blocked;
    art_timer_t sync_sleep;
    art_timer_t total;
    long long nsleep;
    long long nloops;
} statistics_t;


typedef struct cpu_runtime_data {
    struct cpu_runtime_data *cpu; /* Pointer to first element in this list */
    int cpu_count;
    int cpu_index;

    void *(*main)(struct cpu_runtime_data *, int);

    pthread_t thread;
    int physical_id; /* physical index of this cpu */

    art_semaphore_t *sem;

    int *sleep; // Odd value indicates thread sleeping
    int quiescent_at; // Does this need to be cache_aligned?
    struct SharedContext *shared;
    struct LocalContext *local;
    int actors;
    struct AbstractActorInstance **actor; /* Pointer to actors for this cpu */
    void *actor_data;
    int *has_affected;
    statistics_t statistics;
    FILE *traceFile;
    gzFile *traceTurnusFile;
    FILE *infoFile;
} cpu_runtime_data_t;


extern int log_level;

// extern void trace(int level, const char*,...);
extern void register_thread_id(int index);

extern int get_thread_ids(ThreadID **theThreadIDs);

extern void set_cpu_category(int category);

extern void reset_quality_level();

extern void set_quality_levels(int quality, int bandwidth, int granularity);

extern void printout_config();

extern void (*cb_add_threads)(int);

extern void (*cb_register_thread)(int);

extern int buffer_report(cpu_runtime_data_t *cpu);

extern int deadlock_report(cpu_runtime_data_t *cpu,
                           int numActors,
                           int reportAllActors);

#ifdef __cplusplus
}
#endif

#endif
