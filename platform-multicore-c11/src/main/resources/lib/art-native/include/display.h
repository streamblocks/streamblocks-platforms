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

#ifndef _ART_DISPLAY_YUV_H
#define _ART_DISPLAY_YUV_H

/*
 * Definition of YUV input
 */

// We're using int32_t now (could use uint8_t or int16_t?)
typedef int yuv_sample_t;

#define START_Y 0
#define START_U (4*64)
#define START_V (5*64)

#define MB_SIZE (6*64)

/*
 * Definition of frame buffer format
 */

typedef struct FrameBuffer {
    void *framePtr;          // pointer to frame origin (upper left corner)
    int bytesPerPixel;     // size of pixel in bytes (2 or 4 supported)
    int pixelsPerLine;     // line width in pixels (scale factor for y)
    int Roffset;           // Position of R component (e.g. 11 in RGB565)
    int Rwidth;            // Width of R component (e.g. 5 in RGB565)
    int Goffset;           // Position of G component (e.g 5 in RGB 565)
    int Gwidth;            // Width of R component (e.g. 6 in RGB565)
    int Boffset;           // Position of B component (e.g 0)
    int Bwidth;            // Width of B component (e.g. 5 in RGB565)

    void *displaySpecific; // Additional stuff if needed

    // Display-specific methods
    void (*display_yuv)(int x, int y,
                        yuv_sample_t macroBlock[MB_SIZE],
                        const struct FrameBuffer *format);

    void (*frame_done)(const struct FrameBuffer *format);

    void (*free_display)(const struct FrameBuffer *format);
} FrameBuffer;


/*
 * Allocates a display/frame buffer, sets up FrameBufferFormat
 *
 * returns zero if successful
 *
 * NOT provided by libactors (external implementations for FB,GTK,SDL
 * available -select one when building an application)
 */

int allocate_display(int width,
                     int height,
                     const char *title,
                     FrameBuffer *fb);

/*
 * Default implementations of display_yuv for pixel sizes 2 and 4 provided
 * for convenience
 */

void display_yuv_16bpp(int x, int y,
                       yuv_sample_t macroBlock[MB_SIZE],
                       const struct FrameBuffer *fb);

void display_yuv_32bpp(int x, int y,
                       yuv_sample_t macroBlock[MB_SIZE],
                       const struct FrameBuffer *fb);

void Orcc_display_yuv_16bpp(int x, int y,
                            yuv_sample_t macroBlock[MB_SIZE],
                            const struct FrameBuffer *fb);

void Orcc_display_yuv_32bpp(int x, int y,
                            yuv_sample_t macroBlock[MB_SIZE],
                            const struct FrameBuffer *fb);

#endif
