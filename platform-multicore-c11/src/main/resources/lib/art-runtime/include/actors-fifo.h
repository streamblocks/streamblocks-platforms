#ifdef REF
#undef FIFO_NAME
#define FIFO_NAME(f) f##_##ref
#define FIFO_TYPE void*
#else
#define FIFO_NAME_3(f, t) f##_##t
#define FIFO_NAME_2(f, t) FIFO_NAME_3(f, t)
#define FIFO_NAME(f) FIFO_NAME_2(f, FIFO_TYPE)
#endif

static inline unsigned FIFO_NAME(pinAvailIn)(const LocalInputPort *p) {
    return p->available;
}

static inline unsigned FIFO_NAME(pinAvailOut)(const LocalOutputPort *p) {
    return p->available;
}

static inline void FIFO_NAME(pinWrite)(LocalOutputPort *p, FIFO_TYPE token) {
    assert(FIFO_NAME(pinAvailOut)(p) > 0);
    ((FIFO_TYPE *) p->buffer)[p->pos] = token;
    p->pos++;
    if (p->pos >= 0) { p->pos = -(p->capacity); }
    p->available--;
}

static inline void FIFO_NAME(pinWriteRepeat)(LocalOutputPort *p,
                                             FIFO_TYPE *buf,
                                             int n) {

    assert(FIFO_NAME(pinAvailOut)(p) >= n);
    p->available -= n;
    if (p->pos + n >= 0) {
        // Buffer wrap
        memcpy(&((FIFO_TYPE *) p->buffer)[p->pos], buf,
               -(p->pos * sizeof(FIFO_TYPE)));
        buf += -(p->pos);
        n -= -(p->pos);
        p->pos = -(p->capacity);
    }
    if (n) {
        memcpy(&((FIFO_TYPE *) p->buffer)[p->pos], buf,
               n * sizeof(FIFO_TYPE));
        p->pos += n;
    }
}

static inline FIFO_TYPE FIFO_NAME(pinRead)(LocalInputPort *p) {
    FIFO_TYPE result;

    assert(FIFO_NAME(pinAvailIn)(p) > 0);
    result = ((FIFO_TYPE *) p->buffer)[p->pos];
    p->pos++;
    if (p->pos >= 0) { p->pos = -(p->capacity); }
    p->available--;
    return result;
}

static inline void FIFO_NAME(pinReadRepeat)(LocalInputPort *p,
                                            FIFO_TYPE *buf,
                                            int n) {

    assert(FIFO_NAME(pinAvailIn)(p) >= n);
    p->available -= n;
    if (p->pos + n >= 0) {
        // Buffer wrap
        memcpy(buf, &((FIFO_TYPE *) p->buffer)[p->pos],
               -(p->pos * sizeof(FIFO_TYPE)));
        buf += -(p->pos);
        n -= -(p->pos);
        p->pos = -(p->capacity);
    }
    if (n) {
        memcpy(buf, &((FIFO_TYPE *) p->buffer)[p->pos],
               n * sizeof(FIFO_TYPE));
        p->pos += n;
    }
}

static inline void FIFO_NAME(pinConsume)(LocalInputPort *p) {
    assert(FIFO_NAME(pinAvailIn)(p) > 0);
    p->pos++;
    if (p->pos >= 0) { p->pos = -(p->capacity); }
    p->available--;
}

static inline void FIFO_NAME(pinConsumeRepeat)(LocalInputPort *p,
                                               int n) {
    assert(FIFO_NAME(pinAvailIn)(p) >= n);
    p->available -= n;
    if (p->pos + n >= 0) {
        // Buffer wrap
        n -= -(p->pos);
        p->pos = -(p->capacity);
    }
    if (n) {
        p->pos += n;
    }
}

static inline void FIFO_NAME(pinPeekRepeat)(LocalInputPort *p,
                                            FIFO_TYPE *buf,
                                            int n) {
    assert(FIFO_NAME(pinAvailIn)(p) >= n);
    int tmp_pos = p->pos;
    if (tmp_pos + n >= 0) {
        memcpy(buf, &((FIFO_TYPE *) p->buffer)[tmp_pos],
               -(tmp_pos * sizeof(FIFO_TYPE)));
        buf += -(tmp_pos);
        n -= -(tmp_pos);
        tmp_pos = -(p->capacity);
    }
    if (n) {
        memcpy(buf, &((FIFO_TYPE *) p->buffer)[tmp_pos],
               n * sizeof(FIFO_TYPE));
    }

}

static inline FIFO_TYPE FIFO_NAME(pinPeekFront)(const LocalInputPort *p) {
    assert(FIFO_NAME(pinAvailIn)(p) > 0);
    return ((FIFO_TYPE *) p->buffer)[p->pos];
}

static inline FIFO_TYPE FIFO_NAME(pinPeek)(const LocalInputPort *p,
                                           int offset) {
    assert(offset >= 0 && FIFO_NAME(pinAvailIn)(p) >= offset);

    /* p->pos ranges from -capacity to -1, so should offset */
    offset += p->pos;
    if (offset >= 0) {
        offset -= p->capacity; /* wrap-around */
    }
    return ((FIFO_TYPE *) p->buffer)[offset];
}


#ifdef BYTES
static inline unsigned pinAvailIn_bytes(const LocalInputPort *p, int bytes)
{
  return p->available/bytes;
}

static inline unsigned pinAvailOut_bytes(const LocalOutputPort *p, int bytes) 
{
  return p->available/bytes;
}

static inline void pinRead_bytes(LocalInputPort *p, void **buf, int bytes)
{
  assert(FIFO_NAME(pinAvailIn)(p) >= bytes);
  p->available -= bytes;

  *buf = &((FIFO_TYPE*)p->buffer)[p->pos];
  p->pos += bytes;
  //We are guaranteed that a complete token fits hence no wraps inside an operation, if we reached end just point to beginning
  if(0<=(p->pos)) {
      p->pos=-p->capacity;
  }
}

static inline void pinWrite_bytes(LocalOutputPort *p, void **buf, int bytes)
{
  assert(FIFO_NAME(pinAvailOut)(p) >= bytes);
  p->available -= bytes;

  *buf = &((FIFO_TYPE*)p->buffer)[p->pos];
  p->pos += bytes;
  //we are guaranteed that a complete token fits hence no wraps inside a write of a token
  if(0<=(p->pos)) {
      p->pos=-p->capacity;
  }
}

static inline void pinPeek_bytes(const LocalInputPort *p, void **buf, int bytes,
                                           int offset) {
  int pos = p->pos;
  assert(offset>=0);
  assert(p->available>=bytes*(offset+1));
  //TODO change this to direct calculation, but don't have time now to verify
  while(offset>0) {
      pos += bytes;
      if(0<=(pos)) {
          pos=-p->capacity;
      }
      offset--;
  }
  *buf = &((FIFO_TYPE*)p->buffer)[pos];
}
#endif
