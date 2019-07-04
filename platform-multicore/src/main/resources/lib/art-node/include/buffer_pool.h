#ifndef BUFFER_POOL_H
#define BUFFER_POOL_H

#include <ck_ring.h>
#include <stdio.h>
#include <stdlib.h>

/* @brief A buffer of bytes with count and id */
typedef struct counted_buffer_t {
    uint8_t *buffer;
    uint32_t count;
    uint32_t id;

}counted_buffer_t;
/* @brief a pool of buffer managed by two rings
 * This data structure implements a pool of buffers
 * using lock free rings. A ring contains the buffers that are filled
 * by the producing threads and the other contains the buffers that are free and
 * can be used by the producing threads.
 * A consumer thread can dequeue a buffer using the pop_full_buffer() methods
 * and after consuming all the tokens puts back the empty buffer using the
 * push_free_buffer() method.
 * A producer, first acquires the buffer using the pop_free_buffer() method 
 * and after filling it up with tokens puts it back to the full ring
 * using push_full_buffer().
 */ 
typedef struct buffer_pool_t {
    ck_ring_buffer_t *full_pool; // array of full buffer pointers
    ck_ring_buffer_t *free_pool; // array of free buffer pointers
    ck_ring_t full_ring;        // the ring controlling the full buffers
    ck_ring_t free_ring;        // the ring controlling the empty buffers
    counted_buffer_t *buffers;  // array of buffers
    
    unsigned int size;          // The size of the ring
    unsigned int capacity;      // The capacity of each buffer in bytes
}buffer_pool_t;

void buffer_pool_init(buffer_pool_t *bufferPool, unsigned int size, unsigned int capacity);

void buffer_pool_destroy(buffer_pool_t *bufferPool);

void pop_free_buffer(buffer_pool_t *bufferPool, counted_buffer_t **buffer);

void push_free_buffer(buffer_pool_t *bufferPool, counted_buffer_t* buffer);

void pop_full_buffer(buffer_pool_t *bufferPool, counted_buffer_t **buffer);

void push_full_buffer(buffer_pool_t *bufferPool, counted_buffer_t* buffer);

bool try_pop_full_buffer(buffer_pool_t *bufferPool, counted_buffer_t **buffer);
bool try_pop_free_buffer(buffer_pool_t *bufferPool, counted_buffer_t **buffer);

unsigned int free_buffers_count(buffer_pool_t *bufferPool);
unsigned int full_buffers_count(buffer_pool_t *bufferPool);



#endif
