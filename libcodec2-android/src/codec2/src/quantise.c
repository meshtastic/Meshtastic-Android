/*---------------------------------------------------------------------------*\

  FILE........: quantise.c
  AUTHOR......: David Rowe
  DATE CREATED: 31/5/92

  Quantisation functions for the sinusoidal coder.

\*---------------------------------------------------------------------------*/

/*
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
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "defines.h"
#include "dump.h"
#include "quantise.h"
#include "lpc.h"
#include "lsp.h"
#include "codec2_fft.h"
#include "phase.h"
#include "mbest.h"

#undef PROFILE
#include "machdep.h"

#define LSP_DELTA1 0.01         /* grid spacing for LSP root searches */

/*---------------------------------------------------------------------------*\

                          FUNCTION HEADERS

\*---------------------------------------------------------------------------*/

float speech_to_uq_lsps(float lsp[], float ak[], float Sn[], float w[],
			int m_pitch, int order);

/*---------------------------------------------------------------------------*\

                             FUNCTIONS

\*---------------------------------------------------------------------------*/

int lsp_bits(int i) {
    return lsp_cb[i].log2m;
}

int lspd_bits(int i) {
    return lsp_cbd[i].log2m;
}

int lsp_pred_vq_bits(int i) {
    return lsp_cbjmv[i].log2m;
}

/*---------------------------------------------------------------------------*\

  quantise

  Quantises vec by choosing the nearest vector in codebook cb, and
  returns the vector index.  The squared error of the quantised vector
  is added to se.

\*---------------------------------------------------------------------------*/

long quantise(const float * cb, float vec[], float w[], int k, int m, float *se)
/* float   cb[][K];	current VQ codebook		*/
/* float   vec[];	vector to quantise		*/
/* float   w[];         weighting vector                */
/* int	   k;		dimension of vectors		*/
/* int     m;		size of codebook		*/
/* float   *se;		accumulated squared error 	*/
{
   float   e;		/* current error		*/
   long	   besti;	/* best index so far		*/
   float   beste;	/* best error so far		*/
   long	   j;
   int     i;
   float   diff;

   besti = 0;
   beste = 1E32;
   for(j=0; j<m; j++) {
	e = 0.0;
	for(i=0; i<k; i++) {
	    diff = cb[j*k+i]-vec[i];
	    e += (diff*w[i] * diff*w[i]);
	}
	if (e < beste) {
	    beste = e;
	    besti = j;
	}
   }

   *se += beste;

   return(besti);
}



/*---------------------------------------------------------------------------*\

  encode_lspds_scalar()

  Scalar/VQ LSP difference-in-frequency quantiser.

\*---------------------------------------------------------------------------*/

void encode_lspds_scalar(
		 int   indexes[],
		 float lsp[],
		 int   order
)
{
    int   i,k,m;
    float lsp_hz[order];
    float lsp__hz[order];
    float dlsp[order];
    float dlsp_[order];
    float wt[order];
    const float *cb;
    float se;

    for(i=0; i<order; i++) {
	wt[i] = 1.0;
    }

    /* convert from radians to Hz so we can use human readable
       frequencies */

    for(i=0; i<order; i++)
	lsp_hz[i] = (4000.0/PI)*lsp[i];

    wt[0] = 1.0;
    for(i=0; i<order; i++) {

	/* find difference from previous quantised lsp */

	if (i)
	    dlsp[i] = lsp_hz[i] - lsp__hz[i-1];
	else
	    dlsp[0] = lsp_hz[0];

	k = lsp_cbd[i].k;
	m = lsp_cbd[i].m;
	cb = lsp_cbd[i].cb;
	indexes[i] = quantise(cb, &dlsp[i], wt, k, m, &se);
 	dlsp_[i] = cb[indexes[i]*k];

	if (i)
	    lsp__hz[i] = lsp__hz[i-1] + dlsp_[i];
	else
	    lsp__hz[0] = dlsp_[0];
    }

}


void decode_lspds_scalar(
		 float lsp_[],
		 int   indexes[],
		 int   order
)
{
    int   i,k;
    float lsp__hz[order];
    float dlsp_[order];
    const float *cb;

     for(i=0; i<order; i++) {

	k = lsp_cbd[i].k;
	cb = lsp_cbd[i].cb;
 	dlsp_[i] = cb[indexes[i]*k];

	if (i)
	    lsp__hz[i] = lsp__hz[i-1] + dlsp_[i];
	else
	    lsp__hz[0] = dlsp_[0];

	lsp_[i] = (PI/4000.0)*lsp__hz[i];
    }

}

