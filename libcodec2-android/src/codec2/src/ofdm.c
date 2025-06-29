/*---------------------------------------------------------------------------*\

  FILE........: ofdm.c
  AUTHORS.....: David Rowe & Steve Sampson
  DATE CREATED: June 2017

  A Library of functions that implement a PSK OFDM modem, C port of
  the Octave functions in ofdm_lib.m

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2017-2020 David Rowe

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

#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <assert.h>
#include <complex.h>

#include "comp.h"
#include "ofdm_internal.h"
#include "codec2_ofdm.h"
#include "filter.h"
#include "wval.h"
#include "debug_alloc.h"
#include "machdep.h"

#ifdef __EMBEDDED__
#include "codec2_math.h"
#endif /* __EMBEDDED__ */

/* Static Prototypes */

static float cnormf(complex float);
static void allocate_tx_bpf(struct OFDM *);
static void deallocate_tx_bpf(struct OFDM *);
static void dft(struct OFDM *, complex float *, complex float *);
static void idft(struct OFDM *, complex float *, complex float *);
static complex float vector_sum(complex float *, int);
static int est_timing(struct OFDM *, complex float *, int, int, float *, int *, int);
static float est_freq_offset_pilot_corr(struct OFDM *, complex float *, int, int);
static int ofdm_sync_search_core(struct OFDM *);
static void ofdm_demod_core(struct OFDM *, int *);

/* Defines */

#define max( a, b ) ( ((a) > (b)) ? (a) : (b) )
#define min( a, b ) ( ((a) < (b)) ? (a) : (b) )

/*
 * QPSK Quadrant bit-pair values - Gray Coded
 */
static const complex float qpsk[] = {
    1.0f + 0.0f * I,
    0.0f + 1.0f * I,
    0.0f - 1.0f * I,
    -1.0f + 0.0f * I
};

static const complex float qam16[] = {
   1.0f + 1.0f * I,
   1.0f + 3.0f * I,
   3.0f + 1.0f * I,
   3.0f + 3.0f * I,
   1.0f - 1.0f * I,
   1.0f - 3.0f * I,
   3.0f - 1.0f * I,
   3.0f - 3.0f * I,
  -1.0f + 1.0f * I,
  -1.0f + 3.0f * I,
  -3.0f + 1.0f * I,
  -3.0f + 3.0f * I,
  -1.0f - 1.0f * I,
  -1.0f - 3.0f * I,
  -3.0f - 1.0f * I,
  -3.0f - 3.0f * I
};

/*
 * These pilots are compatible with Octave version
 */
static const int8_t pilotvalues[] = {
  -1,-1, 1, 1,-1,-1,-1, 1,
  -1, 1,-1, 1, 1, 1, 1, 1,
   1, 1, 1,-1,-1, 1,-1, 1,
  -1, 1, 1, 1, 1, 1, 1, 1,
   1, 1, 1,-1, 1, 1, 1, 1,
   1,-1,-1,-1,-1,-1,-1, 1,
  -1, 1,-1, 1,-1,-1, 1,-1,
   1, 1, 1, 1,-1, 1,-1, 1
};

/* Local Functions ----------------------------------------------------------*/

static float cnormf(complex float val) {
    float realf = crealf(val);
    float imagf = cimagf(val);

    return realf * realf + imagf * imagf;
}

/*
 * Gray coded QPSK modulation function
 */
complex float qpsk_mod(int *bits) {
    return qpsk[(bits[1] << 1) | bits[0]];
}

/*
 * Gray coded QPSK demodulation function
 *
 * 01 | 00
 * ---+---
 * 11 | 10
 */
void qpsk_demod(complex float symbol, int *bits) {
    complex float rotate = symbol * cmplx(ROT45);

    bits[0] = crealf(rotate) <= 0.0f;
    bits[1] = cimagf(rotate) <= 0.0f;
}

complex float qam16_mod(int *bits) {
    return qam16[
            (bits[3] << 3) | (bits[2] << 2) |
            (bits[1] << 1) | bits[0]
            ];
}

void qam16_demod(complex float symbol, int *bits) {
    float dist[16];
    int i;

    for (i = 0; i < 16; i++) {
        dist[i] = cnormf(symbol - qam16[i]);
    }

    int row = 0;
    float mdist = 10000.0f;

    for (i = 0; i < 16; i++) {
        if (dist[i] < mdist) {
            mdist = dist[i];
            row = i;
        }
    }

    bits[0] = row & 1;
    bits[1] = (row >> 1) & 1;
    bits[2] = (row >> 2) & 1;
    bits[3] = (row >> 3) & 1;
}

/*
 * ------------
 * ofdm_create
 * ------------
 *
 * Returns OFDM data structure on success
 * Return NULL on fail
 *
 * If you want the defaults, call this with config structure
 * and the NC setting to 0. This will fill the structure with
 * default values of the original OFDM modem.
 */
struct OFDM *ofdm_create(const struct OFDM_CONFIG *config) {
    struct OFDM *ofdm;
    float tval;
    int i, j;

    ofdm = (struct OFDM *) CALLOC(1, sizeof (struct OFDM));
    assert(ofdm != NULL);

    if (config == NULL) {
        /* Fill in default values */

        strcpy(ofdm->mode, "700D");
        ofdm->nc = 17;                            /* Number of carriers */
        ofdm->np = 1;
        ofdm->ns = 8;                             /* Number of Symbols per modem frame */
        ofdm->ts = 0.018f;
        ofdm->rs = (1.0f / ofdm->ts);             /* Modulation Symbol Rate */
        ofdm->tcp = .002f;                        /* Cyclic Prefix duration */
        ofdm->tx_centre = 1500.0f;                /* TX Carrier Frequency */
        ofdm->rx_centre = 1500.0f;                /* RX Carrier Frequency */
        ofdm->fs = 8000.0f;                       /* Sample rate */
        ofdm->ntxtbits = 4;
        ofdm->bps = 2;                            /* Bits per Symbol */
        ofdm->nuwbits = 5 * ofdm->bps;            /* default is 5 symbols of Unique Word bits */
        ofdm->bad_uw_errors = 3;
        ofdm->ftwindowwidth = 32;
        ofdm->timing_mx_thresh = 0.30f;
        ofdm->state_machine = "voice1";
        ofdm->edge_pilots = 1;
        ofdm->codename = "HRA_112_112";
        ofdm->amp_est_mode = 0;
        ofdm->tx_bpf_en = true;
        ofdm->amp_scale = 245E3;
        ofdm->clip_gain1 = 2.0;
        ofdm->clip_gain2 = 0.9;
        ofdm->clip_en = false;
        ofdm->foff_limiter = false;
        ofdm->data_mode = "";
        ofdm->fmin = -50.0;                       /* frequency minimum for ofdm acquisition range */
        ofdm->fmax = 50.0;                        /* frequency maximum for ofdm acquisition range */
        memset(ofdm->tx_uw, 0, ofdm->nuwbits);
    } else {
        /* Use the users values */


        strcpy(ofdm->mode, config->mode);
        ofdm->nc = config->nc;                    /* Number of carriers */
        ofdm->np = config->np;                    /* Number of modem Frames per Packet */
        ofdm->ns = config->ns;                    /* Number of Symbol frames */
        ofdm->bps = config->bps;                  /* Bits per Symbol */
        ofdm->ts = config->ts;
        ofdm->tcp = config->tcp;                  /* Cyclic Prefix duration */
        ofdm->tx_centre = config->tx_centre;      /* TX Centre Audio Frequency */
        ofdm->rx_centre = config->rx_centre;      /* RX Centre Audio Frequency */
        ofdm->fs = config->fs;                    /* Sample Frequency */
        ofdm->rs = config->rs;                    /* Symbol Rate */
        ofdm->ntxtbits = config->txtbits;
        ofdm->nuwbits = config->nuwbits;
        ofdm->bad_uw_errors = config->bad_uw_errors;
        ofdm->ftwindowwidth = config->ftwindowwidth;
        ofdm->timing_mx_thresh = config->timing_mx_thresh;
        ofdm->state_machine = config->state_machine;
        ofdm->edge_pilots = config->edge_pilots;
        ofdm->codename = config->codename;
        ofdm->amp_est_mode = config->amp_est_mode;
        ofdm->tx_bpf_en = config->tx_bpf_en;
        ofdm->foff_limiter = config->foff_limiter;
        ofdm->amp_scale = config->amp_scale;
        ofdm->clip_gain1 = config->clip_gain1;
        ofdm->clip_gain2 = config->clip_gain2;
        ofdm->clip_en = config->clip_en;
        memcpy(ofdm->tx_uw, config->tx_uw, ofdm->nuwbits);
        ofdm->data_mode = config->data_mode;
        ofdm->fmin = config->fmin;              /* frequency minimum for ofdm acquisition range */
        ofdm->fmax = config->fmax;              /* frequency maximum for ofdm acquisition range */

    }

    ofdm->rs = (1.0f / ofdm->ts);                 /* Modulation Symbol Rate */
    ofdm->m = (int) (ofdm->fs / ofdm->rs);        /* 700D: 144 */
    ofdm->ncp = (int) (ofdm->tcp * ofdm->fs);     /* 700D: 16 */
    ofdm->inv_m = (1.0f / (float) ofdm->m);

    /* basic sanity checks */
    assert((int)floorf(ofdm->fs / ofdm->rs) == ofdm->m);
    assert(!strcmp(ofdm->state_machine, "voice1") ||
           !strcmp(ofdm->state_machine, "data") ||
           !strcmp(ofdm->state_machine, "voice2"));
    assert(ofdm->nuwbits <= MAX_UW_BITS);

    /* Copy constants into states */

    strcpy(ofdm->config.mode, ofdm->mode);
    ofdm->config.tx_centre = ofdm->tx_centre;
    ofdm->config.rx_centre = ofdm->rx_centre;
    ofdm->config.fs = ofdm->fs;
    ofdm->config.rs = ofdm->rs;
    ofdm->config.ts = ofdm->ts;
    ofdm->config.tcp = ofdm->tcp;
    ofdm->config.timing_mx_thresh = ofdm->timing_mx_thresh;
    ofdm->config.nc = ofdm->nc;
    ofdm->config.ns = ofdm->ns;
    ofdm->config.np = ofdm->np;
    ofdm->config.bps = ofdm->bps;
    ofdm->config.nuwbits = ofdm->nuwbits;
    ofdm->config.txtbits = ofdm->ntxtbits;
    ofdm->config.bad_uw_errors = ofdm->bad_uw_errors;
    ofdm->config.ftwindowwidth = ofdm->ftwindowwidth;
    ofdm->config.state_machine = ofdm->state_machine;
    ofdm->config.edge_pilots = ofdm->edge_pilots;
    ofdm->config.codename = ofdm->codename;
    ofdm->config.amp_est_mode = ofdm->amp_est_mode;
    ofdm->config.tx_bpf_en = ofdm->tx_bpf_en;
    ofdm->config.foff_limiter = ofdm->foff_limiter;
    ofdm->config.amp_scale = ofdm->amp_scale;
    ofdm->config.clip_gain1 = ofdm->clip_gain1;
    ofdm->config.clip_gain2 = ofdm->clip_gain2;
    ofdm->config.clip_en = ofdm->clip_en;
    memcpy(ofdm->config.tx_uw, ofdm->tx_uw, ofdm->nuwbits);
    ofdm->config.data_mode = ofdm->data_mode;
    ofdm->config.fmin = ofdm->fmin;
    ofdm->config.fmax = ofdm->fmax;
    

    /* Calculate sizes from config param */

