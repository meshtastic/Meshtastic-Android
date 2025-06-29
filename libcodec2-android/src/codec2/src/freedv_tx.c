/*---------------------------------------------------------------------------*\

  FILE........: freedv_tx.c
  AUTHOR......: David Rowe
  DATE CREATED: August 2014

  Demo transmit program for FreeDV API functions.

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

struct my_callback_state {
    char  tx_str[80];
    char *ptx_str;
    int calls;
};

char my_get_next_tx_char(void *callback_state) {
    struct my_callback_state* pstate = (struct my_callback_state*)callback_state;
    char  c = *pstate->ptx_str++;
    if (*pstate->ptx_str == 0) {
        pstate->ptx_str = pstate->tx_str;
    }

    return c;
}

void on_reliable_text_rx(reliable_text_t rt, const char* txt_ptr, int length, void* state)
{
    // empty since we don't expect to receive anything in this program.
}

int main(int argc, char *argv[]) {
    FILE                     *fin, *fout;
    struct freedv            *freedv;
    int                       mode;
    int                       use_testframes, use_clip, use_txbpf, use_dpsk, use_reliabletext;
    char                     *callsign = "";
    reliable_text_t           reliable_text_obj;
    char f2020[80] = {0};
#ifdef __LPCNET__
    sprintf(f2020,"|2020|2020");
#endif
   
    if (argc < 4) {
    helpmsg:
        fprintf(stderr, "usage: %s [options] 1600|700C|700D|700E|2400A|2400B|800XA%s InputRawSpeechFile OutputModemRawFile\n"
                "\n"
                "  --clip         0|1  Clipping (compression) of modem output samples for reduced PAPR\n"
                "                      and higher average power\n"
                "  --dpsk              Use differential PSK rather than coherent PSK\n"
                "  --reliabletext txt  Send 'txt' using reliable text protocol\n"
                "  --testframes        Send testframe instead of coded speech. Number of testsframes depends on\n"
                "                      length of speech input file\n"
                "  --txbpf        0|1  Bandpass filter\n"
                "\n", argv[0], f2020);
        fprintf(stderr, "example: $ %s 1600 hts1a.raw hts1a_fdmdv.raw\n", argv[0]);
        exit(1);
    }

    use_testframes = 0; use_clip = 0; use_txbpf = 1; use_dpsk = 0; use_reliabletext = 0;

    int o = 0;
    int opt_idx = 0;
    while( o != -1 ){
        static struct option long_opts[] = {
            {"clip",           required_argument,  0, 'l'},
            {"dpsk",           no_argument,        0, 'd'},
            {"help",           no_argument,        0, 'h'},
            {"reliabletext",   required_argument,  0, 'r'},
            {"testframes",     no_argument,        0, 't'},
            {"txbpf",          required_argument,  0, 'b'},
            {0, 0, 0, 0}
        };

        o = getopt_long(argc,argv,"l:dhr:tb:",long_opts,&opt_idx);

        switch(o) {
        case 'b':
            use_txbpf = atoi(optarg);
            break;
        case 'd':
            use_dpsk = 1;
            break;
        case 'l':
            use_clip = atoi(optarg);
            break;
        case 'r':
            use_reliabletext = 1;
            callsign = optarg;
            break;
        case 't':
            use_testframes = 1;
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
    if (!strcmp(argv[dx],"2020B")) mode = FREEDV_MODE_2020B;    
    #endif
    if (mode == -1) {
        fprintf(stderr, "Error in mode: %s\n", argv[dx]);
        exit(1);
    }

    if (strcmp(argv[dx+1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[dx+1],"rb")) == NULL ) {
        fprintf(stderr, "Error opening input raw speech sample file: %s: %s.\n", argv[dx+1], strerror(errno));
        exit(1);
    }

    if (strcmp(argv[dx+2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[dx+2],"wb")) == NULL ) {
        fprintf(stderr, "Error opening output modem sample file: %s: %s.\n", argv[dx+2], strerror(errno));
        exit(1);
    }

    freedv = freedv_open(mode);
    assert(freedv != NULL);

    /* these are all optional ------------------ */
    freedv_set_test_frames(freedv, use_testframes);
    freedv_set_clip(freedv, use_clip);
    freedv_set_tx_bpf(freedv, use_txbpf);
    freedv_set_dpsk(freedv, use_dpsk);
    freedv_set_verbose(freedv, 1);
    freedv_set_eq(freedv, 1); /* for 700C/D/E & 800XA */

    if (use_reliabletext) {
        reliable_text_obj = reliable_text_create();
        assert(reliable_text_obj != NULL);
        reliable_text_set_string(reliable_text_obj, callsign, strlen(callsign));
        reliable_text_use_with_freedv(reliable_text_obj, freedv, on_reliable_text_rx, NULL);
    }
    else {
        /* set up callback for txt msg chars */
        struct my_callback_state  my_cb_state;
        sprintf(my_cb_state.tx_str, "cq cq cq hello world\r");
        my_cb_state.ptx_str = my_cb_state.tx_str;
        my_cb_state.calls = 0;
        freedv_set_callback_txt(freedv, NULL, &my_get_next_tx_char, &my_cb_state);
    }
    
    /* handy functions to set buffer sizes, note tx/modulator always
       returns freedv_get_n_nom_modem_samples() (unlike rx side) */
    int n_speech_samples = freedv_get_n_speech_samples(freedv);
    short speech_in[n_speech_samples];
    int n_nom_modem_samples = freedv_get_n_nom_modem_samples(freedv);
    short mod_out[n_nom_modem_samples];

    /* OK main loop  --------------------------------------- */

    while(fread(speech_in, sizeof(short), n_speech_samples, fin) == n_speech_samples) {
        freedv_tx(freedv, mod_out, speech_in);
        fwrite(mod_out, sizeof(short), n_nom_modem_samples, fout);

        /* if using pipes we don't want the usual buffering to occur */
        if (fout == stdout) fflush(stdout);
    }

    freedv_close(freedv);
    if (use_reliabletext) reliable_text_destroy(reliable_text_obj);
    fclose(fin);
    fclose(fout);

    return 0;
}
