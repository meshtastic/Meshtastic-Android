/*---------------------------------------------------------------------------*\

  FILE........: tdeframer.c
  AUTHOR......: Brady O'Brien
  DATE CREATED: 8 April 2016

  C unit test for the VHF framer/deframer used by modes 2400A and 2400B.
  The deframer should sync up within one frame at a BER of 10e-3
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

/* BER of test */
#define TESTBER 0.01

/* Frame count */
#define FRCNT 1500

/* Random bits leading frame */
#define LRCNT 44

#include <stdio.h>
#include <math.h>
#include <stdint.h>
#include <freedv_vhf_framing.h>
#include <golay23.h>
#include <string.h>

/* The main loop of the test driver */
int main(int argc,char *argv[]){
    uint8_t * bit_buffer;
    uint8_t c2_buffer[10];
    struct freedv_vhf_deframer * fvd;
    int i,p,k;
    int bitbufferlen;
    int fsize;
    int ftype;
    int first_tol;
    
    if(argc<2){
        fprintf(stderr,"Usage: %s [A|B]\n",argv[0]);
        exit(1);
    }
    
    if(strcmp(argv[1],"A")==0){
        ftype = FREEDV_VHF_FRAME_A;
        first_tol = 2;
    }else if(strcmp(argv[1],"B")==0){
        ftype = FREEDV_HF_FRAME_B;
        first_tol = 5;
    }else{
        fprintf(stderr,"Usage: %s [A|B]\n",argv[0]);
        exit(1);
    }
    
    srand(1);
    golay23_init();
    
    /* Set up the deframer */
    fvd = fvhff_create_deframer(ftype,1);

    fsize = fvhff_get_frame_size(fvd);
    bitbufferlen = (LRCNT+fsize*FRCNT);
    
    /* Allocate bit buffer */
    bit_buffer = (uint8_t *) malloc(sizeof(uint8_t)*bitbufferlen);
    p = 0;
    
    /* Fill out front of buffer */
    for(i=0; i<LRCNT; i++){
        bit_buffer[p++] = rand()&0x1;
    }
    
    /* Place frames */
    for(i=0; i<FRCNT; i++){
        /* Encode frame index into golay codeword to protect from test BER*/
        k = golay23_encode((i+1)&0x0FFF);
        c2_buffer[5] = (k    )&0xFF;
        c2_buffer[1] = (k>>8 )&0xFF;
        c2_buffer[0] = (k>>16)&0x7F;
        /* Frame the bits */
        fvhff_frame_bits(ftype, &bit_buffer[p+(i*fsize)], c2_buffer,NULL,NULL);
    }
    
    /* Flip bits */
    for(i=0; i<bitbufferlen; i++){
        if( (rand()&0xFFFFFF) < (int)(TESTBER*0xFFFFFF)){
            bit_buffer[i] = bit_buffer[i]?0:1;
        }
    }
    
    p=0;
    int first_extract = 0;
    int total_extract = 0;
    int err_count = 0;
    printf("\n");
    /* Deframe some bits */
    for(i=0; i<bitbufferlen; i+=fsize){
        if( fvhff_deframe_bits(fvd, c2_buffer, NULL, NULL, &bit_buffer[i])){
            /* Extract golay23 codeword */
            k  = ((int)c2_buffer[5])    ;
            k |= ((int)c2_buffer[1])<<8 ;
            k |= ((int)c2_buffer[0])<<16;
            k = k & 0x7FFFFF;
            /* Decode frame index */
            p = golay23_decode(k);
            err_count += golay23_count_errors(k,p);
            p = p>>11;

            printf("%d,\t",p);
            total_extract++;
            if(first_extract==0)
                first_extract=p;
        }
    }
    printf("\n");
    float measured_ber = (float)err_count/(float)(23*total_extract);
    
    printf("First extracted frame %d\n",first_extract);
    printf("Extracted %d frames of %d, %f hit rate\n",total_extract,FRCNT,((float)total_extract/(float)FRCNT));
    printf("Bit error rate %f measured from golay code\n",measured_ber);
    printf("Bit error rate %f measured by deframer\n",fvd->ber_est);
    printf("Bit error rate %f measured by deframer\n",(float)fvd->total_uw_err/(float)fvd->total_uw_bits);
    /* Check test condition */
    if(first_extract<first_tol){
        printf("Test passed at test BER of %f!\n",TESTBER);
        exit(0);
    }else{
        printf("** Test failed at test BER of %f!\n",TESTBER);
        exit(1);
    }
    fvhff_destroy_deframer(fvd);
}
