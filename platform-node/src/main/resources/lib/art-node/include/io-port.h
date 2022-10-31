#ifndef IO_PORT_H_INCLUDED
#define IO_PORT_H_INCLUDED

#include <stdint.h>

struct InputPort;
struct OutputPort;

struct tokenFn {
  char * (*serialize)(void *, char*);
  char * (*deserialize)(void **, char*, long);
  long (*size)(void *);
  int  (*free)(void *, int);
};

typedef struct tokenFn tokenFn;

typedef struct InputPort InputPort;
typedef struct OutputPort OutputPort;

#if 0
enum {
  T_DBL,
  T_I32,
  T_I16,
  T_I8,
  T_BOOL,
  T_REF
};

struct token_t {
  void *ptr;
  int type;
  union {
    double double_buf;
    int32_t int32_t_buf;
    int16_t int16_t_buf;
    int8_t int8_t_buf;
    int32_t bool_t_buf;
    void *ref_buf;
  } content;
};
#endif

unsigned int input_port_available(const InputPort *self);
unsigned int output_port_space_left(const OutputPort *self);

void input_port_peek(const InputPort *self, int pos, unsigned int token_Size, void *token);
void input_port_read(InputPort *self, unsigned int token_size, void *token);
void output_port_write(OutputPort *self, unsigned int token_size, const void *token);
void input_port_consume(InputPort *self, unsigned int token_size);

//#include "actors-rts.h"

void input_port_update_available(InputPort *self);
void input_port_update_drained_at(InputPort *self);
int input_port_has_producer(InputPort *self);
int input_port_update_tokens_consumed(InputPort *self);
int input_port_update_counter(InputPort *self);

int output_port_update_tokens_produced(OutputPort *self);
void output_port_update_full_at(OutputPort *self);

struct InputPort *input_port_new(void);
struct OutputPort *output_port_new(void);

struct InputPort *input_port_array_new(int number_of_ports);
struct OutputPort *output_port_array_new(int number_of_ports);
struct InputPort *input_port_array_get(InputPort *arr, int index);
struct OutputPort *output_port_array_get(OutputPort *arr, int index);
void output_port_array_free(OutputPort *arr);
void input_port_array_free(InputPort *arr);

void input_port_set_producer(InputPort *self, OutputPort *producer);
OutputPort *input_port_producer(InputPort *self);
void *output_port_buffer_start(OutputPort *self);
void output_port_setup_buffer(OutputPort *self, unsigned int capacity, unsigned int token_size);
void output_port_set_buffer_start(OutputPort *self, void *buffer_start);
void *output_port_buffer_end(OutputPort *self);
void *output_port_write_ptr(OutputPort *self);
const void *input_port_read_ptr(InputPort *self);
void input_port_set_buffer_start(InputPort *self, void *buffer_start);
void input_port_set_read_ptr(InputPort *self, void *read_ptr);
void output_port_set_capacity(OutputPort *self, int capacity);
void output_port_set_write_ptr(OutputPort *self, void *write_ptr);
void *output_port_buffer_end(OutputPort *self);
void output_port_set_buffer_end(OutputPort *self, void *buffer_end);
void input_port_set_buffer_end(InputPort *self, void *buffer_end);
void output_port_buffer_start_free(OutputPort *self);
int output_port_capacity(OutputPort *self);
void input_port_set_capacity(InputPort *self, int capacity);

void input_port_set_functions(InputPort *self, tokenFn *functions);
tokenFn *output_port_functions(OutputPort *self);
void output_port_set_functions(OutputPort *self, tokenFn *functions);

int output_port_available(OutputPort *self);
int output_port_tokens_produced(OutputPort *self);
void output_port_set_tokens_produced(OutputPort *self, int tokens_produced);
int input_port_tokens_consumed(InputPort *self);
void input_port_set_tokens_consumed(InputPort *self, int tokens_consumed);

void input_port_init_consumer(InputPort *self);
void output_port_init_consumer_list(OutputPort *self);
void output_port_remove_consumer(OutputPort *self, InputPort *input);
void output_port_add_consumer(OutputPort *self, InputPort *input);
void output_port_disconnect_consumers(OutputPort *self);
unsigned int output_port_max_available(OutputPort *self);

InputPort *output_port_first_consumer(OutputPort *self);

void output_port_input_port_connect(OutputPort *, InputPort *);
void output_port_input_port_disconnect(OutputPort *, InputPort *);
void output_port_set_available(OutputPort *, unsigned int);
void output_port_reset_read_ptr(OutputPort *, int tokens, int token_size);
void output_port_set_space_left(OutputPort *, unsigned int);
#endif
