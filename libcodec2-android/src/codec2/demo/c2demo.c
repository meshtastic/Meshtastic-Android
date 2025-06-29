/*---------------------------------------------------------------------------*\

  FILE........: c2demo.c
  AUTHOR......: David Rowe
  DATE CREATED: 15/11/2010

  Encodes and decodes a file of raw speech samples using Codec 2.
  Demonstrates use of Codec 2 function API.
  
  cd codec2/build_linux
  ./demo/c2demo ../raw/hts1a.raw his1a_out.raw
  aplay -f S16_LE hts1a_out.raw
  
\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2010 David Rowe

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
#include "codec2.h"

int main(int argc, char *argv[])
{
    struct CODEC2 *codec2;
    FILE          *fin;
    FILE          *fout;

    if (argc != 3) {
        printf("usage: %s InputRawSpeechFile OutputRawSpeechFile\n", argv[0]);
        exit(1);
    }

    if ( (fin = fopen(argv[1],"rb")) == NULL ) {
        fprintf(stderr, "Error opening input speech file: %s\n", argv[1]);
        exit(1);
    }

    if ( (fout = fopen(argv[2],"wb")) == NULL ) {
        fprintf(stderr, "Error opening output speech file: %s\n", argv[2]);
         exit(1);
    }

    /* Note only one set of Codec 2 states is required for an encoder
       and decoder pair. */
    codec2 = codec2_create(CODEC2_MODE_1300);
    size_t nsam = codec2_samples_per_frame(codec2);
    short speech_samples[nsam];
    /* Bits from the encoder are packed into bytes */
    unsigned char compressed_bytes[codec2_bytes_per_frame(codec2)];

    while(fread(speech_samples, sizeof(short), nsam, fin) == nsam) {
        codec2_encode(codec2, compressed_bytes, speech_samples);
        codec2_decode(codec2, speech_samples, compressed_bytes);
        fwrite(speech_samples, sizeof(short), nsam, fout);
    }

    codec2_destroy(codec2);
    fclose(fin);
    fclose(fout);

    return 0;
}
