/*---------------------------------------------------------------------------*\

  FILE........: cohpsk.c
  AUTHOR......: David Rowe
  DATE CREATED: March 2015

  Functions that implement a coherent PSK FDM modem.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2015 David Rowe

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

/*---------------------------------------------------------------------------*\

                               INCLUDES

\*---------------------------------------------------------------------------*/

#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

#include "codec2_cohpsk.h"
#include "cohpsk_defs.h"
#include "cohpsk_internal.h"
#include "fdmdv_internal.h"
#include "pilots_coh.h"
#include "comp_prim.h"
#include "kiss_fft.h"
#include "linreg.h"
#include "rn_coh.h"
#include "test_bits_coh.h"

#include "debug_alloc.h"

static COMP qpsk_mod[] = {
    { 1.0, 0.0},
    { 0.0, 1.0},
    { 0.0,-1.0},
    {-1.0, 0.0}
};

static int sampling_points[] = {0, 1, 6, 7};

void corr_with_pilots(float *corr_out, float *mag_out, struct COHPSK *coh, int t, float f_fine);
void update_ct_symb_buf(COMP ct_symb_buf[][COHPSK_NC*COHPSK_ND], COMP ch_symb[][COHPSK_NC*COHPSK_ND]);

/*---------------------------------------------------------------------------*\

                               FUNCTIONS

\*---------------------------------------------------------------------------*/


/*--------------------------------------------------------------------------* \

  FUNCTION....: cohpsk_create
  AUTHOR......: David Rowe
  DATE CREATED: Marcg 2015

  Create and initialise an instance of the modem.  Returns a pointer
  to the modem states or NULL on failure.  One set of states is
  sufficient for a full duplex modem.

\*---------------------------------------------------------------------------*/

struct COHPSK *cohpsk_create(void)
{
    struct COHPSK *coh;
    struct FDMDV  *fdmdv;
    int            r,c,p,i;
    float          freq_hz, result;
    float          tau = 2.0f * M_PI;

    assert(COHPSK_NC == PILOTS_NC);
    assert(COHPSK_NOM_SAMPLES_PER_FRAME == (COHPSK_M*NSYMROWPILOT));
    assert(COHPSK_MAX_SAMPLES_PER_FRAME == (COHPSK_M*NSYMROWPILOT+COHPSK_M/P));
    assert(COHPSK_NSYM == NSYM);  /* as we want to use the tx sym mem on fdmdv */
    assert(COHPSK_NT == NT);

    coh = (struct COHPSK*)MALLOC(sizeof(struct COHPSK));
    if (coh == NULL)
        return NULL;

    /* set up buffer of tx pilot symbols for coh demod on rx */

    for(r=0; r<2*NPILOTSFRAME; ) {
        for(p=0; p<NPILOTSFRAME; r++, p++) {
            for(c=0; c<COHPSK_NC; c++) {
                coh->pilot2[r][c] = pilots_coh[p][c];
            }
        }
    }

    /* Clear symbol buffer memory */

    for (r=0; r<NCT_SYMB_BUF; r++) {
        for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
            coh->ct_symb_buf[r][c].real = 0.0;
            coh->ct_symb_buf[r][c].imag = 0.0;
        }
    }

    coh->ff_phase.real = 1.0; coh->ff_phase.imag = 0.0;
    coh->sync  = 0;
    coh->frame = 0;
    coh->ratio = 0.0;
    coh->nin = COHPSK_M;

    /* clear sync window buffer */

    for (i=0; i<NSW*NSYMROWPILOT*COHPSK_M; i++) {
        coh->ch_fdm_frame_buf[i].real = 0.0;
        coh->ch_fdm_frame_buf[i].imag = 0.0;
    }

    /* set up fdmdv states so we can use those modem functions */

    /*
     * NC*ND -1 Realize that the function creates a sync carrier (+1),
     * or one more carrier than asked for. We ignore any initialization
     * inside of fdmdv and take care of that here, using the whole
     * NC*ND number of carriers to be used in cohpsk.
     */
    fdmdv = fdmdv_create((COHPSK_NC*COHPSK_ND) - 1);

    fdmdv->fsep = COHPSK_RS*(1.0 + COHPSK_EXCESS_BW);

    for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
	fdmdv->phase_tx[c].real = 1.0;
 	fdmdv->phase_tx[c].imag = 0.0;

        /* note non-linear carrier spacing to help PAPR, works v well in conjunction with CLIP */

        freq_hz = fdmdv->fsep*( -(COHPSK_NC*COHPSK_ND)/2 - 0.5f + powf(c + 1.0f, 0.98f) );
        result = tau * freq_hz/COHPSK_FS;

	fdmdv->freq[c].real = cosf(result);
 	fdmdv->freq[c].imag = sinf(result);
 	fdmdv->freq_pol[c]  = result;

        //printf("c: %d %f %f\n",c,freq_hz,fdmdv->freq_pol[c]);
        for(i=0; i<COHPSK_NFILTER; i++) {
            coh->rx_filter_memory[c][i].real = 0.0;
            coh->rx_filter_memory[c][i].imag = 0.0;
        }

        /* optional per-carrier amplitude weighting for testing */

        coh->carrier_ampl[c] = 1.0;
    }
    
    result = tau * FDMDV_FCENTRE/COHPSK_FS;
    fdmdv->fbb_rect.real     = cosf(result);
    fdmdv->fbb_rect.imag     = sinf(result);
    fdmdv->fbb_pol           = result;

    coh->fdmdv = fdmdv;

    coh->sig_rms = coh->noise_rms = 0.0;

    for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
        for (r=0; r<NSYMROW; r++) {
            coh->rx_symb[r][c].real = 0.0;
            coh->rx_symb[r][c].imag = 0.0;
        }
    }

    coh->verbose = 0;

    /* disable optional logging by default */

    coh->rx_baseband_log = NULL;
    coh->rx_baseband_log_col_index = 0;
    coh->rx_filt_log = NULL;
    coh->rx_filt_log_col_index = 0;
    coh->ch_symb_log = NULL;
    coh->ch_symb_log_r = 0;
    coh->rx_timing_log = NULL;
    coh->rx_timing_log_index = 0;

    /* test frames */

    coh->ptest_bits_coh_tx = coh->ptest_bits_coh_rx[0] = coh->ptest_bits_coh_rx[1] = (int*)test_bits_coh;
    coh->ptest_bits_coh_end = (int*)test_bits_coh + sizeof(test_bits_coh)/sizeof(int);

    return coh;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: cohpsk_destroy
  AUTHOR......: David Rowe
  DATE CREATED: March 2015

  Destroy an instance of the modem.

\*---------------------------------------------------------------------------*/

