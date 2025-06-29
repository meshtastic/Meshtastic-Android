/*---------------------------------------------------------------------------*\

  FILE........: freedv_data_raw_tx.c
  AUTHOR......: David Rowe
  DATE CREATED: May 2020

  Demonstrates transmitting frames of raw data bytes (instead of
  compressed speech) using the FreeDV API.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2020 David Rowe

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

#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <stdint.h>
#include <getopt.h>

#include "freedv_api.h"
#include "fsk.h"
#include "ofdm_internal.h"

size_t send_preamble(struct freedv *freedv, FILE *fout, int use_complex, size_t n_mod_out);
size_t send_modulated_data(struct freedv *freedv, FILE *fout, int use_complex, size_t n_mod_out, uint8_t bytes_in[]);
size_t send_postamble(struct freedv *freedv, FILE *fout, int use_complex, size_t n_mod_out);
size_t send_silence(FILE *fout, size_t shorts_per_sample, size_t samples_delay);
void comp_to_short(short mod_out_short[], COMP mod_out_comp[], int n_mod_out);

int main(int argc, char *argv[]) {
    FILE                     *fin, *fout;
    struct freedv_advanced    adv = {0,2,100,8000,1000,200, "H_256_512_4"};
    struct freedv            *freedv;
    int                       mode;
    int                       use_clip, use_txbpf, testframes, Ntestframes = 0;
    int                       use_complex = 0;
    float                     amp = FSK_SCALE;
    size_t                    shorts_per_sample = 1;
    int                       Nbursts = 1, sequence_numbers = 0;
    int                       inter_burst_delay_ms = 0;
    int                       postdelay_ms = 0;
    uint8_t                   source_byte = 0;

    if (argc < 4) {
    helpmsg:
        fprintf(stderr, "\nusage: %s [options] FSK_LDPC|DATAC0|DATAC1|DATAC3 InputBinaryDataFile OutputModemRawFile\n"
               "\n"
               "  --testframes      T         send a total of T test frames (T should equal B*N)\n"
               "  --bursts          B         send B bursts of N testframes (default 1)\n"
               "  --framesperburst  N         burst mode, N frames per burst (default 1)\n"
               "  --delay           ms        testframe inter-burst delay in ms\n"
               "  --postdelay       ms        additional delay at end of run in ms\n"
               "  -c                          complex signed 16 bit output format (default real)\n"
               "  --clip            0|1       clipping for reduced PAPR\n"
               "  --txbpf           0|1       bandpass filter\n"
               "  --seq                       send packet sequence numbers (breaks testframe BER counting)\n"
               "  --source          Byte      insert a (non-zero) source address att byte[0]\n"
               "  --complexout                complex sample output (default real)\n"
               "  --quiet\n"
               "\n"
               "For FSK_LDPC only:\n\n"
               "  -a      amp     maximum amplitude of FSK signal\n"
               "  -m      2|4     number of FSK tones\n"
               "  --Fs    FreqHz  sample rate (default 8000)\n"
               "  --Rs    FreqHz  symbol rate (default 100)\n"
               "  --tone1 FreqHz  freq of first tone (default 1000)\n"
               "  --shift FreqHz  shift between tones (default 200)\n\n"
               , argv[0]);
        fprintf(stderr, "example: $ %s --testframes 6 --bursts 3 --framesperburst 2 datac0 /dev/zero samples.s16\n", argv[0]);
        fprintf(stderr, "example: $ %s  -c --testframes 10 FSK_LDPC /dev/zero samples.iq16\n\n", argv[0]);
        exit(1);
    }

    use_clip = -1; use_txbpf = -1; testframes = 0;
    int framesperburst = 1;
    int quiet = 0;
    
    int o = 0;
    int opt_idx = 0;
    while( o != -1 ){
        static struct option long_opts[] = {
            {"testframes",     required_argument,  0, 't'},
            {"help",           no_argument,        0, 'h'},
            {"txbpf",          required_argument,  0, 'b'},
            {"clip",           required_argument,  0, 'l'},
            {"Fs",             required_argument,  0, 'f'},
            {"Rs",             required_argument,  0, 'r'},
            {"tone1",          required_argument,  0, '1'},
            {"shift",          required_argument,  0, 's'},
            {"bursts",         required_argument,  0, 'e'},
            {"framesperburst", required_argument,  0, 'g'},
            {"delay",          required_argument,  0, 'j'},
            {"postdelay",      required_argument,  0, 'k'},
            {"seq",            no_argument,        0, 'd'},
            {"source",         required_argument,  0, 'i'},
            {"amp",            required_argument,  0, 'a'},
            {"quiet",          no_argument,        0, 'q'},
            {"complexout",     no_argument,        0, 'c'},
            {0, 0, 0, 0}
        };

        o = getopt_long(argc,argv,"a:cdt:hb:l:e:f:g:r:1:s:m:qi:",long_opts,&opt_idx);

        switch(o) {
        case 'a':
            amp = atof(optarg)/2.0;
            break;
        case 'b':
            use_txbpf = atoi(optarg);
            break;
        case 'c':
            use_complex = 1;
            shorts_per_sample = 2;
            break;
        case 'd':
            sequence_numbers = 1;
            break;
        case 'i':
            source_byte = strtol(optarg, NULL, 0);
            fprintf(stderr,"source byte: 0x%02x\n", source_byte);
            break;
        case 'e':
            Nbursts = atoi(optarg);
            break;
        case 'g':
            framesperburst = atoi(optarg);
            break;
        case 'j':
            inter_burst_delay_ms = atoi(optarg);
            break;
        case 'k':
            postdelay_ms = atoi(optarg);
            break;
        case 't':
            testframes = 1;
            Ntestframes = atoi(optarg);
            break;
        case 'l':
            use_clip = atoi(optarg);
            break;
        case 'm':
            adv.M = atoi(optarg);
            break;
        case 'q':
            quiet = 1;
            break;
        case 'f':
            adv.Fs = atoi(optarg);
            break;
        case 'r':
            adv.Rs = atoi(optarg);
            break;
        case '1':
            adv.first_tone = atoi(optarg);
            break;
        case 's':
            adv.tone_spacing = atoi(optarg);
            break;
        case 'h':
        case '?':
            goto helpmsg;
            break;
        }
    }
    int dx = optind;

    if( (argc - dx) < 3) {
        fprintf(stderr, "too few arguments.\n");
        goto helpmsg;
    }

    mode = -1;
    if (!strcmp(argv[dx],"FSK_LDPC") || !strcmp(argv[dx],"fsk_ldpc")) mode = FREEDV_MODE_FSK_LDPC;
    if (!strcmp(argv[dx],"DATAC0") || !strcmp(argv[dx],"datac0")) mode = FREEDV_MODE_DATAC0;
    if (!strcmp(argv[dx],"DATAC1") || !strcmp(argv[dx],"datac1")) mode = FREEDV_MODE_DATAC1;
    if (!strcmp(argv[dx],"DATAC3") || !strcmp(argv[dx],"datac3")) mode = FREEDV_MODE_DATAC3;
    if (mode == -1) {
      fprintf(stderr, "Error: in mode: %s", argv[dx]);
      exit(1);
    }

    if (strcmp(argv[dx+1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[dx+1],"rb")) == NULL ) {
        fprintf(stderr, "Error opening input file of bytes: %s: %s.\n", argv[dx+1], strerror(errno));
        exit(1);
    }

    if (strcmp(argv[dx+2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[dx+2],"wb")) == NULL ) {
        fprintf(stderr, "Error opening output modem sample file: %s: %s.\n", argv[dx+2], strerror(errno));
        exit(1);
    }

    if (mode != FREEDV_MODE_FSK_LDPC)
        freedv = freedv_open(mode);
    else
        freedv = freedv_open_advanced(mode, &adv);

    assert(freedv != NULL);

    /* these are optional ------------------ */
    if (use_clip != -1) freedv_set_clip(freedv, use_clip);
    if (use_txbpf != -1) freedv_set_tx_bpf(freedv, use_txbpf);
    freedv_set_tx_amp(freedv, amp);

    /* Data modes have a multiple of 8 payload bits/frame */
    int bytes_per_modem_frame = freedv_get_bits_per_modem_frame(freedv)/8;
    int payload_bytes_per_modem_frame = bytes_per_modem_frame;
    payload_bytes_per_modem_frame -= 2; /* 16 bits used for the CRC */
    if (!quiet) fprintf(stderr, "payload bytes_per_modem_frame: %d ", payload_bytes_per_modem_frame);
    assert((freedv_get_bits_per_modem_frame(freedv) % 8) == 0);
    int     n_mod_out = freedv_get_n_tx_modem_samples(freedv);
    uint8_t bytes_in[bytes_per_modem_frame];

    if (mode == FREEDV_MODE_FSK_LDPC) {
        if (!quiet) fprintf(stderr, "Frequency: Fs: %4.1f kHz Rs: %4.1f kHz Tone1: %4.1f kHz Shift: %4.1f kHz M: %d \n",
                        (float)adv.Fs/1E3, (float)adv.Rs/1E3, (float)adv.first_tone/1E3, (float)adv.tone_spacing/1E3, adv.M);

        if (adv.tone_spacing < adv.Rs) {
            fprintf(stderr, "Need shift: %d > Rs: %d\n", adv.tone_spacing, adv.Rs);
            exit(1);
        }
    }

    /* a few sanity checks */
    if (testframes) {
        if (Ntestframes != framesperburst*Nbursts) {
            if (!quiet) fprintf(stderr, "Adjusting testframes to equal framesperburst*bursts\n");
            Ntestframes = framesperburst*Nbursts;
        }
    } else {
        if (framesperburst != 1) {
            fprintf(stderr, "Only one frame per burst currently supported in stdin mode\n");
            exit(1);
        }
    }

    int frames = 0;
    size_t on_samples = 0;
    size_t off_samples = 0;

    /* --------- Test Frame Mode --------------------------------------------------*/
    
    if (testframes) {

        /* generate a fixed test frame */
        uint8_t testframe_bytes[bytes_per_modem_frame];
        memset(testframe_bytes, 0, bytes_per_modem_frame);
        int bits_per_frame = freedv_get_bits_per_modem_frame(freedv);
        uint8_t testframe_bits[bits_per_frame];
        ofdm_generate_payload_data_bits(testframe_bits, bits_per_frame);
        freedv_pack(testframe_bytes, testframe_bits, bits_per_frame);
        if (!quiet) fprintf(stderr, "\n");

        for(int b=0; b<Nbursts; b++) {
            on_samples += send_preamble(freedv, fout, use_complex, n_mod_out);

            for(int fpb=0; fpb<framesperburst; fpb++) {
                memcpy(bytes_in, testframe_bytes, bytes_per_modem_frame);
                if (source_byte) bytes_in[0] = source_byte;
                if (sequence_numbers) bytes_in[1] = (frames+1) & 0xff;

                /* The raw data modes requires a CRC in the last two bytes */
                uint16_t crc16 = freedv_gen_crc16(bytes_in, payload_bytes_per_modem_frame);
                bytes_in[bytes_per_modem_frame-2] = crc16 >> 8;
                bytes_in[bytes_per_modem_frame-1] = crc16 & 0xff;

                on_samples += send_modulated_data(freedv, fout, use_complex, n_mod_out, bytes_in);
            
                /* if using pipes we don't want the usual buffering to occur */
                if (fout == stdout) fflush(stdout);

                frames++;
            }
        
            on_samples += send_postamble(freedv, fout, use_complex, n_mod_out);

            int samples_delay = 0;
            if (inter_burst_delay_ms) {
                /* user defined inter-burst delay */
                samples_delay = FREEDV_FS_8000*inter_burst_delay_ms/1000;
            }
            else {                
                /* just enough silence at the end of burst to allow demod to complete processing */
                samples_delay = 2*freedv_get_n_nom_modem_samples(freedv);
            }
            on_samples += send_silence(fout, shorts_per_sample, samples_delay);
        }

    } else {

        /* --------- modulate data from stdin mode --------------------------------------------------*/

        while (fread(bytes_in, sizeof(uint8_t), payload_bytes_per_modem_frame, fin) == payload_bytes_per_modem_frame) {
            on_samples += send_preamble(freedv, fout, use_complex, n_mod_out);

            if (source_byte) bytes_in[0] = source_byte;
            if (sequence_numbers) bytes_in[1] = (frames+1) & 0xff;

            /* The raw data modes requires a CRC in the last two bytes */
            uint16_t crc16 = freedv_gen_crc16(bytes_in, payload_bytes_per_modem_frame);
            bytes_in[bytes_per_modem_frame-2] = crc16 >> 8;
            bytes_in[bytes_per_modem_frame-1] = crc16 & 0xff;

            on_samples += send_modulated_data(freedv, fout, use_complex, n_mod_out, bytes_in);
            
            /* if using pipes we don't want the usual buffering to occur */
            if (fout == stdout) fflush(stdout);

            on_samples += send_postamble(freedv, fout, use_complex, n_mod_out);

            int samples_delay = 0;
            if (inter_burst_delay_ms) {
                /* user defined inter-burst delay */
                samples_delay = FREEDV_FS_8000*inter_burst_delay_ms/1000;
            }
            else {                
                /* just enough silence at the end of burst to allow demod to complete processing */
                samples_delay = 2*freedv_get_n_nom_modem_samples(freedv);
            }
            off_samples += send_silence(fout, shorts_per_sample, samples_delay);
            frames++;
        }
    }

    /* optional silence at the end of run */
    if (postdelay_ms) {
        int samples_delay = FREEDV_FS_8000*postdelay_ms/1000;
        if (!quiet) fprintf(stderr, "postdelay: %d %d\n", postdelay_ms, samples_delay);
        off_samples += send_silence(fout, shorts_per_sample, samples_delay);
    }
    
    /* SNR offset to use in channel simulator to account for on/off time of burst signal */
    float mark_space_ratio = (float)on_samples/(on_samples+off_samples);
    float mark_space_SNR_offset = 10*log10(mark_space_ratio);
    if (!quiet) fprintf(stderr, "marks:space: %3.2f SNR offset: %5.2f\n", mark_space_ratio, mark_space_SNR_offset);
    
    freedv_close(freedv);
    fclose(fin);
    fclose(fout);

    return 0;
}


