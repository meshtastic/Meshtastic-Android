/*---------------------------------------------------------------------------*\

  FILE........: freedv_700.c
  AUTHOR......: David Rowe
  DATE CREATED: May 2020

  Functions that implement the various FreeDV 700 modes, and more generally 
  OFDM data modes.

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
#include "filter.h"

extern char *ofdm_statemode[];

void freedv_700c_open(struct freedv *f) {
    f->snr_squelch_thresh = 0.0;
    f->squelch_en = 0;

    f->cohpsk = cohpsk_create();
    f->nin = f->nin_prev = COHPSK_NOM_SAMPLES_PER_FRAME;
    f->n_nat_modem_samples = COHPSK_NOM_SAMPLES_PER_FRAME;                       // native modem samples as used by the modem
    f->n_nom_modem_samples = f->n_nat_modem_samples * FREEDV_FS_8000 / COHPSK_FS;// number of samples after native samples are interpolated to 8000 sps
    f->n_max_modem_samples = COHPSK_MAX_SAMPLES_PER_FRAME * FREEDV_FS_8000 / COHPSK_FS + 1;
    f->modem_sample_rate = FREEDV_FS_8000;                                       // note weird sample rate tamed by resampling
    f->clip_en = 1;
    f->sz_error_pattern = cohpsk_error_pattern_size();
    f->test_frames_diversity = 1;

    f->ptFilter7500to8000 = (struct quisk_cfFilter *)MALLOC(sizeof(struct quisk_cfFilter));
    f->ptFilter8000to7500 = (struct quisk_cfFilter *)MALLOC(sizeof(struct quisk_cfFilter));
    quisk_filt_cfInit(f->ptFilter8000to7500, quiskFilt120t480, sizeof(quiskFilt120t480)/sizeof(float));
    quisk_filt_cfInit(f->ptFilter7500to8000, quiskFilt120t480, sizeof(quiskFilt120t480)/sizeof(float));

    f->speech_sample_rate = FREEDV_FS_8000;
    f->codec2 = codec2_create(CODEC2_MODE_700C); assert(f->codec2 != NULL);

    f->n_codec_frames = 2;
    f->n_speech_samples = f->n_codec_frames*codec2_samples_per_frame(f->codec2);
    f->bits_per_codec_frame = codec2_bits_per_frame(f->codec2);
    f->bits_per_modem_frame = f->n_codec_frames*codec2_bits_per_frame(f->codec2);
    assert(f->bits_per_modem_frame == COHPSK_BITS_PER_FRAME);

    f->tx_payload_bits = (uint8_t*)MALLOC(f->bits_per_modem_frame*sizeof(char)); assert(f->tx_payload_bits != NULL);
    f->rx_payload_bits = (uint8_t*)MALLOC(f->bits_per_modem_frame*sizeof(char)); assert(f->rx_payload_bits != NULL);
}

void freedv_comptx_700c(struct freedv *f, COMP mod_out[]) {
    int    i;
    COMP   tx_fdm[f->n_nat_modem_samples];
    int    tx_bits[COHPSK_BITS_PER_FRAME];

    /* earlier modems used one bit per int for unpacked bits */
    for(i=0; i<COHPSK_BITS_PER_FRAME; i++) tx_bits[i] = f->tx_payload_bits[i];

    /* optionally overwrite the codec bits with test frames */
    if (f->test_frames) {
        cohpsk_get_test_bits(f->cohpsk, tx_bits);
    }

    /* cohpsk modulator */
    cohpsk_mod(f->cohpsk, tx_fdm, tx_bits, COHPSK_BITS_PER_FRAME);

    float gain = 1.0;
    if (f->clip_en) {
        cohpsk_clip(tx_fdm, COHPSK_CLIP, COHPSK_NOM_SAMPLES_PER_FRAME);
        gain = 2.5;
    }
    for(i=0; i<f->n_nat_modem_samples; i++)
        mod_out[i] = fcmult(gain*FDMDV_SCALE*NORM_PWR_COHPSK, tx_fdm[i]);
    i = quisk_cfInterpDecim((complex float *)mod_out, f->n_nat_modem_samples, f->ptFilter7500to8000, 16, 15);
}

