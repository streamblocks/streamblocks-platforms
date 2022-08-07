#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "options.h"
#include "actors-rts.h"
#include "util.h"

#define DEFAULT_FIFO_LENGTH 4096

void show_usage(char *name) {
    printf("Usage: %s [OPTION...]\n", name);
    printf("Executes network %s using the ACTORS run-time system\n", name);
    printf("\nOptions:\n"
           "--cfile=FILE            Sets affinity and/or FIFO capacities as\n"
           "                        specified in the configuration file\n"
           "--generate=FILE         Generate configuration file from current\n"
           "                        execution (also see --with_complexity and\n"
           "                        --width_bandwidth)\n"
           "--help                  Display this help list\n"
           "--loopmax=N             Restrict the maximum number of action\n"
           "                        firings per actor\n"
           "--statistics            Display run-time statistics\n"
           "--timing                Collect and display timing statistics\n"
           "--trace                 Generate execution trace:\n"
           "                        Action trace generated if actors are\n"
           "                        compiled with CFLAGS=-DTRACE\n"
           "--turnus-trace          Generate execution trace for TURNUS:\n"
           "                        Action trace generated if actors are\n"
           "                        compiled with CFLAGS=-DTRACE_TURNUS\n"
           "--with-complexity       Output per-actor complexity (cycles) in\n"
           "                        configuration file (see --generate)\n"
           "--with-bandwidth        Output per-connection bandwidth (#tokens)\n"
           "                        in configuration file (see --generate).\n"
           "                        Note: wraps around at 4G tokens\n"
           "--termination-report    Describe network state at termination\n"
           "--hardware-profile=FILE Generate hardware profiling info if \n"
           "                        a hardware partition is present. Based \n"
           "                        whether SystemC simulation is running or \n"
           "                        OpenCL kernel is executing, different \n"
           "                        profiling information may be produced.\n"
           "--vcd-trace-level=N     Generates vcd dump if SystemC simulation \n"
           "                        is being performed. N determines the \n"
           "                        level of details in the VCD dump:\n"
           "                           0: no vcd dump\n"
           "                           1: top level signals and ports\n"
           "                           2: + internal FIFO interfaces\n"
           "                           3: + trigger state machines\n"
           "                        the vcd dump is saved to: \n"
           "                        ./network_tester.vcd\n"
           "--i=FILE                Optional input file\n"
           "--l=N                   Set the number of loops of the previous\n"
           "                        input file                               \n"
           "--f=B                   Restricts the number of displayed frame \n"
           "                        and then exits\n");
    printf(
           "--depth=N               Buffer depth for all connections,        \n"
           "                        default is %d tokens                     \n"
           "--use-default-depth     Ignores the buffer size configurations   \n"
           "                        provided in using the cfile option and",
           DEFAULT_FIFO_LENGTH);

}

void pre_parse_args(int argc, char *argv[], RuntimeOptions *options) {

    options->enable_display = 1;
    options->nbr_loops=-1;
    options->nbr_frames = -1;

    options->no_cfile_connections = 0;
    options->buffer_depth = DEFAULT_FIFO_LENGTH;

    for (int i = 1; i < argc; i++) {

        if (strcmp(argv[i], "--timing") == 0) {
            options->show_timing = 1;
            options->flags |= FLAG_TIMING;
        } else if (strcmp(argv[i], "--statistics") == 0) {
            options->show_statistics = 1;
        } else if (strncmp(argv[i], "--loopmax=", 10) == 0) {
            options->arg_loopmax = atoi(&argv[i][10]);
        } else if (strncmp(argv[i], "--cfile=", 8) == 0) {
            options->configFilename = &argv[i][8];
        } else if (strncmp(argv[i], "--generate=", 11) == 0) {
            options->generateFileName = &argv[i][11];
        } else if (strcmp(argv[i], "--with-complexity") == 0) {
            options->flags |= FLAG_TIMING;
            options->with_complexity = 1;
        } else if (strcmp(argv[i], "--with-bandwidth") == 0) {
            options->with_bandwidth = 1;
        } else if (strcmp(argv[i], "--trace") == 0) {
            options->generate_trace = 1;
        } else if (strcmp(argv[i], "--turnus-trace") == 0) {
            options->generate_turnus_trace = 1;
        } else if (strcmp(argv[i], "--termination-report") == 0) {
            options->terminationReport = 1;
        } else if (strncmp(argv[i], "--hardware-profile=", 19) == 0) {
            options->hardwareProfileFileName = &argv[i][19];
        } else if (strncmp(argv[i], "--vcd-trace-level=", 18) == 0) {
            options->vcd_trace_level = &argv[i][18];
        } else if (strncmp(argv[i], "--i=", 4) == 0) {
            options->input_file = &argv[i][4];
        } else if (strncmp(argv[i], "--n", 3) == 0) {
            options->enable_display = 0;
        } else if (strncmp(argv[i], "--l=", 4) == 0) {
            options->nbr_loops = atoi(&argv[i][4]);
        } else if (strncmp(argv[i], "--f=", 4) == 0) {
            options->nbr_frames = atoi(&argv[i][4]);
        } else if (strncmp(argv[i], "--d=", 4) == 0) {
            options->buffer_depth = atoi(&argv[i][4]);
        } else if (strcmp(argv[i], "--use-default-depth") == 0) {
            options->no_cfile_connections = 1;
        }  else if (strcmp(argv[i], "--help") == 0) {
            show_usage(argv[0]);
            exit(0);
        } else {
            printf("Invalid command-line argument '%s'\n", argv[i]);
            exit(1);
        }
    }
    opt = options;
}


