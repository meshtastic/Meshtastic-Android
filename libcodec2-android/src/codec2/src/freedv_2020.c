/*---------------------------------------------------------------------------*\

  FILE........: freedv_2020.c
  AUTHOR......: David Rowe
  DATE CREATED: May 2020

  Functions that implement the FreeDV 2020 mode.

\*---------------------------------------------------------------------------*/

#include <assert.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

#include "fsk.h"
#include "fmfsk.h"
#include "codec2.h"
#include "codec2_fdmdv.h"
#include "varicode.h"
#include "freedv_api.h"
#include "freedv_api_internal.h"
#include "comp_prim.h"

#include "codec2_ofdm.h"
#include "ofdm_internal.h"
#include "mpdecode_core.h"
#include "gp_interleaver.h"
#include "ldpc_codes.h"
#include "interldpc.h"
#include "debug_alloc.h"

extern char *ofdm_statemode[];

#ifdef __LPCNET__
void freedv_2020x_open(struct freedv *f) {
    f->speech_sample_rate = FREEDV_FS_16000;
    f->snr_squelch_thresh = 4.0;
    f->squelch_en = 0;

    struct OFDM_CONFIG ofdm_config;
    switch (f->mode) {
    case FREEDV_MODE_2020:
        ofdm_init_mode("2020", &ofdm_config);
        break;
    case FREEDV_MODE_2020B:
        ofdm_init_mode("2020B", &ofdm_config);
        break;
    default:
        assert(0);
    }
    
    f->ofdm = ofdm_create(&ofdm_config);

    f->ldpc = (struct LDPC*)MALLOC(sizeof(struct LDPC));
    assert(f->ldpc != NULL);

    ldpc_codes_setup(f->ldpc, f->ofdm->codename);
    int data_bits_per_frame;
    int vq_type;
    switch (f->mode) {
    case FREEDV_MODE_2020:
        data_bits_per_frame = 312;
        vq_type = 1;    /* vanilla VQ */
        break;
    case FREEDV_MODE_2020B:
        f->ldpc->protection_mode = LDPC_PROT_2020B;
        data_bits_per_frame = 156;
        vq_type = 2;    /* index optimised VQ for increased robustness to single bit errors */
        break;
    default:
        assert(0);
    }

    set_data_bits_per_frame(f->ldpc, data_bits_per_frame);
    int coded_syms_per_frame = f->ldpc->coded_bits_per_frame/f->ofdm->bps;

    f->ofdm_bitsperframe = ofdm_get_bits_per_frame(f->ofdm);
    f->ofdm_nuwbits = f->ofdm->config.nuwbits;
    f->ofdm_ntxtbits = f->ofdm->config.txtbits;
    assert(f->ofdm_ntxtbits == 4);
    
    if (f->verbose) {
        fprintf(stderr, "f->mode = %d\n", f->mode);
        fprintf(stderr, "vq_type = %d\n", vq_type);
        fprintf(stderr, "ldpc_data_bits_per_frame = %d\n", f->ldpc->ldpc_data_bits_per_frame);
        fprintf(stderr, "ldpc_coded_bits_per_frame  = %d\n", f->ldpc->ldpc_coded_bits_per_frame);
        fprintf(stderr, "data_bits_per_frame = %d\n", data_bits_per_frame);
        fprintf(stderr, "coded_bits_per_frame  = %d\n", f->ldpc->coded_bits_per_frame);
        fprintf(stderr, "coded_syms_per_frame  = %d\n", f->ldpc->coded_bits_per_frame/f->ofdm->bps);
        fprintf(stderr, "ofdm_bits_per_frame  = %d\n", f->ofdm_bitsperframe);
        fprintf(stderr, "ofdm_nuwbits = %d\n", f->ofdm_nuwbits);
        fprintf(stderr, "ofdm_ntxtbits = %d\n", f->ofdm_ntxtbits);
    }

    f->codeword_symbols = (COMP*)MALLOC(sizeof(COMP) * coded_syms_per_frame);
    assert(f->codeword_symbols != NULL);

    f->codeword_amps = (float*)MALLOC(sizeof(float) * coded_syms_per_frame);
    assert(f->codeword_amps != NULL);

    for (int i=0; i< coded_syms_per_frame; i++) {
        f->codeword_symbols[i].real = 0.0f;
        f->codeword_symbols[i].imag = 0.0f;
        f->codeword_amps[i] = 0.0f;
    }

    f->nin = f->nin_prev = ofdm_get_samples_per_frame(f->ofdm);
    f->n_nat_modem_samples = ofdm_get_samples_per_frame(f->ofdm);
    f->n_nom_modem_samples = ofdm_get_samples_per_frame(f->ofdm);
    f->n_max_modem_samples = ofdm_get_max_samples_per_frame(f->ofdm);
    f->modem_sample_rate = f->ofdm->config.fs;
    f->clip_en = 0;
    f->sz_error_pattern = f->ofdm_bitsperframe;

    /* storage for pass through audio interpolating filter.  These are
       the rate FREEDV_FS_8000 modem input samples before interpolation */
    f->passthrough_2020 = CALLOC(1, sizeof(float)*(FDMDV_OS_TAPS_16K + freedv_get_n_max_modem_samples(f)));
    assert(f->passthrough_2020 != NULL);
    
    // make sure we have enough storage for worst case nin with passthrough, in 2020
    // we oversample the 8 kHz input Rx audio to 16 kHz
    int nout_max = 2*freedv_get_n_max_modem_samples(f);
    assert(nout_max <= freedv_get_n_max_speech_samples(f));

    f->lpcnet = lpcnet_freedv_create(vq_type); assert(f->lpcnet != NULL);
    f->codec2 = NULL;

    /* should be exactly an integer number of Codec frames in a OFDM modem frame */
    assert((f->ldpc->data_bits_per_frame % lpcnet_bits_per_frame(f->lpcnet)) == 0);

    f->n_codec_frames = f->ldpc->data_bits_per_frame/lpcnet_bits_per_frame(f->lpcnet);
    f->n_speech_samples = f->n_codec_frames*lpcnet_samples_per_frame(f->lpcnet);
    f->bits_per_codec_frame = lpcnet_bits_per_frame(f->lpcnet);
    f->bits_per_modem_frame = f->n_codec_frames*f->bits_per_codec_frame;

    f->tx_payload_bits = (unsigned char*)MALLOC(f->bits_per_modem_frame);
    assert(f->tx_payload_bits != NULL);
    f->rx_payload_bits = (unsigned char*)MALLOC(f->bits_per_modem_frame);
    assert(f->rx_payload_bits != NULL);
    
    /* attenuate audio 12dB as channel noise isn't that pleasant */
    f->passthrough_gain = 0.25;
}

