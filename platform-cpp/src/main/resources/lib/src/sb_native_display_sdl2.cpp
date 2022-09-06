#include <iostream>
#include "sb_native_display.h"

using namespace std::video::display;

char std::video::display::displayYUV_getFlags(){
    return sb::options::disable_display ? 0 : 1;
}

void std::video::display::displayYUV_setSize(int width, int height) {

    // allocate window, renderer, texture
    pWindow1 = SDL_CreateWindow("display", SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED,
                                width, height, SDL_WINDOW_SHOWN | SDL_WINDOW_OPENGL);
#if defined(__APPLE__) || defined(__MACOSX)
    pRenderer1 = SDL_CreateRenderer(pWindow1, -1, SDL_RENDERER_SOFTWARE);
#else
    pRenderer1 = SDL_CreateRenderer(pWindow1, -1, SDL_RENDERER_ACCELERATED);
#endif
    bmpTex1 = SDL_CreateTexture(pRenderer1, SDL_PIXELFORMAT_YV12,
                                SDL_TEXTUREACCESS_STREAMING, width, height);
    if (pWindow1 == NULL || pRenderer1 == NULL || bmpTex1 == NULL) {
        std::cerr << "Could not open window1" << std::endl;
    }
}

void std::video::display::displayYUV_displayPicture(unsigned char *pictureBufferY,
                                                    unsigned char *pictureBufferU, unsigned char *pictureBufferV,
                                                    unsigned int pictureWidth, unsigned int pictureHeight) {
    static unsigned int lastWidth = 0;
    static unsigned int lastHeight = 0;
    SDL_Event event;

    if ((pictureHeight != lastHeight) || (pictureWidth != lastWidth)) {
        displayYUV_setSize(pictureWidth, pictureHeight);
        lastHeight = pictureHeight;
        lastWidth = pictureWidth;
    }

    size1 = pictureWidth * pictureHeight;

    SDL_LockTexture(bmpTex1, NULL, (void **) &pixels1, &pitch1);
    memcpy(pixels1, pictureBufferY, size1);
    memcpy(pixels1 + size1, pictureBufferV, size1 / 4);
    memcpy(pixels1 + size1 * 5 / 4, pictureBufferU, size1 / 4);
    SDL_UnlockTexture(bmpTex1);
    SDL_UpdateTexture(bmpTex1, NULL, pixels1, pitch1);
    // refresh screen
    //    SDL_RenderClear(pRenderer1);
    SDL_RenderCopy(pRenderer1, bmpTex1, NULL, NULL);
    SDL_RenderPresent(pRenderer1);

    /* Grab all the events off the queue. */
    while (SDL_PollEvent(&event)) {
        switch (event.type) {
            case SDL_QUIT:
                exit(0);
                break;
            default:
                break;
        }
    }
}

void std::video::display::displayYUV_init() {
    if (!init) {
        init = 1;

        // First, initialize SDL's video subsystem.
        if (SDL_Init(SDL_INIT_VIDEO) < 0) {
            std::cerr << "Video initialization failed: " << SDL_GetError() << std::endl;
        }

        atexit(SDL_Quit);
    }
}

/**
 * @brief Return the number of frames the user want to decode before exiting the application.
 * If user didn't use the -f flag, it returns -1 (DEFAULT_INFINITEà).
 * @return The
 */
int std::video::display::displayYUV_getNbFrames() {
    return sb::options::nbFrames;
}

void std::video::display::display_close() {
    SDL_Quit();
}
