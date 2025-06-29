/*---------------------------------------------------------------------------*\

  FILE........: modem_stats.h
  AUTHOR......: David Rowe
  DATE CREATED: June 2015

  Common structure for returning demod stats from fdmdv and cohpsk modems.

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

#ifndef __MODEM_STATS__
#define __MODEM_STATS__

#include "comp.h"

#ifdef __cplusplus
  extern "C" {
#endif

#define MODEM_STATS_NC_MAX      50
#define MODEM_STATS_NR_MAX      160
#define MODEM_STATS_ET_MAX      8
#define MODEM_STATS_EYE_IND_MAX 160
#define MODEM_STATS_NSPEC       512
#define MODEM_STATS_MAX_F_HZ    4000
#define MODEM_STATS_MAX_F_EST   4

struct MODEM_STATS {
    int    Nc;
    float  snr_est;                          /* estimated SNR of rx signal in dB (3 kHz noise BW)  */
#ifndef __EMBEDDED__
    COMP   rx_symbols[MODEM_STATS_NR_MAX][MODEM_STATS_NC_MAX+1];
                                             /* latest received symbols, for scatter plot          */
#endif
    int    nr;                               /* number of rows in rx_symbols                       */
    int    sync;                             /* demod sync state                                   */
    float  foff;                             /* estimated freq offset in Hz                        */
    float  rx_timing;                        /* estimated optimum timing offset in samples         */
    float  clock_offset;                     /* Estimated tx/rx sample clock offset in ppm         */
    float  sync_metric;                      /* number between 0 and 1 indicating quality of sync  */
    int    pre, post;                        /* preamble/postamble det counters for burst data     */
    int    uw_fails;                         /* Failed to detect Unique word (burst data)          */
    
    /* FSK eye diagram traces */
    /* Eye diagram plot -- first dim is trace number, second is the trace idx */
#ifndef __EMBEDDED__
    float  rx_eye[MODEM_STATS_ET_MAX][MODEM_STATS_EYE_IND_MAX];
    int    neyetr;                           /* How many eye traces are plotted */
    int    neyesamp;                         /* How many samples in the eye diagram */

    /* optional for FSK modems - est tone freqs */

    float f_est[MODEM_STATS_MAX_F_EST];
#endif

    /* Buf for FFT/waterfall */

#ifndef __EMBEDDED__
   float  fft_buf[2*MODEM_STATS_NSPEC];
   void  *fft_cfg;
#endif
};

void modem_stats_open(struct MODEM_STATS *f);
void modem_stats_close(struct MODEM_STATS *f);
void modem_stats_get_rx_spectrum(struct MODEM_STATS *f, float mag_spec_dB[], COMP rx_fdm[], int nin);

#ifdef __cplusplus
}
#endif

#endif
