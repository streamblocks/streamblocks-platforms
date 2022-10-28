//
//  actors-coder.h
//  Calvin
//
//  Created by Per Persson on 2014-03-24.
//
//

#ifndef ACTORS_CODER_H
#define ACTORS_CODER_H

#include <cstddef>

/**
 * "Class" for coder. See struct declaration below for API.
 */
typedef struct ActorCoder ActorCoder;

/**
 * Known serialization formats
 */
typedef enum CODER_FORMAT {
    JSON_CODER,
    DEBUG_CODER
} ActorCoderFormat;

/**
 * Opaque state object type
 */
typedef void CoderState;

/**
 * Create a new coder instance that serializes to/from the format specified by fmt.
 * All memory management is internal to the coder except for decoding of strings,
 * see documentation for decode() method.
 */
ActorCoder *newCoder(ActorCoderFormat fmt);

/**
 * Destroy a coder instance and free its associated resources.
 */
void destroyCoder(ActorCoder *coder);

/******************************************************************************
 * ActorCoder is an object that encapsulates serialization of data so that
 *   1) there is a single API to use
 *   2) the actual serialization format is independent of the coding operations
 *   3) memory handling is automatic
 *
 * Example usage:
 *    To encode the following C data:
 *
 *    struct {int a, float b, int x[XSIZE]} foo;
 *
 *    ActorsCoder *coder = newCoder(JSON_CODER);
 *    CoderState *state = coder->init(coder);
 *    CoderState *strct = coder->encode_struct(coder, state, "foo");
 *    coder->encode(coder, strct, "a", &foo.a, "i");
 *    coder->encode(coder, strct, "b", &foo.b, "f");
 *    CoderState *array = coder->encode_array(coder, strct, "x");
 *    for (int i=0; i<XSIZE; i++) {
 *         coder->encode(coder, array, NULL, &foo.x[i], "i");
 *    }
 *    data = coder->data(coder);
 *    // send data ...
 *    destroyCoder(coder);
 *
 * To decode the above:
 *
 *    ActorsCoder *coder = newCoder(JSON_CODER);
 *    CoderState *state = coder->init(coder);
 *    coder->set_data(coder, data);
 *    CoderState *strct = coder->decode_struct(coder, state, "foo");
 *    coder->decode(coder, strct, "a", &foo.a, "i");
 *    coder->decode(coder, strct, "b", &foo.b, "f");
 *    CoderState *array = coder->decode_array(coder, strct, "x");
 *    for (int i=0; i<XSIZE; i++) {
 *        coder->decode(coder, array, NULL, &foo.x[i], "i");
 *    }
 *    destroyCoder(coder);
 *
 * See testsuite for more examples.
 *
 * ToDo:
 *   Add appropriate formats
 *   Elaborate formats (uint16_t, etc.)
 *   Memory handling for data
 *
 * Known bugs:
 *   Memory leak when using "detach" methods
 *   Data is not prepared for transport as-is
 *
 */
struct ActorCoder {
    /** 
     * The encoding/decoding format set for the coder
     */
    ActorCoderFormat type;
  
    /**
     * Initialize an instance
     */
    CoderState *(*init)(ActorCoder *_this);
  
    /** 
     * Encode a "basic" type, i.e. int, float, string, etc.
     * Parameters:
     *     state     - an opaque reference to the root container
     *     key       - a key to reference a specific value
     *     value_ref - a pointer to the value to store for 'key'
     *     type      - a type specifier for value ref, available types are:
     *                      "s" : C-string
     *                      "i" : int32
     *                      "f" : float
     *
     * Oddities: 
     *     When the state represents an array (see encode_array) parameter key
     *     is unused, and instead order of encode operations becomes important.
     *
     * FIXME: The types and type specifiers should be given some thought.
     */
    void (*encode)(ActorCoder *_this, CoderState *state, const char *key, void *value_ref, const char *type);
    
    /** 
     * Encode a struct (N.B. just the container, not the contents)
     * Parameters:
     *     _this      - the coder instance itself
     *     state     - an opaque reference to the current container
     *     key       - a key to reference a specific value
     * Returns:
     *     a reference to the the container created for the struct data
     */
    CoderState *(*encode_struct)(ActorCoder *_this, CoderState *state, const char *key);
    
    /**
     * Encode an array (N.B. just the container, not the contents)
     * Parameters:
     *     _this      - the coder instance itself
     *     state     - an opaque reference to the current container
     *     key       - a key to reference a specific value
     * Returns:
     *     a reference to the the container created for the array data
     */
    CoderState *(*encode_array)(ActorCoder *_this, CoderState *state, const char *key);
    
    /**
     * Not yet implemented
     */
    void (*encode_memory)(ActorCoder *_this, CoderState *state, const char *key, void *ptr, size_t length);
    
    /**
     * Decode a "basic" type, i.e. int, float, string, etc.
     * Parameters:
     *     _this      - the coder instance itself
     *     state     - an opaque reference to the current container
     *     key       - a key to reference a specific value
     *     value_ref - a pointer to the value to store for 'key'
     *     type      - a type specifier for value ref, see decode for details
     *
     * Oddities:
     *     When the state represents an array (see encode_array) parameter key
     *     is unused, and instead order of decode operations becomes important.
     *
     *     When a string is decoded, the caller takes ownership and is thus
     *     responsible for freeing it.
     */
    void (*decode)(ActorCoder *_this, CoderState *state, const char *key, void *value_ref, const char *type);
    
    /**
     * Decode a struct (N.B. just the container, not the contents)
     * Parameters:
     *     _this      - the coder instance itself
     *     state     - an opaque reference to the current container
     *     key       - the key referencing a specific struct
     * Returns:
     *     a reference to the the container corresponding to the given key
     */
    CoderState *(*decode_struct)(ActorCoder *_this, CoderState *state, const char *key);

    /**
     * Decode an array (N.B. just the container, not the contents)
     * Parameters:
     *     _this      - the coder instance itself
     *     state     - an opaque reference to the current container
     *     key       - the key referencing a specific array
     * Returns:
     *     a reference to the the container corresponding to the given key
     */
    CoderState *(*decode_array)(ActorCoder *_this, CoderState *state, const char *key);
    
    /**
     * Not implemented yet
     */
    void (*decode_memory)(ActorCoder *_this, CoderState *state, const char *key, void *ptr, std::size_t length);
  
    /**
     * Return an opaque object that contains the serialized data ready for transport
     * 
     * FIXME: Likely to change in details
     */
    void *(*data)(ActorCoder *_this);

    /**
     * Load serialized data for subsequential decoding
     * Parameters:
     *     _this    - the coder instance itself
     *     closure - data returned by encoder->data()
     * Returns:
     *     state   - an opaque reference to the root container
     */
    CoderState *(*set_data)(ActorCoder *_this, void *closure);

    /**
     * Return a human-readable description of the data held by the coder as a 
     * single string (no newlines anywhere in the string).
     */
    const char *(*_description)(ActorCoder *_this);
    
    /**
     * Private. Called by destroyCoder()
     */
    void (*destructor)(ActorCoder *_this);
    
};

#endif // ACTORS_CODER_H