    ofdm->bitsperframe = (ofdm->ns - 1) * (ofdm->nc * ofdm->bps);   // 238 for nc = 17
    ofdm->bitsperpacket = ofdm->np * ofdm->bitsperframe;
    ofdm->tpacket = (float)(ofdm->np * ofdm->ns) * (ofdm->tcp + ofdm->ts); /* time for one packet */
    ofdm->rowsperframe = ofdm->bitsperframe / (ofdm->nc * ofdm->bps);
    ofdm->samplespersymbol = (ofdm->m + ofdm->ncp);
    ofdm->samplesperframe = ofdm->ns * ofdm->samplespersymbol;
    if (*ofdm->data_mode != 0)
        // in burst data modes we skip ahead one frame to jump over preamble
        ofdm->max_samplesperframe = 2*ofdm->samplesperframe;
    else
        ofdm->max_samplesperframe = ofdm->samplesperframe + (ofdm->samplespersymbol / 4);
    /* extra storage at start of rxbuf to allow us to step back in time */
    if (strlen(ofdm->data_mode))
        ofdm->nrxbufhistory = (ofdm->np+2)*ofdm->samplesperframe; 
    else
        ofdm->nrxbufhistory = 0;
    ofdm->rxbufst = ofdm->nrxbufhistory;
    ofdm->nrxbufmin = 3*ofdm->samplesperframe + 3*ofdm->samplespersymbol;
    ofdm->nrxbuf = ofdm->nrxbufhistory + ofdm->nrxbufmin;

    ofdm->pilot_samples = (complex float *) MALLOC(sizeof (complex float) * ofdm->samplespersymbol);
    assert(ofdm->pilot_samples != NULL);

    ofdm->rxbuf = (complex float *) MALLOC(sizeof (complex float) * ofdm->nrxbuf);
    assert(ofdm->rxbuf != NULL);
    for(int i=0; i<ofdm->nrxbuf; i++) ofdm->rxbuf[i] = 0;
    
    ofdm->pilots = (complex float *) MALLOC(sizeof (complex float) * (ofdm->nc + 2));
    assert(ofdm->pilots !=  NULL);

    /*
     * rx_sym is a 2D array of variable size
     *
     * allocate rx_sym row storage. It is a pointer to a pointer
     */
    ofdm->rx_sym = MALLOC(sizeof (complex float) * (ofdm->ns + 3));
    assert(ofdm->rx_sym != NULL);

    /* allocate rx_sym column storage */

    for (i = 0; i < (ofdm->ns + 3); i++) {
        ofdm->rx_sym[i] = (complex float *) MALLOC(sizeof(complex float) * (ofdm->nc + 2));
	      assert(ofdm->rx_sym[i] != NULL);
    }

    /* The rest of these are 1D arrays of variable size */

    ofdm->rx_np = MALLOC(sizeof (complex float) * (ofdm->rowsperframe * ofdm->nc));
    assert(ofdm->rx_np != NULL);

    ofdm->rx_amp = MALLOC(sizeof (float) * (ofdm->rowsperframe * ofdm->nc));
    assert(ofdm->rx_amp != NULL);

    ofdm->aphase_est_pilot_log = MALLOC(sizeof (float) * (ofdm->rowsperframe * ofdm->nc));
    assert(ofdm->aphase_est_pilot_log != NULL);

    /* Null pointers to unallocated buffers */
    ofdm->tx_bpf = NULL;
    if (ofdm->tx_bpf_en)
        allocate_tx_bpf(ofdm);

    /* store complex BPSK pilot symbols */

    assert(sizeof (pilotvalues) >= (ofdm->nc + 2) * sizeof (int8_t));

    /* There are only 64 pilot values available */

    for (i = 0; i < (ofdm->nc + 2); i++) {
        ofdm->pilots[i] = ((float) pilotvalues[i]) + 0.0f * I;
    }
    if (ofdm->edge_pilots == 0) {
        ofdm->pilots[0] = ofdm->pilots[ofdm->nc + 1] = 0.0f;
    }
    /* carrier tables for up and down conversion */

    ofdm->doc = (TAU / (ofdm->fs / ofdm->rs));
    tval = ((float) ofdm->nc / 2.0f);
    ofdm->tx_nlower = roundf((ofdm->tx_centre / ofdm->rs) - tval) - 1.0f;
    ofdm->rx_nlower = roundf((ofdm->rx_centre / ofdm->rs) - tval) - 1.0f;

    for (i = 0; i < ofdm->nrxbuf; i++) {
        ofdm->rxbuf[i] = 0.0f;
    }

    for (i = 0; i < (ofdm->ns + 3); i++) {
        for (j = 0; j < (ofdm->nc + 2); j++) {
            ofdm->rx_sym[i][j] = 0.0f;
        }
    }

    for (i = 0; i < ofdm->rowsperframe * ofdm->nc; i++) {
        ofdm->rx_np[i] = 0.0f;
    }

    for (i = 0; i < ofdm->rowsperframe; i++) {
        for (j = 0; j < ofdm->nc; j++) {
            ofdm->aphase_est_pilot_log[ofdm->nc * i + j] = 0.0f;
            ofdm->rx_amp[ofdm->nc * i + j] = 0.0f;
        }
    }

    /* default settings of options and states */

    ofdm->verbose = 0;
    ofdm->timing_en = true;
    ofdm->foff_est_en = true;
    ofdm->phase_est_en = true;
    ofdm->phase_est_bandwidth = high_bw;
    ofdm->phase_est_bandwidth_mode = AUTO_PHASE_EST;
    ofdm->packetsperburst = 0;  // default: never lose syn in raw data mode
    
    ofdm->coarse_foff_est_hz = 0.0f;
    ofdm->foff_est_gain = 0.1f;
    ofdm->foff_est_hz = 0.0f;
    ofdm->sample_point = 0;
    ofdm->timing_est = 0;
    ofdm->timing_valid = 0;
    ofdm->timing_mx = 0.0f;
    ofdm->nin = ofdm->samplesperframe;
    ofdm->mean_amp = 0.0f;
    ofdm->foff_metric = 0.0f;
    
    ofdm->fmin = -50.0f;
    ofdm->fmax = 50.0f;
    
    /*
     * Unique Word symbol placement.  Note we need to group the UW
     * bits so they fit into symbols.  The LDPC decoder works on
     * symbols so we can't break up any symbols into UW/payload bits.
     */
    ofdm->uw_ind = MALLOC(sizeof (int) * ofdm->nuwbits);
    assert(ofdm->uw_ind != NULL);

    ofdm->uw_ind_sym = MALLOC(sizeof (int) * (ofdm->nuwbits / ofdm->bps));
    assert(ofdm->uw_ind_sym != NULL);

    /*
     * The Unique Word is placed in different indexes based on
     * the number of carriers requested.
     */
    int nuwsyms = ofdm->nuwbits / ofdm->bps;
    int Ndatasymsperframe = (ofdm->ns-1)*ofdm->nc;
    int uw_step = ofdm->nc + 1;                   // default step size
    int last_sym = floorf(nuwsyms*uw_step/ofdm->bps);
    if (last_sym >= ofdm->np*Ndatasymsperframe)
        uw_step = ofdm->nc - 1;                   // try a different step
    last_sym = floorf(nuwsyms*uw_step/ofdm->bps);
    assert(last_sym < ofdm->np*Ndatasymsperframe);// bail if we still can't fit them all

    for (i = 0, j = 0; i < nuwsyms; i++, j += ofdm->bps) {
        int val = floorf((i + 1) * uw_step / ofdm->bps);

        ofdm->uw_ind_sym[i] = val;             // symbol index

        for (int b = 0; b < ofdm->bps ; b++) {
            ofdm->uw_ind[j + b] = (val * ofdm->bps) + b;
        }
    }

    // work out how many frames UW is spread over
    int symsperframe = ofdm->bitsperframe / ofdm->bps;
    ofdm->nuwframes = (int) ceilf((float)ofdm->uw_ind_sym[nuwsyms-1]/symsperframe);
     
    ofdm->tx_uw_syms = MALLOC(sizeof (complex float) * (ofdm->nuwbits / ofdm->bps));
    assert(ofdm->tx_uw_syms != NULL);

    assert(ofdm->bps == 2); // TODO generalise
    for (int s = 0; s < (ofdm->nuwbits / ofdm->bps); s++) {
        int dibit[2];
        dibit[1] = ofdm->tx_uw[2*s];
        dibit[0] = ofdm->tx_uw[2*s+1];
        ofdm->tx_uw_syms[s] = qpsk_mod(dibit);
    }

    /* sync state machine */

    ofdm->sync_state = search;
    ofdm->last_sync_state = search;

    ofdm->uw_errors = 0;
    ofdm->sync_counter = 0;
    ofdm->frame_count = 0;
    ofdm->sync_start = false;
    ofdm->sync_end = false;
    ofdm->sync_mode = autosync;
    ofdm->modem_frame = 0;

    /* create the OFDM pilot time-domain waveform */

    complex float *temp = MALLOC(sizeof (complex float) * ofdm->m);
    assert(temp != NULL);

    idft(ofdm, temp, ofdm->pilots);

    /*
     * pilot_samples is 160 samples, but timing and freq offset est
     * were found by experiment to work better without a cyclic
     * prefix, so we uses zeroes instead.
     */

    /* zero out Cyclic Prefix (CP) time-domain values */

    for (i = 0; i < ofdm->ncp; i++) {
        ofdm->pilot_samples[i] = 0.0f;
    }

    /* Now copy the whole thing after the above */

    for (i = ofdm->ncp, j = 0; j < ofdm->m; i++, j++) {
        ofdm->pilot_samples[i] = temp[j];
    }

    FREE(temp);

    /* calculate constant used to normalise timing correlation maximum */
    float acc = 0.0f;
    for (i = 0; i < ofdm->samplespersymbol; i++) {
        acc += cnormf(ofdm->pilot_samples[i]);
    }

    ofdm->timing_norm = ofdm->samplespersymbol * acc;
    ofdm->clock_offset_counter = 0;
    ofdm->dpsk_en = false;

    if (strlen(ofdm->data_mode)) {
        ofdm->tx_preamble = (COMP*)malloc(sizeof(COMP)*ofdm->samplesperframe);
        assert(ofdm->tx_preamble != NULL);
        ofdm_generate_preamble(ofdm, ofdm->tx_preamble, 2);
        ofdm->tx_postamble = (COMP*)malloc(sizeof(COMP)*ofdm->samplesperframe);
        assert(ofdm->tx_postamble != NULL);
        ofdm_generate_preamble(ofdm, ofdm->tx_postamble, 3);
    }
    ofdm->postambledetectoren = !strcmp(ofdm->data_mode,"burst");
    
    return ofdm; /* Success */
}

static void allocate_tx_bpf(struct OFDM *ofdm) {
    ofdm->tx_bpf = MALLOC(sizeof(struct quisk_cfFilter));
    assert(ofdm->tx_bpf != NULL);

    /* Transmit bandpass filter; complex coefficients, center frequency */

    if (!strcmp(ofdm->mode, "700D")) {
        quisk_filt_cfInit(ofdm->tx_bpf, filtP650S900, sizeof (filtP650S900) / sizeof (float));
        quisk_cfTune(ofdm->tx_bpf, ofdm->tx_centre / ofdm->fs);
    }
    else if (!strcmp(ofdm->mode, "700E") || !strcmp(ofdm->mode, "2020")) {
        quisk_filt_cfInit(ofdm->tx_bpf, filtP900S1100, sizeof (filtP900S1100) / sizeof (float));
        quisk_cfTune(ofdm->tx_bpf, ofdm->tx_centre / ofdm->fs);
    }
    else if (!strcmp(ofdm->mode, "2020B")) {
        quisk_filt_cfInit(ofdm->tx_bpf, filtP1100S1300, sizeof (filtP1100S1300) / sizeof (float));
        quisk_cfTune(ofdm->tx_bpf, ofdm->tx_centre / ofdm->fs);
    }
    else if  (!strcmp(ofdm->mode, "datac0") || !strcmp(ofdm->mode, "datac3")) {
        quisk_filt_cfInit(ofdm->tx_bpf, filtP400S600, sizeof (filtP400S600) / sizeof (float));
        quisk_cfTune(ofdm->tx_bpf, ofdm->tx_centre / ofdm->fs);
    }
    else assert(0);
}

static void deallocate_tx_bpf(struct OFDM *ofdm) {
    assert(ofdm->tx_bpf != NULL);
    quisk_filt_destroy(ofdm->tx_bpf);
    FREE(ofdm->tx_bpf);
    ofdm->tx_bpf = NULL;
}

