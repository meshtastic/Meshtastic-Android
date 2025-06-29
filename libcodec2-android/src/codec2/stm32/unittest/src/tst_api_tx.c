/*---------------------------------------------------------------------------*\

  FILE........: freedv_tx_profile.c
  AUTHOR......: David Rowe
  DATE CREATED: 13 August 2014

  Profiling freedv_tx() operation on the STM32F4.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2014 David Rowe

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

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include <errno.h>

#include "semihosting.h"
#include "codec2_ofdm.h"
#include "ofdm_internal.h"
#include "interldpc.h"
#include "gp_interleaver.h"

#include "stm32f4xx_conf.h"
#include "stm32f4xx.h"
#include "freedv_api.h"
#include "machdep.h"

int main(int argc, char *argv[]) {
    struct freedv *f;
    FILE          *fin, *fout;
    int            frame, n_samples;

    semihosting_init();

    //PROFILE_VAR(freedv_start);

    //machdep_profile_init();

    f = freedv_open(FREEDV_MODE_1600);
    n_samples = freedv_get_n_speech_samples(f);
    short inbuf[n_samples], outbuf[n_samples];

    freedv_set_test_frames(f, 1);

    // Transmit ---------------------------------------------------------------------

    fin = fopen("stm_in.raw", "rb");
    if (fin == NULL) {
        printf("Error opening input file\n");
        exit(1);
    }

    fout = fopen("mod.raw", "wb");
    if (fout == NULL) {
        printf("Error opening output file\n");
        exit(1);
    }

    frame = 0;

    while (fread(inbuf, sizeof(short), n_samples, fin) == n_samples) {
        //PROFILE_SAMPLE(freedv_start);
        freedv_tx(f, outbuf, inbuf);
        //PROFILE_SAMPLE_AND_LOG2(freedv_start, "  freedv_tx");

        fwrite(outbuf, sizeof(short), n_samples, fout);
        printf("frame: %d\n", ++frame);
        //machdep_profile_print_logged_samples();
   }

    fclose(fin);
    fclose(fout);

    return 0;
}

/* vi:set ts=4 et sts=4: */
