/* 
  FILE...: tollr.c
  AUTHOR.: David Rowe
  CREATED: July 2020

  Converts oneBitPerByte hard decisions to LLRs for LDPC testing.
*/

#include <stdio.h>
#include <stdint.h>

int main(void) {
    uint8_t bit;
    while(fread(&bit,sizeof(uint8_t), 1, stdin)) {
        float llr = 10.0*(1-2*bit);
        fwrite(&llr,sizeof(float),1,stdout);
    }
    return 0;
}
