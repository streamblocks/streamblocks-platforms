#ifndef _DLL_H
#define _DLL_H

#include <semaphore.h>
#include <sys/types.h>
#include <sys/time.h>
#include <pthread.h>

/* make the header usable from C++ */
#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define RUNNING_STATE      0
#define READY_STATE        1
#define WAITING_STATE      2
#define WAKEUP_STATE      3
#define TERMINATED_STATE  4

//#define QUEUE_MUTEXTED

#ifdef QUEUE_MUTEXTED
extern int InitializeCriticalSection(sem_t *semaphore);
extern int EnterCriticalSection(sem_t *semaphore);
extern int LeaveCriticalSection(sem_t *semaphore);
#define LOCK(_ic)      EnterCriticalSection(&(_ic->lock))
#define UNLOCK(_ic)      LeaveCriticalSection(&(_ic->lock))
#define RELEASE(_ic)    DeleteCriticalSection(&(_ic->lock))
#define INIT(_ic)      InitializeCriticalSection(&(_ic->lock))
#else
#define LOCK(_ic)
#define UNLOCK(_ic)
#define RELEASE(_ic)
#define INIT(_ic)
#endif

typedef struct _stat {
    unsigned int totNumSleeps;
    unsigned int totTimeSleeps;
} Stat;

typedef struct _linkednode {
    struct _linkednode *next;
    struct _linkednode *prev;
    void *obj;
} LinkedNode;

typedef struct _list {
    LinkedNode *head;
    LinkedNode *tail;
    int numNodes;
    int lid;          //list id
    int cid;          //cpu core id
#ifdef EDF
    struct timespec list_period;
    struct timespec list_budget;
#endif
    int state;

    Stat stat;

    pthread_mutex_t mt;
    pthread_cond_t cv;

    struct _list *extList;

    FILE *file;
} LIST;

#ifdef __cplusplus
}
#endif

#endif
