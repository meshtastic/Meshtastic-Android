/* 
  FILE...: ldpc_enc.c
  AUTHOR.: Bill Cowley, David Rowe
  CREATED: Sep 2016

  RA LDPC encoder program. Using the elegant back substitution of RA
  LDPC codes.
*/

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>

#include "mpdecode_core.h"
#include "ldpc_codes.h"
#include "ofdm_internal.h"

int opt_exists(char *argv[], int argc, char opt[]) {
    int i;
    for (i=0; i<argc; i++) {
        if (strcmp(argv[i], opt) == 0) {
            return i;
        }
    }
    return 0;
}

int main(int argc, char *argv[])
{
    FILE         *fin, *fout;
    int           arg, sd, i, frames, codename, testframes, Nframes, data_bits_per_frame, parity_bits_per_frame;
    struct LDPC   ldpc;
    int unused_data_bits;
    
    if (argc < 2) {
        fprintf(stderr, "\n");
        fprintf(stderr, "usage: %s InputOneBytePerBit OutputFile [--sd] [--code CodeName] [--testframes Nframes] [--unused numUnusedDataBits]\n", argv[0]);
        fprintf(stderr, "\n");
        fprintf(stderr, "usage: %s --listcodes\n\n", argv[0]);
        fprintf(stderr, "  List supported codes (more can be added via using Octave ldpc scripts)\n");
        fprintf(stderr, "\n");
        exit(0);
    }

    /* todo: put this in a function file to share with ldpc_dec.c */

    if ((codename = opt_exists(argv, argc, "--listcodes")) != 0) {
        ldpc_codes_list();
        exit(0);
    }

    /* set up LDPC code */

    int code_index = 0;
    if ((codename = opt_exists(argv, argc, "--code")) != 0)
        code_index = ldpc_codes_find(argv[codename+1]);
    memcpy(&ldpc,&ldpc_codes[code_index],sizeof(struct LDPC));
    fprintf(stderr, "Using: %s\n", ldpc.name);
    
    data_bits_per_frame = ldpc.NumberRowsHcols;
    parity_bits_per_frame = ldpc.NumberParityBits;
    
    unsigned char ibits[data_bits_per_frame];
    unsigned char pbits[parity_bits_per_frame];
    float         sdout[data_bits_per_frame+parity_bits_per_frame];

    if (strcmp(argv[1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[1],"rb")) == NULL ) {
        fprintf(stderr, "Error opening input bit file: %s: %s.\n",
                argv[1], strerror(errno));
        exit(1);
    }
        
    if (strcmp(argv[2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[2],"wb")) == NULL ) {
        fprintf(stderr, "Error opening output bit file: %s: %s.\n",
                argv[2], strerror(errno));
        exit(1);
    }
    
    sd = 0;
    if (opt_exists(argv, argc, "--sd")) {
        sd = 1;
    }

    unused_data_bits = 0;
    if ((arg = opt_exists(argv, argc, "--unused"))) {
        unused_data_bits = atoi(argv[arg+1]);
    }
    
    testframes = Nframes = 0;

    if ((arg = (opt_exists(argv, argc, "--testframes")))) {
        testframes = 1;
        Nframes = atoi(argv[arg+1]);
        fprintf(stderr, "Nframes: %d\n", Nframes);
    }

    frames = 0;
    int written = 0;
    
    while (fread(ibits, sizeof(char), data_bits_per_frame, fin) == data_bits_per_frame) {
        if (testframes) {
            uint16_t r[data_bits_per_frame];
            ofdm_rand(r, data_bits_per_frame);

            for(i=0; i<data_bits_per_frame-unused_data_bits; i++) {
                ibits[i] = r[i] > 16384;
            }
            for(i=data_bits_per_frame-unused_data_bits; i<data_bits_per_frame; i++) {
                ibits[i] = 1;
            }
           
        }
        
        encode(&ldpc, ibits, pbits);  
        
        if (sd) {
            /* map to BPSK symbols */
            for (i=0; i<data_bits_per_frame-unused_data_bits; i++)
                sdout[i] = 1.0 - 2.0 * ibits[i];
            for (i=0; i<parity_bits_per_frame; i++)
                sdout[i+data_bits_per_frame-unused_data_bits] = 1.0 - 2.0 * pbits[i];
            written += fwrite(sdout, sizeof(float), data_bits_per_frame-unused_data_bits+parity_bits_per_frame, fout); 
        }
        else {
            written += fwrite(ibits, sizeof(char), data_bits_per_frame, fout); 
            written += fwrite(pbits, sizeof(char), parity_bits_per_frame, fout); 
        }
        
        frames++;       
        if (testframes && (frames >= Nframes)) {
            goto finished;
        }
    }

 finished:

    fprintf(stderr, "written: %d\n", written);
    fclose(fin);  
    fclose(fout); 

    return 1;
}
