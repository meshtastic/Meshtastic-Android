/*---------------------------------------------------------------------------*\

  FILE........: freedv_mixed_rx.c
  AUTHOR......: Jeroen Vreeken & David Rowe
  DATE CREATED: May 2020

  Demo receive program for FreeDV API that demonstrates shows mixed
  VHF packet data and speech frames.

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
#include <stdlib.h>
#include <stdio.h>

#include "freedv_api.h"
#include "modem_stats.h"

#include "codec2.h"


struct my_callback_state {
    int calls;
    FILE *ftxt;
};

/* Called when a packet has been received */
void my_datarx(void *callback_state, unsigned char *packet, size_t size) {
    struct my_callback_state* pstate = (struct my_callback_state*)callback_state;
    
    pstate->calls++;
    
    if (pstate->ftxt != NULL) {
        size_t i;
	
	fprintf(pstate->ftxt, "data (%zd bytes): ", size);
	for (i = 0; i < size; i++) {
	    fprintf(pstate->ftxt, "0x%02x ", packet[i]);
	}
	fprintf(pstate->ftxt, "\n");
    }
}

/* Called when a new packet can be send */
void my_datatx(void *callback_state, unsigned char *packet, size_t *size) {
    /* This should not happen while receiving.. */
    fprintf(stderr, "datarx callback called, this should not happen!\n");    
    *size = 0;
}

int main(int argc, char *argv[]) {
    FILE                      *fin, *fout, *ftxt;
    struct freedv             *freedv;
    int                        nin, nout, nout_total = 0, frame = 0;
    struct my_callback_state   my_cb_state = {0};
    int                        mode;
    int                        use_codecrx, verbose;
    struct CODEC2             *c2 = NULL;
    int                        i;

    
    if (argc < 4) {
	printf("usage: %s 2400A|2400B|800XA InputModemSpeechFile OutputSpeechRawFile\n"
               " [--codecrx] [-v]\n", argv[0]);
	printf("e.g    %s 2400A hts1a_fdmdv.raw hts1a_out.raw\n", argv[0]);
	exit(1);
    }

    mode = -1;
    if (!strcmp(argv[1],"2400A"))
        mode = FREEDV_MODE_2400A;
    if (!strcmp(argv[1],"2400B"))
        mode = FREEDV_MODE_2400B;
    if (!strcmp(argv[1],"800XA"))
        mode = FREEDV_MODE_800XA;
    assert(mode != -1);

    if (strcmp(argv[2], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[2],"rb")) == NULL ) {
	fprintf(stderr, "Error opening input raw modem sample file: %s: %s.\n",
         argv[2], strerror(errno));
	exit(1);
    }

    if (strcmp(argv[3], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[3],"wb")) == NULL ) {
	fprintf(stderr, "Error opening output speech sample file: %s: %s.\n",
         argv[3], strerror(errno));
	exit(1);
    }

    use_codecrx = 0; verbose = 0;
    
    if (argc > 4) {
        for (i = 4; i < argc; i++) {
            if (strcmp(argv[i], "--codecrx") == 0) {
                int c2_mode;

                if ((mode == FREEDV_MODE_700C) || (mode == FREEDV_MODE_700D) || (mode == FREEDV_MODE_800XA)) {
                    c2_mode = CODEC2_MODE_700C;
                } else {
                    c2_mode = CODEC2_MODE_1300;
                }
                use_codecrx = 1;

                c2 = codec2_create(c2_mode);
                assert(c2 != NULL);
            }

            if (strcmp(argv[i], "-v") == 0) {
                verbose = 1;
            }
            if (strcmp(argv[i], "-vv") == 0) {
                verbose = 2;
            }
        }
    }

    freedv = freedv_open(mode);
    assert(freedv != NULL);

    freedv_set_verbose(freedv, verbose);

    short speech_out[freedv_get_n_max_speech_samples(freedv)];
    short demod_in[freedv_get_n_max_modem_samples(freedv)];

    ftxt = fopen("freedv_rx_log.txt","wt");
    assert(ftxt != NULL);
    my_cb_state.ftxt = ftxt;
    freedv_set_callback_data(freedv, my_datarx, my_datatx, &my_cb_state);

    /* Note we need to work out how many samples demod needs on each
       call (nin).  This is used to adjust for differences in the tx and rx
       sample clock frequencies.  Note also the number of output
       speech samples is time varying (nout). */

    nin = freedv_nin(freedv);
    while(fread(demod_in, sizeof(short), nin, fin) == nin) {
        frame++;
        
        if (use_codecrx == 0) {
            /* usual case: use the freedv_api to do everything: speech decoding, demodulating */
            nout = freedv_rx(freedv, speech_out, demod_in);
        } else {
            /* demo of codecrx mode - separate demodulation and speech decoding */
            int bits_per_codec_frame = freedv_get_bits_per_codec_frame(freedv);
            int bits_per_modem_frame = freedv_get_bits_per_modem_frame(freedv);
            int bytes_per_codec_frame = (bits_per_codec_frame + 7) / 8;
            int bytes_per_modem_frame = (bits_per_modem_frame + 7) / 8;
            int codec_frames = bits_per_modem_frame / bits_per_codec_frame;
            int samples_per_frame = codec2_samples_per_frame(c2);
            unsigned char encoded[bytes_per_codec_frame * codec_frames];
	    unsigned char rawdata[bytes_per_modem_frame];

            nout = 0;
	    
            /* Use the freedv_api to demodulate only */
            int ncodec = freedv_rawdatarx(freedv, rawdata, demod_in);
	    freedv_codec_frames_from_rawdata(freedv, encoded, rawdata);
	  
            /* decode the speech ourself (or send it to elsewhere, e.g. network) */
            if (ncodec) {
                unsigned char *enc_frame = encoded;
                short *speech_frame = speech_out;
                
                for (i = 0; i < codec_frames; i++) {
                    codec2_decode(c2, speech_frame, enc_frame);
                    enc_frame += bytes_per_codec_frame;
                    speech_frame += samples_per_frame;
                    nout += samples_per_frame;
                }
	    }
        }
        fprintf(ftxt, "Demod of %d samples resulted %d speech samples\n", nin, nout);

        if (nout == 0)
	{
	   /* We did not get any audio.
	      This means the modem is (probably) synced, but a data frame was received
	      Fill in the 'blanks' use by data frames with silence 
	    */
	        nout = freedv_get_n_speech_samples(freedv);
	        memset(speech_out, 0, nout * sizeof(short));
        }
	
        nin = freedv_nin(freedv);

        fwrite(speech_out, sizeof(short), nout, fout);
        nout_total += nout;
        
	/* if this is in a pipeline, we probably don't want the usual
           buffering to occur */

        if (fout == stdout) fflush(stdout);
    }

    fclose(ftxt);
    fclose(fin);
    fclose(fout);
    fprintf(stderr, "frames decoded: %d  output speech samples: %d, data packets: %d\n", frame, nout_total, my_cb_state.calls);

    freedv_close(freedv);
    return 0;
}

