/*---------------------------------------------------------------------------*\

  FILE........: tfsk.c
  AUTHOR......: Brady O'Brien
  DATE CREATED: 20 January 2016

  C test driver for fsk_mod and fsk_demod in fsk.c. Reads a file with input
  bits/rf and spits out modulated/demoduladed samples and a dump of internal
  state. To run unit test, see octave/tfsk.m

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
#include "fsk.c"

#define ST_BITS 10000
#define ST_FS 8000
#define ST_RS 100
#define ST_F1 1200
#define ST_Fs 400
#define ST_EBNO 8
#define ST_M 2

#define TEST_SELF_FULL 1    /* No-arg self test */
#define TEST_MOD 2          /* Test modulator using in and out file */
#define TEST_MOD_H 3          /* Test modulator using in and out file */
#define TEST_DEMOD 4        /* Test demodulator using in and out file */
#define TEST_DEMOD_H 5      /* Test demodulator using in and out file */


int main(int argc,char *argv[]){
    struct FSK *fsk;
    int Fs,Rs,f1,fs,M, lock_nin;
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
        modem_probe_init("fsk2","fsk2_tfsk_log.txt");
        Fs = ST_FS;
        Rs = ST_RS;
        f1 = ST_F1;
        fs = ST_Fs;
        M = ST_M;
        lock_nin = 0;
    } else if (argc<10){
    /* Not running any test */
        printf("Usage: %s [(M|D|DX) Mode TXFreq1 TXFreqSpace SampleRate SymbolRate lock_nin InputFile OutputFile OctaveLogFile]\n",argv[0]);
        exit(1);
    } else {
    /* Running stim-drivin test */
        /* Mod test */
        if(strcmp(argv[1],"MX")==0){
            test_type = TEST_MOD_H;
        } else if(strcmp(argv[1],"M")==0 || strcmp(argv[1],"m")==0) {
            test_type = TEST_MOD;
        /* Demod test */
        } else if(strcmp(argv[1],"DX")==0)  {
            test_type = TEST_DEMOD_H;
        } else if(strcmp(argv[1],"D")==0 || strcmp(argv[1],"d")==0) {
            test_type = TEST_DEMOD;
        } else {
            printf("Must specify mod or demod test with M or D\n");
            exit(1);
        }
        /* Extract parameters */
        M = atoi(argv[2]);
        f1 = atoi(argv[3]);
        fs = atoi(argv[4]);
        Fs = atoi(argv[5]);
        Rs = atoi(argv[6]);
        lock_nin = atoi(argv[7]);
        
        /* Open files */
        fin = fopen(argv[8],"r");
        fout = fopen(argv[9],"w");
        
        if(fin == NULL || fout == NULL){
            printf("Couldn't open test vector files\n");
            exit(1);
        }
        /* Init modem probing */
        modem_probe_init("fsk",argv[10]);
        
    }
    
	srand(1);
    /* set up FSK */
    if(test_type == TEST_DEMOD_H || test_type == TEST_MOD_H){
        fsk = fsk_create_hbr(Fs,Rs,M,10,FSK_DEFAULT_NSYM,f1,fs);
        if(test_type == TEST_DEMOD_H)
            test_type = TEST_DEMOD;
        else
            test_type = TEST_MOD;
    }else{
        fsk = fsk_create(Fs,Rs,M,f1,fs);
    }
    fsk_set_freq_est_limits(fsk, 300, 2800);
    fsk->lock_nin = lock_nin;
    
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
            while( fread(bitbufp,sizeof(uint8_t),fsk->Nbits,fin) == fsk->Nbits){
                i++;
                bitbufp+=fsk->Nbits;
                /* Make sure we don't break the buffer */
                if(i*fsk->Nbits > bitbufsize){
                    bitbuf = realloc(bitbuf,sizeof(uint8_t)*(bitbufsize+fsk->Nbits));
                    bitbufsize += fsk->Nbits;
                }
            }
        }
        /* Allocate modulation buffer */
        modbuf = (float*)malloc(sizeof(float)*(bitbufsize/fsk->Nbits)*fsk->N*4);
        modbufsize = (bitbufsize/fsk->Nbits)*fsk->N;
        /* Do the modulation */
        modbufp = modbuf;
        bitbufp = bitbuf;
        while( bitbufp < bitbuf+bitbufsize){
            fsk_mod(fsk, modbufp, bitbufp, fsk->Nbits);
            modbufp += fsk->N;
            bitbufp += fsk->Nbits;
        }
        /* For a mod-only test, write out the result */
        if(test_type == TEST_MOD){
            fwrite(modbuf,sizeof(float),modbufsize,fout);
            free(modbuf);
        }
        /* Free bit buffer */
        free(bitbuf);
    }
    
    /* Now test the demod */
    if(test_type == TEST_DEMOD || test_type == TEST_SELF_FULL){
        free(modbuf);
        modbuf = malloc(sizeof(float)*(fsk->N+fsk->Ts*2));
        bitbuf = malloc(sizeof(uint8_t)*fsk->Nbits);
        /* Demod-only test */
        if(test_type == TEST_DEMOD){
            while( fread(modbuf,sizeof(float),fsk_nin(fsk),fin) == fsk_nin(fsk) ){
                int n = fsk_nin(fsk);
                COMP modbuf_comp[n];
                for(i=0; i<n; i++) {
                    modbuf_comp[i].real = modbuf[i];
                    modbuf_comp[i].imag = 0.0;
                }
                fsk_demod(fsk,bitbuf,modbuf_comp);
                fwrite(bitbuf,sizeof(uint8_t),fsk->Nbits,fout);
            }
        }
        /* Demod after channel imp. and mod */
        else{
            bitbufp = bitbuf;
            modbufp = modbuf;
            while( modbufp < modbuf + modbufsize){
                int n = fsk_nin(fsk);
                COMP modbuf_comp[n];
                for(i=0; i<n; i++) {
                    modbuf_comp[i].real = modbuf[i];
                    modbuf_comp[i].imag = 0.0;
                }
                fsk_demod(fsk,bitbuf,modbuf_comp);
                modbufp += fsk_nin(fsk);
            }
        }
        free(bitbuf);
    }
    
    modem_probe_close();
    if(test_type == TEST_DEMOD || test_type == TEST_MOD){
        fclose(fin);
        fclose(fout);
    }
    fsk_destroy(fsk);
    exit(0);
}

