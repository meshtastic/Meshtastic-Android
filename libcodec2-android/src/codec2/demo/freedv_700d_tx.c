/*---------------------------------------------------------------------------*\

  FILE........: freedv_700d_tx.c
  AUTHOR......: David Rowe
  DATE CREATED: April 2021

  Demo transmit program using the FreeDV API (700D mode).
 
  usage:
  
  cd ~/codec2/build_linux
  cat ../raw/ve9qrp_10s.raw | ./demo/freedv_700d_tx | ./demo/freedv_700d_rx | aplay -f S16_LE

  Listen to the modulated Tx signal:
  
  cat ../raw/ve9qrp_10s.raw | ./demo/freedv_700d_tx | aplay -f S16_LE
  
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
#include <string.h>

#include "freedv_api.h"

int main(int argc, char *argv[]) {
    struct freedv            *freedv;

    freedv = freedv_open(FREEDV_MODE_700D);
    assert(freedv != NULL);

    /* handy functions to set buffer sizes */
    int n_speech_samples = freedv_get_n_speech_samples(freedv);
    short speech_in[n_speech_samples];
    int n_nom_modem_samples = freedv_get_n_nom_modem_samples(freedv);
    short mod_out[n_nom_modem_samples];

    /* OK main loop  --------------------------------------- */

    while(fread(speech_in, sizeof(short), n_speech_samples, stdin) == n_speech_samples) {
        freedv_tx(freedv, mod_out, speech_in);
        fwrite(mod_out, sizeof(short), n_nom_modem_samples, stdout);
    }

    freedv_close(freedv);

    return 0;
}
