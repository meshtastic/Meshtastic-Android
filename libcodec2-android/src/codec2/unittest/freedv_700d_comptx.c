/*---------------------------------------------------------------------------*\

  freedv_comptx.c

  Complex valued Tx to support ctests.

\*---------------------------------------------------------------------------*/

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
    COMP mod_out[n_nom_modem_samples];
    short mod_out_short[2*n_nom_modem_samples];

    /* OK main loop  --------------------------------------- */

    while(fread(speech_in, sizeof(short), n_speech_samples, stdin) == n_speech_samples) {
        freedv_comptx(freedv, mod_out, speech_in);
        for(int i=0; i<n_nom_modem_samples; i++) {
            mod_out_short[2*i] = mod_out[i].real;
            mod_out_short[2*i+1] = mod_out[i].imag;
        }
        fwrite(mod_out_short, sizeof(short), 2*n_nom_modem_samples, stdout);
    }

    freedv_close(freedv);

    return 0;
}
