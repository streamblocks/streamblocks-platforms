#include <stdio.h>
extern void printDebug(const char* format, ...);

#ifdef DPRINT
#define dprint(a) printDebug(a)
#define dprintc(f,b,s) printDebug("%c[0;%d;%dm%s%c[0;38;48m",0x1B,f,b,s,0x1B)
#define dprint1(a,i1) printDebug(a,i1)
#define dprint2(a,i1,i2) printDebug(a,i1,i2)
#define dprint3(a,i1,i2,i3) printDebug(a,i1,i2,i3)
#else
#define dprint(a)
#define dprintc(f,b,s)
#define dprint1(a,i1)
#define dprint2(a,i1,i2)
#define dprint3(a,i1,i2,i3)
#endif