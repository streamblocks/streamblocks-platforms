
#ifndef _ART_OPTIONS_H_
#define _ART_OPTIONS_H_

#ifdef __cplusplus
extern "C" {
#endif

typedef struct{
    int flags;
    int show_timing;
    int show_statistics;
    int arg_loopmax;
    char *generateFileName;
    char *configFilename;
    int with_complexity;
    int with_bandwidth;
    int terminationReport;
    int generate_trace;
    int generate_turnus_trace;
    char *hardwareProfileFileName;
    char *vcd_trace_level;

    // -- ORCC-RVC related
    char *input_file;
    char *input_directory;
    char *yuv_file;
    int enable_display;
    int nbr_loops;
    int nbr_frames;
    // -- buffer related
    int buffer_depth;
    int no_cfile_connections;
} RuntimeOptions;

void show_usage(char *name);
void pre_parse_args(int argc, char *argv[], RuntimeOptions *options);

#ifdef __cplusplus
}
#endif

#endif  /* _ART_OPTIONS_H_ */