#define MIN(a,b) ((a)<(b)?(a):(b))
#define MAX_ENTRIES 16384

void compute_weights(const float *x, float *w, int ndim)
{
  int i;
  w[0] = MIN(x[0], x[1]-x[0]);
  for (i=1;i<ndim-1;i++)
    w[i] = MIN(x[i]-x[i-1], x[i+1]-x[i]);
  w[ndim-1] = MIN(x[ndim-1]-x[ndim-2], PI-x[ndim-1]);

  for (i=0;i<ndim;i++)
    w[i] = 1./(.01+w[i]);
}

int find_nearest(const float *codebook, int nb_entries, float *x, int ndim)
{
  int i, j;
  float min_dist = 1e15;
  int nearest = 0;

  for (i=0;i<nb_entries;i++)
  {
    float dist=0;
    for (j=0;j<ndim;j++)
      dist += (x[j]-codebook[i*ndim+j])*(x[j]-codebook[i*ndim+j]);
    if (dist<min_dist)
    {
      min_dist = dist;
      nearest = i;
    }
  }
  return nearest;
}

int find_nearest_weighted(const float *codebook, int nb_entries, float *x, const float *w, int ndim)
{
  int i, j;
  float min_dist = 1e15;
  int nearest = 0;

  for (i=0;i<nb_entries;i++)
  {
    float dist=0;
    for (j=0;j<ndim;j++)
      dist += w[j]*(x[j]-codebook[i*ndim+j])*(x[j]-codebook[i*ndim+j]);
    if (dist<min_dist)
    {
      min_dist = dist;
      nearest = i;
    }
  }
  return nearest;
}

void lspjmv_quantise(float *x, float *xq, int order)
{
  int i, n1, n2, n3;
  float err[order], err2[order], err3[order];
  float w[order], w2[order], w3[order];
  const float *codebook1 = lsp_cbjmv[0].cb;
  const float *codebook2 = lsp_cbjmv[1].cb;
  const float *codebook3 = lsp_cbjmv[2].cb;

  w[0] = MIN(x[0], x[1]-x[0]);
  for (i=1;i<order-1;i++)
    w[i] = MIN(x[i]-x[i-1], x[i+1]-x[i]);
  w[order-1] = MIN(x[order-1]-x[order-2], PI-x[order-1]);

  compute_weights(x, w, order);

  n1 = find_nearest(codebook1, lsp_cbjmv[0].m, x, order);

  for (i=0;i<order;i++)
  {
    xq[i] = codebook1[order*n1+i];
    err[i] = x[i] - xq[i];
  }
  for (i=0;i<order/2;i++)
  {
    err2[i] = err[2*i];
    err3[i] = err[2*i+1];
    w2[i] = w[2*i];
    w3[i] = w[2*i+1];
  }
  n2 = find_nearest_weighted(codebook2, lsp_cbjmv[1].m, err2, w2, order/2);
  n3 = find_nearest_weighted(codebook3, lsp_cbjmv[2].m, err3, w3, order/2);

  for (i=0;i<order/2;i++)
  {
    xq[2*i] += codebook2[order*n2/2+i];
    xq[2*i+1] += codebook3[order*n3/2+i];
  }
}

int check_lsp_order(float lsp[], int order)
{
    int   i;
    float tmp;
    int   swaps = 0;

    for(i=1; i<order; i++)
	if (lsp[i] < lsp[i-1]) {
	    //fprintf(stderr, "swap %d\n",i);
	    swaps++;
	    tmp = lsp[i-1];
	    lsp[i-1] = lsp[i]-0.1;
	    lsp[i] = tmp+0.1;
            i = 1; /* start check again, as swap may have caused out of order */
	}

    return swaps;
}

void force_min_lsp_dist(float lsp[], int order)
{
    int   i;

    for(i=1; i<order; i++)
	if ((lsp[i]-lsp[i-1]) < 0.01) {
	    lsp[i] += 0.01;
	}
}


