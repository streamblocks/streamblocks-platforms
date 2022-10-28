#ifndef LOGGING_H_INCLUDED
#define LOGGING_H_INCLUDED

#include <time.h>
#include <stdio.h>

#define runtimeError(instance, ...) m_critical(__VA_ARGS__)

#define RESET   "\033[0m"
#define RED     "\033[31;1m"
#define YELLOW  "\033[33;1m"

#define GREEN   "\033[32m"
#define BLUE    "\033[34m"

extern int MLOG_USE_COLORS;

#define _LOG(COL, ...) do { \
  time_t unlikely_variable_name_t1; struct tm unlikely_variable_name_t;\
  time(&unlikely_variable_name_t1); \
  localtime_r(&unlikely_variable_name_t1, &unlikely_variable_name_t); \
  printf("%02d:%02d.%02d (%15.15s:%4d) > ", unlikely_variable_name_t.tm_hour, \
      unlikely_variable_name_t.tm_min, unlikely_variable_name_t.tm_sec, \
      __FUNCTION__, __LINE__); \
  if (MLOG_USE_COLORS) { \
    printf(COL __VA_ARGS__); \
    puts(RESET); \
  } else { \
    printf(__VA_ARGS__); \
    puts(""); \
  } \
} while (0)

void mlog_enable_colors(void);
void mlog_disable_colors(void);

#if 1
#define m_message(...) _LOG(GREEN, __VA_ARGS__)
#define m_info(...) _LOG(BLUE, __VA_ARGS__)
#else
#define m_message(...)
#define m_info(...)
#endif
#define m_warning(...) _LOG(YELLOW, __VA_ARGS__)
#define m_critical(...) do { _LOG(RED, __VA_ARGS__); exit(1); } while (0)

#endif
