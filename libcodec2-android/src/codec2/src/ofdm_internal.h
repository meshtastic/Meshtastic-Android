/*---------------------------------------------------------------------------*\

  FILE........: ofdm_internal.h
  AUTHORS.....: David Rowe & Steve Sampson
  DATE CREATED: June 2017

  OFDM Internal definitions.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2017 David Rowe

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

#ifndef OFDM_INTERNAL_H
#define OFDM_INTERNAL_H

#include <complex.h>
#include <stdbool.h>
#include <stdint.h>

#include "codec2_ofdm.h"
#include "filter.h"

#ifdef __cplusplus
extern "C"
{
#endif

#ifndef M_PI
#define M_PI        3.14159265358979323846f
#endif

#define TAU         (2.0f * M_PI)
#define ROT45       (M_PI / 4.0f)
#define MAX_UW_BITS 64

#define cmplx(value) (cosf(value) + sinf(value) * I)
#define cmplxconj(value) (cosf(value) + sinf(value) * -I)

/* modem state machine states */
typedef enum {
    search,
    trial,
    synced
} State;

typedef enum {
    unsync,             /* force sync state machine to lose sync, and search for new sync */
    autosync,           /* falls out of sync automatically */
    manualsync          /* fall out of sync only under operator control */
} Sync;

/* phase estimator bandwidth options */

typedef enum {
    low_bw,             /* can only track a narrow freq offset, but accurate         */
    high_bw             /* can track wider freq offset, but less accurate at low SNR */
} PhaseEstBandwidth;

/*
 * User-defined configuration for OFDM modem.  Used to set up
 * constants at init time, e.g. for different bit rate modems.
 */

struct OFDM_CONFIG {
    float tx_centre;   /* TX Centre Audio Frequency */
    float rx_centre;   /* RX Centre Audio Frequency */
    float fs;          /* Sample Frequency */
    float rs;          /* Symbol Rate */
    float ts;          /* symbol duration */
    float tcp;         /* Cyclic Prefix duration */
    float timing_mx_thresh;

    int nc;            /* Number of carriers */
    int ns;            /* Number of Symbol frames */
    int np;            /* number of modem frames per packet */
    int bps;           /* Bits per Symbol */
    int txtbits;       /* number of auxiliary data bits */
    int nuwbits;       /* number of unique word bits */
    int bad_uw_errors;
    int ftwindowwidth;
    int edge_pilots;
    char *state_machine;  /* name of sync state machine used for this mode */
    char *codename;       /* name of LDPC code used with this mode */
    uint8_t tx_uw[MAX_UW_BITS]; /* user defined unique word */
    int amp_est_mode;
    bool tx_bpf_en;       /* default clippedtx BPF state */
    bool foff_limiter;    /* tames freq offset updates in low SNR */
    float amp_scale;      /* used to scale Tx waveform to approx FREEDV_PEAK with clipper off */
    float clip_gain1;     /* gain we apply to Tx signal before clipping to control PAPR*/
    float clip_gain2;     /* gain we apply to Tx signal after clipping and BBF to control peak level */
    bool  clip_en;
    char mode[16];        /* OFDM mode in string form */
    char *data_mode;
    float fmin;
    float fmax;
};

struct OFDM {
    struct OFDM_CONFIG config;

    char mode[16];        /* mode in string form */
    /*
     * See 700D Part 4 Acquisition blog post and ofdm_dev.m routines
     * for how this was set
     */
    float timing_mx_thresh;

    int nc;
    int ns;              	 /* NS-1 = data symbols between pilots  */
    int bps; 	             /* Bits per symbol */
    int m; 	               /* duration of each symbol in samples */
    int ncp; 	             /* duration of CP in samples */
    int np;                /* number of modem frames per packet. In some modes we want */
                           /* the total packet of data to span multiple modem frames, e.g. HF data */
                           /* and/or when the FEC codeword is larger than the one */
                           /* modem frame.  In other modes (e.g. 700D/2020) Np=1, ie the modem frame */
                           /* is the same length as the packet/FEC frame. */
    int ftwindowwidth;
    int bitsperframe;      /* total bits in all data symbols in modem frame */
    int bitsperpacket;     /* total bits in all data symbols in a packet */
    int rowsperframe;
    int samplespersymbol;
    int samplesperframe;
    int nrxbufhistory;    /* extra storage at start of rxbuf to allow us to step back in time */
    int nrxbufmin;        /* min number of samples we need in rxbuf to process a modem frame */
    int rxbufst;          /* start of rxbuf window used for demod of current rx frame */
    int pre, post;        /* pre-amble and post-amble detections */
    int max_samplesperframe;
    int nuwframes;
    int nrxbuf;
    int ntxtbits;         /* reserve bits/frame for aux text information */
    int nuwbits;          /* number of unique word bits used to achieve packet frame sync */
    int bad_uw_errors;    /* threshold for UW detection check */
    int uw_fails;         /* number of times we exceeded bad_uw_errors and dropped sync */
    int edge_pilots;      /* insert pilots at 1 and Nc+2, to support low bandwidth phase est */
    char *data_mode;      /* "", "streaming", "burst"  */
    int packetsperburst;  /* for OFDM data modes, how many packets before we reset state machine */
    int amp_est_mode;     /* amplitude estimtor algorithm */
    float amp_scale;
    float clip_gain1;
    float clip_gain2;
    bool  clip_en;