// open function for OFDM voice modes
void freedv_ofdm_voice_open(struct freedv *f, char *mode) {
    f->snr_squelch_thresh = 0.0;
    f->squelch_en = 0;
    struct OFDM_CONFIG *ofdm_config = (struct OFDM_CONFIG *) calloc(1, sizeof (struct OFDM_CONFIG));
    assert(ofdm_config != NULL);
    ofdm_init_mode(mode, ofdm_config);

    f->ofdm = ofdm_create(ofdm_config);
    assert(f->ofdm != NULL);
    free(ofdm_config);

    ofdm_config = ofdm_get_config_param(f->ofdm);
    f->ofdm_bitsperpacket = ofdm_get_bits_per_packet(f->ofdm);
    f->ofdm_bitsperframe = ofdm_get_bits_per_frame(f->ofdm);
    f->ofdm_nuwbits = ofdm_config->nuwbits;
    f->ofdm_ntxtbits = ofdm_config->txtbits;

    f->ldpc = (struct LDPC*)MALLOC(sizeof(struct LDPC));
    assert(f->ldpc != NULL);

    ldpc_codes_setup(f->ldpc, f->ofdm->codename);
#ifdef __EMBEDDED__
    f->ldpc->max_iter = 10; /* limit LDPC decoder iterations to limit CPU load */
#endif
    int Nsymsperpacket = ofdm_get_bits_per_packet(f->ofdm) / f->ofdm->bps;
    f->rx_syms = (COMP*)MALLOC(sizeof(COMP) * Nsymsperpacket);
    assert(f->rx_syms != NULL);
    f->rx_amps = (float*)MALLOC(sizeof(float) * Nsymsperpacket);
    assert(f->rx_amps != NULL);
    for(int i=0; i<Nsymsperpacket; i++) {
        f->rx_syms[i].real = f->rx_syms[i].imag = 0.0;
        f->rx_amps[i]= 0.0;
    }

    f->nin = f->nin_prev = ofdm_get_samples_per_frame(f->ofdm);
    f->n_nat_modem_samples = ofdm_get_samples_per_frame(f->ofdm);
    f->n_nom_modem_samples = ofdm_get_samples_per_frame(f->ofdm);
    f->n_max_modem_samples = ofdm_get_max_samples_per_frame(f->ofdm);
    f->modem_sample_rate = f->ofdm->config.fs;
    f->clip_en = 0;
    f->sz_error_pattern = f->ofdm_bitsperframe;

    f->tx_bits = NULL; /* not used for 700D */

    f->speech_sample_rate = FREEDV_FS_8000;
    f->codec2 = codec2_create(CODEC2_MODE_700C); assert(f->codec2 != NULL);
    /* should be exactly an integer number of Codec 2 frames in a OFDM modem frame */
    assert((f->ldpc->data_bits_per_frame % codec2_bits_per_frame(f->codec2)) == 0);

    f->n_codec_frames = f->ldpc->data_bits_per_frame/codec2_bits_per_frame(f->codec2);
    f->n_speech_samples = f->n_codec_frames*codec2_samples_per_frame(f->codec2);
    f->bits_per_codec_frame = codec2_bits_per_frame(f->codec2);
    f->bits_per_modem_frame = f->n_codec_frames*f->bits_per_codec_frame;

    f->tx_payload_bits = (unsigned char*)MALLOC(f->bits_per_modem_frame);
    assert(f->tx_payload_bits != NULL);
    f->rx_payload_bits = (unsigned char*)MALLOC(f->bits_per_modem_frame);
    assert(f->rx_payload_bits != NULL);
    
    /* attenuate audio 12dB as channel noise isn't that pleasant */
    f->passthrough_gain = 0.25;

    /* should all add up to a complete frame */
    assert((ofdm_config->ns - 1) * ofdm_config->nc * ofdm_config->bps ==
	   f->ldpc->coded_bits_per_frame + ofdm_config->txtbits + f->ofdm_nuwbits);
}

