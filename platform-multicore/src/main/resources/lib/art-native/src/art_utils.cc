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

/**
 * @brief Reinterprets and integer value to a float value
 * for instance, is @c int_value=0x4000000 then the returned value is 2.0
 * @param int_value integer value to be reinterpreted as float
 * @return float reinterpreted value
 */

float reinterpret_float(unsigned int int_value) {
    float* as_float = reinterpret_cast<float*>(&int_value);
    return as_float[0];
}

uint32_t reinterpret_uint(float float_value) {
    uint32_t* as_uint = reinterpret_cast<uint32_t*>(&float_value);
    return as_uint[0];
}