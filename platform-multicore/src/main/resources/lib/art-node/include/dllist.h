/*
 * Copyright (c) Ericsson AB, 2013
 * Author: Patrik Persson (patrik.j.persson@ericsson.com)
 * All rights reserved.
 *
 * License terms:
 *
 * Redistribution and use in source and binary forms,
 * with or without modification, are permitted provided
 * that the following conditions are met:
 *     * Redistributions of source code must retain the above
 *       copyright notice, this list of conditions and the
 *       following disclaimer.
 *     * Redistributions in binary form must reproduce the
 *       above copyright notice, this list of conditions and
 *       the following disclaimer in the documentation and/or
 *       other materials provided with the distribution.
 *     * Neither the name of the copyright holder nor the names
 *       of its contributors may be used to endorse or promote
 *       products derived from this software without specific
 *       prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef DLLIST_INCLUSION_GUARD
#define DLLIST_INCLUSION_GUARD

#include <assert.h>
#include <pthread.h>

/* ========================================================================
 * A minimalistic doubly linked list. For convenience, you probably
 * want the 'dllist_element_t' member to be the first one in the
 * linked struct, so you can cast back and forth between list links
 * and struct pointers.
 *
 * This implies that such a struct cannot belong to more than one list
 * at a time.
 *
 * The list also includes a mutex for protecting against concurrent access.
 * ======================================================================== */

/*
 * A list and an element internally use the same type; the 'suc'
 * pointer then refers to the first element, and 'pre' to the last.
 * Hence, the list always contains at least one element (the list
 * head). This simplifies implementation considerably.
 */
typedef struct dllist_link_tag {
  struct dllist_link_tag  *suc, *pre;
} dllist_element_t;

typedef struct {
  dllist_element_t head_element;
  pthread_mutex_t  mutex;
} dllist_head_t;

/* ------------------------------------------------------------------------ */

/** Initialize a list. */
static inline void dllist_create(dllist_head_t *list) {
  list->head_element.suc = list->head_element.pre = &list->head_element;
  pthread_mutex_init(&list->mutex, NULL);
}

/* ------------------------------------------------------------------------ */

/**
 * Initialize a list element. Initially, the element does not belong to
 * any list.
 */
static inline void dllist_init_element(dllist_element_t *elem) {
  elem->suc = elem->pre = NULL;
}

/* ------------------------------------------------------------------------ */

/**
 * Append an element to the tail of a list.
 */
static inline void dllist_append(dllist_head_t *list, dllist_element_t *elem) {
  pthread_mutex_lock(&list->mutex);
  {
    assert(elem != NULL);
    assert(elem->suc == NULL);
    assert(elem->pre == NULL);

    elem->suc = &list->head_element;
    elem->pre = list->head_element.pre;
    elem->pre->suc = elem->suc->pre = elem;
  }
  pthread_mutex_unlock(&list->mutex);
}

/* ------------------------------------------------------------------------ */

/**
 * Remove the element from a list. Fails if the element does not belong to
 * a list.
 */
static inline void dllist_remove(dllist_head_t *list, dllist_element_t *elem) {
  pthread_mutex_lock(&list->mutex);
  {
    assert(elem != NULL);
    assert(elem->pre != NULL);
    assert(elem->suc != NULL);
    assert(elem->pre->suc == elem);
    assert(elem->suc->pre == elem);

    /* FIXME: we never check that the element actually belongs
     * to _this_ list. */

    elem->pre->suc = elem->suc;
    elem->suc->pre = elem->pre;
    elem->pre = elem->suc = NULL;
  }
  pthread_mutex_unlock(&list->mutex);
}

/* ------------------------------------------------------------------------ */

/**
 * @return the first element in the list, or NULL.
 */
#define dllist_first(LIST_PTR) \
  dllist_next((LIST_PTR),&(LIST_PTR)->head_element)

/* ------------------------------------------------------------------------ */

/**
 * @return the successor for element 'elem' in list 'list', or NULL.
 */
static inline dllist_element_t *
dllist_next(dllist_head_t *list, dllist_element_t *elem)
{
  dllist_element_t *next;

  pthread_mutex_lock(&list->mutex);
  {
    assert(elem != NULL);

    next = elem->suc;
    if (next == &list->head_element) {
      next = NULL;
    }
  }
  pthread_mutex_unlock(&list->mutex);

  return next;
}

/** Assumes the list is locked.
 * @return the successor for element 'elem' in list 'list', or NULL.
 */
static inline dllist_element_t *
dllist_next_locked(dllist_head_t *list, dllist_element_t *elem)
{
  dllist_element_t *next;
  
  {
    assert(elem != NULL);
    
    next = elem->suc;
    if (next == &list->head_element) {
      next = NULL;
    }
  }
  
  return next;
}

/**
 * @return the first element in the list, or NULL and locks the list.
 */
static inline dllist_element_t * dllist_first_lock(dllist_head_t * list) {
  pthread_mutex_lock(&list->mutex);
  return dllist_next_locked(list,&list->head_element);
}

/** unlocks the list.
 */
#define dllist_unlock(LIST_PTR) \
  pthread_mutex_unlock(&(LIST_PTR)->mutex)

#endif /* DLLIST_INCLUSION_GUARD */
