/*
 * Copyright (c) EPFL, 2019
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

#ifndef _ART_THREAD_H
#define _ART_THREAD_H


#ifndef _WIN32
#define __USE_GNU 1
#endif

#ifdef _WIN32

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

// Thread
typedef long cpu_set_t;
#define art_thread_t HANDLE
#define art_thread_id_t DWORD

#define art_clear_cpu_set(cpuset) cpuset = 0

/**
 * Sets the affinity of the given thread to the given processor.
 */
static void art_set_thread_affinity(cpu_set_t cpuset, int proc_num, art_thread_t hThread) {
    DWORD_PTR dwThreadAffinityMask = 1 << proc_num;
    SetThreadAffinityMask(hThread, dwThreadAffinityMask);
}

/**
 * Sets the affinity of this process to the given processor.
 */
static void art_set_this_process_affinity(cpu_set_t cpuset, int proc_num) {
    HANDLE hProcess = GetCurrentProcess();
    DWORD_PTR dwProcessAffinityMask = 1 << proc_num;
    SetProcessAffinityMask(hProcess, dwProcessAffinityMask);
}

#define art_thread_create(thread, function, argument, id) thread = CreateThread(NULL, 0, (LPTHREAD_START_ROUTINE) function, (LPVOID) &(argument), 0, &(id))
#define art_thread_join(thread) WaitForSingleObject(thread, INFINITE)

// Semaphore
#define MAX_SEM_COUNT 10

#define art_semaphore_create(semaphore, number) semaphore = CreateSemaphore(NULL, number, MAX_SEM_COUNT, NULL)
#define art_semaphore_wait(semaphore) WaitForSingleObject(semaphore, INFINITE)
#define art_semaphore_set(semaphore) ReleaseSemaphore(semaphore, 1, NULL)
#define art_semaphore_destroy(semaphore) CloseHandle(semaphore)

#define art_semaphore_t HANDLE

#else

#include <pthread.h>

#define art_thread_create(thread, function, argument) pthread_create(&(thread), NULL, function, (void *) &(argument))
#define art_thread_join(thread) pthread_join(thread, NULL)

#define art_thread_t pthread_t
#define art_thread_id_t int

#ifdef __APPLE__

#include <mach/mach_traps.h>
#include <mach/semaphore.h>
#include <mach/task.h>
#include <mach/mach.h>
#include <sys/types.h>
#include <sys/sysctl.h>

#define CPU_SETSIZE 16
#define SYSCTL_CORE_COUNT   "machdep.cpu.core_count"

typedef struct cpu_set {
    uint32_t    count;
} cpu_set_t;


static inline void CPU_ZERO(cpu_set_t *cs) { cs->count = 0; }

static inline void CPU_SET(int num, cpu_set_t *cs) { cs->count |= (1 << num); }

static inline int CPU_ISSET(int num, cpu_set_t *cs) { return (cs->count & (1 << num)); }



static int sched_getaffinity(pid_t pid, size_t cpu_size, cpu_set_t *cpu_set)
{
    int32_t core_count = 0;
    size_t  len = sizeof(core_count);
    int ret = sysctlbyname(SYSCTL_CORE_COUNT, &core_count, &len, 0, 0);
    if (ret) {
        printf("error while get core count %d\n", ret);
        return -1;
    }
    cpu_set->count = 0;
    for (int i = 0; i < core_count; i++) {
        cpu_set->count |= (1 << i);
    }

    return 0;
}

static int pthread_setaffinity_np(pthread_t thread, size_t cpu_size,
                                  cpu_set_t *cpu_set)
{
    thread_port_t mach_thread;
    int core = 0;

    for (core = 0; core < 8 * cpu_size; core++) {
        if (CPU_ISSET(core, cpu_set)) break;
    }
    thread_affinity_policy_data_t policy = { core };
    mach_thread = pthread_mach_thread_np(thread);
    thread_policy_set(mach_thread, THREAD_AFFINITY_POLICY,
                      (thread_policy_t)&policy, 1);
    return 0;
}

#define art_clear_cpu_set(mask) CPU_ZERO(mask)
#define art_isset_cpu_set(proc_num, mask) CPU_ISSET(proc_num, mask)
#define art_set_cpu_set(proc_num, mask) CPU_SET(proc_num, mask)
#define art_get_affinity(pid, mask) sched_getaffinity(pid, sizeof(mask), &mask)


#define art_set_thread_affinity(mask, proc_num, thread) {             	\
            CPU_ZERO(&(mask));										 	\
            CPU_SET(proc_num, &(mask));                                    	\
            pthread_setaffinity_np(thread, sizeof(cpu_set_t), &(mask));    	\
    }
#define art_set_new_affinity(mask, proc_num) ({					\
            int ret; \
            CPU_ZERO(&(mask));											 	\
            CPU_SET(proc_num, &(mask));										\
            ret = pthread_setaffinity_np(0, sizeof(cpu_set_t), &(mask));					\
            ret;\
} )

#define art_set_affinity(thread, mask) pthread_setaffinity_np(thread, sizeof(mask), &mask)


#define art_semaphore_create(semaphore, number) semaphore_create(mach_task_self(), (semaphore), SYNC_POLICY_FIFO, (number))
#define art_semaphore_wait(semaphore) semaphore_wait(semaphore)
#define art_semaphore_try_wait(semaphore) semaphore_wait(semaphore)
#define art_semaphore_set(semaphore) semaphore_signal(semaphore)
#define art_semaphore_destroy(semaphore) semaphore_destroy(mach_task_self(), &(semaphore))
#define art_semaphore_t semaphore_t

#else

#include <sched.h>
#include <semaphore.h>

#define art_clear_cpu_set(mask) CPU_ZERO(mask)
#define art_isset_cpu_set(proc_num, mask) CPU_ISSET(proc_num, mask)
#define art_set_cpu_set(proc_num, mask) CPU_SET(proc_num, mask)
#define art_get_affinity(pid, mask) sched_getaffinity(pid, sizeof(mask), &mask)

#define art_set_thread_affinity(mask, proc_num, thread)( {             	\
            int ret; \
            CPU_ZERO(&(mask));										 	\
            CPU_SET(proc_num, &(mask));                                    	\
            ret = pthread_setaffinity_np(thread, sizeof(cpu_set_t), &(mask));    	\
            ret; \
    })

#define art_set_affinity(thread, mask) pthread_setaffinity_np(thread, sizeof(mask), &mask)

#define art_semaphore_create(semaphore, number) sem_init(semaphore, 0, (number))
#define art_semaphore_wait(semaphore) sem_wait(semaphore)
#define art_semaphore_try_wait(semaphore) sem_trywait(semaphore)
#define art_semaphore_set(semaphore) sem_post(semaphore)
#define art_semaphore_destroy(semaphore) sem_destroy(semaphore)
#define art_semaphore_t sem_t

#endif

#endif


#endif //_ART_THREAD_H
