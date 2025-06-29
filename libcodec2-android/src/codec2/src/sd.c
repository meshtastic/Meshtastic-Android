/*--------------------------------------------------------------------------*\

	FILE........: sd.c
	AUTHOR......: David Rowe
	DATE CREATED: 20/7/93

	Function to determine spectral distortion between two sets of LPCs.

\*--------------------------------------------------------------------------*/

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

#include <math.h>
#include "sd.h"

/*---------------------------------------------------------------------------*\

	FUNCTION....: spectral_dist()

	AUTHOR......: David Rowe
	DATE CREATED: 20/7/93

	This function returns the spectral distoertion between two
	sets of LPCs.

\*---------------------------------------------------------------------------*/

float spectral_dist(float ak1[], float ak2[], int p, codec2_fft_cfg fft_fwd_cfg, int n)
/*  float ak1[];	unquantised set of p+1 LPCs 			 */
/*  float ak2[];	quantised set of p+1 LPCs 			 */
/*  int p;		LP order					 */
/*  fft_fwd_cfg         FFT constants                                    */
/*  int n;		DFT size to use for SD calculations (power of 2) */
{
    COMP  A1[n];	/* DFT of ak1[] 		*/
    COMP  A2[n];	/* DFT of ak2[]			*/
    float P1,P2;	/* power of current bin		*/
    float sd;
    int i;

    for(i=0; i<n; i++) {
	A1[i].real = 0.0;
	A1[i].imag = 0.0;
	A2[i].real = 0.0;
	A2[i].imag = 0.0;
    }

    for(i=0; i<p+1; i++) {
	A1[i].real = ak1[i];
	A2[i].real = ak2[i];
    }

    codec2_fft_inplace(fft_fwd_cfg, A1);
    codec2_fft_inplace(fft_fwd_cfg, A2);

    sd = 0.0;
    for(i=0; i<n; i++) {
	P1 = A1[i].real*A1[i].real + A1[i].imag*A1[i].imag;
	P2 = A2[i].real*A2[i].real + A2[i].imag*A2[i].imag;
	sd += pow(10.0*log10(P2/P1),2.0);
    }
    sd = sd/n;	/* mean sd for this frame in dB*dB, whch can be further averaged across frames */

    return(sd);
}