// open function for OFDM data modes, TODO consider moving to a new
// (freedv_ofdm_data.c) file
void freedv_ofdm_data_open(struct freedv *f) {
    struct OFDM_CONFIG ofdm_config;
    char mode[32];
    if (f->mode == FREEDV_MODE_DATAC0) strcpy(mode, "datac0");
    if (f->mode == FREEDV_MODE_DATAC1) strcpy(mode, "datac1");
    if (f->mode == FREEDV_MODE_DATAC3) strcpy(mode, "datac3");

    ofdm_init_mode(mode, &ofdm_config);
    f->ofdm = ofdm_create(&ofdm_config);
    assert(f->ofdm != NULL);

    // LDPC set up
    f->ldpc = (struct LDPC*)MALLOC(sizeof(struct LDPC));
    assert(f->ldpc != NULL);
    ldpc_codes_setup(f->ldpc, f->ofdm->codename);
#ifdef __EMBEDDED__
    f->ldpc->max_iter = 10; /* limit LDPC decoder iterations to limit CPU load */
#endif

    // useful constants
    f->ofdm_bitsperpacket = ofdm_get_bits_per_packet(f->ofdm);
    f->ofdm_bitsperframe = ofdm_get_bits_per_frame(f->ofdm);
    f->ofdm_nuwbits = ofdm_config.nuwbits;
    f->ofdm_ntxtbits = ofdm_config.txtbits;

    /* payload bits per FreeDV API "frame".  In OFDM modem nomenclature this is the number of
       payload data bits per packet, or the number of data bits in a LDPC codeword */
    f->bits_per_modem_frame = f->ldpc->data_bits_per_frame;

    // buffers for received symbols for one packet/LDPC codeword - may span many OFDM modem frames
    int Nsymsperpacket = ofdm_get_bits_per_packet(f->ofdm) / f->ofdm->bps;
    f->rx_syms = (COMP*)MALLOC(sizeof(COMP) * Nsymsperpacket);
    assert(f->rx_syms != NULL);
    f->rx_amps = (float*)MALLOC(sizeof(float) * Nsymsperpacket);
    assert(f->rx_amps != NULL);
    for(int i=0; i<Nsymsperpacket; i++) {
        f->rx_syms[i].real = f->rx_syms[i].imag = 0.0;
        f->rx_amps[i]= 0.0;
    }

    f->nin = f->nin_prev = ofdm_get_nin(f->ofdm);
    f->n_nat_modem_samples = ofdm_get_samples_per_packet(f->ofdm);
    f->n_nom_modem_samples = ofdm_get_samples_per_frame(f->ofdm);
    /* in burst mode we might jump a preamble frame */
    f->n_max_modem_samples = 2*ofdm_get_max_samples_per_frame(f->ofdm);
    f->modem_sample_rate = f->ofdm->config.fs;
    f->sz_error_pattern = f->ofdm_bitsperpacket;

    // Note inconsistency: freedv API modem "frame" is a OFDM modem packet
    f->tx_payload_bits = (unsigned char*)MALLOC(f->bits_per_modem_frame);
    assert(f->tx_payload_bits != NULL);
    f->rx_payload_bits = (unsigned char*)MALLOC(f->bits_per_modem_frame);
    assert(f->rx_payload_bits != NULL);
}

/* speech or raw data, complex OFDM modulation out */
void freedv_comptx_ofdm(struct freedv *f, COMP mod_out[]) {
    int    i, k;
    int    nspare;

    /* Generate Varicode txt bits (if used), waren't protected by FEC */
    nspare = f->ofdm_ntxtbits;
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
        }
        else txt_bits[k] = 0;
    }

    /* optionally replace payload bits with test frames known to rx */
    if (f->test_frames) {
        uint8_t payload_data_bits[f->bits_per_modem_frame];
        ofdm_generate_payload_data_bits(payload_data_bits, f->bits_per_modem_frame);

        for (i = 0; i < f->bits_per_modem_frame; i++) {
            f->tx_payload_bits[i] = payload_data_bits[i];
        }
    }

    /* OK now ready to LDPC encode, interleave, and OFDM modulate */
    ofdm_ldpc_interleave_tx(f->ofdm, f->ldpc, (complex float*)mod_out, f->tx_payload_bits, txt_bits);
}


