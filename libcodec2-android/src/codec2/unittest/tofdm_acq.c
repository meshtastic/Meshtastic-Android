/*---------------------------------------------------------------------------*\

  FILE........: tofdm_acq.c
  AUTHORS.....: David Rowe
  DATE CREATED: Mar 2021

  Tests for the acquistion (sync) parts of the C version of the OFDM modem. 
  This program outputs a file of Octave vectors that are loaded and 
  automatically tested against the Octave version of the modem by the Octave 
  script tofdm_acq.m

\*---------------------------------------------------------------------------*/

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>

#include "ofdm_internal.h"
#include "codec2_ofdm.h"
#include "octave.h"

#define MAX_FRAMES 500

int main(int argc, char *argv[])
{
    struct OFDM *ofdm;
    struct OFDM_CONFIG ofdm_config;

    ofdm_init_mode("datac0", &ofdm_config);
    ofdm = ofdm_create(&ofdm_config);
    ofdm->data_mode = "burst";
    ofdm->verbose = 2;
    ofdm->timing_mx_thresh = 0.15;
    ofdm->postambledetectoren = 1;
    assert(ofdm != NULL);
    
    int nin = ofdm_get_nin(ofdm);
    int rxbufst = ofdm->rxbufst;
    
    FILE *fin = fopen(argv[1],"rb"); assert(fin != NULL);
    short rx_scaled[ofdm_get_max_samples_per_frame(ofdm)];
    int f = 0;
    
    float timing_mx_log[MAX_FRAMES];
    int ct_est_log[MAX_FRAMES];
    float foff_est_log[MAX_FRAMES];
    int timing_valid_log[MAX_FRAMES];
    int nin_log[MAX_FRAMES];
    
    while (fread(rx_scaled, sizeof (short), nin, fin) == nin) {
        fprintf(stderr, "%3d ", f);
        ofdm_sync_search_shorts(ofdm, rx_scaled, ofdm->amp_scale / 2.0f);

        if (f < MAX_FRAMES) {
            timing_mx_log[f] = ofdm->timing_mx;
            ct_est_log[f] = ofdm->ct_est;
            foff_est_log[f] = ofdm->foff_est_hz;
            timing_valid_log[f] = ofdm->timing_valid;
            nin_log[f] = ofdm->nin;
        }
        f++;
        
        // reset these to defaults, as they get modified when timing_valid asserted
        ofdm->nin = nin;
        ofdm->rxbufst = rxbufst;
    }
    fclose(fin);
       
    /*---------------------------------------------------------*\
               Dump logs to Octave file for evaluation
                      by tofdm_acq.m Octave script
    \*---------------------------------------------------------*/

    FILE *fout = fopen("tofdm_acq_out.txt","wt");
    assert(fout != NULL);
    fprintf(fout, "# Created by tofdm_acq.c\n");
    octave_save_complex(fout, "tx_preamble_c", (COMP*)ofdm->tx_preamble, 1, ofdm->samplesperframe, ofdm->samplesperframe);
    octave_save_complex(fout, "tx_postamble_c", (COMP*)ofdm->tx_postamble, 1, ofdm->samplesperframe, ofdm->samplesperframe);
    octave_save_float(fout, "timing_mx_log_c", timing_mx_log, 1, f, f);
    octave_save_float(fout, "foff_est_log_c", foff_est_log, 1, f, f);
    octave_save_int(fout, "ct_est_log_c", ct_est_log, 1, f);
    octave_save_int(fout, "timing_valid_log_c", timing_valid_log, 1, f);
    octave_save_int(fout, "nin_log_c", nin_log, 1, f);
    fclose(fout);

    ofdm_destroy(ofdm);

    return 0;
}
