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
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include <sys/timeb.h>
#include <sys/mman.h>
#include <sys/ioctl.h>
#include <gtk/gtk.h>

#include "display.h"


typedef struct {
    int width;
    int height;
    GtkWidget *window;
    GtkWidget *darea;
    guchar *rgbbuf;
} gtk_display_t;

static void free_display(const struct FrameBuffer *fb);

static void frame_done(const struct FrameBuffer *fb);

static void on_darea_expose(GtkWidget *widget, GdkEventExpose *event, gpointer user_data);


int allocate_display(int width,
                     int height,
                     const char *title,
                     FrameBuffer *fb) {
    gtk_display_t *disp = (gtk_display_t *) malloc(sizeof(gtk_display_t));
    int one = 1;
    char *littleEndian = (char *) &one;

    gtk_init(NULL, NULL);
    gdk_init(NULL, NULL);
    // gdk_rgb_init(); /* depreceated -does nothing */
    disp->width = width;
    disp->height = height;
    disp->window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    disp->darea = gtk_drawing_area_new();
    disp->rgbbuf = malloc(4 * width * height);
    gtk_drawing_area_size(GTK_DRAWING_AREA(disp->darea), width, height);
    gtk_container_add(GTK_CONTAINER(disp->window), disp->darea);
    gtk_window_set_title(GTK_WINDOW(disp->window), title);

    fb->framePtr = disp->rgbbuf;
    fb->bytesPerPixel = 4;
    fb->pixelsPerLine = width;

    if (*littleEndian) {
        fb->Roffset = 0;
        fb->Goffset = 8;
        fb->Boffset = 16;
    } else {
        fb->Roffset = 24;
        fb->Goffset = 16;
        fb->Boffset = 8;

    }
    fb->Rwidth = 8;
    fb->Gwidth = 8;
    fb->Bwidth = 8;

    fb->displaySpecific = disp;

    fb->display_yuv = display_yuv_32bpp;
    fb->frame_done = frame_done;
    fb->free_display = free_display;

    return 0;
}


static void free_display(const struct FrameBuffer *fb) {
    gtk_display_t *disp = (gtk_display_t *) fb->displaySpecific;
    if (disp != 0) {
        free(disp->rgbbuf);
        free(disp);
    }
}


static void frame_done(const struct FrameBuffer *fb) {
    gtk_display_t *disp = (gtk_display_t *) fb->displaySpecific;

    gtk_signal_connect(GTK_OBJECT(disp->darea), "expose-event", GTK_SIGNAL_FUNC(on_darea_expose), disp);
    gtk_drawing_area_size(GTK_DRAWING_AREA(disp->darea), disp->width, disp->height);
    gtk_widget_show_all(disp->window);
    gtk_main();

}

static void on_darea_expose(GtkWidget *widget, GdkEventExpose *event, gpointer user_data) {
    gtk_display_t *disp = (gtk_display_t *) user_data;
    gdk_draw_rgb_32_image(widget->window,
                          widget->style->fg_gc[GTK_STATE_NORMAL],
                          0, 0, disp->width, disp->height,
                          GDK_RGB_DITHER_MAX, disp->rgbbuf,
                          disp->width * 4);
    gtk_main_quit();
}