void cohpsk_destroy(struct COHPSK *coh)
{
    fdmdv_destroy(coh->fdmdv);
    assert(coh != NULL);
    FREE(coh);
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: bits_to_qpsk_symbols()
  AUTHOR......: David Rowe
  DATE CREATED: March 2015

  Rate Rs modulator.  Maps bits to parallel DQPSK symbols and inserts pilot symbols.

\*---------------------------------------------------------------------------*/

void bits_to_qpsk_symbols(COMP tx_symb[][COHPSK_NC*COHPSK_ND], int tx_bits[], int nbits)
{
    int   i, r, c, p_r, data_r, d, diversity;
    short bits;

    /* check allowed number of bits supplied matches number of QPSK
       symbols in the frame */

    assert( (NSYMROW*COHPSK_NC*2 == nbits) || (NSYMROW*COHPSK_NC*2*COHPSK_ND == nbits));

    /* if we input twice as many bits we don't do diversity */

    if (NSYMROW*COHPSK_NC*2 == nbits) {
        diversity = 1; /* diversity mode                         */
    }
    else {
        diversity = 2; /* twice as many bits, non diversity mode */
    }

    /*
      Insert two rows of Nc pilots at beginning of data frame.

      Organise QPSK symbols into a NSYMBROWS rows by PILOTS_NC*ND cols matrix,
      each column is a carrier, time flows down the cols......

      Note: the "& 0x1" prevents and non binary tx_bits[] screwing up
      our lives.  Call me defensive.

      sqrtf(ND) term ensures the same energy/symbol for different
      diversity factors.
    */

    r = 0;
    for(p_r=0; p_r<2; p_r++) {
        for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
            tx_symb[r][c].real = pilots_coh[p_r][c % COHPSK_NC]/sqrtf(COHPSK_ND);
            tx_symb[r][c].imag = 0.0;
        }
        r++;
    }
    for(data_r=0; data_r<NSYMROW; data_r++, r++) {
        for(c=0; c<COHPSK_NC*diversity; c++) {
            i = c*NSYMROW + data_r;
            bits = (tx_bits[2*i]&0x1)<<1 | (tx_bits[2*i+1]&0x1);
            tx_symb[r][c] = fcmult(1.0/sqrtf(COHPSK_ND),qpsk_mod[bits]);
        }
    }

    assert(p_r == NPILOTSFRAME);
    assert(r == NSYMROWPILOT);

    /* if in diversity mode, copy symbols to upper carriers */

    for(d=1; d<1+COHPSK_ND-diversity; d++) {
        for(r=0; r<NSYMROWPILOT; r++) {
            for(c=0; c<COHPSK_NC; c++) {
                tx_symb[r][c+COHPSK_NC*d] = tx_symb[r][c];
            }
        }
    }
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: qpsk_symbols_to_bits()
  AUTHOR......: David Rowe
  DATE CREATED: March 2015

  Rate Rs demodulator. Extract pilot symbols and estimate amplitude and phase
  of each carrier.  Correct phase of data symbols and convert to bits.

  Further improvement.  In channels with slowly changing phase we
  could optionally use pilots from several past and future symbols.

\*---------------------------------------------------------------------------*/

void qpsk_symbols_to_bits(struct COHPSK *coh, float rx_bits[], COMP ct_symb_buf[][COHPSK_NC*COHPSK_ND])
{
    int   p, r, c, i, pc, d, n;
    float x[NPILOTSFRAME+2], x1;
    COMP  y[NPILOTSFRAME+2], yfit;
    COMP  rx_symb_linear[NSYMROW*COHPSK_NC*COHPSK_ND];
    COMP  m, b;
    COMP   __attribute__((unused)) corr, rot, pi_on_4, phi_rect, div_symb;
    float mag,  __attribute__((unused)) phi_,  __attribute__((unused)) amp_;
    float sum_x, sum_xx, noise_var;
    float spi_4 = M_PI / 4.0f;
    COMP  s;

    pi_on_4.real = cosf(spi_4); pi_on_4.imag = sinf(spi_4);

    for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {

        /* Set up lin reg model and interpolate phase.  Works better than average for channels with
           quickly changing phase, like HF. */

        for(p=0; p<NPILOTSFRAME+2; p++) {
            x[p] = sampling_points[p];
            pc = c % COHPSK_NC;
            y[p] = fcmult(coh->pilot2[p][pc], ct_symb_buf[sampling_points[p]][c]);
        }

        linreg(&m, &b, x, y, NPILOTSFRAME+2);
        for(r=0; r<NSYMROW; r++) {
            x1 = (float)(r+NPILOTSFRAME);
            yfit = cadd(fcmult(x1,m),b);
            coh->phi_[r][c] = atan2f(yfit.imag, yfit.real);
        }

        /* amplitude estimation */

        mag = 0.0f;
        for(p=0; p<NPILOTSFRAME+2; p++) {
            mag  += cabsolute(ct_symb_buf[sampling_points[p]][c]);
        }
        amp_ =  mag/(NPILOTSFRAME+2);
        for(r=0; r<NSYMROW; r++) {
             coh->amp_[r][c] = amp_;
        }
    }

    /* now correct phase of data symbols */

    for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
        for (r=0; r<NSYMROW; r++) {
            phi_rect.real = cosf(coh->phi_[r][c]); phi_rect.imag = -sinf(coh->phi_[r][c]);
            coh->rx_symb[r][c] = cmult(ct_symb_buf[NPILOTSFRAME + r][c], phi_rect);
            i = c*NSYMROW + r;
            rx_symb_linear[i] = coh->rx_symb[r][c];
        }
    }

    /* and finally optional diversity combination, note output is soft decn a "1" is < 0 */

    for(c=0; c<COHPSK_NC; c++) {
        for(r=0; r<NSYMROW; r++) {
            div_symb = coh->rx_symb[r][c];
            for (d=1; d<COHPSK_ND; d++) {
                div_symb = cadd(div_symb, coh->rx_symb[r][c + COHPSK_NC*d]);
            }
            rot = cmult(div_symb, pi_on_4);
            i = c*NSYMROW + r;
            rx_bits[2*i+1] = rot.real;
            rx_bits[2*i]   = rot.imag;

            /* demodulate bits from upper and lower carriers separately for test purposes */

            assert(COHPSK_ND == 2);

            i = c*NSYMROW + r;
            rot = cmult(coh->rx_symb[r][c], pi_on_4);
            coh->rx_bits_lower[2*i+1] = rot.real;
            coh->rx_bits_lower[2*i]   = rot.imag;
            rot = cmult(coh->rx_symb[r][c + COHPSK_NC], pi_on_4);
            coh->rx_bits_upper[2*i+1] = rot.real;
            coh->rx_bits_upper[2*i]   = rot.imag;
        }
    }


    /* estimate RMS signal and noise */

    mag = 0.0f;
    for(i=0; i<NSYMROW*COHPSK_NC*COHPSK_ND; i++)
        mag += cabsolute(rx_symb_linear[i]);
    coh->sig_rms = mag/(NSYMROW*COHPSK_NC*COHPSK_ND);

    sum_x = 0.0f;
    sum_xx = 0.0f;
    n = 0;
    for (i=0; i<NSYMROW*COHPSK_NC*COHPSK_ND; i++) {
      s = rx_symb_linear[i];
      if (fabsf(s.real) > coh->sig_rms) {
        sum_x  += s.imag;
        sum_xx += s.imag*s.imag;
        n++;
      }
    }

    noise_var = 0.0f;
    if (n > 1) {
      noise_var = (n*sum_xx - sum_x*sum_x)/(n*(n-1));
    }
    coh->noise_rms = sqrtf(noise_var);

}


