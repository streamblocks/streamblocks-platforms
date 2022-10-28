#include "logging.h"
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

int MLOG_USE_COLORS = 0;

void mlog_enable_colors(void) { MLOG_USE_COLORS = 1; }

void mlog_disable_colors(void) { MLOG_USE_COLORS = 0; }
