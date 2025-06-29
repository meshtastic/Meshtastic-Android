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

/* Typical run, using internal testframes:

    # Copy N frames of a raw audio file to stm_in.raw. 
    # N=6, count = 6 * 1280 * 2 = 15360
    dd bs=1 count=15360 if=../../../../raw/hts1.raw of=stm_in.raw

    # Reference
    freedv_tx 700D stm_in.raw ref_mod.raw --testframes

    # Create config
    echo "71000000" > stm_cfg.txt

    # Run stm32
    run_stm32_prog ../../src/tst_api_mod.elf --load

    # Check output
    freedv_rx 700D ref_mod.raw ref_rx.raw --testframes
    freedv_rx 700D stm_out.raw stm_rx.raw --testframes
    #optional: ofdm_demod ref_mod.raw ref_demod.raw --ldpc --testframes
    #optional: ofdm_demod stm_out.raw stm_demod.raw --ldpc --testframes

    compare_ints -s -b 2 ref_mod.raw stm_out.raw
*/


#include <assert.h>
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
#include "codec2.h"

#include "semihosting.h"
#include "stm32f4xx_conf.h"
#include "stm32f4xx.h"
#include "machdep.h"
#include "memtools.h"

struct my_callback_state {
    char  tx_str[80];
    char *ptx_str;
    int calls;
};

char my_get_next_tx_char(void *callback_state) {
    struct my_callback_state* pstate = (struct my_callback_state*)callback_state;
    char  c = *pstate->ptx_str++;
    //fprintf(stderr, "my_get_next_tx_char: %c\n", c);
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
    fprintf(stderr, "datarx callback called, this should not happen!\n");
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
    int            f_cfg, f_in, f_out;
    int            frame;
    int            num_read;

    //struct CODEC2 *c2;
    struct my_callback_state  my_cb_state;

    semihosting_init();
    memtools_find_unused(printf);
    
    ////////
    // Test configuration, read from stm_cfg.txt
    int     config_mode;        // 0
    int     config_testframes;  // 1
    int     use_clip = 0;       // 2
    int     use_txbpf = 0;      // 3
    //int     config_verbose;   // 6
    //int     config_profile;   // 7
    char config[8];
    f_cfg = open("stm_cfg.txt", O_RDONLY);
    if (f_cfg == -1) {
        fprintf(stderr, "Error opening config file\n");
        exit(1);
    }
    if (read(f_cfg, &config[0], 8) != 8) {
        fprintf(stderr, "Error reading config file\n");
        exit(1);
    }
    config_mode = config[0] - '0';
    if (config_mode == 8)
    {
        // For the purposes of the UT system, '8' is 700E.
        config_mode = FREEDV_MODE_700E;
    }
    config_testframes = config[1] - '0';
    use_clip = config[2] - '0';
    use_txbpf = config[3] - '0';
    //config_verbose = config[6] - '0';
    //config_profile = config[7] - '0';
    close(f_cfg);

    //int use_codectx = 0; 
    //int use_datatx = 0; 
    //int use_testframes = 0; 
    int use_ext_vco = 0;

    ////////
    //PROFILE_VAR(freedv_start);
    //machdep_profile_init();

    ////////
    freedv = freedv_open(config_mode);
    assert(freedv != NULL);
    
    fprintf(stderr, "freedv opened %p\n", freedv);

    freedv_set_test_frames(freedv, config_testframes);

    int n_speech_samples = freedv_get_n_speech_samples(freedv);
    short *speech_in = (short*)malloc(sizeof(short)*n_speech_samples);
    int n_nom_modem_samples = freedv_get_n_nom_modem_samples(freedv);
    short *mod_out = (short*)malloc(sizeof(short)*n_nom_modem_samples);

    fprintf(stderr, "n_speech_samples: %d n_nom_modem_samples: %d\n", 
    			n_speech_samples, n_nom_modem_samples);

    fprintf(stderr, "mod_out: %p\n", mod_out);
 
   /*
    // This is "codectx" operation:
    int c2_mode;
    if (config_mode == FREEDV_MODE_700)  {
        c2_mode = CODEC2_MODE_700;
    } else if ((config_mode == FREEDV_MODE_700B) ||
               (config_mode == FREEDV_MODE_800XA)) {
        c2_mode = CODEC2_MODE_700B;
    } else if ((config_mode == FREEDV_MODE_700C) ||
               (config_mode == FREEDV_MODE_700D)) {
        c2_mode = CODEC2_MODE_700C;
    } else {
        c2_mode = CODEC2_MODE_1300;
    }
    c2 = codec2_create(c2_mode);

    int bits_per_codec_frame = codec2_bits_per_frame(c2);
    int bytes_per_codec_frame = (bits_per_codec_frame + 7) / 8;
    int codec_frames = freedv_get_n_codec_bits(freedv) / bits_per_codec_frame;
    int inbuf_size = bytes_per_codec_frame * codec_frames;
    unsigned char inbuf[inbuf_size];
*/

    freedv_set_snr_squelch_thresh(freedv, -100.0);
    freedv_set_squelch_en(freedv, 1);
    freedv_set_clip(freedv, use_clip);
    freedv_set_tx_bpf(freedv, use_txbpf);
    freedv_set_ext_vco(freedv, use_ext_vco);
    freedv_set_eq(freedv, 1);
    
    memtools_find_unused(printf);

    // set up callback for txt msg chars 
    sprintf(my_cb_state.tx_str, "cq cq cq hello world\r");
    my_cb_state.ptx_str = my_cb_state.tx_str;
    my_cb_state.calls = 0;
    freedv_set_callback_txt(freedv, NULL, &my_get_next_tx_char, &my_cb_state);

    // set up callback for protocol bits
    freedv_set_callback_protocol(freedv, NULL, &my_get_next_proto, &my_cb_state);

    // set up callback for data packets
    freedv_set_callback_data(freedv, my_datarx, my_datatx, &my_cb_state);

    ////////
    // Streams
    f_in = open("stm_in.raw", O_RDONLY);
    if (f_in == -1) {
        perror("Error opening input file\n");
        exit(1);
    }

    f_out = open("stm_out.raw", (O_CREAT | O_WRONLY), 0644);
    if (f_out == -1) {
        perror("Error opening output file\n");
        exit(1);
    }

    frame = 0;

    fprintf(stderr, "starting main loop\n");

    ////////
    // Main loop
    while ((num_read = read(f_in, speech_in, (sizeof(short) * n_speech_samples))) ==
                                             (sizeof(short) * n_speech_samples)) {
        fprintf(stderr, "frame: %d\r", frame);

        freedv_tx(freedv, mod_out, speech_in);

        write(f_out, mod_out, (sizeof(short) * n_nom_modem_samples));

        frame++ ;
        //machdep_profile_print_logged_samples();

    }
    printf("Done\n");

    close(f_in);
    close(f_out);
    printf("\nEnd of Test\n");

    return(0);
}

/* vi:set ts=4 et sts=4: */
