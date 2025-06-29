/*---------------------------------------------------------------------------*\

  FILE........: codec2.c
  AUTHOR......: David Rowe
  DATE CREATED: 21/8/2010

  Codec2 fully quantised encoder and decoder functions.  If you want use
  codec2, the codec2_xxx functions are for you.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2010 David Rowe

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
#include <stdbool.h>
#include <string.h>
#include <math.h>

#include "defines.h"
#include "codec2_fft.h"
#include "sine.h"
#include "nlp.h"
#include "dump.h"
#include "lpc.h"
#include "quantise.h"
#include "phase.h"
#include "interp.h"
#include "postfilter.h"
#include "codec2.h"
#include "lsp.h"
#include "newamp2.h"
#include "codec2_internal.h"
#include "machdep.h"
#include "bpf.h"
#include "bpfb.h"
#include "c2wideband.h"

#include "debug_alloc.h"

/*---------------------------------------------------------------------------* \

                             FUNCTION HEADERS

\*---------------------------------------------------------------------------*/

void analyse_one_frame(struct CODEC2 *c2, MODEL *model, short speech[]);
void synthesise_one_frame(struct CODEC2 *c2, short speech[], MODEL *model,
			  COMP Aw[], float gain);
void codec2_encode_3200(struct CODEC2 *c2, unsigned char * bits, short speech[]);
void codec2_decode_3200(struct CODEC2 *c2, short speech[], const unsigned char * bits);
void codec2_encode_2400(struct CODEC2 *c2, unsigned char * bits, short speech[]);
void codec2_decode_2400(struct CODEC2 *c2, short speech[], const unsigned char * bits);
void codec2_encode_1600(struct CODEC2 *c2, unsigned char * bits, short speech[]);
void codec2_decode_1600(struct CODEC2 *c2, short speech[], const unsigned char * bits);
void codec2_encode_1400(struct CODEC2 *c2, unsigned char * bits, short speech[]);
void codec2_decode_1400(struct CODEC2 *c2, short speech[], const unsigned char * bits);
void codec2_encode_1300(struct CODEC2 *c2, unsigned char * bits, short speech[]);
void codec2_decode_1300(struct CODEC2 *c2, short speech[], const unsigned char * bits, float ber_est);
void codec2_encode_1200(struct CODEC2 *c2, unsigned char * bits, short speech[]);
void codec2_decode_1200(struct CODEC2 *c2, short speech[], const unsigned char * bits);
void codec2_encode_700c(struct CODEC2 *c2, unsigned char * bits, short speech[]);
void codec2_decode_700c(struct CODEC2 *c2, short speech[], const unsigned char * bits);
void codec2_encode_450(struct CODEC2 *c2, unsigned char * bits, short speech[]);
void codec2_decode_450(struct CODEC2 *c2, short speech[], const unsigned char * bits);
void codec2_decode_450pwb(struct CODEC2 *c2, short speech[], const unsigned char * bits);
static void ear_protection(float in_out[], int n);



/*---------------------------------------------------------------------------*\

                                FUNCTIONS

\*---------------------------------------------------------------------------*/

/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_create
  AUTHOR......: David Rowe
  DATE CREATED: 21/8/2010

  Create and initialise an instance of the codec.  Returns a pointer
  to the codec states or NULL on failure.  One set of states is
  sufficient for a full duuplex codec (i.e. an encoder and decoder).
  You don't need separate states for encoders and decoders.  See
  c2enc.c and c2dec.c for examples.

\*---------------------------------------------------------------------------*/


//Don't create CODEC2_MODE_450PWB for Encoding as it has undefined behavior !
struct CODEC2 * codec2_create(int mode)
{
    struct CODEC2 *c2;
    int            i,l;

    // ALL POSSIBLE MODES MUST BE CHECKED HERE!
    // we test if the desired mode is enabled at compile time
    // and return NULL if not

    if (false == ( CODEC2_MODE_ACTIVE(CODEC2_MODE_3200, mode) 
		   || CODEC2_MODE_ACTIVE(CODEC2_MODE_2400, mode)
		   || CODEC2_MODE_ACTIVE(CODEC2_MODE_1600, mode)
		   || CODEC2_MODE_ACTIVE(CODEC2_MODE_1400, mode)
		   || CODEC2_MODE_ACTIVE(CODEC2_MODE_1300, mode)
		   || CODEC2_MODE_ACTIVE(CODEC2_MODE_1200, mode)
		   || CODEC2_MODE_ACTIVE(CODEC2_MODE_700C, mode)
		   || CODEC2_MODE_ACTIVE(CODEC2_MODE_450, mode)
		   || CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, mode)
		) ) 
    {
        return NULL;
    }  

    c2 = (struct CODEC2*)MALLOC(sizeof(struct CODEC2));
    if (c2 == NULL)
	return NULL;

    c2->mode = mode;

    /* store constants in a few places for convenience */
    
    if (CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, mode) == 0) {
        c2->c2const = c2const_create(8000, N_S);
    }else{
        c2->c2const = c2const_create(16000, N_S);
    }
    c2->Fs = c2->c2const.Fs;
    int n_samp = c2->n_samp = c2->c2const.n_samp;
    int m_pitch = c2->m_pitch = c2->c2const.m_pitch;
	
    c2->Pn = (float*)MALLOC(2*n_samp*sizeof(float));
    if (c2->Pn == NULL) {
	return NULL;
    }
    c2->Sn_ = (float*)MALLOC(2*n_samp*sizeof(float));
    if (c2->Sn_ == NULL) {
        FREE(c2->Pn);
	return NULL;
    }
    c2->w = (float*)MALLOC(m_pitch*sizeof(float));
    if (c2->w == NULL) {
        FREE(c2->Pn);
        FREE(c2->Sn_);
	return NULL;
    }
    c2->Sn = (float*)MALLOC(m_pitch*sizeof(float));
    if (c2->Sn == NULL) {
        FREE(c2->Pn);
        FREE(c2->Sn_);
        FREE(c2->w);
	return NULL;
    }

    for(i=0; i<m_pitch; i++)
	c2->Sn[i] = 1.0;
    c2->hpf_states[0] = c2->hpf_states[1] = 0.0;
    for(i=0; i<2*n_samp; i++)
	c2->Sn_[i] = 0;
    c2->fft_fwd_cfg = codec2_fft_alloc(FFT_ENC, 0, NULL, NULL);
    c2->fftr_fwd_cfg = codec2_fftr_alloc(FFT_ENC, 0, NULL, NULL);
    make_analysis_window(&c2->c2const, c2->fft_fwd_cfg, c2->w,c2->W);
    make_synthesis_window(&c2->c2const, c2->Pn);
    c2->fftr_inv_cfg = codec2_fftr_alloc(FFT_DEC, 1, NULL, NULL);
    c2->prev_f0_enc = 1/P_MAX_S;
    c2->bg_est = 0.0;
    c2->ex_phase = 0.0;

    for(l=1; l<=MAX_AMP; l++)
	c2->prev_model_dec.A[l] = 0.0;
    c2->prev_model_dec.Wo = TWO_PI/c2->c2const.p_max;
    c2->prev_model_dec.L = PI/c2->prev_model_dec.Wo;
    c2->prev_model_dec.voiced = 0;

    for(i=0; i<LPC_ORD; i++) {
      c2->prev_lsps_dec[i] = i*PI/(LPC_ORD+1);
    }
    c2->prev_e_dec = 1;

    c2->nlp = nlp_create(&c2->c2const);
    if (c2->nlp == NULL) {
	return NULL;
    }

    c2->lpc_pf = 1; c2->bass_boost = 1; c2->beta = LPCPF_BETA; c2->gamma = LPCPF_GAMMA;

    c2->xq_enc[0] = c2->xq_enc[1] = 0.0;
    c2->xq_dec[0] = c2->xq_dec[1] = 0.0;

    c2->smoothing = 0;
    c2->se = 0.0; c2->nse = 0;
    c2->user_rate_K_vec_no_mean_ = NULL;
    c2->post_filter_en = 1;
    
    c2->bpf_buf = (float*)MALLOC(sizeof(float)*(BPF_N+4*c2->n_samp));
    assert(c2->bpf_buf != NULL);
    for(i=0; i<BPF_N+4*c2->n_samp; i++)
        c2->bpf_buf[i] = 0.0;

    c2->softdec = NULL;
    c2->gray = 1;
    
    /* newamp1 initialisation */

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_700C, c2->mode)) {
        mel_sample_freqs_kHz(c2->rate_K_sample_freqs_kHz, NEWAMP1_K, ftomel(200.0), ftomel(3700.0) );
        int k;
        for(k=0; k<NEWAMP1_K; k++) {
            c2->prev_rate_K_vec_[k] = 0.0;
            c2->eq[k] = 0.0;
        }
        c2->eq_en = 0;
        c2->Wo_left = 0.0;
        c2->voicing_left = 0;;
        c2->phase_fft_fwd_cfg = codec2_fft_alloc(NEWAMP1_PHASE_NFFT, 0, NULL, NULL);
        c2->phase_fft_inv_cfg = codec2_fft_alloc(NEWAMP1_PHASE_NFFT, 1, NULL, NULL);
    }

    /* newamp2 initialisation */

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450, c2->mode)) {
        n2_mel_sample_freqs_kHz(c2->n2_rate_K_sample_freqs_kHz, NEWAMP2_K);
        int k;
        for(k=0; k<NEWAMP2_K; k++) {
            c2->n2_prev_rate_K_vec_[k] = 0.0;
        }
        c2->Wo_left = 0.0;
        c2->voicing_left = 0;;
        c2->phase_fft_fwd_cfg = codec2_fft_alloc(NEWAMP2_PHASE_NFFT, 0, NULL, NULL);
        c2->phase_fft_inv_cfg = codec2_fft_alloc(NEWAMP2_PHASE_NFFT, 1, NULL, NULL);
    }
    /* newamp2 PWB initialisation */

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, c2->mode)) {
        n2_mel_sample_freqs_kHz(c2->n2_pwb_rate_K_sample_freqs_kHz, NEWAMP2_16K_K);
        int k;
        for(k=0; k<NEWAMP2_16K_K; k++) {
            c2->n2_pwb_prev_rate_K_vec_[k] = 0.0;
        }
        c2->Wo_left = 0.0;
        c2->voicing_left = 0;;
        c2->phase_fft_fwd_cfg = codec2_fft_alloc(NEWAMP2_PHASE_NFFT, 0, NULL, NULL);
        c2->phase_fft_inv_cfg = codec2_fft_alloc(NEWAMP2_PHASE_NFFT, 1, NULL, NULL);
    }

    c2->fmlfeat = NULL; c2->fmlmodel = NULL;

    // make sure that one of the two decode function pointers is empty
    // for the encode function pointer this is not required since we always set it
    // to a meaningful value
  
    c2->decode = NULL;
    c2->decode_ber = NULL;

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_3200, c2->mode))
    {
	c2->encode = codec2_encode_3200;
	c2->decode = codec2_decode_3200;
    }

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_2400, c2->mode))
    {
	c2->encode = codec2_encode_2400;
	c2->decode = codec2_decode_2400;
    }

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1600, c2->mode))
    {
	c2->encode = codec2_encode_1600;
	c2->decode = codec2_decode_1600;
    }

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1400, c2->mode))
    {
	c2->encode = codec2_encode_1400;
	c2->decode = codec2_decode_1400;
    }

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1300, c2->mode))
    {
	c2->encode = codec2_encode_1300;
	c2->decode_ber = codec2_decode_1300;
    }

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1200, c2->mode))
    {
	c2->encode = codec2_encode_1200;
	c2->decode = codec2_decode_1200;
    }

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_700C, c2->mode))
    {
	c2->encode = codec2_encode_700c;
	c2->decode = codec2_decode_700c;
    }

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450, c2->mode))
    {
	c2->encode = codec2_encode_450;
	c2->decode = codec2_decode_450;
    }

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, c2->mode))
    {
    	//Encode PWB doesnt make sense
	c2->encode = codec2_encode_450;
	c2->decode = codec2_decode_450pwb;
    }

    
    return c2;
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_destroy
  AUTHOR......: David Rowe
  DATE CREATED: 21/8/2010

  Destroy an instance of the codec.

