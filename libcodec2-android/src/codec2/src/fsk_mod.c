/*---------------------------------------------------------------------------*\

  FILE........: fsk_mod.c
  AUTHOR......: Brady O'Brien and David Rowe
  DATE CREATED: 8 January 2016

  Command line FSK modulator.  Reads in bits, writes FSK modulated output.
   
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
#include <string.h>
#include <getopt.h>
#include "fsk.h"
#include "codec2_fdmdv.h"

int main(int argc,char *argv[]){
    struct FSK *fsk;
    int Fs,Rs,f1,fs,M;
    int i;
    int p, user_p = 0;
    FILE *fin,*fout;
    int complex = 0;
    int bytes_per_sample = 2;
    float amp = FDMDV_SCALE;
    int test_mode = 0;
    
    char usage[] = "usage: %s [-p P] [-c] [-a Amplitude] [-t] Mode SampleFreq SymbolFreq TxFreq1 TxFreqSpace InputOneBitPerCharFile OutputModRawFile\n"
                   "  -c            complex signed 16 bit output format\n"
                   "  -a Amplitude  Amplitude of signal\n"
                   "  -t test mode unmodulated carrier, useful for setting levels\n";

    int opt;
    while ((opt = getopt(argc, argv, "a:p:ct")) != -1) {
        switch (opt) {
        case 'a':
            amp = atof(optarg)/2.0;  /* fsk_mod amplitude is +/-2 */
            break;
        case 'c':
            complex = 1; bytes_per_sample = 4;
            break;
        case 't':
            test_mode = 1;
            break;
        case 'p':
            p = atoi(optarg);
            user_p = 1;
            break;
        default:
            fprintf(stderr, usage, argv[0]);
            exit(1);
        }
    }

    if (argc<8){
        fprintf(stderr, usage, argv[0]);
        exit(1);
    }
    
    /* Extract parameters */
    M = atoi(argv[optind++]);
    Fs = atoi(argv[optind++]);
    Rs = atoi(argv[optind++]);
    f1 = atoi(argv[optind++]);
    fs = atoi(argv[optind++]);
    
    if(strcmp(argv[optind],"-")==0){
        fin = stdin;
    }else{
        fin = fopen(argv[optind],"r");
    }
    optind++;
    
    if(strcmp(argv[optind],"-")==0){
        fout = stdout;
    }else{
        fout = fopen(argv[optind],"w");
    }

    /* p is not actually used for the modulator, but we need to set it for fsk_create() to be happy */    
    if (!user_p)
        p = Fs/Rs;
    
    /* set up FSK */
    fsk = fsk_create_hbr(Fs,Rs,M,p,FSK_DEFAULT_NSYM,f1,fs);
    
    if(fin==NULL || fout==NULL || fsk==NULL){
        fprintf(stderr,"Couldn't open files\n");
        exit(1);
    }
        
    /* Mote we use the same buffer sizes as demod (fsk->Nbits, fsk->N)
       for convenience, but other sizes are possible for the
       FSK modulator. */
    uint8_t bitbuf[fsk->Nbits];
    
    while( fread(bitbuf,sizeof(uint8_t),fsk->Nbits,fin) == fsk->Nbits ){
        if (test_mode) memset(bitbuf,0,fsk->Nbits);
        if (complex == 0) {
            float modbuf[fsk->N];
            int16_t rawbuf[fsk->N];
            /* 16 bit signed short real output */
            fsk_mod(fsk,modbuf,bitbuf,fsk->Nbits);
            for(i=0; i<fsk->N; i++)
                rawbuf[i] = (int16_t)(modbuf[i]*amp);
            fwrite(rawbuf,bytes_per_sample,fsk->N,fout);
       } else {
            /* 16 bit signed char complex output */
            COMP modbuf[fsk->N];
            int16_t rawbuf[2*fsk->N];
            fsk_mod_c(fsk,(COMP*)modbuf,bitbuf,fsk->Nbits);
            for(i=0; i<fsk->N; i++) {
                rawbuf[2*i] = (int16_t)(modbuf[i].real*amp);
                rawbuf[2*i+1] = (int16_t)(modbuf[i].imag*amp);
            }            
            fwrite(rawbuf,bytes_per_sample,fsk->N,fout);
        }
               
        if(fout == stdin){
            fflush(fout);
        }
    }
    
    fsk_destroy(fsk);
}
