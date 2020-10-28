/*
 * Copyright (c) Ericsson AB, 2013
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

/**
 * Some native Linux/C code that sets up /dev/dsp and uses its as a sink for floating-point audio samples
 */

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <error.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <stdint.h>
#include <sys/ioctl.h>
#include <sys/soundcard.h>

static struct
{
    int fd;
    char *buffer;
    int sizeInSamples;
    int bytesPerSample;
} speakerState;

static void set_ioctl(uint32_t devdsp, uint32_t request, uint32_t value, const char *errorMsg)
{
    uint32_t value_set = value;
    int result = ioctl(devdsp, request, &value_set);

    if (result == -1)
    {
        perror("set_ioctl");
        exit(-1);
    }
    else if (value_set != value)
    {
        error(-1, 1, "set_ioctl: could not set %s", errorMsg);
    }
}

int openAudioOutput(int numChannels, int sampleRate, int bitsPerSample)
{
    int devdsp = open("/dev/dsp", O_RDWR);
    int format = AFMT_U8;

    if (devdsp < 0)
    {
        perror("Couldn't open /dev/dsp for output");
        exit(-1);
    }

    if (bitsPerSample > 8)
    {
        if (bitsPerSample <= 16)
            format = AFMT_S16_LE;
    }

    set_ioctl(devdsp, SNDCTL_DSP_SETFMT, format, "format");
    set_ioctl(devdsp, SNDCTL_DSP_CHANNELS, numChannels, "channels");
    set_ioctl(devdsp, SNDCTL_DSP_SPEED, sampleRate, "sample rate");

    speakerState.fd = devdsp;
    speakerState.buffer = 0;
    speakerState.bytesPerSample = (bitsPerSample + 7) / 8;
    speakerState.sizeInSamples = 0;
    return devdsp;
}

void audioOutput(int fd, float *x, int N)
{
    char *buffer8 = speakerState.buffer;

    if (speakerState.sizeInSamples < N)
    {
        buffer8 = static_cast<char *>(malloc(N * speakerState.bytesPerSample));
        speakerState.buffer = buffer8;
        speakerState.sizeInSamples = N;
    }

    if (speakerState.bytesPerSample == 2)
    {
        int16_t *buffer16 = (int16_t *)buffer8;
        int i;

        for (i = 0; i < N; ++i)
        {
            int y = (int)(32767 * x[i]);
            buffer16[i] = (y < -32767) ? 32767 : ((y > 32767) ? 32767 : y);
        }
    }
    else
    {
        int i;

        for (i = 0; i < N; ++i)
        {
            int y = (int)(127 * x[i]) + 128;
            buffer8[i] = (y < 0) ? 0 : ((y > 255) ? 255 : y);
        }
    }

    write(fd, buffer8, speakerState.bytesPerSample * N);
}