\*---------------------------------------------------------------------------*/

void codec2_destroy(struct CODEC2 *c2)
{
    assert(c2 != NULL);
    FREE(c2->bpf_buf);
    nlp_destroy(c2->nlp);
    codec2_fft_free(c2->fft_fwd_cfg);
    codec2_fftr_free(c2->fftr_fwd_cfg);
    codec2_fftr_free(c2->fftr_inv_cfg);
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_700C, c2->mode)) {
        codec2_fft_free(c2->phase_fft_fwd_cfg);
        codec2_fft_free(c2->phase_fft_inv_cfg);
    }
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450, c2->mode)) {
        codec2_fft_free(c2->phase_fft_fwd_cfg);
        codec2_fft_free(c2->phase_fft_inv_cfg);
    }
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, c2->mode)) {
        codec2_fft_free(c2->phase_fft_fwd_cfg);
        codec2_fft_free(c2->phase_fft_inv_cfg);
    }
    FREE(c2->Pn);
    FREE(c2->Sn);
    FREE(c2->w);
    FREE(c2->Sn_);
    FREE(c2);
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_bits_per_frame
  AUTHOR......: David Rowe
  DATE CREATED: Nov 14 2011

  Returns the number of bits per frame.

\*---------------------------------------------------------------------------*/

int codec2_bits_per_frame(struct CODEC2 *c2) {
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_3200, c2->mode))
	return 64;
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_2400, c2->mode))
	return 48;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1600, c2->mode))
	return 64;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1400, c2->mode))
	return 56;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1300, c2->mode))
	return 52;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1200, c2->mode))
	return 48;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_700C, c2->mode))
	return 28;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450, c2->mode))
	return 18;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, c2->mode))
	return 18;

    return 0; /* shouldn't get here */   
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_bytes_per_frame
  DATE CREATED: April 2021

  Returns the number of bytes per frame.  Useful for allocated storage for
  codec2_encode()/codec2_decode().  Note the number of bits may not be a
  multiple of 8, thefore some bits in the last byte may be unused.

\*---------------------------------------------------------------------------*/

int codec2_bytes_per_frame(struct CODEC2 *c2) {
	return (codec2_bits_per_frame(c2)+7)/8;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_samples_per_frame
  AUTHOR......: David Rowe
  DATE CREATED: Nov 14 2011

  Returns the number of speech samples per frame.

\*---------------------------------------------------------------------------*/

int codec2_samples_per_frame(struct CODEC2 *c2) {
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_3200, c2->mode))
	return 160;
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_2400, c2->mode))
	return 160;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1600, c2->mode))
	return 320;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1400, c2->mode))
	return 320;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1300, c2->mode))
	return 320;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1200, c2->mode))
	return 320;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_700C, c2->mode))
	return 320;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450, c2->mode))
	return 320;
    if  ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, c2->mode))
	return 640;
    return 0; /* shouldnt get here */
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_encode
  AUTHOR......: David Rowe
  DATE CREATED: Nov 14 2011

  Take an input buffer of speech samples, and compress them to a packed buffer
  of bytes.

\*---------------------------------------------------------------------------*/

void codec2_encode(struct CODEC2 *c2, unsigned char *bytes, short speech[])
{
    assert(c2 != NULL);
    assert(c2->encode != NULL);

    c2->encode(c2, bytes, speech);

}

/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode
  AUTHOR......: David Rowe
  DATE CREATED: Nov 14 2011

  Take an input packed buffer of bytes, and decode them to a buffer of speech
  samples.

\*---------------------------------------------------------------------------*/

void codec2_decode(struct CODEC2 *c2, short speech[], const unsigned char *bytes)
{
    codec2_decode_ber(c2, speech, bytes, 0.0);
}

