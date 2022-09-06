#include "sb_native_source.h"

using namespace std::io::source;

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <winsock2.h>

char std::io::source::getLastUserChar() {
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

#else

char std::io::source::getLastUserChar() {
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

void std::io::source::source_init() {
    input_file = sb::options::input_file;
    if (input_file.empty()) {
        std::cerr << "No input file given!" << std::endl;
        exit(1);
    }

    file.open(input_file.c_str(), std::ios::binary);
    if (!file.is_open()) {
        std::cerr << "could not open file " << input_file << std::endl;
        exit(1);
    }

    loopsCount = sb::options::nbLoops;
}

int std::io::source::source_sizeOfFile() {
    file.seekg(0L, std::ios::end);
    long size = file.tellg();
    file.seekg(0L, std::ios::beg);
    return size;
}

void std::io::source::source_rewind() {
    file.clear();
    file.seekg(0, std::ios::beg);
}

unsigned int std::io::source::source_readByte() {
    return file.get();
}

void std::io::source::source_readNBytes(unsigned char outTable[], unsigned int nbTokenToRead) {
    file.read((char *) outTable, nbTokenToRead);
}

unsigned int std::io::source::source_getNbLoop(void) {
    return sb::options::nbLoops;
}

void std::io::source::source_decrementNbLoops() {
    --loopsCount;
}

bool std::io::source::source_isMaxLoopsReached() {
    return sb::options::nbLoops != -1 && loopsCount <= 0;
}

void std::io::source::source_exit(int exitCode) {
    exit(exitCode);
}