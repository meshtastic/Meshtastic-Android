/*---------------------------------------------------------------------------*\

  FILE........: cohpsk_mod.c
  AUTHOR......: David Rowe
  DATE CREATED: April 5 2015

  Given an input file of bits (note one bit per float, soft decision format),
  outputs a raw file (8kHz, 16 bit shorts) of COHPSK modem samples
  ready to send over a HF radio channel.

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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <errno.h>

#include "codec2_cohpsk.h"
#include "codec2_fdmdv.h"

int opt_exists(char *argv[], int argc, char opt[]) {
    int i;
    for (i=0; i<argc; i++) {
        if (strcmp(argv[i], opt) == 0) {
            return i;
        }
    }
    return 0;
}

int main(int argc, char *argv[])
{
    FILE          *fin, *fout;
    struct COHPSK *cohpsk;
    char          tx_bits_char[2*COHPSK_BITS_PER_FRAME];
    int           tx_bits[2*COHPSK_BITS_PER_FRAME];
    COMP          tx_fdm[COHPSK_NOM_SAMPLES_PER_FRAME];
    short         tx_fdm_scaled[COHPSK_NOM_SAMPLES_PER_FRAME];
    int           frames, diversity;
    int           i;

    if (argc < 3) {
        fprintf(stderr, "\n");
	fprintf(stderr, "usage: %s InputOneCharPerBitFile OutputModemRawFile [-nd]\n", argv[0]);
        fprintf(stderr, "\n");
        fprintf(stderr, "  --nd        non-diversity mode, input frames of %d bits\n", 2*COHPSK_BITS_PER_FRAME);
        fprintf(stderr, "\n");
	exit(1);
    }

    if (strcmp(argv[1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[1],"rb")) == NULL ) {
	fprintf(stderr, "Error opening input file: %s: %s.\n",
         argv[1], strerror(errno));
	exit(1);
    }

    if (strcmp(argv[2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[2],"wb")) == NULL ) {
	fprintf(stderr, "Error opening output modem sample file: %s: %s.\n",
         argv[2], strerror(errno));
	exit(1);
    }

    cohpsk = cohpsk_create();

    if (opt_exists(argv, argc, "--nd")) {
        diversity = 2;
    }
    else {
        diversity = 1;
    }
    fprintf(stderr, "diversity: %d\n", diversity);

    frames = 0;

    while(fread(tx_bits_char, sizeof(char), COHPSK_BITS_PER_FRAME*diversity, fin) == COHPSK_BITS_PER_FRAME*diversity) {
	      frames++;

        for(i=0; i<COHPSK_BITS_PER_FRAME*diversity; i++)
            tx_bits[i] = tx_bits_char[i];
	      cohpsk_mod(cohpsk, tx_fdm, tx_bits, COHPSK_BITS_PER_FRAME*diversity);
        cohpsk_clip(tx_fdm, COHPSK_CLIP, COHPSK_NOM_SAMPLES_PER_FRAME);

	      /* scale and save to disk as shorts */

	      for(i=0; i<COHPSK_NOM_SAMPLES_PER_FRAME; i++)
	          tx_fdm_scaled[i] = FDMDV_SCALE * tx_fdm[i].real;

 	      fwrite(tx_fdm_scaled, sizeof(short), COHPSK_NOM_SAMPLES_PER_FRAME, fout);

	      /* if this is in a pipeline, we probably don't want the usual
	         buffering to occur */

        if (fout == stdout) fflush(stdout);
    }

    fclose(fin);
    fclose(fout);
    cohpsk_destroy(cohpsk);

    return 0;
}