int freedv_comprx_700c(struct freedv *f, COMP demod_in_8kHz[]) {
    int   i;
    int   sync;

    int rx_status = 0;

    // quisk_cfInterpDecim() modifies input data so lets make a copy just in case there
    // is no sync and we need to echo inpout to output

    // freedv_nin(f): input samples at Fs=8000 Hz
    // f->nin: input samples at Fs=7500 Hz

    COMP demod_in[freedv_nin(f)];

    for(i=0; i<freedv_nin(f); i++)
        demod_in[i] = demod_in_8kHz[i];

    i = quisk_cfInterpDecim((complex float *)demod_in, freedv_nin(f), f->ptFilter8000to7500, 15, 16);

    for(i=0; i<f->nin; i++)
        demod_in[i] = fcmult(1.0/FDMDV_SCALE, demod_in[i]);

    float rx_soft_bits[COHPSK_BITS_PER_FRAME];

    cohpsk_demod(f->cohpsk, rx_soft_bits, &sync, demod_in, &f->nin);

    for(i=0; i<f->bits_per_modem_frame; i++)
        f->rx_payload_bits[i] = rx_soft_bits[i] < 0.0f;

    f->sync = sync;
    cohpsk_get_demod_stats(f->cohpsk, &f->stats);
    f->snr_est = f->stats.snr_est;

    if (sync) {
        rx_status = FREEDV_RX_SYNC;
        if (f->test_frames == 0) {
            rx_status |= FREEDV_RX_BITS;
        }
        else {

            if (f->test_frames_diversity) {
                /* normal operation - error pattern on frame after diveristy combination */
                short error_pattern[COHPSK_BITS_PER_FRAME];
                int   bit_errors;

                /* test data, lets see if we can sync to the test data sequence */

                char rx_bits_char[COHPSK_BITS_PER_FRAME];
                for(i=0; i<COHPSK_BITS_PER_FRAME; i++)
                    rx_bits_char[i] = rx_soft_bits[i] < 0.0;
                cohpsk_put_test_bits(f->cohpsk, &f->test_frame_sync_state, error_pattern, &bit_errors, rx_bits_char, 0);
                if (f->test_frame_sync_state) {
                    f->total_bit_errors += bit_errors;
                    f->total_bits       += COHPSK_BITS_PER_FRAME;
                    if (f->freedv_put_error_pattern != NULL) {
                        (*f->freedv_put_error_pattern)(f->error_pattern_callback_state, error_pattern, COHPSK_BITS_PER_FRAME);
                    }
                }
            }
            else {
                /* calculate error pattern on uncombined carriers - test mode to spot any carrier specific issues like
                   tx passband filtering */

                short error_pattern[2*COHPSK_BITS_PER_FRAME];
                char  rx_bits_char[COHPSK_BITS_PER_FRAME];
                int   bit_errors_lower, bit_errors_upper;

                /* lower group of carriers */

                float *rx_bits_lower = cohpsk_get_rx_bits_lower(f->cohpsk);
                for(i=0; i<COHPSK_BITS_PER_FRAME; i++) {
                    rx_bits_char[i] = rx_bits_lower[i] < 0.0;
                }
                cohpsk_put_test_bits(f->cohpsk, &f->test_frame_sync_state, error_pattern, &bit_errors_lower, rx_bits_char, 0);

                /* upper group of carriers */

                float *rx_bits_upper = cohpsk_get_rx_bits_upper(f->cohpsk);
                for(i=0; i<COHPSK_BITS_PER_FRAME; i++) {
                    rx_bits_char[i] = rx_bits_upper[i] < 0.0;
                }
                cohpsk_put_test_bits(f->cohpsk, &f->test_frame_sync_state_upper, &error_pattern[COHPSK_BITS_PER_FRAME], &bit_errors_upper, rx_bits_char, 1);

                /* combine total errors and call callback */

                if (f->test_frame_sync_state && f->test_frame_sync_state_upper) {
                    f->total_bit_errors += bit_errors_lower + bit_errors_upper;
                    f->total_bits       += 2*COHPSK_BITS_PER_FRAME;
                    if (f->freedv_put_error_pattern != NULL) {
                        (*f->freedv_put_error_pattern)(f->error_pattern_callback_state, error_pattern, 2*COHPSK_BITS_PER_FRAME);
                    }
                }

            }
        }

    }

    return rx_status;
}

/*
  OFDM demod function that can support complex (float) or real (short)
  samples.  The real short samples are useful for low memory platforms such as
  the SM1000.
*/

