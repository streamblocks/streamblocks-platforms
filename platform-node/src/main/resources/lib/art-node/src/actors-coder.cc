//
//  actors-coder.c
//  Calvin
//
//  Created by Per Persson on 2014-03-24.
//
//

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "actors-coder.h"
#include "cJSON.h"
#include <stdint.h>

/* ========================================================================= */

typedef struct ActorJSONCoder {
    ActorCoder baseCoder;
    cJSON *root;
    cJSON *garbage;
    char *descr;
} ActorJSONCoder;

static CoderState *json_init(ActorCoder *_this)
{
    ActorJSONCoder *coder = (ActorJSONCoder *)_this;

    return (void *)(coder->root);
}

// Utility function to add object to container.
// Returns the object passed as parameter (for chaining)
static cJSON  *_store_object(cJSON *container, cJSON *object, const char *key)
{
    if (cJSON_Array == container->type) {
        cJSON_AddItemToArray(container, object);
    } else {
        cJSON_AddItemToObject(container, key, object);
    }
    
    return object;
}

static CoderState *json_encode_struct(ActorCoder *_this, CoderState *state, const char *key)
{
    return (void *)_store_object((cJSON *)state, cJSON_CreateObject(), key);
}

static CoderState *json_encode_array(ActorCoder *_this, CoderState *state, const char *key)
{
    return (void *)_store_object((cJSON *)state, cJSON_CreateArray(), key);
}

static void json_encode(ActorCoder *_this, CoderState *state, const char *key, void *value_ref, const char *type)
{
    // Parameter conversion
    cJSON *value = NULL;
    switch (type[0]) {
        case 's': // C string
            value = cJSON_CreateString(*(const char **)value_ref);
            break;
        case 'b': // boolean
          value = cJSON_CreateBool((int)*((int32_t *)value_ref));
          break;
        case 'i': // C int 32 bit
            value = cJSON_CreateNumber((double)*((int32_t *)value_ref));
            break;
        case 'I': // C unsigned int 32 bit
            value = cJSON_CreateNumber((double)*((uint32_t *)value_ref));
            break;
        case 'h': // C int 16 bit
            value = cJSON_CreateNumber((double)*((int16_t *)value_ref));
            break;
        case 'H': // C unsigned int 16 bit
            value = cJSON_CreateNumber((double)*((uint16_t *)value_ref));
            break;
        case 'c': // C int 8 bit
            value = cJSON_CreateNumber((double)*((int8_t *)value_ref));
            break;
        case 'C': // C unsigned int 8 bit
            value = cJSON_CreateNumber((double)*((uint8_t *)value_ref));
            break;
        case 'd': // C double
          value = cJSON_CreateNumber((double)*((double *)value_ref));
          break;
        case 'f': // C float
            value = cJSON_CreateNumber((double)*((float *)value_ref));
            break;
        
        default:
            fprintf(stderr, "Error: Unknown type specifier '%c'\n", type[0]);
            break;
    }
    (void)_store_object((cJSON *)state, value, key);
}

static void json_encode_memory(ActorCoder *_this, CoderState *state, const char *key, void *ptr, size_t length)
{
    cJSON_AddItemToObject((cJSON *)state, key, cJSON_CreateString("FIXME: Base64 encoded data ..."));
}

// Utility function to detach an object from a container.
// Adds object to garbage list for later collection.
// Returns the detached object
static cJSON *_detach_and_track_object(ActorJSONCoder *_this, cJSON *container, const char *key)
{
    cJSON *object = NULL;
    if (cJSON_Array == container->type) {
        object = cJSON_DetachItemFromArray(container, 0);
    } else {
        object = cJSON_DetachItemFromObject(container, key);
    }
    cJSON_AddItemToArray(_this->garbage, object);

    return object;
}

static void json_decode(ActorCoder *_this, CoderState *state, const char *key, void *value_ref, const char *type)
{
    cJSON *value = _detach_and_track_object((ActorJSONCoder *)_this, (cJSON *)state, key);
  
    switch (type[0]) {
        case 's': // C string
        {
            *(char **)value_ref = strdup(value->valuestring);
        }
        break;
        case 'b': // boolean
        {
          *(int32_t *)value_ref = (int32_t)(value->type==cJSON_True);
        }
        break;
        case 'i': // C int 32 bit
        {
            *(int32_t *)value_ref = (int32_t)(value->valueint);
        }
        break;
        case 'I': // C unsigned int 32 bit
        {
          *(uint32_t *)value_ref = (uint32_t)(value->valueint);
        }
        break;
        case 'h': // C int 16 bit
        {
          *(int16_t *)value_ref = (int16_t)(value->valueint);
        }
        break;
        case 'H': // C unsigned int 16 bit
        {
          *(uint16_t *)value_ref = (uint16_t)(value->valueint);
        }
        break;
        case 'c': // C int 8 bit
        {
          *(int8_t *)value_ref = (int8_t)(value->valueint);
        }
        break;
        case 'C': // C unsigned int 8 bit
        {
          *(uint8_t *)value_ref = (uint8_t)(value->valueint);
        }
        break;
        case 'd': // C double
        {
          *(double *)value_ref = (double)(value->valuedouble);
        }
        break;
        case 'f': // C float
        {
            *(float *)value_ref = (float)(value->valuedouble);
        }
        break;
        
        default:
            fprintf(stderr, "Error: Unknown type specifier '%c'\n", type[0]);
            break;
    }
}

