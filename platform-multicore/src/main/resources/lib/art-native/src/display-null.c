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


static void free_display(const struct FrameBuffer *fb);

static void frame_done(const struct FrameBuffer *fb);

static void display_yuv(int x, int y,
                        yuv_sample_t macroBlock[MB_SIZE],
                        const struct FrameBuffer *format);

int allocate_display(int width,
                     int height,
                     const char *title,
                     FrameBuffer *fb) {
    fb->framePtr = 0;
    fb->bytesPerPixel = 0;
    fb->pixelsPerLine = 0;
    fb->Roffset = 0;
    fb->Rwidth = 0;
    fb->Goffset = 0;
    fb->Gwidth = 0;
    fb->Boffset = 0;
    fb->Bwidth = 0;
    fb->displaySpecific = 0;
    fb->display_yuv = display_yuv;
    fb->frame_done = frame_done;
    fb->free_display = free_display;
    return 0;
}


static void display_yuv(int x, int y,
                        yuv_sample_t macroBlock[MB_SIZE],
                        const struct FrameBuffer *format) {
}

static void free_display(const struct FrameBuffer *fb) {
}

static void frame_done(const struct FrameBuffer *fb) {
}
