/*---------------------------------------------------------------------------*\

  FILE........: ofdm_get_test_bits.c
  AUTHOR......: David Rowe
  DATE CREATED: Mar 2018

  Generate input for the OFDM modem in either coded or uncoded mode.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2018 David Rowe

  All rights reserved.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License version 2.1, as
  published by the Free Software Foundation.  This program is
  distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, see <http://www.gnu.org/licenses/>.
*/

#define OPTPARSE_IMPLEMENTATION
#define OPTPARSE_API static
#include "optparse.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <errno.h>

#include "codec2_ofdm.h"
#include "ofdm_internal.h"
#include "ldpc_codes.h"
#include "interldpc.h"
#include "varicode.h"

#define IS_DIR_SEPARATOR(c)     ((c) == '/')

static const char *progname;

void opt_help() {
    fprintf(stderr, "\nUsage: %s [options]\n\n", progname);
    fprintf(stderr, "  --out     filename  Name of OutputOneCharPerBitFile\n");
    fprintf(stderr, "  --frames  n         Number of frames to output (default 10)\n");
    fprintf(stderr, "  --length  n         Frame length in bits (default 238)\n");
    fprintf(stderr, "  --bcb               Insert burst control byte at the start of each frame (FSK_LDPC testing)\n");    
    fprintf(stderr, "  --verbose           Output variable assigned values to stderr\n\n");

    exit(-1);
}

int main(int argc, char *argv[])
{
    FILE         *fout;
    char         *fout_name = NULL;
    int           opt, verbose, n;
    int           Nframes, output_specified, bcb_en;
    int           Ndatabitsperpacket;
    uint8_t       burst_control;
    
    char *pn = argv[0] + strlen (argv[0]);

    while (pn != argv[0] && !IS_DIR_SEPARATOR (pn[-1]))
        --pn;
    
    progname = pn;

    /* Turn off stream buffering */

    setvbuf(stdout, NULL, _IONBF, BUFSIZ);

    fout = stdout;
    output_specified = 0;
    Nframes = 10;
    Ndatabitsperpacket = 224;
    verbose = 0;
    bcb_en = 0;
    
    struct optparse options;

    struct optparse_long longopts[] = {
        {"bcb",     'b', OPTPARSE_NONE},
        {"out",     'o', OPTPARSE_REQUIRED},
        {"frames",  'n', OPTPARSE_REQUIRED},
        {"length",  'l', OPTPARSE_REQUIRED},
        {"verbose", 'v', OPTPARSE_NONE},
        {0, 0, 0}
    };

    optparse_init(&options, argv);

    while ((opt = optparse_long(&options, longopts, NULL)) != -1) {
        switch (opt) {
            case '?':
                opt_help();
            case 'b':
                bcb_en = 1;
                break;
            case 'o':
                fout_name = options.optarg;
                output_specified = 1;
                break;
            case 'n':
                Nframes = atoi(options.optarg);
                break;
            case 'l':
                Ndatabitsperpacket = atoi(options.optarg);
                break;
            case 'v':
                verbose = 1;
        }
    }

    /* Print remaining arguments to give user a hint */

    char *arg;

    while ((arg = optparse_arg(&options)))
        fprintf(stderr, "%s\n", arg);

    if (output_specified) {
        if ((fout = fopen(fout_name, "wb")) == NULL) {
            fprintf(stderr, "Error opening output bit file: %s\n", fout_name);
            exit(-1);
        }
    }

    if (verbose)
        fprintf(stderr, "Nframes: %d Ndatabitsperframe: %d bcb: %d\n", Nframes, Ndatabitsperpacket, bcb_en);

    uint8_t data_bits[Ndatabitsperpacket];
    ofdm_generate_payload_data_bits(data_bits, Ndatabitsperpacket);

    burst_control = 1;
    for (n = 0; n<Nframes; n++) {
        if (bcb_en) fwrite(&burst_control, 1, 1, fout);
 	fwrite(data_bits, sizeof(char), Ndatabitsperpacket, fout);
        burst_control = 0;
    }
    if (bcb_en) {
        // dummy end frame just to signal end of burst
        burst_control = 2;
	fwrite(&burst_control, 1, 1, fout);
        memset(data_bits, 0, Ndatabitsperpacket);
 	fwrite(data_bits, sizeof(char), Ndatabitsperpacket, fout);
    }    
    
    if (output_specified)
        fclose(fout);

    return 0;
}

