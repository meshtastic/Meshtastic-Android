/*---------------------------------------------------------------------------*\

  FILE........: sine.c
  AUTHOR......: David Rowe
  DATE CREATED: 19/8/2010

  Sinusoidal analysis and synthesis functions.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 1990-2010 David Rowe

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

/*---------------------------------------------------------------------------*\

				INCLUDES

\*---------------------------------------------------------------------------*/

#include <stdlib.h>
#include <stdio.h>
#include <math.h>

#include "defines.h"
#include "sine.h"
#include "kiss_fft.h"

#define HPF_BETA 0.125

/*---------------------------------------------------------------------------*\

				HEADERS

\*---------------------------------------------------------------------------*/

void hs_pitch_refinement(MODEL *model, COMP Sw[], float pmin, float pmax,
			 float pstep);

/*---------------------------------------------------------------------------*\

				FUNCTIONS

\*---------------------------------------------------------------------------*/

C2CONST c2const_create(int Fs, float framelength_s) {
    C2CONST c2const;

    assert((Fs == 8000) || (Fs == 16000));
    c2const.Fs = Fs;
    c2const.n_samp = round(Fs*framelength_s);
    c2const.max_amp = floor(Fs*P_MAX_S/2);
    c2const.p_min = floor(Fs*P_MIN_S);
    c2const.p_max = floor(Fs*P_MAX_S);
    c2const.m_pitch = floor(Fs*M_PITCH_S);
    c2const.Wo_min = TWO_PI/c2const.p_max;
    c2const.Wo_max = TWO_PI/c2const.p_min;

    if (Fs == 8000) {
        c2const.nw = 279;
    } else {
        c2const.nw = 511;  /* actually a bit shorter in time but lets us maintain constant FFT size */
    }

    c2const.tw = Fs*TW_S;

    /*
    fprintf(stderr, "max_amp: %d m_pitch: %d\n", c2const.n_samp, c2const.m_pitch);
    fprintf(stderr, "p_min: %d p_max: %d\n", c2const.p_min, c2const.p_max);
    fprintf(stderr, "Wo_min: %f Wo_max: %f\n", c2const.Wo_min, c2const.Wo_max);
    fprintf(stderr, "nw: %d tw: %d\n", c2const.nw, c2const.tw);
    */

    return c2const;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: make_analysis_window
  AUTHOR......: David Rowe
  DATE CREATED: 11/5/94

  Init function that generates the time domain analysis window and it's DFT.

\*---------------------------------------------------------------------------*/

void make_analysis_window(C2CONST *c2const, codec2_fft_cfg fft_fwd_cfg, float w[], float W[])
{
  float m;
  COMP  wshift[FFT_ENC];
  int   i,j;
  int   m_pitch = c2const->m_pitch;
  int   nw      = c2const->nw;

  /*
     Generate Hamming window centered on M-sample pitch analysis window

  0            M/2           M-1
  |-------------|-------------|
        |-------|-------|
            nw samples

     All our analysis/synthsis is centred on the M/2 sample.
  */

  m = 0.0;
  for(i=0; i<m_pitch/2-nw/2; i++)
    w[i] = 0.0;
  for(i=m_pitch/2-nw/2,j=0; i<m_pitch/2+nw/2; i++,j++) {
    w[i] = 0.5 - 0.5*cosf(TWO_PI*j/(nw-1));
    m += w[i]*w[i];
  }
  for(i=m_pitch/2+nw/2; i<m_pitch; i++)
    w[i] = 0.0;

  /* Normalise - makes freq domain amplitude estimation straight
     forward */

  m = 1.0/sqrtf(m*FFT_ENC);
  for(i=0; i<m_pitch; i++) {
    w[i] *= m;
  }

  /*
     Generate DFT of analysis window, used for later processing.  Note
     we modulo FFT_ENC shift the time domain window w[], this makes the
     imaginary part of the DFT W[] equal to zero as the shifted w[] is
     even about the n=0 time axis if nw is odd.  Having the imag part
     of the DFT W[] makes computation easier.

     0                      FFT_ENC-1
     |-------------------------|

      ----\               /----
           \             /
            \           /          <- shifted version of window w[n]
             \         /
              \       /
               -------

     |---------|     |---------|
       nw/2              nw/2
  */

  COMP temp[FFT_ENC];

  for(i=0; i<FFT_ENC; i++) {
    wshift[i].real = 0.0;
    wshift[i].imag = 0.0;
  }
  for(i=0; i<nw/2; i++)
    wshift[i].real = w[i+m_pitch/2];
  for(i=FFT_ENC-nw/2,j=m_pitch/2-nw/2; i<FFT_ENC; i++,j++)
   wshift[i].real = w[j];

  codec2_fft(fft_fwd_cfg, wshift, temp);

  /*
      Re-arrange W[] to be symmetrical about FFT_ENC/2.  Makes later
      analysis convenient.

   Before:


     0                 FFT_ENC-1
     |----------|---------|
     __                   _
       \                 /
        \_______________/

   After:

     0                 FFT_ENC-1
     |----------|---------|
               ___
              /   \
     ________/     \_______

  */


  for(i=0; i<FFT_ENC/2; i++) {
      W[i] = temp[i + FFT_ENC / 2].real;
      W[i + FFT_ENC / 2] = temp[i].real;
  }

}

/*---------------------------------------------------------------------------*\

  FUNCTION....: hpf
  AUTHOR......: David Rowe
  DATE CREATED: 16 Nov 2010

  High pass filter with a -3dB point of about 160Hz.

    y(n) = -HPF_BETA*y(n-1) + x(n) - x(n-1)

\*---------------------------------------------------------------------------*/

float hpf(float x, float states[])
{
    states[0] = -HPF_BETA*states[0] + x - states[1];
    states[1] = x;

    return states[0];
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: dft_speech
  AUTHOR......: David Rowe
  DATE CREATED: 27/5/94

  Finds the DFT of the current speech input speech frame.

\*---------------------------------------------------------------------------*/

// TODO: we can either go for a faster FFT using fftr and some stack usage
// or we can reduce stack usage to almost zero on STM32 by switching to fft_inplace
#if 1
void dft_speech(C2CONST *c2const, codec2_fft_cfg fft_fwd_cfg, COMP Sw[], float Sn[], float w[])
{
    int  i;
    int  m_pitch = c2const->m_pitch;
    int   nw      = c2const->nw;

    for(i=0; i<FFT_ENC; i++) {
        Sw[i].real = 0.0;
        Sw[i].imag = 0.0;
    }

    /* Centre analysis window on time axis, we need to arrange input
       to FFT this way to make FFT phases correct */

    /* move 2nd half to start of FFT input vector */

    for(i=0; i<nw/2; i++)
        Sw[i].real = Sn[i+m_pitch/2]*w[i+m_pitch/2];

    /* move 1st half to end of FFT input vector */

    for(i=0; i<nw/2; i++)
        Sw[FFT_ENC-nw/2+i].real = Sn[i+m_pitch/2-nw/2]*w[i+m_pitch/2-nw/2];

    codec2_fft_inplace(fft_fwd_cfg, Sw);
}
#else
void dft_speech(codec2_fftr_cfg fftr_fwd_cfg, COMP Sw[], float Sn[], float w[])
{
    int  i;
  float sw[FFT_ENC];

  for(i=0; i<FFT_ENC; i++) {
    sw[i] = 0.0;
  }

  /* Centre analysis window on time axis, we need to arrange input
     to FFT this way to make FFT phases correct */

  /* move 2nd half to start of FFT input vector */

  for(i=0; i<nw/2; i++)
    sw[i] = Sn[i+m_pitch/2]*w[i+m_pitch/2];

  /* move 1st half to end of FFT input vector */

  for(i=0; i<nw/2; i++)
    sw[FFT_ENC-nw/2+i] = Sn[i+m_pitch/2-nw/2]*w[i+m_pitch/2-nw/2];

  codec2_fftr(fftr_fwd_cfg, sw, Sw);
}
#endif


/*---------------------------------------------------------------------------*\

  FUNCTION....: two_stage_pitch_refinement
  AUTHOR......: David Rowe
  DATE CREATED: 27/5/94

  Refines the current pitch estimate using the harmonic sum pitch
  estimation technique.

\*---------------------------------------------------------------------------*/

void two_stage_pitch_refinement(C2CONST *c2const, MODEL *model, COMP Sw[])
{
  float pmin,pmax,pstep;	/* pitch refinment minimum, maximum and step */

  /* Coarse refinement */

  pmax = TWO_PI/model->Wo + 5;
  pmin = TWO_PI/model->Wo - 5;
  pstep = 1.0;
  hs_pitch_refinement(model,Sw,pmin,pmax,pstep);

  /* Fine refinement */

  pmax = TWO_PI/model->Wo + 1;
  pmin = TWO_PI/model->Wo - 1;
  pstep = 0.25;
  hs_pitch_refinement(model,Sw,pmin,pmax,pstep);

  /* Limit range */

  if (model->Wo < TWO_PI/c2const->p_max)
    model->Wo = TWO_PI/c2const->p_max;
  if (model->Wo > TWO_PI/c2const->p_min)
    model->Wo = TWO_PI/c2const->p_min;

  model->L = floorf(PI/model->Wo);

  /* trap occasional round off issues with floorf() */
  if (model->Wo*model->L >= 0.95*PI) {
      model->L--;
  }
  assert(model->Wo*model->L < PI);
}

/*---------------------------------------------------------------------------*\

 FUNCTION....: hs_pitch_refinement
 AUTHOR......: David Rowe
 DATE CREATED: 27/5/94

 Harmonic sum pitch refinement function.

 pmin   pitch search range minimum
 pmax	pitch search range maximum
 step   pitch search step size
 model	current pitch estimate in model.Wo

 model 	refined pitch estimate in model.Wo

\*---------------------------------------------------------------------------*/

void hs_pitch_refinement(MODEL *model, COMP Sw[], float pmin, float pmax, float pstep)
{
  int m;		/* loop variable */
  int b;		/* bin for current harmonic centre */
  float E;		/* energy for current pitch*/
  float Wo;		/* current "test" fundamental freq. */
  float Wom;		/* Wo that maximises E */
  float Em;		/* mamimum energy */
  float r, one_on_r;	/* number of rads/bin */
  float p;		/* current pitch */

  /* Initialisation */

  model->L = PI/model->Wo;	/* use initial pitch est. for L */
  Wom = model->Wo;
  Em = 0.0;
  r = TWO_PI/FFT_ENC;
  one_on_r = 1.0/r;

  /* Determine harmonic sum for a range of Wo values */

  for(p=pmin; p<=pmax; p+=pstep) {
    E = 0.0;
    Wo = TWO_PI/p;
    
    float bFloat = Wo * one_on_r;
    float currentBFloat = bFloat;

    /* Sum harmonic magnitudes */
    for(m=1; m<=model->L; m++) {
        b = (int)(currentBFloat + 0.5);
        E += Sw[b].real*Sw[b].real + Sw[b].imag*Sw[b].imag;
        currentBFloat += bFloat;
    }
    /* Compare to see if this is a maximum */

    if (E > Em) {
      Em = E;
      Wom = Wo;
    }
  }

  model->Wo = Wom;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: estimate_amplitudes
  AUTHOR......: David Rowe
  DATE CREATED: 27/5/94

  Estimates the complex amplitudes of the harmonics.

\*---------------------------------------------------------------------------*/

void estimate_amplitudes(MODEL *model, COMP Sw[], float W[], int est_phase)
{
  int   i,m;		/* loop variables */
  int   am,bm;		/* bounds of current harmonic */
  float den;		/* denominator of amplitude expression */

  float r = TWO_PI/FFT_ENC;
  float one_on_r = 1.0/r;

  for(m=1; m<=model->L; m++) {
    /* Estimate ampltude of harmonic */

    den = 0.0;
    am = (int)((m - 0.5)*model->Wo*one_on_r + 0.5);
    bm = (int)((m + 0.5)*model->Wo*one_on_r + 0.5);

    for(i=am; i<bm; i++) {
      den += Sw[i].real*Sw[i].real + Sw[i].imag*Sw[i].imag;
    }

    model->A[m] = sqrtf(den);

    if (est_phase) {
        int b = (int)(m*model->Wo/r + 0.5); /* DFT bin of centre of current harmonic */

        /* Estimate phase of harmonic, this is expensive in CPU for
           embedded devicesso we make it an option */

        model->phi[m] = atan2f(Sw[b].imag,Sw[b].real);
    }
  }
}

/*---------------------------------------------------------------------------*\

  est_voicing_mbe()

  Returns the error of the MBE cost function for a fiven F0.

  Note: I think a lot of the operations below can be simplified as
  W[].imag = 0 and has been normalised such that den always equals 1.

\*---------------------------------------------------------------------------*/

float est_voicing_mbe(
                      C2CONST *c2const,
                      MODEL *model,
                      COMP   Sw[],
                      float  W[]
                      )
{
    int   l,al,bl,m;    /* loop variables */
    COMP  Am;             /* amplitude sample for this band */
    int   offset;         /* centers Hw[] about current harmonic */
    float den;            /* denominator of Am expression */
    float error;          /* accumulated error between original and synthesised */
    float Wo;
    float sig, snr;
    float elow, ehigh, eratio;
    float sixty;
    COMP   Ew;
    Ew.real = 0;
    Ew.imag = 0;

    int l_1000hz = model->L*1000.0/(c2const->Fs/2);
    sig = 1E-4;
    for(l=1; l<=l_1000hz; l++) {
	sig += model->A[l]*model->A[l];
    }

    Wo = model->Wo;
    error = 1E-4;

    /* Just test across the harmonics in the first 1000 Hz */

    for(l=1; l<=l_1000hz; l++) {
	Am.real = 0.0;
	Am.imag = 0.0;
	den = 0.0;
	al = ceilf((l - 0.5)*Wo*FFT_ENC/TWO_PI);
	bl = ceilf((l + 0.5)*Wo*FFT_ENC/TWO_PI);

	/* Estimate amplitude of harmonic assuming harmonic is totally voiced */

        offset = FFT_ENC/2 - l*Wo*FFT_ENC/TWO_PI + 0.5;
	for(m=al; m<bl; m++) {
	    Am.real += Sw[m].real*W[offset+m];
	    Am.imag += Sw[m].imag*W[offset+m];
	    den += W[offset+m]*W[offset+m];
        }

        Am.real = Am.real/den;
        Am.imag = Am.imag/den;

        /* Determine error between estimated harmonic and original */

        for(m=al; m<bl; m++) {
	    Ew.real = Sw[m].real - Am.real*W[offset+m];
	    Ew.imag = Sw[m].imag - Am.imag*W[offset+m];
	    error += Ew.real*Ew.real;
	    error += Ew.imag*Ew.imag;
	}
    }

    snr = 10.0*log10f(sig/error);
    if (snr > V_THRESH)
	model->voiced = 1;
    else
	model->voiced = 0;

    /* post processing, helps clean up some voicing errors ------------------*/

    /*
       Determine the ratio of low freqency to high frequency energy,
       voiced speech tends to be dominated by low frequency energy,
       unvoiced by high frequency. This measure can be used to
       determine if we have made any gross errors.
    */

    int l_2000hz = model->L*2000.0/(c2const->Fs/2);
    int l_4000hz = model->L*4000.0/(c2const->Fs/2);
    elow = ehigh = 1E-4;
    for(l=1; l<=l_2000hz; l++) {
	elow += model->A[l]*model->A[l];
    }
    for(l=l_2000hz; l<=l_4000hz; l++) {
	ehigh += model->A[l]*model->A[l];
    }
    eratio = 10.0*log10f(elow/ehigh);

    /* Look for Type 1 errors, strongly V speech that has been
       accidentally declared UV */

    if (model->voiced == 0)
	if (eratio > 10.0)
	    model->voiced = 1;

    /* Look for Type 2 errors, strongly UV speech that has been
       accidentally declared V */

    if (model->voiced == 1) {
	if (eratio < -10.0)
	    model->voiced = 0;

	/* A common source of Type 2 errors is the pitch estimator
	   gives a low (50Hz) estimate for UV speech, which gives a
	   good match with noise due to the close harmoonic spacing.
	   These errors are much more common than people with 50Hz3
	   pitch, so we have just a small eratio threshold. */

	sixty = 60.0*TWO_PI/c2const->Fs;
	if ((eratio < -4.0) && (model->Wo <= sixty))
	    model->voiced = 0;
    }
    //printf(" v: %d snr: %f eratio: %3.2f %f\n",model->voiced,snr,eratio,dF0);

    return snr;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: make_synthesis_window
  AUTHOR......: David Rowe
  DATE CREATED: 11/5/94

  Init function that generates the trapezoidal (Parzen) sythesis window.

\*---------------------------------------------------------------------------*/

void make_synthesis_window(C2CONST *c2const, float Pn[])
{
  int   i;
  float win;
  int   n_samp = c2const->n_samp;
  int   tw     = c2const->tw;

  /* Generate Parzen window in time domain */

  win = 0.0;
  for(i=0; i<n_samp/2-tw; i++)
    Pn[i] = 0.0;
  win = 0.0;
  for(i=n_samp/2-tw; i<n_samp/2+tw; win+=1.0/(2*tw), i++ )
    Pn[i] = win;
  for(i=n_samp/2+tw; i<3*n_samp/2-tw; i++)
    Pn[i] = 1.0;
  win = 1.0;
  for(i=3*n_samp/2-tw; i<3*n_samp/2+tw; win-=1.0/(2*tw), i++)
    Pn[i] = win;
  for(i=3*n_samp/2+tw; i<2*n_samp; i++)
    Pn[i] = 0.0;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: synthesise
  AUTHOR......: David Rowe
  DATE CREATED: 20/2/95

  Synthesise a speech signal in the frequency domain from the
  sinusodal model parameters.  Uses overlap-add with a trapezoidal
  window to smoothly interpolate betwen frames.

\*---------------------------------------------------------------------------*/

void synthesise(
  int    n_samp,
  codec2_fftr_cfg fftr_inv_cfg,
  float  Sn_[],		/* time domain synthesised signal              */
  MODEL *model,		/* ptr to model parameters for this frame      */
  float  Pn[],		/* time domain Parzen window                   */
  int    shift          /* flag used to handle transition frames       */
)
{
    int   i,l,j,b;	        /* loop variables */
    COMP  Sw_[FFT_DEC/2+1];	/* DFT of synthesised signal */
    float sw_[FFT_DEC];	        /* synthesised signal */

    if (shift) {
	/* Update memories */
	for(i=0; i<n_samp-1; i++) {
	    Sn_[i] = Sn_[i+n_samp];
	}
	Sn_[n_samp-1] = 0.0;
    }

    for(i=0; i<FFT_DEC/2+1; i++) {
	Sw_[i].real = 0.0;
	Sw_[i].imag = 0.0;
    }

    /* Now set up frequency domain synthesised speech */

    for(l=1; l<=model->L; l++) {
        b = (int)(l*model->Wo*FFT_DEC/TWO_PI + 0.5);
        if (b > ((FFT_DEC/2)-1)) {
            b = (FFT_DEC/2)-1;
        }
        Sw_[b].real = model->A[l]*cosf(model->phi[l]);
        Sw_[b].imag = model->A[l]*sinf(model->phi[l]);
    }

    /* Perform inverse DFT */

    codec2_fftri(fftr_inv_cfg, Sw_,sw_);

    /* Overlap add to previous samples */

    #ifdef USE_KISS_FFT
    #define    FFTI_FACTOR ((float)1.0)
    #else
    #define    FFTI_FACTOR ((float32_t)FFT_DEC)
    #endif

    for(i=0; i<n_samp-1; i++) {
        Sn_[i] += sw_[FFT_DEC-n_samp+1+i]*Pn[i] * FFTI_FACTOR;
    }

    if (shift)
        for(i=n_samp-1,j=0; i<2*n_samp; i++,j++)
            Sn_[i] = sw_[j]*Pn[i] * FFTI_FACTOR;
    else
        for(i=n_samp-1,j=0; i<2*n_samp; i++,j++)
            Sn_[i] += sw_[j]*Pn[i] * FFTI_FACTOR;
}


/* todo: this should probably be in some states rather than a static */
static unsigned long next = 1;

int codec2_rand(void) {
    next = next * 1103515245 + 12345;
    return((unsigned)(next/65536) % 32768);
}

