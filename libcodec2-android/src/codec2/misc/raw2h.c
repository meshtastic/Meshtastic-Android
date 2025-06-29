/*
  raw2h.c
  David Rowe
  10 April 2013

  Converts a raw sound file to a C header file.  Used for generating arrays to
  test Codec2 on embedded systems without disk I/O.
*/

#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

int main(int argc, char *argv[]) {
    FILE *fraw, *fheader;
    int   i, samples, ret;
    short sam;

    if (argc != 5) {
	printf("usage: %s inputRawFile outputHeaderFile arrayName samples\n", argv[0]);
	exit(1);
    }

    fraw = fopen(argv[1] ,"rb");
    assert(fraw != NULL);
    fheader = fopen(argv[2],"wt");
    assert(fheader != NULL);
    samples = atoi(argv[4]);

    fprintf(fheader, "short %s[] = {\n", argv[3]);
    for(i=0; i<samples-1; i++) {
	ret = fread(&sam, sizeof(short), 1, fraw);
        assert(ret == 1);
        fprintf(fheader, "%d,\n", sam);
    }
    ret = fread(&sam, sizeof(short), 1, fraw);
    assert(ret == 1);
    fprintf(fheader, "%d\n};\n", sam);

    fclose(fraw);
    fclose(fheader);

    return 0;
}
