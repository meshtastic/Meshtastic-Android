/*----------------------------------------------------------------------------*\

  FILE....: speexnoisesup.c
  AUTHOR..: David Rowe
  CREATED.: Sun 22 June 2014

  File I/O based test program for Speex pre-processor, used for
  initial testing of Speech noise supression.

\*----------------------------------------------------------------------------*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdint.h>
#include <speex/speex_preprocess.h>

#define    N          80
#define    FS       8000

int main(int argc, char *argv[]) {
    FILE *fin, *fout;
    short buf[N];
    SpeexPreprocessState *st;

    if (argc < 2) {
	    printf("usage: %s InFile OutFile\n", argv[0]);
	    exit(0);
    }

    if (strcmp(argv[1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[1],"rb")) == NULL ) {
        fprintf(stderr, "Error opening %s\n", argv[1]);
        exit(1);
    }

    if (strcmp(argv[2], "-") == 0) fout = stdout;
    else if ((fout = fopen(argv[2],"wb")) == NULL) {
	    fprintf(stderr, "Error opening %s\n", argv[2]);
	    exit(1);
    }

    st = speex_preprocess_state_init(N, FS);

    while(fread(buf, sizeof(short), N, fin) == N) {
        speex_preprocess_run(st, buf);
	    fwrite(buf, sizeof(short), N, fout);
        if (fout == stdout) fflush(stdout);
    }

    speex_preprocess_state_destroy(st);

    fclose(fin);
    fclose(fout);

    return 0;
}
