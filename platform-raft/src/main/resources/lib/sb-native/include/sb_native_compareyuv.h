#pragma once

#include "options.h"

namespace std {
    namespace video {
        namespace display {
            static FILE *ptrFile;
            static unsigned int fileSize;
            static char useCompare;

            int compareYUV_compareComponent(
                    const int x_size, const int y_size, const int x_size_test_img,
                    const unsigned char *true_img_uchar,
                    const unsigned char *test_img_uchar,
                    unsigned char SizeMbSide, char Component_Type);

            void compareYUV_init();

            void
            compareYUV_readComponent(unsigned char **Component, unsigned short width, unsigned short height,
                                     char sizeChanged);

            void compareYUV_comparePicture(unsigned char *pictureBufferY, unsigned char *pictureBufferU,
                                           unsigned char *pictureBufferV, unsigned char pictureWidth,
                                           unsigned short pictureHeight);
        }
    }
}