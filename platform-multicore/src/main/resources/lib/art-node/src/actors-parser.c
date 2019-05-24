/*
 * Copyright (c) Ericsson AB, 2009-2013
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

#include <string.h>
#include <ctype.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>

#include <fcntl.h>
#include <sys/types.h>
#include <ifaddrs.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <unistd.h>

#include "actors-parser.h"

#include "actors-network.h"
#include "actors-registry.h"

/*
 * stringification,
 * http://gcc.gnu.org/onlinedocs/cpp/Stringification.html
 */
#define xstr(s) str(s)
#define str(s) #s

/* ------------------------------------------------------------------------- */

/* Each parser instance (from file, tty, or socket) maintains this state */
struct parser_state {
  int quit_flag;         /* true if we're about to stop parsing */
  int echo_input_flag;   /* true if command input is echoed */

  FILE *out;

  /* state for getline() */
  char *buffer_ptr;
  size_t buffer_size;

  /* keeping track of next word in command line */
  char *next_word;
};

/* Max number of simultaneous clients to socket server */
#define MAX_CLIENTS  (10)

/* ========================================================================= */

void reset_line_parser(struct parser_state *state)
{
  state->next_word = state->buffer_ptr;
}

/* ------------------------------------------------------------------------- */

/** Returns next word in buffer, or NULL if no more words */
char *get_next_word(struct parser_state *state)
{
  char *word_start = NULL;
  /* First, scan ahead of any initial whitespace */
  while (isspace(*(state->next_word))) {
    state->next_word ++;
  }
  if (! *(state->next_word)) {
    return NULL;
  }

  /* Found a word. Now find out where it ends. */
  word_start = state->next_word;
  int in_quote = 0;
  while ((*(state->next_word)) && (in_quote || !isspace(*(state->next_word))) ) {
    //Toggle so between odd and even " in_quote is true
    in_quote = in_quote ^ ((*(state->next_word)) == '"');
    state->next_word ++;
  }
  if (*(state->next_word)) {
    *(state->next_word)++ = '\0';
  }

  return word_start;
}

/* ------------------------------------------------------------------------- */

/**
 * Parses a port reference. Returns type of reference.
 *
 * Syntax: actor.port   OR   host:port
 */
static enum {
  PORT_REF_INVALID,
  PORT_REF_LOCAL,
  PORT_REF_REMOTE
} parse_port_ref(struct parser_state *state,
                 const char **actor,   /* output */
                 const char **port)    /* output */
{
  assert(actor);
  assert(port);

  char *descr = get_next_word(state);
  if (! descr) {
    return PORT_REF_INVALID;
  }

  /*
   * Colon takes priority: the host spec will contain dots
   */

  char *colonpos = index(descr, ':');
  if (colonpos) {
    *colonpos = '\0';

    *actor = descr;
    *port = colonpos + 1;

    return PORT_REF_REMOTE;
  }

  char *dotpos = index(descr, '.');
  if (dotpos) {
    *dotpos = '\0';

    *actor = descr;
    *port = dotpos + 1;

    return PORT_REF_LOCAL;
  }

  return PORT_REF_INVALID;
}

/* ========================================================================= */

static void error(struct parser_state *state, const char *fmt, ...)
{
  va_list args;
  va_start(args, fmt);
  fprintf(state->out, "ERROR");
  if (fmt) {
    fprintf(state->out, " ");
    vfprintf(state->out, fmt, args);
  }
  va_end(args);
  fprintf(state->out, "\n");
}

/* ------------------------------------------------------------------------- */

/** Initial OK response, remaining part provided elsewhere */
static void ok_begin(struct parser_state *state)
{
  fprintf(state->out, "OK");
}

/* ------------------------------------------------------------------------- */

/** End of a status response */
static void ok_end(struct parser_state *state)
{
  fprintf(state->out, "\n");
}

/* ------------------------------------------------------------------------- */

static void ok(struct parser_state *state, const char *fmt, ...)
{
  va_list args;
  va_start(args, fmt);
  ok_begin(state);
  if (fmt) {
    fprintf(state->out, " ");
    vfprintf(state->out, fmt, args);
  }
  va_end(args);
  ok_end(state);
}

/* ========================================================================= */

static void actors_handler(struct parser_state *state)
{
  ok_begin(state);
  listActors(state->out);
  ok_end(state);
}

/* ------------------------------------------------------------------------- */

static void address_handler(struct parser_state *state)
{
  struct ifaddrs *ifa;

  if (getifaddrs(&ifa)) {
    error(state, "could not obtain local IP addresses: %s", strerror(errno));
  } else {
    ok_begin(state);
    do {
      if (ifa->ifa_addr != NULL && ifa->ifa_addr->sa_family == AF_INET) {

	/* Currently, only IPv4 supported */	
	struct sockaddr_in *addr = (struct sockaddr_in *) ifa->ifa_addr;

	int i;
	unsigned long a = ntohl(addr->sin_addr.s_addr);
	if (a != 0x7f000001u) { /* stay away from localhost */
	  fprintf(state->out, " ");
	  for (i = 0; i < 4; i++) {
	    fprintf(state->out, "%d", (unsigned int) ((a >> 24) & 0x00ffu));
	    if (i <= 2) {
	      fprintf(state->out, ".");
	    }
	    a <<= 8;
	  }
	}
      }

      ifa = ifa->ifa_next;
    } while (ifa);

    ok_end(state);

    freeifaddrs(ifa);
  }
}

