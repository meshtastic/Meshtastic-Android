/*---------------------------------------------------------------------------*\

  FILE........: fsk_demod.c
  AUTHOR......: Brady O'Brien
  DATE CREATED: 8 January 2016

  C test driver for fsk_demod in fsk.c. Reads in a stream of 32 bit cpu endian
  floats and writes out the detected bits
   

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
#include "modem_stats.h"
#define MODEMPROBE_ENABLE
#include "modem_probe.h"
#include "codec2_fdmdv.h"

int main(int argc,char *argv[]){
    struct FMFSK *fmfsk;
    int Fs,Rb;
    struct MODEM_STATS stats;
    float loop_time;
    int enable_stats = 0;
    int stats_ctr = 0;
    int stats_loop = 0;
    FILE *fin,*fout;
    uint8_t *bitbuf;
    int16_t *rawbuf;
    float *modbuf;
    int i,j,t;
    
    if(argc<4){
        fprintf(stderr,"usage: %s SampleFreq BitRate InputModemRawFile OutputOneBitPerCharFile [S]\n",argv[0]);
        exit(1);
    }
    
    /* Extract parameters */
    Fs = atoi(argv[1]);
    Rb = atoi(argv[2]);
    
    /* Open files */
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
	
    /* set up FSK */
    fmfsk = fmfsk_create(Fs,Rb);

    if(argc>5){
	if(strcmp(argv[5],"S")==0){
	    enable_stats = 1;
	    loop_time = ((float)fmfsk_nin(fmfsk))/((float)Fs);
	    stats_loop = (int)(.125/loop_time);
	    stats_ctr = 0;
	}
    }
    
    if(fin==NULL || fout==NULL || fmfsk==NULL){
        fprintf(stderr,"Couldn't open test vector files\n");
        exit(1);
    }
    
    /* allocate buffers for processing */
    bitbuf = (uint8_t*)malloc(sizeof(uint8_t)*fmfsk->nbit);
    rawbuf = (int16_t*)malloc(sizeof(int16_t)*(fmfsk->N+fmfsk->Ts*2));
    modbuf = (float*)malloc(sizeof(float)*(fmfsk->N+fmfsk->Ts*2));
    
    /* Demodulate! */
    while( fread(rawbuf,sizeof(int16_t),fmfsk_nin(fmfsk),fin) == fmfsk_nin(fmfsk) ){
	for(i=0;i<fmfsk_nin(fmfsk);i++){
	    modbuf[i] = ((float)rawbuf[i])/FDMDV_SCALE;
	}
	
	modem_probe_samp_f("t_d_sampin",modbuf,fmfsk_nin(fmfsk));
        fmfsk_demod(fmfsk,bitbuf,modbuf);
        
	for(i=0;i<fmfsk->nbit;i++){
	    t = (int)bitbuf[i];
	    modem_probe_samp_i("t_d_bitout",&t,1);
	}
        
	fwrite(bitbuf,sizeof(uint8_t),fmfsk->nbit,fout);
        
	if(enable_stats && stats_ctr <= 0){
	    fmfsk_get_demod_stats(fmfsk,&stats);
	    fprintf(stderr,"{\"EbNodB\": %2.2f,\t\"ppm\": %d,",stats.snr_est,(int)stats.clock_offset);
	    fprintf(stderr,"\t\"f1_est\":%.1f,\t\"f2_est\":%.1f",0.0,0.0);
	    fprintf(stderr,",\t\"eye_diagram\":[");
	    for(i=0;i<stats.neyetr;i++){
		fprintf(stderr,"[");
		for(j=0;j<stats.neyesamp;j++){
		    fprintf(stderr,"%f",stats.rx_eye[i][j]);
		    if(j<stats.neyesamp-1) fprintf(stderr,",");
		}
		fprintf(stderr,"]");
		if(i<stats.neyetr-1) fprintf(stderr,",");
	    }
	    
	    fprintf(stderr,"]}\n");
	    stats_ctr = stats_loop;
	}
	stats_ctr--;
	
        if (fout == stdin) {
	    fflush(fout);
	}
    }
    
    modem_probe_close();

    free(modbuf);
    free(rawbuf);
    free(bitbuf);

    fclose(fin);
    fclose(fout);
    fmfsk_destroy(fmfsk);

    exit(0);
}

