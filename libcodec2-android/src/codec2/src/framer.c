/*---------------------------------------------------------------------------*\

  FILE........: framer.c
  AUTHOR......: David Rowe
  DATE CREATED: July 2020

  Command line framer, inserts a Unique word into a sequence of
  oneBitPerchar bits.

\*---------------------------------------------------------------------------*/


/*
  Copyright (C) 2020 David Rowe

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
#include "fsk.h"

unsigned int toInt(char c)
{
  if (c >= '0' && c <= '9') return      c - '0';
  if (c >= 'A' && c <= 'F') return 10 + c - 'A';
  if (c >= 'a' && c <= 'f') return 10 + c - 'a';
  return -1;
}

int main(int argc,char *argv[]){
    FILE *fin, *fout;
    
    if (argc != 5) {
        fprintf(stderr,"usage: %s InputBitsOnePerByte OutputBitsOnePerByte frameSizeBits HexUW\n",argv[0]);
        exit(1);
    }

    if (strcmp(argv[1],"-") == 0) {
        fin = stdin;
    } else {
        if ((fin = fopen(argv[1],"rb")) == NULL) {
            fprintf(stderr,"Couldn't open input file: %s\n", argv[1]);
            exit(1);
        }
    }
    
    if (strcmp(argv[2],"-") == 0) {
        fout = stdout;
    } else {
        if ((fout = fopen(argv[2],"wb")) == NULL) {
            fprintf(stderr,"Couldn't open output file: %s\n", argv[2]);
            exit(1);
        }
    }

    /* extract UW array */
    
    size_t framesize = atoi(argv[3]);
    char *uw_hex = argv[4];
    uint8_t uw[4*strlen(uw_hex)];
    int uwsize = 0;
    for(int c=0; c<strlen(uw_hex); c++)
        for(int i=0; i<4; i++)
            uw[uwsize++] = (toInt(uw_hex[c]) >> (3-i)) & 0x1; /* MSB first */
    assert(uwsize == 4*strlen(uw_hex));

    fprintf(stderr, "uw_hex: %s uwsize: %d\n", uw_hex, uwsize);
    for(int i=0; i<uwsize; i++)
        fprintf(stderr,"%d ", uw[i]);
    fprintf(stderr,"\n");

    /* main loop */

    uint8_t frame[framesize];
    while(fread(frame,sizeof(uint8_t),framesize,fin) == framesize) {
        fwrite(uw,sizeof(uint8_t),uwsize,fout);
        fwrite(frame,sizeof(uint8_t),framesize,fout);
    }

    fclose(fin);
    fclose(fout);

    return 0;
}
