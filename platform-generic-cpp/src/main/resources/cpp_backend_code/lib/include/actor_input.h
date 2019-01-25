#ifndef _ACTOR_INPUT_H
#define _ACTOR_INPUT_H

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
class ActorInput : public Actor {
public:
    ActorInput(FILE *stream, FifoList<T> *channel) {
        this->channel = channel;
        this->stream = stream;
    }

    bool run() override{
        size_t space_before = channel->space();
        size_t space = space_before;
        while (space > 0 && !feof(stream)) {
            T v[BUFFER_SIZE];
            size_t read = fread(&v, sizeof(T), space > BUFFER_SIZE ? BUFFER_SIZE : space, stream);
            channel->write(v, read);
            space -= read;
        }

        return space != space_before;
    }


private:
    // -- Input Channel
    FifoList<T> *channel;

    // -- Input File
    FILE *stream;

};

#endif //_ACTOR_INPUT_H


