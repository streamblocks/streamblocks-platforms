#ifndef SLLIST_H_INCLUDED
#define SLLIST_H_INCLUDED

#include <assert.h>
#include <pthread.h>

typedef struct slist_node {
  struct slist_node *next;
} slist_node;

typedef struct slist {
  slist_node *first;
  pthread_mutex_t lock;
  int len;
} slist;

static inline int slist_len(slist *list) { return list->len; }

static inline void slist_create(slist *list) {
  list->first = NULL;
  pthread_mutex_init(&list->lock, NULL);
}

static inline void slist_init_node(slist_node *node) { node->next = NULL; }

static inline void slist_append(slist *list, slist_node *node) {
  slist_node *idx = (slist_node *)list;
  pthread_mutex_lock(&list->lock);
  {
    assert(node != NULL);
    node->next = NULL;

    while (idx->next != NULL) {
      idx = idx->next;
    }
    idx->next = node;
    list->len++;
  }
  pthread_mutex_unlock(&list->lock);
}

static inline void slist_remove(slist *list, slist_node *node) {
  slist_node *idx = (slist_node *)list;
  pthread_mutex_lock(&list->lock);
  {
    assert(node != NULL);
    while (idx->next != NULL && idx->next != node) {
      idx = idx->next;
    }
    if (idx->next == NULL) {
      /* node not member of list */
    } else {
      idx->next = idx->next->next;
      list->len--;
    }
  }
  pthread_mutex_unlock(&list->lock);
}

static inline slist_node *slist_first(slist *list) { return list->first; }

static inline slist_node *slist_next(slist *list, slist_node *node) {
  slist_node *next = (slist_node *)list;
  pthread_mutex_lock(&list->lock);
  {
    assert(node != NULL);
    next = node->next;
  }
  pthread_mutex_unlock(&list->lock);
  return next;
}

#endif
