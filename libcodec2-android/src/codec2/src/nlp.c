/*---------------------------------------------------------------------------*\

  FILE........: nlp.c
  AUTHOR......: David Rowe
  DATE CREATED: 23/3/93

  Non Linear Pitch (NLP) estimation functions.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2009 David Rowe

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

#include "defines.h"
#include "nlp.h"
#include "dump.h"
#include "codec2_fft.h"
#undef PROFILE
#include "machdep.h"
#include "os.h"

#include <assert.h>
#include <math.h>
#include <stdlib.h>

/*---------------------------------------------------------------------------*\

 				DEFINES

\*---------------------------------------------------------------------------*/

#define PMAX_M      320		/* maximum NLP analysis window size     */
#define COEFF       0.95	/* notch filter parameter               */
#define PE_FFT_SIZE 512		/* DFT size for pitch estimation        */
#define DEC         5		/* decimation factor                    */
#define SAMPLE_RATE 8000
#define PI          3.141592654	/* mathematical constant                */
#define T           0.1         /* threshold for local minima candidate */
#define F0_MAX      500
#define CNLP        0.3	        /* post processor constant              */
#define NLP_NTAP 48	        /* Decimation LPF order */

/* 8 to 16 kHz sample rate conversion */

#define FDMDV_OS                 2                            /* oversampling rate                   */
#define FDMDV_OS_TAPS_16K       48                            /* number of OS filter taps at 16kHz   */
#define FDMDV_OS_TAPS_8K        (FDMDV_OS_TAPS_16K/FDMDV_OS)  /* number of OS filter taps at 8kHz    */

/*---------------------------------------------------------------------------*\

 				GLOBALS

\*---------------------------------------------------------------------------*/

/* 48 tap 600Hz low pass FIR filter coefficients */

const float nlp_fir[] = {
  -1.0818124e-03,
  -1.1008344e-03,
  -9.2768838e-04,
  -4.2289438e-04,
   5.5034190e-04,
   2.0029849e-03,
   3.7058509e-03,
   5.1449415e-03,
   5.5924666e-03,
   4.3036754e-03,
   8.0284511e-04,
  -4.8204610e-03,
  -1.1705810e-02,
  -1.8199275e-02,
  -2.2065282e-02,
  -2.0920610e-02,
  -1.2808831e-02,
   3.2204775e-03,
   2.6683811e-02,
   5.5520624e-02,
   8.6305944e-02,
   1.1480192e-01,
   1.3674206e-01,
   1.4867556e-01,
   1.4867556e-01,
   1.3674206e-01,
   1.1480192e-01,
   8.6305944e-02,
   5.5520624e-02,
   2.6683811e-02,
   3.2204775e-03,
  -1.2808831e-02,
  -2.0920610e-02,
  -2.2065282e-02,
  -1.8199275e-02,
  -1.1705810e-02,
  -4.8204610e-03,
   8.0284511e-04,
   4.3036754e-03,
   5.5924666e-03,
   5.1449415e-03,
   3.7058509e-03,
   2.0029849e-03,
   5.5034190e-04,
  -4.2289438e-04,
  -9.2768838e-04,
  -1.1008344e-03,
  -1.0818124e-03
};

typedef struct {
    int           Fs;                /* sample rate in Hz            */
    int           m;
    float         w[PMAX_M/DEC];     /* DFT window                   */
    float         sq[PMAX_M];	     /* squared speech samples       */
    float         mem_x,mem_y;       /* memory for notch filter      */
    float         mem_fir[NLP_NTAP]; /* decimation FIR filter memory */
    codec2_fft_cfg  fft_cfg;         /* kiss FFT config              */
    float        *Sn16k;	     /* Fs=16kHz input speech vector */
    FILE         *f;
} NLP;

float post_process_sub_multiples(COMP Fw[],
				 int pmin, int pmax, float gmax, int gmax_bin,
				 float *prev_f0);
static void fdmdv_16_to_8(float out8k[], float in16k[], int n);

/*---------------------------------------------------------------------------*\

  nlp_create()

  Initialisation function for NLP pitch estimator.

\*---------------------------------------------------------------------------*/

