/*
   lpcnet_freq.c

   freq.c from LPCnet project, I think this code originally came from
   Opus.
*/

/* Copyright (c) 2017-2018 Mozilla */
/*
   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions
   are met:

   - Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   - Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include "comp.h"
#include "lpcnet_freq.h"

#define SQUARE(x) ((x)*(x))

/* FFT bin index of centre of each band, assuming an 80 sample time
   domain window (5ms at 16 kHz), which results in 40 samples in the
   positive freq side of the FFT.  TODO - refactor this to something more generic */
static float eband5ms[] = {
  0,  1,  2,  3,  4,  5,  6,  7,  8, 10, 12, 14, 16, 20, 24, 28, 34, 40
};

/* bandE[i] is the sum of energy in a triangular window centred on
   eband5ms[i], with adjustments for first and last band */
int lpcnet_compute_band_energy(float *bandE, float *bandCentrekHz, COMP *X, float Fs, int Nfft) {
    float sum[LPCNET_FREQ_MAX_BANDS] = {0};
    int nb_bands;
    float scale;

    assert((Fs == 8000) || (Fs == 16000));
    if (Fs == 8000)
	nb_bands = 14;
    else
	nb_bands = 18;

    /* map eband5ms[] bins to our FFT size and Fs */
    scale = ((float)Nfft/2)/eband5ms[nb_bands-1];

    /* sum energy from either side of band centre */
    for (int i=0;i<nb_bands-1;i++) {
	int band_size;
	band_size = (eband5ms[i+1]-eband5ms[i])*scale;
	//fprintf(stderr, "band: %d band_size: %d offset: %d scale: %f\n", i, band_size, (int)(eband5ms[i]*scale), scale);
	for (int j=0;j<band_size;j++) {
	    float tmp;
	    float frac = (float)j/band_size;
	    int bin = eband5ms[i]*scale;
	    assert((bin+j) < Nfft/2);
	    tmp = SQUARE(X[bin + j].real);
	    tmp += SQUARE(X[bin + j].imag);
	    sum[i] += (1-frac)*tmp;
	    sum[i+1] += frac*tmp;
	}
    }

    /* first and last band only summed from half of triangular window */
    sum[0] *= 2;
    sum[nb_bands-1] *= 2;
    for (int i=0;i<nb_bands;i++) {
        bandCentrekHz[i] = eband5ms[i]*Fs/40.0/1000.0;
	bandE[i] = 10.0*log10(sum[i]);
    }

    return nb_bands;
}
