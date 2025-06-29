/*---------------------------------------------------------------------------*\

  FILE........: freedv_datac0c1_rx.c
  AUTHOR......: David Rowe
  DATE CREATED: Dec 2021

  Demonstrates receiving frames of raw data bytes using the FreeDV
  API.  Two parallel receivers are running, so we can receive either
  DATAC0 or DATAC1 frames.  Demonstrates a common use case for HF data
  - the ability to receive signalling as well as payload data frames.

  usage: 

  cd codec2/build_linux
  ./demo/freedv_datacc01_tx | ./demo/freedv_datac0c1_rx

  Give it a hard time with some channel noise, frequency offset, and sample 
  clock offsets:

  ./demo/freedv_datac0c1_tx | ./src/cohpsk_ch - - -24 -f 20 --Fs 8000 | 
  sox -t .s16 -c 1 -r 8000 - -t .s16 -c 1 -r 8008 - | 
  ./demo/freedv_datac0c1_rx

  Replace the final line with "aplay -f S16" to listen to the
  simulated Rx signal.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2021 David Rowe

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
#include <stdint.h>
#include <string.h>

#include "freedv_api.h"

#define NBUF 160

int run_receiver(struct freedv *freedv, short buf[], short demod_in[], int *pn, uint8_t bytes_out[]);

int main(int argc, char *argv[]) {

    // set up DATAC0 Rx
    struct freedv *freedv_c0 = freedv_open(FREEDV_MODE_DATAC0);
    assert(freedv_c0 != NULL);
    freedv_set_frames_per_burst(freedv_c0, 1);
    freedv_set_verbose(freedv_c0, 0);
    int bytes_per_modem_frame_c0 = freedv_get_bits_per_modem_frame(freedv_c0)/8;
    uint8_t bytes_out_c0[bytes_per_modem_frame_c0];
    short  demod_in_c0[freedv_get_n_max_modem_samples(freedv_c0)];
    
    // set up DATAC1 Rx
    struct freedv *freedv_c1 = freedv_open(FREEDV_MODE_DATAC1);
    assert(freedv_c1 != NULL);
    freedv_set_frames_per_burst(freedv_c1, 1);
    freedv_set_verbose(freedv_c1, 0);
    int bytes_per_modem_frame_c1 = freedv_get_bits_per_modem_frame(freedv_c1)/8;
    uint8_t bytes_out_c1[bytes_per_modem_frame_c1];
    short  demod_in_c1[freedv_get_n_max_modem_samples(freedv_c1)];

    // number of samples in demod_in buffer for each Rx
    int n_c0 = 0;
    int n_c1 = 0;
    // number of frames received in each mode
    int c0_frames = 0;
    int c1_frames = 0;

    short buf[NBUF];

    // read a fixed buffer from stdin, use that to fill c0 and c1 demod_in buffers
    while(fread(buf, sizeof(short), NBUF, stdin) == NBUF) {
        
        if (run_receiver(freedv_c0, buf, demod_in_c0, &n_c0, bytes_out_c0)) {
            fprintf(stderr, "DATAC0 frame received!\n");
            c0_frames++;
        }
        if (run_receiver(freedv_c1, buf, demod_in_c1, &n_c1, bytes_out_c1)) {
            fprintf(stderr, "DATAC1 frame received!\n");
            c1_frames++;
        }
        
    }

    fprintf(stderr, "DATAC0 Frames: %d DATAC1 Frames: %d\n", c0_frames, c1_frames);

    freedv_close(freedv_c0);
    freedv_close(freedv_c1);

    return 0;
}

int run_receiver(struct freedv *freedv, short buf[], short demod_in[], int *pn, uint8_t bytes_out[]) {
    int n = *pn;
    int nbytes_out = 0;
    int nin;
    
    // NBUF new samples into DATAC1 Rx
    memcpy(&demod_in[n], buf, sizeof(short)*NBUF);
    n += NBUF; assert(n <= freedv_get_n_max_modem_samples(freedv));
    nin = freedv_nin(freedv);
    while (n > nin) {
        nbytes_out = freedv_rawdatarx(freedv, bytes_out, demod_in);
        // nin samples were read
        n -= nin; assert(n >= 0);
        memmove(demod_in, &demod_in[nin], sizeof(short)*n);
        nin = freedv_nin(freedv);
    }

    *pn = n;
    return nbytes_out;
}
