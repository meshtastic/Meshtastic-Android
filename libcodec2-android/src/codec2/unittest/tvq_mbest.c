/*
  tvq_mbest.c
  David Rowe Dec 2019

  Generate some test vectors to exercise misc/vq_mbest.c
*/

#include <assert.h>
#include <stdlib.h>
#include <stdio.h>

void write_float_file(char fn[], float *values, int n) {
    FILE *f=fopen(fn,"wb");
    assert(f != NULL);
    assert(fwrite(values, sizeof(float), n, f) == n);
    fclose(f);
}

int main(void) {
    /* we're only interested in searching the inner 2 values, outer elements should be
       ignored */
    float target[] = {0.0,1.0,1.0,0.0};
    write_float_file("target.f32", target, 4);
    float vq1[] = {1.0,0.9,0.9,1.0,  /* this will be a better match on first stage */
		   2.0,0.8,0.8,2.0}; /* but after second stage should choose this  */
    write_float_file("vq1.f32", vq1, 8);
    float vq2[] = {10.0,0.3,0.3,10.0,
		   20.0,0.2,0.2,20.0}; /* 0.8+0.2 == 1.0 so best 2nd stage entry     */
    write_float_file("vq2.f32", vq2, 8);
    return 0;
}
