#include <libgen.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/time.h>
#include <unistd.h>

#include "actors-network.h"
#include "actors-parser.h"
#include "actors-registry.h"
#include "logging.h"

static void sig_handler(int signum) { exit(0); }

/* ------------------------------------------------------------------------- */

static void usage(const char *bname) {
  printf(GREEN "usage: %s [-ic] [-d <ms>] [-p <port>] [script1.cs script2.cs "
               "... scriptN.cs]\n"
               "  Named scripts are executed in sequence. Other options:\n"
               "  -i        : start interactive interpreter\n"
               "  -c        : enable log colors\n"
               "  -d  <ms>  : add ms delay to main loop\n"
               "  -s <port> : start command server at given port " RESET "\n",
         bname);
  exit(0);
}

int main(int argc, char **argv) {
  int interactive = 0;
  short port = 0;
  int opt;
  int mainloop_delay = 0;

  signal(1, &sig_handler);

  while ((opt = getopt(argc, argv, "cp:s:id:")) != -1) {
    switch (opt) {
    case 'c':
      mlog_enable_colors();
      break;
    case 'i':
      interactive = 1;
      break;
    case 'd':
      mainloop_delay = atoi(optarg);
      break;
    case 's': /* FALLTHROUGH */
    case 'p':
      port = atoi(optarg);
      break;
    default: /* '?' */
      usage(basename(argv[0]));
    }
  }

  registryInit();
  initActorNetwork(mainloop_delay);

  if (port != 0) {
    spawnServer(port);
    /* Wait for it */
    m_message("Server running on port %d", port);
    select(0, NULL, NULL, NULL, NULL);
  } else if (interactive) {
    parseInteractively();
  } else {
    /* execute each named file in non-interactive mode */
    while (optind < argc) {
      parseFile(argv[optind++]);
    }
  }

  return 0;
}
