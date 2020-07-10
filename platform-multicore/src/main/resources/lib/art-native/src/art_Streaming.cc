/*
 * Actor art_Streaming (ActorClass_art_Streaming)
 */

#include "actors-rts.h"
#include <errno.h>
#include <sys/socket.h>
#include <unistd.h>
#include <semaphore.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <stdlib.h>
#include <stdio.h>

#define BUF_SIZE        1024
#define MAXPENDING      5    /* Maximum outstanding connection requests */

typedef struct {
    AbstractActorInstance base;
    unsigned short port;
    char buf[BUF_SIZE];
    int pos;
    int size;
} ActorInstance_art_Streaming;

typedef struct cpu_runtime_data {
    struct cpu_runtime_data *cpu; /* Pointer to first element in this list */
    int cpu_count;
    int cpu_index;

    void *(*main)(struct cpu_runtime_data *, int);

    pthread_t thread;
    int physical_id; /* physical index of this cpu */
    sem_t *sem;
    int *sleep; // Odd value indicates thread sleeping
} cpu_runtime_data_t;

static const int exitcode_block_Out_1[] = {
        EXITCODE_BLOCK(1), 0, 1
};

ART_ACTION_CONTEXT(0, 1);

ART_ACTION_SCHEDULER(art_Streaming_action_scheduler) {
    const int *result = EXIT_CODE_YIELD;
    ActorInstance_art_Streaming *thisActor = (ActorInstance_art_Streaming *) pBase;
    int n;
    ART_ACTION_SCHEDULER_ENTER(0, 1);

    n = pinAvailOut_int32_t(ART_OUTPUT(0));

    ART_ACTION_SCHEDULER_LOOP {
        ART_ACTION_SCHEDULER_LOOP_TOP;

        if (thisActor->size == 0) {
            result = EXITCODE_TERMINATE;
            goto out;
        }


        // Here we are sure that we have data in buffer
        if (thisActor->pos < thisActor->size && n > 0) {
            n--;
            ART_ACTION_ENTER(action1, 0);
            pinWrite_int32_t(ART_OUTPUT(0), thisActor->buf[thisActor->pos]);

            thisActor->pos++;

            ART_ACTION_EXIT(action1, 0);
        } else {
            result = exitcode_block_Out_1;
            goto out;
        }
        ART_ACTION_SCHEDULER_LOOP_BOTTOM;
    }

    out:
    ART_ACTION_SCHEDULER_EXIT(0, 1);
    return result;

}

static void wakeup_me(void *instance) {
    if (instance) {
        ActorInstance_art_Streaming *thisActor = (ActorInstance_art_Streaming *) instance;
        cpu_runtime_data_t *cpu = (cpu_runtime_data_t *) thisActor->base.cpu;
        // wake me up if I'm sleeping
        if (*cpu->sleep & 1)
            sem_post(cpu->sem);
    }
}

void *recvProc(void *instance) {
    ActorInstance_art_Streaming *thisActor = (ActorInstance_art_Streaming *) instance;
    int servSock;
    int clntSock;
    struct sockaddr_in servAddr;
    struct sockaddr_in clntAddr;
    unsigned int clntLen;

    /* Create socket for incoming connections */
    if ((servSock = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP)) < 0) {
        printf("socket() failed: %d\n", servSock);
        return NULL;
    }

    /* Construct local address structure */
    memset(&servAddr, 0, sizeof(servAddr));   /* Zero out structure */
    servAddr.sin_family = AF_INET;                /* Internet address family */
    servAddr.sin_addr.s_addr = htonl(INADDR_ANY); /* Any incoming interface */
    servAddr.sin_port = htons(thisActor->port);      /* Local port */

    /* Bind to the local address */
    if (bind(servSock, (struct sockaddr *) &servAddr, sizeof(servAddr)) < 0) {
        printf("bind() failed\n");
        return NULL;
    }

    /* Mark the socket so it will listen for incoming connections */
    if (listen(servSock, MAXPENDING) < 0) {
        printf("listen() failed\n");
        return NULL;
    }

    printf("Lisening on port %d\n", thisActor->port);

    //for (;;) /* Run forever */
    {
        /* Set the size of the in-out parameter */
        clntLen = sizeof(clntAddr);
        /* Wait for a client to connect */
        if ((clntSock = accept(servSock, (struct sockaddr *) &clntAddr, &clntLen)) < 0) {
            printf("accept() failed\n");
            return NULL;
        }
        /* clntSock is connected to a client! */
        printf("Handling client %s\n", inet_ntoa(clntAddr.sin_addr));

        for (;;) {
            if (thisActor->pos >= thisActor->size) {
                /* Receive message from client */
                if ((thisActor->size = recv(clntSock, thisActor->buf, BUF_SIZE, 0)) <= 0) {
                    break;
                }
                thisActor->pos = 0;

                //wakeup me in case sleeping
                wakeup_me(instance);
            }
            usleep(1000);
        }
        close(clntSock);
    }
    close(servSock);

    pthread_exit(NULL);
}

static void constructor(AbstractActorInstance *pBase) {
    ActorInstance_art_Streaming *thisActor = (ActorInstance_art_Streaming *) pBase;
    pthread_t thread;
    int rc = 0;

    thisActor->size = -1;
    thisActor->pos = 0;

    rc = pthread_create(&thread, NULL, recvProc, (void *) thisActor);
    if (rc) {
        printf("ERROR; return code from pthread_create() is %d\n", rc);
        exit(-1);
    }
}

static void destructor(AbstractActorInstance *pBase) {
}

static void setParam(AbstractActorInstance *pBase,
                     const char *paramName,
                     const char *value) {
    ActorInstance_art_Streaming *thisActor = (ActorInstance_art_Streaming *) pBase;
    if (strcmp(paramName, "port") == 0) {
        thisActor->port = atoi(value);
    } else {
        thisActor->port = 12345;
    }
}

static const PortDescription outputPortDescriptions[] = {
        {0, "Out", sizeof(int32_t)}
};

static const int portRate_0[] = {
        0
};

static const int portRate_1[] = {
        1
};

static const ActionDescription actionDescriptions[] = {
        {"action", "action", 0, portRate_1}
};

ActorClass ActorClass_art_Streaming = INIT_ActorClass(
        "ART.art_Streaming",
        ActorInstance_art_Streaming,
        constructor,
        setParam,
        art_Streaming_action_scheduler,
        destructor,
        0, 0,
        1, outputPortDescriptions,
        1, actionDescriptions
);
