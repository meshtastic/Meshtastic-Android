/*---------------------------------------------------------------------------*\

  FILE........: ofdm_mem.c
  AUTHOR......: Don Reid
  DATE CREATED: 11 June 2018

  Prints out the memory used by the OFDM modem states.  Used to optimise
  memory use for thw STM32F4 port.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2018 Don Reid

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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <complex.h>

#include "codec2_ofdm.h"
#include "ofdm_internal.h"

int main()
{
    struct OFDM_CONFIG *ofdm_config;
    struct OFDM *ofdm;

    ofdm = ofdm_create(NULL);
    assert(ofdm != NULL);

    /* Get a copy of the actual modem config */
    ofdm_config = ofdm_get_config_param(ofdm);

    int ofdm_m = (int) (ofdm_config->fs / ofdm_config->rs); /* 144 */
    int ofdm_ncp = (int) (ofdm_config->tcp * ofdm_config->fs); /* 16 */
    int ofdm_bitsperframe = (ofdm_config->ns - 1) * (ofdm_config->nc * ofdm_config->bps);
    int ofdm_rowsperframe = ofdm_bitsperframe / (ofdm_config->nc * ofdm_config->bps);
    int ofdm_samplesperframe = ofdm_config->ns * (ofdm_m + ofdm_ncp);
    int ofdm_rxbuf = 3 * ofdm_samplesperframe + 3 * (ofdm_m + ofdm_ncp);
    int ofdm_nuwbits = (ofdm_config->ns - 1) * ofdm_config->bps - ofdm_config->txtbits;

    int used = 0;

    printf("struct OFDM.................: %zd\n", sizeof(struct OFDM));
    printf("config......................: %zd\n", sizeof(struct OFDM_CONFIG));
    used +=                                       sizeof(struct OFDM_CONFIG);
    printf("pilot_samples...............: %zd\n", sizeof (complex float) * (ofdm_m + ofdm_ncp));
    used +=                                       sizeof (complex float) * (ofdm_m + ofdm_ncp);
    printf("rxbuf.......................: %zd\n", sizeof (complex float) * ofdm_rxbuf);
    used +=                                       sizeof (complex float) * ofdm_rxbuf;
    printf("pilots......................: %zd\n", sizeof (complex float) * (ofdm_config->nc + 2));
    used +=                                       sizeof (complex float) * (ofdm_config->nc + 2);

    size_t rxsym_size = sizeof (complex float) * (ofdm_config->ns + 3) * (ofdm_config->nc + 2);

    printf("rx_sym......................: %zd\n", rxsym_size);
    used +=                                       rxsym_size;
    printf("rx_np.......................: %zd\n", sizeof (complex float) * (ofdm_rowsperframe * ofdm_config->nc));
    used +=                                       sizeof (complex float) * (ofdm_rowsperframe * ofdm_config->nc);
    printf("rx_amp......................: %zd\n", sizeof (float) * (ofdm_rowsperframe * ofdm_config->nc));
    used +=                                       sizeof (float) * (ofdm_rowsperframe * ofdm_config->nc);
    printf("aphase_est_pilot_log........: %zd\n", sizeof (float) * (ofdm_rowsperframe * ofdm_config->nc));
    used +=                                       sizeof (float) * (ofdm_rowsperframe * ofdm_config->nc);
    printf("tx_uw.......................: %zd\n", sizeof (int) * ofdm_nuwbits);
    used +=                                       sizeof (int) * ofdm_nuwbits;
    printf("sync_state..................: %zd\n", sizeof (State));
    used +=                                       sizeof (State);
    printf("last_sync_state.............: %zd\n", sizeof (State));
    used +=                                       sizeof (State);
    printf("sync_state_interleaver......: %zd\n", sizeof (State));
    used +=                                       sizeof (State);
    printf("last_sync_state_interleaver.: %zd\n", sizeof (State));
    used +=                                       sizeof (State);

    // add in non-array sizes
    int single = 0;
    single +=  8 * sizeof(int);
    single += 13 * sizeof(float);
    single +=  1 * sizeof(complex float);
    single +=  1 * sizeof(float *);
    single +=  4 * sizeof(bool);
    printf("single values...............: %d\n",  single);
    used +=                                       single;

    printf("Total used .................: %zd\n", (size_t) used);

    ofdm_destroy(ofdm);

    return 0;
}

