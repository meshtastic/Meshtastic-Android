/*---------------------------------------------------------------------------*\

  FILE........: cohpsk_demod.c
  AUTHOR......: David Rowe
  DATE CREATED: April 6 2015

  Given an input file of raw file (8kHz, 16 bit shorts) of COHPSK modem samples,
  outputs a file of bits (note one bit per int, not compressed).

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2015 David Rowe

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
#include <math.h>
#include <errno.h>
#include <getopt.h>

#include "codec2_cohpsk.h"
#include "cohpsk_defs.h"
#include "cohpsk_internal.h"
#include "codec2_fdmdv.h"
#include "octave.h"

#include "debug_alloc.h"

#define LOG_FRAMES 100
#define SYNC_FRAMES 12                    /* sync state uses up extra log storage as we reprocess several times */

int opt_exists(char *argv[], int argc, char opt[]) {
    int i;
    for (i=0; i<argc; i++) {
        if (strcmp(argv[i], opt) == 0) {
            return i;
        }
    }
    return 0;
}

int main(int argc, char *argv[])
{
    FILE          *fin, *fout, *foct;
    struct COHPSK *cohpsk;
    float         rx_bits[COHPSK_BITS_PER_FRAME];
    char          rx_bits_char[COHPSK_BITS_PER_FRAME];
    double        rx_bits_double[COHPSK_BITS_PER_FRAME];
    COMP          rx_fdm[COHPSK_MAX_SAMPLES_PER_FRAME];
    short         rx_fdm_scaled[COHPSK_MAX_SAMPLES_PER_FRAME];
    int           frames, sync, nin_frame;
    float        *rx_amp_log = NULL;
    float        *rx_phi_log = NULL;
    COMP         *rx_symb_log = NULL;
    float         f_est_log[LOG_FRAMES], ratio_log[LOG_FRAMES];
    int           i, r, c, log_data_r, oct, logframes, diversity, sd;

    foct = NULL;
    oct = 0;
    diversity = 1;
    sd = 0;
    int verbose = 0;
    
    int o = 0;
    int opt_idx = 0;
    while ( o != -1 ) {
        static struct option long_opts[] = {
            {"help",      no_argument,        0, 'h'},
            {"octave",    required_argument,  0, 'o'},
            {"nd",        no_argument,        0, 'n'},
            {"sd",        no_argument,        0, 's'},
            {"verbose",   no_argument,        0, 'v'},
            {0, 0, 0, 0}
        };
        
        o = getopt_long(argc,argv,"ho:nsv",long_opts,&opt_idx);
        
        switch(o) {
            case 'o':
                if ( (foct = fopen(optarg,"wt")) == NULL ) {
                    fprintf(stderr, "Error opening output Octave file: %s: %s.\n",
                            optarg, strerror(errno));
                    exit(1);
                }
                fprintf(stderr, "opened: %s\n", optarg);
                oct = 1;
                break;
            case 'n':
                diversity = 2;
                break;
            case 's':
                sd = 1;
                break;
            case 'v':
                verbose = 1;
            break;    
            case 'h':
            case '?':
                goto helpmsg;
                break;
        }
    }
    
    int dx = optind;
    
    if( (argc - dx) < 2) {
        fprintf(stderr, "Too few arguments\n");
    helpmsg:
	printf("usage: %s [options] InputModemRawFile OutputFile \n", argv[0]);
        fprintf(stderr, "\n");
        fprintf(stderr, "                    Default output file format is one byte per bit\n");
        fprintf(stderr, "  -o OctaveLogFile  Octave log file for testing\n");
        fprintf(stderr, "  --nd              non-diversity mode, output frames of %d bits\n", COHPSK_ND*COHPSK_BITS_PER_FRAME);
        fprintf(stderr, "  --sd              soft decision output, one double per symbol\n");
        fprintf(stderr, "  -v                verbose mode\n");
        fprintf(stderr, "\n");
        exit(1);
    }

   if (strcmp(argv[dx], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[dx],"rb")) == NULL ) {
	fprintf(stderr, "Error opening input modem sample file: %s: %s.\n",
         argv[1], strerror(errno));
	exit(1);
    }

    if (strcmp(argv[dx+1], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[dx+1],"wb")) == NULL ) {
	fprintf(stderr, "Error opening output file: %s: %s.\n",
         argv[2], strerror(errno));
	exit(1);
    }

    cohpsk = cohpsk_create();
    cohpsk_set_verbose(cohpsk, verbose);

    if (oct) {
        logframes = LOG_FRAMES;
        rx_amp_log = (float *)MALLOC(sizeof(float)*logframes*NSYMROW*COHPSK_NC*COHPSK_ND);
        assert(rx_amp_log != NULL);
        rx_phi_log = (float *)MALLOC(sizeof(float)*logframes*NSYMROW*COHPSK_NC*COHPSK_ND);
        assert(rx_phi_log != NULL);
        rx_symb_log = (COMP *)MALLOC(sizeof(COMP)*logframes*NSYMROW*COHPSK_NC*COHPSK_ND);
        assert(rx_symb_log != NULL);
        cohpsk->rx_timing_log = (float*)MALLOC(sizeof(float)*SYNC_FRAMES*logframes*NSYMROWPILOT);
        assert(cohpsk->rx_timing_log != NULL);
    }

    log_data_r = 0;
    frames = 0;

    nin_frame = COHPSK_NOM_SAMPLES_PER_FRAME;
    while(fread(rx_fdm_scaled, sizeof(short), nin_frame, fin) == nin_frame) {
	frames++;
        cohpsk_set_frame(cohpsk, frames);

	/* scale and demod */

	for(i=0; i<nin_frame; i++) {
	    rx_fdm[i].real = rx_fdm_scaled[i]/FDMDV_SCALE;
            rx_fdm[i].imag = 0.0;
        }

	cohpsk_demod(cohpsk, rx_bits, &sync, rx_fdm, &nin_frame);

 	if (sync) {
            if (diversity == 1) {
                if (sd == 0) {
                    for(i=0; i<COHPSK_BITS_PER_FRAME; i++)
                        rx_bits_char[i] = rx_bits[i] < 0.0;
                    fwrite(rx_bits_char, sizeof(char), COHPSK_BITS_PER_FRAME, fout);
                }
                else {
                    for(i=0; i<COHPSK_BITS_PER_FRAME; i++)
                        rx_bits_double[i] = rx_bits[i];
                    fwrite(rx_bits_double, sizeof(double), COHPSK_BITS_PER_FRAME, fout);
                }
            }
            else {
                if (sd == 0) {
                    for(i=0; i<COHPSK_BITS_PER_FRAME; i++)
                        rx_bits_char[i] = cohpsk->rx_bits_lower[i] < 0.0;
                    fwrite(rx_bits_char, sizeof(char), COHPSK_BITS_PER_FRAME, fout);
                    for(i=0; i<COHPSK_BITS_PER_FRAME; i++)
                        rx_bits_char[i] = cohpsk->rx_bits_upper[i] < 0.0;
                    fwrite(rx_bits_char, sizeof(char), COHPSK_BITS_PER_FRAME, fout);
                }
                else {
                    for(i=0; i<COHPSK_BITS_PER_FRAME; i++)
                        rx_bits_double[i] = cohpsk->rx_bits_lower[i];
                    fwrite(rx_bits_double, sizeof(double), COHPSK_BITS_PER_FRAME, fout);
                    for(i=0; i<COHPSK_BITS_PER_FRAME; i++)
                        rx_bits_double[i] = cohpsk->rx_bits_upper[i];
                    fwrite(rx_bits_double, sizeof(double), COHPSK_BITS_PER_FRAME, fout);
                }
            }

            if (oct) {
                for(r=0; r<NSYMROW; r++, log_data_r++) {
                    for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
                        rx_amp_log[log_data_r*COHPSK_NC*COHPSK_ND+c] = cohpsk->amp_[r][c];
                        rx_phi_log[log_data_r*COHPSK_NC*COHPSK_ND+c] = cohpsk->phi_[r][c];
                        rx_symb_log[log_data_r*COHPSK_NC*COHPSK_ND+c] = cohpsk->rx_symb[r][c];
                    }
                }

                f_est_log[frames-1] = cohpsk->f_est;
                ratio_log[frames-1] = cohpsk->ratio;
                //fprintf(stderr,"ratio: %f\n", cohpsk->ratio);

                //printf("frames: %d log_data_r: %d\n", frames, log_data_r);
                if (frames == logframes)
                    oct = 0;
            }
        }

    	/* if this is in a pipeline, we probably don't want the usual
    	   buffering to occur */

        if (fout == stdout) fflush(stdout);
    }

    fclose(fin);
    fclose(fout);

    /* optionally dump Octave files */

    if (foct != NULL) {
        octave_save_float(foct, "rx_amp_log_c", (float*)rx_amp_log, log_data_r, COHPSK_NC*COHPSK_ND, COHPSK_NC*COHPSK_ND);
        octave_save_float(foct, "rx_phi_log_c", (float*)rx_phi_log, log_data_r, COHPSK_NC*COHPSK_ND, COHPSK_NC*COHPSK_ND);
        octave_save_complex(foct, "rx_symb_log_c", (COMP*)rx_symb_log, log_data_r, COHPSK_NC*COHPSK_ND, COHPSK_NC*COHPSK_ND);
        octave_save_float(foct, "rx_timing_log_c", (float*)cohpsk->rx_timing_log, 1, cohpsk->rx_timing_log_index, cohpsk->rx_timing_log_index);
        octave_save_float(foct, "f_est_log_c", f_est_log, 1, logframes, logframes);
        octave_save_float(foct, "ratio_log_c", ratio_log, 1, logframes, logframes);
        fclose(foct);
    }

    cohpsk_destroy(cohpsk);


    return 0;
}
