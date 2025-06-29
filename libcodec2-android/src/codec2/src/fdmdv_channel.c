/*---------------------------------------------------------------------------*\

  FILE........: fdmdv_channel.c
  AUTHOR......: David Rowe
  DATE CREATED: 2 August 2014

  Given an input raw file (8kHz, 16 bit shorts) of FDMDV modem
  samples, adds channel impairments and outputs to another raw file.

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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <errno.h>

#include "codec2_fdmdv.h"
#include "fdmdv_internal.h"

int main(int argc, char *argv[])
{
    FILE         *fin, *fout;
    short         rx_fdm_buf[FDMDV_NOM_SAMPLES_PER_FRAME];
    COMP          rx_fdm[FDMDV_NOM_SAMPLES_PER_FRAME];
    struct FDMDV *fdmdv;
    float         snrdB, sam;
    int           i;

    if (argc < 3) {
	printf("usage: %s InputModemRawFile OutputModemRawFile SNRdB\n", argv[0]);
	printf("e.g    %s test_in.raw test_out.raw 4\n", argv[0]);
	exit(1);
    }

    if (strcmp(argv[1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[1],"rb")) == NULL ) {
	fprintf(stderr, "Error opening input modem sample file: %s: %s.\n",
         argv[1], strerror(errno));
	exit(1);
    }

    if (strcmp(argv[2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[2],"wb")) == NULL ) {
	fprintf(stderr, "Error opening output modem sample file: %s: %s.\n",
         argv[2], strerror(errno));
	exit(1);
    }

    snrdB = atof(argv[3]);
    fdmdv = fdmdv_create(FDMDV_NC);

    while(fread(rx_fdm_buf, sizeof(short), FDMDV_NOM_SAMPLES_PER_FRAME, fin) == FDMDV_NOM_SAMPLES_PER_FRAME) {

	for(i=0; i<FDMDV_NOM_SAMPLES_PER_FRAME; i++) {
	    rx_fdm[i].real = (float)rx_fdm_buf[i]/FDMDV_SCALE;
            rx_fdm[i].imag = 0.0;
        }

        /* real signal so we adjust SNR to suit.  I think.  I always get confused by this! */

        fdmdv_simulate_channel(&fdmdv->sig_pwr_av, rx_fdm, FDMDV_NOM_SAMPLES_PER_FRAME, snrdB - 3.0);

	for(i=0; i<FDMDV_NOM_SAMPLES_PER_FRAME; i++) {
	    sam = FDMDV_SCALE*rx_fdm[i].real;
            if (sam >  32767.0) sam =  32767.0;
            if (sam < -32767.0) sam = -32767.0;
            rx_fdm_buf[i] = sam;
        }

        fwrite(rx_fdm_buf, sizeof(short), FDMDV_NOM_SAMPLES_PER_FRAME, fout);

	/* if this is in a pipeline, we probably don't want the usual
	   buffering to occur */

        if (fout == stdout) fflush(stdout);
    }

    fclose(fin);
    fclose(fout);
    fdmdv_destroy(fdmdv);

    return 0;
}

