#include <stdlib.h>

float random(float low, float high){
    float d =  (float) rand() / ( (float) RAND_MAX + 1);
    return (float) low + d * (high - low);
}