void *nlp_create(C2CONST *c2const)
{
    NLP *nlp;
    int  i;
    int  m = c2const->m_pitch;
    int  Fs = c2const->Fs;

    nlp = (NLP*)malloc(sizeof(NLP));
    if (nlp == NULL)
	return NULL;

    assert((Fs == 8000) || (Fs == 16000));
    nlp->Fs = Fs;

    nlp->m = m;

    /* if running at 16kHz allocate storage for decimating filter memory */

    if (Fs == 16000) {
        nlp->Sn16k = (float*)malloc(sizeof(float)*(FDMDV_OS_TAPS_16K + c2const->n_samp));
        for(i=0; i<FDMDV_OS_TAPS_16K; i++) {
           nlp->Sn16k[i] = 0.0;
        }
        if (nlp->Sn16k == NULL) {
            free(nlp);
            return NULL;
        }

        /* most processing occurs at 8 kHz sample rate so halve m */

        m /= 2;
    }

    assert(m <= PMAX_M);
    
    for(i=0; i<m/DEC; i++) {
	nlp->w[i] = 0.5 - 0.5*cosf(2*PI*i/(m/DEC-1));
    }

    for(i=0; i<PMAX_M; i++)
	nlp->sq[i] = 0.0;
    nlp->mem_x = 0.0;
    nlp->mem_y = 0.0;
    for(i=0; i<NLP_NTAP; i++)
	nlp->mem_fir[i] = 0.0;

    nlp->fft_cfg = codec2_fft_alloc (PE_FFT_SIZE, 0, NULL, NULL);
    assert(nlp->fft_cfg != NULL);

    return (void*)nlp;
}

/*---------------------------------------------------------------------------*\

  nlp_destroy()

  Shut down function for NLP pitch estimator.

\*---------------------------------------------------------------------------*/

void nlp_destroy(void *nlp_state)
{
    NLP   *nlp;
    assert(nlp_state != NULL);
    nlp = (NLP*)nlp_state;

    codec2_fft_free(nlp->fft_cfg);
    if (nlp->Fs == 16000) {
        free(nlp->Sn16k);
    }
    free(nlp_state);
}

/*---------------------------------------------------------------------------*\

  nlp()

  Determines the pitch in samples using the Non Linear Pitch (NLP)
  algorithm [1]. Returns the fundamental in Hz.  Note that the actual
  pitch estimate is for the centre of the M sample Sn[] vector, not
  the current N sample input vector.  This is (I think) a delay of 2.5
  frames with N=80 samples.  You should align further analysis using
  this pitch estimate to be centred on the middle of Sn[].

  Two post processors have been tried, the MBE version (as discussed
  in [1]), and a post processor that checks sub-multiples.  Both
  suffer occasional gross pitch errors (i.e. neither are perfect).  In
  the presence of background noise the sub-multiple algorithm tends
  towards low F0 which leads to better sounding background noise than
  the MBE post processor.

  A good way to test and develop the NLP pitch estimator is using the
  tnlp (codec2/unittest) and the codec2/octave/plnlp.m Octave script.

  A pitch tracker searching a few frames forward and backward in time
  would be a useful addition.

  References:

    [1] http://rowetel.com/downloads/1997_rowe_phd_thesis.pdf Chapter 4

\*---------------------------------------------------------------------------*/