void ofdm_destroy(struct OFDM *ofdm) {
    int i;

    if (strlen(ofdm->data_mode)) {
        free(ofdm->tx_preamble);
        free(ofdm->tx_postamble);
    }
    if (ofdm->tx_bpf) {
        deallocate_tx_bpf(ofdm);
    }

    FREE(ofdm->pilot_samples);
    FREE(ofdm->rxbuf);
    FREE(ofdm->pilots);

    for (i = 0; i < (ofdm->ns + 3); i++) { /* 2D array */
        FREE(ofdm->rx_sym[i]);
    }

    FREE(ofdm->rx_sym);
    FREE(ofdm->rx_np);
    FREE(ofdm->rx_amp);
    FREE(ofdm->aphase_est_pilot_log);
    FREE(ofdm->tx_uw_syms);
    FREE(ofdm->uw_ind);
    FREE(ofdm->uw_ind_sym);
    FREE(ofdm);
}

/*
 * Convert frequency domain into time domain
 *
 * This algorithm was optimized for speed
 */
static void idft(struct OFDM *ofdm, complex float *result, complex float *vector) {
    int row, col;

    result[0] = 0.0f;

    for (col = 0; col < (ofdm->nc + 2); col++) {
        result[0] += vector[col];    // cexp(j0) == 1
    }

    result[0] *= ofdm->inv_m;

    for (row = 1; row < ofdm->m; row++) {
        complex float c = cmplx(ofdm->tx_nlower * ofdm->doc *row);
        complex float delta = cmplx(ofdm->doc * row);

        result[row] = 0.0f;

        for (col = 0; col < (ofdm->nc + 2); col++) {
            result[row] += (vector[col] * c);
            c *= delta;
        }

        result[row] *= ofdm->inv_m;
    }
}

/*
 * Convert time domain into frequency domain
 *
 * This algorithm was optimized for speed
 */
static void dft(struct OFDM *ofdm, complex float *result, complex float *vector) {
    int row, col;

    for (col = 0; col < (ofdm->nc + 2); col++) {
        result[col] = vector[0];                 // conj(cexp(j0)) == 1
    }

    for (col = 0; col < (ofdm->nc + 2); col++) {
        float tval = (ofdm->rx_nlower + col) * ofdm->doc;
        complex float c = cmplxconj(tval);
        complex float delta = c;

        for (row = 1; row < ofdm->m; row++) {
            result[col] += (vector[row] * c);
            c *= delta;
        }
    }
}

static complex float vector_sum(complex float *a, int num_elements) {
    complex float sum = 0.0f;
    int i;

    for (i = 0; i < num_elements; i++) {
        sum += a[i];
    }

    return sum;
}


/*
 * Correlates the OFDM pilot symbol samples with a window of received
 * samples to determine the most likely timing offset.  Combines two
 * frames pilots so we need at least Nsamperframe+M+Ncp samples in rx.
 *
 * Can be used for acquisition (coarse timing), and fine timing.
 *
 * Breaks when freq offset approaches +/- symbol rate (e.g
 * +/- 25 Hz for 700D).
 */
static int est_timing(struct OFDM *ofdm, complex float *rx, int length,
  int fcoarse, float *timing_mx, int *timing_valid, int step) {
    complex float corr_st, corr_en;
    int Ncorr = length - (ofdm->samplesperframe + ofdm->samplespersymbol);
    float corr[Ncorr];
    int i, j;
    float acc = 0.0f;

    for (i = 0; i < length; i++) {
        acc += cnormf(rx[i]);
    }

    float av_level = 1.0f/(2.0f * sqrtf(ofdm->timing_norm * acc / length) + 1E-12f);

    /* precompute the freq shift multiplied by pilot samples outside of main loop */

    PROFILE_VAR(wvecpilot);
    PROFILE_SAMPLE(wvecpilot);

    complex float wvec_pilot[ofdm->samplespersymbol];

    switch(fcoarse) {
    case -40:
      for (j = 0; j < ofdm->samplespersymbol; j++)
	wvec_pilot[j] = conjf(ofdm_wval[j]*ofdm->pilot_samples[j]);
      break;
    case 0:
      for (j = 0; j < ofdm->samplespersymbol; j++)
	wvec_pilot[j] = conjf(ofdm->pilot_samples[j]);
      break;
    case 40:
      for (j = 0; j < ofdm->samplespersymbol; j++)
	wvec_pilot[j] = ofdm_wval[j]*conjf(ofdm->pilot_samples[j]);
      break;
    default:
      assert(0);
    }

    /* use of __REAL__ provides a speed in increase of 10ms/frame during acquisition, however complex
       is fast enough for real time operation */

#if defined(__EMBEDDED__) && defined(__REAL__)
    float rx_real[length];
    float wvec_pilot_real[ofdm->samplespersymbol];
    float wvec_pilot_imag[ofdm->samplespersymbol];

    for (i = 0; i < length; i++) {
        rx_real[i] = crealf(rx[i]);
    }

    for (i = 0; i < ofdm->samplespersymbol; i++) {
        wvec_pilot_real[i] = crealf(wvec_pilot[i]);
        wvec_pilot_imag[i] = cimagf(wvec_pilot[i]);
    }

#endif
    PROFILE_SAMPLE_AND_LOG2(wvecpilot, "  wvecpilot");
    PROFILE_VAR(corr_start);
    PROFILE_SAMPLE(corr_start);

    for (i = 0; i < Ncorr; i += step) {
        corr_st = 0.0f;
        corr_en = 0.0f;

#ifdef __EMBEDDED__
#ifdef __REAL__
        // Note: this code untested
	float re,im;
        
        codec2_dot_product_f32(&rx_real[i], wvec_pilot_real, ofdm->samplespersymbol, &re);
	codec2_dot_product_f32(&rx_real[i], wvec_pilot_imag, ofdm->samplespersymbol, &im);
	corr_st = re + im * I;

	codec2_dot_product_f32(&rx_real[i+ ofdm->samplesperframe], wvec_pilot_real, ofdm->samplespersymbol, &re);
	codec2_dot_product_f32(&rx_real[i+ ofdm->samplesperframe], wvec_pilot_imag, ofdm->samplespersymbol, &im);
	corr_en = re + im * I;
        
#else
	float re,im;
        
	codec2_complex_dot_product_f32((COMP*)&rx[i], (COMP*)wvec_pilot, ofdm->samplespersymbol, &re, &im);
	corr_st = re + im * I;

	codec2_complex_dot_product_f32((COMP*)&rx[i+ ofdm->samplesperframe], (COMP*)wvec_pilot, ofdm->samplespersymbol, &re, &im);
	corr_en = re + im * I;
#endif
#else
	for (j = 0; j < ofdm->samplespersymbol; j++) {
            int ind = i + j;

	    corr_st = corr_st + (rx[ind                        ] * wvec_pilot[j]);
            corr_en = corr_en + (rx[ind + ofdm->samplesperframe] * wvec_pilot[j]);
        }
#endif // __EMBEDDED__
        corr[i] = (cabsf(corr_st) + cabsf(corr_en)) * av_level;
    }

    PROFILE_SAMPLE_AND_LOG2(corr_start, "  corr");

    /* find the max magnitude and its index */

    int timing_est = 0;
    *timing_mx = 0.0f;

    for (i = 0; i < Ncorr; i+=step) {
        if (corr[i] > *timing_mx) {
            *timing_mx = corr[i];
            timing_est = i;
        }
    }

    // only declare timing valid if there are enough samples in rxbuf to demodulate a frame
    *timing_valid = (cabsf(rx[timing_est]) > 0.0) && (*timing_mx > ofdm->timing_mx_thresh);

    if (ofdm->verbose > 2) {
        fprintf(stderr, "  av_level: %f  max: %f timing_est: %d timing_valid: %d\n", (double) av_level,
             (double) *timing_mx, timing_est, *timing_valid);
    }

    return timing_est;
}

/*
 * Determines frequency offset at current timing estimate, used for
 * coarse freq offset estimation during acquisition.  Works up to +/-
 * the symbol rate, e.g. +/- 25Hz for the FreeDV 700D configuration.
 */
static float est_freq_offset_pilot_corr(struct OFDM *ofdm, complex float *rx, int timing_est, int fcoarse) {
    int st = -20; int en = 20; float foff_est = 0.0f; float Cabs_max = 0.0f;

    /* precompute the freq shift multiplied by pilot samples outside of main loop */

    complex float wvec_pilot[ofdm->samplespersymbol];
    int j;

    switch(fcoarse) {
    case -40:
      for (j = 0; j < ofdm->samplespersymbol; j++)
        wvec_pilot[j] = conjf(ofdm_wval[j]*ofdm->pilot_samples[j]);
      break;
    case 0:
      for (j = 0; j < ofdm->samplespersymbol; j++)
        wvec_pilot[j] = conjf(ofdm->pilot_samples[j]);
      break;
    case 40:
      for (j = 0; j < ofdm->samplespersymbol; j++)
        wvec_pilot[j] = ofdm_wval[j]*conjf(ofdm->pilot_samples[j]);
      break;
    default:
      assert(0);
    }

    // sample sum of DFT magnitude of correlated signals at each freq offset and look for peak
    for (int f = st; f < en; f++) {
        complex float corr_st = 0.0f;
        complex float corr_en = 0.0f;
        float tmp = TAU * f / ofdm->fs;
	      complex float delta = cmplxconj(tmp);
	      complex float w = cmplxconj(0.0f);
	      int i;

        for (i = 0; i < ofdm->samplespersymbol; i++) {
            // "mix" down (correlate) the pilot sequences from frame with 0 Hz offset pilot samples
            complex float csam = wvec_pilot[i] * w;
            int est = timing_est + i;

            corr_st += rx[est                        ] * csam;
            corr_en += rx[est + ofdm->samplesperframe] * csam;
	          w = w * delta;
	      }

	      float Cabs = cabsf(corr_st) + cabsf(corr_en);

	      if (Cabs > Cabs_max) {
	          Cabs_max = Cabs;
	          foff_est = f;
    	  }
    }

    ofdm->foff_metric = 0.0f; // not used in this version of freq est algorithm

    if (ofdm->verbose > 2) {
        fprintf(stderr, "cabs_max: %f  foff_est: %f\n", (double) Cabs_max, (double) foff_est);
    }

    return foff_est;
}


/*
 * ----------------------------------------------
 * ofdm_txframe - modulates one frame of symbols
 * ----------------------------------------------
 */
void ofdm_txframe(struct OFDM *ofdm, complex float *tx, complex float *tx_sym_lin) {
    complex float aframe[ofdm->np * ofdm->ns][ofdm->nc + 2];
    complex float asymbol[ofdm->m];
    complex float asymbol_cp[ofdm->samplespersymbol];
    int i, j, k, m;

    /* initialize aframe to complex zero */

    for (i = 0; i < (ofdm->np * ofdm->ns); i++) {
        for (j = 0; j < (ofdm->nc + 2); j++) {
            aframe[i][j] = 0.0f;
        }
    }

    /*
     * Place symbols in multi-carrier frame with pilots
     * This will place boundary values of complex zero around data
     */
    int s = 0;
    for (int r = 0; r < ofdm->np*ofdm->ns; r++) {

        if ((r % ofdm->ns) == 0) {
            /* copy in a row of complex pilots to first row of each frame */
            for (i = 0; i < (ofdm->nc + 2); i++) {
              aframe[r][i] = ofdm->pilots[i];
            }
        }
        else {
            /* copy in the Nc complex data symbols with [0 Nc 0] or (Nc + 2) total */
            for (j = 1; j < (ofdm->nc + 1); j++) {
                aframe[r][j] = tx_sym_lin[s++];
                if (ofdm->dpsk_en == true) {
                    aframe[r][j] *= aframe[r-1][j];
                }
            }
        }
    }

    /* OFDM up-convert symbol by symbol so we can add CP */

    for (i = 0, m = 0; i < (ofdm->np * ofdm->ns); i++, m += ofdm->samplespersymbol) {
        idft(ofdm, asymbol, aframe[i]);

        /* Copy the last Ncp samples to the front */

        for (j = (ofdm->m - ofdm->ncp), k = 0; j < ofdm->m; j++, k++) {
            asymbol_cp[k] = asymbol[j];
        }

        /* Now copy the all samples for this row after it */

        for (j = ofdm->ncp, k = 0; k < ofdm->m; j++, k++) {
            asymbol_cp[j] = asymbol[k];
        }

        /* Now move row to the tx output */

        for (j = 0; j < ofdm->samplespersymbol; j++) {
            tx[m + j] = asymbol_cp[j];
        }
    }

    size_t samplesperpacket = ofdm->np*ofdm->samplesperframe;
    ofdm_hilbert_clipper(ofdm, tx, samplesperpacket);
}


