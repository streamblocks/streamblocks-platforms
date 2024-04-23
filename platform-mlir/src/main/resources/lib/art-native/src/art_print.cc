#include <stdio.h>
#include <stdint.h>

// -- Print

void print(char *text) {
    fputs(text, stdout);
}

void println(char *text) {
    puts(text);
}