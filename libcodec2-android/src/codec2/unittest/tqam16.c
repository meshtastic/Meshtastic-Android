/*---------------------------------------------------------------------------*\

  FILE........: tqam16.c
  AUTHOR......: David Rowe
  DATE CREATED: August 2020

  Simple sanity check for QAM16 symbol mapping.

\*---------------------------------------------------------------------------*/

#include <stdio.h>
#include <string.h>
#include "ofdm_internal.h"

int main(void) {
    int c;
    for(c=0; c<16; c++) {
        int tx_bits[4], rx_bits[4];
        for(int i=0; i<4; i++)
            tx_bits[i] = (c >> (3-i)) & 0x1;
        complex float symbol = qam16_mod(tx_bits);
        qam16_demod(symbol, rx_bits);
        if (memcmp(tx_bits, rx_bits, 4)) {
            fprintf(stderr, "FAIL on %d!\ntx_bits: ",c);
            for(int i=0; i<4; i++) fprintf(stderr, "%d ", tx_bits[i]);
            fprintf(stderr, "%f %f\nrx_bits: ", creal(symbol), cimag(symbol));
            for(int i=0; i<4; i++) fprintf(stderr, "%d ", rx_bits[i]);
            fprintf(stderr, "%f %f\n", creal(symbol), cimag(symbol));
            return 1;
        }
    }

    fprintf(stderr, "%d tested OK...\nPASS!\n", c);
    return 0;
}


