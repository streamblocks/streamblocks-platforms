#ifndef __OCL_MACROS__
#define __OCL_MACROS__

#define OCL_ERROR true

#define ANSI_BOLD_RED "\033[1;31m"
#define ANSI_BOLD_YELLOW "\033[1;33m"
#define ANSI_RESET_COLOR "\033[0m"
#define ANSI_BOLD_GREEN "\033[1;32m"

#define OCL_CHECK(error, call)                                                 \
  call;                                                                        \
  if (error != CL_SUCCESS) {                                                   \
    printf(ANSI_BOLD_RED "%s:%d Error calling " #call                          \
                         ", error code is: %d\n" ANSI_RESET_COLOR,             \
           __FILE__, __LINE__, error);                                         \
    exit(EXIT_FAILURE);                                                        \
  }

#define OCL_MSG(fmt, args...)                                                  \
  do {                                                                         \
    if (OCL_VERBOSE)                                                           \
      fprintf(stdout,                                                          \
              ANSI_BOLD_GREEN "OCL_MSG:%-30s:%-5d\t" ANSI_RESET_COLOR fmt,     \
              __func__, __LINE__, ##args);                                     \
  } while (0);

#define OCL_ERR(fmt, args...)                                                  \
  do {                                                                         \
    if (OCL_ERROR)                                                             \
      fprintf(stderr,                                                          \
              ANSI_BOLD_RED "OCL_ERR:%-30s:%-5d\t" ANSI_RESET_COLOR fmt,       \
              __func__, __LINE__, ##args);                                     \
  } while (0);

#define OCL_ASSERT(cond, fmt, args...)                                         \
  do {                                                                         \
    if (cond == false) {                                                       \
      fprintf(stderr, ANSI_BOLD_RED                                            \
              "ASSERTION FAILED: %s():%d:\t\t\t\t" ANSI_RESET_COLOR fmt,       \
              __func__, __LINE__, ##args);                                     \
      exit(EXIT_FAILURE);                                                      \
    }                                                                          \
  } while (0);

#endif