/*---------------------------------------------------------------------------*\

   lpc_post_filter()

   Applies a post filter to the LPC synthesis filter power spectrum
   Pw, which supresses the inter-formant energy.

   The algorithm is from p267 (Section 8.6) of "Digital Speech",
   edited by A.M. Kondoz, 1994 published by Wiley and Sons.  Chapter 8
   of this text is on the MBE vocoder, and this is a freq domain
   adaptation of post filtering commonly used in CELP.

   I used the Octave simulation lpcpf.m to get an understanding of the
   algorithm.

   Requires two more FFTs which is significantly more MIPs.  However
   it should be possible to implement this more efficiently in the
   time domain.  Just not sure how to handle relative time delays
   between the synthesis stage and updating these coeffs.  A smaller
   FFT size might also be accetable to save CPU.

   TODO:
   [ ] sync var names between Octave and C version
   [ ] doc gain normalisation
   [ ] I think the first FFT is not rqd as we do the same
       thing in aks_to_M2().

\*---------------------------------------------------------------------------*/

void lpc_post_filter(codec2_fftr_cfg fftr_fwd_cfg, float Pw[], float ak[],
                     int order, int dump, float beta, float gamma, int bass_boost, float E)
{
    int   i;
    float x[FFT_ENC];   /* input to FFTs                */
    COMP  Ww[FFT_ENC/2+1];  /* weighting spectrum           */
    float Rw[FFT_ENC/2+1];  /* R = WA                       */
    float e_before, e_after, gain;
    float Pfw;
    float max_Rw, min_Rw;
    float coeff;
    PROFILE_VAR(tstart, tfft1, taw, tfft2, tww, tr);

    PROFILE_SAMPLE(tstart);

    /* Determine weighting filter spectrum W(exp(jw)) ---------------*/

    for(i=0; i<FFT_ENC; i++) {
	x[i] = 0.0;
    }

    x[0]  = ak[0];
    coeff = gamma;
    for(i=1; i<=order; i++) {
	x[i] = ak[i] * coeff;
        coeff *= gamma;
    }
    codec2_fftr(fftr_fwd_cfg, x, Ww);

    PROFILE_SAMPLE_AND_LOG(tfft2, taw, "        fft2");

    for(i=0; i<FFT_ENC/2; i++) {
	Ww[i].real = Ww[i].real*Ww[i].real + Ww[i].imag*Ww[i].imag;
    }

    PROFILE_SAMPLE_AND_LOG(tww, tfft2, "        Ww");

    /* Determined combined filter R = WA ---------------------------*/

    max_Rw = 0.0; min_Rw = 1E32;
    for(i=0; i<FFT_ENC/2; i++) {
	Rw[i] = sqrtf(Ww[i].real * Pw[i]);
	if (Rw[i] > max_Rw)
	    max_Rw = Rw[i];
	if (Rw[i] < min_Rw)
	    min_Rw = Rw[i];

    }

    PROFILE_SAMPLE_AND_LOG(tr, tww, "        R");

    #ifdef DUMP
    if (dump)
      dump_Rw(Rw);
    #endif

    /* create post filter mag spectrum and apply ------------------*/

    /* measure energy before post filtering */

    e_before = 1E-4;
    for(i=0; i<FFT_ENC/2; i++)
	e_before += Pw[i];

    /* apply post filter and measure energy  */

    #ifdef DUMP
    if (dump)
	dump_Pwb(Pw);
    #endif


    e_after = 1E-4;
    for(i=0; i<FFT_ENC/2; i++) {
        Pfw = powf(Rw[i], beta);
        Pw[i] *= Pfw * Pfw;
        e_after += Pw[i];
    }
    gain = e_before/e_after;

    /* apply gain factor to normalise energy, and LPC Energy */

    gain *= E;
    for(i=0; i<FFT_ENC/2; i++) {
	Pw[i] *= gain;
    }

    if (bass_boost) {
        /* add 3dB to first 1 kHz to account for LP effect of PF */

        for(i=0; i<FFT_ENC/8; i++) {
            Pw[i] *= 1.4*1.4;
        }
    }

    PROFILE_SAMPLE_AND_LOG2(tr, "        filt");
}


/*---------------------------------------------------------------------------*\

   aks_to_M2()

   Transforms the linear prediction coefficients to spectral amplitude
   samples.  This function determines A(m) from the average energy per
   band using an FFT.

\*---------------------------------------------------------------------------*/

