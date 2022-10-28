#include "io-port.h"
#include "logging.h"
#include "slist.h"
#include <cstring>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>

/*
 * Circular buffer (used by FIFO operations)
 */
//template<typename T> 
struct circ_buffer {
  void *bufferStart;   // Start of cyclic buffer
  void *bufferEnd;     // One past end of cyclic buffer
  const void *readPtr; // position in cyclic buffer
  void *writePtr;
  unsigned spaceLeft;
  unsigned available; // number of available tokens
};

/*
 * InputPort
 * Extends LocalInputPort, computes available tokens in pre-fire step,
 * updates tokensConsumed in post-fire step
 */
struct InputPort {
  slist_node asConsumer;

  OutputPort *producer;

  struct circ_buffer *localInputPort;

  tokenFn functions; // functions to handle structured tokens
};

/*
 * OutputPort
 * Extends LocalOutputPort: computes spaceLeft in pre-fire step,
 * updates tokensProduced in post-fire step
 */
struct OutputPort {
  struct circ_buffer localOutputPort;
  unsigned capacity;       // capacity of buffer (in tokens)
  unsigned tokensProduced; // number of tokens produced
  unsigned fullAt;         // tokensProduced when FIFO is full
  unsigned tokensConsumed; // number of tokens consumed
  unsigned drainedAt;      // point at which all tokensConsumed

  tokenFn functions; // functions to handle structured tokens

  slist consumers;
};

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

#define LOCK                                                                   \
  do {                                                                         \
    pthread_mutex_lock(&lock);                                                 \
  } while (0)

#define UNLOCK                                                                 \
  do {                                                                         \
    pthread_mutex_unlock(&lock);                                               \
  } while (0)

void input_port_update_available(InputPort *self) {
  unsigned int available;
  available = self->producer->tokensProduced - self->producer->tokensConsumed;
  self->localInputPort->available = available;
}

void input_port_update_drained_at(InputPort *self) {
  self->producer->drainedAt =
      self->producer->tokensConsumed + self->localInputPort->available;
}

int input_port_has_producer(InputPort *self) {
  void *producer;
  LOCK;
  producer = self->producer;
  UNLOCK;
  return producer != NULL;
}

int input_port_update_counter(InputPort *self) {
  unsigned int counter;
  int fired = 0;

  LOCK;
  if (self != NULL && self->localInputPort != NULL) {
    counter = self->producer->drainedAt - self->localInputPort->available;
    if (counter != self->producer->tokensConsumed) {
      self->producer->tokensConsumed = counter;
      fired = 1;
    }
  }
  UNLOCK;

  return fired;
}

int output_port_update_tokens_produced(OutputPort *self) {
  unsigned int counter;
  int fired = 0;
  LOCK;
  counter = self->fullAt - self->localOutputPort.spaceLeft;

  if (counter != self->tokensProduced) {
    self->tokensProduced = counter;
    fired = 1;
  }
  UNLOCK;
  return fired;
}

void output_port_update_full_at(OutputPort *self) {
  unsigned int max_available;
  unsigned int space_left;
  LOCK;
  max_available = output_port_max_available(self);
  space_left = self->capacity - max_available;
  self->localOutputPort.spaceLeft = space_left;
  // fullAt: tokensProduced counter when FIFO is full
  self->fullAt = self->tokensProduced + space_left;
  UNLOCK;
}

unsigned int input_port_available(const InputPort *self) {
  unsigned int available = 0;
  LOCK;
  if (self != NULL && self->localInputPort != NULL) {
    available = self->localInputPort->available;
  }
  UNLOCK;
  return available;
}

unsigned int output_port_space_left(const OutputPort *self) {
  return self->localOutputPort.spaceLeft;
}

struct InputPort *input_port_new(void) {
  return input_port_array_new(1);
}

struct OutputPort *output_port_new() {
  return output_port_array_new(1);
}

struct OutputPort *output_port_array_new(int number_of_ports) {
  return static_cast<OutputPort *>(
      calloc(number_of_ports, sizeof(struct OutputPort)));
}

void output_port_array_free(OutputPort *arr) { free(arr); }

void input_port_array_free(InputPort *arr) { free(arr); }

struct InputPort *input_port_array_new(int number_of_ports) {
  return static_cast<InputPort *>(
      calloc(number_of_ports, sizeof(struct InputPort)));
}