/* Scale Tx signal and optionally apply two stage Hilbert clipper to improve PAPR */
void ofdm_hilbert_clipper(struct OFDM *ofdm, complex float *tx, size_t n) {
    
    /* vanilla Tx output waveform should be about OFDM_PEAK */
    for(int i=0; i<n; i++) tx[i] *= ofdm->amp_scale;

    if (ofdm->clip_en) {
        // this gain set the drive into the Hilbert Clipper and sets PAPR
        for(int i=0; i<n; i++) tx[i] *= ofdm->clip_gain1;
        ofdm_clip(tx, OFDM_PEAK, n);
    }

    /* BPF to remove out of band energy clipper introduces */
    if (ofdm->tx_bpf_en) {
        assert(!strcmp(ofdm->mode, "700D") || !strcmp(ofdm->mode, "700E")
               || !strcmp(ofdm->mode, "2020") || !strcmp(ofdm->mode, "2020B")
               || !strcmp(ofdm->mode, "datac0") || !strcmp(ofdm->mode, "datac3"));
        assert(ofdm->tx_bpf != NULL);
        complex float tx_filt[n];

        quisk_ccfFilter(tx, tx_filt, n, ofdm->tx_bpf);
        memmove(tx, tx_filt, n * sizeof (complex float));
    }

    /* BPF messes up peak levels, this gain gets back to approx OFDM_PEAK */
    if (ofdm->tx_bpf_en && ofdm->clip_en)
        for(int i=0; i<n; i++) tx[i] *= ofdm->clip_gain2;

    /* a very small percentage of samples may still exceed OFDM_PEAK, in
       clipped or unclipped mode.  Lets remove them so we present consistent
       levels to the transmitter */
    
    ofdm_clip(tx, OFDM_PEAK, n);
}


struct OFDM_CONFIG *ofdm_get_config_param(struct OFDM *ofdm) { return &ofdm->config; }
int ofdm_get_nin(struct OFDM *ofdm) {return ofdm->nin;}
int ofdm_get_samples_per_frame(struct OFDM *ofdm) { return ofdm->samplesperframe;}
int ofdm_get_samples_per_packet(struct OFDM *ofdm) { return ofdm->samplesperframe*ofdm->np;}
int ofdm_get_max_samples_per_frame(struct OFDM *ofdm) {return ofdm->max_samplesperframe; }
int ofdm_get_bits_per_frame(struct OFDM *ofdm) {return  ofdm->bitsperframe; }
int ofdm_get_bits_per_packet(struct OFDM *ofdm) {return  ofdm->bitsperpacket; }
void ofdm_set_verbose(struct OFDM *ofdm, int level) { ofdm->verbose = level; }

void ofdm_set_timing_enable(struct OFDM *ofdm, bool val) {
    ofdm->timing_en = val;

    if (ofdm->timing_en == false) {
        /* manually set ideal timing instant */

        ofdm->sample_point = (ofdm->ncp - 1);
    }
}

int ofdm_get_phase_est_bandwidth_mode(struct OFDM *ofdm) {
    return ofdm->phase_est_bandwidth_mode;    /* int version of enum */
}

void ofdm_set_phase_est_bandwidth_mode(struct OFDM *ofdm, int val) {
    assert((val == AUTO_PHASE_EST) || (val == LOCKED_PHASE_EST));
    ofdm->phase_est_bandwidth_mode = val;
}

void ofdm_set_foff_est_enable(struct OFDM *ofdm, bool val) {
    ofdm->foff_est_en = val;
}

void ofdm_set_phase_est_enable(struct OFDM *ofdm, bool val) {
    ofdm->phase_est_en = val;
}

void ofdm_set_off_est_hz(struct OFDM *ofdm, float val) {
    ofdm->foff_est_hz = val;
}

void ofdm_set_tx_bpf(struct OFDM *ofdm, bool val) {
    if (val == true) {
    	 if (ofdm->tx_bpf == NULL)
             allocate_tx_bpf(ofdm);

    	ofdm->tx_bpf_en = true;
    }
    else {
    	if (ofdm->tx_bpf != NULL)
            deallocate_tx_bpf(ofdm);

    	ofdm->tx_bpf_en = false;
    }
}

void ofdm_set_dpsk(struct OFDM *ofdm, bool val) {
    ofdm->dpsk_en = val;
}

// select burst mode, and set packets per burst
void ofdm_set_packets_per_burst(struct OFDM *ofdm, int packetsperburst) {
    ofdm->data_mode = "burst";
    ofdm->packetsperburst = packetsperburst;
    ofdm->postambledetectoren = true;
}

/*
 * --------------------------------------
 * ofdm_mod - modulates one frame of bits
 * --------------------------------------
 */
void ofdm_mod(struct OFDM *ofdm, COMP *result, const int *tx_bits) {
    int length = ofdm->bitsperpacket / ofdm->bps;
    complex float *tx = (complex float *) result; // complex has same memory layout
    complex float tx_sym_lin[length];
    int dibit[2];
    int s, i;

    if (ofdm->bps == 1) {
        /* Here we will have Nbitsperpacket / 1 */

        for (s = 0; s < length; s++) {
            tx_sym_lin[s] = (float) (2 * tx_bits[s] - 1);
        }
    } else if (ofdm->bps == 2) {
        /* Here we will have Nbitsperpacket / 2 */

        for (s = 0, i = 0; i < length; s += 2, i++) {
            dibit[0] = tx_bits[s + 1] & 0x1;
            dibit[1] = tx_bits[s    ] & 0x1;

            tx_sym_lin[i] = qpsk_mod(dibit);
        }
    } /* else if (ofdm->bps == 3) { } TODO */

    ofdm_txframe(ofdm, tx, tx_sym_lin);
}

/*
 * ----------------------------------------------------------------------------------
 * ofdm_sync_search - attempts to find coarse sync parameters for modem initial sync
 * ----------------------------------------------------------------------------------
 */

/*
 * This is a wrapper to maintain the older functionality
 * with an array of COMPs as input
 */
int ofdm_sync_search(struct OFDM *ofdm, COMP *rxbuf_in) {
    /*
     * insert latest input samples into rxbuf
     * so it is primed for when we have to call ofdm_demod()
     */

    /* note can't use memcpy when src and dest overlap */
    memmove(&ofdm->rxbuf[0], &ofdm->rxbuf[ofdm->nin],
           (ofdm->nrxbuf - ofdm->nin) * sizeof (complex float));
    memmove(&ofdm->rxbuf[(ofdm->nrxbuf - ofdm->nin)],
        rxbuf_in, ofdm->nin * sizeof (complex float));

    return(ofdm_sync_search_core(ofdm));
}

/*
 * This is a wrapper to reduce memory allocated.
 * This works with ofdm_demod and freedv_api. Gain is not used here.
 */
int ofdm_sync_search_shorts(struct OFDM *ofdm, short *rxbuf_in, float gain) {
    int i, j;

    /* shift the buffer left based on nin */

    memmove(&ofdm->rxbuf[0], &ofdm->rxbuf[ofdm->nin],
            (ofdm->nrxbuf - ofdm->nin) * sizeof (complex float));

    /* insert latest input samples onto tail of rxbuf */

    for (j = 0, i = (ofdm->nrxbuf - ofdm->nin); i < ofdm->nrxbuf; j++, i++) {
        ofdm->rxbuf[i] = ((float)rxbuf_in[j] / 32767.0f);
    }

    return ofdm_sync_search_core(ofdm);
}

/* Joint estimation of timing and freq used for burst data acquisition */

static float est_timing_and_freq(struct OFDM *ofdm, 
                                 int *t_est, float *foff_est,
                                 complex float *rx, int Nrx, 
                                 complex float *known_samples, int Npsam,
                                 int tstep, float fmin, float fmax, float fstep) {
    int Ncorr = Nrx - Npsam + 1;
    float max_corr = 0;
    *t_est = 0; *foff_est = 0.0;
    for (float afcoarse=fmin; afcoarse<=fmax; afcoarse += fstep) {
        float w = TAU * afcoarse / ofdm->fs;
        complex float mvec[Npsam];
        for(int i=0; i<Npsam; i++) {
            complex float ph = cmplx(w*i);
            mvec[i] = known_samples[i]*ph;
        }
        for(int t=0; t<Ncorr; t+=tstep) {
            complex float corr = 0;
            for(int i=0; i<Npsam; i++)
                corr += rx[i+t]*conjf(mvec[i]);
            if (cabsf(corr) > max_corr) {
                max_corr = cabsf(corr);
                *t_est = t;
                *foff_est = afcoarse;
            }
        }
    }   
        
    /* obtain normalised real number for timing_mx */
    float mag1=0, mag2=0;
    for(int i=0; i<Npsam; i++) {
        mag1 += cabsf(known_samples[i]*conjf(known_samples[i]));
        mag2 += cabsf(rx[i+*t_est]*conjf(rx[i+*t_est]));
    }
    float timing_mx = max_corr*max_corr/(mag1*mag2+1E-12);
    if (ofdm->verbose > 2) {
        fprintf(stderr, "  t_est: %4d timing:mx: %f foff_est: %f\n", *t_est, (double)timing_mx, (double)*foff_est);
    }
    
    return timing_mx;
}

/* Two stage burst mode acquisition  */

static void burst_acquisition_detector(struct OFDM *ofdm, 
                                       complex float *rx, int n, 
                                       complex float *known_sequence,
                                       int *ct_est, float *foff_est, float *timing_mx)
{
    
    float fmin, fmax, fstep;
    int tstep;

    // initial search over coarse grid
    tstep = 4; fstep = 5; fmin = ofdm->fmin; fmax = ofdm->fmax;
    *timing_mx = est_timing_and_freq(ofdm, ct_est, foff_est,
                                 &rx[n], 2*ofdm->samplesperframe, 
                                 known_sequence, ofdm->samplesperframe,
                                 tstep, fmin, fmax, fstep);

    // refine estimate over finer grid
    fmin = *foff_est - ceilf(fstep/2.0); fmax = *foff_est + ceilf(fstep/2.0); 
    int fine_st = n + *ct_est - tstep/2.0;
    *timing_mx = est_timing_and_freq(ofdm, ct_est, foff_est,
                                 &rx[fine_st], ofdm->samplesperframe + tstep, 
                                 known_sequence, ofdm->samplesperframe,
                                 1, fmin, fmax, 1.0);                
                                 
    // refer ct_est to nominal start of frame rx[n]
    *ct_est += fine_st - n;
}
    
static int ofdm_sync_search_burst(struct OFDM *ofdm) {
    
    int st = ofdm->rxbufst + ofdm->m + ofdm->ncp + ofdm->samplesperframe;
    char *pre_post = "";
    
    int pre_ct_est; float pre_foff_est, pre_timing_mx;        
    burst_acquisition_detector(ofdm, ofdm->rxbuf, st, (complex float*)ofdm->tx_preamble, 
                               &pre_ct_est, &pre_foff_est, &pre_timing_mx);

    int post_ct_est; float post_foff_est, post_timing_mx;        
    if (ofdm->postambledetectoren)
        burst_acquisition_detector(ofdm, ofdm->rxbuf, st, (complex float*)ofdm->tx_postamble, 
                                   &post_ct_est, &post_foff_est, &post_timing_mx);
    
    int ct_est; float foff_est, timing_mx;        
    if (!ofdm->postambledetectoren || (pre_timing_mx > post_timing_mx)) {
        timing_mx = pre_timing_mx; ct_est = pre_ct_est; foff_est = pre_foff_est;
        pre_post = "pre";
    } else {
        timing_mx = post_timing_mx; ct_est = post_ct_est; foff_est = post_foff_est;
        pre_post = "post";
    }
    
    int timing_valid = timing_mx > ofdm->timing_mx_thresh;
    if (timing_valid) {
        if (!strcmp(pre_post, "post")) {
            ofdm->post++;
            // we won't be need any new samples for a while ....
            ofdm->nin = 0;
            // backup to first modem frame in packet
            ofdm->rxbufst -= ofdm->np*ofdm->samplesperframe; 
            ofdm->rxbufst += ct_est;
        } else {
            ofdm->pre++;
            // ct_est is start of preamble, so advance past that to start of first modem frame
            ofdm->nin = ofdm->samplesperframe + ct_est - 1;
        }
    } else 
        ofdm->nin = ofdm->samplesperframe;
    
    ofdm->ct_est = ct_est;
    ofdm->foff_est_hz = foff_est;
    ofdm->timing_mx = timing_mx;
    ofdm->timing_valid = timing_valid;

    if (ofdm->verbose > 1) {
        fprintf(stderr, "  ct_est: %4d nin: %4d mx: %3.2f foff_est: % 5.1f timing_valid: %d %4s\n",
                ct_est, ofdm->nin, (double)timing_mx, (double)foff_est, timing_valid, pre_post);
    }

    return ofdm->timing_valid;
}