void aks_to_M2(
  codec2_fftr_cfg  fftr_fwd_cfg,
  float         ak[],	     /* LPC's */
  int           order,
  MODEL        *model,	     /* sinusoidal model parameters for this frame */
  float         E,	     /* energy term */
  float        *snr,	     /* signal to noise ratio for this frame in dB */
  int           dump,        /* true to dump sample to dump file */
  int           sim_pf,      /* true to simulate a post filter */
  int           pf,          /* true to enable actual LPC post filter */
  int           bass_boost,  /* enable LPC filter 0-1kHz 3dB boost */
  float         beta,
  float         gamma,       /* LPC post filter parameters */
  COMP          Aw[]         /* output power spectrum */
)
{
  int i,m;		/* loop variables */
  int am,bm;		/* limits of current band */
  float r;		/* no. rads/bin */
  float Em;		/* energy in band */
  float Am;		/* spectral amplitude sample */
  float signal, noise;
  PROFILE_VAR(tstart, tfft, tpw, tpf);

  PROFILE_SAMPLE(tstart);

  r = TWO_PI/(FFT_ENC);

  /* Determine DFT of A(exp(jw)) --------------------------------------------*/
  {
      float a[FFT_ENC];  /* input to FFT for power spectrum */

      for(i=0; i<FFT_ENC; i++) {
          a[i] = 0.0;
      }

      for(i=0; i<=order; i++)
          a[i] = ak[i];
      codec2_fftr(fftr_fwd_cfg, a, Aw);
  }
  PROFILE_SAMPLE_AND_LOG(tfft, tstart, "      fft");

  /* Determine power spectrum P(w) = E/(A(exp(jw))^2 ------------------------*/

  float Pw[FFT_ENC/2];

#ifndef FDV_ARM_MATH
  for(i=0; i<FFT_ENC/2; i++) {
    Pw[i] = 1.0/(Aw[i].real*Aw[i].real + Aw[i].imag*Aw[i].imag + 1E-6);
  }
#else
  // this difference may seem strange, but the gcc for STM32F4 generates almost 5 times
  // faster code with the two loops: 1120 ms -> 242 ms
  // so please leave it as is or improve further
  // since this code is called 4 times it results in almost 4ms gain (21ms -> 17ms per audio frame decode @ 1300 )

  for(i=0; i<FFT_ENC/2; i++)
  {
      Pw[i] = Aw[i].real * Aw[i].real + Aw[i].imag * Aw[i].imag  + 1E-6;
  }
  for(i=0; i<FFT_ENC/2; i++) {
      Pw[i] = 1.0/(Pw[i]);
  }
#endif

  PROFILE_SAMPLE_AND_LOG(tpw, tfft, "      Pw");

  if (pf)
      lpc_post_filter(fftr_fwd_cfg, Pw, ak, order, dump, beta, gamma, bass_boost, E);
  else {
      for(i=0; i<FFT_ENC/2; i++) {
          Pw[i] *= E;
      }
  }

  PROFILE_SAMPLE_AND_LOG(tpf, tpw, "      LPC post filter");

  #ifdef DUMP
  if (dump)
      dump_Pw(Pw);
  #endif

  /* Determine magnitudes from P(w) ----------------------------------------*/

  /* when used just by decoder {A} might be all zeroes so init signal
     and noise to prevent log(0) errors */

  signal = 1E-30; noise = 1E-32;

  for(m=1; m<=model->L; m++) {
      am = (int)((m - 0.5)*model->Wo/r + 0.5);
      bm = (int)((m + 0.5)*model->Wo/r + 0.5);

      // FIXME: With arm_rfft_fast_f32 we have to use this
      // otherwise sometimes a to high bm is calculated
      // which causes trouble later in the calculation
      // chain
      // it seems for some reason model->Wo is calculated somewhat too high
      if (bm>FFT_ENC/2)
      {
          bm = FFT_ENC/2;
      }
      Em = 0.0;

      for(i=am; i<bm; i++)
          Em += Pw[i];
      Am = sqrtf(Em);

      signal += model->A[m]*model->A[m];
      noise  += (model->A[m] - Am)*(model->A[m] - Am);

      /* This code significantly improves perf of LPC model, in
         particular when combined with phase0.  The LPC spectrum tends
         to track just under the peaks of the spectral envelope, and
         just above nulls.  This algorithm does the reverse to
         compensate - raising the amplitudes of spectral peaks, while
         attenuating the null.  This enhances the formants, and
         supresses the energy between formants. */

      if (sim_pf) {
          if (Am > model->A[m])
              Am *= 0.7;
          if (Am < model->A[m])
              Am *= 1.4;
      }
      model->A[m] = Am;
  }
  *snr = 10.0*log10f(signal/noise);

  PROFILE_SAMPLE_AND_LOG2(tpf, "      rec");
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: encode_Wo()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Encodes Wo using a WO_LEVELS quantiser.

\*---------------------------------------------------------------------------*/

int encode_Wo(C2CONST *c2const, float Wo, int bits)
{
    int   index, Wo_levels = 1<<bits;
    float Wo_min = c2const->Wo_min;
    float Wo_max = c2const->Wo_max;
    float norm;

    norm = (Wo - Wo_min)/(Wo_max - Wo_min);
    index = floorf(Wo_levels * norm + 0.5);
    if (index < 0 ) index = 0;
    if (index > (Wo_levels-1)) index = Wo_levels-1;

    return index;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: decode_Wo()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Decodes Wo using a WO_LEVELS quantiser.

\*---------------------------------------------------------------------------*/

float decode_Wo(C2CONST *c2const, int index, int bits)
{
    float Wo_min = c2const->Wo_min;
    float Wo_max = c2const->Wo_max;
    float step;
    float Wo;
    int   Wo_levels = 1<<bits;

    step = (Wo_max - Wo_min)/Wo_levels;
    Wo   = Wo_min + step*(index);

    return Wo;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: encode_log_Wo()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Encodes Wo in the log domain using a WO_LEVELS quantiser.

\*---------------------------------------------------------------------------*/

int encode_log_Wo(C2CONST *c2const, float Wo, int bits)
{
    int   index, Wo_levels = 1<<bits;
    float Wo_min = c2const->Wo_min;
    float Wo_max = c2const->Wo_max;
    float norm;

    norm = (log10f(Wo) - log10f(Wo_min))/(log10f(Wo_max) - log10f(Wo_min));
    index = floorf(Wo_levels * norm + 0.5);
    if (index < 0 ) index = 0;
    if (index > (Wo_levels-1)) index = Wo_levels-1;

    return index;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: decode_log_Wo()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Decodes Wo using a WO_LEVELS quantiser in the log domain.

\*---------------------------------------------------------------------------*/

float decode_log_Wo(C2CONST *c2const, int index, int bits)
{
    float Wo_min = c2const->Wo_min;
    float Wo_max = c2const->Wo_max;
    float step;
    float Wo;
    int   Wo_levels = 1<<bits;

    step = (log10f(Wo_max) - log10f(Wo_min))/Wo_levels;
    Wo   = log10f(Wo_min) + step*(index);

    return POW10F(Wo);
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: speech_to_uq_lsps()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Analyse a windowed frame of time domain speech to determine LPCs
  which are the converted to LSPs for quantisation and transmission
  over the channel.

\*---------------------------------------------------------------------------*/

float speech_to_uq_lsps(float lsp[],
			float ak[],
		        float Sn[],
		        float w[],
		        int m_pitch,
                        int   order
)
{
    int   i, roots;
    float Wn[m_pitch];
    float R[order+1];
    float e, E;

    e = 0.0;
    for(i=0; i<m_pitch; i++) {
	Wn[i] = Sn[i]*w[i];
	e += Wn[i]*Wn[i];
    }

    /* trap 0 energy case as LPC analysis will fail */

    if (e == 0.0) {
	for(i=0; i<order; i++)
	    lsp[i] = (PI/order)*(float)i;
	return 0.0;
    }

    autocorrelate(Wn, R, m_pitch, order);
    levinson_durbin(R, ak, order);

    E = 0.0;
    for(i=0; i<=order; i++)
	E += ak[i]*R[i];

    /* 15 Hz BW expansion as I can't hear the difference and it may help
       help occasional fails in the LSP root finding.  Important to do this
       after energy calculation to avoid -ve energy values.
    */

    for(i=0; i<=order; i++)
	ak[i] *= powf(0.994,(float)i);

    roots = lpc_to_lsp(ak, order, lsp, 5, LSP_DELTA1);
    if (roots != order) {
	/* if root finding fails use some benign LSP values instead */
	for(i=0; i<order; i++)
	    lsp[i] = (PI/order)*(float)i;
    }

    return E;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: encode_lsps_scalar()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Scalar LSP quantiser. From a vector of unquantised (floating point)
  LSPs finds the quantised LSP indexes.

\*---------------------------------------------------------------------------*/

void encode_lsps_scalar(int indexes[], float lsp[], int order)
{
    int    i,k,m;
    float  wt[1];
    float  lsp_hz[order];
    const float * cb;
    float se;

    /* convert from radians to Hz so we can use human readable
       frequencies */

    for(i=0; i<order; i++)
	lsp_hz[i] = (4000.0/PI)*lsp[i];

    /* scalar quantisers */

    wt[0] = 1.0;
    for(i=0; i<order; i++) {
	k = lsp_cb[i].k;
	m = lsp_cb[i].m;
	cb = lsp_cb[i].cb;
	indexes[i] = quantise(cb, &lsp_hz[i], wt, k, m, &se);
    }
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: decode_lsps_scalar()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  From a vector of quantised LSP indexes, returns the quantised
  (floating point) LSPs.

\*---------------------------------------------------------------------------*/

void decode_lsps_scalar(float lsp[], int indexes[], int order)
{
    int    i,k;
    float  lsp_hz[order];
    const float * cb;

    for(i=0; i<order; i++) {
	k = lsp_cb[i].k;
	cb = lsp_cb[i].cb;
	lsp_hz[i] = cb[indexes[i]*k];
    }

    /* convert back to radians */

    for(i=0; i<order; i++)
	lsp[i] = (PI/4000.0)*lsp_hz[i];
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: encode_lsps_vq()
  AUTHOR......: David Rowe
  DATE CREATED: 15 Feb 2012

  Multi-stage VQ LSP quantiser developed by Jean-Marc Valin.

\*---------------------------------------------------------------------------*/

void encode_lsps_vq(int *indexes, float *x, float *xq, int order)
{
  int i, n1, n2, n3;
  float err[order], err2[order], err3[order];
  float w[order], w2[order], w3[order];
  const float *codebook1 = lsp_cbjmv[0].cb;
  const float *codebook2 = lsp_cbjmv[1].cb;
  const float *codebook3 = lsp_cbjmv[2].cb;

  w[0] = MIN(x[0], x[1]-x[0]);
  for (i=1;i<order-1;i++)
    w[i] = MIN(x[i]-x[i-1], x[i+1]-x[i]);
  w[order-1] = MIN(x[order-1]-x[order-2], PI-x[order-1]);

  compute_weights(x, w, order);

  n1 = find_nearest(codebook1, lsp_cbjmv[0].m, x, order);

  for (i=0;i<order;i++)
  {
    xq[i]  = codebook1[order*n1+i];
    err[i] = x[i] - xq[i];
  }
  for (i=0;i<order/2;i++)
  {
    err2[i] = err[2*i];
    err3[i] = err[2*i+1];
    w2[i] = w[2*i];
    w3[i] = w[2*i+1];
  }
  n2 = find_nearest_weighted(codebook2, lsp_cbjmv[1].m, err2, w2, order/2);
  n3 = find_nearest_weighted(codebook3, lsp_cbjmv[2].m, err3, w3, order/2);

  indexes[0] = n1;
  indexes[1] = n2;
  indexes[2] = n3;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: decode_lsps_vq()
  AUTHOR......: David Rowe
  DATE CREATED: 15 Feb 2012

\*---------------------------------------------------------------------------*/

void decode_lsps_vq(int *indexes, float *xq, int order, int stages)
{
  int i, n1, n2, n3;
  const float *codebook1 = lsp_cbjmv[0].cb;
  const float *codebook2 = lsp_cbjmv[1].cb;
  const float *codebook3 = lsp_cbjmv[2].cb;

  n1 = indexes[0];
  n2 = indexes[1];
  n3 = indexes[2];

  for (i=0;i<order;i++) {
      xq[i] = codebook1[order*n1+i];
  }

  if (stages != 1) {
      for (i=0;i<order/2;i++) {
          xq[2*i] += codebook2[order*n2/2+i];
          xq[2*i+1] += codebook3[order*n3/2+i];
      }
  }

}


/*---------------------------------------------------------------------------*\

  FUNCTION....: bw_expand_lsps()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Applies Bandwidth Expansion (BW) to a vector of LSPs.  Prevents any
  two LSPs getting too close together after quantisation.  We know
  from experiment that LSP quantisation errors < 12.5Hz (25Hz step
  size) are inaudible so we use that as the minimum LSP separation.

\*---------------------------------------------------------------------------*/

void bw_expand_lsps(float lsp[], int order, float min_sep_low, float min_sep_high)
{
    int i;

    for(i=1; i<4; i++) {

	if ((lsp[i] - lsp[i-1]) < min_sep_low*(PI/4000.0))
	    lsp[i] = lsp[i-1] + min_sep_low*(PI/4000.0);

    }

    /* As quantiser gaps increased, larger BW expansion was required
       to prevent twinkly noises.  This may need more experiment for
       different quanstisers.
    */

    for(i=4; i<order; i++) {
	if (lsp[i] - lsp[i-1] < min_sep_high*(PI/4000.0))
	    lsp[i] = lsp[i-1] + min_sep_high*(PI/4000.0);
    }
}

void bw_expand_lsps2(float lsp[],
		    int   order
)
{
    int i;

    for(i=1; i<4; i++) {

	if ((lsp[i] - lsp[i-1]) < 100.0*(PI/4000.0))
	    lsp[i] = lsp[i-1] + 100.0*(PI/4000.0);

    }

    /* As quantiser gaps increased, larger BW expansion was required
       to prevent twinkly noises.  This may need more experiment for
       different quanstisers.
    */

    for(i=4; i<order; i++) {
	if (lsp[i] - lsp[i-1] < 200.0*(PI/4000.0))
	    lsp[i] = lsp[i-1] + 200.0*(PI/4000.0);
    }
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: apply_lpc_correction()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Apply first harmonic LPC correction at decoder.  This helps improve
  low pitch males after LPC modelling, like hts1a and morig.

\*---------------------------------------------------------------------------*/

void apply_lpc_correction(MODEL *model)
{
    if (model->Wo < (PI*150.0/4000)) {
	model->A[1] *= 0.032;
    }
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: encode_energy()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Encodes LPC energy using an E_LEVELS quantiser.

\*---------------------------------------------------------------------------*/

int encode_energy(float e, int bits)
{
    int   index, e_levels = 1<<bits;
    float e_min = E_MIN_DB;
    float e_max = E_MAX_DB;
    float norm;

    e = 10.0*log10f(e);
    norm = (e - e_min)/(e_max - e_min);
    index = floorf(e_levels * norm + 0.5);
    if (index < 0 ) index = 0;
    if (index > (e_levels-1)) index = e_levels-1;

    return index;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: decode_energy()
  AUTHOR......: David Rowe
  DATE CREATED: 22/8/2010

  Decodes energy using a E_LEVELS quantiser.

\*---------------------------------------------------------------------------*/

float decode_energy(int index, int bits)
{
    float e_min = E_MIN_DB;
    float e_max = E_MAX_DB;
    float step;
    float e;
    int   e_levels = 1<<bits;

    step = (e_max - e_min)/e_levels;
    e    = e_min + step*(index);
    e    = POW10F(e/10.0);

    return e;
}


static float ge_coeff[2] = {0.8, 0.9};

void compute_weights2(const float *x, const float *xp, float *w)
{
  w[0] = 30;
  w[1] = 1;
  if (x[1]<0)
  {
     w[0] *= .6;
     w[1] *= .3;
  }
  if (x[1]<-10)
  {
     w[0] *= .3;
     w[1] *= .3;
  }
  /* Higher weight if pitch is stable */
  if (fabsf(x[0]-xp[0])<.2)
  {
     w[0] *= 2;
     w[1] *= 1.5;
  } else if (fabsf(x[0]-xp[0])>.5) /* Lower if not stable */
  {
     w[0] *= .5;
  }

  /* Lower weight for low energy */
  if (x[1] < xp[1]-10)
  {
     w[1] *= .5;
  }
  if (x[1] < xp[1]-20)
  {
     w[1] *= .5;
  }

  //w[0] = 30;
  //w[1] = 1;

  /* Square the weights because it's applied on the squared error */
  w[0] *= w[0];
  w[1] *= w[1];

}

/*---------------------------------------------------------------------------*\

  FUNCTION....: quantise_WoE()
  AUTHOR......: Jean-Marc Valin & David Rowe
  DATE CREATED: 29 Feb 2012

  Experimental joint Wo and LPC energy vector quantiser developed by
  Jean-Marc Valin.  Exploits correlations between the difference in
  the log pitch and log energy from frame to frame.  For example
  both the pitch and energy tend to only change by small amounts
  during voiced speech, however it is important that these changes be
  coded carefully.  During unvoiced speech they both change a lot but
  the ear is less sensitve to errors so coarser quantisation is OK.

  The ear is sensitive to log energy and loq pitch so we quantise in
  these domains.  That way the error measure used to quantise the
  values is close to way the ear senses errors.

  See http://jmspeex.livejournal.com/10446.html

\*---------------------------------------------------------------------------*/

void quantise_WoE(C2CONST *c2const, MODEL *model, float *e, float xq[])
{
  int          i, n1;
  float        x[2];
  float        err[2];
  float        w[2];
  const float *codebook1 = ge_cb[0].cb;
  int          nb_entries = ge_cb[0].m;
  int          ndim = ge_cb[0].k;
  float Wo_min = c2const->Wo_min;
  float Wo_max = c2const->Wo_max;
  float Fs = c2const->Fs;

  /* VQ is only trained for Fs = 8000 Hz */

  assert(Fs == 8000);

  x[0] = log10f((model->Wo/PI)*4000.0/50.0)/log10f(2);
  x[1] = 10.0*log10f(1e-4 + *e);

  compute_weights2(x, xq, w);
  for (i=0;i<ndim;i++)
    err[i] = x[i]-ge_coeff[i]*xq[i];
  n1 = find_nearest_weighted(codebook1, nb_entries, err, w, ndim);

  for (i=0;i<ndim;i++)
  {
    xq[i] = ge_coeff[i]*xq[i] + codebook1[ndim*n1+i];
    err[i] -= codebook1[ndim*n1+i];
  }

  /*
    x = log2(4000*Wo/(PI*50));
    2^x = 4000*Wo/(PI*50)
    Wo = (2^x)*(PI*50)/4000;
  */

  model->Wo = powf(2.0, xq[0])*(PI*50.0)/4000.0;

  /* bit errors can make us go out of range leading to all sorts of
     probs like seg faults */

  if (model->Wo > Wo_max) model->Wo = Wo_max;
  if (model->Wo < Wo_min) model->Wo = Wo_min;

  model->L  = PI/model->Wo; /* if we quantise Wo re-compute L */

  *e = POW10F(xq[1]/10.0);
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: encode_WoE()
  AUTHOR......: Jean-Marc Valin & David Rowe
  DATE CREATED: 11 May 2012

  Joint Wo and LPC energy vector quantiser developed my Jean-Marc
  Valin.  Returns index, and updated states xq[].

\*---------------------------------------------------------------------------*/

int encode_WoE(MODEL *model, float e, float xq[])
{
  int          i, n1;
  float        x[2];
  float        err[2];
  float        w[2];
  const float *codebook1 = ge_cb[0].cb;
  int          nb_entries = ge_cb[0].m;
  int          ndim = ge_cb[0].k;

  assert((1<<WO_E_BITS) == nb_entries);

  if (e < 0.0) e = 0;  /* occasional small negative energies due LPC round off I guess */

  x[0] = log10f((model->Wo/PI)*4000.0/50.0)/log10f(2);
  x[1] = 10.0*log10f(1e-4 + e);

  compute_weights2(x, xq, w);
  for (i=0;i<ndim;i++)
    err[i] = x[i]-ge_coeff[i]*xq[i];
  n1 = find_nearest_weighted(codebook1, nb_entries, err, w, ndim);

  for (i=0;i<ndim;i++)
  {
    xq[i] = ge_coeff[i]*xq[i] + codebook1[ndim*n1+i];
    err[i] -= codebook1[ndim*n1+i];
  }

  //printf("enc: %f %f (%f)(%f) \n", xq[0], xq[1], e, 10.0*log10(1e-4 + e));
  return n1;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: decode_WoE()
  AUTHOR......: Jean-Marc Valin & David Rowe
  DATE CREATED: 11 May 2012

  Joint Wo and LPC energy vector quantiser developed my Jean-Marc
  Valin.  Given index and states xq[], returns Wo & E, and updates
  states xq[].

\*---------------------------------------------------------------------------*/

void decode_WoE(C2CONST *c2const, MODEL *model, float *e, float xq[], int n1)
{
  int          i;
  const float *codebook1 = ge_cb[0].cb;
  int          ndim = ge_cb[0].k;
  float Wo_min = c2const->Wo_min;
  float Wo_max = c2const->Wo_max;

  for (i=0;i<ndim;i++)
  {
    xq[i] = ge_coeff[i]*xq[i] + codebook1[ndim*n1+i];
  }

  //printf("dec: %f %f\n", xq[0], xq[1]);
  model->Wo = powf(2.0, xq[0])*(PI*50.0)/4000.0;

  /* bit errors can make us go out of range leading to all sorts of
     probs like seg faults */

  if (model->Wo > Wo_max) model->Wo = Wo_max;
  if (model->Wo < Wo_min) model->Wo = Wo_min;

  model->L  = PI/model->Wo; /* if we quantise Wo re-compute L */

  *e = POW10F(xq[1]/10.0);
}