    float tx_centre;      /* TX Center frequency */
    float rx_centre;      /* RX Center frequency */
    float fs;             /* Sample rate */
    float ts;             /* Symbol cycle time */
    float rs;             /* Symbol rate */
    float tcp;            /* Cyclic prefix duration */
    float tpacket;        /* time for one packet in ms */
    float inv_m;          /* 1/m */
    float tx_nlower;      /* TX lowest carrier freq */
    float rx_nlower;      /* RX lowest carrier freq */
    float doc;            /* division of radian circle */
    
    float fmin;
    float fmax;

    // Pointers

    struct quisk_cfFilter *tx_bpf;

    complex float *pilot_samples;
    complex float *rxbuf;
    complex float *pilots;
    complex float **rx_sym;
    complex float *rx_np;
    complex float *tx_uw_syms;
    COMP          *tx_preamble;
    COMP          *tx_postamble;

    float *rx_amp;
    float *aphase_est_pilot_log;

    uint8_t tx_uw[MAX_UW_BITS];
    int *uw_ind;
    int *uw_ind_sym;

    // State enums
    State sync_state;
    State last_sync_state;

    // Sync enums
    Sync sync_mode;

    // Phase enums
    PhaseEstBandwidth phase_est_bandwidth;

    int phase_est_bandwidth_mode;

    // Complex
    complex float foff_metric;

    // Float
    float foff_est_gain;
    bool  foff_limiter;
    float foff_est_hz;
    float timing_mx;
    float coarse_foff_est_hz;
    float timing_norm;
    float mean_amp;

    // Integer
    int clock_offset_counter;
    int verbose;
    int sample_point;
    int timing_est;
    int timing_valid;
    int ct_est;
    int nin;
    int uw_errors;
    int sync_counter;
    int frame_count;  /* general purpose counter of modem frames */
    int packet_count; /* data mode: number of packets received so far */ 
    int modem_frame;  /* increments for every modem frame in packet */
    
    // Boolean
    bool sync_start;
    bool sync_end;
    bool timing_en;
    bool foff_est_en;
    bool phase_est_en;
    bool tx_bpf_en;
    bool dpsk_en;
    bool postambledetectoren; /* allows us to optionally disable the postamble detector */
    
    char *codename;
    char *state_machine;
};

/* Prototypes */

complex float qpsk_mod(int *);
complex float qam16_mod(int *);
void qpsk_demod(complex float, int *);
void qam16_demod(complex float, int *);
void ofdm_txframe(struct OFDM *, complex float *, complex float []);
void ofdm_assemble_qpsk_modem_packet(struct OFDM *, uint8_t [], uint8_t [], uint8_t []);
void ofdm_assemble_qpsk_modem_packet_symbols(struct OFDM *, complex float [], COMP [], uint8_t []);
void ofdm_disassemble_qpsk_modem_packet(struct OFDM *, complex float rx_syms[], float rx_amps[], COMP [], float [], short []);
void ofdm_disassemble_qpsk_modem_packet_with_text_amps(struct OFDM *, complex float rx_syms[], float rx_amps[], COMP [], float [], short [], int*);
void ofdm_extract_uw(struct OFDM *ofdm, complex float rx_syms[], float rx_amps[], uint8_t rx_uw[]);
void ofdm_rand(uint16_t [], int);
void ofdm_rand_seed(uint16_t r[], int n, uint64_t seed) ;
void ofdm_generate_payload_data_bits(uint8_t data_bits[], int n);
void ofdm_generate_preamble(struct OFDM *ofdm, COMP *tx_preamble, int seed);
int ofdm_get_phase_est_bandwidth_mode(struct OFDM *);
void ofdm_set_phase_est_bandwidth_mode(struct OFDM *, int);
void ofdm_clip(complex float tx[], float clip_thresh, int n);
void ofdm_hilbert_clipper(struct OFDM *ofdm, complex float *tx, size_t n);
float ofdm_esno_est_calc(complex float *rx_sym, int nsym);
float ofdm_snr_from_esno(struct OFDM *ofdm, float EsNodB);
void ofdm_get_demod_stats(struct OFDM *ofdm, struct MODEM_STATS *stats, complex float *rx_syms, int Nsymsperpacket);

#ifdef __cplusplus
}
#endif

#endif