/*
 * Attempts to find coarse sync parameters for modem initial sync (streaming mode)
 */
static int ofdm_sync_search_stream(struct OFDM *ofdm) {
    int act_est, afcoarse;

    /* Attempt coarse timing estimate (i.e. detect start of frame) at a range of frequency offsets */

    int st = ofdm->rxbufst + ofdm->samplesperframe + ofdm->samplespersymbol;
    int en = st + 2 * ofdm->samplesperframe + ofdm->samplespersymbol;

    int fcoarse = 0;
    float atiming_mx, timing_mx = 0.0f;
    int ct_est = 0;
    int atiming_valid, timing_valid = 0;

    PROFILE_VAR(timing_start);
    PROFILE_SAMPLE(timing_start);

    for (afcoarse = -40; afcoarse <= 40; afcoarse += 40) {
        act_est = est_timing(ofdm, &ofdm->rxbuf[st], (en - st), afcoarse, &atiming_mx, &atiming_valid, 2);

        if (atiming_mx > timing_mx) {
            ct_est = act_est;
            timing_mx = atiming_mx;
            fcoarse = afcoarse;
            timing_valid = atiming_valid;
        }
    }

    PROFILE_SAMPLE_AND_LOG2(timing_start, "  timing");

    /* refine freq est within -/+ 20 Hz window */

    PROFILE_VAR(freq_start);
    PROFILE_SAMPLE(freq_start);

    ofdm->coarse_foff_est_hz = est_freq_offset_pilot_corr(ofdm, &ofdm->rxbuf[st], ct_est, fcoarse);
    ofdm->coarse_foff_est_hz += fcoarse;

    PROFILE_SAMPLE_AND_LOG2(freq_start, "  freq");

    if (ofdm->verbose > 1) {
        fprintf(stderr, "    ct_est: %4d foff_est: %4.1f timing_valid: %d timing_mx: %5.4f\n",
                ct_est, (double) ofdm->coarse_foff_est_hz, timing_valid,
                (double)timing_mx);
    }

    ofdm->timing_valid = timing_valid;
    if (ofdm->timing_valid != 0) {
        /* potential candidate found .... */

        /* calculate number of samples we need on next buffer to get into sync */

        ofdm->nin = ct_est;

        /* reset modem states */

        ofdm->sample_point = ofdm->timing_est = 0;
        ofdm->foff_est_hz = ofdm->coarse_foff_est_hz;
        ofdm->timing_mx = timing_mx;
    } else {
        ofdm->nin = ofdm->samplesperframe;
    }

    ofdm->timing_mx = timing_mx;

    return ofdm->timing_valid;
}

static int ofdm_sync_search_core(struct OFDM *ofdm) {
    if (!strcmp(ofdm->data_mode, "burst"))
        return ofdm_sync_search_burst(ofdm);
    else
        return ofdm_sync_search_stream(ofdm);
}

/*
 * ------------------------------------------
 * ofdm_demod - Demodulates one frame of bits
 * ------------------------------------------
 */

/*
 * This is a wrapper to maintain the older functionality with an
 * array of COMPs as input
 */
void ofdm_demod(struct OFDM *ofdm, int *rx_bits, COMP *rxbuf_in) {
    complex float *rx = (complex float *) &rxbuf_in[0]; // complex has same memory layout
    int i, j;

    /* shift the buffer left based on nin */
    for (i = 0, j = ofdm->nin; i < (ofdm->nrxbuf - ofdm->nin); i++, j++) {
        ofdm->rxbuf[i] = ofdm->rxbuf[j];
    }

    /* insert latest input samples onto tail of rxbuf */
    for (j = 0, i = (ofdm->nrxbuf - ofdm->nin); i < ofdm->nrxbuf; j++, i++) {
        ofdm->rxbuf[i] = rx[j];
    }

    ofdm_demod_core(ofdm, rx_bits);
}

/*
 * This is a wrapper with a new interface to reduce memory allocated.
 * This works with ofdm_demod and freedv_api. Gain is not used here.
 */
void ofdm_demod_shorts(struct OFDM *ofdm, int *rx_bits, short *rxbuf_in, float gain) {
    int i, j;

    /* shift the buffer left based on nin */

    for (i = 0, j = ofdm->nin; i < (ofdm->nrxbuf - ofdm->nin); i++, j++) {
        ofdm->rxbuf[i] = ofdm->rxbuf[j];
    }

    /* insert latest input samples onto tail of rxbuf */

    for (j = 0, i = (ofdm->nrxbuf - ofdm->nin); i < ofdm->nrxbuf; j++, i++) {
        ofdm->rxbuf[i] = ((float)rxbuf_in[j] / 32767.0f);
    }

    ofdm_demod_core(ofdm, rx_bits);
}

/*
 * This is the rest of the function which expects that the data is
 * already in ofdm->rxbuf
 */
