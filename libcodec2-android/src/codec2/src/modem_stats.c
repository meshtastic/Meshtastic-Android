/*---------------------------------------------------------------------------*\

  FILE........: modem_stats.c
  AUTHOR......: David Rowe
  DATE CREATED: June 2015

  Common functions for returning demod stats from fdmdv and cohpsk modems.

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
#include <math.h>
#include "modem_stats.h"
#include "codec2_fdmdv.h"
#include "kiss_fft.h"

void modem_stats_open(struct MODEM_STATS *f)
{
    int i;

    /* zero out all the stats */

    memset(f, 0, sizeof(struct MODEM_STATS));

    /* init the FFT */

#ifndef __EMBEDDED__
    for(i=0; i<2*MODEM_STATS_NSPEC; i++)
	f->fft_buf[i] = 0.0;
    f->fft_cfg = (void*)kiss_fft_alloc (2*MODEM_STATS_NSPEC, 0, NULL, NULL);
    assert(f->fft_cfg != NULL);
#endif
}

void modem_stats_close(struct MODEM_STATS *f)
{
#ifndef __EMBEDDED__
    KISS_FFT_FREE(f->fft_cfg);
#endif
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: modem_stats_get_rx_spectrum()
  AUTHOR......: David Rowe
  DATE CREATED: 9 June 2012

  Returns the MODEM_STATS_NSPEC point magnitude spectrum of the rx signal in
  dB. The spectral samples are scaled so that 0dB is the peak, a good
  range for plotting is 0 to -40dB.

  Note only the real part of the complex input signal is used at
  present.  A complex variable is used for input for compatability
  with the other rx signal procesing.

  Successive calls can be used to build up a waterfall or spectrogram
  plot, by mapping the received levels to colours.

  The time-frequency resolution of the spectrum can be adjusted by varying
  MODEM_STATS_NSPEC.  Note that a 2* MODEM_STATS_NSPEC size FFT is reqd to get
  MODEM_STATS_NSPEC output points.  MODEM_STATS_NSPEC must be a power of 2.

  See octave/tget_spec.m for a demo real time spectral display using
  Octave. This demo averages the output over time to get a smoother
  display:

     av = 0.9*av + 0.1*mag_dB

\*---------------------------------------------------------------------------*/

#ifndef __EMBEDDED__
void modem_stats_get_rx_spectrum(struct MODEM_STATS *f, float mag_spec_dB[], COMP rx_fdm[], int nin)
{
    int   i,j;
    COMP  fft_in[2*MODEM_STATS_NSPEC];
    COMP  fft_out[2*MODEM_STATS_NSPEC];
    float full_scale_dB;

    /* update buffer of input samples */

    for(i=0; i<2*MODEM_STATS_NSPEC-nin; i++)
	f->fft_buf[i] = f->fft_buf[i+nin];
    for(j=0; j<nin; j++,i++)
	f->fft_buf[i] = rx_fdm[j].real;
    assert(i == 2*MODEM_STATS_NSPEC);

    /* window and FFT */

    for(i=0; i<2*MODEM_STATS_NSPEC; i++) {
	fft_in[i].real = f->fft_buf[i] * (0.5 - 0.5*cosf((float)i*2.0*M_PI/(2*MODEM_STATS_NSPEC)));
	fft_in[i].imag = 0.0;
    }

    kiss_fft((kiss_fft_cfg)f->fft_cfg, (kiss_fft_cpx *)fft_in, (kiss_fft_cpx *)fft_out);

    /* FFT scales up a signal of level 1 FDMDV_NSPEC */

    full_scale_dB = 20*log10(MODEM_STATS_NSPEC*FDMDV_SCALE);

    /* scale and convert to dB */

    for(i=0; i<MODEM_STATS_NSPEC; i++) {
	mag_spec_dB[i]  = 10.0*log10f(fft_out[i].real*fft_out[i].real + fft_out[i].imag*fft_out[i].imag + 1E-12);
	mag_spec_dB[i] -= full_scale_dB;
    }
}
#endif
