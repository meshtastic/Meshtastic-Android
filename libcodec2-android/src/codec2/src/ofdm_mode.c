/*---------------------------------------------------------------------------*\

  FILE........: ofdm_mode.c
  AUTHORS.....: David Rowe & Steve Sampson
  DATE CREATED: July 2020

  Mode specific configuration for OFDM modem.

\*---------------------------------------------------------------------------*/

#include <assert.h>
#include <string.h>
#include "codec2_ofdm.h"
#include "ofdm_internal.h"

void ofdm_init_mode(char mode[], struct OFDM_CONFIG *config) {
    assert(mode != NULL);
    assert(config != NULL);

    assert(strlen(mode) < 16);
    strcpy(config->mode, mode);

    /* Fill in default values - 700D */

    config->nc = 17;                            /* Number of carriers */
    config->np = 1;
    config->ns = 8;                             /* Number of Symbols per modem frame */
    config->ts = 0.018f;
    config->tcp = .002f;                        /* Cyclic Prefix duration */
    config->tx_centre = 1500.0f;                /* TX Carrier Frequency */
    config->rx_centre = 1500.0f;                /* RX Carrier Frequency */
    config->fs = 8000.0f;                       /* Sample rate */
    config->txtbits = 4;
    config->bps = 2;                            /* Bits per Symbol */
    config->nuwbits = 5 * config->bps;          /* default is 5 symbols of Unique Word bits */
    config->bad_uw_errors = 3;
    config->ftwindowwidth = 32;
    config->timing_mx_thresh = 0.30f;
    config->edge_pilots = 1;
    config->state_machine = "voice1";
    config->data_mode = "";
    config->codename = "HRA_112_112";
    config->clip_gain1 = 2.5;
    config->clip_gain2 = 0.8;
    config->clip_en = false;
    config->tx_bpf_en = true;
    config->amp_scale = 245E3;
    config->foff_limiter = false;
    memset(config->tx_uw, 0, MAX_UW_BITS);

    if (strcmp(mode,"700D") == 0) {
    } else if (strcmp(mode,"700E") == 0) {
         config->ts = 0.014;  config->tcp = 0.006; config->nc = 21; config->ns=4;
         config->edge_pilots = 0;
         config->nuwbits = 12; config->bad_uw_errors = 3; config->txtbits = 2;
         config->state_machine = "voice2"; config->amp_est_mode = 1;
         config->ftwindowwidth = 80;
         config->codename = "HRA_56_56"; config->tx_bpf_en = false;
         config->foff_limiter = true;
         config->amp_scale = 155E3; config->clip_gain1 = 3; config->clip_gain2 = 0.8;
    } else if ((strcmp(mode,"2020") == 0)) {
         config->ts = 0.0205;  config->nc = 31; config->codename = "HRAb_396_504";
         config->tx_bpf_en = false; config->amp_scale = 167E3; config->clip_gain1 = 2.5; config->clip_gain2 = 0.8;
    } else if (strcmp(mode,"2020B") == 0) {
         config->ts = 0.014;  config->tcp = 0.004; config->nc = 29; config->ns=5; config->codename = "HRA_56_56";
         config->txtbits = 4; config->nuwbits = 8*2; config->bad_uw_errors = 5;
         config->tx_bpf_en = false; config->amp_scale = 130E3; config->clip_gain1 = 2.5; config->clip_gain2 = 0.8;
         config->edge_pilots = 0; config->state_machine = "voice2";
         config->ftwindowwidth = 64; config->foff_limiter = true;
    } else if (strcmp(mode,"qam16") == 0) {
        config->ns=5; config->np=5; config->tcp = 0.004; config->ts = 0.016; config->nc = 33;
        config->bps=4; config->txtbits = 0; config->nuwbits = 15*4; config->bad_uw_errors = 5;
        config->ftwindowwidth = 32; config->state_machine = "data"; config->amp_est_mode = 1;
        config->tx_bpf_en = false;
        config->data_mode = "streaming";
    } else if (strcmp(mode,"datac0") == 0) {
        config->ns=5; config->np=4; config->tcp = 0.006; config->ts = 0.016; config->nc = 9;
        config->edge_pilots = 0;
        config->txtbits = 0; config->nuwbits = 32; config->bad_uw_errors = 9;
        config->state_machine = "data"; config->amp_est_mode = 1;
        config->ftwindowwidth = 80; config->codename = "H_128_256_5";
        uint8_t uw[] = {1,1,0,0, 1,0,1,0,  1,1,1,1, 0,0,0,0};
        memcpy(config->tx_uw, uw, sizeof(uw));
        config->timing_mx_thresh = 0.08f;    
        config->data_mode = "streaming";
        config->amp_scale = 300E3; config->clip_gain1 = 2.2; config->clip_gain2 = 0.8;
        config->tx_bpf_en = true; config->clip_en = true;
    } else if (strcmp(mode,"datac1") == 0) {
        config->ns=5; config->np=38; config->tcp = 0.006; config->ts = 0.016; config->nc = 27;
        config->edge_pilots = 0;
        config->txtbits = 0; config->nuwbits = 16; config->bad_uw_errors = 6;
        config->state_machine = "data"; config->amp_est_mode = 1; 
        config->ftwindowwidth = 80; config->codename = "H_4096_8192_3d";
        uint8_t uw[] = {1,1,0,0, 1,0,1,0,  1,1,1,1, 0,0,0,0};
        assert(sizeof(uw) == config->nuwbits);
        memcpy(config->tx_uw, uw, config->nuwbits);
        config->timing_mx_thresh = 0.10f;    
        config->data_mode = "streaming";
        // WIP but for now just let SSB filter do BPF of clipped signal
        //config->amp_scale = 1253; config->clip_gain1 = 2.5; config->clip_gain2 = 0.8;
        config->tx_bpf_en = false; config->clip_en = false;
    } else if (strcmp(mode,"datac3") == 0) {
        config->ns=5; config->np=29; config->tcp = 0.006; config->ts = 0.016; config->nc = 9;
        config->edge_pilots = 0;
        config->txtbits = 0; config->state_machine = "data";
        config->ftwindowwidth = 80; config->timing_mx_thresh = 0.10;
        config->codename = "H_1024_2048_4f"; config->amp_est_mode = 1;
        /* custom UW - we use a longer UW with higher bad_uw_errors threshold due to high raw BER */
        config->nuwbits = 40; config->bad_uw_errors = 10;
        uint8_t uw[] = {1,1,0,0, 1,0,1,0,  1,1,1,1, 0,0,0,0, 1,1,1,1, 0,0,0,0};
        assert(sizeof(uw) <= MAX_UW_BITS);
        memcpy(config->tx_uw, uw, sizeof(uw));
        memcpy(&config->tx_uw[config->nuwbits-sizeof(uw)], uw, sizeof(uw));
        config->data_mode = "streaming";
        config->amp_scale = 300E3; config->clip_gain1 = 2.2; config->clip_gain2 = 0.8;
        config->tx_bpf_en = true; config->clip_en = true;
     }
    else {
        assert(0);
    }
    config->rs=1.0f/config->ts;
}
