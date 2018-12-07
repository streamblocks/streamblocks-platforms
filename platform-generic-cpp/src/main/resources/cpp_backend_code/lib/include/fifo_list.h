#ifndef _FIFOLIST_H
#define _FIFOLIST_H

#include <vector>
#include <memory>
#include <initializer_list>
#include "fifo.h"



template<typename T>
class FifoList {
    std::vector<Fifo<T> *> _fifos;

public:
    template<typename... Args>
    FifoList(const Args... args) {
        for (auto a : {typename std::common_type<Args...>::type(args)...}) {
            _fifos.push_back(a);
        }
    }

    size_t space(){
        size_t min = SIZE_MAX;
        for (Fifo<T> *f : _fifos) {
            size_t s = f->space();
            if(s<min) {min = s;}
        }
        return min;
    }

    bool has_space(size_t tokens) {
        for (Fifo<T> *f : _fifos) {
            if (!f->has_space(tokens)) {
                return false;
            }
        }
        return true;
    }

    void write_one(T data) {
        for (Fifo<T> *f : _fifos) {
            f->write_one(data);
        }
    }

    void write(T *data, size_t tokens) {
        for (Fifo<T> *f : _fifos) {
            f->write(data, tokens);
        }
    }
};

#endif //_FIFOLIST_H
