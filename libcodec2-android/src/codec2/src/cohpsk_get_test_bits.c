/*---------------------------------------------------------------------------*\

  FILE........: cohpsk_put_test_bits.c
  AUTHOR......: David Rowe
  DATE CREATED: April 2015

  Generates a file of test bits, useful for input to cohpsk_mod.

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
#include "test_bits_coh.h"

int main(int argc, char *argv[])
{
    FILE         *fout;
    int           tx_bits[COHPSK_BITS_PER_FRAME];
    char          tx_bits_char[COHPSK_BITS_PER_FRAME];
    int           numBits, nFrames, n;
    int           *ptest_bits_coh, *ptest_bits_coh_end, i;

    if (argc < 2) {
	printf("usage: %s OutputOneCharPerBitFile numBits\n", argv[0]);
	exit(1);
    }

    if (strcmp(argv[1], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[1],"wb")) == NULL ) {
	fprintf(stderr, "Error opening output file: %s: %s.\n",
         argv[1], strerror(errno));
	exit(1);
    }

    ptest_bits_coh = (int*)test_bits_coh;
    ptest_bits_coh_end = (int*)test_bits_coh + sizeof(test_bits_coh)/sizeof(int);
    numBits = atoi(argv[2]);
    nFrames = numBits/COHPSK_BITS_PER_FRAME;

    for(n=0; n<nFrames; n++) {

        memcpy(tx_bits, ptest_bits_coh, sizeof(int)*COHPSK_BITS_PER_FRAME);
        ptest_bits_coh += COHPSK_BITS_PER_FRAME;
        if (ptest_bits_coh >= ptest_bits_coh_end) {
            ptest_bits_coh = (int*)test_bits_coh;
        }

        for(i=0; i<COHPSK_BITS_PER_FRAME; i++)
            tx_bits_char[i] = tx_bits[i];

	fwrite(tx_bits_char, sizeof(char), COHPSK_BITS_PER_FRAME, fout);

	/* if this is in a pipeline, we probably don't want the usual
	   buffering to occur */

        if (fout == stdout) fflush(stdout);
    }

    fclose(fout);

    return 0;
}

