/*---------------------------------------------------------------------------*\

  FILE........: tfsk_llr.c
  AUTHOR......: David Rowe
  DATE CREATED: July 2020

  Simple test program for 4FSK LLR routines.

\*---------------------------------------------------------------------------*/

#include <stdio.h>
#include <math.h>
#include "mpdecode_core.h"

#define M       4
#define BPS     2
#define NSYM     5
#define V_EST    2
#define SNR_EST 10

/* Generated test vectors with:

   octave:100> init_cml('~/cml/');
   octave:101> rx_filt=[1 0 0 0; 0 1 0 0; 0 0 1 0; 0 0 0 1]
   octave:102> symL = DemodFSK(rx_filt,10,1); -Somap(symL)
*/

/* one col per symbol:
       0    1    2    3    4 */
 float rx_filt[] = {
     1.0, 0.0, 0.0, 0.0, 1.0,  /* filter 0 */
     0.0, 1.0, 0.0, 0.0, 0.0,  /* filter 1 */
     0.0, 0.0, 1.0, 0.0, 0.0,  /* filter 2 */
     0.0, 0.0, 0.0, 1.0, 0.0   /* filter 3 */
};

float llrs_target[] = {
     7.3252,   7.3252,  /* bit 0, 1      */
     7.3252,  -7.3252,  /*     2, 3, ... */
    -7.3252,   7.3252,
    -7.3252,  -7.3252,
     7.3252,   7.3252
};

int main(void) { 
    float llrs[BPS*NSYM] = {0};
    
    fsk_rx_filt_to_llrs(llrs, rx_filt, V_EST, SNR_EST, M, NSYM);

    float error = 0.0;
    for(int i=0; i<NSYM*BPS; i++) {
        fprintf(stderr,"% f\n",llrs[i]);
        error += pow(llrs[i]-llrs_target[i],2.0);
    }

    if (error < 1E-3) {
        fprintf(stderr, "PASS\n");
        return 0;
    }
    else {
        fprintf(stderr, "FAIL\n");
        return 1;
    }
}