int freedv_comp_short_rx_ofdm(struct freedv *f, void *demod_in_8kHz, int demod_in_is_short, float gain) {
    int   i, k;
    int   n_ascii;
    char  ascii_out;
    struct OFDM *ofdm = f->ofdm;
    struct LDPC *ldpc = f->ldpc;

    /* useful constants */
    int Nbitsperframe = ofdm_get_bits_per_frame(ofdm);
    int Nbitsperpacket = ofdm_get_bits_per_packet(ofdm);
    int Nsymsperframe = Nbitsperframe / ofdm->bps;
    int Nsymsperpacket = Nbitsperpacket / ofdm->bps;
    int Npayloadbitsperpacket = Nbitsperpacket - ofdm->nuwbits - ofdm->ntxtbits;
    int Npayloadsymsperpacket = Npayloadbitsperpacket/ofdm->bps;
    int Ndatabitsperpacket = ldpc->data_bits_per_frame;

    complex float *rx_syms = (complex float*)f->rx_syms;
    float *rx_amps = f->rx_amps;

    int    rx_bits[Nbitsperframe];
    short  txt_bits[f->ofdm_ntxtbits];
    COMP   payload_syms[Npayloadsymsperpacket];
    float  payload_amps[Npayloadsymsperpacket];

    int    Nerrs_raw = 0;
    int    Nerrs_coded = 0;
    int    iter = 0;
    int    parityCheckCount = 0;
    uint8_t rx_uw[f->ofdm_nuwbits];

    float new_gain = gain / f->ofdm->amp_scale;

    assert((demod_in_is_short == 0) || (demod_in_is_short == 1));

    int rx_status = 0;
    float EsNo = 3.0;    /* further work: estimate this properly from signal */
    f->sync = 0;
    
    /* looking for OFDM modem sync */
    if (ofdm->sync_state == search) {
        if (demod_in_is_short)
            ofdm_sync_search_shorts(f->ofdm, (short*)demod_in_8kHz, new_gain);
        else
            ofdm_sync_search(f->ofdm, (COMP*)demod_in_8kHz);
        f->snr_est = -5.0;
    }

    if ((ofdm->sync_state == synced) || (ofdm->sync_state == trial)) {
        /* OK we have OFDM modem sync */
        rx_status |= FREEDV_RX_SYNC;
        if (ofdm->sync_state == trial) rx_status |= FREEDV_RX_TRIAL_SYNC;
        if (demod_in_is_short)
            ofdm_demod_shorts(ofdm, rx_bits, (short*)demod_in_8kHz, new_gain);
        else
            ofdm_demod(ofdm, rx_bits, (COMP*)demod_in_8kHz);

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

        // update some FreeDV API level stats
        f->sync = 1;

        if (ofdm->modem_frame == (ofdm->np-1)) {
            /* we have received enough modem frames to complete packet and run LDPC decoder */
            int txt_sym_index = 0;
            ofdm_disassemble_qpsk_modem_packet_with_text_amps(ofdm, rx_syms, rx_amps, payload_syms, payload_amps, txt_bits, &txt_sym_index);

            COMP payload_syms_de[Npayloadsymsperpacket];
            float payload_amps_de[Npayloadsymsperpacket];
            gp_deinterleave_comp (payload_syms_de, payload_syms, Npayloadsymsperpacket);
            gp_deinterleave_float(payload_amps_de, payload_amps, Npayloadsymsperpacket);

            float llr[Npayloadbitsperpacket];
            uint8_t decoded_codeword[Npayloadbitsperpacket];
            symbols_to_llrs(llr, payload_syms_de, payload_amps_de,
                            EsNo, ofdm->mean_amp, Npayloadsymsperpacket);
            iter = run_ldpc_decoder(ldpc, decoded_codeword, llr, &parityCheckCount);
            memcpy(f->rx_payload_bits, decoded_codeword, Ndatabitsperpacket);

            if (strlen(ofdm->data_mode)) {
                // we need a valid CRC to declare a data packet valid
                if (freedv_check_crc16_unpacked(f->rx_payload_bits, Ndatabitsperpacket))
                    rx_status |= FREEDV_RX_BITS;
                else
                    rx_status |= FREEDV_RX_BIT_ERRORS;
            } else {
                
                // voice modes aren't as strict - pass everything through to the speech decoder, but flag
                // frame with possible errors
                rx_status |= FREEDV_RX_BITS;
                if (parityCheckCount != ldpc->NumberParityBits)
                   rx_status |= FREEDV_RX_BIT_ERRORS;
            }

            if (f->test_frames) {
                /* est uncoded BER from payload bits */
                Nerrs_raw = count_uncoded_errors(ldpc, &f->ofdm->config, payload_syms_de, strlen(ofdm->data_mode));
                f->total_bit_errors += Nerrs_raw;
                f->total_bits += Npayloadbitsperpacket;

                /* coded errors from decoded bits */
                uint8_t payload_data_bits[Ndatabitsperpacket];
                ofdm_generate_payload_data_bits(payload_data_bits, Ndatabitsperpacket);
                if (strlen(ofdm->data_mode)) {
                    uint16_t tx_crc16 = freedv_crc16_unpacked(payload_data_bits, Ndatabitsperpacket - 16);
                    uint8_t tx_crc16_bytes[] = { tx_crc16 >> 8, tx_crc16 & 0xff };
                    freedv_unpack(payload_data_bits + Ndatabitsperpacket - 16, tx_crc16_bytes, 16);
                }
                Nerrs_coded = count_errors(payload_data_bits, f->rx_payload_bits, Ndatabitsperpacket);
                f->total_bit_errors_coded += Nerrs_coded;
                f->total_bits_coded += Ndatabitsperpacket;
                if (Nerrs_coded) f->total_packet_errors++;
                f->total_packets++;
            }

            /* decode txt bits (if used) */
            for(k=0; k<f->ofdm_ntxtbits; k++)  {
                if (k % 2 == 0 && (f->freedv_put_next_rx_symbol != NULL))
                {
                    (*f->freedv_put_next_rx_symbol)(f->callback_state_sym, rx_syms[txt_sym_index], rx_amps[txt_sym_index]);
                    txt_sym_index++;
                }
                n_ascii = varicode_decode(&f->varicode_dec_states, &ascii_out, &txt_bits[k], 1, 1);
                if (n_ascii && (f->freedv_put_next_rx_char != NULL)) {
                    (*f->freedv_put_next_rx_char)(f->callback_state, ascii_out);
                }
            }

            ofdm_get_demod_stats(ofdm, &f->stats, rx_syms, Nsymsperpacket);
            f->snr_est = f->stats.snr_est;
        }  /* complete packet */

        if ((ofdm->np == 1) && (ofdm->modem_frame == 0)) {
           /* add in UW bit errors, useful in non-testframe, 
              single modem frame per packet modes */
            for(i=0; i<f->ofdm_nuwbits; i++) {
                if (rx_uw[i] != ofdm->tx_uw[i]) {
                    f->total_bit_errors++;
                }
            }
            f->total_bits += f->ofdm_nuwbits;
        }
        
    } 

    /* iterate state machine and update nin for next call */

    f->nin = ofdm_get_nin(ofdm);
    ofdm_sync_state_machine(ofdm, rx_uw);
     
    int print_full = 0; int print_truncated = 0;
    if (f->verbose && ((rx_status & FREEDV_RX_BITS) || (rx_status &  FREEDV_RX_BIT_ERRORS)))
        print_full = 1;
    if ((f->verbose == 2) && !((rx_status & FREEDV_RX_BITS) || (rx_status &  FREEDV_RX_BIT_ERRORS)))
        print_truncated = 1;
    if (print_full) { 
        fprintf(stderr, "%3d nin: %4d st: %-6s euw: %2d %2d mf: %2d f: %5.1f pbw: %d snr: %4.1f eraw: %4d ecdd: %4d iter: %3d "
                "pcc: %3d rxst: %s\n",
                f->frames++, ofdm->nin,
                ofdm_statemode[ofdm->last_sync_state],
                ofdm->uw_errors,
                ofdm->sync_counter,
                ofdm->modem_frame,
	 	            (double)ofdm->foff_est_hz, ofdm->phase_est_bandwidth,
                (double)f->snr_est, Nerrs_raw, Nerrs_coded, iter, parityCheckCount, rx_sync_flags_to_text[rx_status]);
    }
    if (print_truncated) { 
            fprintf(stderr, "%3d nin: %4d st: %-6s euw: %2d %2d mf: %2d f: %5.1f pbw: %d                                        "
                "         rxst: %s\n",
                f->frames++, ofdm->nin,
                ofdm_statemode[ofdm->last_sync_state],
                ofdm->uw_errors,
                ofdm->sync_counter,
                ofdm->modem_frame,
	 	            (double)ofdm->foff_est_hz, ofdm->phase_est_bandwidth,
                rx_sync_flags_to_text[rx_status]);
    }

    return rx_status;
}
