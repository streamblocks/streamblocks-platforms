#include <stdio.h>
#include <stdint.h>

// -- Print

void print_i(int32_t value){
    printf("%d", value);
}

void print_f(float value){
    printf("%f", value);
}

void print_d(double value){
    printf("%f", value);
}

void print_s(const char * value){
    printf("%s", value);
}

void print_c(int value){
    printf("%c", (char) value);
}

// -- Print and return line

void println(){
    printf("\n");
}

void println_i(int32_t value){
    printf("%d\n", value);
}

void println_f(float value){
    printf("%f\n", value);
}

void println_d(double value){
    printf("%f\n", value);
}

void println_s(const char * value){
    printf("%s\n", value);
}

void println_s_i(const char * id, int value){
    printf("%s : %d\n", id, value);
}