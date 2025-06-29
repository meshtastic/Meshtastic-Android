/*---------------------------------------------------------------------------*\

  FILE........: newamp2.h
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018
  BASED ON....:	"newamp1.h" by David Rowe

  Quantisation functions for the sinusoidal coder, using "newamp1"
  algorithm that resamples variable rate L [Am} to a fixed rate K then
  VQs.

\*---------------------------------------------------------------------------*/

/*
  Copyright Thomas Kurin and Stefan Erhardt 2018

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

#ifndef __NEWAMP2__
#define __NEWAMP2__

#define NEWAMP2_N_INDEXES    4  /* Number of indexes to pack: vq1, vq2, energy, Wo */
#define NEWAMP2_PHASE_NFFT 128  /* size of FFT used for phase synthesis            */
#define NEWAMP2_K           29  /* rate K vector length							   */
#define NEWAMP2_16K_K       40  /* rate K vector length	for 16k Mode			   */                    

#include "codec2_fft.h"
#include "comp.h"

void n2_mel_sample_freqs_kHz(float rate_K_sample_freqs_kHz[], int K);
void n2_resample_const_rate_f(C2CONST *c2const, MODEL *model, float rate_K_vec[], float rate_K_sample_freqs_kHz[], int K);
void n2_rate_K_mbest_encode(int *indexes, float *x, float *xq, int ndim);
void n2_resample_rate_L(C2CONST *c2const, MODEL *model, float rate_K_vec[], float rate_K_sample_freqs_kHz[], int K,int plosive_flag);
void n2_post_filter_newamp2(float vec[], float sample_freq_kHz[], int K, float pf_gain);
void newamp2_interpolate(float interpolated_surface_[], float left_vec[], float right_vec[], int K,int plosive_flag);
void newamp2_model_to_indexes(C2CONST *c2const,
                              int    indexes[], 
                              MODEL *model, 
                              float  rate_K_vec[], 
                              float  rate_K_sample_freqs_kHz[], 
                              int    K,
                              float *mean,
                              float  rate_K_vec_no_mean[], 
                              float  rate_K_vec_no_mean_[],
                              int	 plosiv
                              );
void newamp2_indexes_to_rate_K_vec(float  rate_K_vec_[],  
                                   float  rate_K_vec_no_mean_[],
                                   float  rate_K_sample_freqs_kHz[], 
                                   int    K,
                                   float *mean_,
                                   int    indexes[],
                                   float  pf_gain);
void newamp2_16k_indexes_to_rate_K_vec(float  rate_K_vec_[],  
                                   float  rate_K_vec_no_mean_[],
                                   float  rate_K_sample_freqs_kHz[], 
                                   int    K,
                                   float *mean_,
                                   int    indexes[],
                                   float  pf_gain);
void newamp2_indexes_to_model(C2CONST *c2const,
                              MODEL  model_[],
                              COMP   H[],
                              float  interpolated_surface_[],
                              float  prev_rate_K_vec_[],
                              float  *Wo_left,
                              int    *voicing_left,
                              float  rate_K_sample_freqs_kHz[], 
                              int    K,
                              codec2_fft_cfg fwd_cfg, 
                              codec2_fft_cfg inv_cfg,
                              int    indexes[],
                              float  pf_gain,
                              int flag16k);

#endif
