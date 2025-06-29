/*
  Copyright (C) 2018 James C. Ahlstrom

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

#ifndef __FILTER__
#define __FILTER__

#include <complex.h>

struct quisk_cfFilter {        // Structure to hold the static data for FIR filters
    float * dCoefs;            // real filter coefficients
    complex float * cpxCoefs;  // complex filter coefficients
    int nBuf;                  // dimension of cBuf
    int nTaps;                 // dimension of dSamples, cSamples, dCoefs
    int decim_index;           // index of next sample for decimation
    complex float * cSamples;  // storage for old samples
    complex float * ptcSamp;   // next available position in cSamples
    complex float * cBuf;      // auxillary buffer for interpolation
} ;

extern int quisk_cfInterpDecim(complex float *, int, struct quisk_cfFilter *, int, int);
extern void quisk_filt_cfInit(struct quisk_cfFilter *, float *, int);
extern void quisk_filt_destroy(struct quisk_cfFilter *);
extern void quisk_cfTune(struct quisk_cfFilter *, float);
extern void quisk_ccfFilter(complex float *, complex float *, int, struct quisk_cfFilter *);

extern float filtP400S600[100];
extern float filtP550S750[160];
extern float filtP650S900[100];
extern float filtP900S1100[100];
extern float filtP1100S1300[100];

extern float quiskFilt120t480[480];

#endif
