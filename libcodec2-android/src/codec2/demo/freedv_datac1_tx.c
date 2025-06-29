/*---------------------------------------------------------------------------*\

  FILE........: freedv_datac1_tx.c
  AUTHOR......: David Rowe
  DATE CREATED: April 2021

  Demonstrates transmitting frames of raw data bytes using the FreeDV API datac1
  mode.  The data on stdin is transmitted as a sequence of modulated bursts. 

  Format of each burst: ...|preamble|data frame|postamble|silence|....
  
  There is just one data frame per burst in this demo.
  
  usage:
  
  cd codec2/build_linux
  head -c $((510*10)) </dev/urandom > binaryIn.bin
  cat binaryIn.bin | ./demo/freedv_datac1_tx | ./demo/freedv_datac1_rx > binaryOut.bin
  diff binaryIn.bin binaryOut.bin
  
  Listen to the modulated Tx signal:
  
  cat binaryIn.bin | ./demo/freedv_datac1_tx | aplay -f S16_LE
  
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

int main(int argc, char *argv[]) {
    struct freedv            *freedv;

    freedv = freedv_open(FREEDV_MODE_DATAC1);
    assert(freedv != NULL);

    size_t bytes_per_modem_frame = freedv_get_bits_per_modem_frame(freedv)/8;
    size_t payload_bytes_per_modem_frame = bytes_per_modem_frame - 2; /* 16 bits used for the CRC */
    size_t n_mod_out = freedv_get_n_tx_modem_samples(freedv);
    uint8_t bytes_in[bytes_per_modem_frame];
    short mod_out_short[n_mod_out];
    
    for(int b=0; b<10; b++) {
        /* send preamble */
        int n_preamble = freedv_rawdatapreambletx(freedv, mod_out_short);
        fwrite(mod_out_short, sizeof(short), n_preamble, stdout);
        
        /* read our input data frame from stdin */
        size_t nread = fread(bytes_in, sizeof(uint8_t), payload_bytes_per_modem_frame, stdin);
        if (nread != payload_bytes_per_modem_frame) break;

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

    freedv_close(freedv);

    return 0;
}