/*---------------------------------------------------------------------------*\

  FUNCTION....: tx_filter_and_upconvert_coh()
  AUTHOR......: David Rowe
  DATE CREATED: May 2015

  Given NC symbols construct M samples (1 symbol) of NC filtered
  and upconverted symbols.

  TODO: work out a way to merge with fdmdv version, e.g. run time define M/NSYM,
  and run unittests on fdmdv and cohpsk modem afterwards.

\*---------------------------------------------------------------------------*/

void tx_filter_and_upconvert_coh(COMP tx_fdm[], int Nc, const COMP tx_symbols[],
                                 COMP tx_filter_memory[][COHPSK_NSYM],
                                 COMP phase_tx[], COMP freq[],
                                 COMP *fbb_phase, COMP fbb_rect)
{
    int     c;
    int     i,j,k;
    float   acc;
    COMP    gain;
    COMP    tx_baseband;
    COMP  two = {2.0, 0.0};
    float mag;

    gain.real = sqrtf(2.0)/2.0;
    gain.imag = 0.0;

    for(i=0; i<COHPSK_M; i++) {
	tx_fdm[i].real = 0.0;
	tx_fdm[i].imag = 0.0;
    }

    for(c=0; c<Nc; c++)
	tx_filter_memory[c][COHPSK_NSYM-1] = cmult(tx_symbols[c], gain);

    /*
       tx filter each symbol, generate M filtered output samples for
       each symbol, which we then freq shift and sum with other
       carriers.  Efficient polyphase filter techniques used as
       tx_filter_memory is sparse
    */

    for(c=0; c<Nc; c++) {
        for(i=0; i<COHPSK_M; i++) {

	    /* filter real sample of symbol for carrier c */

	    acc = 0.0;
	    for(j=0,k=COHPSK_M-i-1; j<COHPSK_NSYM; j++,k+=COHPSK_M)
		acc += COHPSK_M * tx_filter_memory[c][j].real * gt_alpha5_root_coh[k];
	    tx_baseband.real = acc;

	    /* filter imag sample of symbol for carrier c */

	    acc = 0.0;
	    for(j=0,k=COHPSK_M-i-1; j<COHPSK_NSYM; j++,k+=COHPSK_M)
		acc += COHPSK_M * tx_filter_memory[c][j].imag * gt_alpha5_root_coh[k];
	    tx_baseband.imag = acc;
            //printf("%d %d %f %f\n", c, i, tx_baseband.real, tx_baseband.imag);

            /* freq shift and sum */

	    phase_tx[c] = cmult(phase_tx[c], freq[c]);
	    tx_fdm[i] = cadd(tx_fdm[i], cmult(tx_baseband, phase_tx[c]));
            //printf("%d %d %f %f\n", c, i, phase_tx[c].real, phase_tx[c].imag);
	}
        //exit(0);
    }

    /* shift whole thing up to carrier freq */

    for (i=0; i<COHPSK_M; i++) {
	*fbb_phase = cmult(*fbb_phase, fbb_rect);
	tx_fdm[i] = cmult(tx_fdm[i], *fbb_phase);
    }

    /*
      Scale such that total Carrier power C of real(tx_fdm) = Nc.  This
      excludes the power of the pilot tone.
      We return the complex (single sided) signal to make frequency
      shifting for the purpose of testing easier
    */

    for (i=0; i<COHPSK_M; i++)
	tx_fdm[i] = cmult(two, tx_fdm[i]);

    /* normalise digital oscillators as the magnitude can drift over time */

    for (c=0; c<Nc; c++) {
        mag = cabsolute(phase_tx[c]);
	phase_tx[c].real /= mag;
	phase_tx[c].imag /= mag;
    }

    mag = cabsolute(*fbb_phase);
    fbb_phase->real /= mag;
    fbb_phase->imag /= mag;

    /* shift memory, inserting zeros at end */

    for(i=0; i<COHPSK_NSYM-1; i++)
	for(c=0; c<Nc; c++)
	    tx_filter_memory[c][i] = tx_filter_memory[c][i+1];

    for(c=0; c<Nc; c++) {
	tx_filter_memory[c][COHPSK_NSYM-1].real = 0.0;
	tx_filter_memory[c][COHPSK_NSYM-1].imag = 0.0;
    }
}