size_t send_preamble(struct freedv *freedv, FILE *fout, int use_complex, size_t n_mod_out) {
    short mod_out_short[2*n_mod_out];
    int shorts_per_sample = 1;
    int n_preamble = 0;
    
    if (use_complex == 0) {
        n_preamble = freedv_rawdatapreambletx(freedv, mod_out_short);
    } else {
        COMP mod_out_comp[n_mod_out];
        n_preamble = freedv_rawdatapreamblecomptx(freedv, mod_out_comp);
        comp_to_short(mod_out_short, mod_out_comp, n_preamble);
        shorts_per_sample = 2;
    }
    assert(n_preamble == freedv_get_n_tx_preamble_modem_samples(freedv));
    assert(n_preamble <= n_mod_out);
    fwrite(mod_out_short, sizeof(short), shorts_per_sample*n_preamble, fout);
    return n_preamble;
}


size_t send_modulated_data(struct freedv *freedv, FILE *fout, int use_complex, size_t n_mod_out, uint8_t bytes_in[]) {
    short mod_out_short[2*n_mod_out];
    int shorts_per_sample = 1;
    
    if (use_complex == 0) {
        freedv_rawdatatx(freedv, mod_out_short, bytes_in);
    } else {
        COMP mod_out_comp[n_mod_out];
        freedv_rawdatacomptx(freedv, mod_out_comp, bytes_in);
        comp_to_short(mod_out_short, mod_out_comp, n_mod_out);
        shorts_per_sample = 2;
    }
    fwrite(mod_out_short, sizeof(short), shorts_per_sample*n_mod_out, fout);
    return n_mod_out;
}


