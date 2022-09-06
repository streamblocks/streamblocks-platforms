#include <iostream>

#include "sb_native_compareyuv.h"

using namespace std::video::display;

int std::video::display::compareYUV_compareComponent(
        const int x_size, const int y_size, const int x_size_test_img,
        const unsigned char *true_img_uchar,
        const unsigned char *test_img_uchar,
        unsigned char SizeMbSide, char Component_Type) {
    int pix_x, pix_y, blk_x, blk_y;
    int error = 0;
    int WidthSzInBlk = x_size / SizeMbSide;
    int HeightSzInBlk = y_size / SizeMbSide;

    for (blk_y = 0; blk_y < HeightSzInBlk; blk_y++) {
        for (blk_x = 0; blk_x < WidthSzInBlk; blk_x++) {
            for (pix_y = 0; pix_y < SizeMbSide; pix_y++) {
                for (pix_x = 0; pix_x < SizeMbSide; pix_x++) {
                    int Idx_pix = (blk_y * SizeMbSide + pix_y) * x_size + (blk_x * SizeMbSide + pix_x);
                    int Idx_test_pix = (blk_y * SizeMbSide + pix_y) * x_size_test_img + (blk_x * SizeMbSide + pix_x);

                    if (true_img_uchar[Idx_pix] - test_img_uchar[Idx_test_pix] != 0) {
                        error++;
                        if (error < 50) {
                            const unsigned int blkIdxX = pix_x * 4 / SizeMbSide;
                            const unsigned int blkIdxY = pix_y * 4 / SizeMbSide;
                            const unsigned int inverse4x4Tab[16] =
                                    {0, 1, 4, 5, 2, 3, 6, 7, 8, 9,
                                     12, 13, 10, 11, 14, 15};
                            const unsigned int blkIdx = inverse4x4Tab[blkIdxX +
                                                                      4 * blkIdxY];
                            fprintf(stderr,
                                    "\nerror %3d instead of %3d at position : mb=(%d;%d) , loc_in_mb=(%d;%d) , blk: %d",
                                    test_img_uchar[Idx_pix], true_img_uchar
                                    [Idx_pix], blk_x, blk_y, pix_x, pix_y, blkIdx);
                        }
                    }
                }
            }
        }
    }

    if (error != 0) {
        fprintf(stderr, "\n%d error(s) in %c Component !!!\n", error, Component_Type);
        exit(-1);
    }
    return error;
}

void std::video::display::compareYUV_init() {
    //Fix me!! Dirty but it's the only way for the moment.
    /*
    if (opt->yuv_file == NULL) {
        useCompare = 0;
        return;
    }
    useCompare = 1;
    ptrFile = fopen(opt->yuv_file, "rb");
    if (ptrFile == NULL) {
        fprintf(stderr, "Cannot open yuv_file concatenated input file '%s' for reading\n", opt->yuv_file);
        exit(-1);
    }

    fileSize = fsize(ptrFile);

    compareErrors = 0;
     */
}

void
std::video::display::compareYUV_readComponent(unsigned char **Component, unsigned short width, unsigned short height,
                                              char sizeChanged) {
    size_t numByteRead;

    if (*Component == NULL) {
        *Component = (unsigned char *) malloc(width * height * sizeof(unsigned char));
    } else {
        if (sizeChanged) {
            *Component = (unsigned char *) realloc(*Component, width * height * sizeof(unsigned char));
        }
    }
    if (*Component == NULL) {
        fprintf(stderr, "Problem when allocating memory.\n");
        exit(-5);
    }
    numByteRead = fread(*Component, sizeof(unsigned char), width * height, ptrFile);
    if (numByteRead != (width * height)) {
        long long currPos = ftell(ptrFile);
        fseek(ptrFile, 0, SEEK_END);
        if (currPos == ftell(ptrFile)) {
            printf("\nComparison done.\nExiting.\n");
            exit(0);
        }
        fprintf(stderr, "Error when using fread\n");
        exit(-6);
    }
}

void std::video::display::compareYUV_comparePicture(unsigned char *pictureBufferY, unsigned char *pictureBufferU,
                                                    unsigned char *pictureBufferV, unsigned char pictureWidth,
                                                    unsigned short pictureHeight) {
    static unsigned int frameNumber = 0;
    static int prevXSize = 0;
    static int prevYSize = 0;

    static unsigned char *Y = NULL;
    static unsigned char *U = NULL;
    static unsigned char *V = NULL;

    char sizeChanged;

    if (useCompare) {
        int numErrors = 0;

        printf("Frame number %d", frameNumber);
        frameNumber++;

        sizeChanged = ((prevXSize * prevYSize) != (pictureWidth * pictureHeight)) ? 1 : 0;
        compareYUV_readComponent(&Y, pictureWidth, pictureHeight, sizeChanged);
        compareYUV_readComponent(&U, pictureWidth / 2, pictureHeight / 2, sizeChanged);
        compareYUV_readComponent(&V, pictureWidth / 2, pictureHeight / 2, sizeChanged);

        numErrors += compareYUV_compareComponent(pictureWidth, pictureHeight, pictureWidth, Y, pictureBufferY, 16, 'Y');
        numErrors += compareYUV_compareComponent(pictureWidth >> 1, pictureHeight >> 1, pictureWidth >> 1, U,
                                                 pictureBufferU, 8, 'U');
        numErrors += compareYUV_compareComponent(pictureWidth >> 1, pictureHeight >> 1, pictureWidth >> 1, V,
                                                 pictureBufferV, 8, 'V');

        if (numErrors == 0) {
            printf("; no error detected !\n");
        } else {
            printf("; %d errors detected !\n", numErrors);
            // compareErrors += numErrors;
        }

        if (ftell(ptrFile) == fileSize) {
            rewind(ptrFile);
            frameNumber = 0;
        }
        prevXSize = pictureWidth;
        prevYSize = pictureHeight;
    }
}