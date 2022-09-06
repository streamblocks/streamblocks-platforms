#include <iostream>
#include <chrono>

#include "sb_native_framerate.h"

using Clock = std::chrono::high_resolution_clock;

using Ms = std::chrono::milliseconds;

using namespace std::video::display;

void std::video::display::print_fps_avg() {
    unsigned int endTime = std::chrono::duration_cast<Ms>(Clock::now().time_since_epoch()).count();

    float decodingTime = (float) (endTime - startTime) / 1000.0f;
    float framerate = (float) numPicturesDecoded / decodingTime;

    std::cout << numPicturesDecoded << " images in " << decodingTime << " seconds: " << framerate << " FPS" << std::endl;
}

void std::video::display::fpsPrintInit() {
    startTime = std::chrono::duration_cast<Ms>(Clock::now().time_since_epoch()).count();
    numPicturesDecoded = 0;
    partialNumPicturesDecoded = 0;
    lastNumPic = 0;
    atexit(print_fps_avg);
    relativeStartTime = startTime;
}

void std::video::display::fpsPrintNewPicDecoded() {
    unsigned int endTime;
    numPicturesDecoded++;
    partialNumPicturesDecoded++;
    endTime = std::chrono::duration_cast<Ms>(Clock::now().time_since_epoch()).count();

    if (endTime - relativeStartTime > 5000) {
        printf("%f images/sec\n", 1000.0f * (float) (numPicturesDecoded - lastNumPic) / (float) (endTime - relativeStartTime));
        relativeStartTime = endTime;
        lastNumPic = numPicturesDecoded;
    }
}