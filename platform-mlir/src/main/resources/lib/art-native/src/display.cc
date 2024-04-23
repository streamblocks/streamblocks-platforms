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

#include "display.h"

typedef short int16bpp_t;
typedef int int32bpp_t;

#define CLIP(x, max) (((unsigned int) (x)<=(max))? (x) : ((x)<0? 0: (max)))


void Orcc_display_yuv_16bpp(int x, int y,
                            yuv_sample_t macroBlock[MB_SIZE],
                            const struct FrameBuffer *format) {
    yuv_sample_t *uptr = macroBlock + START_U;
    yuv_sample_t *vptr = macroBlock + START_V;
    int lineLength = format->pixelsPerLine;
    int16bpp_t *fbp = (int16bpp_t *) format->framePtr;
    int Roffset = format->Roffset;
    int Rloss = 16 - format->Rwidth; // starting out with 16 bits, need Rwidth
    int Rmax = (1 << format->Rwidth) - 1;
    int Goffset = format->Goffset;
    int Gloss = 16 - format->Gwidth;
    int Gmax = (1 << format->Gwidth) - 1;
    int Boffset = format->Boffset;
    int Bloss = 16 - format->Bwidth;
    int Bmax = (1 << format->Bwidth) - 1;
    int j, k, yStartOfLine;


    fbp += x + y * lineLength;
    yStartOfLine = 0;
    for (j = 0; j < 8; j++) {
        int yStartOfQuad = 0;

        for (k = 0; k < 8; k++) {
            yuv_sample_t *yptr = macroBlock + yStartOfLine + yStartOfQuad;
            int tu = *uptr++ - 128;
            int tv = *vptr++ - 128;
            int ruv = 409 * tv + 128;
            int guv = 100 * tu + 208 * tv - 128;
            int buv = 516 * tu;
            int dj, dk;

            for (dk = 0; dk < 2; dk++, yptr++, fbp++) {
                for (dj = 0; dj < 2; dj++) {
                    int y = yptr[16 * dj];
                    int t = (y - 16) * 298;
                    int r = (t + ruv) >> Rloss;
                    int g = (t + guv) >> Gloss;
                    int b = (t + buv) >> Bloss;
                    fbp[lineLength * dj] = (CLIP(r, Rmax) << Roffset)
                                           | (CLIP(g, Gmax) << Goffset)
                                           | (CLIP(b, Bmax) << Boffset);
                }
            }
            yStartOfQuad = (yStartOfQuad + 2);
        }
        yStartOfLine = (yStartOfLine + 32);

        // Skip odd lines of frame buffer
        fbp += 2 * lineLength - 16;
    }
}


void Orcc_display_yuv_32bpp(int x, int y,
                            yuv_sample_t macroBlock[MB_SIZE],
                            const struct FrameBuffer *format) {
    yuv_sample_t *uptr = macroBlock + START_U;
    yuv_sample_t *vptr = macroBlock + START_V;
    int lineLength = format->pixelsPerLine;
    int32bpp_t *fbp = (int32bpp_t *) format->framePtr;
    int Roffset = format->Roffset;
    int Rloss = 16 - format->Rwidth; // starting out with 16 bits, need Rwidth
    int Rmax = (1 << format->Rwidth) - 1;
    int Goffset = format->Goffset;
    int Gloss = 16 - format->Gwidth;
    int Gmax = (1 << format->Gwidth) - 1;
    int Boffset = format->Boffset;
    int Bloss = 16 - format->Bwidth;
    int Bmax = (1 << format->Bwidth) - 1;
    int j, k, yStartOfLine;


    fbp += x + y * lineLength;
    yStartOfLine = 0;
    for (j = 0; j < 8; j++) {
        int yStartOfQuad = 0;

        for (k = 0; k < 8; k++) {
            yuv_sample_t *yptr = macroBlock + yStartOfLine + yStartOfQuad;
            int tu = *uptr++ - 128;
            int tv = *vptr++ - 128;
            int ruv = 409 * tv + 128;
            int guv = 100 * tu + 208 * tv - 128;
            int buv = 516 * tu;
            int dj, dk;

            for (dk = 0; dk < 2; dk++, yptr++, fbp++) {
                for (dj = 0; dj < 2; dj++) {
                    int y = yptr[16 * dj];
                    int t = (y - 16) * 298;
                    int r = (t + ruv) >> Rloss;
                    int g = (t - guv) >> Gloss;
                    int b = (t + buv) >> Bloss;
                    fbp[lineLength * dj] = (CLIP(r, Rmax) << Roffset)
                                           | (CLIP(g, Gmax) << Goffset)
                                           | (CLIP(b, Bmax) << Boffset);
                }
            }
            yStartOfQuad = (yStartOfQuad + 2);
        }
        yStartOfLine = (yStartOfLine + 32);

        // Skip odd lines of frame buffer
        fbp += 2 * lineLength - 16;
    }
}


