/*---------------------------------------------------------------------------*\

  FILE........: tst_ofdm_mod.c
  AUTHOR......: David Rowe, Don Reid
  DATE CREATED: 25 June 2018

  Test and profile OFDM modulation on the STM32F4.

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

/* This is a unit test implementation of the OFDM Mod function.
 *
 * Typical run:

    ofdm_gen_test_bits stm_in.raw 6 --rand 

    ofdm_mod stm_in.raw ref_mod_out.raw

    echo "00000000" > stm_cfg.txt

    <Load stm32 and run>

    compare_ints -s -b2 ref_mod_out.raw mod.raw

    ofdm_demod ref_mod_out.raw ref_ofdm_demod.raw --testframes
    ofdm_demod mod.raw stm_demod.raw --testframes

 * For LDPC use:

    ofdm_gen_test_bits stm_in.raw 6 --rand --ldpc

    ofdm_mod stm_in.raw ref_mod_out.raw --ldpc

    echo "00100000" > stm_cfg.txt

    <Load stm32 and run>

    ofdm_demod ref_mod_out.raw ref_ofdm_demod.raw --ldpc --testframes
    ofdm_demod mod.raw stm_demod.raw --ldpc --testframes

 */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>

#include "semihosting.h"
#include "codec2_ofdm.h"
#include "ofdm_internal.h"
#include "ldpc_codes.h"
#include "interldpc.h"
#include "gp_interleaver.h"

#include "stm32f4xx_conf.h"
#include "stm32f4xx.h"
#include "machdep.h"

#include "debug_alloc.h"

int main(int argc, char *argv[]) {
    struct OFDM *ofdm;
    FILE        *fcfg;
    struct LDPC  ldpc;

    // Test configuration, read from stm_cfg.txt
    int          config_verbose;
//    int          config_testframes;
    int          config_ldpc_en;
//    int          config_log_payload_syms;
    int          config_profile;

    int          Nbitsperframe, Nsamperframe;
    int          frame = 0;
    int          i;

    semihosting_init();

    printf("OFDM_mod test and profile\n");

    // Read configuration - a file of '0' or '1' characters
    char config[8];
    fcfg = fopen("stm_cfg.txt", "r");
    if (fcfg == NULL) {
        fprintf(stderr, "Error opening config file\n");
        exit(1);
    }
    if (fread(&config[0], 1, 8, fcfg) != 8) {
        fprintf(stderr, "Error reading config file\n");
        exit(1);
    }
    config_verbose = config[0] - '0';
//    config_testframes = config[1] - '0';
    config_ldpc_en = config[2] - '0';
//    config_log_payload_syms = config[3] - '0';
    config_profile = config[4] - '0';
    fclose(fcfg);

    PROFILE_VAR(ofdm_mod_start);
    if (config_profile) machdep_profile_init();

    struct OFDM_CONFIG *ofdm_config;

    ofdm = ofdm_create(NULL);
    assert(ofdm != NULL);

    /* Get a copy of the actual modem config */
    ofdm_config = ofdm_get_config_param(ofdm);

    ldpc_codes_setup(&ldpc, "HRA_112_112");

    Nbitsperframe = ofdm_get_bits_per_frame(ofdm);
    int Ndatabitsperframe;
    if (config_ldpc_en) {
        Ndatabitsperframe = ldpc.data_bits_per_frame;
    } else {
        Ndatabitsperframe = ofdm_get_bits_per_frame(ofdm) - ofdm->nuwbits - ofdm->ntxtbits;
    }

    Nsamperframe = ofdm_get_samples_per_frame(ofdm);
//    int ofdm_nuwbits = (ofdm_config->ns - 1) * ofdm_config->bps - ofdm_config->txtbits;

    if (config_verbose) {
        ofdm_set_verbose(ofdm, config_verbose);
        fprintf(stderr, "Nsamperframe: %d, Nbitsperframe: %d \n", Nsamperframe, Nbitsperframe);
    }

    int ofdm_ntxtbits =  ofdm_config->txtbits;

    uint8_t tx_bits_char[Ndatabitsperframe];
    int16_t tx_scaled[Nsamperframe];
    uint8_t txt_bits_char[ofdm_ntxtbits];

    for(i=0; i< ofdm_ntxtbits; i++) {
        txt_bits_char[i] = 0;
    }    
    
    if (config_verbose) {
	    ofdm_print_info(ofdm);
    }

    int sin = open("stm_in.raw", O_RDONLY);
    if (sin < 0) {
        printf("Error opening input file\n");
        exit(1);
    }

    int sout = open("mod.raw", O_WRONLY|O_TRUNC|O_CREAT, 0666);
    if (sout < 0) {
        printf("Error opening output file\n");
        exit(1);
    }

    while (read(sin, tx_bits_char, sizeof(char) * Ndatabitsperframe) == Ndatabitsperframe) {
        fprintf(stderr, "Frame %d\n", frame);

        if (config_profile) { PROFILE_SAMPLE(ofdm_mod_start); }

            if (config_ldpc_en) {

                complex float tx_sams[Nsamperframe];
                ofdm_ldpc_interleave_tx(ofdm, &ldpc, tx_sams, tx_bits_char, txt_bits_char);

                for(i=0; i<Nsamperframe; i++) {
                    tx_scaled[i] = crealf(tx_sams[i]);
                }

             } else { // !config_ldpc_en

                uint8_t tx_frame[Nbitsperframe];
                ofdm_assemble_qpsk_modem_packet(ofdm, tx_frame, tx_bits_char, txt_bits_char);

                int tx_bits[Nbitsperframe];
                for(i=0; i<Nbitsperframe; i++) {
                    tx_bits[i] = tx_frame[i];
                }

	        if (config_verbose >=3) {
                    fprintf(stderr, "\ntx_bits:\n");
                    for (i = 0; i < Nbitsperframe; i++) {
                        fprintf(stderr, "  %3d %8d\n", i, tx_bits[i]);
                    }
                }

                COMP tx_sams[Nsamperframe];
                ofdm_mod(ofdm, tx_sams, tx_bits);

	        if (config_verbose >=3) {
                    fprintf(stderr, "\ntx_sams:\n");
                    for (i = 0; i < Nsamperframe; i++) {
                        fprintf(stderr, "  %3d % f\n", i, (double)tx_sams[i].real);
                    }
                }

                for(i=0; i<Nsamperframe; i++) {
                    tx_scaled[i] = tx_sams[i].real;
                }
            }

        if (config_profile) PROFILE_SAMPLE_AND_LOG2(ofdm_mod_start, "  ofdm_mod");

        write(sout, tx_scaled, sizeof(int16_t) * Nsamperframe);

        frame ++;

    }  // while (fread(...

    close(sin);
    close(sout);

    if (config_verbose)
        printf("%d frames processed\n", frame);

    if (config_profile) {
        printf("\nStart Profile Data\n");
        machdep_profile_print_logged_samples();
        printf("End Profile Data\n");
        }

    printf("\nEnd of Test\n");
    fclose(stdout);
    fclose(stderr);

    return 0;
}

/* vi:set ts=4 et sts=4: */