void codec2_decode_ber(struct CODEC2 *c2, short speech[], const unsigned char *bits, float ber_est)
{
    assert(c2 != NULL);
    assert(c2->decode != NULL || c2->decode_ber != NULL);

    if (c2->decode != NULL)
    {
	c2->decode(c2, speech, bits);
    }
    else
    {
	c2->decode_ber(c2, speech, bits, ber_est);
    }
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_encode_3200
  AUTHOR......: David Rowe
  DATE CREATED: 13 Sep 2012

  Encodes 160 speech samples (20ms of speech) into 64 bits.

  The codec2 algorithm actually operates internally on 10ms (80
  sample) frames, so we run the encoding algorithm twice.  On the
  first frame we just send the voicing bits.  On the second frame we
  send all model parameters.  Compared to 2400 we encode the LSP
  differences, a larger number of bits for the LSP(d)s and scalar
  (non-VQ) quantisation for pitch and energy.

  The bit allocation is:

    Parameter                                   bits/frame
    ------------------------------------------------------
    Harmonic magnitudes (LSP differerences)     50
    Pitch (Wo)                                   7
    Energy                                       5
    Voicing (10ms update)                        2
    TOTAL                                       64

\*---------------------------------------------------------------------------*/

void codec2_encode_3200(struct CODEC2 *c2, unsigned char * bits, short speech[])
{
    MODEL   model;
    float   ak[LPC_ORD+1];
    float   lsps[LPC_ORD];
    float   e;
    int     Wo_index, e_index;
    int     lspd_indexes[LPC_ORD];
    int     i;
    unsigned int nbit = 0;

    assert(c2 != NULL);

    memset(bits, '\0', ((codec2_bits_per_frame(c2) + 7) / 8));

    /* first 10ms analysis frame - we just want voicing */

    analyse_one_frame(c2, &model, speech);
    pack(bits, &nbit, model.voiced, 1);

    /* second 10ms analysis frame */

    analyse_one_frame(c2, &model, &speech[c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);
    Wo_index = encode_Wo(&c2->c2const, model.Wo, WO_BITS);
    pack(bits, &nbit, Wo_index, WO_BITS);

    e = speech_to_uq_lsps(lsps, ak, c2->Sn, c2->w, c2->m_pitch, LPC_ORD);
    e_index = encode_energy(e, E_BITS);
    pack(bits, &nbit, e_index, E_BITS);

    encode_lspds_scalar(lspd_indexes, lsps, LPC_ORD);
    for(i=0; i<LSPD_SCALAR_INDEXES; i++) {
	pack(bits, &nbit, lspd_indexes[i], lspd_bits(i));
    }
    assert(nbit == (unsigned)codec2_bits_per_frame(c2));
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode_3200
  AUTHOR......: David Rowe
  DATE CREATED: 13 Sep 2012

  Decodes a frame of 64 bits into 160 samples (20ms) of speech.

\*---------------------------------------------------------------------------*/

void codec2_decode_3200(struct CODEC2 *c2, short speech[], const unsigned char * bits)
{
    MODEL   model[2];
    int     lspd_indexes[LPC_ORD];
    float   lsps[2][LPC_ORD];
    int     Wo_index, e_index;
    float   e[2];
    float   snr;
    float   ak[2][LPC_ORD+1];
    int     i,j;
    unsigned int nbit = 0;
    COMP    Aw[FFT_ENC];

    assert(c2 != NULL);

    /* only need to zero these out due to (unused) snr calculation */

    for(i=0; i<2; i++)
	for(j=1; j<=MAX_AMP; j++)
	    model[i].A[j] = 0.0;

    /* unpack bits from channel ------------------------------------*/

    /* this will partially fill the model params for the 2 x 10ms
       frames */

    model[0].voiced = unpack(bits, &nbit, 1);
    model[1].voiced = unpack(bits, &nbit, 1);

    Wo_index = unpack(bits, &nbit, WO_BITS);
    model[1].Wo = decode_Wo(&c2->c2const, Wo_index, WO_BITS);
    model[1].L  = PI/model[1].Wo;

    e_index = unpack(bits, &nbit, E_BITS);
    e[1] = decode_energy(e_index, E_BITS);

    for(i=0; i<LSPD_SCALAR_INDEXES; i++) {
	lspd_indexes[i] = unpack(bits, &nbit, lspd_bits(i));
    }
    decode_lspds_scalar(&lsps[1][0], lspd_indexes, LPC_ORD);

    /* interpolate ------------------------------------------------*/

    /* Wo and energy are sampled every 20ms, so we interpolate just 1
       10ms frame between 20ms samples */

    interp_Wo(&model[0], &c2->prev_model_dec, &model[1], c2->c2const.Wo_min);
    e[0] = interp_energy(c2->prev_e_dec, e[1]);

    /* LSPs are sampled every 20ms so we interpolate the frame in
       between, then recover spectral amplitudes */

    interpolate_lsp_ver2(&lsps[0][0], c2->prev_lsps_dec, &lsps[1][0], 0.5, LPC_ORD);

    for(i=0; i<2; i++) {
	lsp_to_lpc(&lsps[i][0], &ak[i][0], LPC_ORD);
	aks_to_M2(c2->fftr_fwd_cfg, &ak[i][0], LPC_ORD, &model[i], e[i], &snr, 0, 0,
                  c2->lpc_pf, c2->bass_boost, c2->beta, c2->gamma, Aw);
	apply_lpc_correction(&model[i]);
	synthesise_one_frame(c2, &speech[c2->n_samp*i], &model[i], Aw, 1.0);
    }

    /* update memories for next frame ----------------------------*/

    c2->prev_model_dec = model[1];
    c2->prev_e_dec = e[1];
    for(i=0; i<LPC_ORD; i++)
	c2->prev_lsps_dec[i] = lsps[1][i];
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_encode_2400
  AUTHOR......: David Rowe
  DATE CREATED: 21/8/2010

  Encodes 160 speech samples (20ms of speech) into 48 bits.

  The codec2 algorithm actually operates internally on 10ms (80
  sample) frames, so we run the encoding algorithm twice.  On the
  first frame we just send the voicing bit.  On the second frame we
  send all model parameters.

  The bit allocation is:

    Parameter                      bits/frame
    --------------------------------------
    Harmonic magnitudes (LSPs)     36
    Joint VQ of Energy and Wo       8
    Voicing (10ms update)           2
    Spare                           2
    TOTAL                          48

\*---------------------------------------------------------------------------*/

void codec2_encode_2400(struct CODEC2 *c2, unsigned char * bits, short speech[])
{
    MODEL   model;
    float   ak[LPC_ORD+1];
    float   lsps[LPC_ORD];
    float   e;
    int     WoE_index;
    int     lsp_indexes[LPC_ORD];
    int     i;
    int     spare = 0;
    unsigned int nbit = 0;

    assert(c2 != NULL);

    memset(bits, '\0', ((codec2_bits_per_frame(c2) + 7) / 8));

    /* first 10ms analysis frame - we just want voicing */

    analyse_one_frame(c2, &model, speech);
    pack(bits, &nbit, model.voiced, 1);

    /* second 10ms analysis frame */

    analyse_one_frame(c2, &model, &speech[c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    e = speech_to_uq_lsps(lsps, ak, c2->Sn, c2->w, c2->m_pitch, LPC_ORD);
    WoE_index = encode_WoE(&model, e, c2->xq_enc);
    pack(bits, &nbit, WoE_index, WO_E_BITS);

    encode_lsps_scalar(lsp_indexes, lsps, LPC_ORD);
    for(i=0; i<LSP_SCALAR_INDEXES; i++) {
	pack(bits, &nbit, lsp_indexes[i], lsp_bits(i));
    }
    pack(bits, &nbit, spare, 2);

    assert(nbit == (unsigned)codec2_bits_per_frame(c2));
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode_2400
  AUTHOR......: David Rowe
  DATE CREATED: 21/8/2010

  Decodes frames of 48 bits into 160 samples (20ms) of speech.

\*---------------------------------------------------------------------------*/

void codec2_decode_2400(struct CODEC2 *c2, short speech[], const unsigned char * bits)
{
    MODEL   model[2];
    int     lsp_indexes[LPC_ORD];
    float   lsps[2][LPC_ORD];
    int     WoE_index;
    float   e[2];
    float   snr;
    float   ak[2][LPC_ORD+1];
    int     i,j;
    unsigned int nbit = 0;
    COMP    Aw[FFT_ENC];

    assert(c2 != NULL);

    /* only need to zero these out due to (unused) snr calculation */

    for(i=0; i<2; i++)
	for(j=1; j<=MAX_AMP; j++)
	    model[i].A[j] = 0.0;

    /* unpack bits from channel ------------------------------------*/

    /* this will partially fill the model params for the 2 x 10ms
       frames */

    model[0].voiced = unpack(bits, &nbit, 1);

    model[1].voiced = unpack(bits, &nbit, 1);
    WoE_index = unpack(bits, &nbit, WO_E_BITS);
    decode_WoE(&c2->c2const, &model[1], &e[1], c2->xq_dec, WoE_index);

    for(i=0; i<LSP_SCALAR_INDEXES; i++) {
	lsp_indexes[i] = unpack(bits, &nbit, lsp_bits(i));
    }
    decode_lsps_scalar(&lsps[1][0], lsp_indexes, LPC_ORD);
    check_lsp_order(&lsps[1][0], LPC_ORD);
    bw_expand_lsps(&lsps[1][0], LPC_ORD, 50.0, 100.0);

    /* interpolate ------------------------------------------------*/

    /* Wo and energy are sampled every 20ms, so we interpolate just 1
       10ms frame between 20ms samples */

    interp_Wo(&model[0], &c2->prev_model_dec, &model[1], c2->c2const.Wo_min);
    e[0] = interp_energy(c2->prev_e_dec, e[1]);

    /* LSPs are sampled every 20ms so we interpolate the frame in
       between, then recover spectral amplitudes */

    interpolate_lsp_ver2(&lsps[0][0], c2->prev_lsps_dec, &lsps[1][0], 0.5, LPC_ORD);
    for(i=0; i<2; i++) {
	lsp_to_lpc(&lsps[i][0], &ak[i][0], LPC_ORD);
	aks_to_M2(c2->fftr_fwd_cfg, &ak[i][0], LPC_ORD, &model[i], e[i], &snr, 0, 0,
                  c2->lpc_pf, c2->bass_boost, c2->beta, c2->gamma, Aw);
	apply_lpc_correction(&model[i]);
	synthesise_one_frame(c2, &speech[c2->n_samp*i], &model[i], Aw, 1.0);

	/* dump parameters for deep learning experiments */
	
	if (c2->fmlfeat != NULL) {
	    /* 10 LSPs - energy - Wo - voicing flag - 10 LPCs */                
	    fwrite(&lsps[i][0], LPC_ORD, sizeof(float), c2->fmlfeat);
	    fwrite(&e[i], 1, sizeof(float), c2->fmlfeat);
	    fwrite(&model[i].Wo, 1, sizeof(float), c2->fmlfeat); 
	    float voiced_float = model[i].voiced;
	    fwrite(&voiced_float, 1, sizeof(float), c2->fmlfeat);
	    fwrite(&ak[i][1], LPC_ORD, sizeof(float), c2->fmlfeat);
	}
    }

    /* update memories for next frame ----------------------------*/

    c2->prev_model_dec = model[1];
    c2->prev_e_dec = e[1];
    for(i=0; i<LPC_ORD; i++)
	c2->prev_lsps_dec[i] = lsps[1][i];
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_encode_1600
  AUTHOR......: David Rowe
  DATE CREATED: Feb 28 2013

  Encodes 320 speech samples (40ms of speech) into 64 bits.

  The codec2 algorithm actually operates internally on 10ms (80
  sample) frames, so we run the encoding algorithm 4 times:

  frame 0: voicing bit
  frame 1: voicing bit, Wo and E
  frame 2: voicing bit
  frame 3: voicing bit, Wo and E, scalar LSPs

  The bit allocation is:

    Parameter                      frame 2  frame 4   Total
    -------------------------------------------------------
    Harmonic magnitudes (LSPs)      0       36        36
    Pitch (Wo)                      7        7        14
    Energy                          5        5        10
    Voicing (10ms update)           2        2         4
    TOTAL                          14       50        64

\*---------------------------------------------------------------------------*/

void codec2_encode_1600(struct CODEC2 *c2, unsigned char * bits, short speech[])
{
    MODEL   model;
    float   lsps[LPC_ORD];
    float   ak[LPC_ORD+1];
    float   e;
    int     lsp_indexes[LPC_ORD];
    int     Wo_index, e_index;
    int     i;
    unsigned int nbit = 0;

    assert(c2 != NULL);

    memset(bits, '\0',  ((codec2_bits_per_frame(c2) + 7) / 8));

    /* frame 1: - voicing ---------------------------------------------*/

    analyse_one_frame(c2, &model, speech);
    pack(bits, &nbit, model.voiced, 1);

    /* frame 2: - voicing, scalar Wo & E -------------------------------*/

    analyse_one_frame(c2, &model, &speech[c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    Wo_index = encode_Wo(&c2->c2const, model.Wo, WO_BITS);
    pack(bits, &nbit, Wo_index, WO_BITS);

    /* need to run this just to get LPC energy */
    e = speech_to_uq_lsps(lsps, ak, c2->Sn, c2->w, c2->m_pitch, LPC_ORD);
    e_index = encode_energy(e, E_BITS);
    pack(bits, &nbit, e_index, E_BITS);

    /* frame 3: - voicing ---------------------------------------------*/

    analyse_one_frame(c2, &model, &speech[2*c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    /* frame 4: - voicing, scalar Wo & E, scalar LSPs ------------------*/

    analyse_one_frame(c2, &model, &speech[3*c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    Wo_index = encode_Wo(&c2->c2const, model.Wo, WO_BITS);
    pack(bits, &nbit, Wo_index, WO_BITS);

    e = speech_to_uq_lsps(lsps, ak, c2->Sn, c2->w, c2->m_pitch, LPC_ORD);
    e_index = encode_energy(e, E_BITS);
    pack(bits, &nbit, e_index, E_BITS);

    encode_lsps_scalar(lsp_indexes, lsps, LPC_ORD);
    for(i=0; i<LSP_SCALAR_INDEXES; i++) {
	pack(bits, &nbit, lsp_indexes[i], lsp_bits(i));
    }

    assert(nbit == (unsigned)codec2_bits_per_frame(c2));
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode_1600
  AUTHOR......: David Rowe
  DATE CREATED: 11 May 2012

  Decodes frames of 64 bits into 320 samples (40ms) of speech.

\*---------------------------------------------------------------------------*/

void codec2_decode_1600(struct CODEC2 *c2, short speech[], const unsigned char * bits)
{
    MODEL   model[4];
    int     lsp_indexes[LPC_ORD];
    float   lsps[4][LPC_ORD];
    int     Wo_index, e_index;
    float   e[4];
    float   snr;
    float   ak[4][LPC_ORD+1];
    int     i,j;
    unsigned int nbit = 0;
    float   weight;
    COMP    Aw[FFT_ENC];

    assert(c2 != NULL);

    /* only need to zero these out due to (unused) snr calculation */

    for(i=0; i<4; i++)
	for(j=1; j<=MAX_AMP; j++)
	    model[i].A[j] = 0.0;

    /* unpack bits from channel ------------------------------------*/

    /* this will partially fill the model params for the 4 x 10ms
       frames */

    model[0].voiced = unpack(bits, &nbit, 1);

    model[1].voiced = unpack(bits, &nbit, 1);
    Wo_index = unpack(bits, &nbit, WO_BITS);
    model[1].Wo = decode_Wo(&c2->c2const, Wo_index, WO_BITS);
    model[1].L  = PI/model[1].Wo;

    e_index = unpack(bits, &nbit, E_BITS);
    e[1] = decode_energy(e_index, E_BITS);

    model[2].voiced = unpack(bits, &nbit, 1);

    model[3].voiced = unpack(bits, &nbit, 1);
    Wo_index = unpack(bits, &nbit, WO_BITS);
    model[3].Wo = decode_Wo(&c2->c2const, Wo_index, WO_BITS);
    model[3].L  = PI/model[3].Wo;

    e_index = unpack(bits, &nbit, E_BITS);
    e[3] = decode_energy(e_index, E_BITS);

    for(i=0; i<LSP_SCALAR_INDEXES; i++) {
	lsp_indexes[i] = unpack(bits, &nbit, lsp_bits(i));
    }
    decode_lsps_scalar(&lsps[3][0], lsp_indexes, LPC_ORD);
    check_lsp_order(&lsps[3][0], LPC_ORD);
    bw_expand_lsps(&lsps[3][0], LPC_ORD, 50.0, 100.0);

    /* interpolate ------------------------------------------------*/

    /* Wo and energy are sampled every 20ms, so we interpolate just 1
       10ms frame between 20ms samples */

    interp_Wo(&model[0], &c2->prev_model_dec, &model[1], c2->c2const.Wo_min);
    e[0] = interp_energy(c2->prev_e_dec, e[1]);
    interp_Wo(&model[2], &model[1], &model[3], c2->c2const.Wo_min);
    e[2] = interp_energy(e[1], e[3]);

    /* LSPs are sampled every 40ms so we interpolate the 3 frames in
       between, then recover spectral amplitudes */

    for(i=0, weight=0.25; i<3; i++, weight += 0.25) {
	interpolate_lsp_ver2(&lsps[i][0], c2->prev_lsps_dec, &lsps[3][0], weight, LPC_ORD);
    }
    for(i=0; i<4; i++) {
	lsp_to_lpc(&lsps[i][0], &ak[i][0], LPC_ORD);
	aks_to_M2(c2->fftr_fwd_cfg, &ak[i][0], LPC_ORD, &model[i], e[i], &snr, 0, 0,
                  c2->lpc_pf, c2->bass_boost, c2->beta, c2->gamma, Aw);
	apply_lpc_correction(&model[i]);
	synthesise_one_frame(c2, &speech[c2->n_samp*i], &model[i], Aw, 1.0);
    }

    /* update memories for next frame ----------------------------*/

    c2->prev_model_dec = model[3];
    c2->prev_e_dec = e[3];
    for(i=0; i<LPC_ORD; i++)
	c2->prev_lsps_dec[i] = lsps[3][i];

}

/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_encode_1400
  AUTHOR......: David Rowe
  DATE CREATED: May 11 2012

  Encodes 320 speech samples (40ms of speech) into 56 bits.

  The codec2 algorithm actually operates internally on 10ms (80
  sample) frames, so we run the encoding algorithm 4 times:

  frame 0: voicing bit
  frame 1: voicing bit, joint VQ of Wo and E
  frame 2: voicing bit
  frame 3: voicing bit, joint VQ of Wo and E, scalar LSPs

  The bit allocation is:

    Parameter                      frame 2  frame 4   Total
    -------------------------------------------------------
    Harmonic magnitudes (LSPs)      0       36        36
    Energy+Wo                       8        8        16
    Voicing (10ms update)           2        2         4
    TOTAL                          10       46        56

\*---------------------------------------------------------------------------*/

void codec2_encode_1400(struct CODEC2 *c2, unsigned char * bits, short speech[])
{
    MODEL   model;
    float   lsps[LPC_ORD];
    float   ak[LPC_ORD+1];
    float   e;
    int     lsp_indexes[LPC_ORD];
    int     WoE_index;
    int     i;
    unsigned int nbit = 0;

    assert(c2 != NULL);

    memset(bits, '\0',  ((codec2_bits_per_frame(c2) + 7) / 8));

    /* frame 1: - voicing ---------------------------------------------*/

    analyse_one_frame(c2, &model, speech);
    pack(bits, &nbit, model.voiced, 1);

    /* frame 2: - voicing, joint Wo & E -------------------------------*/

    analyse_one_frame(c2, &model, &speech[c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    /* need to run this just to get LPC energy */
    e = speech_to_uq_lsps(lsps, ak, c2->Sn, c2->w, c2->m_pitch, LPC_ORD);

    WoE_index = encode_WoE(&model, e, c2->xq_enc);
    pack(bits, &nbit, WoE_index, WO_E_BITS);

    /* frame 3: - voicing ---------------------------------------------*/

    analyse_one_frame(c2, &model, &speech[2*c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    /* frame 4: - voicing, joint Wo & E, scalar LSPs ------------------*/

    analyse_one_frame(c2, &model, &speech[3*c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    e = speech_to_uq_lsps(lsps, ak, c2->Sn, c2->w, c2->m_pitch, LPC_ORD);
    WoE_index = encode_WoE(&model, e, c2->xq_enc);
    pack(bits, &nbit, WoE_index, WO_E_BITS);

    encode_lsps_scalar(lsp_indexes, lsps, LPC_ORD);
    for(i=0; i<LSP_SCALAR_INDEXES; i++) {
	pack(bits, &nbit, lsp_indexes[i], lsp_bits(i));
    }

    assert(nbit == (unsigned)codec2_bits_per_frame(c2));
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode_1400
  AUTHOR......: David Rowe
  DATE CREATED: 11 May 2012

  Decodes frames of 56 bits into 320 samples (40ms) of speech.

\*---------------------------------------------------------------------------*/

void codec2_decode_1400(struct CODEC2 *c2, short speech[], const unsigned char * bits)
{
    MODEL   model[4];
    int     lsp_indexes[LPC_ORD];
    float   lsps[4][LPC_ORD];
    int     WoE_index;
    float   e[4];
    float   snr;
    float   ak[4][LPC_ORD+1];
    int     i,j;
    unsigned int nbit = 0;
    float   weight;
    COMP    Aw[FFT_ENC];

    assert(c2 != NULL);

    /* only need to zero these out due to (unused) snr calculation */

    for(i=0; i<4; i++)
	for(j=1; j<=MAX_AMP; j++)
	    model[i].A[j] = 0.0;

    /* unpack bits from channel ------------------------------------*/

    /* this will partially fill the model params for the 4 x 10ms
       frames */

    model[0].voiced = unpack(bits, &nbit, 1);

    model[1].voiced = unpack(bits, &nbit, 1);
    WoE_index = unpack(bits, &nbit, WO_E_BITS);
    decode_WoE(&c2->c2const, &model[1], &e[1], c2->xq_dec, WoE_index);

    model[2].voiced = unpack(bits, &nbit, 1);

    model[3].voiced = unpack(bits, &nbit, 1);
    WoE_index = unpack(bits, &nbit, WO_E_BITS);
    decode_WoE(&c2->c2const, &model[3], &e[3], c2->xq_dec, WoE_index);

    for(i=0; i<LSP_SCALAR_INDEXES; i++) {
	lsp_indexes[i] = unpack(bits, &nbit, lsp_bits(i));
    }
    decode_lsps_scalar(&lsps[3][0], lsp_indexes, LPC_ORD);
    check_lsp_order(&lsps[3][0], LPC_ORD);
    bw_expand_lsps(&lsps[3][0], LPC_ORD, 50.0, 100.0);

    /* interpolate ------------------------------------------------*/

    /* Wo and energy are sampled every 20ms, so we interpolate just 1
       10ms frame between 20ms samples */

    interp_Wo(&model[0], &c2->prev_model_dec, &model[1], c2->c2const.Wo_min);
    e[0] = interp_energy(c2->prev_e_dec, e[1]);
    interp_Wo(&model[2], &model[1], &model[3], c2->c2const.Wo_min);
    e[2] = interp_energy(e[1], e[3]);

    /* LSPs are sampled every 40ms so we interpolate the 3 frames in
       between, then recover spectral amplitudes */

    for(i=0, weight=0.25; i<3; i++, weight += 0.25) {
	interpolate_lsp_ver2(&lsps[i][0], c2->prev_lsps_dec, &lsps[3][0], weight, LPC_ORD);
    }
    for(i=0; i<4; i++) {
	lsp_to_lpc(&lsps[i][0], &ak[i][0], LPC_ORD);
	aks_to_M2(c2->fftr_fwd_cfg, &ak[i][0], LPC_ORD, &model[i], e[i], &snr, 0, 0,
                  c2->lpc_pf, c2->bass_boost, c2->beta, c2->gamma, Aw);
	apply_lpc_correction(&model[i]);
	synthesise_one_frame(c2, &speech[c2->n_samp*i], &model[i], Aw, 1.0);
    }

    /* update memories for next frame ----------------------------*/

    c2->prev_model_dec = model[3];
    c2->prev_e_dec = e[3];
    for(i=0; i<LPC_ORD; i++)
	c2->prev_lsps_dec[i] = lsps[3][i];

}

/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_encode_1300
  AUTHOR......: David Rowe
  DATE CREATED: March 14 2013

  Encodes 320 speech samples (40ms of speech) into 52 bits.

  The codec2 algorithm actually operates internally on 10ms (80
  sample) frames, so we run the encoding algorithm 4 times:

  frame 0: voicing bit
  frame 1: voicing bit,
  frame 2: voicing bit
  frame 3: voicing bit, Wo and E, scalar LSPs

  The bit allocation is:

    Parameter                      frame 2  frame 4   Total
    -------------------------------------------------------
    Harmonic magnitudes (LSPs)      0       36        36
    Pitch (Wo)                      0        7         7
    Energy                          0        5         5
    Voicing (10ms update)           2        2         4
    TOTAL                           2       50        52

\*---------------------------------------------------------------------------*/

void codec2_encode_1300(struct CODEC2 *c2, unsigned char * bits, short speech[])
{
    MODEL   model;
    float   lsps[LPC_ORD];
    float   ak[LPC_ORD+1];
    float   e;
    int     lsp_indexes[LPC_ORD];
    int     Wo_index, e_index;
    int     i;
    unsigned int nbit = 0;
 
    assert(c2 != NULL);

    memset(bits, '\0',  ((codec2_bits_per_frame(c2) + 7) / 8));

    /* frame 1: - voicing ---------------------------------------------*/

    analyse_one_frame(c2, &model, speech);
    pack_natural_or_gray(bits, &nbit, model.voiced, 1, c2->gray);

    /* frame 2: - voicing ---------------------------------------------*/

    analyse_one_frame(c2, &model, &speech[c2->n_samp]);
    pack_natural_or_gray(bits, &nbit, model.voiced, 1, c2->gray);

    /* frame 3: - voicing ---------------------------------------------*/

    analyse_one_frame(c2, &model, &speech[2*c2->n_samp]);
    pack_natural_or_gray(bits, &nbit, model.voiced, 1, c2->gray);

    /* frame 4: - voicing, scalar Wo & E, scalar LSPs ------------------*/

    analyse_one_frame(c2, &model, &speech[3*c2->n_samp]);
    pack_natural_or_gray(bits, &nbit, model.voiced, 1, c2->gray);

    Wo_index = encode_Wo(&c2->c2const, model.Wo, WO_BITS);
    pack_natural_or_gray(bits, &nbit, Wo_index, WO_BITS, c2->gray);

    e = speech_to_uq_lsps(lsps, ak, c2->Sn, c2->w, c2->m_pitch, LPC_ORD);
    e_index = encode_energy(e, E_BITS);
    pack_natural_or_gray(bits, &nbit, e_index, E_BITS, c2->gray);

    encode_lsps_scalar(lsp_indexes, lsps, LPC_ORD);
    for(i=0; i<LSP_SCALAR_INDEXES; i++) {
	pack_natural_or_gray(bits, &nbit, lsp_indexes[i], lsp_bits(i), c2->gray);
    }

    assert(nbit == (unsigned)codec2_bits_per_frame(c2));
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode_1300
  AUTHOR......: David Rowe
  DATE CREATED: 11 May 2012

  Decodes frames of 52 bits into 320 samples (40ms) of speech.

\*---------------------------------------------------------------------------*/

void codec2_decode_1300(struct CODEC2 *c2, short speech[], const unsigned char * bits, float ber_est)
{
    MODEL   model[4];
    int     lsp_indexes[LPC_ORD];
    float   lsps[4][LPC_ORD];
    int     Wo_index, e_index;
    float   e[4];
    float   snr;
    float   ak[4][LPC_ORD+1];
    int     i,j;
    unsigned int nbit = 0;
    float   weight;
    COMP    Aw[FFT_ENC];

    assert(c2 != NULL);
    
    /* only need to zero these out due to (unused) snr calculation */

    for(i=0; i<4; i++)
	for(j=1; j<=MAX_AMP; j++)
	    model[i].A[j] = 0.0;

    /* unpack bits from channel ------------------------------------*/

    /* this will partially fill the model params for the 4 x 10ms
       frames */

    model[0].voiced = unpack_natural_or_gray(bits, &nbit, 1, c2->gray);
    model[1].voiced = unpack_natural_or_gray(bits, &nbit, 1, c2->gray);
    model[2].voiced = unpack_natural_or_gray(bits, &nbit, 1, c2->gray);
    model[3].voiced = unpack_natural_or_gray(bits, &nbit, 1, c2->gray);

    Wo_index = unpack_natural_or_gray(bits, &nbit, WO_BITS, c2->gray);
    model[3].Wo = decode_Wo(&c2->c2const, Wo_index, WO_BITS);
    model[3].L  = PI/model[3].Wo;

    e_index = unpack_natural_or_gray(bits, &nbit, E_BITS, c2->gray);
    e[3] = decode_energy(e_index, E_BITS);

    for(i=0; i<LSP_SCALAR_INDEXES; i++) {
	lsp_indexes[i] = unpack_natural_or_gray(bits, &nbit, lsp_bits(i), c2->gray);
    }
    decode_lsps_scalar(&lsps[3][0], lsp_indexes, LPC_ORD);
    check_lsp_order(&lsps[3][0], LPC_ORD);
    bw_expand_lsps(&lsps[3][0], LPC_ORD, 50.0, 100.0);

    if (ber_est > 0.15) {
        model[0].voiced =  model[1].voiced = model[2].voiced = model[3].voiced = 0;
        e[3] = decode_energy(10, E_BITS);
        bw_expand_lsps(&lsps[3][0], LPC_ORD, 200.0, 200.0);
        //fprintf(stderr, "soft mute\n");
    }

    /* interpolate ------------------------------------------------*/

    /* Wo, energy, and LSPs are sampled every 40ms so we interpolate
       the 3 frames in between */

    for(i=0, weight=0.25; i<3; i++, weight += 0.25) {
	interpolate_lsp_ver2(&lsps[i][0], c2->prev_lsps_dec, &lsps[3][0], weight, LPC_ORD);
        interp_Wo2(&model[i], &c2->prev_model_dec, &model[3], weight, c2->c2const.Wo_min);
        e[i] = interp_energy2(c2->prev_e_dec, e[3],weight);
    }

    /* then recover spectral amplitudes */

    for(i=0; i<4; i++) {
	lsp_to_lpc(&lsps[i][0], &ak[i][0], LPC_ORD);
	aks_to_M2(c2->fftr_fwd_cfg, &ak[i][0], LPC_ORD, &model[i], e[i], &snr, 0, 0,
                  c2->lpc_pf, c2->bass_boost, c2->beta, c2->gamma, Aw);
	apply_lpc_correction(&model[i]);
	synthesise_one_frame(c2, &speech[c2->n_samp*i], &model[i], Aw, 1.0);

	/* dump parameters for deep learning experiments */
	
	if (c2->fmlfeat != NULL) {
	    /* 10 LSPs - energy - Wo - voicing flag - 10 LPCs */                
	    fwrite(&lsps[i][0], LPC_ORD, sizeof(float), c2->fmlfeat);
	    fwrite(&e[i], 1, sizeof(float), c2->fmlfeat);
	    fwrite(&model[i].Wo, 1, sizeof(float), c2->fmlfeat); 
	    float voiced_float = model[i].voiced;
	    fwrite(&voiced_float, 1, sizeof(float), c2->fmlfeat);
	    fwrite(&ak[i][1], LPC_ORD, sizeof(float), c2->fmlfeat);
	}
    }
 
    #ifdef DUMP
    dump_lsp_(&lsps[3][0]);
    dump_ak_(&ak[3][0], LPC_ORD);
    #endif

    /* update memories for next frame ----------------------------*/

    c2->prev_model_dec = model[3];
    c2->prev_e_dec = e[3];
    for(i=0; i<LPC_ORD; i++)
	c2->prev_lsps_dec[i] = lsps[3][i];

}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_encode_1200
  AUTHOR......: David Rowe
  DATE CREATED: Nov 14 2011

  Encodes 320 speech samples (40ms of speech) into 48 bits.

  The codec2 algorithm actually operates internally on 10ms (80
  sample) frames, so we run the encoding algorithm four times:

  frame 0: voicing bit
  frame 1: voicing bit, joint VQ of Wo and E
  frame 2: voicing bit
  frame 3: voicing bit, joint VQ of Wo and E, VQ LSPs

  The bit allocation is:

    Parameter                      frame 2  frame 4   Total
    -------------------------------------------------------
    Harmonic magnitudes (LSPs)      0       27        27
    Energy+Wo                       8        8        16
    Voicing (10ms update)           2        2         4
    Spare                           0        1         1
    TOTAL                          10       38        48

\*---------------------------------------------------------------------------*/

void codec2_encode_1200(struct CODEC2 *c2, unsigned char * bits, short speech[])
{
    MODEL   model;
    float   lsps[LPC_ORD];
    float   lsps_[LPC_ORD];
    float   ak[LPC_ORD+1];
    float   e;
    int     lsp_indexes[LPC_ORD];
    int     WoE_index;
    int     i;
    int     spare = 0;
    unsigned int nbit = 0;

    assert(c2 != NULL);

    memset(bits, '\0',  ((codec2_bits_per_frame(c2) + 7) / 8));

    /* frame 1: - voicing ---------------------------------------------*/

    analyse_one_frame(c2, &model, speech);
    pack(bits, &nbit, model.voiced, 1);

    /* frame 2: - voicing, joint Wo & E -------------------------------*/

    analyse_one_frame(c2, &model, &speech[c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    /* need to run this just to get LPC energy */
    e = speech_to_uq_lsps(lsps, ak, c2->Sn, c2->w, c2->m_pitch, LPC_ORD);

    WoE_index = encode_WoE(&model, e, c2->xq_enc);
    pack(bits, &nbit, WoE_index, WO_E_BITS);

    /* frame 3: - voicing ---------------------------------------------*/

    analyse_one_frame(c2, &model, &speech[2*c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    /* frame 4: - voicing, joint Wo & E, scalar LSPs ------------------*/

    analyse_one_frame(c2, &model, &speech[3*c2->n_samp]);
    pack(bits, &nbit, model.voiced, 1);

    e = speech_to_uq_lsps(lsps, ak, c2->Sn, c2->w, c2->m_pitch, LPC_ORD);
    WoE_index = encode_WoE(&model, e, c2->xq_enc);
    pack(bits, &nbit, WoE_index, WO_E_BITS);

    encode_lsps_vq(lsp_indexes, lsps, lsps_, LPC_ORD);
    for(i=0; i<LSP_PRED_VQ_INDEXES; i++) {
	pack(bits, &nbit, lsp_indexes[i], lsp_pred_vq_bits(i));
    }
    pack(bits, &nbit, spare, 1);

    assert(nbit == (unsigned)codec2_bits_per_frame(c2));
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode_1200
  AUTHOR......: David Rowe
  DATE CREATED: 14 Feb 2012

  Decodes frames of 48 bits into 320 samples (40ms) of speech.

\*---------------------------------------------------------------------------*/

void codec2_decode_1200(struct CODEC2 *c2, short speech[], const unsigned char * bits)
{
    MODEL   model[4];
    int     lsp_indexes[LPC_ORD];
    float   lsps[4][LPC_ORD];
    int     WoE_index;
    float   e[4];
    float   snr;
    float   ak[4][LPC_ORD+1];
    int     i,j;
    unsigned int nbit = 0;
    float   weight;
    COMP    Aw[FFT_ENC];

    assert(c2 != NULL);

    /* only need to zero these out due to (unused) snr calculation */

    for(i=0; i<4; i++)
	for(j=1; j<=MAX_AMP; j++)
	    model[i].A[j] = 0.0;

    /* unpack bits from channel ------------------------------------*/

    /* this will partially fill the model params for the 4 x 10ms
       frames */

    model[0].voiced = unpack(bits, &nbit, 1);

    model[1].voiced = unpack(bits, &nbit, 1);
    WoE_index = unpack(bits, &nbit, WO_E_BITS);
    decode_WoE(&c2->c2const, &model[1], &e[1], c2->xq_dec, WoE_index);

    model[2].voiced = unpack(bits, &nbit, 1);

    model[3].voiced = unpack(bits, &nbit, 1);
    WoE_index = unpack(bits, &nbit, WO_E_BITS);
    decode_WoE(&c2->c2const, &model[3], &e[3], c2->xq_dec, WoE_index);

    for(i=0; i<LSP_PRED_VQ_INDEXES; i++) {
	lsp_indexes[i] = unpack(bits, &nbit, lsp_pred_vq_bits(i));
    }
    decode_lsps_vq(lsp_indexes, &lsps[3][0], LPC_ORD , 0);
    check_lsp_order(&lsps[3][0], LPC_ORD);
    bw_expand_lsps(&lsps[3][0], LPC_ORD, 50.0, 100.0);

    /* interpolate ------------------------------------------------*/

    /* Wo and energy are sampled every 20ms, so we interpolate just 1
       10ms frame between 20ms samples */

    interp_Wo(&model[0], &c2->prev_model_dec, &model[1], c2->c2const.Wo_min);
    e[0] = interp_energy(c2->prev_e_dec, e[1]);
    interp_Wo(&model[2], &model[1], &model[3], c2->c2const.Wo_min);
    e[2] = interp_energy(e[1], e[3]);

    /* LSPs are sampled every 40ms so we interpolate the 3 frames in
       between, then recover spectral amplitudes */

    for(i=0, weight=0.25; i<3; i++, weight += 0.25) {
	interpolate_lsp_ver2(&lsps[i][0], c2->prev_lsps_dec, &lsps[3][0], weight, LPC_ORD);
    }
    for(i=0; i<4; i++) {
	lsp_to_lpc(&lsps[i][0], &ak[i][0], LPC_ORD);
	aks_to_M2(c2->fftr_fwd_cfg, &ak[i][0], LPC_ORD, &model[i], e[i], &snr, 0, 0,
                  c2->lpc_pf, c2->bass_boost, c2->beta, c2->gamma, Aw);
	apply_lpc_correction(&model[i]);
	synthesise_one_frame(c2, &speech[c2->n_samp*i], &model[i], Aw, 1.0);
    }

    /* update memories for next frame ----------------------------*/

    c2->prev_model_dec = model[3];
    c2->prev_e_dec = e[3];
    for(i=0; i<LPC_ORD; i++)
	c2->prev_lsps_dec[i] = lsps[3][i];
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_encode_700c
  AUTHOR......: David Rowe
  DATE CREATED: Jan 2017

  Version c of 700 bit/s codec that uses newamp1 fixed rate VQ of amplitudes.

  Encodes 320 speech samples (40ms of speech) into 28 bits.

  The codec2 algorithm actually operates internally on 10ms (80
  sample) frames, so we run the encoding algorithm four times:

  frame 0: nothing
  frame 1: nothing
  frame 2: nothing
  frame 3: 18 bit 2 stage VQ (9 bits/stage), 4 bits energy, 
           6 bit scalar Wo/voicing. No spare bits.

  Voicing is encoded using the 0 index of the Wo quantiser.

  The bit allocation is:

    Parameter                      frames 1-3   frame 4   Total
    -----------------------------------------------------------
    Harmonic magnitudes (rate k VQ)     0         18        18
    Energy                              0          4         4
    log Wo/voicing                      0          6         6
    TOTAL                               0         28        28

\*---------------------------------------------------------------------------*/

void codec2_encode_700c(struct CODEC2 *c2, unsigned char * bits, short speech[])
{
    MODEL        model;
    int          indexes[4], i, M=4;
    unsigned int nbit = 0;

    assert(c2 != NULL);

    memset(bits, '\0',  ((codec2_bits_per_frame(c2) + 7) / 8));

    for(i=0; i<M; i++) {
        analyse_one_frame(c2, &model, &speech[i*c2->n_samp]);
    }

    int K = 20;
    float rate_K_vec[K], mean;
    float rate_K_vec_no_mean[K], rate_K_vec_no_mean_[K];

    newamp1_model_to_indexes(&c2->c2const, 
                             indexes, 
                             &model, 
                             rate_K_vec, 
                             c2->rate_K_sample_freqs_kHz,
                             K,
                             &mean,
                             rate_K_vec_no_mean,
                             rate_K_vec_no_mean_, &c2->se, c2->eq, c2->eq_en);
    c2->nse += K;

#ifndef CORTEX_M4
    /* dump features for deep learning experiments */
    if (c2->fmlfeat != NULL) {
        fwrite(&mean, 1, sizeof(float), c2->fmlfeat);
        fwrite(rate_K_vec_no_mean, K, sizeof(float), c2->fmlfeat);
        fwrite(rate_K_vec_no_mean_, K, sizeof(float), c2->fmlfeat);
	MODEL model_; memcpy(&model_, &model, sizeof(model));
	float rate_K_vec_[K];
	for(int k=0; k<K; k++)
	    rate_K_vec_[k] = rate_K_vec_no_mean_[k] + mean;
	resample_rate_L(&c2->c2const, &model_, rate_K_vec_, c2->rate_K_sample_freqs_kHz, K);
        fwrite(&model_.A, MAX_AMP, sizeof(float), c2->fmlfeat);
    }
    if (c2->fmlmodel != NULL)
	fwrite(&model,sizeof(MODEL),1,c2->fmlmodel);
#endif
    
    pack_natural_or_gray(bits, &nbit, indexes[0], 9, 0);
    pack_natural_or_gray(bits, &nbit, indexes[1], 9, 0);
    pack_natural_or_gray(bits, &nbit, indexes[2], 4, 0);
    pack_natural_or_gray(bits, &nbit, indexes[3], 6, 0);

    assert(nbit == (unsigned)codec2_bits_per_frame(c2));
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode_700c
  AUTHOR......: David Rowe
  DATE CREATED: August 2015

  Decodes frames of 28 bits into 320 samples (40ms) of speech.

\*---------------------------------------------------------------------------*/

void codec2_decode_700c(struct CODEC2 *c2, short speech[], const unsigned char * bits)
{
    MODEL   model[4];
    int     indexes[4];
    int     i;
    unsigned int nbit = 0;

    assert(c2 != NULL);

    /* unpack bits from channel ------------------------------------*/

    indexes[0] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    indexes[1] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    indexes[2] = unpack_natural_or_gray(bits, &nbit, 4, 0);
    indexes[3] = unpack_natural_or_gray(bits, &nbit, 6, 0);
    
    int M = 4;
    COMP  HH[M][MAX_AMP+1];
    float interpolated_surface_[M][NEWAMP1_K];

    newamp1_indexes_to_model(&c2->c2const,
                             model,
                             (COMP*)HH,
                             (float*)interpolated_surface_,
                             c2->prev_rate_K_vec_,
                             &c2->Wo_left,
                             &c2->voicing_left,
                             c2->rate_K_sample_freqs_kHz, 
                             NEWAMP1_K,
                             c2->phase_fft_fwd_cfg, 
                             c2->phase_fft_inv_cfg,
                             indexes,
                             c2->user_rate_K_vec_no_mean_,
                             c2->post_filter_en);


   for(i=0; i<M; i++) {
       if (c2->fmlfeat != NULL) {
	   /* We use standard nb_features=55 feature records for compatability with train_lpcnet.py */
	   float features[55] = {0};
	   /* just using 18/20 for compatability with LPCNet, coarse scaling for NN imput */
	   for(int j=0; j<18; j++)
	       features[j] = (interpolated_surface_[i][j]-30)/40;
	   int pitch_index = 21 + 2.0*M_PI/model[i].Wo;
	   features[36] = 0.02*(pitch_index-100);
	   features[37] = model[i].voiced;
	   fwrite(features, 55, sizeof(float), c2->fmlfeat);
       }
       
       /* 700C is a little quieter so lets apply some experimentally derived audio gain */
       synthesise_one_frame(c2, &speech[c2->n_samp*i], &model[i], &HH[i][0], 1.5);
   }
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_energy_700c
  AUTHOR......: Jeroen Vreeken
  DATE CREATED: Jan 2017

  Decodes energy value from encoded bits.

\*---------------------------------------------------------------------------*/

float codec2_energy_700c(struct CODEC2 *c2, const unsigned char * bits)
{
    int     indexes[4];
    unsigned int nbit = 0;

    assert(c2 != NULL);

    /* unpack bits from channel ------------------------------------*/

    indexes[0] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    indexes[1] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    indexes[2] = unpack_natural_or_gray(bits, &nbit, 4, 0);
    indexes[3] = unpack_natural_or_gray(bits, &nbit, 6, 0);

    float mean = newamp1_energy_cb[0].cb[indexes[2]];
    mean -= 10;
    if (indexes[3] == 0)
    	mean -= 10;

    return POW10F(mean/10.0);
}

float codec2_energy_450(struct CODEC2 *c2, const unsigned char * bits)
{
    int     indexes[4];
    unsigned int nbit = 0;

    assert(c2 != NULL);

    /* unpack bits from channel ------------------------------------*/

    indexes[0] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    //indexes[1] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    indexes[2] = unpack_natural_or_gray(bits, &nbit, 3, 0);
    indexes[3] = unpack_natural_or_gray(bits, &nbit, 6, 0);
    
    float mean = newamp2_energy_cb[0].cb[indexes[2]];
    mean -= 10;
    if (indexes[3] == 0)
    	mean -= 10;

    return POW10F(mean/10.0);
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_get_energy()
  AUTHOR......: Jeroen Vreeken
  DATE CREATED: 08/03/2016

  Extract energy value from an encoded frame.

\*---------------------------------------------------------------------------*/

float codec2_get_energy(struct CODEC2 *c2, const unsigned char *bits)
{
    assert(c2 != NULL);
    assert(
	   ( CODEC2_MODE_ACTIVE(CODEC2_MODE_3200, c2->mode)) ||
	   ( CODEC2_MODE_ACTIVE(CODEC2_MODE_2400, c2->mode)) ||
	   ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1600, c2->mode)) ||
	   ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1400, c2->mode)) ||
	   ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1300, c2->mode)) ||
	   ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1200, c2->mode)) ||
	   ( CODEC2_MODE_ACTIVE(CODEC2_MODE_700C, c2->mode)) ||
	   ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450, c2->mode)) ||
	   ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, c2->mode))
	   );
    MODEL model;
    float xq_dec[2] = {};
    int e_index, WoE_index;
    float e = 0.0f;
    unsigned int nbit;

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_3200, c2->mode)) {
        nbit = 1 + 1 + WO_BITS;
	e_index = unpack(bits, &nbit, E_BITS);
        e = decode_energy(e_index, E_BITS);
    }
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_2400, c2->mode)) {
        nbit = 1 + 1;
        WoE_index = unpack(bits, &nbit, WO_E_BITS);
        decode_WoE(&c2->c2const, &model, &e, xq_dec, WoE_index);
    }
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1600, c2->mode)) {
        nbit = 1 + 1 + WO_BITS;
        e_index = unpack(bits, &nbit, E_BITS);
        e = decode_energy(e_index, E_BITS);
    }
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1400, c2->mode)) {
        nbit = 1 + 1;
        WoE_index = unpack(bits, &nbit, WO_E_BITS);
        decode_WoE(&c2->c2const, &model, &e, xq_dec, WoE_index);
    }
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1300, c2->mode)) {
        nbit = 1 + 1 + 1 + 1 + WO_BITS;
        e_index = unpack_natural_or_gray(bits, &nbit, E_BITS, c2->gray);
        e = decode_energy(e_index, E_BITS);
    }
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_1200, c2->mode)) {
        nbit = 1 + 1;
        WoE_index = unpack(bits, &nbit, WO_E_BITS);
        decode_WoE(&c2->c2const, &model, &e, xq_dec, WoE_index);
    }
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_700C, c2->mode)) {
        e = codec2_energy_700c(c2, bits);
    }
    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_450, c2->mode) ||  CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, c2->mode)) {
        e = codec2_energy_450(c2, bits);
    }
    
    return e;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_encode_450
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018
  
  450 bit/s codec that uses newamp2 fixed rate VQ of amplitudes.

  Encodes 320 speech samples (40ms of speech) into 28 bits.

  The codec2 algorithm actually operates internally on 10ms (80
  sample) frames, so we run the encoding algorithm four times:

  frame 0: nothing
  frame 1: nothing
  frame 2: nothing
  frame 3: 9 bit 1 stage VQ, 3 bits energy, 
           6 bit scalar Wo/voicing/plosive. No spare bits.
           
  If a plosive is detected the frame at the energy-step is encoded.

  Voicing is encoded using the 000000 index of the Wo quantiser.
  Plosive is encoded using the 111111 index of the Wo quantiser.

  The bit allocation is:

    Parameter                      frames 1-3   frame 4   Total
    -----------------------------------------------------------
    Harmonic magnitudes (rate k VQ)     0          9         9 
    Energy                              0          3         3
    log Wo/voicing/plosive              0          6         6
    TOTAL                               0         18        18


\*---------------------------------------------------------------------------*/

void codec2_encode_450(struct CODEC2 *c2, unsigned char * bits, short speech[])
{
	MODEL        model;
    int          indexes[4], i,h, M=4;
    unsigned int nbit = 0;
    int plosiv = 0;
    float energydelta[M];
	int spectralCounter;

    assert(c2 != NULL);

    memset(bits, '\0',  ((codec2_bits_per_frame(c2) + 7) / 8));
    for(i=0; i<M; i++){
        analyse_one_frame(c2, &model, &speech[i*c2->n_samp]);
        energydelta[i] = 0;
        spectralCounter = 0;
        for(h = 0;h<(model.L);h++){
			//only detect above 300 Hz
			if(h*model.Wo*(c2->c2const.Fs/2000.0)/M_PI > 0.3){
				energydelta[i] = (double)energydelta[i] + (double)20.0*log10(model.A[10]+1E-16);
				spectralCounter = spectralCounter+1;
			}
				
			}
		energydelta[i] = energydelta[i] / spectralCounter ;
    }
    //Constants for plosive Detection tdB = threshold; minPwr = from below this level plosives have to rise
    float tdB = 15; //not fixed can be changed
    float minPwr = 15; //not fixed can be changed
		if((c2->energy_prev)<minPwr && energydelta[0]>((c2->energy_prev)+tdB)){
			
			plosiv = 1;
		}
		if(energydelta[0]<minPwr && energydelta[1]>(energydelta[0]+tdB)){
			
			plosiv = 2;
		}
		if(energydelta[1]<minPwr &&energydelta[2]>(energydelta[1]+tdB)){
			
			plosiv = 3;
		}
		if(energydelta[2]<minPwr &&energydelta[3]>(energydelta[2]+tdB)){
			
			plosiv = 4;
		}
	if(plosiv != 0 && plosiv != 4){
		analyse_one_frame(c2, &model, &speech[(plosiv-1)*c2->n_samp]);
		}
    
    c2->energy_prev = energydelta[3];
    

    int K = 29;
    float rate_K_vec[K], mean;
    float rate_K_vec_no_mean[K], rate_K_vec_no_mean_[K];
    if(plosiv > 0){
		plosiv = 1;
	}
    newamp2_model_to_indexes(&c2->c2const, 
                             indexes, 
                             &model, 
                             rate_K_vec, 
                             c2->n2_rate_K_sample_freqs_kHz,
                             K,
                             &mean,
                             rate_K_vec_no_mean,
                             rate_K_vec_no_mean_,
                             plosiv);

                             
	pack_natural_or_gray(bits, &nbit, indexes[0], 9, 0);
    //pack_natural_or_gray(bits, &nbit, indexes[1], 9, 0);
    pack_natural_or_gray(bits, &nbit, indexes[2], 3, 0);
    pack_natural_or_gray(bits, &nbit, indexes[3], 6, 0);

    assert(nbit == (unsigned)codec2_bits_per_frame(c2));
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode_450
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018

\*---------------------------------------------------------------------------*/

void codec2_decode_450(struct CODEC2 *c2, short speech[], const unsigned char * bits)
{
    MODEL   model[4];
    int     indexes[4];
    int     i;
    unsigned int nbit = 0;

    assert(c2 != NULL);

    /* unpack bits from channel ------------------------------------*/

    indexes[0] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    //indexes[1] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    indexes[2] = unpack_natural_or_gray(bits, &nbit, 3, 0);
    indexes[3] = unpack_natural_or_gray(bits, &nbit, 6, 0);
    
    int M = 4;
    COMP  HH[M][MAX_AMP+1];
    float interpolated_surface_[M][NEWAMP2_K];
    int pwbFlag = 0;

    newamp2_indexes_to_model(&c2->c2const,
                             model,
                             (COMP*)HH,
                             (float*)interpolated_surface_,
                             c2->n2_prev_rate_K_vec_,
                             &c2->Wo_left,
                             &c2->voicing_left,
                             c2->n2_rate_K_sample_freqs_kHz, 
                             NEWAMP2_K,
                             c2->phase_fft_fwd_cfg, 
                             c2->phase_fft_inv_cfg,
                             indexes,
                             1.5,
                             pwbFlag);


   for(i=0; i<M; i++) {
       synthesise_one_frame(c2, &speech[c2->n_samp*i], &model[i], &HH[i][0], 1.5);
   }
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: codec2_decode_450pwb
  AUTHOR......: Thomas Kurin and Stefan Erhardt
  INSTITUTE...:	Institute for Electronics Engineering, University of Erlangen-Nuremberg
  DATE CREATED: July 2018
  
  Decodes the 450 codec data in pseudo wideband at 16kHz samplerate.

\*---------------------------------------------------------------------------*/

void codec2_decode_450pwb(struct CODEC2 *c2, short speech[], const unsigned char * bits)
{
    MODEL   model[4];
    int     indexes[4];
    int     i;
    unsigned int nbit = 0;

    assert(c2 != NULL);

    /* unpack bits from channel ------------------------------------*/

    indexes[0] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    //indexes[1] = unpack_natural_or_gray(bits, &nbit, 9, 0);
    indexes[2] = unpack_natural_or_gray(bits, &nbit, 3, 0);
    indexes[3] = unpack_natural_or_gray(bits, &nbit, 6, 0);
    
    int M = 4;
    COMP  HH[M][MAX_AMP+1];
    float interpolated_surface_[M][NEWAMP2_16K_K];
    int pwbFlag = 1;

    newamp2_indexes_to_model(&c2->c2const,
                             model,
                             (COMP*)HH,
                             (float*)interpolated_surface_,
                             c2->n2_pwb_prev_rate_K_vec_,
                             &c2->Wo_left,
                             &c2->voicing_left,
                             c2->n2_pwb_rate_K_sample_freqs_kHz, 
                             NEWAMP2_16K_K,
                             c2->phase_fft_fwd_cfg, 
                             c2->phase_fft_inv_cfg,
                             indexes,
                             1.5,
                             pwbFlag);


   for(i=0; i<M; i++) {
       synthesise_one_frame(c2, &speech[c2->n_samp*i], &model[i], &HH[i][0], 1.5);
   }
}


/*---------------------------------------------------------------------------* \

  FUNCTION....: synthesise_one_frame()
  AUTHOR......: David Rowe
  DATE CREATED: 23/8/2010

  Synthesise 80 speech samples (10ms) from model parameters.

\*---------------------------------------------------------------------------*/

void synthesise_one_frame(struct CODEC2 *c2, short speech[], MODEL *model, COMP Aw[], float gain)
{
    int     i;

    if ( CODEC2_MODE_ACTIVE(CODEC2_MODE_700C, c2->mode) || CODEC2_MODE_ACTIVE(CODEC2_MODE_450, c2->mode) || CODEC2_MODE_ACTIVE(CODEC2_MODE_450PWB, c2->mode)  ) {
        /* newamp1/2, we've already worked out rate L phase */
        COMP *H = Aw;
        phase_synth_zero_order(c2->n_samp, model, &c2->ex_phase, H);       
    } else {
        /* LPC based phase synthesis */
        COMP H[MAX_AMP+1];
        sample_phase(model, H, Aw);
        phase_synth_zero_order(c2->n_samp, model, &c2->ex_phase, H);
    }

    postfilter(model, &c2->bg_est);
    synthesise(c2->n_samp, c2->fftr_inv_cfg, c2->Sn_, model, c2->Pn, 1);

    for(i=0; i<c2->n_samp; i++) {
        c2->Sn_[i] *= gain;
    }
    
    ear_protection(c2->Sn_, c2->n_samp);

    for(i=0; i<c2->n_samp; i++) {
	if (c2->Sn_[i] > 32767.0)
	    speech[i] = 32767;
	else if (c2->Sn_[i] < -32767.0)
	    speech[i] = -32767;
	else
	    speech[i] = c2->Sn_[i];
    }

}


/*---------------------------------------------------------------------------* \

  FUNCTION....: analyse_one_frame()
  AUTHOR......: David Rowe
  DATE CREATED: 23/8/2010

  Extract sinusoidal model parameters from 80 speech samples (10ms of
  speech).

\*---------------------------------------------------------------------------*/

void analyse_one_frame(struct CODEC2 *c2, MODEL *model, short speech[])
{
    COMP    Sw[FFT_ENC];
    float   pitch;
    int     i;
    int     n_samp = c2->n_samp;
    int     m_pitch = c2->m_pitch;

    /* Read input speech */

    for(i=0; i<m_pitch-n_samp; i++)
      c2->Sn[i] = c2->Sn[i+n_samp];
    for(i=0; i<n_samp; i++)
      c2->Sn[i+m_pitch-n_samp] = speech[i];

    dft_speech(&c2->c2const, c2->fft_fwd_cfg, Sw, c2->Sn, c2->w);

    /* Estimate pitch */
    nlp(c2->nlp, c2->Sn, n_samp, &pitch, Sw, c2->W, &c2->prev_f0_enc);
    model->Wo = TWO_PI/pitch;
    model->L = PI/model->Wo;

    /* estimate model parameters */
    two_stage_pitch_refinement(&c2->c2const, model, Sw);

    /* estimate phases when doing ML experiments */
    if (c2->fmlfeat != NULL)
	estimate_amplitudes(model, Sw, c2->W, 1);
    else
	estimate_amplitudes(model, Sw, c2->W, 0);
    est_voicing_mbe(&c2->c2const, model, Sw, c2->W);
    #ifdef DUMP
    dump_model(model);
    #endif
}


/*---------------------------------------------------------------------------* \

  FUNCTION....: ear_protection()
  AUTHOR......: David Rowe
  DATE CREATED: Nov 7 2012

  Limits output level to protect ears when there are bit errors or the input
  is overdriven.  This doesn't correct or mask bit errors, just reduces the
  worst of their damage.

\*---------------------------------------------------------------------------*/

static void ear_protection(float in_out[], int n) {
    float max_sample, over, gain;
    int   i;

    /* find maximum sample in frame */

    max_sample = 0.0;
    for(i=0; i<n; i++)
        if (in_out[i] > max_sample)
            max_sample = in_out[i];

    /* determine how far above set point */

    over = max_sample/30000.0;

    /* If we are x dB over set point we reduce level by 2x dB, this
       attenuates major excursions in amplitude (likely to be caused
       by bit errors) more than smaller ones */

    if (over > 1.0) {
        gain = 1.0/(over*over);
        for(i=0; i<n; i++)
            in_out[i] *= gain;
    }
}


void codec2_set_lpc_post_filter(struct CODEC2 *c2, int enable, int bass_boost, float beta, float gamma)
{
    assert((beta >= 0.0) && (beta <= 1.0));
    assert((gamma >= 0.0) && (gamma <= 1.0));
    c2->lpc_pf = enable;
    c2->bass_boost = bass_boost;
    c2->beta = beta;
    c2->gamma = gamma;
}


/*
   Allows optional stealing of one of the voicing bits for use as a
   spare bit, only 1300 & 1400 & 1600 bit/s supported for now.
   Experimental method of sending voice/data frames for FreeDV.
*/

int codec2_get_spare_bit_index(struct CODEC2 *c2)
{
    assert(c2 != NULL);

    switch(c2->mode) {
    case CODEC2_MODE_1300:
        return 2; // bit 2 (3th bit) is v2 (third voicing bit)
        break;
    case CODEC2_MODE_1400:
        return 10; // bit 10 (11th bit) is v2 (third voicing bit)
        break;
    case CODEC2_MODE_1600:
        return 15; // bit 15 (16th bit) is v2 (third voicing bit)
        break;
    }

    return -1;
}

/*
   Reconstructs the spare voicing bit.  Note works on unpacked bits
   for convenience.
*/

int codec2_rebuild_spare_bit(struct CODEC2 *c2, char unpacked_bits[])
{
    int v1,v3;

    assert(c2 != NULL);

    v1 = unpacked_bits[1];

    switch(c2->mode) {
    case CODEC2_MODE_1300:

        v3 = unpacked_bits[1+1+1];

        /* if either adjacent frame is voiced, make this one voiced */

        unpacked_bits[2] = (v1 || v3);

        return 0;

        break;

    case CODEC2_MODE_1400:

        v3 = unpacked_bits[1+1+8+1];

        /* if either adjacent frame is voiced, make this one voiced */

        unpacked_bits[10] = (v1 || v3);

        return 0;

        break;

    case CODEC2_MODE_1600:
        v3 = unpacked_bits[1+1+8+5+1];

        /* if either adjacent frame is voiced, make this one voiced */

        unpacked_bits[15] = (v1 || v3);

        return 0;

        break;
    }

    return -1;
}

void codec2_set_natural_or_gray(struct CODEC2 *c2, int gray)
{
    assert(c2 != NULL);
    c2->gray = gray;
}

void codec2_set_softdec(struct CODEC2 *c2, float *softdec)
{
    assert(c2 != NULL);
    c2->softdec = softdec;
}

void codec2_open_mlfeat(struct CODEC2 *codec2_state, char *feat_fn, char *model_fn) {
    if ((codec2_state->fmlfeat = fopen(feat_fn, "wb")) == NULL) {
	fprintf(stderr, "error opening machine learning feature file: %s\n", feat_fn);
	exit(1);
    }    
    if (model_fn) {
	if ((codec2_state->fmlmodel = fopen(model_fn, "wb")) == NULL) {
	fprintf(stderr, "error opening machine learning Codec 2 model file: %s\n", feat_fn);
	exit(1);
	}
    }
}

#ifndef __EMBEDDED__
void codec2_load_codebook(struct CODEC2 *codec2_state, int num, char *filename) {
    FILE *f;
    
    if ((f = fopen(filename, "rb")) == NULL) {
	fprintf(stderr, "error opening codebook file: %s\n", filename);
	exit(1);
    }
    //fprintf(stderr, "reading newamp1vq_cb[%d] k=%d m=%d\n", num, newamp1vq_cb[num].k, newamp1vq_cb[num].m);
    float tmp[newamp1vq_cb[num].k*newamp1vq_cb[num].m];
    int nread = fread(tmp, sizeof(float), newamp1vq_cb[num].k*newamp1vq_cb[num].m, f);
    float *p = (float*)newamp1vq_cb[num].cb;
    for(int i=0; i<newamp1vq_cb[num].k*newamp1vq_cb[num].m; i++)
       p[i] = tmp[i];
    // fprintf(stderr, "nread = %d %f %f\n", nread, newamp1vq_cb[num].cb[0], newamp1vq_cb[num].cb[1]);
    assert(nread == newamp1vq_cb[num].k*newamp1vq_cb[num].m);
    fclose(f);
}
#endif

float codec2_get_var(struct CODEC2 *codec2_state) {
    if (codec2_state->nse)
        return codec2_state->se/codec2_state->nse;
    else
        return 0;
}

float *codec2_enable_user_ratek(struct CODEC2 *codec2_state, int *K) {
    codec2_state->user_rate_K_vec_no_mean_ = (float*)malloc(sizeof(float)*NEWAMP1_K);
    *K = NEWAMP1_K;
    return codec2_state->user_rate_K_vec_no_mean_;
}

void codec2_700c_post_filter(struct CODEC2 *codec2_state, int en) {
    codec2_state->post_filter_en = en;
}

void codec2_700c_eq(struct CODEC2 *codec2_state, int en) {
    codec2_state->eq_en = en;
    codec2_state->se = 0.0; codec2_state->nse = 0;
}
