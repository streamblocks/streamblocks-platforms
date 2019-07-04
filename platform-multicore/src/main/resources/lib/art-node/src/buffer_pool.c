#include "buffer_pool.h"

void buffer_pool_init(buffer_pool_t *bufferPool, unsigned int size, unsigned int capacity)
{
    ck_ring_init(&bufferPool->free_ring, size);
    ck_ring_init(&bufferPool->full_ring, size);
    
    bufferPool->free_pool = (ck_ring_buffer_t *)malloc(sizeof(ck_ring_buffer_t) * size);
    bufferPool->full_pool = (ck_ring_buffer_t *)malloc(sizeof(ck_ring_buffer_t) * size);

    
    bufferPool->buffers = (counted_buffer_t *) calloc(size - 1, sizeof(counted_buffer_t));
    

    for (int i = 0; i < size - 1; i ++) {
        bufferPool->buffers[i].buffer = (uint8_t *) malloc(sizeof(uint8_t) * capacity);
        bufferPool->buffers[i].count = 0;
        bufferPool->buffers[i].id = i;
        
    }
    
    

    bufferPool->size = size;
    bufferPool->capacity = capacity;

    for (int i = 0; i < size - 1; i ++) {
        ck_ring_enqueue_mpmc(&bufferPool->free_ring, 
            bufferPool->free_pool, 
            &bufferPool->buffers[i]);
    }

    
}


void buffer_pool_destroy(buffer_pool_t *bufferPool) {
    // Fill in later
}

void pop_free_buffer(buffer_pool_t *bufferPool, counted_buffer_t **buffer) {
    while (!ck_ring_dequeue_mpmc(&bufferPool->free_ring, bufferPool->free_pool, buffer));
}

void push_free_buffer(buffer_pool_t *bufferPool, counted_buffer_t* buffer) {
    while (!ck_ring_enqueue_mpmc(&bufferPool->free_ring, bufferPool->free_pool, buffer));
}

inline void pop_full_buffer(buffer_pool_t *bufferPool, counted_buffer_t **buffer) {
    while (!ck_ring_dequeue_mpmc(&bufferPool->full_ring, bufferPool->full_pool, buffer));

}
bool try_pop_full_buffer(buffer_pool_t *bufferPool, counted_buffer_t **buffer) {
    return ck_ring_dequeue_mpmc(&bufferPool->full_ring, bufferPool->full_pool, buffer);
}

bool try_pop_free_buffer(buffer_pool_t *bufferPool, counted_buffer_t **buffer) {
    return ck_ring_dequeue_mpmc(&bufferPool->free_ring, bufferPool->free_pool, buffer);
}
void push_full_buffer(buffer_pool_t *bufferPool, counted_buffer_t* buffer) {
    while (!ck_ring_enqueue_mpmc(&bufferPool->full_ring, bufferPool->full_pool, buffer));
}
unsigned int free_buffers_count(buffer_pool_t *bufferPool) {
    return ck_ring_size(&bufferPool->free_ring);
}
unsigned int full_buffers_count(buffer_pool_t *bufferPool) {
    return ck_ring_size(&bufferPool->full_ring);
}
