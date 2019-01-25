#ifndef _ACTOR_OUTPUT_H
#define _ACTOR_OUTPUT_H

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>
#include "actor.h"
#include "fifo.h"
#include "fifo_list.h"

#pragma clang diagnostic ignored "-Wparentheses-equality"

#define BUFFER_SIZE 1024

template<typename T>
class ActorOutput : public Actor {
public:
    ActorOutput(FILE *stream, Fifo<T> *channel) {
        this->channel = channel;
        this->stream = stream;
    }

    bool run() override{
        size_t space = channel->space();

        if (space > 0) {
            T v[channel->space()];
            channel->peek(0, space, v);
            fwrite(v, sizeof(T), space, stream);
            channel->consume(space);
            return true;
        } else {
            return false;
        }
    }


private:
    // -- Input Channel
    Fifo<T> *channel;

    // -- Input File
    FILE *stream;

};

#endif //_ACTOR_OUTPUT_H


