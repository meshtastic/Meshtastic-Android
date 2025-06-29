/*---------------------------------------------------------------------------*\

  FILE........: newamp2.c
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018
  BASED ON....:	"newamp1" by David Rowe

  Quantisation functions for the sinusoidal coder, using "newamp1"
  algorithm that resamples variable rate L [Am} to a fixed rate K then
  VQs.

\*---------------------------------------------------------------------------*/

/*
  Copyright David Rowe 2017

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

#include "defines.h"
#include "phase.h"
#include "quantise.h"
#include "mbest.h"
#include "newamp1.h"
#include "newamp2.h"

/*---------------------------------------------------------------------------*\

  FUNCTION....: n2_mel_sample_freqs_kHz()
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

  Outputs fixed frequencies for the K-Vectors to be able to work with both 8k and 16k mode.

\*---------------------------------------------------------------------------*/

void n2_mel_sample_freqs_kHz(float rate_K_sample_freqs_kHz[], int K)
{
	float freq[] = {0.199816, 0.252849, 0.309008, 0.368476, 0.431449, 0.498134, 0.568749, 0.643526, 0.722710, 0.806561, 0.895354, 0.989380,
					1.088948, 1.194384, 1.306034, 1.424264, 1.549463, 1.682041, 1.822432, 1.971098, 2.128525, 2.295232, 2.471763, 2.658699,
					2.856652, 3.066272, 3.288246, 3.523303, 3.772214, 4.035795, 4.314912, 4.610478, 4.923465, 5.254899, 5.605865, 5.977518,
					6.371075, 6.787827, 7.229141, 7.696465};
    int k;
	//printf("\n\n");
    for (k=0; k<K; k++) {
        rate_K_sample_freqs_kHz[k] = freq[k];
    //    printf("%f ",mel);
    //    printf("%f \n",rate_K_sample_freqs_kHz[k]);
    }
    
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: n2_resample_const_rate_f() still equal to resample_const_rate_f()
  AUTHOR......: David Rowe
  DATE CREATED: Jan 2017

  Resample Am from time-varying rate L=floor(pi/Wo) to fixed rate K.

\*---------------------------------------------------------------------------*/

void n2_resample_const_rate_f(C2CONST *c2const, MODEL *model, float rate_K_vec[], float rate_K_sample_freqs_kHz[], int K)
{
    int m;
    float AmdB[MAX_AMP+1], rate_L_sample_freqs_kHz[MAX_AMP+1], AmdB_peak;

    /* convert rate L=pi/Wo amplitude samples to fixed rate K */

    AmdB_peak = -100.0;
    for(m=1; m<=model->L; m++) {
        AmdB[m] = 20.0*log10(model->A[m]+1E-16);
        if (AmdB[m] > AmdB_peak) {
            AmdB_peak = AmdB[m];
        }
        rate_L_sample_freqs_kHz[m] = m*model->Wo*(c2const->Fs/2000.0)/M_PI;
        //printf("m: %d AmdB: %f AmdB_peak: %f  sf: %f\n", m, AmdB[m], AmdB_peak, rate_L_sample_freqs_kHz[m]);
    }
    
    /* clip between peak and peak -50dB, to reduce dynamic range */

    for(m=1; m<=model->L; m++) {
        if (AmdB[m] < (AmdB_peak-50.0)) {
            AmdB[m] = AmdB_peak-50.0;
        }
    }

    interp_para(rate_K_vec, &rate_L_sample_freqs_kHz[1], &AmdB[1], model->L, rate_K_sample_freqs_kHz, K);    
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: n2_rate_K_mbest_encode
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

  One stage rate K newamp2 VQ quantiser using mbest search.

\*---------------------------------------------------------------------------*/

void n2_rate_K_mbest_encode(int *indexes, float *x, float *xq, int ndim)
{
  int i, n1;
  const float *codebook1 = newamp2vq_cb[0].cb;
  struct MBEST *mbest_stage1;
  float w[ndim];
  int   index[1];

  /* codebook is compiled for a fixed K */

  //assert(ndim == newamp2vq_cb[0].k);

  /* equal weights, could be argued mel freq axis gives freq dep weighting */

  for(i=0; i<ndim; i++)
      w[i] = 1.0;

  mbest_stage1 = mbest_create(1);
  
  index[0] = 0;

  /* Stage 1 */

  mbest_search450(codebook1, x, w, ndim,NEWAMP2_K, newamp2vq_cb[0].m, mbest_stage1, index);
  n1 = mbest_stage1->list[0].index[0];

  mbest_destroy(mbest_stage1);

  //indexes[1]: legacy from newamp1
  indexes[0] = n1; indexes[1] = n1;

}


/*---------------------------------------------------------------------------*\

  FUNCTION....: n2_resample_rate_L
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

  Decoder side conversion of rate K vector back to rate L.
  Plosives are set to zero for the first 2 of 4 frames.

\*---------------------------------------------------------------------------*/

void n2_resample_rate_L(C2CONST *c2const, MODEL *model, float rate_K_vec[], float rate_K_sample_freqs_kHz[], int K,int plosive_flag)
{
   float rate_K_vec_term[K+2], rate_K_sample_freqs_kHz_term[K+2];
   float AmdB[MAX_AMP+1], rate_L_sample_freqs_kHz[MAX_AMP+1];
   int m,k;

   /* terminate either end of the rate K vecs with 0dB points */

   rate_K_vec_term[0] = rate_K_vec_term[K+1] = 0.0;
   rate_K_sample_freqs_kHz_term[0] = 0.0;
   rate_K_sample_freqs_kHz_term[K+1] = 4.0;

   for(k=0; k<K; k++) {
       rate_K_vec_term[k+1] = rate_K_vec[k];
       rate_K_sample_freqs_kHz_term[k+1] = rate_K_sample_freqs_kHz[k];
  
       //printf("k: %d f: %f rate_K: %f\n", k, rate_K_sample_freqs_kHz[k], rate_K_vec[k]);
   }

   for(m=1; m<=model->L; m++) {
       rate_L_sample_freqs_kHz[m] = m*model->Wo*(c2const->Fs/2000.0)/M_PI;
   }

   interp_para(&AmdB[1], rate_K_sample_freqs_kHz_term, rate_K_vec_term, K+2, &rate_L_sample_freqs_kHz[1], model->L);    
   for(m=1; m<=model->L; m++) {
		if(plosive_flag==0){
			model->A[m] = pow(10.0,  AmdB[m]/20.0);
		}else{
			model->A[m] = 0.1;
		}
       // printf("m: %d f: %f AdB: %f A: %f\n", m, rate_L_sample_freqs_kHz[m], AmdB[m], model->A[m]);
   }
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: n2_post_filter_newamp2
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

  Postfilter for the pseudo wideband mode. Still has to be adapted!

\*---------------------------------------------------------------------------*/

void n2_post_filter_newamp2(float vec[], float sample_freq_kHz[], int K, float pf_gain)
{
    int k;

    /*
      vec is rate K vector describing spectrum of current frame lets
      pre-emp before applying PF. 20dB/dec over 300Hz.  Postfilter
      affects energy of frame so we measure energy before and after
      and normalise.  Plenty of room for experiment here as well.
    */
    
    float pre[K];
    float e_before = 0.0;
    float e_after = 0.0;
    for(k=0; k<K; k++) {
        pre[k] = 20.0*log10f(sample_freq_kHz[k]/0.3);
        vec[k] += pre[k];
        e_before += POW10F(vec[k]/10.0);
        vec[k] *= pf_gain;
        e_after += POW10F(vec[k]/10.0);
    }

    float gain = e_after/e_before;
    float gaindB = 10*log10f(gain);
  
    for(k=0; k<K; k++) {
        vec[k] -= gaindB;
        vec[k] -= pre[k];
    }
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: newamp2_model_to_indexes
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

  newamp2 encoder: Encodes the 8k sampled samples using mbest search (one stage)

\*---------------------------------------------------------------------------*/

void newamp2_model_to_indexes(C2CONST *c2const,
                              int    indexes[], 
                              MODEL *model, 
                              float  rate_K_vec[], 
                              float  rate_K_sample_freqs_kHz[], 
                              int    K,
                              float *mean,
                              float  rate_K_vec_no_mean[], 
                              float  rate_K_vec_no_mean_[],
							  int    plosive
                              )
{
    int k;

    /* convert variable rate L to fixed rate K */

    resample_const_rate_f(c2const, model, rate_K_vec, rate_K_sample_freqs_kHz, K);

    /* remove mean and two stage VQ */

    float sum = 0.0;
    for(k=0; k<K; k++)
        sum += rate_K_vec[k];
    *mean = sum/K;
    for(k=0; k<K; k++)
    {
        rate_K_vec_no_mean[k] = rate_K_vec[k] - *mean;        
	}
	//NEWAMP2_16K_K+1 because the last vector is not a vector for VQ (and not included in the constant)
	//but a calculated medium mean value
    n2_rate_K_mbest_encode(indexes, rate_K_vec_no_mean, rate_K_vec_no_mean_, NEWAMP2_16K_K+1);

    /* scalar quantise mean (effectively the frame energy) */

    float w[1] = {1.0};
    float se;
    indexes[2] = quantise(newamp2_energy_cb[0].cb, 
                          mean, 
                          w, 
                          newamp2_energy_cb[0].k, 
                          newamp2_energy_cb[0].m, 
                          &se);

    /* scalar quantise Wo.  We steal the smallest Wo index to signal
       an unvoiced frame */

    if (model->voiced) {
        int index = encode_log_Wo(c2const, model->Wo, 6);
        if (index == 0) {
            index = 1;
        }
        if (index == 63) {
            index = 62;
        }
        indexes[3] = index;
    }
    else {
        indexes[3] = 0;
    }
    if(plosive != 0){
		indexes[3] = 63;
		}
 }


/*---------------------------------------------------------------------------*\

  FUNCTION....: newamp2_indexes_to_rate_K_vec
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

  newamp2 decoder for amplitudes {Am}.  Given the rate K VQ and energy
  indexes, outputs rate K vector. Equal to newamp1 but using only one stage VQ.

\*---------------------------------------------------------------------------*/

void newamp2_indexes_to_rate_K_vec(float  rate_K_vec_[],  
                                   float  rate_K_vec_no_mean_[],
                                   float  rate_K_sample_freqs_kHz[], 
                                   int    K,
                                   float *mean_,
                                   int    indexes[],
                                   float  pf_gain)
{
    int   k;
    const float *codebook1 = newamp2vq_cb[0].cb;
    int n1 = indexes[0];
    
    for(k=0; k<K; k++) {
      rate_K_vec_no_mean_[k] = codebook1[(NEWAMP2_16K_K+1)*n1+k];
    }

    post_filter_newamp1(rate_K_vec_no_mean_, rate_K_sample_freqs_kHz, K, pf_gain);

    *mean_ = newamp2_energy_cb[0].cb[indexes[2]];

    for(k=0; k<K; k++) {
        rate_K_vec_[k] = rate_K_vec_no_mean_[k] + *mean_;
    }
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: newamp2_16k_indexes_to_rate_K_vec
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

  newamp2 decoder for amplitudes {Am}.  Given the rate K VQ and energy
  indexes, outputs rate K vector. Extends the sample rate by looking up the corresponding
  higher frequency values with their energy difference to the base energy (=>mean2)

\*---------------------------------------------------------------------------*/

void newamp2_16k_indexes_to_rate_K_vec(float  rate_K_vec_[],  
                                   float  rate_K_vec_no_mean_[],
                                   float  rate_K_sample_freqs_kHz[], 
                                   int    K,
                                   float *mean_,
                                   int    indexes[],
                                   float  pf_gain)
{
    int   k;
    const float *codebook1 = newamp2vq_cb[0].cb;
    float mean2 = 0;
    int n1 = indexes[0];
    
    for(k=0; k<K; k++) {
      rate_K_vec_no_mean_[k] = codebook1[(K+1)*n1+k];
    }

    n2_post_filter_newamp2(rate_K_vec_no_mean_, rate_K_sample_freqs_kHz, K, pf_gain);

    *mean_ = newamp2_energy_cb[0].cb[indexes[2]];
    mean2 = *mean_  + codebook1[(K+1)*n1+K] -10;
    
    //HF ear Protection
    if(mean2>50){
		mean2 = 50;
		}

    for(k=0; k<K; k++) {
		if(k<NEWAMP2_K){
			rate_K_vec_[k] = rate_K_vec_no_mean_[k] + *mean_;
		}
		else{
			//Amplify or Reduce ??
			rate_K_vec_[k] = rate_K_vec_no_mean_[k] + mean2;
			}
    }
}
/*---------------------------------------------------------------------------*\

  FUNCTION....: newamp2_interpolate
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

  Interpolates to the 4 10ms Frames and leaves the forst 2 empty for plosives

\*---------------------------------------------------------------------------*/

void newamp2_interpolate(float interpolated_surface_[], float left_vec[], float right_vec[], int K, int plosive_flag)
{
    int  i, k;
    int  M = 4;
    float c;

    /* (linearly) interpolate 25Hz amplitude vectors back to 100Hz */

	if(plosive_flag == 0){
		for(i=0,c=1.0; i<M; i++,c-=1.0/M) {
			for(k=0; k<K; k++) {
				interpolated_surface_[i*K+k] = left_vec[k]*c + right_vec[k]*(1.0-c); 
			}
		}
	}
	else{
		for(i=0,c=1.0; i<M; i++,c-=1.0/M) {
			for(k=0; k<K; k++) {
				if(i<2){
					interpolated_surface_[i*K+k] = 0; 
				}
				else{
					//perhaps add some dB ?
					interpolated_surface_[i*K+k] = right_vec[k]; 
				}
			}
		}
	
	}
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: newamp2_indexes_to_model
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

  newamp2 decoder. Chooses whether to decode to 16k mode or to 8k mode

\*---------------------------------------------------------------------------*/

void newamp2_indexes_to_model(C2CONST *c2const,
                              MODEL  model_[],
                              COMP   H[],
                              float *interpolated_surface_,
                              float  prev_rate_K_vec_[],
                              float  *Wo_left,
                              int    *voicing_left,
                              float  rate_K_sample_freqs_kHz[], 
                              int    K,
                              codec2_fft_cfg fwd_cfg, 
                              codec2_fft_cfg inv_cfg,
                              int    indexes[],
                              float pf_gain,
                              int flag16k)
{
    float rate_K_vec_[K], rate_K_vec_no_mean_[K], mean_, Wo_right;
    int   voicing_right, k;
    int   M = 4;

    /* extract latest rate K vector */

	if(flag16k == 0){
		newamp2_indexes_to_rate_K_vec(rate_K_vec_, 
									rate_K_vec_no_mean_,
									rate_K_sample_freqs_kHz, 
									K,
									&mean_,
									indexes,
									pf_gain);
	}else{
		newamp2_16k_indexes_to_rate_K_vec(rate_K_vec_, 
									rate_K_vec_no_mean_,
									rate_K_sample_freqs_kHz, 
									K,
									&mean_,
									indexes,
									pf_gain);	
	}


    /* decode latest Wo and voicing and plosive */
	int plosive_flag = 0;
	
	//Voiced with Wo
    if (indexes[3]>0 && indexes[3]<63) {
        Wo_right = decode_log_Wo(c2const, indexes[3], 6);
        voicing_right = 1;
    }
    //Unvoiced
    else if(indexes[3] == 0){
        Wo_right  = 2.0*M_PI/100.0;
        voicing_right = 0;
    }
    //indexes[3]=63 (= Plosive) and unvoiced
    else {
		Wo_right  = 2.0*M_PI/100.0;
        voicing_right = 0;
        plosive_flag = 1;
	}

    /* interpolate 25Hz rate K vec back to 100Hz */

    float *left_vec = prev_rate_K_vec_;
    float *right_vec = rate_K_vec_;
    newamp2_interpolate(interpolated_surface_, left_vec, right_vec, K,plosive_flag);

    /* interpolate 25Hz v and Wo back to 100Hz */

    float aWo_[M];
    int avoicing_[M], aL_[M], i;

    interp_Wo_v(aWo_, aL_, avoicing_, *Wo_left, Wo_right, *voicing_left, voicing_right);

    /* back to rate L amplitudes, synthesis phase for each frame */

    for(i=0; i<M; i++) {
        model_[i].Wo = aWo_[i];
        model_[i].L  = aL_[i];
        model_[i].voiced = avoicing_[i];
        //Plosive Detected
		if(plosive_flag>0){
			//First two frames are set to zero	
			if (i<2){
				n2_resample_rate_L(c2const, &model_[i], &interpolated_surface_[K*i], rate_K_sample_freqs_kHz, K,1);
			}
			else{
				n2_resample_rate_L(c2const, &model_[i], &interpolated_surface_[K*i], rate_K_sample_freqs_kHz, K,0);
			}
		}
		//No Plosive, standard resample
		else{
			n2_resample_rate_L(c2const, &model_[i], &interpolated_surface_[K*i], rate_K_sample_freqs_kHz, K,0);
		}
		determine_phase(c2const, &H[(MAX_AMP+1)*i], &model_[i], NEWAMP2_PHASE_NFFT, fwd_cfg, inv_cfg);
    }

    /* update memories for next time */

    for(k=0; k<K; k++) {
        prev_rate_K_vec_[k] = rate_K_vec_[k];
    }
    *Wo_left = Wo_right;
    *voicing_left = voicing_right;

}