void freedv_comptx_2020(struct freedv *f, COMP mod_out[]) {
    int    i, k;

    int data_bits_per_frame = f->ldpc->data_bits_per_frame;
    uint8_t tx_bits[data_bits_per_frame];

    memcpy(tx_bits, f->tx_payload_bits, data_bits_per_frame);

    // Generate Varicode txt bits. Txt bits in OFDM frame come just
    // after Unique Word (UW).  Txt bits aren't protected by FEC, and need to be
    // added to each frame after interleaver as done it's thing

    int nspare = f->ofdm_ntxtbits;
    uint8_t txt_bits[nspare];

    for(k=0; k<nspare; k++) {
        if (f->nvaricode_bits == 0) {
            /* get new char and encode */
            char s[2];
            if (f->freedv_get_next_tx_char != NULL) {
                s[0] = (*f->freedv_get_next_tx_char)(f->callback_state);
                f->nvaricode_bits = varicode_encode(f->tx_varicode_bits, s, VARICODE_MAX_BITS, 1, f->varicode_dec_states.code_num);
                f->varicode_bit_index = 0;
            }
        }
        if (f->nvaricode_bits) {
            txt_bits[k] = f->tx_varicode_bits[f->varicode_bit_index++];
            f->nvaricode_bits--;
        } else txt_bits[k] = 0;
    }

    /* optionally replace codec payload bits with test frames known to rx */

    if (f->test_frames) {
        uint8_t payload_data_bits[data_bits_per_frame];
        ofdm_generate_payload_data_bits(payload_data_bits, data_bits_per_frame);

        for (i = 0; i < data_bits_per_frame; i++) {
            tx_bits[i] = payload_data_bits[i];
        }
    }

    /* OK now ready to LDPC encode, interleave, and OFDM modulate */
    ofdm_ldpc_interleave_tx(f->ofdm, f->ldpc, (complex float*)mod_out, tx_bits, txt_bits);
}

