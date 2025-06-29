/*---------------------------------------------------------------------------*\

  FILE........: freedv_datac1_rx.c
  AUTHOR......: David Rowe
  DATE CREATED: April 2021

  Demonstrates receiving frames of raw data bytes using the FreeDV API.

  See freedv_datac1_tx.c for instructions.
  
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
    struct freedv             *freedv;

    freedv = freedv_open(FREEDV_MODE_DATAC1);
    assert(freedv != NULL);
    freedv_set_frames_per_burst(freedv, 1);
    freedv_set_verbose(freedv, 2);
    
    int bytes_per_modem_frame = freedv_get_bits_per_modem_frame(freedv)/8;
    uint8_t bytes_out[bytes_per_modem_frame];
    short  demod_in[freedv_get_n_max_modem_samples(freedv)];

    size_t nin, nbytes_out;
    nin = freedv_nin(freedv);
    while(fread(demod_in, sizeof(short), nin, stdin) == nin) {

        nbytes_out = freedv_rawdatarx(freedv, bytes_out, demod_in);    
        nin = freedv_nin(freedv); /* must call this every loop */
        if (nbytes_out) {
            /* don't output CRC */
            fwrite(bytes_out, sizeof(uint8_t), nbytes_out-2, stdout);
        }
    }

    freedv_close(freedv);

    return 0;
}
