/*
   tlininterp.c
   David Rowe
   Jan 2017

   Fast linear interpolator for high oversampling rates.  Upsample
   with a decent filter first such that the signal is "low pass" wrt
   to the input sample rate.

   build: gcc tlininterp.c -o tlininterp -Wall -O2

*/

#include <assert.h>
#include <getopt.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

#define NBUF         1000
#define SIGNED_16BIT    0
#define SIGNED_8BIT     1

void display_help(void) {
    fprintf(stderr, "\nusage: tlininterp inputRawFile OutputRawFile OverSampleRatio [-c]\n");
    fprintf(stderr, "\nUse - for stdin/stdout\n\n");
    fprintf(stderr, "-c complex signed 16 bit input and output\n");
    fprintf(stderr, "-d complex signed 16 bit input, complex signed 8 bit output\n");
    fprintf(stderr, "-f +Fs/4 freq shift\n\n");
}

int main(int argc, char *argv[]) {
    FILE       *fin, *fout;
    short       left[2], right[2], out[2*NBUF], i;
    float       oversample, t;
    int8_t      out_s8[2*NBUF];
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

    oversample = atof(argv[3]);
    if (oversample <= 1) {
 	display_help();
	exit(1);
    }
       
    int channels = 1;
    int freq_shift = 0;
    lo_i[0] = -1; lo_i[1] =  0;
    lo_q[0] =  0; lo_q[1] = -1;
    int format = SIGNED_16BIT;
    int opt;
    while ((opt = getopt(argc, argv, "cdf")) != -1) {
        switch (opt) {
        case 'c': channels = 2; break;
        case 'd': channels = 2; format = SIGNED_8BIT; break;
        case 'f': freq_shift = 1; break;
        default:
            display_help();
            exit(1);
        }
    }

    for (i=0; i<channels; i++)
        left[i] = 0;
    t = 0.0;
    int j = 0;
    while(fread(&right, sizeof(short)*channels, 1, fin) == 1) {
        while (t < 1.0) {

            for (i=0; i<channels; i++) {
                out[2*j+i] = (1.0 - t)*left[i] + t*right[i];
            }

            if (freq_shift) {

                /* update local osc recursion */

                lo_i[2] = -lo_i[0]; lo_q[2] = -lo_q[0];
                
                /* complex mixer to up-shift complex samples */

                int a = out[2*j];
                int b = out[2*j+1];
                int c = lo_i[2];
                int d = lo_q[2];

                out[2*j]   = a*c - b*d;
                out[2*j+1] = b*c + a*d;

                //fprintf(stderr, "% d % d % 5d % 5d\n", lo_i[2], lo_q[2], out[0], out[1]);

                /* update LO memory */

                lo_i[0] = lo_i[1]; lo_i[1] = lo_i[2]; 
                lo_q[0] = lo_q[1]; lo_q[1] = lo_q[2]; 
            }

            /* once we have enough samples write to disk */

            j++;
            if (j == NBUF) {
                if (format == SIGNED_16BIT) {
                    fwrite(&out, sizeof(short)*channels, NBUF, fout);
                } else {
                    for (i=0; i<channels*NBUF; i++) {
                        out_s8[i] = out[i] >> 8;
                    }
                    fwrite(&out_s8, sizeof(int8_t)*channels, NBUF, fout);
                }
                j = 0;
            }

            t += 1.0/oversample;
        }
        
        t -= 1.0;
        for (i=0; i<channels; i++)
            left[i] = right[i];
    }

    /* write remaining samples to disk */

    if (format == SIGNED_16BIT) {
        fwrite(&out, sizeof(short), j, fout);
    } else {
        for (i=0; i<j; i++) {
            out_s8[i] = out[i] >> 8;
        }
        fwrite(&out_s8, sizeof(int8_t)*channels, j, fout);
    }

    fclose(fout);
    fclose(fin);

    return 0;
}
