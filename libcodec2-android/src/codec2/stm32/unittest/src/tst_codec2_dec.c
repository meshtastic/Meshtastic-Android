/*------------------------------------------------------

  FILE........: tst_codec2_dec.c
  AUTHOR......: David Rowe, Don Reid
  DATE CREATED: 30 May 2013, Oct 2018

  Test Codec 2 decoding on the STM32F4.

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
    c2enc 700C spch_in.raw stm_in.raw

    # Reference
    c2dec 700C stm_in.raw ref_dec.raw
    
    # Create config
    echo "81000000" > stm_cfg.txt

    # Run stm32
    run_stm32_prog ../../src/tst_codec2_dec.elf --load

    # Compare outputs
    compare_ints -s -b 2 ref_dec.raw stm_out.raw

    # Manual play (and listen)
    aplay -f S16_LE  ref_dec.raw
    #
    aplay -f S16_LE stm_out.raw


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
#include <assert.h>

#include "codec2.h"

#include "semihosting.h"
#include "stm32f4xx_conf.h"
#include "stm32f4xx.h"
#include "machdep.h"
    

static char fin_buffer[1024];
static __attribute__ ((section (".ccm"))) char fout_buffer[4*8192];

int main(int argc, char *argv[]) {
    int            f_cfg;
    int            frame;
    void          *codec2;
    short         *buf;
    unsigned char *bits;

    int            nsam, nbit, nbyte;

    semihosting_init();

    ////////
    // Test configuration, read from stm_cfg.txt
    int     config_mode;        // 0
    int     config_gray;        // 1
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
    config_gray = config[1] - '0';
    //config_verbose = config[6] - '0';
    //config_profile = config[7] - '0';
    close(f_cfg);


    ////////
    // Setup
    codec2 = codec2_create(config_mode);
    assert(codec2 != NULL);
    codec2_set_natural_or_gray(codec2, config_gray);

    nsam = codec2_samples_per_frame(codec2);
    nbit = codec2_bits_per_frame(codec2);
    buf = (short*)malloc(nsam*sizeof(short));
    nbyte = (nbit + 7) / 8;
    bits = (unsigned char*)malloc(nbyte*sizeof(char));


    ////////
    // Streams
    FILE* fin = fopen("stm_in.raw", "rb");
    if (fin == NULL) {
        perror("Error opening input file\n");
        exit(1);
    }
    setvbuf(fin, fin_buffer,_IOFBF,sizeof(fin_buffer));

    FILE *fout = fopen("stm_out.raw", "wb" );
    if (fout == NULL) {
        perror("Error opening output file\n");
        exit(1);
    }
    setvbuf(fout, fout_buffer,_IOFBF,sizeof(fout_buffer));

    frame = 0;

    ////////
    // Main loop
    int bytes_per_frame = (sizeof(char) * nbyte);
    while (fread(bits, 1, bytes_per_frame, fin) == (size_t)bytes_per_frame) {

        codec2_decode_ber(codec2, buf, bits, 0.0);
 	
        fwrite(buf, sizeof(short) , nsam, fout);

        frame ++;
        }


    fclose(fin);
    fclose(fout);

    printf("\nEnd of Test\n");
    fclose(stdout);
    fclose(stderr);

}

/* vi:set ts=4 et sts=4: */