int freedv_comprx_2020(struct freedv *f, COMP demod_in[]) {
    int   i, k;
    int   n_ascii;
    char  ascii_out;
    struct OFDM *ofdm = f->ofdm;
    struct LDPC *ldpc = f->ldpc;

    int    data_bits_per_frame = ldpc->data_bits_per_frame;
    int    coded_bits_per_frame = ldpc->coded_bits_per_frame;
    int    coded_syms_per_frame = ldpc->coded_bits_per_frame/ofdm->bps;
    COMP  *codeword_symbols = f->codeword_symbols;
    float *codeword_amps = f->codeword_amps;
    int    rx_bits[f->ofdm_bitsperframe];
    short txt_bits[f->ofdm_ntxtbits];
    COMP  payload_syms[coded_syms_per_frame];
    float payload_amps[coded_syms_per_frame];

    int rx_status = 0;

    int Nerrs_raw = 0;
    int Nerrs_coded = 0;
    int Ncoded;
    int iter = 0;
    int parityCheckCount = 0;
    uint8_t rx_uw[f->ofdm_nuwbits];

    f->sync = 0;

    // TODO: should be higher for 2020?
    float EsNo = 3.0;

    /* looking for modem sync */

    if (ofdm->sync_state == search) {
        ofdm_sync_search(f->ofdm, demod_in);
        f->snr_est = -5.0;
    }

    /* OK modem is in sync */

    if ((ofdm->sync_state == synced) || (ofdm->sync_state == trial)) {
        rx_status |= FREEDV_RX_SYNC;
        if (ofdm->sync_state == trial) rx_status |= FREEDV_RX_TRIAL_SYNC;

        int txt_sym_index = 0;
        
        ofdm_demod(ofdm, rx_bits, demod_in);
        ofdm_extract_uw(ofdm, ofdm->rx_np, ofdm->rx_amp, rx_uw);
        ofdm_disassemble_qpsk_modem_packet_with_text_amps(ofdm, ofdm->rx_np, ofdm->rx_amp, payload_syms, payload_amps, txt_bits, &txt_sym_index);

        f->sync = 1;

        assert((f->ofdm_nuwbits+f->ofdm_ntxtbits+coded_bits_per_frame) == f->ofdm_bitsperframe);

        /* newest symbols at end of buffer (uses final i from last loop), note we
           change COMP formats from what modem uses internally */

        for(i=0; i< coded_syms_per_frame; i++) {
            codeword_symbols[i] = payload_syms[i];
            codeword_amps[i]    = payload_amps[i];
         }

        /* run de-interleaver */

        COMP  codeword_symbols_de[coded_syms_per_frame];
        float codeword_amps_de[coded_syms_per_frame];

        gp_deinterleave_comp (codeword_symbols_de, codeword_symbols, coded_syms_per_frame);
        gp_deinterleave_float(codeword_amps_de   , codeword_amps   , coded_syms_per_frame);

        float llr[coded_bits_per_frame];
        uint8_t out_char[coded_bits_per_frame];

        if (f->test_frames) {
            Nerrs_raw = count_uncoded_errors(ldpc, &f->ofdm->config, codeword_symbols_de, 0);
            f->total_bit_errors += Nerrs_raw;
            f->total_bits += f->ofdm_bitsperframe;
        }

        symbols_to_llrs(llr, codeword_symbols_de, codeword_amps_de,
                EsNo, ofdm->mean_amp, coded_syms_per_frame);
        ldpc_decode_frame(ldpc, &parityCheckCount, &iter, out_char, llr);
        if (parityCheckCount != ldpc->NumberParityBits) rx_status |= FREEDV_RX_BIT_ERRORS;

        if (f->test_frames) {
            uint8_t payload_data_bits[data_bits_per_frame];
            ofdm_generate_payload_data_bits(payload_data_bits, data_bits_per_frame);
            count_errors_protection_mode(ldpc->protection_mode, &Nerrs_coded, &Ncoded, payload_data_bits, out_char, data_bits_per_frame);
            f->total_bit_errors_coded += Nerrs_coded;
            f->total_bits_coded += Ncoded;
            if (Nerrs_coded) f->total_packet_errors++;
            f->total_packets++;
        } else {
            memcpy(f->rx_payload_bits, out_char, data_bits_per_frame);
        }

        rx_status |= FREEDV_RX_BITS;

        /* If modem is synced we can decode txt bits */

        for(k=0; k<f->ofdm_ntxtbits; k++)  {
            if (k % 2 == 0 && (f->freedv_put_next_rx_symbol != NULL))
            {
                (*f->freedv_put_next_rx_symbol)(f->callback_state_sym,
                                                ofdm->rx_np[txt_sym_index],
                                                ofdm->rx_amp[txt_sym_index]);
                txt_sym_index++;
            }
            
            //fprintf(stderr, "txt_bits[%d] = %d\n", k, rx_bits[i]);
            n_ascii = varicode_decode(&f->varicode_dec_states, &ascii_out, &txt_bits[k], 1, 1);
            if (n_ascii && (f->freedv_put_next_rx_char != NULL)) {
                (*f->freedv_put_next_rx_char)(f->callback_state, ascii_out);
            }
        }

        /* estimate uncoded BER from UW.  Coded bit errors could
           probably be estimated as half of all failed LDPC parity
           checks */

        for(i=0; i<f->ofdm_nuwbits; i++) {
            if (rx_uw[i] != ofdm->tx_uw[i]) {
                f->total_bit_errors++;
            }
        }
        f->total_bits += f->ofdm_nuwbits;
        
        ofdm_get_demod_stats(f->ofdm, &f->stats, ofdm->rx_np, ofdm->rowsperframe*ofdm->nc);
        f->snr_est = f->stats.snr_est;
    }

    /* iterate state machine and update nin for next call */

    f->nin = ofdm_get_nin(ofdm);
    ofdm_sync_state_machine(ofdm, rx_uw);

    if ((f->verbose && (ofdm->last_sync_state == search)) || (f->verbose == 2)) {
        assert(rx_status <= 15);
        fprintf(stderr, "%3d st: %-6s euw: %2d %1d f: %5.1f pbw: %d snr: %4.1f eraw: %3d ecdd: %3d iter: %3d pcc: %3d rxst: %s\n",
                f->frames++, ofdm_statemode[ofdm->last_sync_state], ofdm->uw_errors, ofdm->sync_counter,
		(double)ofdm->foff_est_hz, ofdm->phase_est_bandwidth,
                f->snr_est, Nerrs_raw, Nerrs_coded, iter, parityCheckCount, rx_sync_flags_to_text[rx_status]);
    }

    return rx_status;
}
#endif
