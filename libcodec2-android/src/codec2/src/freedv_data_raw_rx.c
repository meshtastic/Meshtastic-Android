/*---------------------------------------------------------------------------*\

  FILE........: freedv_data_raw_rx.c
  AUTHOR......: David Rowe
  DATE CREATED: May 2020

  Demonstrates receiving frames of raw data bytes (instead of
  compressed speech) using the FreeDV API.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2020 David Rowe

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
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <getopt.h>
#include <signal.h>

#include "freedv_api.h"
#include "modem_stats.h"
#include "octave.h"
#include "fsk.h"

/* other processes can end this program using signals */

static volatile int finish = 0;
void  INThandler(int sig) {
    fprintf(stderr,"signal received: %d\n", sig);
    finish = 1;
}

int main(int argc, char *argv[]) {
    FILE                      *fin, *fout;
    struct freedv_advanced     adv = {0,2,100,8000,1000,200, "H_256_512_4"};
    struct freedv             *freedv;
    int                        nin, nbytes, nbytes_out = 0, nframes_out = 0, buf = 0;
    int                        mode;
    int                        verbose = 0, use_testframes = 0;
    int                        mask = 0;
    int                        framesperburst = 1;
    FILE                      *foct = NULL;
    int                        quiet = 0;
    int                        single_line_summary = 0;
    float                      snr_sum = 0.0;
    
    if (argc < 3) {
    helpmsg:
      	fprintf(stderr, "\nusage: %s [options] FSK_LDPC|DATAC0|DATAC1|DATAC3 InputModemSpeechFile BinaryDataFile\n"
               "  -v or --vv              verbose options\n"
               "  --testframes            count raw and coded errors in testframes sent by tx\n"
               "  --framesperburst  N     N frames per burst (default 1, must match Tx)\n"
               "  --scatter         file  write scatter diagram symbols to file (Octave text file format)\n"
               "  --singleline            single line summary at end of test, used for logging\n"
               "  --quiet\n"
               "\n"
               "For FSK_LDPC only:\n\n"
               "  -m      2|4     number of FSK tones\n"
               "  --Fs    FreqHz  sample rate (default 8000)\n"
               "  --Rs    FreqHz  symbol rate (default 100)\n"
               "  --mask shiftHz  Use \"mask\" freq estimator (default is \"peak\" estimator)\n\n", argv[0]);
       	
        fprintf(stderr, "example: %s --framesperburst 1 --testframes datac0 samples.s16 /dev/null\n\n", argv[0]);
	    exit(1);
    }

    int o = 0;
    int opt_idx = 0;
    while( o != -1 ){
        static struct option long_opts[] = {
            {"testframes",      no_argument,        0, 't'},
            {"help",            no_argument,        0, 'h'},
            {"Fs",              required_argument,  0, 'f'},
            {"Rs",              required_argument,  0, 'r'},
            {"vv",              no_argument,        0, 'x'},
            {"vvv",             no_argument,        0, 'y'},
            {"mask",            required_argument,  0, 'k'},
            {"framesperburst",  required_argument,  0, 's'},
            {"scatter",         required_argument,  0, 'c'},
            {"quiet",           required_argument,  0, 'q'},
            {"singleline",      no_argument,        0, 'b'},
            {0, 0, 0, 0}
        };

        o = getopt_long(argc,argv,"bf:hm:qr:tvx",long_opts,&opt_idx);

        switch(o) {
        case 'b':
            single_line_summary = 1;
            break;
        case 'c':
            foct = fopen(optarg,"wt");
            assert(foct != NULL);
            break;
        case 'f':
            adv.Fs = atoi(optarg);
            break;
        case 'k':
            mask = 1;
            adv.tone_spacing = atoi(optarg);
            break;
        case 'm':
            adv.M = atoi(optarg);
            break;
        case 'q':
            quiet = 1;
            break;
        case 'r':
            adv.Rs = atoi(optarg);
            break;
        case 's':
            framesperburst = atoi(optarg);
            break;
        case 't':
            use_testframes = 1;
            break;
        case 'v':
            verbose = 1;
            break;
        case 'x':
            verbose = 2;
            break;
        case 'y':
            verbose = 3;
            break;
        case 'h':
        case '?':
            goto helpmsg;
            break;
        }
    }
    int dx = optind;

    if( (argc - dx) < 3) {
        fprintf(stderr, "too few arguments.\n");
        goto helpmsg;
    }

    mode = -1;
    if (!strcmp(argv[dx],"FSK_LDPC") || !strcmp(argv[dx],"fsk_ldpc")) mode = FREEDV_MODE_FSK_LDPC;
    if (!strcmp(argv[dx],"DATAC0") || !strcmp(argv[dx],"datac0")) mode = FREEDV_MODE_DATAC0;
    if (!strcmp(argv[dx],"DATAC1") || !strcmp(argv[dx],"datac1")) mode = FREEDV_MODE_DATAC1;
    if (!strcmp(argv[dx],"DATAC3") || !strcmp(argv[dx],"datac3")) mode = FREEDV_MODE_DATAC3;
    if (mode == -1) {
        fprintf(stderr, "Error in mode: %s\n", argv[dx]);
        exit(1);
    }

    if (strcmp(argv[dx+1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[dx+1],"rb")) == NULL ) {
	     fprintf(stderr, "Error opening input raw modem sample file: %s: %s.\n",
             argv[2], strerror(errno));
	     exit(1);
    }

    if (strcmp(argv[dx+2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[dx+2],"wb")) == NULL ) {
	     fprintf(stderr, "Error opening output speech sample file: %s: %s.\n",
               argv[3], strerror(errno));
	     exit(1);
    }

    if (mode != FREEDV_MODE_FSK_LDPC)
        freedv = freedv_open(mode);
    else {
        freedv = freedv_open_advanced(mode, &adv);
        struct FSK *fsk = freedv_get_fsk(freedv);
        fsk_set_freq_est_alg(fsk, mask);
    }

    assert(freedv != NULL);
    freedv_set_verbose(freedv, verbose);
    freedv_set_test_frames(freedv, use_testframes);
    freedv_set_frames_per_burst(freedv, framesperburst);
    
    if (mode == FREEDV_MODE_FSK_LDPC) {
        struct FSK *fsk = freedv_get_fsk(freedv);
        if (!quiet) fprintf(stderr, "Nbits: %d N: %d Ndft: %d\n", fsk->Nbits, fsk->N, fsk->Ndft);
    }

    /* for streaming bytes it's much easier use the modes that have a multiple of 8 payload bits/frame */
    assert((freedv_get_bits_per_modem_frame(freedv) % 8) == 0);
    int bytes_per_modem_frame = freedv_get_bits_per_modem_frame(freedv)/8;
    // last two bytes used for CRC
    if (!quiet) fprintf(stderr, "payload bytes_per_modem_frame: %d\n", bytes_per_modem_frame - 2);
    uint8_t bytes_out[bytes_per_modem_frame];
    short  demod_in[freedv_get_n_max_modem_samples(freedv)];

    signal(SIGINT, INThandler);
    signal(SIGTERM, INThandler);

    /* We need to work out how many samples the demod needs on each
       call (nin).  This is used to adjust for differences in the tx
       and rx sample clock frequencies */

    nin = freedv_nin(freedv);
    while((fread(demod_in, sizeof(short), nin, fin) == nin) && !finish) {
        buf++;

        nbytes = freedv_rawdatarx(freedv, bytes_out, demod_in);
        nin = freedv_nin(freedv);

        if (nbytes) {
            // dont output CRC
            fwrite(bytes_out, sizeof(uint8_t), nbytes-2, fout);
            
            // log some stats
            nbytes_out += nbytes-2;
            nframes_out++;
            struct MODEM_STATS stats;
            freedv_get_modem_extended_stats(freedv, &stats);
            snr_sum += stats.snr_est;
            if (foct) {
                char name[64]; sprintf(name, "rx_symbols_%d", nframes_out);
                octave_save_complex(foct, name, (COMP*) stats.rx_symbols, stats.nr, stats.Nc, MODEM_STATS_NC_MAX+1);
            }
        }
        
	    /* if using pipes we probably don't want the usual buffering */
        if (fout == stdout) fflush(stdout);
    }

    fclose(fin);
    fclose(fout);
    fprintf(stderr, "modembufs: %6d bytes: %5d Frms.: %5d SNRAv: %5.2f\n", 
            buf, nbytes_out, nframes_out, snr_sum/nframes_out);
    int ret = 0;
    
    /* in testframe mode finish up with some stats */

    if (freedv_get_test_frames(freedv)) {
        int Tbits = freedv_get_total_bits(freedv);
        int Terrs = freedv_get_total_bit_errors(freedv);
        float uncoded_ber = (float)Terrs/Tbits;
        fprintf(stderr, "BER......: %5.4f Tbits: %5d Terrs: %5d\n", (double)uncoded_ber, Tbits, Terrs);
        int Tbits_coded = freedv_get_total_bits_coded(freedv);
        int Terrs_coded = freedv_get_total_bit_errors_coded(freedv);
        float coded_ber = (float)Terrs_coded/Tbits_coded;
        fprintf(stderr, "Coded BER: %5.4f Tbits: %5d Terrs: %5d\n", (double)coded_ber, Tbits_coded, Terrs_coded);
        int Tpackets = freedv_get_total_packets(freedv);
        int Tpacket_errors = freedv_get_total_packet_errors(freedv);
        fprintf(stderr, "Coded FER: %5.4f Tfrms: %5d Tfers: %5d\n", (float)Tpacket_errors/Tpackets, Tpackets, Tpacket_errors);
        
        if (single_line_summary) {
            struct MODEM_STATS stats;
            freedv_get_modem_extended_stats(freedv, &stats);
            fprintf(stderr, "FrmGd FrmDt Bytes SNRAv RawBER    Pre  Post UWfails\n");
            fprintf(stderr, "%5d %5d %5d %5.2f %5.4f  %5d %5d   %5d\n", 
                    nframes_out, Tpackets, nbytes_out, snr_sum/nframes_out, uncoded_ber, stats.pre, stats.post, stats.uw_fails);
        }
        
        /* set return code for Ctest */
        if ((uncoded_ber < 0.1f) && (coded_ber < 0.01f))
            ret = 0;
        else
            ret = 1;
    }

    freedv_close(freedv);
    if (foct) fclose(foct);
    return ret;
}
