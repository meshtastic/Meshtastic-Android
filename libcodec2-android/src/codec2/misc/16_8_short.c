/*
   16_8_short.c
   David Rowe
   October 2018

   Utilty for resampling raw files from 16 to 8 kHz.
*/

#include <assert.h>
#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#include "codec2_fdmdv.h"

#define N8                        160 /* procssing buffer size at 8 kHz */
#define N16             (N8*FDMDV_OS)

int main(int argc, char *argv[]) {
    short in16k_short[FDMDV_OS_TAPS_16K + N16];
    FILE *f16;
    short out8k_short[N16];
    FILE *f8;
    int i;

    if (argc != 3) {
        fprintf(stderr, "usage: %s 16kHz.raw 8kHz.raw\n", argv[0]);
        exit(1);
    }
    f16 = fopen(argv[1], "rb");
    assert(f16 != NULL);
    f8 = fopen(argv[2], "wb");
    assert(f8 != NULL);

    /* clear filter memories */

    for(i=0; i<FDMDV_OS_TAPS_16K; i++)
	in16k_short[i] = 0;

    while(fread(&in16k_short[FDMDV_OS_TAPS_16K], sizeof(short), N16, f16) == N16) {
	fdmdv_16_to_8_short(out8k_short, &in16k_short[FDMDV_OS_TAPS_16K], N8);
	fwrite(out8k_short, sizeof(short), N8, f8);
    }

    fclose(f16);
    fclose(f8);
    return 0;
}
