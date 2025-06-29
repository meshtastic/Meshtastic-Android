
/*---------------------------------------------------------------------------*\

  FILE........: vhf_deframe_c2.c
  AUTHOR......: Brady O'Brien
  DATE CREATED: 8 March 2016

  C tool to extract codec2 data from freedv VHF 2400A/B/whatever frames
   

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
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "freedv_vhf_framing.h"

int main(int argc,char *argv[]){
    struct freedv_vhf_deframer * deframer;
    FILE *fin,*fout;
    uint8_t *bitbuf;
    uint8_t *c2buf;
    int frame_fmt = 0;
    int fsize,c2size;
    
    if(argc<3){
        fprintf(stderr,"usage: %s (A|B) InpuC2File OutputOneBitPerCharFile\n",argv[0]);
        exit(1);
    }
    
    if(strcmp(argv[1],"A")==0){
        frame_fmt = FREEDV_VHF_FRAME_A;
    } else if (strcmp(argv[1],"B")==0) {
        frame_fmt = FREEDV_HF_FRAME_B;
    } else {
        fprintf(stderr,"usage: %s (A|B) InpuC2File OutputOneBitPerCharFile\n",argv[0]);
        exit(1);
    }
    
    /* Open files */
    if(strcmp(argv[2],"-")==0){
        fin = stdin;
    }else{
        fin = fopen(argv[2],"r");
    }
	
    if(strcmp(argv[3],"-")==0){
        fout = stdout;
    }else{
        fout = fopen(argv[3],"w");
    }

    /* Set up deframer */
    deframer = fvhff_create_deframer(frame_fmt,0);
    
    if(fin==NULL || fout==NULL || deframer==NULL){
        fprintf(stderr,"Couldn't open test vector files\n");
        goto cleanup;
    }
    
    c2size = fvhff_get_codec2_size(deframer);
    fsize = fvhff_get_frame_size(deframer);
    
    /* allocate buffers for processing */
    bitbuf = (uint8_t*)malloc(sizeof(uint8_t)*fsize);
    c2buf = (uint8_t*)malloc(sizeof(uint8_t)*c2size);
    
    /* Deframe! */
    while( fread(c2buf,sizeof(uint8_t),c2size,fin) == c2size ){
        fvhff_frame_bits(frame_fmt,bitbuf,c2buf,NULL,NULL);
        fwrite(bitbuf,sizeof(uint8_t),fsize,fout);
        
        if(fout == stdin){
            fflush(fout);
        }
    }
    
    free(bitbuf);
    free(c2buf);
    
    cleanup:
    fclose(fin);
    fclose(fout);
    fvhff_destroy_deframer(deframer);
    exit(0);
}

