/*---------------------------------------------------------------------------*\

  FILE........: fm_demod.c
  AUTHOR......: David Rowe
  DATE CREATED: Feb 24 2015

  Given an input raw file (44.4 kHz, 16 bit shorts) with a FM signal centered
  11.1 kHz, outputs a file of demodulated audio samples.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2015 David Rowe

  All rights reserved.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License version 2.1, as
  published by the Free Software Foundation.  This program is
  distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, see <http://www.gnu.org/licenses/>.
*/

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <errno.h>

#include "codec2_fm.h"
#include "octave.h"

#define N 160

#define TEST_MOD_COMP

int main(int argc, char *argv[])
{
    FILE         *fin, *fout;
    struct FM    *fm;
    short         buf[N*2];
    float         rx[N];
#if defined(TEST_MODE) && !defined(TEST_MODE_COMP)
    float         rx_out[N];
#endif
    COMP          out_comp[N];
    int           i;

    if (argc < 2) {
	printf("usage: %s InputFMRawFile OutputSpeechRawFile\n", argv[0]);
	printf("e.g    %s fm.raw fm_demodulated.raw\n", argv[0]);
	exit(1);
    }

    if (strcmp(argv[1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[1],"rb")) == NULL ) {
	fprintf(stderr, "Error opening input file: %s: %s.\n",
         argv[1], strerror(errno));
	exit(1);
    }

    if (strcmp(argv[2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[2],"wb")) == NULL ) {
	fprintf(stderr, "Error opening output file: %s: %s.\n",
         argv[2], strerror(errno));
	exit(1);
    }

    fm         = fm_create(N);
    fm->Fs     = 48000.0;
    fm->fm_max = 3000.0;
    fm->fd     = 5000.0;
    fm->fc     = 0;

    while(fread(buf, sizeof(short), N, fin) == N) {
	for(i=0; i<N; i++) {
	    rx[i] = ((float)buf[i])/16384;
        }
#ifdef TEST_MOD
	fm_mod(fm, rx, rx_out);
#else
#ifdef  TEST_MOD_COMP
	fm_mod_comp(fm, rx, out_comp);
#else
        fm_demod(fm, rx_out, rx);
#endif
#endif


#ifdef TEST_MOD_COMP
	for(i=0; i<N; i++) {
	    buf[i*2    ] = 16384*out_comp[i].real;
            buf[1+(i*2)] = 16384*out_comp[i].imag;
        }
	fwrite(buf, sizeof(short), N*2, fout);
#else
	for(i=0; i<N; i++) {
	    buf[i] = 16384*rx_out[i];
        }
        fwrite(buf, sizeof(short), N, fout);
#endif
    }

    fm_destroy(fm);
    fclose(fin);
    fclose(fout);

    return 0;
}