/* ------------------------------------------------------------------------- */

static void classes_handler(struct parser_state *state)
{
  ok_begin(state);
  registryList(state->out);
  ok_end(state);
}

/* ------------------------------------------------------------------------- */

static void connect_handler(struct parser_state *state)
{
  const char *src_actor;
  const char *src_port;
  const char *dst_actor;
  const char *dst_port;

  if (parse_port_ref(state, &src_actor, &src_port) != PORT_REF_LOCAL) {
    error(state, "invalid source actor");
    return;
  }

  switch (parse_port_ref(state, &dst_actor, &dst_port)) {
    case PORT_REF_LOCAL:
      createLocalConnection(src_actor, src_port, dst_actor, dst_port);
      ok(state, "local connection %s.%s --> %s.%s",
         src_actor, src_port,
         dst_actor, dst_port);
      break;
    case PORT_REF_REMOTE:
      createRemoteConnection(src_actor, src_port, dst_actor, dst_port);
      ok(state, "remote connection %s.%s --> %s.%s",
         src_actor, src_port,
         dst_actor, dst_port);
      break;
    case PORT_REF_INVALID:
    default:
      error(state, "invalid destination actor");
      break;
  }
}

/* ------------------------------------------------------------------------- */

static void destroy_handler(struct parser_state *state)
{
  const char *actor_name = get_next_word(state);
  if (! actor_name) {
    error(state, "missing actor name");
    return;
  }

  do {
    destroyActorInstance(actor_name);
    actor_name = get_next_word(state);
  } while (actor_name);

  ok(state, "destroyed");
}

/* ------------------------------------------------------------------------- */

static void enable_handler(struct parser_state *state)
{
  const char *actor_name = get_next_word(state);
  if (! actor_name) {
    error(state, "missing actor name");
    return;
  }

  do {
    enableActorInstance(actor_name);
    actor_name = get_next_word(state);
  } while(actor_name);

  ok(state, "enabled");
}

/* ------------------------------------------------------------------------- */

static void join_handler(struct parser_state *state)
{
  waitForIdle();

  ok(state, "network is idle");
}

/* ------------------------------------------------------------------------- */

static void listen_handler(struct parser_state *state)
{
  const char *actor_name;
  const char *port_name;
  if (parse_port_ref(state, &actor_name, &port_name) != PORT_REF_LOCAL) {
    error(state, "invalid local port");
    return;
  }

  ok(state, "%d", createSocketReceiver(actor_name, port_name));
}

/* ------------------------------------------------------------------------- */

static void load_handler(struct parser_state *state)
{
  const char *filename = get_next_word(state);
  if (! filename) {
    error(state, "missing filename");
    return;
  }

  const ActorClass *klass = registryLoadClass(filename);
  if (!klass) {
    error(state, "cannot load file");
    return;
  }

  registryAddClass(klass);
  ok(state, "%s", klass->name);
}

/* ------------------------------------------------------------------------- */

static void new_handler(struct parser_state *state)
{
  const char *class_name = get_next_word(state);
  if (! class_name) {
    error(state, "missing class name");
    return;
  }

  const ActorClass *klass = registryGetClass(class_name);
  if (! klass) {
    error(state, "class %s not loaded", class_name);
    return;
  }

  const char *actor_name = get_next_word(state);
  if (! actor_name) {
    error(state, "missing instance name");
    return;
  }

  AbstractActorInstance *actor = createActorInstance(klass, actor_name);

  /* check for actor parameters, if any */
  for(;;) {
    const char *arg = get_next_word(state);
    if (! arg) {
      break;
    }
    char *splitpoint = index(arg, '=');
    if (! splitpoint) {
      error(state, "invalid argument: %s", arg);
      return;
    }
    *splitpoint = '\0';
    //If quoted string remove quotes
    if(*(splitpoint+1) == '"') {
      splitpoint+=2;
      char *lastquote = index(splitpoint, '"');
      if(lastquote)
        *lastquote = '\0';
    } else {
      splitpoint++;
    }
    
    setActorParam(actor, arg, splitpoint);
  }

  ok(state, "created actor %s", actor_name);
}

/* ------------------------------------------------------------------------- */

static void quit_handler(struct parser_state *state)
{
  state->quit_flag = 1;

  ok(state, "bye");
}

/* ------------------------------------------------------------------------- */

static void show_handler(struct parser_state *state)
{
  const char *actor_name = get_next_word(state);
  if (! actor_name) {
    error(state, "missing actor name");
    return;
  }
  ok_begin(state);
  showActor(state->out, actor_name);
  ok_end(state);
}

/* ------------------------------------------------------------------------- */

