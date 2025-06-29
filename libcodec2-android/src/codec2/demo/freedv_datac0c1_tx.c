/*---------------------------------------------------------------------------*\

  FILE........: freedv_datac0c1_tx.c
  AUTHOR......: David Rowe
  DATE CREATED: Dec 2021

  Transmitting alternate frames of two different raw data modes.  See
  freedv_datac0c1_rx.c
  
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

#include "freedv_api.h"
#include "ofdm_internal.h"

#define FRAMES 10

void send_burst(struct freedv *freedv);

int main(void) {
    struct freedv *freedv_c0, *freedv_c1;

    freedv_c0 = freedv_open(FREEDV_MODE_DATAC0); assert(freedv_c0 != NULL);
    freedv_c1 = freedv_open(FREEDV_MODE_DATAC1); assert(freedv_c1 != NULL);

    // send frames in different modes in random order
    int c0_frames = 0;
    int c1_frames = 0;
    while ((c0_frames < FRAMES) || (c1_frames < FRAMES)) {
        if (rand() & 1) {
            if (c0_frames < FRAMES) {
                send_burst(freedv_c0);
                c0_frames++;
            }
        } else { 
            if (c1_frames < FRAMES) {
                send_burst(freedv_c1);
                c1_frames++;
            }
        }
    }

    freedv_close(freedv_c0);
    freedv_close(freedv_c1);

    return 0;
}


void send_burst(struct freedv *freedv) {
    size_t bits_per_frame = freedv_get_bits_per_modem_frame(freedv);
    size_t bytes_per_modem_frame = bits_per_frame/8;
    size_t payload_bytes_per_modem_frame = bytes_per_modem_frame - 2; /* 16 bits used for the CRC */
    size_t n_mod_out = freedv_get_n_tx_modem_samples(freedv);
    uint8_t bytes_in[bytes_per_modem_frame];
    short mod_out_short[n_mod_out];

    /* generate a test frame */
    uint8_t testframe_bits[bits_per_frame];
    ofdm_generate_payload_data_bits(testframe_bits, bits_per_frame);
    freedv_pack(bytes_in, testframe_bits, bits_per_frame);
    
    /* send preamble */
    int n_preamble = freedv_rawdatapreambletx(freedv, mod_out_short);
    fwrite(mod_out_short, sizeof(short), n_preamble, stdout);
        
    /* The raw data modes require a CRC in the last two bytes */
    uint16_t crc16 = freedv_gen_crc16(bytes_in, payload_bytes_per_modem_frame);
    bytes_in[bytes_per_modem_frame-2] = crc16 >> 8;
    bytes_in[bytes_per_modem_frame-1] = crc16 & 0xff;

    /* modulate and send a data frame */
    freedv_rawdatatx(freedv, mod_out_short, bytes_in);
    fwrite(mod_out_short, sizeof(short), n_mod_out, stdout);
                    
    /* send postamble */
    int n_postamble = freedv_rawdatapostambletx(freedv, mod_out_short);
    fwrite(mod_out_short, sizeof(short), n_postamble, stdout);

    /* create some silence between bursts */
    int inter_burst_delay_ms = 200;
    int samples_delay = FREEDV_FS_8000*inter_burst_delay_ms/1000;
    short sil_short[samples_delay];
    for(int i=0; i<samples_delay; i++) sil_short[i] = 0;
    fwrite(sil_short, sizeof(short), samples_delay, stdout);
}