float nlp(
  void *nlp_state,
  float  Sn[],			/* input speech vector                                */
  int    n,			/* frames shift (no. new samples in Sn[])             */
  float *pitch,			/* estimated pitch period in samples at current Fs    */
  COMP   Sw[],                  /* Freq domain version of Sn[]                        */
  float  W[],                   /* Freq domain window                                 */
  float *prev_f0                /* previous pitch f0 in Hz, memory for pitch tracking */
)
{
    NLP   *nlp;
    float  notch;		    /* current notch filter output          */
    COMP   Fw[PE_FFT_SIZE];	    /* DFT of squared signal (input/output) */
    float  gmax;
    int    gmax_bin;
    int    m, i, j;
    float  best_f0;
    PROFILE_VAR(start, tnotch, filter, peakpick, window, fft, magsq, shiftmem);

    assert(nlp_state != NULL);
    nlp = (NLP*)nlp_state;
    m = nlp->m;

    /* Square, notch filter at DC, and LP filter vector */

    /* If running at 16 kHz decimate to 8 kHz, as NLP ws designed for
       Fs = 8kHz. The decimating filter introduces about 3ms of delay,
       that shouldn't be a problem as pitch changes slowly. */

    if (nlp->Fs == 8000) {
        /* Square latest input samples */

        for(i=m-n; i<m; i++) {
	  nlp->sq[i] = Sn[i]*Sn[i];
        }
    }
    else {
        assert(nlp->Fs == 16000);

        /* re-sample at 8 KHz */

        for(i=0; i<n; i++) {
            nlp->Sn16k[FDMDV_OS_TAPS_16K+i] = Sn[m-n+i];
        }

        m /= 2; n /= 2;

        float Sn8k[n];
        fdmdv_16_to_8(Sn8k, &nlp->Sn16k[FDMDV_OS_TAPS_16K], n);

        /* Square latest input samples */

        for(i=m-n, j=0; i<m; i++, j++) {
	    nlp->sq[i] = Sn8k[j]*Sn8k[j];
        }
        assert(j <= n);
    }
    //fprintf(stderr, "n: %d m: %d\n", n, m);

    PROFILE_SAMPLE(start);

    for(i=m-n; i<m; i++) {	/* notch filter at DC */
	notch = nlp->sq[i] - nlp->mem_x;
	notch += COEFF*nlp->mem_y;
	nlp->mem_x = nlp->sq[i];
	nlp->mem_y = notch;
	nlp->sq[i] = notch + 1.0;  /* With 0 input vectors to codec,
				      kiss_fft() would take a long
				      time to execute when running in
				      real time.  Problem was traced
				      to kiss_fft function call in
				      this function. Adding this small
				      constant fixed problem.  Not
				      exactly sure why. */
    }

    PROFILE_SAMPLE_AND_LOG(tnotch, start, "      square and notch");

    for(i=m-n; i<m; i++) {	/* FIR filter vector */

	for(j=0; j<NLP_NTAP-1; j++)
	    nlp->mem_fir[j] = nlp->mem_fir[j+1];
	nlp->mem_fir[NLP_NTAP-1] = nlp->sq[i];

	nlp->sq[i] = 0.0;
	for(j=0; j<NLP_NTAP; j++)
	    nlp->sq[i] += nlp->mem_fir[j]*nlp_fir[j];
    }

    PROFILE_SAMPLE_AND_LOG(filter, tnotch, "      filter");

    /* Decimate and DFT */

    for(i=0; i<PE_FFT_SIZE; i++) {
	Fw[i].real = 0.0;
	Fw[i].imag = 0.0;
    }
    for(i=0; i<m/DEC; i++) {
	Fw[i].real = nlp->sq[i*DEC]*nlp->w[i];
    }
    PROFILE_SAMPLE_AND_LOG(window, filter, "      window");
    #ifdef DUMP
    dump_dec(Fw);
    #endif

    // FIXME: check if this can be converted to a real fft
    // since all imag inputs are 0
    codec2_fft_inplace(nlp->fft_cfg, Fw);
    PROFILE_SAMPLE_AND_LOG(fft, window, "      fft");

    for(i=0; i<PE_FFT_SIZE; i++)
	Fw[i].real = Fw[i].real*Fw[i].real + Fw[i].imag*Fw[i].imag;

    PROFILE_SAMPLE_AND_LOG(magsq, fft, "      mag sq");
    #ifdef DUMP
    dump_sq(m, nlp->sq);
    dump_Fw(Fw);
    #endif

    /* todo: express everything in f0, as pitch in samples is dep on Fs */

    int pmin = floor(SAMPLE_RATE*P_MIN_S);
    int pmax = floor(SAMPLE_RATE*P_MAX_S);

    /* find global peak */

    gmax = 0.0;
    gmax_bin = PE_FFT_SIZE*DEC/pmax;
    for(i=PE_FFT_SIZE*DEC/pmax; i<=PE_FFT_SIZE*DEC/pmin; i++) {
	if (Fw[i].real > gmax) {
	    gmax = Fw[i].real;
	    gmax_bin = i;
	}
    }

    PROFILE_SAMPLE_AND_LOG(peakpick, magsq, "      peak pick");

    best_f0 = post_process_sub_multiples(Fw, pmin, pmax, gmax, gmax_bin, prev_f0);

    PROFILE_SAMPLE_AND_LOG(shiftmem, peakpick,  "      post process");

    /* Shift samples in buffer to make room for new samples */

    for(i=0; i<m-n; i++)
	nlp->sq[i] = nlp->sq[i+n];

    /* return pitch period in samples and F0 estimate */

    *pitch = (float)nlp->Fs/best_f0;

    PROFILE_SAMPLE_AND_LOG2(shiftmem,  "      shift mem");

    PROFILE_SAMPLE_AND_LOG2(start,  "      nlp int");

    *prev_f0 = best_f0;

    return(best_f0);
}

