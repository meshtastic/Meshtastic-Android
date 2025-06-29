/*---------------------------------------------------------------------------*\

  FILE........: codec2_internal.h
  AUTHOR......: David Rowe
  DATE CREATED: April 16 2012

  Header file for Codec2 internal states, exposed via this header
  file to assist in testing.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2012 David Rowe

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

#ifndef __CODEC2_INTERNAL__
#define __CODEC2_INTERNAL__

#include "codec2_fft.h"
#include "newamp1.h"
#include "newamp2.h"

struct CODEC2 {
    int           mode;
    C2CONST       c2const;
    int           Fs;
    int           n_samp;
    int           m_pitch;
    codec2_fft_cfg  fft_fwd_cfg;           /* forward FFT config                        */
    codec2_fftr_cfg fftr_fwd_cfg;          /* forward real FFT config                   */
    float        *w;	                   /* [m_pitch] time domain hamming window      */
    float         W[FFT_ENC];	           /* DFT of w[]                                */
    float        *Pn;	                   /* [2*n_samp] trapezoidal synthesis window   */
    float        *bpf_buf;                 /* buffer for band pass filter               */
    float        *Sn;                      /* [m_pitch] input speech                    */
    float         hpf_states[2];           /* high pass filter states                   */
    void         *nlp;                     /* pitch predictor states                    */
    int           gray;                    /* non-zero for gray encoding                */

    codec2_fftr_cfg  fftr_inv_cfg;         /* inverse FFT config                        */
    float        *Sn_;	                   /* [2*n_samp] synthesised output speech      */
    float         ex_phase;                /* excitation model phase track              */
    float         bg_est;                  /* background noise estimate for post filter */
    float         prev_f0_enc;             /* previous frame's f0    estimate           */
    MODEL         prev_model_dec;          /* previous frame's model parameters         */
    float         prev_lsps_dec[LPC_ORD];  /* previous frame's LSPs                     */
    float         prev_e_dec;              /* previous frame's LPC energy               */

    int           lpc_pf;                  /* LPC post filter on                        */
    int           bass_boost;              /* LPC post filter bass boost                */
    float         beta;                    /* LPC post filter parameters                */
    float         gamma;

    float         xq_enc[2];               /* joint pitch and energy VQ states          */
    float         xq_dec[2];

    int           smoothing;               /* enable smoothing for channels with errors */
    float        *softdec;                 /* optional soft decn bits from demod        */

    /* newamp1 states */

    float          rate_K_sample_freqs_kHz[NEWAMP1_K];
    float          prev_rate_K_vec_[NEWAMP1_K];
    float          Wo_left;
    int            voicing_left;
    codec2_fft_cfg phase_fft_fwd_cfg;
    codec2_fft_cfg phase_fft_inv_cfg;      
    float          se;                       /* running sum of squared error */
    unsigned int   nse;                      /* number of terms in sum       */
    float         *user_rate_K_vec_no_mean_; /* optional, user supplied vector for quantisation experiments */
    int            post_filter_en;
    float          eq[NEWAMP1_K];            /* optional equaliser */
    int            eq_en;
    
    /*newamp2 states (also uses newamp1 states )*/
    float 	   energy_prev;
    float          n2_rate_K_sample_freqs_kHz[NEWAMP2_K];
    float          n2_prev_rate_K_vec_[NEWAMP2_K];
    float          n2_pwb_rate_K_sample_freqs_kHz[NEWAMP2_16K_K];
    float          n2_pwb_prev_rate_K_vec_[NEWAMP2_16K_K];

    /* used to dump features for deep learning experiments */
    FILE *fmlfeat, *fmlmodel;

    /* encode/decode function pointers for the selected mode */
    void (*encode)(struct CODEC2 *c2, unsigned char * bits, short speech[]);
    void (*decode)(struct CODEC2 *c2, short speech[], const unsigned char * bits);
    void (*decode_ber)(struct CODEC2 *c2, short speech[], const unsigned char * bits, float ber_est);
};

// test and debug
void analyse_one_frame(struct CODEC2 *c2, MODEL *model, short speech[]);
void synthesise_one_frame(struct CODEC2 *c2, short speech[], MODEL *model,
			  COMP Aw[], float gain);
#endif
