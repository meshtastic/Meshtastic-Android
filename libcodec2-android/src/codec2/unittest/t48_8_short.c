/*
   t48_8_short.c
   David Rowe
   Dec 2021
*/

#include <assert.h>
#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#include "codec2_fdmdv.h"

#define N8         180        /* processing buffer size at 8 kHz */
#define N48        (N8*FDMDV_OS_48)
#define MEM8       FDMDV_OS_TAPS_48_8K
#define FRAMES     50
#define TWO_PI     6.283185307
#define FS         48000

#define SINE

int main() {
    short in8k[MEM8+N8];
    short out48k[N48];
    FILE *f48;

    short in48k[FDMDV_OS_TAPS_48K + N48];
    short out8k[N48];
    FILE *f8, *f8in;

    int i,f,t,t1;
    float freq = 800.0;

    f48 = fopen("out48.raw", "wb");
    assert(f48 != NULL);
    f8 = fopen("out8.raw", "wb");
    assert(f8 != NULL);
    f8in = fopen("in8.raw", "wb");
    assert(f8in != NULL);

    /* clear filter memories */

    for(i=0; i<MEM8; i++)
	in8k[i] = 0.0;
    for(i=0; i<FDMDV_OS_TAPS_48K; i++)
	in48k[i] = 0.0;

    t = t1 = 0;
    for(f=0; f<FRAMES; f++) {

#ifdef DC
	for(i=0; i<N8; i++)
	    in8k[MEM8+i] = 16000.0;
#endif
#ifdef SINE
	for(i=0; i<N8; i++,t++)
	    in8k[MEM8+i] = 16000.0*cos(TWO_PI*t*freq/(FS/FDMDV_OS_48));
#endif
	fwrite(&in8k[MEM8], sizeof(short), N8, f8in);

	/* upsample  */
	fdmdv_8_to_48_short(out48k, &in8k[MEM8], N8);

	/* save 48k to disk for plotting and check out */
	fwrite(out48k, sizeof(short), N48, f48);

	/* add a 10 kHz spurious signal for fun, we want down sampler to
	   knock this out */
	for(i=0; i<N48; i++,t1++)
	    in48k[i+FDMDV_OS_TAPS_48K] = out48k[i] + 16000.0*cos(TWO_PI*t1*1E4/FS);

	/* downsample */
	fdmdv_48_to_8_short(out8k, &in48k[FDMDV_OS_TAPS_48K], N8);

	/* save 8k to disk for plotting and check out */
	fwrite(out8k, sizeof(short), N8, f8);
    }

    fclose(f48);
    fclose(f8);
    fclose(f8in);
    return 0;

}