static void ofdm_demod_core(struct OFDM *ofdm, int *rx_bits) {
    int prev_timing_est = ofdm->timing_est;
    int i, j, k, rr, st, en;

    /*
     * get user and calculated freq offset
     */
    float woff_est = TAU * ofdm->foff_est_hz / ofdm->fs;

    /* update timing estimate ---------------------------------------------- */

    if (ofdm->timing_en == true) {
        /* update timing at start of every frame */

        st = ofdm->rxbufst + ofdm->samplespersymbol + ofdm->samplesperframe - (int) floorf((float)ofdm->ftwindowwidth / 2) + ofdm->timing_est;
        en = st + ofdm->samplesperframe - 1 + ofdm->samplespersymbol + ofdm->ftwindowwidth;

        complex float work[(en - st)];

        /*
         * Adjust for the frequency error by shifting the phase
         * using a conjugate multiply
         */
        for (j = 0, i = st; i < en; j++, i++) {
            work[j] = ofdm->rxbuf[i] * cmplxconj(woff_est * i);
        }

        int ft_est = est_timing(ofdm, work, (en - st), 0.0f, &ofdm->timing_mx, &ofdm->timing_valid, 1);

        ofdm->timing_est += ft_est - (int) ceilf((float)ofdm->ftwindowwidth / 2) + 1;

        if (ofdm->verbose > 2) {
            fprintf(stderr, "  ft_est: %2d timing_est: %2d sample_point: %2d\n", ft_est, ofdm->timing_est,
                ofdm->sample_point);
        }

        /* Black magic to keep sample_point inside cyclic prefix.  Or something like that. */

        ofdm->sample_point = max(ofdm->timing_est + 4, ofdm->sample_point);
        ofdm->sample_point = min(ofdm->timing_est + ofdm->ncp-4, ofdm->sample_point);
    }

    /*
     * Convert the time-domain samples to the frequency-domain using the rx_sym
     * data matrix. This will be  Nc+2 carriers of 11 symbols.
     *
     * You will notice there are Nc+2 BPSK symbols for each pilot symbol, and
     * that there are Nc QPSK symbols for each data symbol.
     *
     *  XXXXXXXXXXXXXXXXX  <-- Timing Slip
     * PPPPPPPPPPPPPPPPPPP <-- Previous Frames Pilot
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD      Ignore these past data symbols
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     * PPPPPPPPPPPPPPPPPPP <-- This Frames Pilot
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD      These are the current data symbols to be decoded
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     * PPPPPPPPPPPPPPPPPPP <-- Next Frames Pilot
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD      Ignore these next data symbols
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     *  DDDDDDDDDDDDDDDDD
     * PPPPPPPPPPPPPPPPPPP <-- Future Frames Pilot
     *  XXXXXXXXXXXXXXXXX  <-- Timing Slip
     *
     * So this algorithm will have seven data symbols and four pilot symbols to process.
     * The average of the four pilot symbols is our phase estimation.
     */
    for (i = 0; i < (ofdm->ns + 3); i++) {
        for (j = 0; j < (ofdm->nc + 2); j++) {
            ofdm->rx_sym[i][j] = 0.0f;
        }
    }

    /*
     * "Previous" pilot symbol is one modem frame above.
     */
    st = ofdm->rxbufst + ofdm->samplespersymbol + 1 + ofdm->sample_point;
    en = st + ofdm->m;

    complex float work[ofdm->m];

    /* down-convert at current timing instant------------------------------- */

    for (k = 0, j = st; j < en; k++, j++) {
        work[k] = ofdm->rxbuf[j] * cmplxconj(woff_est * j);
    }

    /*
     * Each symbol is of course ofdm->samplespersymbol samples long and
     * becomes Nc+2 carriers after DFT.
     *
     * We put this carrier pilot symbol at the top of our matrix:
     *
     * 1 .................. Nc+2
     *
     * +----------------------+
     * |    Previous Pilot    |  rx_sym[0]
     * +----------------------+
     * |                      |
     *
     */
    dft(ofdm, ofdm->rx_sym[0], work);

    /*
     * "This" pilot comes after the extra symbol allotted at the top, and after
     * the "previous" pilot and previous data symbols (let's call it, the previous
     * modem frame).
     *
     * So we will now be starting at "this" pilot symbol, and continuing to the
     * "next" pilot symbol.
     *
     * In this routine we also process the current data symbols.
     */
    for (rr = 0; rr < (ofdm->ns + 1); rr++) {
        st = ofdm->rxbufst + ofdm->samplespersymbol + ofdm->samplesperframe + (rr * ofdm->samplespersymbol) + 1 + ofdm->sample_point;
        en = st + ofdm->m;

        /* down-convert at current timing instant---------------------------------- */

        for (k = 0, j = st; j < en; k++, j++) {
            work[k] = ofdm->rxbuf[j] * cmplxconj(woff_est * j);
        }

        /*
         * We put these Nc+2 carrier symbols into our matrix after the previous pilot:
         *
         * 1 .................. Nc+2
         * |    Previous Pilot    |  rx_sym[0]
         * +----------------------+
         * |      This Pilot      |  rx_sym[1]
         * +----------------------+
         * |         Data         |  rx_sym[2]
         * +----------------------+
         * |         Data         |  rx_sym[3]
         * +----------------------+
         * |         Data         |  rx_sym[4]
         * +----------------------+
         * |         Data         |  rx_sym[5]
         * +----------------------+
         * |         Data         |  rx_sym[6]
         * +----------------------+
         * |         Data         |  rx_sym[7]
         * +----------------------+
         * |         Data         |  rx_sym[8]
         * +----------------------+
         * |      Next Pilot      |  rx_sym[9]
         * +----------------------+
         * |                      |  rx_sym[10]
         */
        dft(ofdm, ofdm->rx_sym[rr + 1], work);
    }

    /*
     * OK, now we want to process to the "future" pilot symbol. This is after
     * the "next" modem frame.
     *
     * We are ignoring the data symbols between the "next" pilot and "future" pilot.
     * We only want the "future" pilot symbol, to perform the averaging of all pilots.
     */
    st = ofdm->rxbufst + ofdm->samplespersymbol + (3 * ofdm->samplesperframe) + 1 + ofdm->sample_point;
    en = st + ofdm->m;

    /* down-convert at current timing instant------------------------------- */

    for (k = 0, j = st; j < en; k++, j++) {
        work[k] = ofdm->rxbuf[j] * cmplxconj(woff_est * j);
    }

    /*
     * We put the future pilot after all the previous symbols in the matrix:
     *
     * 1 .................. Nc+2
     *
     * |                      |  rx_sym[9]
     * +----------------------+
     * |     Future Pilot     |  rx_sym[10]
     * +----------------------+
     */
    dft(ofdm, ofdm->rx_sym[ofdm->ns + 2], work);

    /*
     * We are finished now with the DFT and down conversion
     * From here on down we are in the frequency domain
     */

    /* est freq err based on all carriers ---------------------------------- */

    if (ofdm->foff_est_en == true) {
        /*
         * sym[1] is 'this' pilot symbol, sym[9] is 'next' pilot symbol.
         *
         * By subtracting the two averages of these pilots, we find the frequency
         * by the change in phase over time.
         */
        complex float freq_err_rect =
                conjf(vector_sum(ofdm->rx_sym[1], ofdm->nc + 2)) *
                vector_sum(ofdm->rx_sym[ofdm->ns + 1], ofdm->nc + 2);

        /* prevent instability in atan(im/re) when real part near 0 */

        freq_err_rect += 1E-6f;

        float freq_err_hz = cargf(freq_err_rect) * ofdm->rs / (TAU * ofdm->ns);
        if (ofdm->foff_limiter) {
            /* optionally tame updates in low SNR channels */
            if (freq_err_hz >  1.0) freq_err_hz = 1.0;
            if (freq_err_hz < -1.0) freq_err_hz = -1.0;
        }
        ofdm->foff_est_hz += (ofdm->foff_est_gain * freq_err_hz);
    }

    /* OK - now estimate and correct pilot phase  -------------------------- */

    complex float aphase_est_pilot_rect;
    float aphase_est_pilot[ofdm->nc + 2];
    float aamp_est_pilot[ofdm->nc + 2];

    for (i = 0; i < (ofdm->nc + 2); i++) {
        aphase_est_pilot[i] = 10.0f;
        aamp_est_pilot[i] = 0.0f;
    }

    for (i = 1; i < (ofdm->nc + 1); i++) { /* ignore first and last carrier for count */
        if (ofdm->phase_est_bandwidth == low_bw) {
            complex float symbol[3];

            /*
             * Use all pilots normally, results in low SNR performance,
             * but will fall over in high Doppler propagation
             *
             * Basically we divide the Nc+2 pilots into groups of 3
             * Then average the phase surrounding each of the data symbols.
             */
            for (k = 0, j = (i - 1); k < 3; k++, j++) {
                symbol[k] = ofdm->rx_sym[1][j] * conjf(ofdm->pilots[j]); /* this pilot conjugate */
            }

            aphase_est_pilot_rect = vector_sum(symbol, 3);

            for (k = 0, j = (i - 1); k < 3; k++, j++) {
                symbol[k] = ofdm->rx_sym[ofdm->ns + 1][j] * conjf(ofdm->pilots[j]); /* next pilot conjugate */
            }

            aphase_est_pilot_rect += vector_sum(symbol, 3);

            /* use pilots in past and future */

            for (k = 0, j = (i - 1); k < 3; k++, j++) {
                symbol[k] = ofdm->rx_sym[0][j] * conjf(ofdm->pilots[j]); /* previous pilot */
            }

            aphase_est_pilot_rect += vector_sum(symbol, 3);

            for (k = 0, j = (i - 1); k < 3; k++, j++) {
                symbol[k] = ofdm->rx_sym[ofdm->ns + 2][j] * conjf(ofdm->pilots[j]); /* future pilot */
            }

            aphase_est_pilot_rect += vector_sum(symbol, 3);

            /* amplitude is estimated over 12 pilots */
            aphase_est_pilot_rect /= 12.0f;

            aphase_est_pilot[i] = cargf(aphase_est_pilot_rect);
            aamp_est_pilot[i] = cabsf(aphase_est_pilot_rect);
        } else {
            assert(ofdm->phase_est_bandwidth == high_bw);

            /*
             * Use only symbols at 'this' and 'next' to quickly track changes
             * in phase due to high Doppler spread in propagation (no neighbor averaging).
             *
             * As less pilots are averaged, low SNR performance will be poorer
             */
            aphase_est_pilot_rect = ofdm->rx_sym[1][i] * conjf(ofdm->pilots[i]);             /* "this" pilot conjugate */
            aphase_est_pilot_rect += ofdm->rx_sym[ofdm->ns + 1][i] * conjf(ofdm->pilots[i]); /* "next" pilot conjugate */

            /* we estimate over 2 pilots */
            aphase_est_pilot_rect /= 2.0f;
            aphase_est_pilot[i] = cargf(aphase_est_pilot_rect);

            if (ofdm->amp_est_mode == 0) {
                // legacy 700D ampl est method
                aamp_est_pilot[i] = cabsf(aphase_est_pilot_rect);
            } else {
                aamp_est_pilot[i] = cabsf(ofdm->rx_sym[1][i]) + cabsf(ofdm->rx_sym[ofdm->ns + 1][i])/2.0;
            }
        }

        aphase_est_pilot[i] = cargf(aphase_est_pilot_rect);
        aamp_est_pilot[i] = cabsf(aphase_est_pilot_rect);
    }

    /*
     * correct the phase offset using phase estimate, and demodulate
     * bits, separate loop as it runs across cols (carriers) to get
     * frame bit ordering correct
     */
    complex float rx_corr;
    int abit[2];
    int bit_index = 0;
    float sum_amp = 0.0f;

    for (rr = 0; rr < ofdm->rowsperframe; rr++) {
        /*
         * Note the i starts with the second carrier, ends with Nc+1.
         * so we ignore the first and last carriers.
         *
         * Also note we are using sym[2..8] or the seven data symbols.
         */
        for (i = 1; i < (ofdm->nc + 1); i++) {
            if (ofdm->phase_est_en == true) {
                if (ofdm->dpsk_en == true) {
                    /* differential detection, using pilot as reference at start of frame */
                    rx_corr = ofdm->rx_sym[rr + 2][i] * cmplxconj(cargf(ofdm->rx_sym[rr + 1][i]));
                } else  {
                    /* regular coherent detection */
                    rx_corr = ofdm->rx_sym[rr + 2][i] * cmplxconj(aphase_est_pilot[i]);
                }
            } else {
                rx_corr = ofdm->rx_sym[rr + 2][i];
            }

            /*
             * Output complex data symbols after phase correction;
             * (_np = no pilots) the pilot symbols have been removed
             */
            ofdm->rx_np[(rr * ofdm->nc) + (i - 1)] = rx_corr;

            /*
             * Note even though amp ests are the same for each col,
             * the FEC decoder likes to have one amplitude per symbol
             * so convenient to log them all
             */
            ofdm->rx_amp[(rr * ofdm->nc) + (i - 1)] = aamp_est_pilot[i];
            sum_amp += aamp_est_pilot[i];

            /*
             * Note like amps in this implementation phase ests are the
             * same for each col, but we log them for each symbol anyway
             */
            ofdm->aphase_est_pilot_log[(rr * ofdm->nc) + (i - 1)] = aphase_est_pilot[i];

            if (ofdm->bps == 1) {
                rx_bits[bit_index++] = crealf(rx_corr) > 0.0f;
            } else if (ofdm->bps == 2) {
                /*
                 * Only one final task, decode what quadrant the phase
                 * is in, and return the dibits
                 */
                qpsk_demod(rx_corr, abit);
                rx_bits[bit_index++] = abit[1];
                rx_bits[bit_index++] = abit[0];
            }
        }
    }

    /* update mean amplitude estimate for LDPC decoder scaling */

    ofdm->mean_amp = 0.9f * ofdm->mean_amp + 0.1f * sum_amp / (ofdm->rowsperframe * ofdm->nc);

    /* Adjust nin to take care of sample clock offset */

    ofdm->nin = ofdm->samplesperframe;

    if (ofdm->timing_en == true) {
        ofdm->clock_offset_counter += (prev_timing_est - ofdm->timing_est);

        int thresh = ofdm->samplespersymbol / 8;
        int tshift = ofdm->samplespersymbol / 4;

        if (ofdm->timing_est > thresh) {
            ofdm->nin = ofdm->samplesperframe + tshift;
            ofdm->timing_est -= tshift;
            ofdm->sample_point -= tshift;
        } else if (ofdm->timing_est < -thresh) {
            ofdm->nin = ofdm->samplesperframe - tshift;
            ofdm->timing_est += tshift;
            ofdm->sample_point += tshift;
        }
    }

    // use internal rxbuf samples if they are available
    int rxbufst_next = ofdm->rxbufst + ofdm->nin;
    if (rxbufst_next + ofdm->nrxbufmin <= ofdm->nrxbuf) {
        ofdm->rxbufst = rxbufst_next;
        ofdm->nin = 0;
    }
}


/*
 * Returns an estimate of Es/No in dB - see esno_est.m for more info
 */
float ofdm_esno_est_calc(complex float *rx_sym, int nsym) {

    float sig_var = 0; 
    float step = 1.0f/nsym;
    for (int i = 0; i < nsym; i++)
        sig_var += (cnormf(rx_sym[i]) * step);
    float sig_rms = sqrtf(sig_var);

    float sum_x = 0.0f; float sum_xx = 0.0f; int n = 0;
    for (int i = 0; i < nsym; i++) {
        complex float s = rx_sym[i];

        if (cabsf(s) > sig_rms) {
            if (fabs(crealf(s)) > fabs(cimagf(s))) {
                sum_x += cimagf(s);
                sum_xx += cimagf(s) * cimagf(s);
            } else {
                sum_x += crealf(s);
                sum_xx += crealf(s) * crealf(s);                
            }
            n++;
        }
    }

    float noise_var;
    if (n > 1)
        noise_var = (n * sum_xx - sum_x * sum_x) / (n * (n - 1));
    else
        noise_var = sig_var;
    noise_var *= 2.0f;
    
    float EsNodB = 10.0f * log10f((1E-12f + sig_var) / (1E-12f + noise_var));
    assert(isnan(EsNodB) == 0);
    return EsNodB;
}


float ofdm_snr_from_esno(struct OFDM *ofdm, float EsNodB) {
    float cyclic_power = 10.0f * log10f((float)(ofdm->ncp + ofdm->m) / ofdm->m);
    return EsNodB + 10.0f * log10f((float)(ofdm->nc * ofdm->rs) / 3000.0f) + cyclic_power;
}

/*
 * state machine for 700D/2020
 */
void ofdm_sync_state_machine_voice1(struct OFDM *ofdm, uint8_t *rx_uw) {
    int i;

    State next_state = ofdm->sync_state;

    ofdm->sync_start = false;
    ofdm->sync_end = false;

    if (ofdm->sync_state == search) {
        if (ofdm->timing_valid) {
            ofdm->frame_count = 0;
            ofdm->sync_counter = 0;
            ofdm->sync_start = true;
            ofdm->clock_offset_counter = 0;
            next_state = trial;
        }
    }

    if ((ofdm->sync_state == synced) || (ofdm->sync_state == trial)) {
        ofdm->frame_count++;

        /*
         * freq offset est may be too far out, and has aliases every 1/Ts, so
         * we use a Unique Word to get a really solid indication of sync.
         */
        ofdm->uw_errors = 0;

        for (i = 0; i < ofdm->nuwbits; i++) {
            ofdm->uw_errors += ofdm->tx_uw[i] ^ rx_uw[i];
        }

        /*
         * during trial sync we don't tolerate errors so much, we look
         * for 3 consecutive frames with low error rate to confirm sync
         */
        if (ofdm->sync_state == trial) {
            if (ofdm->uw_errors > 2) {
                /* if we exceed thresh stay in trial sync */

                ofdm->sync_counter++;
                ofdm->frame_count = 0;
            }

            if (ofdm->sync_counter == 2) {
                /* if we get two bad frames drop sync and start again */

                next_state = search;
                ofdm->phase_est_bandwidth = high_bw;
            }

            if (ofdm->frame_count == 4) {
                /* three good frames, sync is OK! */

                next_state = synced;
                /* change to low bandwidth, but more accurate phase estimation */
                /* but only if not locked to high */

                if (ofdm->phase_est_bandwidth_mode != LOCKED_PHASE_EST) {
                    ofdm->phase_est_bandwidth = low_bw;
                }
            }
        }

        /* once we have synced up we tolerate a higher error rate to wait out fades */

        if (ofdm->sync_state == synced) {
            if (ofdm->uw_errors > 2) {
                ofdm->sync_counter++;
            } else {
                ofdm->sync_counter = 0;
            }

            if ((ofdm->sync_mode == autosync) && (ofdm->sync_counter > 6)) {
                /* run of consecutive bad frames ... drop sync */

                next_state = search;
                ofdm->phase_est_bandwidth = high_bw;
            }
        }
    }

    ofdm->last_sync_state = ofdm->sync_state;
    ofdm->sync_state = next_state;
}

