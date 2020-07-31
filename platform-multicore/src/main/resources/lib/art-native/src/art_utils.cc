#include <stdio.h>
#include <stdint.h>
#include <random>


// -- utility functions

int randint(int min, int max) {

    static std::random_device rd;
    static std::mt19937 gen(rd());
    std::uniform_int_distribution<> distrib(min, max);
    return distrib(gen);
}