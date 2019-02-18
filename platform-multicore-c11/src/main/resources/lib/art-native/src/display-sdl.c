/* 
 * Copyright (c) Ericsson AB, 2009
 * Author: Charles Chen Xu (charles.chen.xu@ericsson.com)
 * All rights reserved.
 *
 * License terms:
 *
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met:
 *     * Redistributions of source code must retain the above 
 *       copyright notice, this list of conditions and the 
 *       following disclaimer.
 *     * Redistributions in binary form must reproduce the 
 *       above copyright notice, this list of conditions and 
 *       the following disclaimer in the documentation and/or 
 *       other materials provided with the distribution.
 *     * Neither the name of the copyright holder nor the names 
 *       of its contributors may be used to endorse or promote 
 *       products derived from this software without specific 
 *       prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <stdio.h>
#include <stdlib.h>
#include <SDL.h>

#include "display.h"


typedef struct {
    SDL_Window *window;
    SDL_Renderer *renderer;
    SDL_Texture *texture;
    SDL_Surface *image;
} sdl_display_t;

static void free_display(const struct FrameBuffer *fb);

static void frame_done(const struct FrameBuffer *fb);


int allocate_display(int width,
                     int height,
                     const char *title,
                     FrameBuffer *fb) {
    sdl_display_t *disp = (sdl_display_t *) malloc(sizeof(sdl_display_t));
    int bytesPerPixel;

    //Start SDL
    if (SDL_Init(SDL_INIT_VIDEO) < 0) {
        fprintf(stderr, "display-sdl: SDL_Init: %s\n", SDL_GetError());
        return 1;
    }

    atexit(SDL_Quit);

    disp->window = SDL_CreateWindow("Display", SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED, width, height,
                                    SDL_WINDOW_SHOWN | SDL_WINDOW_OPENGL);
    disp->renderer = SDL_CreateRenderer(disp->window, -1, SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC);

    disp->image = SDL_CreateRGBSurface(0,
                                       width, height,
                                       32,
                                       0x00FF0000,
                                       0x0000FF00,
                                       0x000000FF,
                                       0xFF000000);

    disp->texture = SDL_CreateTexture(disp->renderer, SDL_PIXELFORMAT_ARGB8888,
                                      SDL_TEXTUREACCESS_STREAMING, width, height);


    SDL_UpdateTexture(disp->texture , NULL, disp->image->pixels, disp->image->pitch);
    SDL_RenderClear(disp->renderer);
    SDL_RenderCopy(disp->renderer, disp->texture, NULL, NULL);
    SDL_RenderPresent(disp->renderer);

    if (disp->window == NULL || disp->renderer == NULL || disp->texture == NULL ) {
        fprintf(stderr, "display-sdl: SDL_SetVideoMode: %s\n", SDL_GetError());
        return 1;
    }



    bytesPerPixel = disp->image->format->BytesPerPixel;

    fb->framePtr = disp->image->pixels;
    fb->bytesPerPixel = bytesPerPixel;
    fb->pixelsPerLine = disp->image->pitch / bytesPerPixel;
    fb->Roffset = disp->image->format->Rshift;
    fb->Rwidth = 8 - disp->image->format->Rloss;
    fb->Goffset = disp->image->format->Gshift;
    fb->Gwidth = 8 - disp->image->format->Gloss;
    fb->Boffset = disp->image->format->Bshift;
    fb->Bwidth = 8 - disp->image->format->Bloss;
    fb->displaySpecific = disp;

    switch (bytesPerPixel) {
        case 2:
#ifdef ORCC
            fb->display_yuv = Orcc_display_yuv_16bpp;
#else
            fb->display_yuv = display_yuv_16bpp;
#endif
            break;
        case 4:
#ifdef ORCC
            fb->display_yuv = Orcc_display_yuv_32bpp;
#else
            fb->display_yuv = display_yuv_32bpp;
#endif
            break;
        default:
            fprintf(stderr, "display-sdl: Unsupported pixel size: %dbpp\n",
                    8 * bytesPerPixel);
            return 1;
    }

    fb->frame_done = frame_done;
    fb->free_display = free_display;
    return 0;
}

static void free_display(const struct FrameBuffer *fb) {
    sdl_display_t *disp = (sdl_display_t *) fb->displaySpecific;

    if (disp != 0) {
        SDL_FreeSurface(disp->image);
        free(disp);
    }
}


static void frame_done(const struct FrameBuffer *fb) {
    sdl_display_t *disp = (sdl_display_t *) fb->displaySpecific;

    SDL_Event event;

    SDL_UpdateTexture(disp->texture, NULL, disp->image->pixels, disp->image->pitch);
    SDL_RenderClear(disp->renderer);
    SDL_RenderCopy(disp->renderer, disp->texture, NULL, NULL);
    SDL_RenderPresent(disp->renderer);

    while (SDL_PollEvent(&event)) {
        switch (event.type) {
            case SDL_QUIT:
                exit(0);
            default:
                break;
        }
    }
}
