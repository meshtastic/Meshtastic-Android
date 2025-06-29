/*
  FILE...: mpdecode_core.h
  AUTHOR.: David Rowe
  CREATED: Sep 2016

  C-callable core functions for MpDecode, so they can be used for
  Octave and C programs.  Also some convenience functions to help use
  the C-callable LDPC decoder in C programs.
*/

#ifndef __MPDECODE_CORE__
#define __MPDECODE_CORE__

#include <stdint.h>

#include "comp.h"

struct LDPC {
    char name[32];
    int max_iter;
    int dec_type;
    int q_scale_factor;
    int r_scale_factor;
    int CodeLength;
    int NumberParityBits;
    int NumberRowsHcols;
    int max_row_weight;
    int max_col_weight;

    uint16_t *H_rows;
    uint16_t *H_cols;
    
    /* these two are fixed to code params */
    int ldpc_data_bits_per_frame;
    int ldpc_coded_bits_per_frame;

    /* support for partial use of data bits in codeword and unequal protection schemes */
    int protection_mode; 
    int data_bits_per_frame;
    int coded_bits_per_frame;
};

void encode(struct LDPC *ldpc, unsigned char ibits[], unsigned char pbits[]);

int run_ldpc_decoder(struct LDPC *ldpc, uint8_t out_char[], float input[], int *parityCheckCount);

void sd_to_llr(float llr[], float sd[], int n);
void Demod2D(float symbol_likelihood[], COMP r[], COMP S_matrix[], float EsNo, float fading[], float mean_amp, int number_symbols);
void Somap(float bit_likelihood[], float symbol_likelihood[], int M, int bps, int number_symbols);
void symbols_to_llrs(float llr[], COMP rx_qpsk_symbols[], float rx_amps[], float EsNo, float mean_amp, int nsyms);
void fsk_rx_filt_to_llrs(float llr[], float rx_filt[], float v_est, float SNRest, int M, int nsyms);

void ldpc_print_info(struct LDPC *ldpc);


#endif
