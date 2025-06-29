/*---------------------------------------------------------------------------*\

  FILE........: tst_api_mod.c
  AUTHOR......: David Rowe, Don Reid
  DATE CREATED: August 2014, Oct 2018

  Test modem modulation via freedv API on the STM32F4.

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

/* This is a test implementation of the Freev API Modulation function.
 * It is used for profiling performance.
 *
 * The input is generated within the test.
 * The output is ignored.
 */


#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <math.h>
#include <assert.h>

#include "freedv_api.h"
#include "codec2.h"

#include "semihosting.h"
#include "stm32f4xx_conf.h"
#include "stm32f4xx.h"
#include "machdep.h"

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

void my_get_next_proto(void *callback_state,char *proto_bits){
    struct my_callback_state* cb_states = (struct my_callback_state*)(callback_state);
    snprintf(proto_bits,3,"%2d",cb_states->calls);
    cb_states->calls = cb_states->calls + 1;
}

/* Called when a packet has been received */
void my_datarx(void *callback_state, unsigned char *packet, size_t size) {
    /* This should not happen while sending... */
    assert(0);
}

/* Called when a new packet can be send */
void my_datatx(void *callback_state, unsigned char *packet, size_t *size) {
    static int data_toggle;
    /* Data could come from a network interface, here we just make up some */
    data_toggle = !data_toggle;
    if (data_toggle) {
        /* Send a packet with data */
        int i;
	for (i = 0; i < 64; i++)
	    packet[i] = i;
        *size = i;
    } else {
        /* set size to zero, the freedv api will insert a header frame */
        *size = 0;
    }
}

    
int main(int argc, char *argv[]) {
    struct freedv *freedv;
    int i;

    struct my_callback_state  my_cb_state;

    int use_clip = 0; 
    int use_txbpf = 0;
    int use_ext_vco = 0;

    ////////
    PROFILE_VAR(prof_freedv_tx);
    machdep_profile_init();

    semihosting_init();

    ////////
    freedv = freedv_open(FREEDV_MODE_700D);

    int n_speech_samples = freedv_get_n_speech_samples(freedv);
    short *speech_in = (short*)malloc(sizeof(short)*n_speech_samples);
    int n_nom_modem_samples = freedv_get_n_nom_modem_samples(freedv);
    short *mod_out = (short*)malloc(sizeof(short)*n_nom_modem_samples);

    freedv_set_snr_squelch_thresh(freedv, -100.0);
    freedv_set_squelch_en(freedv, 1);
    freedv_set_clip(freedv, use_clip);
    freedv_set_tx_bpf(freedv, use_txbpf);
    freedv_set_ext_vco(freedv, use_ext_vco);

    // set up callback for txt msg chars 
    sprintf(my_cb_state.tx_str, "cq cq cq hello world\r");
    my_cb_state.ptx_str = my_cb_state.tx_str;
    my_cb_state.calls = 0;
    freedv_set_callback_txt(freedv, NULL, &my_get_next_tx_char, &my_cb_state);

    // set up callback for protocol bits
    freedv_set_callback_protocol(freedv, NULL, &my_get_next_proto, &my_cb_state);

    // set up callback for data packets
    freedv_set_callback_data(freedv, my_datarx, my_datatx, &my_cb_state);

    int frame = 0;

    for (i=0; i<n_speech_samples; i++) speech_in[i] = 0;

    ////////
    // Main loop
    while(frame < 10) {

        PROFILE_SAMPLE(prof_freedv_tx);

        freedv_tx(freedv, mod_out, speech_in);

        PROFILE_SAMPLE_AND_LOG2(prof_freedv_tx, "freedv_tx");

        //write(f_out, mod_out, (sizeof(short) * n_nom_modem_samples));

        frame++ ;

    }
    
    machdep_profile_print_logged_samples();

    fclose(stdout);
    fclose(stderr);
    return(0);
}

/* vi:set ts=4 et sts=4: */
