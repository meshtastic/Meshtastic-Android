/*---------------------------------------------------------------------------*\

  FILE........: tst_codec2_enc.c, (derived from codec2_profile.c)
  AUTHOR......: David Rowe, Don Reid
  DATE CREATED: 30 May 2013, Oct 2018

  Test Codec 2 encoding on the STM32F4.

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

/* This is a unit test implementation of the Codec2_encode function.
 *
 * Typical run:

    # Copy N frames of a raw audio file to stm_in.raw. 
    dd bs=2560 count=30 if=../../../../raw/hts1.raw of=stm_in.raw

    # Run x86 command for reference output
    c2enc 700C stm_in.raw ref_enc.raw 

    # Create config
    echo "80000000" > stm_cfg.txt

    # Run stm32
    run_stm32_prog ../../src/tst_codec2_enc.elf --load

    # Compare outputs
    comare_ints -b 1 ref_enc.raw stm_out.raw

    # Manual play (and listen)
    c2dec 700C ref_enc.raw ref_dec.raw
    aplay -f S16_LE  ref_out.raw
    #
    c2dec 700C stm_out.raw stm_dec.raw
    aplay -f S16_LE stm_dec.raw

 */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <math.h>

#include "codec2.h"

#include "stm32f4xx_conf.h"
#include "stm32f4xx.h"
#include "semihosting.h"
#include "machdep.h"

static __attribute__ ((section (".ccm"))) char fin_buffer[8*8192];
char fout_buffer[1024];


int main(int argc, char *argv[]) {
    int            f_cfg;

    struct CODEC2  *codec2;
    short          *buf;
    unsigned char  *bits;
    int             nsam, nbit, nbyte;
    int             gray;
    int             frame;

    ////////
    // Semihosting
    semihosting_init();

    ////////
    // Test configuration, read from stm_cfg.txt
    int     config_mode;        // 0
    //int     config_verbose;     // 6
    //int     config_profile;     // 7
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
    //config_verbose = config[6] - '0';
    //config_profile = config[7] - '0';
    close(f_cfg);

    ////////
    //PROFILE_VAR(freedv_start);
    //machdep_profile_init();

    ////////
    codec2 = codec2_create(config_mode);
    nsam = codec2_samples_per_frame(codec2);
    nbit = codec2_bits_per_frame(codec2);
    buf = (short*)malloc(nsam*sizeof(short));
    nbyte = (nbit + 7) / 8;
    bits = (unsigned char*)malloc(nbyte*sizeof(char));

    gray = 1; 
    //softdec = 0; 
    //bitperchar = 0;

    codec2_set_natural_or_gray(codec2, gray);

    ////////
    // Streams
    FILE* fin = fopen("stm_in.raw", "rb");
    if (fin == NULL) {
        perror("Error opening input file\n");
        exit(1);
    }
    setvbuf(fin, fin_buffer,_IOFBF,sizeof(fin_buffer));

    FILE* fout = fopen("stm_out.raw", "wb");
    if (fout == NULL) {
        perror("Error opening output file\n");
        exit(1);
    }
   
    frame = 0;

    int bytes_per_frame = (sizeof(short) * nsam);
    while (fread(buf,1, bytes_per_frame, fin) == bytes_per_frame) {

        //PROFILE_SAMPLE(enc_start);
        codec2_encode(codec2, bits, buf);
        //PROFILE_SAMPLE_AND_LOG2(, enc_start, "  enc");

        fwrite(bits, 1, (sizeof(char) * nbyte), fout);
        printf("frame: %d\n", ++frame);

        //machdep_profile_print_logged_samples();
    }

    codec2_destroy(codec2);

    free(buf);
    free(bits);
    fclose(fin);
    fclose(fout);

    printf("\nEnd of Test\n");
    fclose(stdout);
    fclose(stderr);

    return(0);
}

/* vi:set ts=4 et sts=4: */
