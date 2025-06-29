/*---------------------------------------------------------------------------*\

  FILE........: codec2_fm.h
  AUTHOR......: David Rowe
  DATE CREATED: February 2015

  Functions that implement analog FM, see also octave/fm.m.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2015 David Rowe

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

#ifndef __CODEC2_FM__
#define __CODEC2_FM__

#include "comp.h"

struct FM {
    float  Fs;               /* setme: sample rate                  */
    float  fm_max;           /* setme: maximum modulation frequency */
    float  fd;               /* setme: maximum deviation            */
    float  fc;               /* setme: carrier frequency            */
    COMP  *rx_bb;
    COMP   rx_bb_filt_prev;
    float *rx_dem_mem;
    float  tx_phase;
    int    nsam;
    COMP   lo_phase;
};

struct FM *fm_create(int nsam);
void fm_destroy(struct FM *fm_states);
void fm_demod(struct FM *fm, float rx_out[], float rx[]);
void fm_mod(struct FM *fm, float tx_in[], float tx_out[]);
void fm_mod_comp(struct FM *fm_states, float tx_in[], COMP tx_out[]);

#endif

