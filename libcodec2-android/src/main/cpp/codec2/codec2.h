/*---------------------------------------------------------------------------*\

  FILE........: codec2.h
  AUTHOR......: David Rowe
  DATE CREATED: 21 August 2010

  Codec 2 fully quantised encoder and decoder functions.  If you want use
  Codec 2, these are the functions you need to call.

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

#ifdef __cplusplus
  extern "C" {
#endif

#ifndef __CODEC2__
#define  __CODEC2__

#include "codec2/version.h"

#define CODEC2_MODE_3200 0
#define CODEC2_MODE_2400 1
#define CODEC2_MODE_1600 2
#define CODEC2_MODE_1400 3
#define CODEC2_MODE_1300 4
#define CODEC2_MODE_1200 5
#define CODEC2_MODE_700  6
#define CODEC2_MODE_700B 7
#define CODEC2_MODE_700C 8
#define CODEC2_MODE_WB 9
#define CODEC2_MODE_450 	10
#define CODEC2_MODE_450PWB 	11

struct CODEC2;

struct CODEC2 *  codec2_create(int mode);
void codec2_destroy(struct CODEC2 *codec2_state);
void codec2_encode(struct CODEC2 *codec2_state, unsigned char * bits, short speech_in[]);
void codec2_decode(struct CODEC2 *codec2_state, short speech_out[], const unsigned char *bits);
void codec2_decode_ber(struct CODEC2 *codec2_state, short speech_out[], const unsigned char *bits, float ber_est);
int  codec2_samples_per_frame(struct CODEC2 *codec2_state);
int  codec2_bits_per_frame(struct CODEC2 *codec2_state);

void codec2_set_lpc_post_filter(struct CODEC2 *codec2_state, int enable, int bass_boost, float beta, float gamma);
int  codec2_get_spare_bit_index(struct CODEC2 *codec2_state);
int  codec2_rebuild_spare_bit(struct CODEC2 *codec2_state, int unpacked_bits[]);
void codec2_set_natural_or_gray(struct CODEC2 *codec2_state, int gray);
void codec2_set_softdec(struct CODEC2 *c2, float *softdec);
float codec2_get_energy(struct CODEC2 *codec2_state, const unsigned char *bits);


#endif

#ifdef __cplusplus
}
#endif

