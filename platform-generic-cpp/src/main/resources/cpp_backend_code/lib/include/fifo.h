#ifndef _FIFO_H
#define _FIFO_H

#include <stdint.h>

template<typename T>
class Fifo {
public:
    Fifo(size_t size = 512);

    ~Fifo();

    bool has_data(size_t tokens) {
        return (wr - rd) >= tokens;
    }

    size_t space() {
        size_t min = SIZE_MAX;
        {
            size_t s = size - (wr - rd);
            if (s < min) { min = s; }
        }
        return min;
    }

    bool has_space(size_t tokens) {
        if (size - (wr - rd) < tokens) {
            return false;
        }
        return true;
    }

    T peek_first();

    void peek(size_t offset, size_t tokens, T *result);

    void consume(size_t tokens){
        rd += tokens;
    }

    void write_one(T data);

    void write(T * data, size_t tokens);


private:
    T *buffer;
    size_t rd;
    size_t wr;
    size_t size;
};

template<typename T>
Fifo<T>::Fifo(size_t s) :
        buffer(new T[s]), rd(0), wr(0), size(s) {
}

template<typename T>
Fifo<T>::~Fifo() {
    delete[] buffer;
}

template<typename T>
T Fifo<T>::peek_first() {
    return buffer[rd % size];
}

template<typename T>
void Fifo<T>::peek(size_t offset, size_t tokens, T *result) {
    T *res = result;
    for (size_t i = 0; i < tokens; i++) {
        res[i] = buffer[(rd + i + offset) % size];
    }
}

template<typename T>
void Fifo<T>::write_one(T data) {
    buffer[wr % size] = data;
    wr++;
}

template<typename T>
void Fifo<T>::write(T *data, size_t tokens) {
    for(size_t i = 0; i < tokens; i++){
        buffer[wr % size] = data[i];
        wr++;
    }
}


#endif //_FIFO_H
