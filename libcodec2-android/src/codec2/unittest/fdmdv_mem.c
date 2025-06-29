/*---------------------------------------------------------------------------*\

  FILE........: fdmdv_mem.c
  AUTHOR......: David Rowe
  DATE CREATED: 25 June 2014

  Prints out the memory used by the FDMDV modem states.  Used to optimise
  memory use for thw STM32F4 port.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2014 David Rowe

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

#include "fdmdv_internal.h"

extern float pilot_coeff[];

int main(int argc, char *argv[])
{
    struct FDMDV *fdmdv;

    printf("struct FDMDV..........: %ld\n", sizeof(struct FDMDV));
    printf("prev_tx_symbols.......: %ld\n", sizeof(fdmdv->prev_tx_symbols));
    printf("tx_filter_memory......: %ld\n", sizeof(fdmdv->tx_filter_memory));
    printf("phase_tx..............: %ld\n", sizeof(fdmdv->phase_tx));
    printf("freq..................: %ld\n", sizeof(fdmdv->freq));
    printf("pilot_lut.............: %ld\n", sizeof(fdmdv->pilot_lut));
    printf("pilot_baseband1.......: %ld\n", sizeof(fdmdv->pilot_baseband1));
    printf("pilot_baseband2.......: %ld\n", sizeof(fdmdv->pilot_baseband2));
    printf("pilot_lpf1............: %ld\n", sizeof(fdmdv->pilot_lpf1));
    printf("pilot_lpf2............: %ld\n", sizeof(fdmdv->pilot_lpf2));
    printf("S1....................: %ld\n", sizeof(fdmdv->S1));
    printf("S2....................: %ld\n", sizeof(fdmdv->S2));
    printf("phase_rx..............: %ld\n", sizeof(fdmdv->phase_rx));
    printf("rx_fdm_mem............: %ld\n", sizeof(fdmdv->rx_fdm_mem));
    printf("rx_filter_mem_timing..: %ld\n", sizeof(fdmdv->rx_filter_mem_timing));
    printf("phase_difference......: %ld\n", sizeof(fdmdv->phase_difference));
    printf("prev_rx_symbols.......: %ld\n", sizeof(fdmdv->prev_rx_symbols));

    return 0;
}

