/*---------------------------------------------------------------------------*\

  FILE........: tfmfsk.c
  AUTHOR......: Brady O'Brien
  DATE CREATED: 8 February 2016

  C test driver for fmfsk_mod and fmfsk_demod in fmfsk.c. Reads a file with input
  bits/rf and spits out modulated/demoduladed samples and a dump of internal
  state. To run unit test, see octave/tfmfsk.m

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


//#define MODEMPROBE_ENABLE

#include "modem_probe.h"
#include <stdio.h>

/* Note: This is a dirty hack to force fsk.c to compile with modem probing enabled */
#include "fmfsk.c"

#define ST_BITS 10000
#define ST_FS 48000
#define ST_RS 2400
#define ST_EBNO 8

#define TEST_SELF_FULL 1    /* No-arg self test */
#define TEST_MOD 2          /* Test modulator using in and out file */
#define TEST_DEMOD 3        /* Test demodulator using in and out file */


int main(int argc,char *argv[]){
    struct FMFSK *fmfsk;
    int Fs,Rs;
    FILE *fin,*fout;

    uint8_t *bitbuf = NULL;
    float *modbuf = NULL;
    uint8_t *bitbufp;
    float *modbufp;

    size_t bitbufsize = 0;
    size_t modbufsize = 0;

    int test_type;
    
    int i;
    
    fin = NULL;
    fout = NULL;
    
    /* Set up full self-test */
    if(argc == 1){
        test_type = TEST_SELF_FULL;
        modem_probe_init("fmfsk","fmfsk_tfmfsk_log.txt");
        Fs = ST_FS;
        Rs = ST_RS;
    } else if (argc<7){
    /* Not running any test */
        printf("Usage: %s [(M|D) SampleRate BitRate InputFile OutputFile OctaveLogFile]\n",argv[0]);
        exit(1);
    } else {
    /* Running stim-drivin test */
        /* Mod test */
        if(strcmp(argv[1],"M")==0 || strcmp(argv[1],"m")==0) {
            test_type = TEST_MOD;
        /* Demod test */
        } else if(strcmp(argv[1],"D")==0 || strcmp(argv[1],"d")==0) {
            test_type = TEST_DEMOD;
        } else {
            printf("Must specify mod or demod test with M or D\n");
            exit(1);
        }
        /* Extract parameters */
        Fs = atoi(argv[2]);
        Rs = atoi(argv[3]);
        
        /* Open files */
        fin = fopen(argv[4],"r");
        fout = fopen(argv[5],"w");
        
        if(fin == NULL || fout == NULL){
            printf("Couldn't open test vector files\n");
            exit(1);
        }
        /* Init modem probing */
        modem_probe_init("fmfsk",argv[6]);
        
    }
    
	srand(1);
    
    /* set up FSK */
    fmfsk = fmfsk_create(Fs,Rs);
    /* Modulate! */
    if(test_type == TEST_MOD || test_type == TEST_SELF_FULL){
        /* Generate random bits for self test */
        if(test_type == TEST_SELF_FULL){
            bitbufsize = ST_BITS;
            bitbuf = (uint8_t*) malloc(sizeof(uint8_t)*ST_BITS);
            for(i=0; i<ST_BITS; i++){
                /* Generate a randomish bit */
                bitbuf[i] = (uint8_t)(rand()&0x01);
            }
        } else { /* Load bits from a file */
            /* Figure out how many bits are in the input file */
            fseek(fin, 0L, SEEK_END);
            bitbufsize = ftell(fin);
            fseek(fin, 0L, SEEK_SET);
            bitbuf = malloc(sizeof(uint8_t)*bitbufsize);
            i = 0;
            /* Read in some bits */
            bitbufp = bitbuf;
            while( fread(bitbufp,sizeof(uint8_t),fmfsk->nbit,fin) == fmfsk->nbit){
                i++;
                bitbufp+=fmfsk->nbit;
                /* Make sure we don't break the buffer */
                if(i*fmfsk->nbit > bitbufsize){
                    bitbuf = realloc(bitbuf,sizeof(uint8_t)*(bitbufsize+fmfsk->nbit));
                    bitbufsize += fmfsk->nbit;
                }
            }
        }
        /* Allocate modulation buffer */
        modbuf = (float*)malloc(sizeof(float)*(bitbufsize/fmfsk->nbit)*fmfsk->N*4);
        modbufsize = (bitbufsize/fmfsk->nbit)*fmfsk->N;
        /* Do the modulation */
        modbufp = modbuf;
        bitbufp = bitbuf;
        while( bitbufp < bitbuf+bitbufsize){
            fmfsk_mod(fmfsk, modbufp, bitbufp);
            modbufp += fmfsk->N;
            bitbufp += fmfsk->nbit;
        }
        /* For a mod-only test, write out the result */
        if(test_type == TEST_MOD){
            fwrite(modbuf,sizeof(float),modbufsize,fout);
            free(modbuf);
        }
        /* Free bit buffer */
        free(bitbuf);
    }
    
    /* Add channel imp here */
    
    
    /* Now test the demod */
    if(test_type == TEST_DEMOD || test_type == TEST_SELF_FULL){
        free(modbuf);
        modbuf = malloc(sizeof(float)*(fmfsk->N+fmfsk->Ts*2));
        bitbuf = malloc(sizeof(uint8_t)*fmfsk->nbit);
        /* Demod-only test */
        if(test_type == TEST_DEMOD){
            
            //fprintf(stderr,"%d\n",(fmfsk->N+fmfsk->Ts*2));
            while( fread(modbuf,sizeof(float),fmfsk_nin(fmfsk),fin) == fmfsk_nin(fmfsk) ){
                fmfsk_demod(fmfsk,bitbuf,modbuf);
                fwrite(bitbuf,sizeof(uint8_t),fmfsk->nbit,fout);
            }
        }
        /* Demod after channel imp. and mod */
        else{
            bitbufp = bitbuf;
            modbufp = modbuf;
            while( modbufp < modbuf + modbufsize){
                fmfsk_demod(fmfsk,bitbuf,modbuf);
                modbufp += fmfsk_nin(fmfsk);
            }
        }
        free(bitbuf);
    }
    
    modem_probe_close();
    if(test_type == TEST_DEMOD || test_type == TEST_MOD){
        fclose(fin);
        fclose(fout);
    }
    fmfsk_destroy(fmfsk);
    exit(0);
}

