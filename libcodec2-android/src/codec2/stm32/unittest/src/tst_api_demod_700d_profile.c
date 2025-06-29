/*------------------------------------------------------

  FILE........: tst_api_demod.c
  AUTHOR......: David Rowe, Don Reid
  DATE CREATED: 7 July 2018

  Test and profile OFDM de-modulation on the STM32F4.

-------------------------------------------------------*/

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


#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <math.h>
#include <errno.h>

#include "freedv_api.h"
#include "modem_stats.h"
#include "codec2.h"

#include "semihosting.h"
#include "stm32f4xx_conf.h"
#include "stm32f4xx.h"
#include "machdep.h"
    

/* Input and Reference data */
const
#include "api_demod_700d_in_10f.c"

struct my_callback_state {
    //FILE *ftxt;
};

void my_put_next_rx_char(void *callback_state, char c) {
    //fprintf(stdout, "text msg: %c\n", c);
}

void my_put_next_rx_proto(void *callback_state,char *proto_bits){
    //fprintf(stdout, "proto chars: %.*s\n",2, proto_bits);
}

/* Called when a packet has been received */
void my_datarx(void *callback_state, unsigned char *packet, size_t size) {
    //size_t i;
    //    
    //fprintf(stdout, "data (%zd bytes): ", size);
    //for (i = 0; i < size; i++) {
    //    fprintf(stdout, "0x%02x ", packet[i]);
    //}
    //fprintf(stdout, "\n");
}

/* Called when a new packet can be send */
void my_datatx(void *callback_state, unsigned char *packet, size_t *size) {
    /* This should not happen while receiving.. */
    fprintf(stderr, "datarx callback called, this should not happen!\n");    
    *size = 0;
}


int main(int argc, char *argv[]) {
    struct freedv *freedv;
    struct my_callback_state my_cb_state;
    int            frame;
    int            nin, nout;

    ////////
    PROFILE_VAR(prof_freedv_rx);
    machdep_profile_init();

    semihosting_init();

    ////////
    freedv = freedv_open(FREEDV_MODE_700D);

    freedv_set_snr_squelch_thresh(freedv, -100.0);
    freedv_set_squelch_en(freedv, 0);

    short speech_out[freedv_get_n_speech_samples(freedv)];

    freedv_set_callback_txt(freedv, &my_put_next_rx_char, NULL, &my_cb_state);
    freedv_set_callback_protocol(freedv, &my_put_next_rx_proto, NULL, &my_cb_state);
    freedv_set_callback_data(freedv, my_datarx, my_datatx, &my_cb_state);

    frame = 0;

    ////////
    // Main loop

    nin = freedv_nin(freedv);
    int in_ptr = 0;
    while(in_ptr < api_demod_700d_in_10f_len) {

        PROFILE_SAMPLE(prof_freedv_rx);
        
        nout = freedv_shortrx(freedv, speech_out, (short *)&api_demod_700d_in_10f[in_ptr], 1.0f);

        PROFILE_SAMPLE_AND_LOG2(prof_freedv_rx, "freedv_rx");

        //if (nout) write(f_out, speech_out, (sizeof(short) * nout));

        frame++;
        in_ptr += nin * 2;
    }

    machdep_profile_print_logged_samples();

    fclose(stdout);
    fclose(stderr);

}

/* vi:set ts=4 et sts=4: */
