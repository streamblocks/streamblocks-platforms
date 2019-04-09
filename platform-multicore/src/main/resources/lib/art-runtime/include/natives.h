#ifndef ACTORS_NATIVES_H
#define ACTORS_NATIVES_H

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>


#ifdef DPRINT
#define dprint(a) printf(a)
#define dprintc(f,b,s) printf("%c[0;%d;%dm%s%c[0;38;48m",0x1B,f,b,s,0x1B)
#define dprint1(a,i1) printf(a,i1)
#define dprint2(a,i1,i2) printf(a,i1,i2)
#define dprint3(a,i1,i2,i3) printf(a,i1,i2,i3)
extern unsigned int timestamp();
#define ___xstr(a) "<trace timestamp=\"%u\" attr=\"%s\" value="___str(a)"/>\n"
#define ___str(a) #a
#define dprint_trace(attr,str) if(((AbstractActorInstance*)thisActor)->traceFile) fprintf(((AbstractActorInstance*)thisActor)->traceFile,"<trace timestamp=\"%u\" attr=\"%s\" value=\"%s\"/>\n", timestamp(0),attr,str);
#define dprint_trace1(attr,str,i1) if(((AbstractActorInstance*)thisActor)->traceFile) fprintf(((AbstractActorInstance*)thisActor)->traceFile,___xstr(str), timestamp(0),attr,i1);
#define dprint_trace2(attr,str,i1,i2) if(((AbstractActorInstance*)thisActor)->traceFile) fprintf(((AbstractActorInstance*)thisActor)->traceFile,___xstr(str), timestamp(0),attr,i1,i2);
#define dprint_trace3(attr,str,i1,i2,i3) if(((AbstractActorInstance*)thisActor)->traceFile) fprintf(((AbstractActorInstance*)thisActor)->traceFile,___xstr(str), timestamp(0),attr,i1,i2,i3);
#else
#define dprint(a)
#define dprintc(f, b, s)
#define dprint1(a, i1)
#define dprint2(a, i1, i2)
#define dprint3(a, i1, i2, i3)
#define dprint_trace(attr, str)
#define dprint_trace1(attr, str, i1)
#define dprint_trace2(attr, str, i1, i2)
#define dprint_trace3(attr, str, i1, i2, i3)
#endif
#endif
