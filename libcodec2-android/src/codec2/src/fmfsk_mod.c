/*---------------------------------------------------------------------------*\

  FILE........: fmfsk_mod.c
  AUTHOR......: Brady O'Brien
  DATE CREATED: 7 February 2016

  C test driver for fmfsk_mod in fmfsk.c. Reads in a set of bits to modulate
   from a file, passed as a parameter, and writes modulated output to
   another file
   

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
#include "fmfsk.h"
#include "codec2_fdmdv.h"

int main(int argc,char *argv[]){
    struct FMFSK *fmfsk;
    int Fs,Rb;
    int i;
    FILE *fin,*fout;
    uint8_t *bitbuf;
    int16_t *rawbuf;
    float *modbuf;
    
    if(argc<4){
        fprintf(stderr,"usage: %s SampleFreq BitRate InputOneBitPerCharFile OutputModRawFile\n",argv[0]);
        exit(1);
    }
    
    /* Extract parameters */
    Fs = atoi(argv[1]);
    Rb = atoi(argv[2]);
    
    if(strcmp(argv[3],"-")==0){
		fin = stdin;
	}else{
		fin = fopen(argv[3],"r");
	}
	
	if(strcmp(argv[4],"-")==0){
		fout = stdout;
	}else{
		fout = fopen(argv[4],"w");
	}
    
    
    /* set up FMFSK */
    fmfsk = fmfsk_create(Fs,Rb);
    
    if(fin==NULL || fout==NULL || fmfsk==NULL){
        fprintf(stderr,"Couldn't open test vector files\n");
        exit(1);
    }
    
    /* allocate buffers for processing */
    bitbuf = (uint8_t*)malloc(sizeof(uint8_t)*fmfsk->nbit);
    rawbuf = (int16_t*)malloc(sizeof(int16_t)*fmfsk->N);
    modbuf = (float*)malloc(sizeof(float)*fmfsk->N);
    
    /* Modulate! */
    while( fread(bitbuf,sizeof(uint8_t),fmfsk->nbit,fin) == fmfsk->nbit ){
        fmfsk_mod(fmfsk,modbuf,bitbuf);
        for(i=0; i<fmfsk->N; i++){
	    rawbuf[i] = (int16_t)(modbuf[i]*(float)FDMDV_SCALE);
	}
        fwrite(rawbuf,sizeof(int16_t),fmfsk->N,fout);
        
	if(fout == stdin){
	    fflush(fout);
	}
    }
    
    free(modbuf);
    free(rawbuf);
    free(bitbuf);

    fmfsk_destroy(fmfsk);

    fclose(fin);
    fclose(fout);

    exit(0);
}