void display_yuv_16bpp(int x, int y,
                       yuv_sample_t macroBlock[MB_SIZE],
                       const struct FrameBuffer *format) {
    yuv_sample_t *uptr = macroBlock + START_U;
    yuv_sample_t *vptr = macroBlock + START_V;
    int lineLength = format->pixelsPerLine;
    int16bpp_t *fbp = (int16bpp_t *) format->framePtr;
    int Roffset = format->Roffset;
    int Rloss = 16 - format->Rwidth; // starting out with 16 bits, need Rwidth
    int Rmax = (1 << format->Rwidth) - 1;
    int Goffset = format->Goffset;
    int Gloss = 16 - format->Gwidth;
    int Gmax = (1 << format->Gwidth) - 1;
    int Boffset = format->Boffset;
    int Bloss = 16 - format->Bwidth;
    int Bmax = (1 << format->Bwidth) - 1;
    int j, k, yStartOfLine;


    fbp += x + y * lineLength;
    yStartOfLine = 0;
    for (j = 0; j < 8; j++) {
        int yStartOfQuad = 0;

        for (k = 0; k < 8; k++) {
            yuv_sample_t *yptr = macroBlock + yStartOfLine + yStartOfQuad;
            int tu = *uptr++ - 128;
            int tv = *vptr++ - 128;
            int ruv = 409 * tv + 128;
            int guv = 100 * tu + 208 * tv - 128;
            int buv = 516 * tu;
            int dj, dk;

            for (dk = 0; dk < 2; dk++, yptr++, fbp++) {
                for (dj = 0; dj < 2; dj++) {
                    int y = yptr[8 * dj];
                    int t = (y - 16) * 298;
                    int r = (t + ruv) >> Rloss;
                    int g = (t - guv) >> Gloss;
                    int b = (t + buv) >> Bloss;
                    fbp[lineLength * dj] = (CLIP(r, Rmax) << Roffset)
                                           | (CLIP(g, Gmax) << Goffset)
                                           | (CLIP(b, Bmax) << Boffset);
                }
            }
            // yStartOfQuad = 0, 2, 4, 6, 64, 66, 68, 70
            // that is the index of the upper left corner of a 2x2 quad pixel
            // (relative to the start of the line)
            // 58 = 0111010 in binary
            // 70 = 1000110
            // which is why we get the step from 6 to 64
            yStartOfQuad = (yStartOfQuad + 58) & 70;
        }
        // yStartOfLine = 0, 16, 32, 48, 128, 144, 160, 176
        // that is the start of the Y0/Y2 comonent's lines in macroBlock
        //  80 = 01010000 in binary
        // 176 = 10110000
        // which is why we get the step from 48 to 128
        yStartOfLine = (yStartOfLine + 80) & 176;

        // Skip odd lines of frame buffer
        fbp += 2 * lineLength - 16;
    }
}


void display_yuv_32bpp(int x, int y,
                       yuv_sample_t macroBlock[MB_SIZE],
                       const struct FrameBuffer *format) {
    yuv_sample_t *uptr = macroBlock + START_U;
    yuv_sample_t *vptr = macroBlock + START_V;
    int lineLength = format->pixelsPerLine;
    int32bpp_t *fbp = (int32bpp_t *)format->framePtr;
    int Roffset = format->Roffset;
    int Rloss = 16 - format->Rwidth; // starting out with 16 bits, need Rwidth
    int Rmax = (1 << format->Rwidth) - 1;
    int Goffset = format->Goffset;
    int Gloss = 16 - format->Gwidth;
    int Gmax = (1 << format->Gwidth) - 1;
    int Boffset = format->Boffset;
    int Bloss = 16 - format->Bwidth;
    int Bmax = (1 << format->Bwidth) - 1;
    int j, k, yStartOfLine;


    fbp += x + y * lineLength;
    yStartOfLine = 0;
    for (j = 0; j < 8; j++) {
        int yStartOfQuad = 0;

        for (k = 0; k < 8; k++) {
            yuv_sample_t *yptr = macroBlock + yStartOfLine + yStartOfQuad;
            int tu = *uptr++ - 128;
            int tv = *vptr++ - 128;
            int ruv = 409 * tv + 128;
            int guv = 100 * tu + 208 * tv - 128;
            int buv = 516 * tu;
            int dj, dk;

            for (dk = 0; dk < 2; dk++, yptr++, fbp++) {
                for (dj = 0; dj < 2; dj++) {
                    int y = yptr[8 * dj];
                    int t = (y - 16) * 298;
                    int r = (t + ruv) >> Rloss;
                    int g = (t - guv) >> Gloss;
                    int b = (t + buv) >> Bloss;
                    fbp[lineLength * dj] = (CLIP(r, Rmax) << Roffset)
                                           | (CLIP(g, Gmax) << Goffset)
                                           | (CLIP(b, Bmax) << Boffset);
                }
            }
            // yStartOfQuad = 0, 2, 4, 6, 64, 66, 68, 70
            // that is the index of the upper left corner of a 2x2 quad pixel
            // (relative to the start of the line)
            // 58 = 0111010 in binary
            // 70 = 1000110
            // which is why we get the step from 6 to 64
            yStartOfQuad = (yStartOfQuad + 58) & 70;
        }
        // yStartOfLine = 0, 16, 32, 48, 128, 144, 160, 176
        // that is the start of the Y0/Y2 comonent's lines in macroBlock
        //  80 = 01010000 in binary
        // 176 = 10110000
        // which is why we get the step from 48 to 128
        yStartOfLine = (yStartOfLine + 80) & 176;

        // Skip odd lines of frame buffer
        fbp += 2 * lineLength - 16;
    }
}