void corr_with_pilots(float *corr_out, float *mag_out, struct COHPSK *coh, int t, float f_fine)
{
    COMP  acorr, f_fine_rect[NPILOTSFRAME+2], f_corr;
    float mag, corr, result;
    float tau = 2.0f * M_PI;
    int   c, p, pc;

    for (p=0; p<NPILOTSFRAME+2; p++) {
        result = f_fine * tau * (sampling_points[p]+1.0) / COHPSK_RS;
        f_fine_rect[p].real = cosf(result);
        f_fine_rect[p].imag = sinf(result);
    }

    corr = 0.0; mag = 1E-12;
    for (c=0; c<COHPSK_NC*COHPSK_ND; c++) {
        acorr.real = 0.0f; acorr.imag = 0.0f; pc = c % COHPSK_NC;
        for (p=0; p<NPILOTSFRAME+2; p++) {
            f_corr = cmult(f_fine_rect[p], coh->ct_symb_buf[t+sampling_points[p]][c]);
            acorr = cadd(acorr, fcmult(coh->pilot2[p][pc], f_corr));
            mag  += cabsolute(f_corr);
        }
        corr += cabsolute(acorr);
    }

    *corr_out = corr;
    *mag_out  = mag;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: frame_sync_fine_freq_est()
  AUTHOR......: David Rowe
  DATE CREATED: April 2015

  Returns an estimate of frame sync (coarse timing) offset and fine
  frequency offset, advances to next sync state if we have a reliable
  match for frame sync.

\*---------------------------------------------------------------------------*/

void frame_sync_fine_freq_est(struct COHPSK *coh, COMP ch_symb[][COHPSK_NC*COHPSK_ND], int sync, int *next_sync)
{
    int   t;
    float f_fine, mag, max_corr, max_mag, corr, result;
    float tau = 2.0f * M_PI;

    update_ct_symb_buf(coh->ct_symb_buf, ch_symb);

    /* sample pilots at start of this frame and start of next frame */

    if (sync == 0) {

        /* sample correlation over 2D grid of time and fine freq points */

        max_corr = 0.0; max_mag = 1E-12;
        for (f_fine=-20; f_fine<=20; f_fine+=0.25) {
            for (t=0; t<NSYMROWPILOT; t++) {
                corr_with_pilots(&corr, &mag, coh, t, f_fine);
                //printf("  f: %f  t: %d corr: %f mag: %f\n", f_fine, t, corr, mag);
                if (corr >= max_corr) {
                    max_corr = corr;
                    max_mag = mag;
                    coh->ct = t;
                    coh->f_fine_est = f_fine;
                }
            }
        }


        result = coh->f_fine_est * tau / COHPSK_RS;

        coh->ff_rect.real = cosf(result);
        coh->ff_rect.imag = -sinf(result);
        if (coh->verbose)
            fprintf(stderr, "  [%d]   fine freq f: %6.2f max_ratio: %f ct: %d\n", coh->frame, (double)coh->f_fine_est, (double)max_corr/(double)max_mag, coh->ct);

        if (max_corr/max_mag > 0.9) {
            if (coh->verbose)
                fprintf(stderr, "  [%d]   encouraging sync word!\n", coh->frame);
            coh->sync_timer = 0;
            *next_sync = 1;
        }
        else {
            *next_sync = 0;
        }
        coh->ratio = max_corr/max_mag;
    }
}


void update_ct_symb_buf(COMP ct_symb_buf[][COHPSK_NC*COHPSK_ND], COMP ch_symb[][COHPSK_NC*COHPSK_ND])
{
    int r, c, i;

    /* update memory in symbol buffer */

    for(r=0; r<NCT_SYMB_BUF-NSYMROWPILOT; r++) {
        for(c=0; c<COHPSK_NC*COHPSK_ND; c++)
            ct_symb_buf[r][c] = ct_symb_buf[r+NSYMROWPILOT][c];
    }

    for(r=NCT_SYMB_BUF-NSYMROWPILOT, i=0; r<NCT_SYMB_BUF; r++, i++) {
        for(c=0; c<COHPSK_NC*COHPSK_ND; c++)
            ct_symb_buf[r][c] = ch_symb[i][c];
    }
}


int sync_state_machine(struct COHPSK *coh, int sync, int next_sync)
{
    float corr, mag;

    if (sync == 1) {

        /* check that sync is still good, fall out of sync on consecutive bad frames */

        corr_with_pilots(&corr, &mag, coh, coh->ct, coh->f_fine_est);
        coh->ratio = fabsf(corr)/mag;

        // printf("%f\n", cabsolute(corr)/mag);

        if (fabsf(corr)/mag < 0.8)
            coh->sync_timer++;
        else
            coh->sync_timer = 0;

        if (coh->sync_timer == 10) {
            if (coh->verbose)
                fprintf(stderr,"  [%d] lost sync ....\n", coh->frame);
            next_sync = 0;
        }
    }

    sync = next_sync;

    return sync;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: cohpsk_mod()
  AUTHOR......: David Rowe
  DATE CREATED: 5/4/2015

  COHPSK modulator, take a frame of COHPSK_BITS_PER_FRAME or
  2*COHPSK_BITS_PER_FRAME bits and generates a frame of
  COHPSK_NOM_SAMPLES_PER_FRAME modulated symbols.

  if nbits == COHPSK_BITS_PER_FRAME, diveristy mode is used, if nbits
  == 2*COHPSK_BITS_PER_FRAME diversity mode is not used.

  The output signal is complex to support single sided frequency
  shifting, for example when testing frequency offsets in channel
  simulation.

\*---------------------------------------------------------------------------*/

void cohpsk_mod(struct COHPSK *coh, COMP tx_fdm[], int tx_bits[], int nbits)
{
    struct FDMDV *fdmdv = coh->fdmdv;
    COMP  tx_symb[NSYMROWPILOT][COHPSK_NC*COHPSK_ND];
    COMP  tx_onesym[COHPSK_NC*COHPSK_ND];
    int  r,c;

    bits_to_qpsk_symbols(tx_symb, tx_bits, nbits);

    for(r=0; r<NSYMROWPILOT; r++) {
        for(c=0; c<COHPSK_NC*COHPSK_ND; c++)
            tx_onesym[c] = fcmult(coh->carrier_ampl[c], tx_symb[r][c]);
        tx_filter_and_upconvert_coh(&tx_fdm[r*COHPSK_M], COHPSK_NC*COHPSK_ND , tx_onesym, fdmdv->tx_filter_memory,
                                    fdmdv->phase_tx, fdmdv->freq, &fdmdv->fbb_phase_tx, fdmdv->fbb_rect);
    }
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: cohpsk_clip()
  AUTHOR......: David Rowe
  DATE CREATED: May 2015

  Hard clips a complex signal magnitude (Hilbert Clipping) to improve PAPR.

\*---------------------------------------------------------------------------*/

void cohpsk_clip(COMP tx_fdm[], float clip_thresh, int n)
{
    COMP  sam;
    float mag;
    int   i;

    for(i=0; i<n; i++) {
        sam = tx_fdm[i];
        mag = cabsolute(sam);
        if (mag > clip_thresh)  {
            sam = fcmult(clip_thresh/mag, sam);
        }
        tx_fdm[i] = sam;
    }
 }

/*---------------------------------------------------------------------------*\

  FUNCTION....: fdm_downconvert_coh
  AUTHOR......: David Rowe
  DATE CREATED: May 2015

  Frequency shift each modem carrier down to NC baseband signals.

  TODO: try to combine with fdmdv version, carefully re-test fdmdv modem.

\*---------------------------------------------------------------------------*/

void fdm_downconvert_coh(COMP rx_baseband[][COHPSK_M+COHPSK_M/P], int Nc, COMP rx_fdm[], COMP phase_rx[], COMP freq[], int nin)
{
    int   i,c;
    float mag;

    /* maximum number of input samples to demod */

    assert(nin <= (COHPSK_M+COHPSK_M/P));

    /* downconvert */

    for (c=0; c<Nc; c++)
	for (i=0; i<nin; i++) {
	    phase_rx[c] = cmult(phase_rx[c], freq[c]);
	    rx_baseband[c][i] = cmult(rx_fdm[i], cconj(phase_rx[c]));
	}

    /* normalise digital oscilators as the magnitude can drift over time */

    for (c=0; c<Nc; c++) {
        mag = cabsolute(phase_rx[c]);
	phase_rx[c].real /= mag;
	phase_rx[c].imag /= mag;
    }
}

/* Determine if we can use vector ops below. */
#if __GNUC__ > 4 || \
    (__GNUC__ == 4 && (__GNUC_MINOR__ > 6 || \
                       (__GNUC_MINOR__ == 6 && \
                        __GNUC_PATCHLEVEL__ > 0)))
#define USE_VECTOR_OPS 1
#elif __clang_major__ > 3 || \
    (__clang_minor__ == 3 && (__clang_minor__ > 7 || \
                       (__clang_minor__ == 7 && \
                        __clang_patchlevel__ > 0)))
#define USE_VECTOR_OPS 1
#endif

#if USE_VECTOR_OPS

#ifdef __ARM_NEON
#include "arm_neon.h"

typedef float32x4_t float4;
#else
/* Vector of 4 floating point numbers for use by the below function */
typedef float float4 __attribute__ ((vector_size (16)));
#endif // __ARM_NEON

#endif /* USE_VECTOR_OPS */
    
/*---------------------------------------------------------------------------*\

  FUNCTION....: rx_filter_coh()
  AUTHOR......: David Rowe
  DATE CREATED: May 2015

  cohpsk version of fdmdv.c rx_filter function.

  TODO: see if we can merge the two!  Will require re-testing of fdmdv modem.

\*---------------------------------------------------------------------------*/

inline extern void rx_filter_coh(COMP rx_filt[COHPSK_NC*COHPSK_ND][P+1], int Nc, COMP rx_baseband[COHPSK_NC*COHPSK_ND][COHPSK_M+COHPSK_M/P], COMP rx_filter_memory[COHPSK_NC*COHPSK_ND][COHPSK_NFILTER], int nin)
{
    int c,i,j,k,l;
    int n=COHPSK_M/P;
    
    /* rx filter each symbol, generate P filtered output samples for
       each symbol.  Note we keep filter memory at rate M, it's just
       the filter output at rate P */

    for(i=0, j=0; i<nin; i+=n,j++) 
    {

    	/* latest input sample */

    	for(c=0; c<Nc; c++)
        {
            rx_filt[c][j].real = 0.0; 
            rx_filt[c][j].imag = 0.0;
        
            /*
                This call is equivalent to the code below:
            
        	    for(k=COHPSK_NFILTER-n,l=i; k<COHPSK_NFILTER; k++,l++)
                {
                    rx_filter_memory[c][k] = rx_baseband[c][l];
                }
            */
            memcpy(
                &rx_filter_memory[c][COHPSK_NFILTER-n], 
                &rx_baseband[c][i], 
                sizeof(COMP)*n);
        
            /* convolution (filtering) */
       
#if USE_VECTOR_OPS
            /* assumes COHPSK_NFILTER is divisible by 2 */

#ifdef __ARM_NEON
            float4 resultVec = vdupq_n_f32(0);
#else
            float4 resultVec = {0, 0, 0, 0};
#endif // __ARM_NEON

            for(k=0, l=0; k<COHPSK_NFILTER; k += 2, l += 4)
            {
#ifdef __ARM_NEON
                // Fetch gt_alpha5_root_coh and place it into a vector for later use.
                // First half at index k, second half at index k + 1.
                float4 alpha5Vec = vld1q_f32((const float32_t*)&gt_alpha5_root_coh_neon[l]);

                // Load two COMP elements (each containing two floats) into 4 element vector.
                float4 filterMemVec = vld1q_f32((const float32_t *)&rx_filter_memory[c][k]);

                // Multiply each element of filterMemVec by alpha5Vec from above and add to the
                // running total in resultVec. Odd indices are reals, even imag.
                resultVec = vmlaq_f32(resultVec, alpha5Vec, filterMemVec);
#else
                // Fetch gt_alpha5_root_coh and place it into a vector for later use.
                // First half at index k, second half at index k + 1.
                float4 alpha5Vec = {
                    gt_alpha5_root_coh_neon[l], gt_alpha5_root_coh_neon[l + 1], gt_alpha5_root_coh_neon[l + 2], gt_alpha5_root_coh_neon[l + 3],
                };

                // Load two COMP elements (each containing two floats) into 4 element vector.
                float4 filterMemVec = {
                    rx_filter_memory[c][k].real, rx_filter_memory[c][k].imag, rx_filter_memory[c][k + 1].real, rx_filter_memory[c][k + 1].imag, 
                };

                // Multiply each element of filterMemVec by alpha5Vec from above and add to the
                // running total in resultVec. Odd indices are reals, even imag.
                resultVec += alpha5Vec * filterMemVec;
            
#endif // __ARM_NEON
            }

            // Add total from resultVec to rx_filt.
            rx_filt[c][j].real += resultVec[0] + resultVec[2];
            rx_filt[c][j].imag += resultVec[1] + resultVec[3];
#else
    	    for(k=0; k<COHPSK_NFILTER; k++)
            {
                /*
                    Equivalent to this code:

                    rx_filt[c][j] = cadd(rx_filt[c][j], fcmult(gt_alpha5_root_coh[k], rx_filter_memory[c][k]));
                */
                rx_filt[c][j].real += gt_alpha5_root_coh[k] * rx_filter_memory[c][k].real;
                rx_filt[c][j].imag += gt_alpha5_root_coh[k] * rx_filter_memory[c][k].imag;
    	    }
#endif /* USE_VECTOR_OPS */
            
    	    /* 
                make room for next input sample.
            
                The below call is equivalent to this code:
            
                for(k=0,l=n; k<COHPSK_NFILTER-n; k++,l++)
                {
                    rx_filter_memory[c][k] = rx_filter_memory[c][l];
                }
            */
            memmove(
                &rx_filter_memory[c][0], 
                &rx_filter_memory[c][n], 
                sizeof(COMP)*(COHPSK_NFILTER-n));
        }
    }
    
    assert(j <= (P+1)); /* check for any over runs */
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: fdmdv_freq_shift_coh()
  AUTHOR......: David Rowe
  DATE CREATED: May 2015

  Frequency shift modem signal.  The use of complex input and output allows
  single sided frequency shifting (no images).

\*---------------------------------------------------------------------------*/

void fdmdv_freq_shift_coh(COMP rx_fdm_fcorr[], COMP rx_fdm[], float foff, float Fs,
                          COMP *foff_phase_rect, int nin)
{
    COMP  foff_rect;
    float mag;
    float tau = 2.0f * M_PI;
    float result = tau * foff/Fs;
    int   i;

    foff_rect.real = cosf(result);
    foff_rect.imag = sinf(result);
    for(i=0; i<nin; i++) {
	*foff_phase_rect = cmult(*foff_phase_rect, foff_rect);
	rx_fdm_fcorr[i] = cmult(rx_fdm[i], *foff_phase_rect);
    }

    /* normalise digital oscilator as the magnitude can drfift over time */

    mag = cabsolute(*foff_phase_rect);
    foff_phase_rect->real /= mag;
    foff_phase_rect->imag /= mag;
}


void rate_Fs_rx_processing(struct COHPSK *coh, COMP ch_symb[][COHPSK_NC*COHPSK_ND], COMP ch_fdm_frame[], float *f_est, int nsymb, int nin, int freq_track)
{
    struct FDMDV *fdmdv = coh->fdmdv;
    int   r, c, i, ch_fdm_frame_index;
    COMP  rx_fdm_frame_bb[COHPSK_M+COHPSK_M/P];
    COMP  rx_baseband[COHPSK_NC*COHPSK_ND][COHPSK_M+COHPSK_M/P];
    COMP  rx_filt[COHPSK_NC*COHPSK_ND][P+1];
    float env[NT*P], rx_timing;
    COMP  rx_onesym[COHPSK_NC*COHPSK_ND];
    float beta, g;
    COMP  adiff, amod_strip, mod_strip;

    ch_fdm_frame_index = 0;
    rx_timing = 0;

    for (r=0; r<nsymb; r++) {
        fdmdv_freq_shift_coh(rx_fdm_frame_bb, &ch_fdm_frame[ch_fdm_frame_index], -(*f_est), COHPSK_FS, &fdmdv->fbb_phase_rx, nin);
        ch_fdm_frame_index += nin;
        fdm_downconvert_coh(rx_baseband, COHPSK_NC*COHPSK_ND, rx_fdm_frame_bb, fdmdv->phase_rx, fdmdv->freq, nin);
        rx_filter_coh(rx_filt, COHPSK_NC*COHPSK_ND, rx_baseband, coh->rx_filter_memory, nin);
        rx_timing = rx_est_timing(rx_onesym, fdmdv->Nc, rx_filt, fdmdv->rx_filter_mem_timing, env, nin, COHPSK_M);

        for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
            ch_symb[r][c] = rx_onesym[c];
        }

        /* freq tracking, see test_ftrack.m for unit test.  Placed in
           this function as it needs to work on a symbol by symbol
           abasis rather than frame by frame.  This means the control
           loop operates at a sample rate of Rs = 50Hz for say 1 Hz/s
           drift. */

        if (freq_track) {
            beta = 0.005;
            g = 0.2;

            /* combine difference on phase from last symbol over Nc carriers */

            mod_strip.real = 0.0; mod_strip.imag = 0.0;
            for(c=0; c<fdmdv->Nc+1; c++) {
                //printf("rx_onesym[%d] %f %f prev_rx_symbols[%d] %f %f\n", c, rx_onesym[c].real, rx_onesym[c].imag,
                //       fdmdv->prev_rx_symbols[c].real, fdmdv->prev_rx_symbols[c].imag);
                adiff = cmult(rx_onesym[c], cconj(fdmdv->prev_rx_symbols[c]));
                fdmdv->prev_rx_symbols[c] = rx_onesym[c];

                /* 4th power strips QPSK modulation, by multiplying phase by 4
                   Using the abs value of the real coord was found to help
                   non-linear issues when noise power was large. */

                amod_strip = cmult(adiff, adiff);
                amod_strip = cmult(amod_strip, amod_strip);
                amod_strip.real = fabsf(amod_strip.real);
                mod_strip = cadd(mod_strip, amod_strip);
            }
            //printf("modstrip: %f %f\n", mod_strip.real, mod_strip.imag);

            /* loop filter made up of 1st order IIR plus integrator.  Integerator
               was found to be reqd  */

            fdmdv->foff_filt = (1.0f-beta)*fdmdv->foff_filt + beta*atan2f(mod_strip.imag, mod_strip.real);
            //printf("foff_filt: %f angle: %f\n", fdmdv->foff_filt, atan2f(mod_strip.imag, mod_strip.real));
            *f_est += g*fdmdv->foff_filt;
        }

        /* Optional logging used for testing against Octave version */

        if (coh->rx_baseband_log) {
            assert(nin <= (COHPSK_M+COHPSK_M/P));
            for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
                for(i=0; i<nin; i++) {
                    coh->rx_baseband_log[c*coh->rx_baseband_log_col_sz + coh->rx_baseband_log_col_index + i] = rx_baseband[c][i];
                }
            }
            coh->rx_baseband_log_col_index += nin;
            assert(coh->rx_baseband_log_col_index <= coh->rx_baseband_log_col_sz);
        }

        if (coh->rx_filt_log) {
 	  for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
            for(i=0; i<nin/(COHPSK_M/P); i++) {
              coh->rx_filt_log[c*coh->rx_filt_log_col_sz + coh->rx_filt_log_col_index + i] = rx_filt[c][i];
            }
	  }
	  coh->rx_filt_log_col_index += nin/(COHPSK_M/P);
        }

        if (coh->ch_symb_log) {
            for(c=0; c<COHPSK_NC*COHPSK_ND; c++) {
		coh->ch_symb_log[coh->ch_symb_log_r*COHPSK_NC*COHPSK_ND + c] = ch_symb[r][c];
            }
            coh->ch_symb_log_r++;
        }

        if (coh->rx_timing_log) {
            coh->rx_timing_log[coh->rx_timing_log_index] = rx_timing;
            coh->rx_timing_log_index++;
            //printf("rx_timing_log_index: %d\n", coh->rx_timing_log_index);
        }

        /* we only allow a timing shift on one symbol per frame */

        if (nin != COHPSK_M)
            nin = COHPSK_M;
    }

    coh->rx_timing = rx_timing;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: cohpsk_demod()
  AUTHOR......: David Rowe
  DATE CREATED: 5/4/2015

  COHPSK demodulator, takes an array of (nominally) nin_frame =
  COHPSK_NOM_SAMPLES_PER_FRAME modulated samples, returns an array of
  COHPSK_BITS_PER_FRAME bits.

  The input signal is complex to support single sided frequency shifting
  before the demod input (e.g. click to tune feature).

\*---------------------------------------------------------------------------*/

void cohpsk_demod(struct COHPSK *coh, float rx_bits[], int *sync_good, COMP rx_fdm[], int *nin_frame)
{
    COMP  ch_symb[NSW*NSYMROWPILOT][COHPSK_NC*COHPSK_ND];
    int   i, j, sync, anext_sync, next_sync, nin, r, c;
    float max_ratio, f_est;

    assert(*nin_frame <= COHPSK_MAX_SAMPLES_PER_FRAME);

    next_sync = sync = coh->sync;

    for (i=0; i<NSW*NSYMROWPILOT*COHPSK_M-*nin_frame; i++)
        coh->ch_fdm_frame_buf[i] = coh->ch_fdm_frame_buf[i+*nin_frame];
    //printf("nin_frame: %d i: %d i+nin_frame: %d\n", *nin_frame, i, i+*nin_frame);
    for (j=0; i<NSW*NSYMROWPILOT*COHPSK_M; i++,j++)
        coh->ch_fdm_frame_buf[i] = rx_fdm[j];
    //printf("i: %d j: %d rx_fdm[0]: %f %f\n", i,j, rx_fdm[0].real, rx_fdm[0].imag);

    /* if out of sync do Initial Freq offset estimation using NSW frames to flush out filter memories */

    if (sync == 0) {

        /* we can test +/- 20Hz, so we break this up into 3 tests to cover +/- 60Hz */

        max_ratio = 0.0;
        f_est = 0.0;
        for (coh->f_est = FDMDV_FCENTRE-40.0; coh->f_est <= FDMDV_FCENTRE+40.0; coh->f_est += 40.0) {

            if (coh->verbose)
                fprintf(stderr, "  [%d] acohpsk.f_est: %f +/- 20\n", coh->frame, (double)coh->f_est);

            /* we are out of sync so reset f_est and process two frames to clean out memories */

            rate_Fs_rx_processing(coh, ch_symb, coh->ch_fdm_frame_buf, &coh->f_est, NSW*NSYMROWPILOT, COHPSK_M, 0);
            for (i=0; i<NSW-1; i++) {
                update_ct_symb_buf(coh->ct_symb_buf, &ch_symb[i*NSYMROWPILOT]);
            }
            frame_sync_fine_freq_est(coh, &ch_symb[(NSW-1)*NSYMROWPILOT], sync, &anext_sync);

            if (anext_sync == 1) {
                //printf("  [%d] acohpsk.ratio: %f\n", f, coh->ratio);
                if (coh->ratio > max_ratio) {
                    max_ratio   = coh->ratio;
                    f_est       = coh->f_est - coh->f_fine_est;
                    next_sync   = anext_sync;
                }
            }
        }

        if (next_sync == 1) {

            /* we've found a sync candidate!
               re-process last NSW frames with adjusted f_est then check again */

            coh->f_est = f_est;

            if (coh->verbose)
                fprintf(stderr, "  [%d] trying sync and f_est: %f\n", coh->frame, (double)coh->f_est);

            rate_Fs_rx_processing(coh, ch_symb, coh->ch_fdm_frame_buf, &coh->f_est, NSW*NSYMROWPILOT, COHPSK_M, 0);
            for (i=0; i<NSW-1; i++) {
                update_ct_symb_buf(coh->ct_symb_buf, &ch_symb[i*NSYMROWPILOT]);
            }
            /*
              for(i=0; i<NSW*NSYMROWPILOT; i++) {
                printf("%f %f\n", ch_symb[i][0].real, ch_symb[i][0].imag);
            }
            */
            /*
            for(i=0; i<NCT_SYMB_BUF; i++) {
                printf("%f %f\n", coh->ct_symb_buf[i][0].real, coh->ct_symb_buf[i][0].imag);
            }
            */
             frame_sync_fine_freq_est(coh, &ch_symb[(NSW-1)*NSYMROWPILOT], sync, &next_sync);

            if (fabsf(coh->f_fine_est) > 2.0) {
                if (coh->verbose)
                    fprintf(stderr, "  [%d] Hmm %f is a bit big :(\n", coh->frame, (double)coh->f_fine_est);
                next_sync = 0;
            }
        }

        if (next_sync == 1) {
            /* OK we are in sync!
               demodulate first frame (demod completed below) */

            if (coh->verbose)
                fprintf(stderr, "  [%d] in sync! f_est: %f ratio: %f \n", coh->frame, (double)coh->f_est, (double)coh->ratio);
            for(r=0; r<NSYMROWPILOT+2; r++)
                for(c=0; c<COHPSK_NC*COHPSK_ND; c++)
                    coh->ct_symb_ff_buf[r][c] = coh->ct_symb_buf[coh->ct+r][c];
        }
    }

    /* If in sync just do sample rate processing on latest frame */

    if (sync == 1) {
        rate_Fs_rx_processing(coh, ch_symb, rx_fdm, &coh->f_est, NSYMROWPILOT, coh->nin, 1);
        frame_sync_fine_freq_est(coh, ch_symb, sync, &next_sync);

        for(r=0; r<2; r++)
            for(c=0; c<COHPSK_NC*COHPSK_ND; c++)
                coh->ct_symb_ff_buf[r][c] = coh->ct_symb_ff_buf[r+NSYMROWPILOT][c];
        for(; r<NSYMROWPILOT+2; r++)
            for(c=0; c<COHPSK_NC*COHPSK_ND; c++)
                coh->ct_symb_ff_buf[r][c] = coh->ct_symb_buf[coh->ct+r][c];
    }

    /* if we are in sync complete demodulation with symbol rate processing */

    *sync_good = 0;
    if ((next_sync == 1) || (sync == 1)) {
        qpsk_symbols_to_bits(coh, rx_bits, coh->ct_symb_ff_buf);
        *sync_good = 1;
    }

    sync = sync_state_machine(coh, sync, next_sync);
    coh->sync = sync;

    /* work out how many samples we need for the next call to account
       for differences in tx and rx sample clocks */

    nin = COHPSK_M;
    if (sync == 1) {
        if (coh->rx_timing > COHPSK_M/P)
            nin = COHPSK_M + COHPSK_M/P;
        if (coh->rx_timing < -COHPSK_M/P)
            nin = COHPSK_M - COHPSK_M/P;
    }
    coh->nin = nin;
    *nin_frame = (NSYMROWPILOT-1)*COHPSK_M + nin;
    //if (coh->verbose)
    //    fprintf(stderr, "%f %d %d\n", coh->rx_timing, nin, *nin_frame);
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: cohpsk_fs_offset()
  AUTHOR......: David Rowe
  DATE CREATED: May 2015

  Simulates small Fs offset between mod and demod.

\*---------------------------------------------------------------------------*/

int cohpsk_fs_offset(COMP out[], COMP in[], int n, float sample_rate_ppm)
{
    double f;
    double tin = 0.0;
    double step = 1.0 + sample_rate_ppm/1E6;
    int t1, t2;
    int tout = 0;

    while (tin < (double) n) {
      t1 = (int) floor(tin);
      t2 = (int) ceil(tin);
      f = tin - (double) t1;

      out[tout].real = ((double)1.0-f)*(double)in[t1].real + f*(double)in[t2].real;
      out[tout].imag = ((double)1.0-f)*(double)in[t1].imag + f*(double)in[t2].imag;

      tin += step;
      tout++;
      //printf("tin: %f tout: %d f: %f\n", tin, tout, f);
    }

    return tout;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: cohpsk_get_demod_stats()
  AUTHOR......: David Rowe
  DATE CREATED: 14 June 2015

  Fills stats structure with a bunch of demod information.

\*---------------------------------------------------------------------------*/

void cohpsk_get_demod_stats(struct COHPSK *coh, struct MODEM_STATS *stats)
{
    float new_snr_est;
    
#ifndef __EMBEDDED__
    float spi_4 = M_PI/4.0f;
    COMP  pi_4;
    pi_4.real = cosf(spi_4);
    pi_4.imag = sinf(spi_4);
#endif

    stats->Nc = COHPSK_NC*COHPSK_ND;
    assert(stats->Nc <= MODEM_STATS_NC_MAX);
    new_snr_est = 20.0f * log10f((coh->sig_rms+1E-6f)/(coh->noise_rms+1E-6f)) - 10.0f*log10f(3000.0f/700.0f);
    stats->snr_est = 0.9f*stats->snr_est + 0.1f*new_snr_est;

    //fprintf(stderr, "sig_rms: %f noise_rms: %f snr_est: %f\n", coh->sig_rms, coh->noise_rms, stats->snr_est);
    stats->sync = coh->sync;
    stats->foff = coh->f_est - FDMDV_FCENTRE;
    stats->rx_timing = coh->rx_timing;
    stats->clock_offset = 0.0f; /* TODO - implement clock offset estimation */

#ifndef __EMBEDDED__
    assert(NSYMROW <= MODEM_STATS_NR_MAX);
    stats->nr = NSYMROW;
    for(int c=0; c<COHPSK_NC*COHPSK_ND; c++) {
        for (int r=0; r<NSYMROW; r++) {
            stats->rx_symbols[r][c] = cmult(coh->rx_symb[r][c], pi_4);
        }
    }
#endif
}


void cohpsk_set_verbose(struct COHPSK *coh, int verbose)
{
    assert(coh != NULL);
    coh->verbose = verbose;
}


void cohpsk_set_frame(struct COHPSK *coh, int frame)
{
    assert(coh != NULL);
    coh->frame = frame;
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: cohpsk_get_test_bits()
  AUTHOR......: David Rowe
  DATE CREATED: June 2015

  Returns a frame of known test bits.

\*---------------------------------------------------------------------------*/

void cohpsk_get_test_bits(struct COHPSK *coh, int rx_bits[])
{
    memcpy(rx_bits, coh->ptest_bits_coh_tx, sizeof(int)*COHPSK_BITS_PER_FRAME);
    coh->ptest_bits_coh_tx += COHPSK_BITS_PER_FRAME;
    if (coh->ptest_bits_coh_tx >=coh->ptest_bits_coh_end) {
        coh->ptest_bits_coh_tx = (int*)test_bits_coh;
    }
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: cohpsk_put_test_bits()
  AUTHOR......: David Rowe
  DATE CREATED: June 2015

  Accepts bits from demod and attempts to sync with the known
  test_bits sequence.  When synced measures bit errors.

  Has states to track two separate received test sequences based on
  channel 0 or 1.

\*---------------------------------------------------------------------------*/

void cohpsk_put_test_bits(struct COHPSK *coh, int *state, short error_pattern[],
                          int *bit_errors, char rx_bits_char[], int channel)
{
    int i, next_state, anerror;
    int rx_bits[COHPSK_BITS_PER_FRAME];

    assert((channel == 0) || (channel == 1));
    int *ptest_bits_coh_rx = coh->ptest_bits_coh_rx[channel];

    for(i=0; i<COHPSK_BITS_PER_FRAME; i++) {
        rx_bits[i] = rx_bits_char[i];
    }

    *bit_errors = 0;
    for(i=0; i<COHPSK_BITS_PER_FRAME; i++) {
        anerror = (rx_bits[i] & 0x1) ^ ptest_bits_coh_rx[i];
        if ((anerror < 0) || (anerror > 1)) {
            fprintf(stderr, "i: %d rx_bits: %d ptest_bits_coh_rx: %d\n", i, rx_bits[i], ptest_bits_coh_rx[i]);
        }
        *bit_errors += anerror;
        error_pattern[i] = anerror;
    }

    /* state logic */

    next_state = *state;

    if (*state == 0) {
        if (*bit_errors < 4) {
            next_state = 1;
            ptest_bits_coh_rx += COHPSK_BITS_PER_FRAME;
            if (ptest_bits_coh_rx >= coh->ptest_bits_coh_end) {
                ptest_bits_coh_rx = (int*)test_bits_coh;
            }
        }
    }

    /* if 5 frames with large BER reset test frame sync */

    if (*state > 0) {
        if (*bit_errors > 8) {
            if (*state == 6)
                next_state = 0;
            else
                next_state = *state+1;
        }
        else
            next_state = 1;
    }

    if (*state > 0) {
        ptest_bits_coh_rx += COHPSK_BITS_PER_FRAME;
        if (ptest_bits_coh_rx >= coh->ptest_bits_coh_end) {
            ptest_bits_coh_rx = (int*)test_bits_coh;
        }
    }

    //fprintf(stderr, "state: %d next_state: %d bit_errors: %d\n", *state, next_state, *bit_errors);

    *state = next_state;
    coh->ptest_bits_coh_rx[channel] = ptest_bits_coh_rx;
}


int cohpsk_error_pattern_size(void) {
    return COHPSK_BITS_PER_FRAME;
}


float *cohpsk_get_rx_bits_lower(struct COHPSK *coh) {
    return coh->rx_bits_lower;
}

float *cohpsk_get_rx_bits_upper(struct COHPSK *coh) {
    return coh->rx_bits_upper;
}

void cohpsk_set_carrier_ampl(struct COHPSK *coh, int c, float ampl) {
    assert(c < COHPSK_NC*COHPSK_ND);
    coh->carrier_ampl[c] = ampl;
    fprintf(stderr, "cohpsk_set_carrier_ampl: %d %f\n", c, (double)ampl);
}
