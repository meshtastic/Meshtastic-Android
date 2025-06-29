/*---------------------------------------------------------------------------*\

  FILE........: ofdm_demod.c
  AUTHOR......: David Rowe
  DATE CREATED: Mar 2018

  Demodulates an input file of raw file (8kHz, 16 bit shorts) OFDM modem
  samples.  Runs in uncoded or LDPC coded modes.

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
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <errno.h>

#include "codec2_ofdm.h"
#include "ofdm_internal.h"
#include "octave.h"
#include "mpdecode_core.h"
#include "ldpc_codes.h"
#include "gp_interleaver.h"
#include "interldpc.h"

#define IS_DIR_SEPARATOR(c)     ((c) == '/')

#define NFRAMES  100               /* just log the first 100 frames           */
#define NDISCARD 20                /* BER2 measure discards first 20 frames   */
#define FS       8000.0f

static const char *progname;

static const char *statemode[] = {
    "search",
    "trial",
    "synced"
};

void opt_help() {
    fprintf(stderr, "\nusage: %s [options]\n\n", progname);
    fprintf(stderr, "  Default output file format is one byte per bit hard decision\n\n");
    fprintf(stderr, "  --in          filename   Name of InputModemRawFile\n");
    fprintf(stderr, "  --out         filename   Name of OutputOneCharPerBitFile\n");
    fprintf(stderr, "  --log         filename   Octave log file for testing\n");
    fprintf(stderr, "  --mode       modeName    Predefined mode e.g. 700D|2020|datac1\n");
    fprintf(stderr, "  --nc          [17..62]   Number of Carriers (17 default, 62 max)\n");
    fprintf(stderr, "  --np                     Number of packets\n");
    fprintf(stderr, "  --ns           Nframes   One pilot every ns symbols (8 default)\n");
    fprintf(stderr, "  --tcp            Nsecs   Cyclic Prefix Duration (.002 default)\n");
    fprintf(stderr, "  --ts             Nsecs   Symbol Duration (.018 default)\n");
    fprintf(stderr, "  --bandwidth      [0|1]   Select phase est bw mode AUTO low or high (0) or LOCKED high (1) (default 0)\n");
    fprintf(stderr, "                           Must also specify --ldpc option\n");
    fprintf(stderr, "  --tx_freq         freq   Set modulation TX centre Frequency (1500.0 default)\n");
    fprintf(stderr, "  --rx_freq         freq   Set modulation RX centre Frequency (1500.0 default)\n");
    fprintf(stderr, "  --verbose      [1|2|3]   Verbose output level to stderr (default off)\n");
    fprintf(stderr, "  --testframes             Receive test frames and count errors\n");
    fprintf(stderr, "  --ldpc                   Run LDPC decoder\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "  --start_secs      secs   Number of seconds delay before we start to demod\n");
    fprintf(stderr, "  --len_secs        secs   Number of seconds to run demod\n");
    fprintf(stderr, "  --skip_secs   timeSecs   At timeSecs introduce a large timing error by skipping half a frame of samples\n");
    fprintf(stderr, "  --dpsk                   Differential PSK.\n");
    fprintf(stderr, "  --packetsperburst p      use burst mode; number of packets we expect per burst\n");
    fprintf(stderr, "\n");

    exit(-1);
}

int main(int argc, char *argv[]) {
    int i, j, opt, val;

    char *pn = argv[0] + strlen(argv[0]);

    while (pn != argv[0] && !IS_DIR_SEPARATOR(pn[-1]))
        --pn;

    progname = pn;

    /* Turn off stream buffering */

    setvbuf(stdin, NULL, _IONBF, BUFSIZ);
    setvbuf(stdout, NULL, _IONBF, BUFSIZ);

    FILE *fin = stdin;
    FILE *fout = stdout;
    FILE *foct = NULL;

    char *fin_name = NULL;
    char *fout_name = NULL;
    char *log_name = NULL;

    int logframes = NFRAMES;
    int verbose = 0;
    int phase_est_bandwidth_mode = AUTO_PHASE_EST;
    int ldpc_en = 0;
    int Ndatabitsperpacket = 0;
    int packetsperburst = 0;
    
    bool testframes = false;
    bool input_specified = false;
    bool output_specified = false;
    bool log_specified = false;
    bool log_active = false;
    bool dpsk = false;

    float time_to_sync = -1;
    float start_secs = 0.0;
    float len_secs = 0.0;
    float skip_secs = 0.0;

    /* set up the default modem config */
    struct OFDM_CONFIG *ofdm_config = (struct OFDM_CONFIG *) calloc(1, sizeof (struct OFDM_CONFIG));
    assert(ofdm_config != NULL);
    char mode[32] = "700D";
    ofdm_init_mode(mode, ofdm_config);

    struct optparse options;
    struct optparse_long longopts[] = {
        {"in", 'a', OPTPARSE_REQUIRED},
        {"out", 'b', OPTPARSE_REQUIRED},
        {"log", 'c', OPTPARSE_REQUIRED},
        {"testframes", 'd', OPTPARSE_NONE},
        {"bandwidth", 'o', OPTPARSE_REQUIRED},
        {"tx_freq", 'f', OPTPARSE_REQUIRED},
        {"rx_freq", 'g', OPTPARSE_REQUIRED},
        {"verbose", 'v', OPTPARSE_REQUIRED},
        {"ldpc", 'i', OPTPARSE_NONE},
        {"nc", 'j', OPTPARSE_REQUIRED},
        {"tcp", 'k', OPTPARSE_REQUIRED},
        {"ts", 'l', OPTPARSE_REQUIRED},
        {"ns", 'm', OPTPARSE_REQUIRED},
        {"np", 'n', OPTPARSE_REQUIRED},
        {"start_secs", 'x', OPTPARSE_REQUIRED},
        {"len_secs", 'y', OPTPARSE_REQUIRED},
        {"skip_secs", 'z', OPTPARSE_REQUIRED},
        {"dpsk", 'q', OPTPARSE_NONE},
        {"mode", 'r', OPTPARSE_REQUIRED},
        {"packetsperburst", 'e', OPTPARSE_REQUIRED},
        {0, 0, 0}
    };

    optparse_init(&options, argv);

    while ((opt = optparse_long(&options, longopts, NULL)) != -1) {
        switch (opt) {
            case '?':
                opt_help();
            case 'a':
                fin_name = options.optarg;
                input_specified = true;
                break;
            case 'b':
                fout_name = options.optarg;
                output_specified = true;
                break;
            case 'c':
                log_name = options.optarg;
                log_specified = true;
                log_active = true;
                break;
            case 'd':
                testframes = true;
                break;
            case 'e':
                packetsperburst = atoi(options.optarg);
                fprintf(stderr, "burst data mode!\n");
                break;
            case 'i':
                ldpc_en = 1;
                break;
            case 'f':
                ofdm_config->tx_centre = atof(options.optarg);
                break;
            case 'g':
                ofdm_config->rx_centre = atof(options.optarg);
                break;
            case 'j':
                val = atoi(options.optarg);

                if (val > 62 || val < 17) {
                    opt_help();
                } else {
                    ofdm_config->nc = val;
                }
                break;
            case 'k':
                ofdm_config->tcp = atof(options.optarg);
                break;
            case 'l':
                ofdm_config->ts = atof(options.optarg);
                ofdm_config->rs = 1.0f/ofdm_config->ts;
                break;
            case 'm':
                 ofdm_config->ns = atoi(options.optarg);
                break;
            case 'n':
                 ofdm_config->np = atoi(options.optarg);
                break;
            case 'o':
                phase_est_bandwidth_mode = atoi(options.optarg);
                break;
            case 'q':
                dpsk = true;
                break;
            case 'r':
                strcpy(mode, options.optarg);
                ofdm_init_mode(mode, ofdm_config);
                break;
            case 'v':
                verbose = atoi(options.optarg);
                if (verbose < 0 || verbose > 3)
                    verbose = 0;
                break;
            case 'x':
                 start_secs = atoi(options.optarg);
                 break;
            case 'y':
                 len_secs = atoi(options.optarg);
                 break;
            case 'z':
                 skip_secs = atoi(options.optarg);
                 break;

       }
    }

    /* Print remaining arguments to give user a hint */
    char *arg;

    while ((arg = optparse_arg(&options)))
        fprintf(stderr, "%s\n", arg);

    if (input_specified == true) {
        if ((fin = fopen(fin_name, "rb")) == NULL) {
            fprintf(stderr, "Error opening input modem sample file: %s\n", fin_name);
            exit(-1);
        }
    }

    if (output_specified == true) {
        if ((fout = fopen(fout_name, "wb")) == NULL) {
            fprintf(stderr, "Error opening output file: %s\n", fout_name);
            exit(-1);
        }
    }

    if (log_specified == true) {
        if ((foct = fopen(log_name, "wt")) == NULL) {
            fprintf(stderr, "Error opening Octave output file: %s\n", log_name);
            exit(-1);
        }
    }

    /* Create OFDM modem ----------------------------------------------------*/

    struct OFDM *ofdm = ofdm_create(ofdm_config);
    assert(ofdm != NULL);
    free(ofdm_config);

    ofdm_set_phase_est_bandwidth_mode(ofdm, phase_est_bandwidth_mode);
    ofdm_set_dpsk(ofdm, dpsk);
    // default to one packet per burst for burst mode
    if (packetsperburst) {
        ofdm_set_packets_per_burst(ofdm, packetsperburst);
    }
    
    /* Get a copy of the actual modem config (ofdm_create() fills in more parameters) */
    ofdm_config = ofdm_get_config_param(ofdm);

    int ofdm_bitsperframe = ofdm_get_bits_per_frame(ofdm);
    int ofdm_rowsperframe = ofdm_bitsperframe / (ofdm_config->nc * ofdm_config->bps);
    int ofdm_nuwbits = ofdm_config->nuwbits;
    int ofdm_ntxtbits = ofdm_config->txtbits;

    float phase_est_pilot_log[ofdm_rowsperframe * NFRAMES][ofdm_config->nc];
    COMP rx_np_log[ofdm_rowsperframe * ofdm_config->nc * NFRAMES];
    float rx_amp_log[ofdm_rowsperframe * ofdm_config->nc * NFRAMES];
    float foff_hz_log[NFRAMES];
    int timing_est_log[NFRAMES];

    /* zero out the log arrays in case we don't run for NFRAMES and fill them with data */

    for (i = 0; i < (ofdm_rowsperframe * NFRAMES); i++) {
        for (j = 0; j < ofdm_config->nc; j++) {
            phase_est_pilot_log[i][j] = 0.0f;
        }
    }

    for (i = 0; i < (ofdm_rowsperframe * ofdm_config->nc * NFRAMES); i++) {
        rx_np_log[i].real = 0.0f;
        rx_np_log[i].imag = 0.0f;
        rx_amp_log[i] = 0.0f;
    }

    for (i = 0; i < NFRAMES; i++) {
        foff_hz_log[i] = 0.0f;
        timing_est_log[i] = 0.0f;
    }

    /* some useful constants */

    int Nbitsperframe = ofdm_bitsperframe;
    int Nbitsperpacket = ofdm_get_bits_per_packet(ofdm);
    int Nsymsperframe = Nbitsperframe / ofdm_config->bps;
    int Nsymsperpacket = Nbitsperpacket / ofdm_config->bps;
    int Nmaxsamperframe = ofdm_get_max_samples_per_frame(ofdm);
    int Npayloadbitsperframe = ofdm_bitsperframe - ofdm_nuwbits - ofdm_ntxtbits;
    int Npayloadbitsperpacket = Nbitsperpacket - ofdm_nuwbits - ofdm_ntxtbits;
    int Npayloadsymsperframe = Npayloadbitsperframe/ofdm_config->bps;
    int Npayloadsymsperpacket = Npayloadbitsperpacket/ofdm_config->bps;

    /* Set up LPDC codes */

    struct LDPC ldpc;
    COMP payload_syms[Npayloadsymsperpacket];
    float payload_amps[Npayloadsymsperpacket];

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

    if (verbose != 0) {
        ofdm_set_verbose(ofdm, verbose);
    }

    complex float rx_syms[Nsymsperpacket]; float rx_amps[Nsymsperpacket];
    for(int i=0; i<Nsymsperpacket; i++) {
        rx_syms[i] = 0.0;
        rx_amps[i]= 0.0;
    }

    short rx_scaled[Nmaxsamperframe];
    int rx_bits[Nbitsperframe];
    uint8_t rx_bits_char[Nbitsperframe];
    uint8_t rx_uw[ofdm_nuwbits];
    short txt_bits[ofdm_ntxtbits];

    /* error counting */
    int Terrs, Tbits, Terrs2, Tbits2, Terrs_coded, Tbits_coded, frame_count, packet_count, Ndiscard;
    Terrs = Tbits = Terrs2 = Tbits2 = Terrs_coded = Tbits_coded = frame_count = packet_count = 0;
    int Nerrs_raw = 0;
    int Nerrs_coded = 0;
    int Ncoded;
    int Tper = 0;
    int iter = 0;
    int parityCheckCount = 0;
    float SNR3kdB = 0.0;
    float sum_SNR3kdB = 0.0;
    
    if (strlen(ofdm->data_mode) == 0)
        Ndiscard = NDISCARD; /* backwards compatability with 700D/2020        */
    else
        Ndiscard = 1;        /* much longer packets, so discard thresh smaller */

    float EsNo = 3.0f;

    if (verbose == 2)
        fprintf(stderr, "Warning EsNo: %f hard coded\n", EsNo);

    /* More logging */
    COMP payload_syms_log[NFRAMES][Npayloadsymsperframe];
    float payload_amps_log[NFRAMES][Npayloadsymsperframe];

    for (i = 0; i < NFRAMES; i++) {
        for (j = 0; j < Npayloadsymsperframe; j++) {
            payload_syms_log[i][j].real = 0.0f;
            payload_syms_log[i][j].imag = 0.0f;
            payload_amps_log[i][j] = 0.0f;
        }
    }

    int nin_frame = ofdm_get_nin(ofdm);

    int f = 0;
    int finish = 0;

    if (start_secs != 0.0) {
        int offset = start_secs*FS*sizeof(short);
        fseek(fin, offset, SEEK_SET);
    }

    while ((fread(rx_scaled, sizeof (short), nin_frame, fin) == nin_frame) && !finish) {

        if (verbose >= 2)
            fprintf(stderr, "%3d nin: %4d st: %-6s ", f, nin_frame,statemode[ofdm->sync_state]);
        bool log_payload_syms = false;
        Nerrs_raw = Nerrs_coded = 0;

        /* demod */

        if (ofdm->sync_state == search) {
            ofdm_sync_search_shorts(ofdm, rx_scaled, (ofdm->amp_scale / 2.0f));
        }

        if ((ofdm->sync_state == synced) || (ofdm->sync_state == trial)) {
            log_payload_syms = true;

            /* demod the latest modem frame */
            ofdm_demod_shorts(ofdm, rx_bits, rx_scaled, (ofdm->amp_scale / 2.0f));

            /* accumulate a buffer of data symbols for this packet */
            for(i=0; i<Nsymsperpacket-Nsymsperframe; i++) {
                rx_syms[i] = rx_syms[i+Nsymsperframe];
                rx_amps[i] = rx_amps[i+Nsymsperframe];
            }
            memcpy(&rx_syms[Nsymsperpacket-Nsymsperframe], ofdm->rx_np, sizeof(complex float)*Nsymsperframe);
            memcpy(&rx_amps[Nsymsperpacket-Nsymsperframe], ofdm->rx_amp, sizeof(float)*Nsymsperframe);

            /* look for UW as frames enter packet buffer, note UW may span several modem frames */
            int st_uw = Nsymsperpacket - ofdm->nuwframes*Nsymsperframe;
            ofdm_extract_uw(ofdm, &rx_syms[st_uw], &rx_amps[st_uw], rx_uw);

            if (ofdm->modem_frame == (ofdm->np-1)) {

                /* we have received enough frames to make a complete packet .... */

                /* extract payload symbols from packet */
                ofdm_disassemble_qpsk_modem_packet(ofdm, rx_syms, rx_amps, payload_syms, payload_amps, txt_bits);

                if (ldpc_en) {
                    assert((ofdm_nuwbits + ofdm_ntxtbits + Npayloadbitsperpacket) <= Nbitsperpacket);

                    /* run de-interleaver */
                    COMP payload_syms_de[Npayloadsymsperpacket];
                    float payload_amps_de[Npayloadsymsperpacket];
                    gp_deinterleave_comp(payload_syms_de, payload_syms, Npayloadsymsperpacket);
                    gp_deinterleave_float(payload_amps_de, payload_amps, Npayloadsymsperpacket);

                    float llr[Npayloadbitsperpacket];
                    uint8_t out_char[Npayloadbitsperpacket];

                    if (testframes == true) {
                        Nerrs_raw  = count_uncoded_errors(&ldpc, ofdm_config, payload_syms_de,0); Terrs += Nerrs_raw;
                        Tbits += Npayloadbitsperpacket; /* not counting errors in txt bits */
                    }

                    symbols_to_llrs(llr, payload_syms_de, payload_amps_de,
                                    EsNo, ofdm->mean_amp, Npayloadsymsperpacket);

                    assert(Ndatabitsperpacket == ldpc.data_bits_per_frame);
                    ldpc_decode_frame(&ldpc, &parityCheckCount, &iter, out_char, llr);

                    if (testframes == true) {
                        /* construct payload data bits */
                        uint8_t payload_data_bits[Ndatabitsperpacket];
                        ofdm_generate_payload_data_bits(payload_data_bits, Ndatabitsperpacket);
                        count_errors_protection_mode(ldpc.protection_mode, &Nerrs_coded, &Ncoded,
                                                     payload_data_bits, out_char, Ndatabitsperpacket);
                        Terrs_coded += Nerrs_coded;
                        Tbits_coded += Ncoded;
                        if (Nerrs_coded) Tper++;
                    }

                    fwrite(out_char, sizeof (char), Ndatabitsperpacket, fout);
                } else {
                    /* simple hard decision output of payload data bits */
                    assert(Npayloadsymsperpacket*ofdm_config->bps == Npayloadbitsperpacket);
                    for (i = 0; i < Npayloadsymsperpacket; i++) {
                        int bits[2];
                        complex float s = payload_syms[i].real + I * payload_syms[i].imag;
                        qpsk_demod(s, bits);
                        rx_bits_char[ofdm_config->bps * i] = bits[1];
                        rx_bits_char[ofdm_config->bps * i + 1] = bits[0];
                    }

                    fwrite(rx_bits_char, sizeof (uint8_t), Npayloadbitsperpacket, fout);
                }

                /* optional error counting on uncoded data in non-LDPC testframe mode */

                if ((testframes == true) && (ldpc_en == 0)) {
                    /* build up a test frame consisting of unique word, txt bits, and psuedo-random
                       uncoded payload bits.  The psuedo-random generator is the same as Octave so
                       it can interoperate with ofdm_tx.m/ofdm_rx.m */

                    uint8_t payload_bits[Npayloadbitsperpacket];
                    uint8_t txt_bits[ofdm_ntxtbits]; memset(txt_bits, 0, ofdm_ntxtbits);
                    uint8_t tx_bits[Nbitsperpacket];
                    ofdm_generate_payload_data_bits(payload_bits, Npayloadbitsperpacket);
                    ofdm_assemble_qpsk_modem_packet(ofdm, tx_bits, payload_bits, txt_bits);

                    /* count errors across UW, payload, txt bits */
                    int rx_bits[Nbitsperpacket];
                    int dibit[2];
                    assert(ofdm->bps == 2);  /* this only works for QPSK at this stage */
                    for(int s=0; s<Nsymsperpacket; s++) {
                        qpsk_demod(rx_syms[s], dibit);
                        rx_bits[2*s  ] = dibit[1];
                        rx_bits[2*s+1] = dibit[0];
                    }
                    for (Nerrs_raw=0, i = 0; i < Nbitsperpacket; i++) if (tx_bits[i] != rx_bits[i]) Nerrs_raw++;
                    Terrs += Nerrs_raw;
                    Tbits += Nbitsperpacket;

                    if (packet_count >= Ndiscard) {
                        Terrs2 += Nerrs_raw;
                        Tbits2 += Nbitsperpacket;
                    }
                }
                packet_count++;
                
                float EsNodB = ofdm_esno_est_calc(rx_syms, Npayloadsymsperpacket);
                SNR3kdB = ofdm_snr_from_esno(ofdm, EsNodB); sum_SNR3kdB += SNR3kdB;
            } /* complete packet */

            frame_count++;
        }

        /* per-frame modem processing */

        nin_frame = ofdm_get_nin(ofdm);
        ofdm_sync_state_machine(ofdm, rx_uw);

        /* act on any events returned by state machine */

        if (!strcmp(ofdm->data_mode, "streaming") && ofdm->sync_start ) {
            Terrs = Tbits = Terrs2 = Tbits2 = Terrs_coded = Tbits_coded = frame_count = packet_count = 0;
            Nerrs_raw = 0;
            Nerrs_coded = 0;
        }

        if (verbose >= 2) {
           if (ofdm->last_sync_state != search) {
                if ((ofdm->modem_frame == 0) && (ofdm->last_sync_state != trial)) {
                    /* weve just received a complete packet, so print all stats */
                    fprintf(stderr, "euw: %2d %1d mf: %2d f: %5.1f pbw: %d eraw: %3d ecdd: %3d iter: %3d pcc: %3d snr: %5.2f\n",
                        ofdm->uw_errors,
                        ofdm->sync_counter,
                        ofdm->modem_frame,
                        ofdm->foff_est_hz,
                        ofdm->phase_est_bandwidth,
                        Nerrs_raw, Nerrs_coded, iter, parityCheckCount, SNR3kdB);
                } else {
                    /* weve just received a modem frame, abbreviated stats */
                    fprintf(stderr, "euw: %2d %1d mf: %2d f: %5.1f pbw: %d\n",
                        ofdm->uw_errors,
                        ofdm->sync_counter,
                        ofdm->modem_frame,
                        ofdm->foff_est_hz,
                        ofdm->phase_est_bandwidth);                    
                }
            }
                        
            /* detect a successful sync for time to sync tests */
            if ((time_to_sync < 0) && ((ofdm->sync_state == synced) || (ofdm->sync_state == trial)))
                if ((parityCheckCount > 80) && (iter != 100))
                    time_to_sync = (float)(f+1)*ofdm_get_samples_per_frame(ofdm)/FS;

        }

        /* optional logging of states */

        if (log_active == true) {
            /* note corrected phase (rx no phase) is one big linear array for frame */

            for (i = 0; i < ofdm_rowsperframe * ofdm_config->nc; i++) {
                rx_np_log[ofdm_rowsperframe * ofdm_config->nc * f + i].real = crealf(ofdm->rx_np[i]);
                rx_np_log[ofdm_rowsperframe * ofdm_config->nc * f + i].imag = cimagf(ofdm->rx_np[i]);
            }

            /* note phase/amp ests the same for each col, but check them all anyway */

            for (i = 0; i < ofdm_rowsperframe; i++) {
                for (j = 0; j < ofdm_config->nc; j++) {
                    phase_est_pilot_log[ofdm_rowsperframe * f + i][j] = ofdm->aphase_est_pilot_log[ofdm_config->nc * i + j];
                    rx_amp_log[ofdm_rowsperframe * ofdm_config->nc * f + ofdm_config->nc * i + j] = ofdm->rx_amp[ofdm_config->nc * i + j];
                }
            }

            foff_hz_log[f] = ofdm->foff_est_hz;
            timing_est_log[f] = ofdm->timing_est + 1; /* offset by 1 to match Octave */
            if (log_payload_syms == true) {
                for (i = 0; i < Npayloadsymsperpacket; i++) {
                    payload_syms_log[f][i].real = payload_syms[i].real;
                    payload_syms_log[f][i].imag = payload_syms[i].imag;
                    payload_amps_log[f][i] = payload_amps[i];
                }
            }

            if (f == (logframes - 1))
                log_active = false;
        }

        if (len_secs != 0.0) {
            float secs = (float)f*ofdm_get_samples_per_frame(ofdm)/FS;
            if (secs >= len_secs) finish = 1;
        }

        if (skip_secs != 0.0) {
            /* big nasty timing error */
            float secs = (float)f*ofdm_get_samples_per_frame(ofdm)/FS;
            if (secs >= skip_secs) {
                assert(fread(rx_scaled, sizeof (short), nin_frame/2, fin) == nin_frame/2);
                fprintf(stderr,"  Skip!  Just introduced a nasty big timing slip\n");
                skip_secs = 0.0; /* make sure we just introduce one error */
            }
        }

        f++;
    }

    ofdm_destroy(ofdm);

    if (input_specified == true)
        fclose(fin);

    if (output_specified == true)
        fclose(fout);

    /* optionally dump Octave files */

    if (log_specified == true) {
        octave_save_float(foct, "phase_est_pilot_log_c", (float*) phase_est_pilot_log, ofdm_rowsperframe*NFRAMES, ofdm_config->nc, ofdm_config->nc);
        octave_save_complex(foct, "rx_np_log_c", (COMP*) rx_np_log, 1, ofdm_rowsperframe * ofdm_config->nc*NFRAMES, ofdm_rowsperframe * ofdm_config->nc * NFRAMES);
        octave_save_float(foct, "rx_amp_log_c", (float*) rx_amp_log, 1, ofdm_rowsperframe * ofdm_config->nc*NFRAMES, ofdm_rowsperframe * ofdm_config->nc * NFRAMES);
        octave_save_float(foct, "foff_hz_log_c", foff_hz_log, NFRAMES, 1, 1);
        octave_save_int(foct, "timing_est_log_c", timing_est_log, NFRAMES, 1);
        octave_save_complex(foct, "payload_syms_log_c", (COMP*) payload_syms_log, NFRAMES, Npayloadsymsperpacket, Npayloadsymsperpacket);
        octave_save_float(foct, "payload_amps_log_c", (float*) payload_amps_log, NFRAMES, Npayloadsymsperpacket, Npayloadsymsperpacket);

        fclose(foct);
    }

    if ((strlen(ofdm->data_mode) == 0) && (verbose == 2))
        fprintf(stderr, "time_to_sync: %f\n", time_to_sync);

    int ret = 0;
    if (testframes == true) {
        float uncoded_ber = (float) Terrs / Tbits;
        float coded_ber = 0.0;

        if (verbose != 0) {
            fprintf(stderr, "BER......: %5.4f Tbits: %5d Terrs: %5d Tpackets: %5d SNR3kdB: %5.2f\n", 
                uncoded_ber, Tbits, Terrs, packet_count, sum_SNR3kdB/packet_count);

            if ((ldpc_en == 0) && (packet_count > Ndiscard)) {
                fprintf(stderr, "BER2.....: %5.4f Tbits: %5d Terrs: %5d\n", (float) Terrs2 / Tbits2, Tbits2, Terrs2);
            }
        }

        /* set return code for Ctest, 1 for fail */

        if (ldpc_en) {
            coded_ber = (float) Terrs_coded / Tbits_coded;

            if (verbose != 0) {
                fprintf(stderr, "Coded BER: %5.4f Tbits: %5d Terrs: %5d\n", coded_ber, Tbits_coded, Terrs_coded);
                fprintf(stderr, "Coded PER: %5.4f Tpkts: %5d Tpers: %5d Thruput: %5d\n", 
                        (float)Tper/packet_count, packet_count, Tper, packet_count - Tper);
              }
            if ((Tbits_coded == 0) || (coded_ber >= 0.01f))
                ret = 1;
        }

        if ((Tbits == 0) || (uncoded_ber >= 0.1f))
            ret = 1;
    }
    
    if (strlen(ofdm->data_mode)) {
        fprintf(stderr, "Npre.....: %6d Npost: %5d uw_fails: %2d\n", ofdm->pre, ofdm->post, ofdm->uw_fails);
    }
    
    return ret;
}
