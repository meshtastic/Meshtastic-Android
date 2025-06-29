/*---------------------------------------------------------------------------*\

  FILE........: interldpc.h
  AUTHOR......: David Rowe
  DATE CREATED: April 2018

  Helper functions for interleaved LDPC modems.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2018 David Rowe

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

#ifndef __INTERLDPC__
#define __INTERLDPC__

#include <stdint.h>

#include "comp.h"
#include "mpdecode_core.h"
#include "ofdm_internal.h"

/* LDPC unequal protection modes */
#define LDPC_PROT_EQUAL 0    /* all bits in LDPC codeword used, e.g. 700D/700E */
#define LDPC_PROT_2020  1    /* (504,396) but some data bits unused */
#define LDPC_PROT_2020B 3    /* (112,56) but we only protect stage1 VQ in each LPCNet frame */

void set_up_ldpc_constants(struct LDPC *ldpc, int code_length, int parity_bits);
void set_data_bits_per_frame(struct LDPC *ldpc, int new_data_bits_per_frame);
void ldpc_encode_frame(struct LDPC *ldpc, int codeword[], unsigned char tx_bits_char[]);
void qpsk_modulate_frame(COMP tx_symbols[], int codeword[], int n);
void ldpc_decode_frame(struct LDPC *ldpc, int *parityCheckCount, int *iter,  uint8_t  out_char[], float llr[]);
int count_uncoded_errors(struct LDPC *ldpc, struct OFDM_CONFIG *config, COMP codeword_symbols_de[], int crc16);
int count_errors(uint8_t tx_bits[], uint8_t rx_bits[], int n);
void count_errors_protection_mode(int protection_mode, int *pNerrs, int *pNcoded, uint8_t tx_bits[],
                                  uint8_t rx_bits[], int n);
void ofdm_ldpc_interleave_tx(struct OFDM *ofdm, struct LDPC *ldpc, complex float tx_sams[], uint8_t tx_bits[], uint8_t txt_bits[]);

#endif
