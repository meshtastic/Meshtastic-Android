/*---------------------------------------------------------------------------*\

  FILE........: freedv_1600.c
  AUTHOR......: David Rowe
  DATE CREATED: May 2020

  Functions that implement the FreeDV 1600 mode.

\*---------------------------------------------------------------------------*/

#include <assert.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

#include "codec2_fdmdv.h"
#include "golay23.h"
#include "codec2.h"
#include "varicode.h"
#include "freedv_api.h"
#include "freedv_api_internal.h"
#include "comp_prim.h"
#include "debug_alloc.h"
#include "fdmdv_internal.h"

void freedv_1600_open(struct freedv *f) {
    f->snr_squelch_thresh = 2.0;
    f->squelch_en = 1;
    f->tx_sync_bit = 0;
    int Nc = 16;
    f->fdmdv = fdmdv_create(Nc);
    assert(f->fdmdv != NULL);
    golay23_init();
    f->nin = f->nin_prev = FDMDV_NOM_SAMPLES_PER_FRAME;
    f->n_nom_modem_samples = 2*FDMDV_NOM_SAMPLES_PER_FRAME;
    f->n_nat_modem_samples = f->n_nom_modem_samples;
    f->n_max_modem_samples = FDMDV_NOM_SAMPLES_PER_FRAME+FDMDV_MAX_SAMPLES_PER_FRAME;
    f->modem_sample_rate = FREEDV_FS_8000;
    int nbit = fdmdv_bits_per_frame(f->fdmdv);
    f->fdmdv_bits = (int*)MALLOC(nbit*sizeof(int));
    assert(f->fdmdv_bits != NULL);
    nbit = 2*fdmdv_bits_per_frame(f->fdmdv);
    f->fdmdv_tx_bits = (int*)CALLOC(1, nbit*sizeof(int));
    f->fdmdv_rx_bits = (int*)CALLOC(1, nbit*sizeof(int));
    assert(f->fdmdv_tx_bits != NULL); assert(f->fdmdv_rx_bits != NULL);
    f->evenframe = 0;
    f->sz_error_pattern = fdmdv_error_pattern_size(f->fdmdv);

    f->speech_sample_rate = FREEDV_FS_8000;
    f->codec2 = codec2_create(CODEC2_MODE_1300); assert(f->codec2 != NULL);
    f->n_speech_samples = codec2_samples_per_frame(f->codec2);

    f->bits_per_modem_frame = fdmdv_bits_per_frame(f->fdmdv);
    f->bits_per_codec_frame = codec2_bits_per_frame(f->codec2);
    f->n_codec_frames = 1;
    f->tx_payload_bits = MALLOC(f->bits_per_codec_frame); assert(f->tx_payload_bits != NULL);
    f->rx_payload_bits = MALLOC(f->bits_per_codec_frame); assert(f->rx_payload_bits != NULL);
}


void freedv_comptx_fdmdv_1600(struct freedv *f, COMP mod_out[]) {
    int    i, j;
    int    data, codeword1, data_flag_index;
    COMP   tx_fdm[f->n_nat_modem_samples];

    // spare bit in frame that codec defines.  Use this 1
    // bit/frame to send txt messages

    data_flag_index = codec2_get_spare_bit_index(f->codec2);

    if (f->nvaricode_bits) {
        f->tx_payload_bits[data_flag_index] = f->tx_varicode_bits[f->varicode_bit_index++];
        f->nvaricode_bits--;
    }

    if (f->nvaricode_bits == 0) {
        /* get new char and encode */
        char s[2];
        if (f->freedv_get_next_tx_char != NULL) {
            s[0] = (*f->freedv_get_next_tx_char)(f->callback_state);
            f->nvaricode_bits = varicode_encode(f->tx_varicode_bits, s, VARICODE_MAX_BITS, 1, f->varicode_dec_states.code_num);
            f->varicode_bit_index = 0;
        }
    }

    /* Protect first 12 out of first 16 excitation bits with (23,12) Golay Code:

       0,1,2,3: v[0]..v[3]
       4,5,6,7: MSB of pitch
       11,12,13,14: MSB of energy

    */

    data = 0;
    for(i=0; i<8; i++) {
        data <<= 1;
        data |= f->tx_payload_bits[i];
    }
    for(i=11; i<15; i++) {
        data <<= 1;
        data |= f->tx_payload_bits[i];
    }
    codeword1 = golay23_encode(data);

    /* now pack output frame with parity bits at end to make them
       as far apart as possible from the data they protect.  Parity
       bits are LSB of the Golay codeword */

    for(i=0; i<f->bits_per_codec_frame; i++)
        f->fdmdv_tx_bits[i] = f->tx_payload_bits[i];
    for(j=0; i<f->bits_per_codec_frame+11; i++,j++) {
        f->fdmdv_tx_bits[i] = (codeword1 >> (10-j)) & 0x1;
    }
    f->fdmdv_tx_bits[i] = 0; /* spare bit */

    /* optionally overwrite with test frames */

    if (f->test_frames) {
        fdmdv_get_test_bits(f->fdmdv, f->fdmdv_tx_bits);
        fdmdv_get_test_bits(f->fdmdv, &f->fdmdv_tx_bits[f->bits_per_modem_frame]);
    }

    /* modulate even and odd frames */

    fdmdv_mod(f->fdmdv, tx_fdm, f->fdmdv_tx_bits, &f->tx_sync_bit);
    assert(f->tx_sync_bit == 1);

    fdmdv_mod(f->fdmdv, &tx_fdm[FDMDV_NOM_SAMPLES_PER_FRAME], &f->fdmdv_tx_bits[f->bits_per_modem_frame], &f->tx_sync_bit);
    assert(f->tx_sync_bit == 0);

    assert(2*FDMDV_NOM_SAMPLES_PER_FRAME == f->n_nom_modem_samples);

    for(i=0; i<f->n_nom_modem_samples; i++)
        mod_out[i] = fcmult(FDMDV_SCALE, tx_fdm[i]);
}