size_t send_postamble(struct freedv *freedv, FILE *fout, int use_complex, size_t n_mod_out) {
    short mod_out_short[2*n_mod_out];
    int shorts_per_sample = 1;
    int n_postamble = 0;
    
    if (use_complex == 0) {
        n_postamble = freedv_rawdatapostambletx(freedv, mod_out_short);
    } else {
        COMP mod_out_comp[n_mod_out];
        n_postamble = freedv_rawdatapostamblecomptx(freedv, mod_out_comp);
        comp_to_short(mod_out_short, mod_out_comp, n_postamble);
        shorts_per_sample = 2;
    }
    assert(n_postamble == freedv_get_n_tx_postamble_modem_samples(freedv));
    assert(n_postamble <= n_mod_out);
    fwrite(mod_out_short, sizeof(short), shorts_per_sample*n_postamble, fout);
    return n_postamble;
}


size_t send_silence(FILE *fout, size_t shorts_per_sample, size_t samples_delay) {
    size_t n = shorts_per_sample*samples_delay;
    short sil_short[n];
    for(int i=0; i<n; i++) sil_short[i] = 0;
    fwrite(sil_short, sizeof(short), n, fout);
    return samples_delay;
}


void comp_to_short(short mod_out_short[], COMP mod_out_comp[], int n_mod_out) {
    for(int i=0; i<n_mod_out; i++) {
        mod_out_short[2*i] = (short)(mod_out_comp[i].real);
        mod_out_short[2*i+1] = (short)(mod_out_comp[i].imag);
    }
}