/*---------------------------------------------------------------------------*\

  post_process_sub_multiples()

  Given the global maximma of Fw[] we search integer submultiples for
  local maxima.  If local maxima exist and they are above an
  experimentally derived threshold (OK a magic number I pulled out of
  the air) we choose the submultiple as the F0 estimate.

  The rational for this is that the lowest frequency peak of Fw[]
  should be F0, as Fw[] can be considered the autocorrelation function
  of Sw[] (the speech spectrum).  However sometimes due to phase
  effects the lowest frequency maxima may not be the global maxima.

  This works OK in practice and favours low F0 values in the presence
  of background noise which means the sinusoidal codec does an OK job
  of synthesising the background noise.  High F0 in background noise
  tends to sound more periodic introducing annoying artifacts.

\*---------------------------------------------------------------------------*/

float post_process_sub_multiples(COMP Fw[],
				 int pmin, int pmax, float gmax, int gmax_bin,
				 float *prev_f0)
{
    int   min_bin, cmax_bin;
    int   mult;
    float thresh, best_f0;
    int   b, bmin, bmax, lmax_bin;
    float lmax;
    int   prev_f0_bin;

    /* post process estimate by searching submultiples */

    mult = 2;
    min_bin = PE_FFT_SIZE*DEC/pmax;
    cmax_bin = gmax_bin;
    prev_f0_bin = *prev_f0*(PE_FFT_SIZE*DEC)/SAMPLE_RATE;

    while(gmax_bin/mult >= min_bin) {

	b = gmax_bin/mult;			/* determine search interval */
	bmin = 0.8*b;
	bmax = 1.2*b;
	if (bmin < min_bin)
	    bmin = min_bin;

	/* lower threshold to favour previous frames pitch estimate,
	    this is a form of pitch tracking */

	if ((prev_f0_bin > bmin) && (prev_f0_bin < bmax))
	    thresh = CNLP*0.5*gmax;
	else
	    thresh = CNLP*gmax;

	lmax = 0;
	lmax_bin = bmin;
	for (b=bmin; b<=bmax; b++) 	     /* look for maximum in interval */
	    if (Fw[b].real > lmax) {
		lmax = Fw[b].real;
		lmax_bin = b;
	    }

	if (lmax > thresh)
	    if ((lmax > Fw[lmax_bin-1].real) && (lmax > Fw[lmax_bin+1].real)) {
		cmax_bin = lmax_bin;
	    }

	mult++;
    }

    best_f0 = (float)cmax_bin*SAMPLE_RATE/(PE_FFT_SIZE*DEC);

    return best_f0;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: fdmdv_16_to_8()
  AUTHOR......: David Rowe
  DATE CREATED: 9 May 2012

  Changes the sample rate of a signal from 16 to 8 kHz.

  n is the number of samples at the 8 kHz rate, there are FDMDV_OS*n
  samples at the 48 kHz rate.  As above however a memory of
  FDMDV_OS_TAPS samples is reqd for in16k[] (see t16_8.c unit test as example).

  Low pass filter the 16 kHz signal at 4 kHz using the same filter as
  the upsampler, then just output every FDMDV_OS-th filtered sample.

  Note: this function copied from fdmdv.c, included in nlp.c as a convenience
  to avoid linking with another source file.

\*---------------------------------------------------------------------------*/

static void fdmdv_16_to_8(float out8k[], float in16k[], int n)
{
    float acc;
    int   i,j,k;

    for(i=0, k=0; k<n; i+=FDMDV_OS, k++) {
	acc = 0.0;
	for(j=0; j<FDMDV_OS_TAPS_16K; j++)
	    acc += fdmdv_os_filter[j]*in16k[i-j];
        out8k[k] = acc;
    }

    /* update filter memory */

    for(i=-FDMDV_OS_TAPS_16K; i<0; i++)
	in16k[i] = in16k[i + n*FDMDV_OS];
}
