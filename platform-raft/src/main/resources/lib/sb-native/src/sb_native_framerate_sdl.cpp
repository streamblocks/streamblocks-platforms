#include <iostream>
#include <SDL.h>
#include "sb_native_framerate.h"

using namespace std::video::display;

void std::video::display::print_fps_avg() {
    unsigned int endTime = SDL_GetTicks();

    float decodingTime = (endTime - startTime) / 1000.0f;
    float framerate = numPicturesDecoded / decodingTime;

    std::cout << numPicturesDecoded << " images in " << decodingTime << " seconds: " << framerate << " FPS" << std::endl;
}

void std::video::display::fpsPrintInit() {
    startTime = SDL_GetTicks();
    numPicturesDecoded = 0;
    partialNumPicturesDecoded = 0;
    lastNumPic = 0;
    atexit(print_fps_avg);
    relativeStartTime = startTime;
}

void std::video::display::fpsPrintNewPicDecoded() {
    unsigned int endTime;
    float relativeTime;

    numPicturesDecoded++;
    partialNumPicturesDecoded++;
    endTime = SDL_GetTicks();

    relativeTime = (endTime - relativeStartTime) / 1000.0f;

    if (relativeTime >= 5) {
        float framerate = (numPicturesDecoded - lastNumPic) / relativeTime;
        std::cout << framerate << " images/sec" << std::endl;

        relativeStartTime = endTime;
        lastNumPic = numPicturesDecoded;
    }
}