/*
 *  data (streaming mode) state machine
 */
void ofdm_sync_state_machine_data_streaming(struct OFDM *ofdm, uint8_t *rx_uw) {
    State next_state = ofdm->sync_state;
    int i;

    ofdm->sync_start = ofdm->sync_end = 0;

    if (ofdm->sync_state == search) {
        if (ofdm->timing_valid != 0) {
            ofdm->sync_start = true;
            ofdm->sync_counter = 0;
            next_state = trial;
        }
    }

    ofdm->uw_errors = 0;        
    for (i = 0; i < ofdm->nuwbits; i++) {
        ofdm->uw_errors += ofdm->tx_uw[i] ^ rx_uw[i];
    }

    if (ofdm->sync_state == trial) {
        if (ofdm->uw_errors < ofdm->bad_uw_errors) {
            next_state = synced;
            ofdm->packet_count = 0;
            ofdm->modem_frame = ofdm->nuwframes;
        } else {
            ofdm->sync_counter++;

            if (ofdm->sync_counter > ofdm->np) {
                next_state = search;
            }
        }
    }

    // Note if frameperburst==0 we don't ever lose sync, which is useful for 
    // stream based testing or external control of state machine

    if (ofdm->sync_state == synced) {
        ofdm->modem_frame++;

        if (ofdm->modem_frame >= ofdm->np) {
            ofdm->modem_frame = 0;
            ofdm->packet_count++;
            if (ofdm->packetsperburst) {
                if (ofdm->packet_count >= ofdm->packetsperburst)
                    next_state = search;
            }
        }
        
    }

    ofdm->last_sync_state = ofdm->sync_state;
    ofdm->sync_state = next_state;
}

/*
 *  data (burst mode) state machine
 */
void ofdm_sync_state_machine_data_burst(struct OFDM *ofdm, uint8_t *rx_uw) {
    State next_state = ofdm->sync_state;
    int i;

    ofdm->sync_start = ofdm->sync_end = 0;

    if (ofdm->sync_state == search) {
        if (ofdm->timing_valid != 0) {
            ofdm->sync_start = true;
            ofdm->sync_counter = 0;
            next_state = trial;
        }
    }

    ofdm->uw_errors = 0;
    for (i = 0; i < ofdm->nuwbits; i++) {
        ofdm->uw_errors += ofdm->tx_uw[i] ^ rx_uw[i];
    }

    /* pre or post-amble has told us this is the start of the packet.  Confirm we 
      have a valid frame by checking the UW after the modem frames containing
      the UW have been received */
    if (ofdm->sync_state == trial) {
        ofdm->sync_counter++;
        if (ofdm->sync_counter == ofdm->nuwframes) {
            if (ofdm->uw_errors < ofdm->bad_uw_errors) {
                next_state = synced;
                ofdm->packet_count = 0;
                ofdm->modem_frame = ofdm->nuwframes;    
            } else {
               next_state = search;
               // reset rxbuf to make sure we only ever do a postamble loop once through same samples
               ofdm->rxbufst = ofdm->nrxbufhistory;
               for(int i=0; i<ofdm->nrxbuf; i++) ofdm->rxbuf[i] = 0;
               ofdm->uw_fails++;
           }
       }
   }
       
    if (ofdm->sync_state == synced) {
        ofdm->modem_frame++;
        if (ofdm->modem_frame >= ofdm->np) {
            ofdm->modem_frame = 0;
            ofdm->packet_count++;
            if (ofdm->packetsperburst) {
                if (ofdm->packet_count >= ofdm->packetsperburst) {
                    next_state = search;
                    // reset rxbuf to make sure we only ever do a postamble loop once through same samples
                    ofdm->rxbufst = ofdm->nrxbufhistory;
                    for(int i=0; i<ofdm->nrxbuf; i++) ofdm->rxbuf[i] = 0;                
                }
            }
        }    
    }

    ofdm->last_sync_state = ofdm->sync_state;
    ofdm->sync_state = next_state;
}


void ofdm_sync_state_machine_voice2(struct OFDM *ofdm, uint8_t *rx_uw) {
    int i;

    State next_state = ofdm->sync_state;

    ofdm->sync_start = false;
    ofdm->sync_end = false;

    if (ofdm->sync_state == search) {
        if (ofdm->timing_valid) {
            ofdm->frame_count = 0;
            ofdm->sync_counter = 0;
            ofdm->sync_start = true;
            ofdm->clock_offset_counter = 0;
            next_state = trial;
        }
    }

    if ((ofdm->sync_state == synced) || (ofdm->sync_state == trial)) {
        ofdm->frame_count++;

        ofdm->uw_errors = 0;
        for (i = 0; i < ofdm->nuwbits; i++) {
            ofdm->uw_errors += ofdm->tx_uw[i] ^ rx_uw[i];
        }

        if (ofdm->sync_state == trial) {
            if (ofdm->uw_errors <= ofdm->bad_uw_errors)
                next_state = synced;
            else
                next_state = search;
        }

        if (ofdm->sync_state == synced) {
            if (ofdm->uw_errors > ofdm->bad_uw_errors) {
                ofdm->sync_counter++;
            } else {
                ofdm->sync_counter = 0;
            }

            if (ofdm->sync_counter == 6) {
                /* run of consecutive bad frames ... drop sync */
                next_state = search;
            }
        }
    }

    ofdm->last_sync_state = ofdm->sync_state;
    ofdm->sync_state = next_state;
}


/* mode based dispatcher for sync state machines */
void ofdm_sync_state_machine(struct OFDM *ofdm, uint8_t *rx_uw) {
    if (!strcmp(ofdm->state_machine, "voice1"))
        ofdm_sync_state_machine_voice1(ofdm, rx_uw);
    if (!strcmp(ofdm->state_machine, "data")) {
        if (strcmp(ofdm->data_mode,"streaming") == 0)
            ofdm_sync_state_machine_data_streaming(ofdm, rx_uw);
        else
            ofdm_sync_state_machine_data_burst(ofdm, rx_uw);
    }
    if (!strcmp(ofdm->state_machine, "voice2"))
        ofdm_sync_state_machine_voice2(ofdm, rx_uw);
}


/*---------------------------------------------------------------------------* \

  FUNCTIONS...: ofdm_set_sync
  AUTHOR......: David Rowe
  DATE CREATED: May 2018

  External control of sync state machine.
  Ensure this is called in the same thread as ofdm_sync_state_machine().

\*---------------------------------------------------------------------------*/

