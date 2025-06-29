/*---------------------------------------------------------------------------*\

  FILE........: phase.h
  AUTHOR......: David Rowe
  DATE CREATED: 1/2/09

  Functions for modelling phase.

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

#ifndef __PHASE__
#define __PHASE__

#include "codec2_fft.h"
#include "comp.h"

void sample_phase(MODEL *model, COMP filter_phase[], COMP A[]);
void phase_synth_zero_order(int n_samp, MODEL *model, float *ex_phase, COMP filter_phase[]);

void mag_to_phase(float phase[], float Gdbfk[], int Nfft, codec2_fft_cfg fwd_cfg, codec2_fft_cfg inv_cfg);

#endif
