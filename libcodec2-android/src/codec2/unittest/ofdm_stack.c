#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <complex.h>
#include <unistd.h>
#include <assert.h>

#include "comp.h"
#include "ofdm_internal.h"
#include "codec2_ofdm.h"
#include "test_bits_ofdm.h" /* payload_data_bits */
#include "mpdecode_core.h"

#define MAX_ERRORS        32

static int ofdm_bitsperframe;
static int ofdm_rowsperframe;
static int ofdm_nuwbits;
static int ofdm_ntxtbits;
static int ofdm_rx_offset;
static int ofdm_data_bitsperframe;
static int ofdm_samplesperframe;
static int ofdm_max_samplesperframe;
static int ofdm_rxbuf;
static int ofdm_m;
static int ofdm_ncp;

// Forwards
void run_modem(struct OFDM *ofdm, int tx_bits[], int rx_bits[], COMP tx_rx[]);
void dummy_code();

/////////////////////////////////////////////////////////////
///  MAIN()
int main(int argc, char *argv[]) {
    // Options
    int f, i, opt;
    int dummy = 0;  // flag to use dummy code
    int frames = 1; // how many frames
    int print = 0;  // flag to print all bits
    struct OFDM        *ofdm;
    struct OFDM_CONFIG *ofdm_config;

    ofdm = ofdm_create(NULL);
    assert(ofdm != NULL);

    /* Get a copy of the actual modem config */

    ofdm_config = ofdm_get_config_param(ofdm);

    ofdm_m = (int) (ofdm_config->fs / ofdm_config->rs);
    ofdm_ncp = (int) (ofdm_config->tcp * ofdm_config->fs);
    ofdm_bitsperframe = ofdm_get_bits_per_frame(ofdm);
    ofdm_rowsperframe = ofdm_bitsperframe / (ofdm_config->nc * ofdm_config->bps);
    ofdm_samplesperframe = ofdm_get_samples_per_frame(ofdm);
    ofdm_max_samplesperframe = ofdm_get_max_samples_per_frame(ofdm);
    ofdm_rxbuf = 3 * ofdm_samplesperframe + 3 * (ofdm_m + ofdm_ncp);
    ofdm_nuwbits = (ofdm_config->ns - 1) * ofdm_config->bps - ofdm_config->txtbits;
    ofdm_ntxtbits = ofdm_config->txtbits;
    ofdm_rx_offset = (ofdm_nuwbits + ofdm_ntxtbits);
    ofdm_data_bitsperframe = (ofdm_bitsperframe - ofdm_rx_offset);

    int tx_bits[ofdm_data_bitsperframe];
    int rx_bits[ofdm_data_bitsperframe];
    COMP tx_rx[ofdm_samplesperframe];

    while ((opt = getopt(argc, argv, "df:p")) != -1) {
        switch (opt) {
        case 'd':
            dummy = 1;
            break;
        case 'f':
            frames = atoi(optarg);
            break;
        case 'p':
            print = 1;
            break;
        default:
            fprintf(stderr, "Usage: %s [-e] [-f <frames>] [-p]\n", argv[0]);
        }
    }

    for (f = 0; f < frames; f++) {
        ////////
        // Prep inputs

        for(i=0; i<ofdm_data_bitsperframe; i++) {
            tx_bits[i] = payload_data_bits[(i % (sizeof(payload_data_bits)/sizeof(payload_data_bits[0])))];
        }

        ////////
        // Modem (or dummy)

        if (dummy) {
            dummy_code(tx_bits, rx_bits);
        } else {
            run_modem(ofdm, tx_bits, rx_bits, tx_rx);
        }

        ////////
        // Compare results (or print)
        int errors = 0;

        if (print) {
            for(i=0; i<ofdm_data_bitsperframe; i++) {
                fprintf(stderr, "bit %3d: tx = %1d, rx = %1d",
                    i, tx_bits[i], rx_bits[i + ofdm_rx_offset]);

                if (tx_bits[i] != rx_bits[i + ofdm_rx_offset]) {
                    fprintf(stderr, " Error");
                    errors ++;
                }

                fprintf(stderr, "\n");
            }
        } else {
            for(i=0; i<ofdm_data_bitsperframe; i++) {
                if (tx_bits[i] != rx_bits[i + ofdm_rx_offset]) {
                    if (errors < MAX_ERRORS) {
                        fprintf(stderr, "Error in bit %3d: tx = %1d, rx = %1d\n",
                            i, tx_bits[i], rx_bits[i + ofdm_rx_offset]);
                    }

                    errors++;
                }
            }
        }

        fprintf(stderr, "%d Errors\n", errors);

    } // for (f<frames

    ofdm_destroy(ofdm);

}   // end main()


//////////////////////////////////
void run_modem(struct OFDM *ofdm, int tx_bits[], int rx_bits[], COMP tx_rx[]) {
    int mod_bits[ofdm_samplesperframe];
    int i, j;

    ///////////
    // Mod
    ///////////

    for(i=0; i<ofdm_nuwbits; i++) {
        mod_bits[i] = ofdm->tx_uw[i];
    }

    for(i=ofdm_nuwbits; i<ofdm_nuwbits+ofdm_ntxtbits; i++) {
        mod_bits[i] = 0;
    }       

    for(j=0, i=ofdm_nuwbits+ofdm_ntxtbits; j<ofdm_data_bitsperframe; i++,j++) {
        mod_bits[i] = tx_bits[j];
    }

    for(j=0; j<ofdm_data_bitsperframe; i++,j++) {
        mod_bits[i] = tx_bits[j];
    }

    ofdm_mod(ofdm, tx_rx, mod_bits);

    ///////////
    // DeMod
    ///////////

    int  Nsam = ofdm_samplesperframe;
    int  prx = 0;
    int  nin =  ofdm_samplesperframe + 2 * (ofdm_m + ofdm_ncp);

    int  lnew;
    COMP rxbuf_in[ofdm_max_samplesperframe];

    for (i=0; i<ofdm_samplesperframe ; i++,prx++) {
        ofdm->rxbuf[ofdm_rxbuf-nin+i] = tx_rx[prx].real + tx_rx[prx].imag * I;
    }

    for (i=ofdm_samplesperframe ; i<nin; i++) {
        ofdm->rxbuf[ofdm_rxbuf-nin+i] = 0.0 + 0.0 * I;
    }
    
    /* disable estimators for initial testing */
    ofdm_set_verbose(ofdm, false);
    ofdm_set_timing_enable(ofdm, true);
    ofdm_set_foff_est_enable(ofdm, true);
    ofdm_set_phase_est_enable(ofdm, true);

    ofdm->mean_amp = 1.0;

    nin = ofdm_get_nin(ofdm);

    /* Insert samples at end of buffer, set to zero if no samples
       available to disable phase estimation on future pilots on
       last frame of simulation. */

    if ((Nsam-prx) < nin) {
        lnew = Nsam-prx;
    } else {
        lnew = nin;
    }
    for(i=0; i<nin; i++) {
        rxbuf_in[i].real = 0.0;
        rxbuf_in[i].imag = 0.0;
    }

    if (lnew) {
        for(i=0; i<lnew; i++, prx++) {
            rxbuf_in[i] = tx_rx[prx];
        }
    }

    ofdm_demod(ofdm, rx_bits, rxbuf_in);
    

}   // end run_modem()


//////////////////////////////////
void dummy_code(int tx_bits[], int rx_bits[]) {
    int i;

    for(i=0; i<ofdm_data_bitsperframe; i++) {
        rx_bits[i] = tx_bits[i];
    }

}   // end dummy_code()

/* vi:set ts=4 sts=4 et: */
