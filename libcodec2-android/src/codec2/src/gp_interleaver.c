/*---------------------------------------------------------------------------*\

  FILE........: gp_interleaver.c
  AUTHOR......: David Rowe
  DATE CREATED: April 2018

  Golden Prime Interleaver. My interpretation of "On the Analysis and
  Design of Good Algebraic Interleavers", Xie et al,eq (5).

  See also octave/gp_interleaver.m

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2018 David Rowe

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
#include "gp_interleaver.h"

/*
  Choose b for Golden Prime Interleaver.  b is chosen to be the
  closest integer, which is relatively prime to N, to the Golden
  section of N.

  Implemented with a LUT in C for convenience, Octave version
  has a more complete implementation.  If you find you need some more
  numbers head back to the Octave choose_interleaver_b() function.
*/

static const int b_table[] = {
     56, 37,    /* 700E:   HRA_56_56                              */
    106, 67,    /* 2020B:  (112,56) partial protection            */
    112, 71,    /* 700D:   HRA_112_112                            */
    128, 83,    /* datac0: H_128_256_5                            */
    210, 131,   /* 2020:   HRAb_396_504 with 312 data bits used   */
    1024, 641,  /* datac3: H_1024_2048_4f                         */
    1290, 797,  /* datac2: H2064_516_sparse                       */
    4096, 2531  /* datac1: H_4096_8192_3d                         */
};

int choose_interleaver_b(int Nbits)
{
    int i;
    for(i=0; i<sizeof(b_table)/sizeof(int); i+=2) {
        if (b_table[i] == Nbits) {
            return b_table[i+1];
        }
    }

    /* if we get to here it means a Nbits we don't have in our table so choke */

    fprintf(stderr, "gp_interleaver: Nbits: %d, b not found!\n", Nbits);
    assert(0);
    return -1;
}


void gp_interleave_comp(COMP interleaved_frame[], COMP frame[], int Nbits) {
  int b = choose_interleaver_b(Nbits);
  int i,j;
  for (i=0; i<Nbits; i++) {
    j = (b*i) % Nbits;
    interleaved_frame[j] = frame[i];
  }
}

void gp_deinterleave_comp(COMP frame[], COMP interleaved_frame[], int Nbits) {
  int b = choose_interleaver_b(Nbits);
  int i,j;
  for (i=0; i<Nbits; i++) {
    j = (b*i) % Nbits;
    frame[i] =  interleaved_frame[j];
  }
}

void gp_interleave_float(float interleaved_frame[], float frame[], int Nbits) {
  int b = choose_interleaver_b(Nbits);
  int i,j;

  for (i=0; i<Nbits; i++) {
    j = (b*i) % Nbits;
    interleaved_frame[j] = frame[i];
  }
}

void gp_deinterleave_float(float frame[], float interleaved_frame[], int Nbits) {
  int b = choose_interleaver_b(Nbits);
  int i,j;

  for (i=0; i<Nbits; i++) {
    j = (b*i) % Nbits;
    frame[i] = interleaved_frame[j];
  }
}

// The above work on complex numbers (e.g. OFDM symbols), so the below work on
// groups of two bits at a time to remain compatible with the above.
void gp_interleave_bits(char interleaved_frame[], char frame[], int Nbits)
{
    char temp[Nbits];
    int b = choose_interleaver_b(Nbits);
    int i,j;

    for (i=0; i<Nbits; i++) {
      j = (b*i) % Nbits;
      temp[j] = ((frame[i*2] & 1) << 1) | (frame[i*2+1] & 1);
    }
    
    for (i=0; i<Nbits; i++) {
        interleaved_frame[i*2] = temp[i] >> 1;
        interleaved_frame[i*2+1] = temp[i] & 1;
    }
}

void gp_deinterleave_bits(char frame[], char interleaved_frame[], int Nbits)
{
    char temp[Nbits];
    int b = choose_interleaver_b(Nbits);
    int i,j;

    for (i=0; i<Nbits; i++) {
        j = (b*i) % Nbits;
        temp[i] = ((interleaved_frame[j*2] & 1) << 1) | (interleaved_frame[j*2+1] & 1);
    }
    
    for (i=0; i<Nbits; i++) {
      frame[i*2] = temp[i] >> 1;
      frame[i*2 + 1] = temp[i] & 1;
    }
}
