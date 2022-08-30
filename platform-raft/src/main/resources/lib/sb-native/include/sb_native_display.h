#pragma once

#ifdef DISPLAY_ENABLE
#include <SDL.h>
#endif

#include <stdint.h>
#include "options.h"


namespace std {
    namespace video {
        namespace display {
#ifdef DISPLAY_ENABLE
            static SDL_Window *pWindow1;
            static SDL_Renderer *pRenderer1;
            static SDL_Texture *bmpTex1;
            static uint8_t *pixels1;
            static int pitch1, size1;

            static int init = 0;
#endif
            char displayYUV_getFlags();

            void displayYUV_setSize(int width, int height);

            void displayYUV_displayPicture(unsigned char *pictureBufferY,
                                           unsigned char *pictureBufferU, unsigned char *pictureBufferV,
                                           unsigned int pictureWidth, unsigned int pictureHeight);

            void displayYUV_init();

            int displayYUV_getNbFrames();

            void display_close();

        }
    }
}