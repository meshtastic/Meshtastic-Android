/*---------------------------------------------------------------------------*\

  FILE........: freedv_700d_rx.c
  AUTHOR......: David Rowe
  DATE CREATED: April 2021

  Demo receive program for FreeDV API (700D mode), see freedv_700d_tx.c for
  instructions.
 
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

#include "freedv_api.h"

int main(int argc, char *argv[]) {
    struct freedv *freedv;

    freedv = freedv_open(FREEDV_MODE_700D);
    assert(freedv != NULL);

    /* note API functions to tell us how big our buffers need to be */
    short speech_out[freedv_get_n_max_speech_samples(freedv)];
    short demod_in[freedv_get_n_max_modem_samples(freedv)];

    size_t nin,nout;
    nin = freedv_nin(freedv);
    while(fread(demod_in, sizeof(short), nin, stdin) == nin) {
        nout = freedv_rx(freedv, speech_out, demod_in);
        nin = freedv_nin(freedv); /* call me on every loop! */
        fwrite(speech_out, sizeof(short), nout, stdout);
    }

    freedv_close(freedv);
    return 0;
}
