/*---------------------------------------------------------------------------*\

  FILE........: freedv_rx.c
  AUTHOR......: David Rowe
  DATE CREATED: August 2014

  Demo/development receive program for FreeDV API functions:

  Example usage (all one line):

    $ cd codec2/build_linux/src
    $ ./freedv_tx 1600 ../../raw/ve9qrp_10s.raw - | ./freedv_rx 1600 - - | aplay -f S16

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2014 David Rowe

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

#include "reliable_text.h"
#include "freedv_api.h"
#include "modem_stats.h"

#define NDISCARD 5                /* BER measure optionally discards first few frames after sync */

/* optioal call-back function for received txt characters */
void my_put_next_rx_char(void *states, char c) { fprintf((FILE*)states, "%c", c); }

static FILE* reliable_tx_fp;
reliable_text_t reliable_text_obj;

void on_reliable_text_rx(reliable_text_t rt, const char* txt_ptr, int length, void* state)
{
    fprintf(reliable_tx_fp, "%s\n", txt_ptr);
    reliable_text_reset(reliable_text_obj);
}

int main(int argc, char *argv[]) {
    FILE                      *fin, *fout, *ftxt_rx = NULL;
    int                        nin, nout, nout_total = 0, frame = 0;
    struct MODEM_STATS         stats = {0};
    int                        mode;
    int                        sync;
    float                      snr_est;
    float                      clock_offset;
    int                        use_testframes, verbose, discard, use_complex, use_dpsk, use_reliabletext;
    int                        use_squelch;
    float                      squelch = 0;
    struct freedv             *freedv;
    int                        use_passthroughgain;
    float                      passthroughgain = 0.0;
      
    char f2020[80] = {0};
#ifdef __LPCNET__
    sprintf(f2020,"|2020|2020B");
#endif
    
    if (argc < 4) {
    helpmsg:
	fprintf(stderr, "usage: %s [options]  1600|700C|700D|700E|2400A|2400B|800XA%s InputModemSpeechFile OutputSpeechRawFile\n"
                "\n"
                "  --discard               Reset BER stats on loss of sync, helps us get sensible BER results\n"
                "  --dpsk                  Use differential PSK rather than coherent PSK\n"
                "  --reliabletext txt      Send 'txt' using reliable text protocol\n"
                "  --txtrx        filename Store reliable text output to filename\n"
                "  --squelch      leveldB  Set squelch level\n"
                "  --testframes            testframes assumed to be received instead of coded speech, measure BER/PER\n"
                "  --usecomplex            Complex int16 input samples (default real int16)\n"
                "  -v                      Verbose level 1\n"
                "  --vv                    Verbose level 2\n"
                "\n", argv[0], f2020);
        fprintf(stderr, "example: $ %s 1600 hts1a_fdmdv.raw hts1a_out.raw \n", argv[0]);
	exit(1);
    }

    use_testframes = verbose = discard = use_complex = use_dpsk = use_squelch = 0; use_reliabletext = 0;
    use_passthroughgain = 0;
    
    int o = 0;
    int opt_idx = 0;
    while( o != -1 ){
        static struct option long_opts[] = {
            {"discard",         no_argument,        0, 'i'},
            {"dpsk",            no_argument,        0, 'd'},
            {"help",            no_argument,        0, 'h'},
            {"reliabletext",    no_argument,        0, 'r'},
            {"squelch",         required_argument,  0, 's'},
            {"txtrx",           required_argument,  0, 'x'},
            {"testframes",      no_argument,        0, 't'},
            {"usecomplex",      no_argument,        0, 'c'},
            {"verbose1",        no_argument,        0, 'v'},
            {"vv",              no_argument,        0, 'w'},
            {"passthroughgain", required_argument,  0, 'p'},
            {0, 0, 0, 0}
        };

        o = getopt_long(argc,argv,"idhr:s:x:tcvwp:",long_opts,&opt_idx);

        switch(o) {
        case 'i':
            discard = 1;
            break;
        case 'c':
            use_complex = 1;
            break;
        case 'd':
            use_dpsk = 1;
            break;
	case 'p':
	    use_passthroughgain = 1;
	    passthroughgain = atof(optarg);
            break;
	case 'r':
            use_reliabletext = 1;
            break;
        case 's':
            use_squelch = 1;
            squelch = atof(optarg);
            break;
        case 't':
            use_testframes = 1;
            break;
        case 'x':
            ftxt_rx = fopen(optarg, "wt");
            assert(ftxt_rx != NULL);
            break;
        case 'v':
            verbose = 1;
            break;
        case 'w':
            verbose = 2;
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
    if (!strcmp(argv[dx],"1600")) mode = FREEDV_MODE_1600;
    if (!strcmp(argv[dx],"700C")) mode = FREEDV_MODE_700C;
    if (!strcmp(argv[dx],"700D")) mode = FREEDV_MODE_700D;
    if (!strcmp(argv[dx],"700E")) mode = FREEDV_MODE_700E;
    if (!strcmp(argv[dx],"2400A")) mode = FREEDV_MODE_2400A;
    if (!strcmp(argv[dx],"2400B")) mode = FREEDV_MODE_2400B;
    if (!strcmp(argv[dx],"800XA")) mode = FREEDV_MODE_800XA;
    #ifdef __LPCNET__
    if (!strcmp(argv[dx],"2020"))  mode = FREEDV_MODE_2020;
    if (!strcmp(argv[dx],"2020B"))  mode = FREEDV_MODE_2020B;
    #endif
    if (mode == -1) {
        fprintf(stderr, "Error in mode: %s\n", argv[dx]);
        exit(1);
    }

    if (strcmp(argv[dx+1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[dx+1],"rb")) == NULL ) {
	fprintf(stderr, "Error opening input raw modem sample file: %s: %s.\n",
                argv[dx+1], strerror(errno));
	exit(1);
    }

    if (strcmp(argv[dx+2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[dx+2],"wb")) == NULL ) {
	fprintf(stderr, "Error opening output speech sample file: %s: %s.\n",
                argv[dx+2], strerror(errno));
	exit(1);
    }

    freedv = freedv_open(mode);
    assert(freedv != NULL);

    /* set up a few options, calling these is optional -------------------------*/

    freedv_set_test_frames(freedv, use_testframes);
    if (verbose == 2) freedv_set_verbose(freedv, verbose);
    
    if (use_squelch) {
        freedv_set_snr_squelch_thresh(freedv, squelch);
        freedv_set_squelch_en(freedv, 1);
    }
    freedv_set_dpsk(freedv, use_dpsk);
    if (use_passthroughgain) freedv_passthrough_gain(freedv, passthroughgain);
    
    /* install optional handler for recevied txt characters */
    if (ftxt_rx != NULL)
    {
        if (use_reliabletext)
        {
            reliable_tx_fp = ftxt_rx;
            
            reliable_text_obj = reliable_text_create();
            assert(reliable_text_obj != NULL);
            reliable_text_set_string(reliable_text_obj, "AB1CDEF", 7); // not used
            reliable_text_use_with_freedv(reliable_text_obj, freedv, on_reliable_text_rx, NULL);
        }
        else
        {
            freedv_set_callback_txt(freedv, my_put_next_rx_char, NULL, ftxt_rx);
        }
    }

    /* note use of API functions to tell us how big our buffers need to be -----*/

    short speech_out[freedv_get_n_max_speech_samples(freedv)];
    short demod_in[freedv_get_n_max_modem_samples(freedv)];

    /* We need to work out how many samples the demod needs on each
       call (nin).  This is used to adjust for differences in the tx
       and rx sample clock frequencies.  Note also the number of
       output speech samples "nout" is time varying. */

    nin = freedv_nin(freedv);
    while(fread(demod_in, sizeof(short), nin, fin) == nin) {
        frame++;

        if (use_complex) {
            /* exercise the complex version of the API (useful
               for testing 700D which has a different code path for
               short samples) */
            COMP demod_in_complex[nin];

            for(int i=0; i<nin; i++) {
                demod_in_complex[i].real = (float)demod_in[i];
                demod_in_complex[i].imag = 0.0f;
            }
            nout = freedv_comprx(freedv, speech_out, demod_in_complex);
        } else {
            // most common interface - real shorts in, real shorts out
            nout = freedv_rx(freedv, speech_out, demod_in);
        }

       /* IMPORTANT: don't forget to do this in the while loop to
           ensure we fread the correct number of samples: ie update
           "nin" before every call to freedv_rx()/freedv_comprx() */
        nin = freedv_nin(freedv);

        /* optionally read some stats */
        freedv_get_modem_stats(freedv, &sync, &snr_est);
        freedv_get_modem_extended_stats(freedv, &stats);
        int total_bit_errors = freedv_get_total_bit_errors(freedv);
        clock_offset = stats.clock_offset;

        if (discard && (sync == 0)) {
            // discard BER results if we get out of sync, helps us get sensible BER results
            freedv_set_total_bits(freedv, 0); freedv_set_total_bit_errors(freedv, 0);
            freedv_set_total_bits_coded(freedv, 0); freedv_set_total_bit_errors_coded(freedv, 0);
        }

        fwrite(speech_out, sizeof(short), nout, fout);
        nout_total += nout;

        if (verbose == 1) {
            fprintf(stderr, "frame: %d  demod sync: %d  nin: %d demod snr: %3.2f dB  bit errors: %d clock_offset: %f\n",
                    frame, sync, nin, snr_est, total_bit_errors, clock_offset);
        }

	/* if using pipes we probably don't want the usual buffering
           to occur */
        if (fout == stdout) fflush(stdout);
    }

    if (ftxt_rx != NULL) fclose(ftxt_rx);
    fclose(fin);
    fclose(fout);
    fprintf(stderr, "frames decoded: %d  output speech samples: %d\n", frame, nout_total);

    /* finish up with some stats */

    if (freedv_get_test_frames(freedv)) {
        int Tbits = freedv_get_total_bits(freedv);
        int Terrs = freedv_get_total_bit_errors(freedv);
        float uncoded_ber = (float)Terrs/Tbits;
        fprintf(stderr, "BER......: %5.4f  Tbits: %8d  Terrs: %8d\n",
		                    (double)uncoded_ber, Tbits, Terrs);
        if ((mode == FREEDV_MODE_700D) || (mode == FREEDV_MODE_700E)  ||
            (mode == FREEDV_MODE_2020) || (mode == FREEDV_MODE_2020B) ) {
            int Tbits_coded = freedv_get_total_bits_coded(freedv);
            int Terrs_coded = freedv_get_total_bit_errors_coded(freedv);
            float coded_ber = (float)Terrs_coded/Tbits_coded;
            fprintf(stderr, "Coded BER: %5.4f  Tbits: %8d  Terrs: %8d\n",
                    (double)coded_ber, Tbits_coded, Terrs_coded);
            int Tpackets = freedv_get_total_packets(freedv);
            int Tpacket_errors = freedv_get_total_packet_errors(freedv);
            float per = (float)Tpacket_errors/Tpackets;
            fprintf(stderr, "Coded PER: %5.4f  Tpkts: %8d  Tpers: %8d\n",
                    per, Tpackets, Tpacket_errors);

            /* set return code for Ctest */
            if ((uncoded_ber < 0.1f) && (coded_ber < 0.01f))
                return 0;
            else
                return 1;
        }
    }

    if (use_reliabletext)
    {
        reliable_text_destroy(reliable_text_obj);
    }
    
    freedv_close(freedv);
    
    return 0;
}
