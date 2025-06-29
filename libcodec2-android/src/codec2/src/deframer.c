/*---------------------------------------------------------------------------*\

  FILE........: deframer.c
  AUTHOR......: David Rowe
  DATE CREATED: July 2020

  Command line deframer, obtains UW sync, then extracts frame of data bits.

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
    
    if (argc < 5) {
        fprintf(stderr,"usage: %s InOneFloatPerLLR OutOneFloatPerLLR frameSizeBits HexUW [--hard]\n",argv[0]);
        fprintf(stderr,"    --hard  Treat input and output files as OneBitPerByte hard decisions\n");
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

    /* extract UW array from hex on command line */
    
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

    /* set up for LLRs or hard decision inputs */
    
    size_t framedsize = framesize+uwsize;
    int oneBitPerByte = 0;
    int nelement = sizeof(float);
    if (argc == 6) {
        oneBitPerByte = 1;
        nelement = sizeof(uint8_t);
    }
    uint8_t *inbuf = malloc(2*nelement*framedsize);  assert(inbuf != NULL);
    memset(inbuf, 0, 2*nelement*framedsize);
    
    /* main loop */

    uint8_t twoframes[2*framedsize]; memset(twoframes, 0, 2*framedsize);
    int state = 0; int thresh1 = 0.1*uwsize; int thresh2 = 0.4*uwsize; int baduw = 0;
    fprintf(stderr, "thresh1: %d thresh2: %d\n", thresh1, thresh2);
    int best_location, errors;
    while(fread(&inbuf[nelement*framedsize], nelement, framedsize, fin) == framedsize) {

        /* We need to maintain a two frame buffer of hard decision data for UW sync */
        
        if (oneBitPerByte) {
            memcpy(&twoframes[framedsize], inbuf, framedsize);
        } else {
            /* convert bit LLRs to hard decisions */
            for(int i=0; i<framedsize; i++) {
                float *pllr = (float*)&inbuf[nelement*(framedsize+i)];
                if (*pllr < 0)
                    twoframes[framedsize+i] = 1;
                else
                    twoframes[framedsize+i] = 0;
                //fprintf(stderr, "%d %f %d\n", i, *pllr, twoframes[framedsize+i]);
            }
        }
        
        int next_state = state;
        switch(state) {
        case 0:
            /* out of sync - look for UW */
            best_location = 0;
            int best_errors = uwsize;
            for(int i=0; i<framesize; i++) {
                errors = 0;
                for(int u=0; u<uwsize; u++)
                    errors += twoframes[i+u] ^ uw[u];
                //fprintf(stderr, "%d %d %d\n", i, errors, best_errors);
                if (errors < best_errors) { best_errors = errors; best_location = i; }
            }
            if (best_errors <= thresh1) {
                fprintf(stderr, "found UW!\n"); next_state = 1; baduw = 0;
            }
            break;
        case 1:
            /* in sync - check UW still OK */
            errors = 0;
            for(int u=0; u<uwsize; u++)
                errors += twoframes[best_location+u] ^ uw[u];
            if (errors >= thresh2) {
                baduw++;
                if (baduw == 3) {
                    fprintf(stderr, "lost UW!\n"); next_state = 0;
                }
            }
            else baduw = 0;
            break;
        }
        state = next_state;
        
        if (state == 1) {
            fwrite(&inbuf[(best_location+uwsize)*nelement], nelement, framesize, fout);
        }
        memmove(twoframes, &twoframes[framedsize], framedsize);
        memmove(inbuf, &inbuf[nelement*framedsize], nelement*framedsize);
    }

    free(inbuf);
    fclose(fin);
    fclose(fout);

    return 0;
}
