/*---------------------------------------------------------------------------*\

  FILE........: insert_errors.c
  AUTHOR......: David Rowe
  DATE CREATED: 20/2/2013

  Inserts errors into a Codec 2 bit stream using error files.  All files are
  in one bit per char format.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2013 David Rowe

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

#include "codec2.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

int main(int argc, char *argv[])
{
    FILE          *fin;
    FILE          *fout;
    FILE          *ferror;
    unsigned char  abit, error_bit;
    int            errors, bits;

    if (argc < 3) {
	printf("%s InputBitFile OutputBitFile ErrorFile\n", argv[0]);
	exit(1);
    }

    if (strcmp(argv[1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[1],"rb")) == NULL ) {
	fprintf(stderr, "Error opening input bit file: %s: %s.\n",
         argv[1], strerror(errno));
	exit(1);
    }

    if (strcmp(argv[2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[2],"wb")) == NULL ) {
	fprintf(stderr, "Error opening output speech file: %s: %s.\n",
         argv[2], strerror(errno));
	exit(1);
    }

    if ((ferror = fopen(argv[3],"rb")) == NULL ) {
	fprintf(stderr, "Error opening error file: %s: %s.\n",
         argv[3], strerror(errno));
	exit(1);
    }

    bits = errors = 0;

    while(fread(&abit, sizeof(char), 1, fin) == 1) {
        bits++;
        if (fread(&error_bit, sizeof(char), 1, ferror)) {
            abit ^= error_bit;
            errors += error_bit;
        }
        fwrite(&abit, sizeof(char), 1, fout);
        if (fout == stdout) fflush(stdout);
    }

    fclose(fin);
    fclose(fout);
    fclose(ferror);

    fprintf(stderr,"bits: %d errors: %d ber: %4.3f\n", bits, errors, (float)errors/bits);

    return 0;
}