void ofdm_set_sync(struct OFDM *ofdm, int sync_cmd) {
    assert(ofdm != NULL);

    switch (sync_cmd) {
        case UN_SYNC:
            /* force manual unsync, which will cause sync state machine to 
               have re-attempt sync */
            ofdm->sync_state = search;
            /* clear rxbuf so we don't try to sync on any existing OFDM signals
               in buffer */
            for (int i = 0; i < ofdm->nrxbuf; i++) ofdm->rxbuf[i] = 0.0f;
            break;
        case AUTO_SYNC:
            /* normal operating mode - sync state machine decides when to unsync */
            ofdm->sync_mode = autosync;
            break;
        case MANUAL_SYNC:
            /*
             * allow sync state machine to sync, but not to unsync, the
             * operator will decide that manually
             */
            ofdm->sync_mode = manualsync;
            break;
        default:
            assert(0);
    }
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: ofdm_get_demod_stats()
  AUTHOR......: David Rowe
  DATE CREATED: May 2018

  Fills stats structure with a bunch of demod information. Call once per
  packet.

\*---------------------------------------------------------------------------*/

void ofdm_get_demod_stats(struct OFDM *ofdm, struct MODEM_STATS *stats, complex float *rx_syms, int Nsymsperpacket) {
    stats->Nc = ofdm->nc;
    assert(stats->Nc <= MODEM_STATS_NC_MAX);

    float EsNodB = ofdm_esno_est_calc(rx_syms, Nsymsperpacket);
    float SNR3kdB = ofdm_snr_from_esno(ofdm, EsNodB);
    
    if (strlen(ofdm->data_mode))
        /* no smoothing as we have a large number of symbols per packet */
        stats->snr_est = SNR3kdB;
    else {        
        /* in voice modes we further smooth SNR est, fast attack, slow decay */
        if (SNR3kdB > stats->snr_est)
            stats->snr_est = SNR3kdB;
        else
            stats->snr_est = 0.9f * stats->snr_est + 0.1f * SNR3kdB;
    }
    stats->sync = ((ofdm->sync_state == synced) || (ofdm->sync_state == trial));
    stats->foff = ofdm->foff_est_hz;
    stats->rx_timing = ofdm->timing_est;

    float total = ofdm->frame_count * ofdm->samplesperframe;
    stats->clock_offset = 0;
    if (total != 0.0f) {
        stats->clock_offset = ofdm->clock_offset_counter / total;
    }

    stats->sync_metric = ofdm->timing_mx;
    stats->pre = ofdm->pre;
    stats->post = ofdm->post;
    stats->uw_fails = ofdm->uw_fails;
    
#ifndef __EMBEDDED__
    assert(Nsymsperpacket % ofdm->nc == 0);
    int Nrowsperpacket = Nsymsperpacket/ofdm->nc;
    assert(Nrowsperpacket <= MODEM_STATS_NR_MAX);
    stats->nr = Nrowsperpacket;
    for (int c = 0; c < ofdm->nc; c++) {
        for (int r = 0; r < Nrowsperpacket; r++) {
            complex float rot = rx_syms[r * ofdm->nc + c] * cmplx(ROT45);
            stats->rx_symbols[r][c].real = crealf(rot);
            stats->rx_symbols[r][c].imag = cimagf(rot);
        }
    }
#endif
}

/*
 * Assemble packet of bits from UW, payload bits, and txt bits
 */
void ofdm_assemble_qpsk_modem_packet(struct OFDM *ofdm, uint8_t modem_frame[],
        uint8_t payload_bits[], uint8_t txt_bits[]) {
    int s, t;

    int p = 0;
    int u = 0;

    for (s = 0; s < (ofdm->bitsperpacket - ofdm->ntxtbits); s++) {
        if ((u < ofdm->nuwbits) && (s == ofdm->uw_ind[u])) {
            modem_frame[s] = ofdm->tx_uw[u++];
        } else {
            modem_frame[s] = payload_bits[p++];
        }
    }

    assert(u == ofdm->nuwbits);
    assert(p == (ofdm->bitsperpacket - ofdm->nuwbits - ofdm->ntxtbits));

    for (t = 0; s < ofdm->bitsperframe; s++, t++) {
        modem_frame[s] = txt_bits[t];
    }

    assert(t == ofdm->ntxtbits);
}

/*
 * Assemble packet of symbols from UW, payload symbols, and txt bits
 */
void ofdm_assemble_qpsk_modem_packet_symbols(struct OFDM *ofdm, complex float modem_packet[],
  COMP payload_syms[], uint8_t txt_bits[]) {
    complex float *payload = (complex float *) &payload_syms[0]; // complex has same memory layout
    int Nsymsperpacket = ofdm->bitsperpacket / ofdm->bps;
    int Nuwsyms = ofdm->nuwbits / ofdm->bps;
    int Ntxtsyms = ofdm->ntxtbits / ofdm->bps;
    int dibit[2];
    int s, t;

    int p = 0;
    int u = 0;

    assert(ofdm->bps == 2);  /* this only works for QPSK at this stage (e.g. modem packet mod) */

    for (s = 0; s < (Nsymsperpacket - Ntxtsyms); s++) {
        if ((u < Nuwsyms) && (s == ofdm->uw_ind_sym[u])) {
            modem_packet[s] = ofdm->tx_uw_syms[u++];
        } else {
            modem_packet[s] = payload[p++];
        }
    }

    assert(u == Nuwsyms);
    assert(p == (Nsymsperpacket - Nuwsyms - Ntxtsyms));

    for (t = 0; s < Nsymsperpacket; s++, t += 2) {
        dibit[1] = txt_bits[t    ] & 0x1;
        dibit[0] = txt_bits[t + 1] & 0x1;
        modem_packet[s] = qpsk_mod(dibit);
    }

    assert(t == ofdm->ntxtbits);
}

/*
 * Disassemble a received packet of symbols into UW bits and payload data symbols
 */
void ofdm_disassemble_qpsk_modem_packet(struct OFDM *ofdm, complex float rx_syms[], float rx_amps[],
                                        COMP codeword_syms[], float codeword_amps[], short txt_bits[])
{
    complex float *codeword = (complex float *) &codeword_syms[0]; // complex has same memory layout
    int Nsymsperpacket = ofdm->bitsperpacket / ofdm->bps;
    int Nuwsyms = ofdm->nuwbits / ofdm->bps;
    int Ntxtsyms = ofdm->ntxtbits / ofdm->bps;
    int dibit[2];
    int s, t;

    int p = 0;
    int u = 0;

    assert(ofdm->bps == 2);  /* this only works for QPSK at this stage */

    for (s = 0; s < (Nsymsperpacket - Ntxtsyms); s++) {
        if ((u < Nuwsyms) && (s == ofdm->uw_ind_sym[u])) {
            u++;
        } else {
            codeword[p] = rx_syms[s];
            codeword_amps[p] = rx_amps[s];
            p++;
        }
    }

    assert(u == Nuwsyms);
    assert(p == (Nsymsperpacket - Nuwsyms - Ntxtsyms));

    for (t = 0; s < Nsymsperpacket; s++, t += 2) {
        qpsk_demod(rx_syms[s], dibit);

        txt_bits[t    ] = dibit[1];
        txt_bits[t + 1] = dibit[0];
    }

    assert(t == ofdm->ntxtbits);
}

/*
 * Disassemble a received packet of symbols into UW bits and payload data symbols
 */
void ofdm_disassemble_qpsk_modem_packet_with_text_amps(
                                        struct OFDM *ofdm, complex float rx_syms[], float rx_amps[],
                                        COMP codeword_syms[], float codeword_amps[], short txt_bits[],
                                        int* textIndex)
{
    complex float *codeword = (complex float *) &codeword_syms[0]; // complex has same memory layout
    int Nsymsperpacket = ofdm->bitsperpacket / ofdm->bps;
    int Nuwsyms = ofdm->nuwbits / ofdm->bps;
    int Ntxtsyms = ofdm->ntxtbits / ofdm->bps;
    int dibit[2];
    int s, t;

    int p = 0;
    int u = 0;

    assert(ofdm->bps == 2);  /* this only works for QPSK at this stage */
    assert(textIndex != NULL);
    
    for (s = 0; s < (Nsymsperpacket - Ntxtsyms); s++) {
        if ((u < Nuwsyms) && (s == ofdm->uw_ind_sym[u])) {
            u++;
        } else {
            codeword[p] = rx_syms[s];
            codeword_amps[p] = rx_amps[s];
            p++;
        }
    }

    assert(u == Nuwsyms);
    assert(p == (Nsymsperpacket - Nuwsyms - Ntxtsyms));

    *textIndex = s;
    for (t = 0; s < Nsymsperpacket; s++, t += 2) {
        qpsk_demod(rx_syms[s], dibit);

        txt_bits[t    ] = dibit[1];
        txt_bits[t + 1] = dibit[0];
    }

    assert(t == ofdm->ntxtbits);
}

/*
 * Extract just the UW from the packet
 */
void ofdm_extract_uw(struct OFDM *ofdm, complex float rx_syms[], float rx_amps[], uint8_t rx_uw[]) {
    int Nsymsperframe = ofdm->bitsperframe / ofdm->bps;
    int Nuwsyms = ofdm->nuwbits / ofdm->bps;
    int dibit[2];
    int s,u;

    assert(ofdm->bps == 2);  /* this only works for QPSK at this stage (e.g. UW demod) */

    for (s = 0, u = 0; s < Nsymsperframe*ofdm->nuwframes; s++) {
        if ((u < Nuwsyms) && (s == ofdm->uw_ind_sym[u])) {
            qpsk_demod(rx_syms[s], dibit);
            rx_uw[2 * u    ] = dibit[1];
            rx_uw[2 * u + 1] = dibit[0];
            u++;
        }
    }

    assert(u == Nuwsyms);
}

/*
 * Pseudo-random number generator that we can implement in C with
 * identical results to Octave.  Returns an unsigned int between 0
 * and 32767.  Used for generating test frames of various lengths.
 */
void ofdm_rand(uint16_t r[], int n) {
    ofdm_rand_seed(r, n, 1);
}

void ofdm_rand_seed(uint16_t r[], int n, uint64_t seed) {
    for (int i = 0; i < n; i++) {
        seed = (1103515245l * seed + 12345) % 32768;
        r[i] = seed;
    }
}

void ofdm_generate_payload_data_bits(uint8_t payload_data_bits[], int n) {
    uint16_t r[n];
    int i;

    ofdm_rand(r, n);

    for (i = 0; i < n; i++) {
        payload_data_bits[i] = r[i] > 16384;
    }
}

void ofdm_generate_preamble(struct OFDM *ofdm, COMP *tx_preamble, int seed) {
  // need to modify bits per packet to set up pre-amble of a few modem frames in length
  struct OFDM ofdm_preamble;
  memcpy(&ofdm_preamble, ofdm, sizeof(struct OFDM));
  ofdm_preamble.np = 1;
  ofdm_preamble.bitsperpacket = ofdm_preamble.bitsperframe;
  uint16_t r[ofdm_preamble.bitsperpacket];
  ofdm_rand_seed(r, ofdm_preamble.bitsperpacket, seed);
  int preamble_bits[ofdm_preamble.bitsperpacket];
  for(int i=0; i<ofdm_preamble.bitsperpacket; i++) 
      preamble_bits[i] = r[i] > 16384;
  // ensures the signal passes through hilbert clipper unchanged
  ofdm_preamble.amp_scale = 1.0; ofdm_preamble.tx_bpf_en = false;
  ofdm_mod(&ofdm_preamble, tx_preamble, preamble_bits);
}

void ofdm_print_info(struct OFDM *ofdm) {
    char *syncmode[] = {
        "unsync",
        "autosync",
        "manualsync"
    };
    char *phase_est_bandwidth_mode[] = {
        "auto",
        "locked_high"
    };

    fprintf(stderr, "ofdm->tx_centre = %g\n", (double)ofdm->tx_centre);
    fprintf(stderr, "ofdm->rx_centre = %g\n", (double)ofdm->rx_centre);
    fprintf(stderr, "ofdm->fs = %g\n", (double)ofdm->fs);
    fprintf(stderr, "ofdm->ts = %g\n", (double)ofdm->ts);
    fprintf(stderr, "ofdm->rs = %g\n", (double)ofdm->rs);
    fprintf(stderr, "ofdm->tcp = %g\n", (double)ofdm->tcp);
    fprintf(stderr, "ofdm->inv_m = %g\n", (double)ofdm->inv_m);
    fprintf(stderr, "ofdm->tx_nlower = %g\n", (double)ofdm->tx_nlower);
    fprintf(stderr, "ofdm->rx_nlower = %g\n", (double)ofdm->rx_nlower);
    fprintf(stderr, "ofdm->doc = %g\n", (double)ofdm->doc);
    fprintf(stderr, "ofdm->timing_mx_thresh = %g\n", (double)ofdm->timing_mx_thresh);
    fprintf(stderr, "ofdm->nc = %d\n", ofdm->nc);
    fprintf(stderr, "ofdm->np = %d\n", ofdm->np);
    fprintf(stderr, "ofdm->ns = %d\n", ofdm->ns);
    fprintf(stderr, "ofdm->bps = %d\n", ofdm->bps);
    fprintf(stderr, "ofdm->m = %d\n", ofdm->m);
    fprintf(stderr, "ofdm->ncp = %d\n", ofdm->ncp);
    fprintf(stderr, "ofdm->ftwindowwidth = %d\n", ofdm->ftwindowwidth);
    fprintf(stderr, "ofdm->bitsperframe = %d\n", ofdm->bitsperframe);
    fprintf(stderr, "ofdm->bitsperpacket = %d\n", ofdm->bitsperpacket);
    fprintf(stderr, "ofdm->rowsperframe = %d\n", ofdm->rowsperframe);
    fprintf(stderr, "ofdm->samplespersymbol = %d\n", ofdm->samplespersymbol);
    fprintf(stderr, "ofdm->samplesperframe = %d\n", ofdm->samplesperframe);
    fprintf(stderr, "ofdm->max_samplesperframe = %d\n", ofdm->max_samplesperframe);
    fprintf(stderr, "ofdm->nrxbuf = %d\n", ofdm->nrxbuf);
    fprintf(stderr, "ofdm->ntxtbits = %d\n", ofdm->ntxtbits);
    fprintf(stderr, "ofdm->nuwbits = %d\n", ofdm->nuwbits);
    fprintf(stderr, "ofdm->foff_est_gain = %g\n", (double)ofdm->foff_est_gain);
    fprintf(stderr, "ofdm->foff_est_hz = %g\n", (double)ofdm->foff_est_hz);
    fprintf(stderr, "ofdm->timing_mx = %g\n", (double)ofdm->timing_mx);
    fprintf(stderr, "ofdm->coarse_foff_est_hz = %g\n", (double)ofdm->coarse_foff_est_hz);
    fprintf(stderr, "ofdm->timing_norm = %g\n", (double)ofdm->timing_norm);
    fprintf(stderr, "ofdm->mean_amp = %g\n", (double)ofdm->mean_amp);
    fprintf(stderr, "ofdm->clock_offset_counter = %d\n", ofdm->clock_offset_counter);
    fprintf(stderr, "ofdm->verbose = %d\n", ofdm->verbose);
    fprintf(stderr, "ofdm->sample_point = %d\n", ofdm->sample_point);
    fprintf(stderr, "ofdm->timing_est = %d\n", ofdm->timing_est);
    fprintf(stderr, "ofdm->timing_valid = %d\n", ofdm->timing_valid);
    fprintf(stderr, "ofdm->nin = %d\n", ofdm->nin);
    fprintf(stderr, "ofdm->uw_errors = %d\n", ofdm->uw_errors);
    fprintf(stderr, "ofdm->sync_counter = %d\n", ofdm->sync_counter);
    fprintf(stderr, "ofdm->frame_count = %d\n", ofdm->frame_count);
    fprintf(stderr, "ofdm->sync_start = %s\n", ofdm->sync_start ? "true" : "false");
    fprintf(stderr, "ofdm->sync_end = %s\n", ofdm->sync_end ? "true" : "false");
    fprintf(stderr, "ofdm->sync_mode = %s\n", syncmode[ofdm->sync_mode]);
    fprintf(stderr, "ofdm->timing_en = %s\n", ofdm->timing_en ? "true" : "false");
    fprintf(stderr, "ofdm->foff_est_en = %s\n", ofdm->foff_est_en ? "true" : "false");
    fprintf(stderr, "ofdm->phase_est_en = %s\n", ofdm->phase_est_en ? "true" : "false");
    fprintf(stderr, "ofdm->tx_bpf_en = %s\n", ofdm->tx_bpf_en ? "true" : "false");
    fprintf(stderr, "ofdm->dpsk_en = %s\n", ofdm->dpsk_en ? "true" : "false");
    fprintf(stderr, "ofdm->phase_est_bandwidth_mode = %s\n", phase_est_bandwidth_mode[ofdm->phase_est_bandwidth_mode]);
}

// hilbert clipper
void ofdm_clip(complex float tx[], float clip_thresh, int n) {
    complex float sam;
    float mag;
    int   i;

    for(i=0; i<n; i++) {
        sam = tx[i];
        mag = cabsf(sam);
        if (mag > clip_thresh)  {
            sam *= clip_thresh/mag;
        }
        tx[i] = sam;
    }
 }
