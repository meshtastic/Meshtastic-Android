/*
   tdec.c
   David Rowe
   Jan 2017

   Trivial non filtered decimator for high ration sample rate conversion.

   build: gcc tdec.c -o tdec -Wall -O2

*/

#include <assert.h>
#include <getopt.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

#define SIGNED_16BIT   0
#define SIGNED_8BIT    1
#define UNSIGNED_8BIT  2

void freq_shift_complex_buf(short buf[], int n, int lo_i[], int lo_q[]);

void display_help(void) {
    fprintf(stderr, "\nusage: tdec inputRawFile OutputRawFile DecimationRatio [-c]\n");
    fprintf(stderr, "\nUse - for stdin/stdout\n\n");
    fprintf(stderr, "-c complex signed 16 bit input and output\n");
    fprintf(stderr, "-d complex signed 8 bit input (e.g. HackRF), complex signed 16 bit output\n");
    fprintf(stderr, "-e complex unsigned 8 bit input (e.g. RTL-SDR), complex signed 16 bit output\n");
    fprintf(stderr, "-f -Fs/4 freq shift\n\n");
}

int main(int argc, char *argv[]) {
    FILE       *fin, *fout;
    short       dec;
    int         lo_i[3], lo_q[3];

    if (argc < 3) {
	display_help();
	exit(1);
    }

    if (strcmp(argv[1], "-") == 0) 
        fin = stdin;
    else
        fin = fopen(argv[1], "rb");
    assert(fin != NULL);

    if (strcmp(argv[2], "-") == 0) 
        fout = stdout;
    else
        fout = fopen(argv[2], "wb");
    assert(fout != NULL);

    dec = atoi(argv[3]);

    int channels = 1;
    int freq_shift = 0;
    lo_i[0] = -1; lo_i[1] =  0;
    lo_q[0] =  0; lo_q[1] = -1;
    int opt;
    int format = SIGNED_16BIT;
    while ((opt = getopt(argc, argv, "cdef")) != -1) {
        switch (opt) {
        case 'c': channels = 2; break;
        case 'd': channels = 2; format = SIGNED_8BIT; break;
        case 'e': channels = 2; format = UNSIGNED_8BIT; break;
        case 'f': freq_shift = 1; break;
        default:
            display_help();
            exit(1);
        }
    }

    if (format == SIGNED_16BIT) {
        short buf[dec*channels];
        while(fread(buf, sizeof(short)*channels, dec, fin) == dec) {
            if (freq_shift)            
                freq_shift_complex_buf(buf, dec*channels, lo_i, lo_q);
            fwrite(buf, sizeof(short), channels, fout);
        }
    }
    else {
        uint8_t inbuf[dec*channels];
        short   outbuf[dec*channels];
        short   sam, i;
        
        while(fread(inbuf, sizeof(uint8_t)*channels, dec, fin) == dec) {
            for (i=0; i<dec*channels; i++) {
                assert((format == SIGNED_8BIT) || (format == UNSIGNED_8BIT));
                if (format == SIGNED_8BIT) {
                    sam = (short)inbuf[i];
                    sam <<= 8;
                } else {
                    sam = (short)inbuf[i] - 127;
                    sam <<= 8;
                }
                outbuf[i] = sam;
            }
            if (freq_shift)
                freq_shift_complex_buf(outbuf, dec*channels, lo_i, lo_q);
            fwrite(outbuf, sizeof(short), channels, fout);
        }

    }

    fclose(fout);
    fclose(fin);

    return 0;
}
                

void freq_shift_complex_buf(short buf[], int n, int lo_i[], int lo_q[]) {
    int i;

    for (i=0; i<n; i+=2) {
        /* update local osc recursion */

        lo_i[2] = -lo_i[0]; lo_q[2] = -lo_q[0];

        /* freq shift down input samples */

        int a = buf[i];
        int b = buf[i+1];
        int c = lo_i[2];
        int d = -lo_q[2];  /* conj LO as down shift */

        buf[i]   = a*c - b*d;
        buf[i+1] = b*c + a*d;

        //fprintf(stderr, "% d % d % 5d % 5d\n", lo_i[2], lo_q[2], buf[i], buf[i+1]);

        /* update LO memory */

        lo_i[0] = lo_i[1]; lo_i[1] = lo_i[2]; 
        lo_q[0] = lo_q[1]; lo_q[1] = lo_q[2]; 
    }
}

