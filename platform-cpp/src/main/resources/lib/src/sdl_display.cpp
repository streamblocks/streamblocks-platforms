#include <stdio.h>
#include <iostream>
#include <stdlib.h>

#include "sdl_display.h"
#ifndef NO_DISPLAY
static SDL_Surface *m_screen;
static SDL_Overlay *m_overlay;
#else
#include <chrono>
using Clock = std::chrono::high_resolution_clock;
using Ms = std::chrono::milliseconds;
#endif
#include "get_opt.h"

#ifndef NO_DISPLAY
static void press_a_key(int code) {
	char buf[2];
	char *ptrBuff = NULL;

	printf("Press a key to continue\n");
	ptrBuff=fgets(buf, 2, stdin);
	if(ptrBuff == NULL) {
		fprintf(stderr,"error when using fgets\n");
	}
	exit(code);
}

void getSdlOverlay(SDL_Overlay **overlay) {
	*overlay = m_overlay;
}
#endif

static unsigned int startTime;
static unsigned int relativeStartTime;
static int lastNumPic;
static int numPicturesDecoded;

void displayYUV_init()
{
#ifndef NO_DISPLAY
	// First, initialize SDL's video subsystem.
	if (SDL_Init( SDL_INIT_VIDEO ) < 0) {
		fprintf(stderr, "Video initialization failed: %s\n", SDL_GetError());
		press_a_key(-1);
	}

	SDL_WM_SetCaption("display", NULL);

	atexit(SDL_Quit);
#endif
}

void displayYUV_setSize(int width, int height)
{
#ifndef NO_DISPLAY
	//std::cout << "set display to " << width << " x " << height << std::endl;
	m_screen = SDL_SetVideoMode(width, height, 0, SDL_HWSURFACE);
	if (m_screen == NULL) {
		fprintf(stderr, "Couldn't set video mode!\n");
		press_a_key(-1);
	}

	if (m_overlay != NULL) {
		SDL_FreeYUVOverlay(m_overlay);
	}

	m_overlay = SDL_CreateYUVOverlay(width, height, SDL_YV12_OVERLAY, m_screen);
	if (m_overlay == NULL) {
		fprintf(stderr, "Couldn't create overlay: %s\n", SDL_GetError());
		press_a_key(-1);
	}
#endif
}

void displayYUV_displayPicture(unsigned char pictureBufferY[], unsigned char pictureBufferU[], unsigned char pictureBufferV[], short pictureWidth, short pictureHeight) 
{
#ifndef NO_DISPLAY
	static unsigned short lastWidth = 0;
	static unsigned short lastHeight = 0;

	SDL_Rect rect = { 0, 0, (short unsigned int) pictureWidth, (short unsigned int) pictureHeight };

	SDL_Event event;

	if((pictureHeight != lastHeight) || (pictureWidth != lastWidth)) {
		displayYUV_setSize(pictureWidth, pictureHeight);
		lastHeight = pictureHeight;
		lastWidth  = pictureWidth;
	}
	if (SDL_LockYUVOverlay(m_overlay) < 0) {
		fprintf(stderr, "Can't lock screen: %s\n", SDL_GetError());
		press_a_key(-1);
	}

	memcpy(m_overlay->pixels[0], pictureBufferY, pictureWidth * pictureHeight );
	memcpy(m_overlay->pixels[1], pictureBufferV, pictureWidth * pictureHeight / 4 );
	memcpy(m_overlay->pixels[2], pictureBufferU, pictureWidth * pictureHeight / 4 );

	SDL_UnlockYUVOverlay(m_overlay);
	SDL_DisplayYUVOverlay(m_overlay, &rect);

	/* Grab all the events off the queue. */
	while (SDL_PollEvent(&event)) {
		switch (event.type) {
		case SDL_KEYDOWN:
		case SDL_QUIT:
			exit(0);
			break;
		default:
			break;
		}
	}
#endif
}

/**
 * @brief Return the number of frames the user want to decode before exiting the application.
 * If user didn't use the -f flag, it returns -1 (DEFAULT_INFINITEà).
 * @return The
 */
int displayYUV_getNbFrames() 
{
	return nbFrames;
}

unsigned char displayYUV_getFlags()
{
	return 3;
}

int compareYUV_compareComponent(const int x_size, const int y_size, const int x_size_test_img, 
	const unsigned char *true_img_uchar, const unsigned char *test_img_uchar,
	unsigned char SizeMbSide, char Component_Type) 
{
	return 0;
}

void compareYUV_init()
{
}

void compareYUV_readComponent(unsigned char **Component, unsigned short width, unsigned short height, char sizeChanged)
{
}

void compareYUV_comparePicture(unsigned char pictureBufferY[], unsigned char pictureBufferU[],
	unsigned char pictureBufferV[], short pictureWidth,
	short pictureHeight)
{
}

static void print_fps_avg(void) {

#ifndef NO_DISPLAY
	unsigned int endTime = SDL_GetTicks();
#else
	unsigned int endTime = std::chrono::duration_cast<Ms>(Clock::now().time_since_epoch()).count();

#endif
	printf("%i images in %f seconds: %f FPS\n", numPicturesDecoded,
		(float) (endTime - startTime)/ 1000.0f,
		1000.0f * (float) numPicturesDecoded / (float) (endTime -startTime));
}

void fpsPrintInit() {
#ifndef NO_DISPLAY
	startTime = SDL_GetTicks();
#else
	startTime = std::chrono::duration_cast<Ms>(Clock::now().time_since_epoch()).count();
#endif
	relativeStartTime = startTime;
	numPicturesDecoded = 0;
	lastNumPic = 0;
	atexit(print_fps_avg);
}

void fpsPrintNewPicDecoded(void) {
	unsigned int endTime;
	numPicturesDecoded++;
#ifndef NO_DISPLAY
	endTime = SDL_GetTicks();
#else
	endTime = std::chrono::duration_cast<Ms>(Clock::now().time_since_epoch()).count();
#endif
	if (endTime - relativeStartTime > 5000) {
		printf("%f images/sec\n", 1000.0f * (float) (numPicturesDecoded - lastNumPic) / (float) (endTime - relativeStartTime));
		relativeStartTime = endTime;
		lastNumPic = numPicturesDecoded;
	}
}
