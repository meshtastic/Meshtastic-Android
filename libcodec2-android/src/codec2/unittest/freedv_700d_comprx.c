/*---------------------------------------------------------------------------*\

  FILE........: freedv_700d_comprx.c
  AUTHOR......: David Rowe
  DATE CREATED: July 2022

  Complex valued rx to support ctests.  Includes a few operations that will
  only work if complex Tx and Rx signals are being handled correctly.
 
\*---------------------------------------------------------------------------*/

#include <assert.h>
#include <stdlib.h>
#include <stdio.h>

#include "freedv_api.h"
#include "freedv_api_internal.h"
#include "ofdm_internal.h"
#include "codec2_cohpsk.h"
#include "comp_prim.h"

int main(int argc, char *argv[]) {

    /* with no arguments then run with no test code */
    int test_num = 0;
    if (argc == 2) {
        if (strcmp(argv[1],"tx") == 0) {
            test_num = 1;
        }
        if (strcmp(argv[1],"rx") == 0) {
            test_num = 2;
        }
    }
    fprintf(stderr,"%d\n", test_num);
    
    struct freedv *freedv;
    freedv = freedv_open(FREEDV_MODE_700D);
    assert(freedv != NULL);

    /* note API functions to tell us how big our buffers need to be */
    short speech_out[freedv_get_n_max_speech_samples(freedv)];
    short demod_in[2*freedv_get_n_max_modem_samples(freedv)];
    COMP  demod_in_comp[2*freedv_get_n_max_modem_samples(freedv)];
    
    /* set up small freq offset */
    float foff_hz = 25;
    COMP phase_ch; phase_ch.real = 1.0; phase_ch.imag = 0.0;

    /* set complex sine wave interferer at -fc */
    COMP interferer_phase = {1.0,0.0};
    COMP interferer_freq;
    interferer_freq.real = cos(2.0*M_PI*freedv->ofdm->tx_centre/FREEDV_FS_8000);
    interferer_freq.imag = sin(2.0*M_PI*freedv->ofdm->tx_centre/FREEDV_FS_8000);
    interferer_freq = cconj(interferer_freq);

    /* log a file of demod input samples for plotting in Octave */
    FILE *fdemod = fopen("demod.f32","wb"); assert(fdemod != NULL);

    /* measure demod input power, interferer input power */
    float power_d = 0.0; float power_interferer = 0.0; 

    int frames = 0, sum_sync = 0, frames_snr = 0; float sum_snr = 0.0;
    size_t nin, nout;
    nin = freedv_nin(freedv);

    while(fread(demod_in, sizeof(short), 2*nin, stdin) == 2*nin) {
        for(int i=0; i<nin; i++) {
            demod_in_comp[i].real = (float)demod_in[2*i];
            demod_in_comp[i].imag = (float)demod_in[2*i+1];
            //demod_in_comp[i].imag = 0;
        }

        if (test_num == 1) {
            /* So Tx is a complex OFDM signal centered at +fc.  A small
               shift fd followed by Re{} will only work if Tx is complex.
               If Tx is real, neg freq components at -fc+fd will be
               aliased on top of fc+fd wanted signal by Re{} operation.
               This can be tested by setting demod_in_comp[i].imag = 0
               above */
            fdmdv_freq_shift_coh(demod_in_comp, demod_in_comp, foff_hz, FREEDV_FS_8000, &phase_ch, nin);
            for(int i=0; i<nin; i++)
                demod_in_comp[i].imag = 0.0;
        }
    
        if (test_num == 2) {
            /* a complex sinewave (carrier) at -fc will only be ignored if
               Rx is treating signal as complex, otherwise if real a +fc
               alias will appear in the middle of our wanted signal at
               +fc, this can be tested by setting demod_in_comp[i].imag =
               0 below */
            for(int i=0; i<nin; i++) {
                COMP a = fcmult(2E4,interferer_phase);
                interferer_phase = cmult(interferer_phase, interferer_freq);
                power_interferer += a.real*a.real + a.imag*a.imag;
                COMP d = demod_in_comp[i];
                power_d += d.real*d.real + d.imag*d.imag;
                demod_in_comp[i] = cadd(d,a);
                //demod_in_comp[i].imag = 0;
            }
        }
        
        /* useful to take a look at this with Octave */
        fwrite(demod_in_comp, sizeof(COMP), nin, fdemod);
        
        nout = freedv_comprx(freedv, speech_out, demod_in_comp);
        nin = freedv_nin(freedv); /* call me on every loop! */
        fwrite(speech_out, sizeof(short), nout, stdout);
        int sync; float snr_est;
        freedv_get_modem_stats(freedv, &sync, &snr_est);
        fprintf(stderr, "sync: %d  snr_est: %f\n", sync, snr_est);
        frames++; sum_sync += sync; if (sync) { sum_snr += snr_est; frames_snr++; }
    }

    fclose(fdemod);
    freedv_close(freedv);

    if (test_num == 2)
        fprintf(stderr, "Demod/Interferer power ratio: %3.2f dB\n", 10*log10(power_d/power_interferer));
    float snr_av = sum_snr/frames_snr;
    fprintf(stderr, "frames: %d sum_sync: %d snr_av: %3.2f dB\n", frames, sum_sync, snr_av);

    if (snr_av > 10.0)
        return 0;
    else
        return 1;
}
