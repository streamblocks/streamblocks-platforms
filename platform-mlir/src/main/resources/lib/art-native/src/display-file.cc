/* 
 * Copyright (c) Ericsson AB, 2013
 * Author: Harald Gustafsson (harald.gustafsson@ericsson.com)
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
#include "display.h"

static void free_display(const struct FrameBuffer *fb);

static void frame_done(const struct FrameBuffer *fb);

struct displaySpec {
    int width;
    int height;
};

int allocate_display(int width,
                     int height,
                     const char *title,
                     FrameBuffer *fb) {
    struct displaySpec *disp = malloc(sizeof(struct displaySpec));
    int bytesPerPixel;

    bytesPerPixel = 4;
    disp->width = width;
    disp->height = height;

    fb->framePtr = malloc(width * height * sizeof(char) * bytesPerPixel);
    fb->bytesPerPixel = bytesPerPixel;
    fb->pixelsPerLine = width;
    fb->Roffset = 0;
    fb->Rwidth = 8;
    fb->Goffset = 8;
    fb->Gwidth = 8;
    fb->Boffset = 16;
    fb->Bwidth = 8;
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
    FILE *fd = fopen("/tmp/drawingsize", "w");
    if (fd) {
        fprintf(fd, "%i\n%i\n", disp->width, disp->height);
        fclose(fd);
    }
    return 0;
}

static void free_display(const struct FrameBuffer *fb) {
    if (fb->displaySpecific)
        free(fb->displaySpecific);
}


static void frame_done(const struct FrameBuffer *fb) {
    struct displaySpec *disp = fb->displaySpecific;
    FILE *fd = fopen("/tmp/drawing", "wb");
    if (fd) {
        for (int i = (fb->bytesPerPixel - 1);
             i < disp->width * disp->height * fb->bytesPerPixel; i += fb->bytesPerPixel) {
            ((char *) fb->framePtr)[i] = 0xff;
        }
        fwrite(fb->framePtr, fb->bytesPerPixel, disp->width * disp->height, fd);
        fclose(fd);
    }
}