struct InputPort *input_port_array_get(InputPort *arr, int index) {
  return &arr[index];
}

struct OutputPort *output_port_array_get(OutputPort *arr, int index) {
  return &arr[index];
}

void input_port_set_producer(InputPort *self, OutputPort *producer) {
  LOCK;
  self->producer = producer;
  UNLOCK;
}

void *output_port_buffer_start(OutputPort *self) {
  return self->localOutputPort.bufferStart;
}
#if 0
void
input_port_set_buffer_start(InputPort *self, void *buffer_start)
{
  self->localInputPort->bufferStart = buffer_start;
}
#endif
void input_port_set_read_ptr(InputPort *self, void *read_ptr) {
  self->localInputPort->readPtr = read_ptr;
}

void output_port_set_write_ptr(OutputPort *self, void *write_ptr) {
  self->localOutputPort.writePtr = write_ptr;
}

void *output_port_write_ptr(OutputPort *self) {
  return self->localOutputPort.writePtr;
}

const void *input_port_read_ptr(InputPort *self) {
  return self->localInputPort->readPtr;
}

void output_port_set_buffer_end(OutputPort *self, void *buffer_end) {
  self->localOutputPort.bufferEnd = buffer_end;
}

void output_port_set_capacity(OutputPort *self, int capacity) {
  self->capacity = capacity;
}

void *output_port_buffer_end(OutputPort *self) {
  return self->localOutputPort.bufferEnd;
}

void output_port_setup_buffer(OutputPort *self, unsigned int capacity,
                              unsigned int token_size) {
  self->capacity = capacity;
  self->localOutputPort.bufferStart = calloc(capacity, token_size);
  self->localOutputPort.bufferEnd =
      ((char *)self->localOutputPort.bufferStart) + capacity * token_size;
  self->localOutputPort.writePtr = self->localOutputPort.bufferStart;
  self->localOutputPort.readPtr = self->localOutputPort.bufferStart;
  self->localOutputPort.spaceLeft = 0;
  self->localOutputPort.available = 0;
}

void input_port_set_buffer_end(InputPort *self, void *buffer_end) {
  self->localInputPort->bufferEnd = buffer_end;
}

int output_port_capacity(OutputPort *self) { return self->capacity; }

void input_port_set_functions(InputPort *self, tokenFn *functions) {
  LOCK;
  if (functions != NULL) {
    memcpy(&self->functions, functions, sizeof self->functions);
  } else {
    memset(&self->functions, 0, sizeof self->functions);
  }
  UNLOCK;
}

void output_port_set_functions(OutputPort *self, tokenFn *functions) {
  LOCK;
  if (functions != NULL) {
    memcpy(&self->functions, functions, sizeof self->functions);
  } else {
    memset(&self->functions, 0, sizeof self->functions);
  }
  UNLOCK;
}
#if 0
void
output_port_set_buffer_start(OutputPort *self, void *buffer_start)
{
  self->localOutputPort.bufferStart = buffer_start;
}
#endif
OutputPort *input_port_producer(InputPort *self) { return self->producer; }

void output_port_buffer_start_free(OutputPort *self) {
  free(self->localOutputPort.bufferStart);
}

tokenFn *output_port_functions(OutputPort *self) { return &self->functions; }

int output_port_tokens_produced(OutputPort *self) {
  return self->tokensProduced;
}

void output_port_set_tokens_produced(OutputPort *self, int tokens_produced) {
  self->tokensProduced = tokens_produced;
}

int input_port_tokens_consumed(InputPort *self) {
  return self->producer->tokensConsumed;
}

void input_port_set_tokens_consumed(InputPort *self, int tokens_consumed) {
  self->producer->tokensConsumed = tokens_consumed;
}

void input_port_init_consumer(InputPort *self) {
  slist_init_node(&self->asConsumer);
}

void output_port_add_consumer(OutputPort *self, InputPort *input) {
  slist_append(&self->consumers, &input->asConsumer);
}

void output_port_init_consumer_list(OutputPort *self) {
  slist_create(&self->consumers);
}

void output_port_remove_consumer(OutputPort *self, InputPort *input) {
  slist_remove(&self->consumers, &input->asConsumer);
}

