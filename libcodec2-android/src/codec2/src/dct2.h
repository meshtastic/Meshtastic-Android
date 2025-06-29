/*---------------------------------------------------------------------------*\

  FILE........: dct2.h
  AUTHOR......: Phil Ayres
  DATE CREATED: July 2017

 * DCT functions based on existing Codec 2 FFT
 * 
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

#ifndef DCT2_H
#define	DCT2_H

#include "codec2_fft.h"
#include "comp.h"
#include "comp_prim.h"

typedef codec2_fftr_cfg codec2_dct_cfg;

void dct(codec2_dct_cfg cfg, const int N, float y[], float res[]);
void dct2(codec2_dct_cfg cfg_m, codec2_dct_cfg cfg_n, const int M, const int N, float y[M][N], float res[M][N]);
void idct(codec2_dct_cfg cfg, const int N, float a[N], float res[N]);
void idct2(codec2_dct_cfg cfg_m, codec2_dct_cfg cfg_n, int M, int N, float y[M][N], float res[M][N]);
codec2_dct_cfg dct_config(int P);
codec2_dct_cfg idct_config(int P);
void dct_cfg_free(codec2_dct_cfg cfg);

#endif	/* DCT2_H */

