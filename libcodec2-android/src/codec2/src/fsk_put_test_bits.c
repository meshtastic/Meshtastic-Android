/*---------------------------------------------------------------------------*\

  FILE........: fsk_get_test_bits.c
  AUTHOR......: Brady O'Brien
  DATE CREATED: January 2016

  Generates a pseudorandom sequence of bits for testing of fsk_mod and
  fsk_demod.

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
#include <getopt.h>
#include "fsk.h"

#define TEST_FRAME_SIZE 1000  /* must match fsk_get_test_bits.c */

#define VALID_PACKET_BER_THRESH 0.1

int main(int argc,char *argv[]){
    int bitcnt,biterr,i,errs,packetcnt;
    int framesize = TEST_FRAME_SIZE;
    float valid_packet_ber_thresh = VALID_PACKET_BER_THRESH;
    int packet_pass_thresh = 0;
    float ber_pass_thresh = 0;
    FILE *fin;
    uint8_t *bitbuf_tx, *bitbuf_rx, abyte, abit;
    int verbose = 1;
    int packed_in = 0;
    
    char usage[] = "usage: %s [-f frameSizeBits] [-t VaildFrameBERThreshold] [-b BERPass] [-p numPacketsPass] [-k] InputOneBitPerByte\n"
                   "  [-k] packet byte input\n";

    int opt;
    while ((opt = getopt(argc, argv, "f:b:p:hqt:k")) != -1) {
        switch (opt) {
        case 't':
            valid_packet_ber_thresh = atof(optarg);
            break;
          case 'b':
            ber_pass_thresh = atof(optarg);
            break;
        case 'p':
            packet_pass_thresh = atoi(optarg);
            break;
        case 'f':
            framesize = atoi(optarg);
            break;
        case 'q':
            verbose = 0;
            break;
        case 'k':
            packed_in = 1;
            break;
        case 'h':
        default:
            fprintf(stderr, usage, argv[0]);
            exit(1);
        }
    }
    if (argc == 1) {
        fprintf(stderr, usage, argv[0]);
        exit(1);
    }

    int bits_per_byte = 1;
    if (packed_in) {
        if (framesize % 8) {
            fprintf(stderr, "framesize (-f) must be a multiple of 8 for packed byte input (-k)\n");
            exit(1);
        }
        bits_per_byte = 8;
    }

    char *fname = argv[optind++];
    if ((strcmp(fname,"-")==0) || (argc<2)){
        fin = stdin;
    } else {
        fin = fopen(fname,"r");
    }
    
    if(fin==NULL){
        fprintf(stderr,"Couldn't open input file: %s\n", argv[1]);
        exit(1);
    }

    /* allocate buffers for processing */
    bitbuf_tx = (uint8_t*)malloc(sizeof(uint8_t)*framesize);
    bitbuf_rx = (uint8_t*)malloc(sizeof(uint8_t)*framesize);
    
    /* Generate known tx frame from known seed */
    srand(158324);
    for(i=0; i<framesize; i++){
	bitbuf_tx[i] = rand()&0x1;
	bitbuf_rx[i] = 0;
    }
    
    bitcnt = 0; biterr = 0; packetcnt = 0;
    float ber = 0.5;
    
    while(fread(&abyte,sizeof(uint8_t),1,fin)>0){

        for (int b=0; b<bits_per_byte; b++) {
            abit = (abyte >> ((bits_per_byte-1)-b)) & 0x1;
            
            /* update sliding window of input bits */

            for(i=0; i<framesize-1; i++) {
                bitbuf_rx[i] = bitbuf_rx[i+1];
            }
            bitbuf_rx[framesize-1] = abit;

            /* compare to know tx frame.  If they are time aligned, there
               will be a fairly low bit error rate */

            errs = 0;
            for(i=0; i<framesize; i++) {
                if (bitbuf_rx[i] != bitbuf_tx[i]) {
                    errs++;
                }
            }
            if (errs < valid_packet_ber_thresh * framesize) {
                /* OK, we have a valid packet, so lets count errors */
                packetcnt++;
                bitcnt += framesize;
                biterr += errs;
                ber =  (float)biterr/(float)bitcnt;
                if (verbose) {
                    fprintf(stderr,"[%04d] BER %5.3f, bits tested %6d, bit errors %6d errs: %4d \n",
                            packetcnt, ber, bitcnt, biterr, errs);
                }
            }
        }
    }
 
    free(bitbuf_rx);
    free(bitbuf_tx);

    fclose(fin);

    fprintf(stderr,"[%04d] BER %5.3f, bits tested %6d, bit errors %6d\n", packetcnt, ber, bitcnt, biterr);
    if ((packetcnt >= packet_pass_thresh) && (ber <= ber_pass_thresh)) {
        fprintf(stderr,"PASS\n");
        return 0;
    }
    else {
        fprintf(stderr,"FAIL\n");
        return 1;
    }
}
