#ifndef __DEBUG_MACROS_H__
#define __DEBUG_MACROS_H__
#include <stdio.h>
#include <stdlib.h>

#ifndef NDEBUG
#define ENABLE_DEBUG_ASSERT true
#define ENABLE_DEBUG_MESSAGE true
#else
#define ENABLE_DEBUG_ASSERT false
#define ENABLE_DEBUG_MESSAGE false
#endif

#define ENABLE_STATUS_REPORT true
#define ENABLE_WARNING_REPORT true

#define ANSI_BOLD_RED "\033[1;31m"
#define ANSI_BOLD_YELLOW "\033[1;33m"
#define ANSI_RESET_COLOR "\033[0m"
#define ANSI_BOLD_GREEN "\033[1;32m"

#define PANIC(fmt, args...)                                                    \
  do {                                                                         \
    fprintf(stderr, ANSI_BOLD_RED "PANIC: %s():%d: " ANSI_RESET_COLOR fmt,     \
            __func__, __LINE__, ##args);                                       \
    exit(EXIT_FAILURE);                                                        \
  } while (0);

#define ASSERT(cond, fmt, args...)                                             \
  do {                                                                         \
    if (cond == false) {                                                       \
      fprintf(stderr,                                                          \
              ANSI_BOLD_RED                                                    \
              "ASSERTION FAILED: %s():%d: " ANSI_RESET_COLOR fmt,              \
              __func__, __LINE__, ##args);                                     \
      exit(EXIT_FAILURE);                                                      \
    }                                                                          \
  } while (0);

#define DEBUG_ASSERT(cond, fmt, args...)                                       \
  do {                                                                         \
    if (ENABLE_DEBUG_ASSERT)                                                   \
      ASSERT(cond, fmt, args);                                                 \
  } while (0);

#define DEBUG_MESSAGE(fmt, args...)                                            \
  do {                                                                         \
    if (ENABLE_DEBUG_MESSAGE) {                                                \
      fprintf(stderr,                                                          \
              ANSI_BOLD_YELLOW                                                 \
              "DEBUG MESSAGE: %s():%d: " ANSI_RESET_COLOR fmt,                 \
              __func__, __LINE__, ##args);                                     \
    }                                                                          \
  } while (0);

#define STATUS_REPORT(fmt, args...)                                            \
  do {                                                                         \
    if (ENABLE_STATUS_REPORT) {                                                \
      fprintf(stderr,                                                          \
              ANSI_BOLD_GREEN "STATUS_REPORT: %s():%d: " ANSI_RESET_COLOR fmt, \
              __func__, __LINE__, ##args);                                     \
    }                                                                          \
  } while (0);

#define WARNING(fmt, args...)                                                  \
  do {                                                                         \
    if (ENABLE_WARNING_REPORT) {                                               \
      fprintf(stderr,                                                          \
              ANSI_BOLD_YELLOW "WARNING: %s():%d: " ANSI_RESET_COLOR fmt,      \
              __func__, __LINE__, ##args);                                     \
    }                                                                          \
  } while (0);

#endif // __DEBUG_MACROS_H__