static CoderState *json_decode_struct(ActorCoder *_this, CoderState *state, const char *key)
{
    cJSON *object = _detach_and_track_object((ActorJSONCoder *)_this, (cJSON *)state, key);
    if (cJSON_Object != object->type) {
        fprintf(stderr, "Error: Requested object NOT a struct.");
    }
    return (void *)object;
}

static CoderState *json_decode_array(ActorCoder *_this, CoderState *state, const char *key)
{
    cJSON *object = _detach_and_track_object((ActorJSONCoder *)_this, (cJSON *)state, key);
    if (cJSON_Array != object->type) {
        fprintf(stderr, "Error: Requested object NOT an array.");
    }
    return (void *)object;
}

void json_decode_memory(ActorCoder *_this, CoderState *state, const char *key, void *ptr, size_t length)
{
    // FIXME:
}

static const char *json_string_rep(ActorCoder *_this)
{
    ActorJSONCoder *coder = (ActorJSONCoder *)_this;
    free(coder->descr);
    coder->descr = cJSON_PrintUnformatted(coder->root);

    return coder->descr;
}

void *json_data(ActorCoder *_this)
{
    return (void *)json_string_rep(_this);
}

CoderState *json_set_data(ActorCoder *_this, void *closure)
{
    // If closure is NOT a C string, bad things will happen.
    ActorJSONCoder *coder = (ActorJSONCoder *)_this;
    // FIXME: What to do if parse fails?
    cJSON *data = cJSON_Parse(static_cast<const char*>(closure));
    if (data) {
        cJSON_Delete(coder->root);
        coder->root = data;
    }
    return coder->root;
}

static void json_destructor(ActorCoder *_this)
{
    ActorJSONCoder *coder = (ActorJSONCoder *)_this;
    cJSON_Delete(coder->root);
    cJSON_Delete(coder->garbage);
    free(coder->descr);
}

ActorCoder *newJSONCoder(void)
{
    ActorJSONCoder *coder = static_cast<ActorJSONCoder*>(malloc(sizeof(ActorJSONCoder)));
    
    coder->root = cJSON_CreateObject();
    coder->descr = NULL;
    
    coder->baseCoder.init = json_init;
    coder->baseCoder.encode = json_encode;
    coder->baseCoder.encode_struct = json_encode_struct;
    coder->baseCoder.encode_array = json_encode_array;
    coder->baseCoder.encode_memory = json_encode_memory;
    
    coder->baseCoder.decode = json_decode;
    coder->baseCoder.decode_struct = json_decode_struct;
    coder->baseCoder.decode_array = json_decode_array;
    coder->baseCoder.decode_memory = json_decode_memory;
  
    coder->baseCoder.data = json_data;
    coder->baseCoder.set_data = json_set_data;

    coder->baseCoder.destructor = json_destructor;
    coder->baseCoder._description = json_string_rep;
    
    // Store consumed objects so that we won't leak them
    coder->garbage = cJSON_CreateArray();
    
    return (ActorCoder *)coder;
}


/* ========================================================================= */

#define DEBUG_BUFFER_SIZE 4096

typedef struct ActorDebugCoder {
    ActorCoder baseCoder;
} ActorDebugCoder;

CoderState *debug_init(ActorCoder *_this)
{
    return NULL;
}

void debug_encode(ActorCoder *_this, CoderState *state, const char *key, void *value_ref, const char *type)
{
}

CoderState *debug_encode_struct(ActorCoder *_this, CoderState *state, const char *key)
{
    return NULL;
}

CoderState *debug_encode_array(ActorCoder *_this, CoderState *state, const char *key)
{
    return NULL;
}

void debug_encode_memory(ActorCoder *_this, CoderState *state, const char *key, void *ptr, size_t length)
{
}

void debug_decode(ActorCoder *_this, CoderState *state, const char *key, void *value_ref, const char *type)
{
}

void *debug_data(ActorCoder *_this)
{
    return NULL;
}

CoderState *debug_set_data(ActorCoder *_this, void *closure)
{
    return NULL;
}

static const char *debug_string_rep(ActorCoder *_this)
{
    return "DEBUG_CODER";
}

ActorCoder *newDebugCoder(void)
{
    ActorDebugCoder *coder = static_cast<ActorDebugCoder*>(malloc(sizeof(ActorDebugCoder)));
    
    coder->baseCoder.init = debug_init;
    coder->baseCoder.encode = debug_encode;
    coder->baseCoder.encode_struct = debug_encode_struct;
    coder->baseCoder.encode_array = debug_encode_array;
    coder->baseCoder.encode_memory = debug_encode_memory;
    
    coder->baseCoder.data = debug_data;
    coder->baseCoder.set_data = debug_set_data;
    
    coder->baseCoder.decode = debug_decode;
    
    coder->baseCoder.destructor = NULL;
    coder->baseCoder._description = debug_string_rep;
    
    return (ActorCoder *)coder;
}

/* ========================================================================= */

ActorCoder *newCoder(ActorCoderFormat fmt)
{
    ActorCoder *coder = NULL;
    
    switch (fmt) {
        case JSON_CODER:
            coder = newJSONCoder();
            break;
        case DEBUG_CODER: // Fallthrough
        default:
            coder = newDebugCoder();
            fmt = DEBUG_CODER;
            break;
    }
    coder->type = fmt;
    return coder;
}


void destroyCoder(ActorCoder *coder)
{
    if (coder->destructor) {
        coder->destructor(coder);
    }
    free(coder);
}