static const struct command_entry {
  const char *command;
  void (*handler)(struct parser_state *);
} commands[] = {
  { "ACTORS", &actors_handler },
  { "ADDRESS", &address_handler },
  { "CLASSES", &classes_handler },
  { "CONNECT", &connect_handler },
  { "DESTROY", &destroy_handler },
  { "ENABLE", &enable_handler },
  { "JOIN", &join_handler },
  { "LISTEN", &listen_handler },
  { "LOAD", &load_handler },
  { "NEW", &new_handler },
  { "QUIT", &quit_handler },
  { "SHOW", &show_handler },
  { NULL, NULL }
};

/* ========================================================================= */

static void parseLine(struct parser_state *state)
{
  /* filter out all comments */
  char *hashindex = index(state->buffer_ptr, '#');
  if (hashindex) {
    *hashindex = '\0';
  }

  reset_line_parser(state);

  char *command = get_next_word(state);
  if (!command) {
    return;
  }

  const struct command_entry *entry = commands;
  while (entry->command && strcasecmp(entry->command, command)) {
    entry ++;
  }
  if (entry->handler) {
    entry->handler(state);
  } else {
    error(state, "unknown command %s", command);
  }
}

/* ------------------------------------------------------------------------- */

static void parserLoop(struct parser_state *state, FILE *in)
{
  while(! state->quit_flag) {
    fprintf(state->out, "%% ");

    (void) getline(&state->buffer_ptr, &state->buffer_size, in);

    if (feof(in) || ferror(in)) {
      break;
    }

    size_t len = strlen(state->buffer_ptr);
    if (len > 0) {
      /* strip trailing whitespace/newline */
      char *lastchar = state->buffer_ptr + len - 1;
      while (len && isspace(*lastchar)) {
        *lastchar-- = '\0';
        len--;
      }
    }

    if (len) {
      if (state->echo_input_flag) {
        fprintf(state->out, "%s\n", state->buffer_ptr);
      }

      parseLine(state);
    }
  }
}

/* ------------------------------------------------------------------------- */

static void * client_thread(void *arg)
{
  FILE *f = fdopen((int) arg, "r+");

  struct parser_state state = {
    .quit_flag = 0,
    .echo_input_flag = 0,

    .out = f,

    .buffer_ptr = NULL,
    .buffer_size = 0
  };

  ok(&state, "calvin " xstr(ACTORS_RTS_MAJOR) "." xstr(ACTORS_RTS_MINOR));

  parserLoop(&state, f);

  fclose(f);
  free(state.buffer_ptr);

  return NULL;
}

/* ------------------------------------------------------------------------- */

static void * server_main_thread(void *arg)
{
  int server_socket;
  struct sockaddr_in server_addr;
  static const int one = 1;

  unsigned int server_port = (unsigned int) arg;

  server_socket = socket(AF_INET, SOCK_STREAM, 0);
  if (server_socket < 0) {
    fail("could not open server socket: %s", strerror(errno));
  }

  setsockopt(server_socket, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

  server_addr.sin_family = AF_INET;
  server_addr.sin_addr.s_addr = INADDR_ANY;
  server_addr.sin_port = htons(server_port);

  if (bind(server_socket,
           (struct sockaddr *) &server_addr,
           sizeof(server_addr)) < 0)
  {
    fail("could not bind: %s", strerror(errno));
  }

  if (listen(server_socket, MAX_CLIENTS) < 0) {
    fail("could not listen: %s", strerror(errno));
  }

  for (;;) {
    int client_socket;
    struct sockaddr_in client_addr;
    static socklen_t client_addr_size = sizeof(client_addr);
    pthread_t client_pid;

    client_socket = accept(server_socket,
                           (struct sockaddr *) &client_addr,
                           &client_addr_size);
    if (client_socket < 0) {
      fail("client connection failed: %s", strerror(errno));
    }

    pthread_create(&client_pid,
                   NULL,
                   &client_thread,
                   (void *) (long) client_socket);
  }
}

/* ========================================================================= */

void parseFile(const char *filename)
{
  struct parser_state state = {
    .quit_flag = 0,
    .echo_input_flag = 1,

    .out = stdout,

    .buffer_ptr = NULL,
    .buffer_size = 0
  };

  FILE *f = fopen(filename, "r");
  if (! f) {
    error(&state, "cannot open %s: %s", filename, strerror(errno));
    return;
  }

  parserLoop(&state, f);

  free(state.buffer_ptr);
  fclose(f);
}

/* ------------------------------------------------------------------------- */

void parseInteractively(void)
{
  struct parser_state state = {
    .quit_flag = 0,
    .echo_input_flag = 0,

    .out = stdout,

    .buffer_ptr = NULL,
    .buffer_size = 0
  };

  ok(&state, "calvin " xstr(ACTORS_RTS_MAJOR) "." xstr(ACTORS_RTS_MINOR));
  
  parserLoop(&state, stdin);
  
  free(state.buffer_ptr);
}

/* ------------------------------------------------------------------------- */

void spawnServer(unsigned int port)
{
  pthread_t pid;
  
  pthread_create(&pid, NULL, &server_main_thread, (void *) (long) port);
}
