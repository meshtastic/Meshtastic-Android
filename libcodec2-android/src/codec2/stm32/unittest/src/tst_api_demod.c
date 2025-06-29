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

/* Typical run, using internal testframes:

    # Input
    # Copy N frames of a raw audio file to stm_in.raw. 
    dd bs=2560 count=30 if=../../../../raw/hts1.raw of=spch_in.raw
    freedv_tx 700D spch_in.raw stm_in.raw --testframes

    # Reference
    freedv_rx 700D stm_in.raw ref_demod.raw --testframes
    
    # Create config
    echo "71000010" > stm_cfg.txt

    # Run stm32
    run_stm32_prog ../../src/tst_api_demod.elf --load

 */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
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
#include "memtools.h"
    

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

#define SPARE_RAM 3000

int main(int argc, char *argv[]) {
    char           dummy[SPARE_RAM];
    int            f_cfg, f_in, f_out;
    struct freedv *freedv;
    struct my_callback_state my_cb_state;
    int            frame;
    int            nread, nin, nout;
    int            sync;
    float          snr_est;

    // Force test to fail unless we have this much spare RAM (adjusted by experiment)
    memset(dummy, 0, SPARE_RAM);
    
    semihosting_init();
    PROFILE_VAR(freedv_rx_start);
    machdep_profile_init();
    
    ////////
    // Test configuration, read from stm_cfg.txt
    int     config_mode;        // 0
    int     config_testframes;  // 1
    int     config_verbose;   // 6
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
    config_verbose = config[6] - '0';
    //config_profile = config[7] - '0';
    close(f_cfg);
    printf("config_mode: %d config_verbose: %d\n", config_mode, config_verbose);
    
    ////////
    freedv = freedv_open(config_mode);
    assert(freedv != NULL);

    memtools_find_unused(printf);
    
    freedv_set_test_frames(freedv, config_testframes);
    freedv_set_verbose(freedv, config_verbose);

    freedv_set_snr_squelch_thresh(freedv, -100.0);
    freedv_set_squelch_en(freedv, 0);

    short speech_out[freedv_get_n_speech_samples(freedv)];
    short demod_in[freedv_get_n_max_modem_samples(freedv)];

    freedv_set_callback_txt(freedv, &my_put_next_rx_char, NULL, &my_cb_state);
    freedv_set_callback_protocol(freedv, &my_put_next_rx_proto, NULL, &my_cb_state);
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

    ////////
    // Main loop

    nin = freedv_nin(freedv);
    while((nread = read(f_in, demod_in, (sizeof(short) * nin))) == (nin * sizeof(short))) {
        
        fprintf(stderr, "frame: %d, %d bytes read\n", frame, nread);

	PROFILE_SAMPLE(freedv_rx_start);
	nout = freedv_rx(freedv, speech_out, demod_in);
	PROFILE_SAMPLE_AND_LOG2(freedv_rx_start, "  freedv_rx");
	machdep_profile_print_logged_samples();

        fprintf(stderr, "  %d short speech values returned\n", nout);
        if (nout) write(f_out, speech_out, (sizeof(short) * nout));

       if (sync == 0) {
            // discard BER results if we get out of sync, helps us get sensible BER results
            freedv_set_total_bits(freedv, 0); freedv_set_total_bit_errors(freedv, 0);
            freedv_set_total_bits_coded(freedv, 0); freedv_set_total_bit_errors_coded(freedv, 0);
        }
        freedv_get_modem_stats(freedv, &sync, &snr_est);
        int total_bit_errors = freedv_get_total_bit_errors(freedv);
        fprintf(stderr, 
            "frame: %d  demod sync: %d  nin: %d demod snr: %3.2f dB  bit errors: %d\n",
            frame, sync, nin, (double)snr_est, total_bit_errors);

        frame++;
        nin = freedv_nin(freedv);
    }

    //////
    if (freedv_get_test_frames(freedv)) {
        int Tbits = freedv_get_total_bits(freedv);
        int Terrs = freedv_get_total_bit_errors(freedv);
        fprintf(stderr, "BER......: %5.4f Tbits: %5d Terrs: %5d\n",
	    (double)Terrs/Tbits, Tbits, Terrs);
        if (config_mode == FREEDV_MODE_700D || config_mode == FREEDV_MODE_700E) {
            int Tbits_coded = freedv_get_total_bits_coded(freedv);
            int Terrs_coded = freedv_get_total_bit_errors_coded(freedv);
            fprintf(stderr, "Coded BER: %5.4f Tbits: %5d Terrs: %5d\n",
                    (double)Terrs_coded/Tbits_coded, Tbits_coded, Terrs_coded);
        }
    }

    printf("Done\n");

    close(f_in);
    close(f_out);

    memtools_find_unused(printf);
    printf("\nEnd of Test\n");
}

/* vi:set ts=4 et sts=4: */