void output_port_disconnect_consumers(OutputPort *self) {
  slist_node *elem = slist_first(&self->consumers);

  while (elem != NULL) {
    input_port_set_producer((InputPort *)elem, NULL);
    output_port_input_port_disconnect(self, (InputPort *)elem);
    elem = slist_next(&self->consumers, elem);
  }
}

InputPort *output_port_first_consumer(OutputPort *self) {
  slist_node *elem = slist_first(&self->consumers);
  return (InputPort *)elem;
}

unsigned int output_port_max_available(OutputPort *self) {
  unsigned int max_available = 0;
  slist_node *elem = slist_first(&self->consumers);

  while (elem != NULL) {
    unsigned int available = output_port_tokens_produced(self) -
                             input_port_tokens_consumed((InputPort *)elem);
    if (available > max_available) {
      max_available = available;
    }
    elem = slist_next(&self->consumers, elem);
  }
  return max_available;
}

void output_port_write(OutputPort *self, unsigned int token_size,
                       const void *token) {
  memcpy(self->localOutputPort.writePtr, token, token_size);

  self->localOutputPort.writePtr =
      ((char *)self->localOutputPort.writePtr) + token_size;

  if (self->localOutputPort.writePtr >= self->localOutputPort.bufferEnd) {
    self->localOutputPort.writePtr = self->localOutputPort.bufferStart;
  }
  self->localOutputPort.spaceLeft--;
  self->localOutputPort.available++;
  assert(self->localOutputPort.spaceLeft >= 0);
}

void input_port_read(InputPort *self, unsigned int token_size, void *token) {
  assert(token != NULL);
  assert(self->producer != NULL);
  memcpy(token, self->localInputPort->readPtr, token_size);

  self->localInputPort->readPtr =
      ((char *)self->localInputPort->readPtr) + token_size;

  if (self->localInputPort->readPtr >= self->localInputPort->bufferEnd) {
    self->localInputPort->readPtr = self->localInputPort->bufferStart;
  }
  self->localInputPort->available--;
  self->localInputPort->spaceLeft++;
  assert(self->localInputPort->available >= 0);
}

void input_port_consume(InputPort *self){
  if (self->localInputPort->readPtr >= self->localInputPort->bufferEnd) {
    self->localInputPort->readPtr = self->localInputPort->bufferStart;
  }
  self->localInputPort->available--;
  self->localInputPort->spaceLeft++;
  assert(self->localInputPort->available >= 0);
}

void input_port_peek(const InputPort *self, int pos, unsigned int token_size,
                     void *token) {
  int i;
  const void *idx;

  for (i = 0, idx = self->localInputPort->readPtr; i < pos;
       ++i, idx = ((const char *)idx) + token_size) {
    if (idx == self->localInputPort->bufferEnd) {
      idx = self->localInputPort->bufferStart;
    }
  }

  memcpy(token, idx, token_size);
}

void output_port_input_port_connect(OutputPort *producer, InputPort *consumer) {
  m_message("connecting ports");
  input_port_set_producer(consumer, producer);

  consumer->localInputPort = &producer->localOutputPort;
  input_port_set_functions(consumer, output_port_functions(producer));
  input_port_init_consumer(consumer);
  output_port_add_consumer(producer, consumer);
}

void output_port_input_port_disconnect(OutputPort *producer,
                                       InputPort *consumer) {
  m_message("disconnecting ports");
  input_port_set_producer(consumer, NULL);

  LOCK;
  consumer->localInputPort = NULL;
  UNLOCK;
  input_port_set_functions(consumer, NULL);
  output_port_remove_consumer(producer, consumer);
}

void output_port_set_available(OutputPort *self, unsigned int available) {
  self->localOutputPort.available = available;
}

void output_port_reset_read_ptr(OutputPort *self, int tokens, int token_size) {
  int idx;
  self->localOutputPort.readPtr = ((char *)self->localOutputPort.writePtr) -
                                  self->localOutputPort.available * token_size;
  ;
  for (idx = 0; idx < tokens; ++idx) {
    self->localOutputPort.readPtr =
        ((char *)self->localOutputPort.readPtr) - token_size;
    if (self->localOutputPort.readPtr == self->localOutputPort.bufferStart) {
      self->localOutputPort.readPtr = self->localOutputPort.bufferEnd;
    }
  }
}

void output_port_set_space_left(OutputPort *self, unsigned int space_left) {
  self->localOutputPort.spaceLeft = space_left;
}
