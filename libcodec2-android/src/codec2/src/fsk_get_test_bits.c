/*---------------------------------------------------------------------------*\

  FILE........: fsk_get_test_bits.c
  AUTHOR......: Brady O'Brien
  DATE CREATED: January 2016

  Generates a pseudorandom sequence of bits for testing of fsk_mod and fsk_demod

\*---------------------------------------------------------------------------*/


/*
  Copyright (C) 2016 David Rowe

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
#include <string.h>
#include "fsk.h"

#define TEST_FRAME_SIZE 1000  /* arbitrary choice, repeats after this
                                many bits, sets frame size for rx
                                processing */

int main(int argc,char *argv[]){
    int bitcnt, framecnt;
    int framesize = TEST_FRAME_SIZE;
    int i;
    FILE *fout;
    uint8_t *bitbuf;
    
    if(argc < 3){
        fprintf(stderr,"usage: %s OutputBitsOnePerByte numBits [framesize]\n",argv[0]);
        exit(1);
    }

    if (argc == 4){
        framesize = atoi(argv[3]);
        fprintf(stderr, "Using custom frame size of %d bits\n", framesize);
    }
    
    /* Extract parameters */
    bitcnt = atoi(argv[2]);
    framecnt = bitcnt/framesize;
    if (framecnt == 0) {
        fprintf(stderr,"Need a minimum of %d bits\n", framesize);
        exit(1);
    }

    if(strcmp(argv[1],"-")==0){
        fout = stdout;
    }else{
        fout = fopen(argv[1],"w");
    }
    
    if(fout==NULL){
        fprintf(stderr,"Couldn't open output file: %s\n", argv[1]);
        exit(1);
    }
    
    /* allocate buffers for processing */
    bitbuf = (uint8_t*)malloc(sizeof(uint8_t)*framesize);
    
    /* Generate buffer of test frame bits from known seed */
    srand(158324);
    for(i=0; i<framesize; i++){
	bitbuf[i] = rand()&0x1;
    }
        
    /* Output test frames */
    srand(158324);
    for(i=0; i<framecnt; i++){
	fwrite(bitbuf,sizeof(uint8_t),framesize,fout);
	if(fout == stdout){
	    fflush(fout);
	}
    }
    
    free(bitbuf);
    fclose(fout);

    return 0;
}
