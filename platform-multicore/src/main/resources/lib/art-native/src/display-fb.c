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
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/ioctl.h>
#include <linux/fb.h>

#include "display.h"


typedef struct {
    int fd;
    void *frameBuffer;
    int screenSize;
} fb_display_t;


static void free_display(const struct FrameBuffer *format);

static void frame_done(const struct FrameBuffer *format);

int allocate_display(int width,
                     int height,
                     const char *title,
                     FrameBuffer *fb) {
    struct fb_var_screeninfo vinfo;
    struct fb_fix_screeninfo finfo;
    int fd;
    char *startOfFrame;
    int bytesPerPixel;
    int pixelsPerLine;
    fb_display_t *fb_specific;

    fb->displaySpecific = 0;

    /* Open the file for reading and writing */
    fd = open("/dev/fb0", O_RDWR);
    if (fd == -1) {
        perror("/dev/fb0");
        return 1;
    }

    /* Get fixed screen information */
    if (ioctl(fd, FBIOGET_FSCREENINFO, &finfo)) {
        perror("FBIOGET_FSCREENINFO");
        return 1;
    }

    /*  Get variable screen information */
    if (ioctl(fd, FBIOGET_VSCREENINFO, &vinfo)) {
        perror("FBIOGET_VSCREENINFO");;
        return 1;
    }

    /* Map the device to memory */
    startOfFrame = (char *) mmap(0, finfo.smem_len,
                                 PROT_READ | PROT_WRITE, MAP_SHARED,
                                 fd, 0);

    if ((int) startOfFrame == -1) {
        perror("mmap()");
        return 1;
    }

    printf("%dx%d, %dbpp\n",
           vinfo.xres,
           vinfo.yres,
           vinfo.bits_per_pixel);


    bytesPerPixel = (vinfo.bits_per_pixel + 7) >> 3;
    pixelsPerLine = finfo.line_length / bytesPerPixel;

    fb->framePtr = startOfFrame
                   + (vinfo.yoffset * pixelsPerLine + vinfo.xoffset) * bytesPerPixel;

    fb->bytesPerPixel = bytesPerPixel;
    fb->pixelsPerLine = pixelsPerLine;

    fb->Roffset = vinfo.red.offset;
    fb->Rwidth = vinfo.red.length;
    fb->Goffset = vinfo.green.offset;
    fb->Gwidth = vinfo.green.length;
    fb->Boffset = vinfo.blue.offset;
    fb->Bwidth = vinfo.blue.length;

    /*
     * Fill-in FB specific stuff
     */
    fb_specific = (fb_display_t *) malloc(sizeof(fb_display_t));
    fb_specific->fd = fd;
    fb_specific->frameBuffer = startOfFrame;
    fb_specific->screenSize = finfo.smem_len;
    fb->displaySpecific = fb_specific;

    if (fb->bytesPerPixel == 2) {
        fb->display_yuv = display_yuv_16bpp;
    } else if (fb->bytesPerPixel == 4) {
        fb->display_yuv = display_yuv_32bpp;
    } else {
        fprintf(stderr,
                "display-fb: unsupported pixel size: %dbpp\n",
                vinfo.bits_per_pixel);
        exit(1);
    }

    fb->frame_done = frame_done;
    fb->free_display = free_display;
    return 0;
}

static void free_display(const struct FrameBuffer *fb) {
    fb_display_t *disp = (fb_display_t *) fb->displaySpecific;

    if (disp != 0) {
        munmap(disp->frameBuffer, disp->screenSize);
        close(disp->fd);
        free(disp);
    }
}


static void frame_done(const struct FrameBuffer *fb) {
}
