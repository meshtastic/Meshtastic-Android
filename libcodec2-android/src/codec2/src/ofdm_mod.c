/*---------------------------------------------------------------------------*\

  FILE........: ofdm_mod.c
  AUTHOR......: David Rowe
  DATE CREATED: March 2018

  Given an input file of bits (note one bit per char format), outputs
  a raw file (8kHz, 16 bit shorts) of OFDM modem samples ready to send
  over a HF radio channel.

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

#include "codec2_ofdm.h"
#include "ofdm_internal.h"
#include "gp_interleaver.h"
#include "ldpc_codes.h"
#include "interldpc.h"
#include "varicode.h"

#define IS_DIR_SEPARATOR(c)     ((c) == '/')

static const char *progname;

void opt_help() {
    fprintf(stderr, "\nusage: %s [options]\n\n", progname);
    fprintf(stderr, "  --in      filename    Name of InputOneCharPerBitFile\n");
    fprintf(stderr, "  --out     filename    Name of OutputModemRawFile\n");
    fprintf(stderr, "  --mode    modeName    Predefined mode 700D|700E|2020|2020B|datac0|datac1|datac3\n");
    fprintf(stderr, "  --nc      [17..62]    Number of Carriers (17 default, 62 max)\n");
    fprintf(stderr, "  --ns       symbols    One pilot every ns symbols (8 default)\n");
    fprintf(stderr, "  --tcp        Nsecs    Cyclic Prefix Duration (.002 default)\n");
    fprintf(stderr, "  --ts         Nsecs    Symbol Duration (.018 default)\n");
    fprintf(stderr, "  --testframes Nsecs    Transmit test frames for Nsec (--testframes NpacketsPerBurst in burst mode)\n");
    fprintf(stderr, "  --tx_freq     freq    Set an optional modulation TX centre frequency (1500.0 default)\n");
    fprintf(stderr, "  --rx_freq     freq    Set an optional modulation RX centre frequency (1500.0 default)\n\n");
    fprintf(stderr, "  --verbose  [1|2|3]    Verbose output level to stderr (default off)\n");
    fprintf(stderr, "  --txbpf               Transmit band pass filter on (default off)\n");
    fprintf(stderr, "  --clip                Transmit clipper (default off)\n");
    fprintf(stderr, "  --text                Include a standard text message boolean (default off)\n");
    fprintf(stderr, "  -i --ldpc    [1|2]    Run LDPC decoder (1 -> (224,112) 700D code, 2 -> (504,396) 2020 code).\n"
                    "                        In testframe mode raw and coded errors will be counted.\n");
    fprintf(stderr, "  --dpsk                Differential PSK.\n");
    fprintf(stderr, "  --bursts   nBursts    Burst mode: Send nBursts of testframes each\n");
    fprintf(stderr, "\n");
    exit(-1);
}

int main(int argc, char *argv[]) {
    char *fin_name, *fout_name;
    int i, opt, val;

    char *pn = argv[0] + strlen(argv[0]);

    while (pn != argv[0] && !IS_DIR_SEPARATOR(pn[-1]))
        --pn;

    progname = pn;

    /* Turn off stream buffering */

    setvbuf(stdin, NULL, _IONBF, BUFSIZ);
    setvbuf(stdout, NULL, _IONBF, BUFSIZ);

    FILE *fin = stdin;
    FILE *fout = stdout;

    /* set for LDPC coded or uncoded frames */

    int ldpc_en = 0;

    int input_specified = 0;
    int output_specified = 0;
    int verbose = 0;
    int clip_en = 0;
    int txbpf_en = 0;
    int testframes = 0;
    int use_text = 0;
    int dpsk = 0;

    int Npackets = 0;
    int Nsec = 0;
    int burst_mode = 0;
    int Nbursts = 1;
    
    /* set up the default modem config */
    struct OFDM_CONFIG *ofdm_config = (struct OFDM_CONFIG *) calloc(1, sizeof (struct OFDM_CONFIG));
    assert(ofdm_config != NULL);
    char mode[32] = "700D";
    ofdm_init_mode(mode, ofdm_config);

    int   Ndatabitsperpacket = 0;
    struct optparse options;

    struct optparse_long longopts[] = {
        {"in", 'a', OPTPARSE_REQUIRED},
        {"out", 'b', OPTPARSE_REQUIRED},
        {"nc", 'c', OPTPARSE_REQUIRED},
        {"ns", 'm', OPTPARSE_REQUIRED},
        {"tcp", 'd', OPTPARSE_REQUIRED},
        {"ts", 'e', OPTPARSE_REQUIRED},
        {"testframes", 'f', OPTPARSE_REQUIRED},
        {"tx_freq", 'n', OPTPARSE_REQUIRED},
        {"rx_freq", 'i', OPTPARSE_REQUIRED},
        {"ldpc", 'j', OPTPARSE_NONE},
        {"txbpf", 'k', OPTPARSE_NONE},
        {"clip", 'r', OPTPARSE_NONE},
        {"text", 'l', OPTPARSE_NONE},
        {"verbose", 'v', OPTPARSE_REQUIRED},
        {"dpsk", 'q', OPTPARSE_NONE},
        {"mode", 'g', OPTPARSE_REQUIRED},
        {"help", 'h', OPTPARSE_NONE},
        {"bursts", 'o', OPTPARSE_REQUIRED},
        {0, 0, 0}
    };

    optparse_init(&options, argv);

    while ((opt = optparse_long(&options, longopts, NULL)) != -1) {
        switch (opt) {
            case '?':
            case 'h':
                opt_help();
            case 'a':
                fin_name = options.optarg;
                input_specified = 1;
                break;
            case 'b':
                fout_name = options.optarg;
                output_specified = 1;
                break;
            case 'c':
                val = atoi(options.optarg);

                if (val > 62 || val < 17) {
                    opt_help();
                } else {
                    ofdm_config->nc = val;
                }
                break;
            case 'd':
                ofdm_config->tcp = atof(options.optarg);
                break;
            case 'e':
                ofdm_config->ts = atof(options.optarg);
                ofdm_config->rs = 1.0f / ofdm_config->ts;
                break;
            case 'm':
                ofdm_config->ns = atoi(options.optarg);
                break;
            case 'f':
                testframes = 1;
                Nsec = atoi(options.optarg);
                break;
            case 'g':
                strcpy(mode, options.optarg);
                ofdm_init_mode(mode, ofdm_config);
                break;
            case 'n':
                ofdm_config->tx_centre = atof(options.optarg);
                break;
            case 'o':
                burst_mode = 1;
                Nbursts = atoi(options.optarg);
                fprintf(stderr, "bursts: %d\n", Nbursts);
                break;
            case 'i':
                ofdm_config->rx_centre = atof(options.optarg);
                break;
            case 'j':
                ldpc_en = 1;
                break;
            case 'k':
                txbpf_en = 1;
                break;
            case 'l':
                use_text = 1;
                break;
            case 'q':
                dpsk = 1;
                break;
            case 'r':
                clip_en = 1;
                break;
            case 'v':
                verbose = atoi(options.optarg);
                if (verbose < 0 || verbose > 3)
                    verbose = 0;
        }
    }

    /* Print remaining arguments to give user a hint */

    char *arg;

    while ((arg = optparse_arg(&options)))
        fprintf(stderr, "%s\n", arg);

    if (input_specified) {
        if ((fin = fopen(fin_name, "rb")) == NULL) {
            fprintf(stderr, "Error opening input bits file: %s\n", fin_name);
            exit(-1);
        }
    }

    if (output_specified) {
        if ((fout = fopen(fout_name, "wb")) == NULL) {
            fprintf(stderr, "Error opening output modem sample file: %s\n", fout_name);
            exit(-1);
        }
    }

    /* init the modem with our (optinally) custom config */
    struct OFDM *ofdm = ofdm_create(ofdm_config);
    assert(ofdm != NULL);

    free(ofdm_config);

    /* Get a copy of the completed modem config (ofdm_create() fills in more parameters) */
    ofdm_config = ofdm_get_config_param(ofdm);

    /* set up some useful constants */

    int Nbitsperpacket = ofdm_get_bits_per_packet(ofdm);
    int Npayloadbitsperpacket = Nbitsperpacket - ofdm->nuwbits - ofdm->ntxtbits;
    int Nsamperpacket = ofdm_get_samples_per_packet(ofdm);

    /* Set up LPDC code */

    struct LDPC ldpc;
    if (ldpc_en) {
        ldpc_codes_setup(&ldpc, ofdm->codename);
        if (verbose > 1) { fprintf(stderr, "using: %s\n", ofdm->codename); }

        /* mode specific set up */
        if (!strcmp(mode,"2020")) set_data_bits_per_frame(&ldpc, 312);
        if (!strcmp(mode,"2020B")) {
            set_data_bits_per_frame(&ldpc, 156);
            ldpc.protection_mode = LDPC_PROT_2020B;
        }
        Ndatabitsperpacket = ldpc.data_bits_per_frame;

        if (verbose > 1) {
            fprintf(stderr, "LDPC codeword data bits = %d\n", ldpc.ldpc_data_bits_per_frame);
            fprintf(stderr, "LDPC codeword total bits  = %d\n", ldpc.ldpc_coded_bits_per_frame);
            fprintf(stderr, "LDPC codeword data bits used = %d\n", Ndatabitsperpacket);
            fprintf(stderr, "LDPC codeword total length in modem packet = %d\n", Npayloadbitsperpacket);
        }
    }
    else {
        Ndatabitsperpacket = Npayloadbitsperpacket;
    }

    if (verbose) {
        ofdm_set_verbose(ofdm, verbose);
        fprintf(stderr, "Ndatabitsperpacket: %d Npayloadbitsperpacket: %d Nsamperpacket: %d\n",
                Ndatabitsperpacket, Npayloadbitsperpacket, Nsamperpacket);
    }

    if (testframes) {
        if (burst_mode)
            Npackets = Nsec;                      // burst mode: treat Nsecs as Npackets/burst
        else
            Npackets = round(Nsec/ofdm->tpacket); // streaming mode
        if (verbose)
            fprintf(stderr, "Npackets: %d\n", Npackets);
    }

    if (clip_en) { ofdm->clip_en = true; }
    if (txbpf_en) { ofdm_set_tx_bpf(ofdm, 1); }
    if (dpsk) { ofdm_set_dpsk(ofdm, 1); }

    uint8_t txt_bits[ofdm->ntxtbits];
    memset(txt_bits, 0, ofdm->ntxtbits);
    char text_str[] = "cq cq cq hello world\r"; // Add text bits to match other tests
    char *ptr_text = text_str;

    short tx_varicode_bits[VARICODE_MAX_BITS];
    int nvaricode_bits = 0;
    int varicode_bit_index = 0;

    complex float tx_sams[Nsamperpacket];
    short   tx_real[Nsamperpacket];

    if (verbose > 1) ofdm_print_info(ofdm);

    for (int b=0; b<Nbursts; b++) {
        if (burst_mode) {
            fprintf(stderr, "Tx preamble\n");
            complex float tx_preamble[ofdm->samplesperframe];
            memcpy(tx_preamble, ofdm->tx_preamble, sizeof(COMP)*ofdm->samplesperframe);
            ofdm_hilbert_clipper(ofdm, tx_preamble, ofdm->samplesperframe);
            for (i = 0; i < ofdm->samplesperframe; i++) tx_real[i] = crealf(tx_preamble[i]);
            fwrite(tx_real, sizeof (short), ofdm->samplesperframe, fout);
        }
    
        /* main loop ----------------------------------------------------------------*/

        int packet = 0;
        uint8_t data_bits[Ndatabitsperpacket];
        while (fread(data_bits, sizeof (uint8_t), Ndatabitsperpacket, fin) == Ndatabitsperpacket) {

            if (ldpc_en) {
                /* fancy LDPC encoded frames ----------------------------*/

                /* optionally overwrite input data with test frame of
                   payload data bits known to demodulator */

                if (testframes) {

                    if (use_text) {
                        // Get text bits
                        int nspare = ofdm->ntxtbits;
                        int k;

                        for (k = 0; k < nspare; k++) {
                            if (nvaricode_bits) {
                                txt_bits[k] = tx_varicode_bits[varicode_bit_index++];
                                nvaricode_bits--;
                            }

                            if (nvaricode_bits == 0) {
                                /* get new char and encode */
                                char s[2];
                                s[0] = *ptr_text++;

                                if (*ptr_text == 0)
                                    ptr_text = &text_str[0];

                                nvaricode_bits = varicode_encode(tx_varicode_bits, s, VARICODE_MAX_BITS, 1, 1);
                                varicode_bit_index = 0;
                            }
                        }
                    }

                    ofdm_generate_payload_data_bits(data_bits, Ndatabitsperpacket);
                }

                ofdm_ldpc_interleave_tx(ofdm, &ldpc, tx_sams, data_bits, txt_bits);
                for (i = 0; i < Nsamperpacket; i++) tx_real[i] = crealf(tx_sams[i]);
            } else {
                /* just modulate uncoded raw bits ------------------------------------*/

                /* in uncoded mode entire payload is input data bits */
                assert(Ndatabitsperpacket == Npayloadbitsperpacket);

                if (testframes) {
                    /* build up a test frame consisting of unique word, txt bits, and psuedo-random
                       uncoded payload bits.  The psuedo-random generator is the same as Octave so
                       it can interoperate with ofdm_tx.m/ofdm_rx.m */

                    ofdm_generate_payload_data_bits(data_bits, Npayloadbitsperpacket);
                }

                /* assemble packet of bits then modulate */
                uint8_t tx_bits_char[Nbitsperpacket];
                ofdm_assemble_qpsk_modem_packet(ofdm, tx_bits_char, data_bits, txt_bits);
                int tx_bits[Nbitsperpacket];
                for (i = 0; i < Nbitsperpacket; i++) tx_bits[i] = tx_bits_char[i];
                COMP tx_sams[Nsamperpacket];
                ofdm_mod(ofdm, tx_sams, tx_bits);
                for (i = 0; i < Nsamperpacket; i++) tx_real[i] = tx_sams[i].real;
            }

            fwrite(tx_real, sizeof (short), Nsamperpacket, fout);
            packet++;

            if (testframes && (packet >= Npackets))
                break;
        }

        if (burst_mode) {
            // Post-amble 
            fprintf(stderr, "Tx postamble\n");
            complex float tx_postamble[ofdm->samplesperframe];
            memcpy(tx_postamble, ofdm->tx_postamble, sizeof(COMP)*ofdm->samplesperframe);
            ofdm_hilbert_clipper(ofdm, tx_postamble, ofdm->samplesperframe);
            for (i = 0; i < ofdm->samplesperframe; i++) tx_real[i] = crealf(tx_postamble[i]);
            fwrite(tx_real, sizeof (short), ofdm->samplesperframe, fout);
            // Interburst silence
            int samples_delay = ofdm->fs;
            short sil_short[samples_delay];
            for(int i=0; i<samples_delay; i++) sil_short[i] = 0;
            fwrite(sil_short, sizeof(short), samples_delay, fout);
        }
    }

    if (input_specified)
        fclose(fin);

    if (output_specified)
        fclose(fout);

    ofdm_destroy(ofdm);

    return 0;
}
