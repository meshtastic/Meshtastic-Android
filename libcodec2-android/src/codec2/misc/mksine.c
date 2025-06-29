/*
  mksine.c
  David Rowe
  10 Nov 2010

  Creates a file of sine wave samples.
*/

#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <errno.h>

#define TWO_PI     6.283185307
#define FS         8000.0

int main(int argc, char *argv[]) {
    FILE *f;
    int   i,n;
    float freq, length;
    short *buf;
    float amp = 1E4;

    if (argc < 4) {
	      printf("usage: %s outputFile frequencyHz lengthSecs [PeakAmp]\n", argv[0]);
	      exit(1);
    }

    if (strcmp(argv[1], "-") == 0) {
        f = stdout;
    } else if ( (f = fopen(argv[1],"wb")) == NULL ) {
	      fprintf(stderr, "Error opening output file: %s: %s.\n", argv[3], strerror(errno));
	      exit(1);
    }
    freq = atof(argv[2]);
    length = atof(argv[3]);
    if (argc == 5) amp = atof(argv[4]);

    n = length*FS;
    buf = (short*)malloc(sizeof(short)*n);
    assert(buf != NULL);

    for(i=0; i<n; i++)
	      buf[i] = amp*cos(freq*i*(TWO_PI/FS));

    fwrite(buf, sizeof(short), n, f);

    fclose(f);
    free(buf);

    return 0;
}
