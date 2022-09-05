#ifndef __SDL_DISPLAY_H__
#define __SDL_DISPLAY_H__

#ifndef NO_DISPLAY
#ifdef _WIN32 
#include <SDL.h>
#else
#include <SDL/SDL.h>
#endif
#endif

#ifndef NO_DISPLAY
static void press_a_key(int code);
#endif

void displayYUV_init();

void displayYUV_setSize(int width, int height);

void displayYUV_displayPicture(unsigned char pictureBufferY[], unsigned char pictureBufferU[], unsigned char pictureBufferV[], short pictureWidth, short pictureHeight);
	
int displayYUV_getNbFrames();

unsigned char displayYUV_getFlags();

int compareYUV_compareComponent(const int x_size, const int y_size, const int x_size_test_img, 
	const unsigned char *true_img_uchar, const unsigned char *test_img_uchar,
	unsigned char SizeMbSide, char Component_Type);

void compareYUV_init();

void compareYUV_readComponent(unsigned char **Component, unsigned short width, unsigned short height, char sizeChanged);

void compareYUV_comparePicture(unsigned char pictureBufferY[], unsigned char pictureBufferU[],
	unsigned char pictureBufferV[], short pictureWidth,
	short pictureHeight);


static void print_fps_avg(void);

void fpsPrintInit();

void fpsPrintNewPicDecoded(void);

#endif // __SDL_DISPLAY_H__