int freedv_comprx_fdmdv_1600(struct freedv *f, COMP demod_in[]) {
    int                 bits_per_fdmdv_frame;
    int                 i, j;
    int                 recd_codeword, codeword1, data_flag_index, n_ascii;
    short               abit[1];
    char                ascii_out;
    int                 reliable_sync_bit;
    int                 rx_status = 0;

    COMP ademod_in[f->nin];
    for(i=0; i<f->nin; i++)
        ademod_in[i] = fcmult(1.0/FDMDV_SCALE, demod_in[i]);

    bits_per_fdmdv_frame  = fdmdv_bits_per_frame(f->fdmdv);

    fdmdv_demod(f->fdmdv, f->fdmdv_bits, &reliable_sync_bit, ademod_in, &f->nin);
    fdmdv_get_demod_stats(f->fdmdv, &f->stats);
    f->sync = f->fdmdv->sync;
    f->snr_est = f->stats.snr_est;

    if (reliable_sync_bit == 1) {
        f->evenframe = 1;
    }

    if (f->sync) {
        rx_status = FREEDV_RX_SYNC;

        if (f->evenframe == 0) {
            memcpy(f->fdmdv_rx_bits, f->fdmdv_bits, bits_per_fdmdv_frame*sizeof(int));
        }
        else {
            memcpy(&f->fdmdv_rx_bits[bits_per_fdmdv_frame], f->fdmdv_bits, bits_per_fdmdv_frame*sizeof(int));

            if (f->test_frames == 0) {
                recd_codeword = 0;
                for(i=0; i<8; i++) {
                    recd_codeword <<= 1;
                    recd_codeword |= (f->fdmdv_rx_bits[i] & 0x1);
                }
                for(i=11; i<15; i++) {
                    recd_codeword <<= 1;
                    recd_codeword |= (f->fdmdv_rx_bits[i] & 0x1);
                }
                for(i=f->bits_per_codec_frame; i<f->bits_per_codec_frame+11; i++) {
                    recd_codeword <<= 1;
                    recd_codeword |= (f->fdmdv_rx_bits[i] & 0x1);
                }
                codeword1 = golay23_decode(recd_codeword);
                f->total_bit_errors += golay23_count_errors(recd_codeword, codeword1);
                f->total_bits       += 23;

                for(i=0; i<f->bits_per_codec_frame; i++)
                    f->rx_payload_bits[i] = f->fdmdv_rx_bits[i];

                for(i=0; i<8; i++) {
                    f->rx_payload_bits[i] = (codeword1 >> (22-i)) & 0x1;
                }
                for(i=8,j=11; i<12; i++,j++) {
                    f->rx_payload_bits[j] = (codeword1 >> (22-i)) & 0x1;
                }

                // extract txt msg data bit ------------------------------------------------------------

                data_flag_index = codec2_get_spare_bit_index(f->codec2);
                abit[0] = f->rx_payload_bits[data_flag_index];

                n_ascii = varicode_decode(&f->varicode_dec_states, &ascii_out, abit, 1, 1);
                if (n_ascii && (f->freedv_put_next_rx_char != NULL)) {
                    (*f->freedv_put_next_rx_char)(f->callback_state, ascii_out);
                }

                // reconstruct missing bit we steal for data bit and decode
                codec2_rebuild_spare_bit(f->codec2, (char*)f->rx_payload_bits);

                rx_status |= FREEDV_RX_BITS;
            }
            else {
                int   test_frame_sync, bit_errors, ntest_bits, k;
                short error_pattern[fdmdv_error_pattern_size(f->fdmdv)];

                for(k=0; k<2; k++) {
                    /* test frames, so lets sync up to the test frames and count any errors */

                    fdmdv_put_test_bits(f->fdmdv, &test_frame_sync, error_pattern, &bit_errors, &ntest_bits, &f->fdmdv_rx_bits[k*bits_per_fdmdv_frame]);

                    if (test_frame_sync == 1) {
                        f->test_frame_sync_state = 1;
                        f->test_frame_count = 0;
                    }

                    if (f->test_frame_sync_state) {
                        if (f->test_frame_count == 0) {
                            f->total_bit_errors += bit_errors;
                            f->total_bits += ntest_bits;
                            if (f->freedv_put_error_pattern != NULL) {
                                (*f->freedv_put_error_pattern)(f->error_pattern_callback_state, error_pattern, fdmdv_error_pattern_size(f->fdmdv));
                            }
                        }
                        f->test_frame_count++;
                        if (f->test_frame_count == 4)
                            f->test_frame_count = 0;
                    }

                }
            } /* if (test_frames == 0) .... */
        }

        /* note this freewheels if reliable sync dissapears on bad channels */

        if (f->evenframe)
            f->evenframe = 0;
        else
            f->evenframe = 1;

    } /* if (sync) .... */

    return rx_status;
}
