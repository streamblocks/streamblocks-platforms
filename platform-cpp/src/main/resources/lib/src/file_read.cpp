#define _CRT_SECURE_NO_WARNINGS

#include <iostream>
#include <fstream>
#include <string>

#include "file_read.h"
#include "get_opt.h"
#include <stdlib.h>

static std::ifstream file;

static int loopsCount;

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>

char getLastUserChar() {
    HANDLE tui_handle = GetStdHandle(STD_INPUT_HANDLE);
    DWORD tui_evtc;
    char retVal = 0;
    INPUT_RECORD tui_inrec;
    DWORD tui_numread;
    BOOLEAN tui_havehappened = FALSE;

    GetNumberOfConsoleInputEvents(tui_handle,&tui_evtc);
    while (tui_evtc > 0) {
        ReadConsoleInput(tui_handle,&tui_inrec,1,&tui_numread);
        if (tui_inrec.EventType == KEY_EVENT) {
            if (tui_inrec.Event.KeyEvent.bKeyDown) {
                retVal = tui_inrec.Event.KeyEvent.uChar.AsciiChar;
                tui_havehappened = TRUE;
            }
        }
        GetNumberOfConsoleInputEvents(tui_handle,&tui_evtc);
    }

    return retVal;
}

#elif __linux

char getLastUserChar() {
	char val = 0;
	   fd_set rfds;
	   struct timeval tv;
	   int retval;

    FD_ZERO(&rfds);
    FD_SET(0, &rfds);

    tv.tv_sec = 0;
    tv.tv_usec = 1;

   retval = select(1, &rfds, NULL, NULL, &tv);

   if (retval > 0) {
        val = getchar();
    }
	return val;
}

#endif

void source_init() 
{
	if (input_file.empty())
	{
		std::cerr << "No input file given!" << std::endl;
		exit(1);
	}

	file.open(input_file.c_str(), std::ios::binary);
	if (!file.is_open())
	{
		std::cerr << "could not open file "<<  input_file << std::endl;
		exit(1);
	}

	loopsCount = nbLoops;
}

int source_sizeOfFile()
{ 
	file.seekg(0L, std::ios::end);
	long size = file.tellg();
	file.seekg(0L, std::ios::beg);
	return size;
}

void source_rewind()
{
	file.clear();
	file.seekg(0, std::ios::beg);
}

unsigned int source_readByte()
{
	return file.get();
}

void source_readNBytes(unsigned char outTable[], unsigned int nbTokenToRead)
{
	file.read((char *)outTable, nbTokenToRead);
}

unsigned int source_getNbLoop(void)
{
	return nbLoops;
}

void source_decrementNbLoops()
{
	--loopsCount;
}

bool source_isMaxLoopsReached()
{
	return nbLoops != -1 && loopsCount <= 0;
}

void source_exit(int exitCode)
{
}
