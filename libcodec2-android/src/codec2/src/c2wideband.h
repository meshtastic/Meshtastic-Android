/* 
 * File:   c2wideband.h
 * Author: phil
 *
 * Created on 02 July 2017, 20:42
 */

#ifndef C2WIDEBAND_H 
#define	C2WIDEBAND_H 

#include "defines.h"
#include "phase.h"
#include "quantise.h"
#include "newamp1.h"
#include "codec2_internal.h"

#define C2WB_K           30  /* rate K vector length */
#define C2WB_FS       16000 

#define C2WB_NT           8  /* number of blocks in time = 160ms blocks */
#define C2WB_TF           0.02  /* 20ms frames */
#define C2WB_DEC          2  /* decimation factor */
#define C2WB_SPERF       30  /* samples per frame */
//TODO: decide what this is
#define C2WB_BPERF      256  /* bits per frame */


typedef struct {
    int rmap[C2WB_K * C2WB_NT];
    int cmap[C2WB_K * C2WB_NT];
} WIDEBAND_MAP;



void codec2_decode_wb(struct CODEC2 *c2, short speech[], const unsigned char * bits);

void calculate_Am_freqs_kHz(float Wo, int L, float p_Am_freqs_kHz[]);
void resample_const_rate_f_mel(C2CONST *c2const, MODEL * model, float K, float* rate_K_surface, float* rate_K_sample_freqs_kHz);
void correct_rate_K_vec(MODEL *model, float rate_K_vec[], float rate_K_sample_freqs_kHz[], float Am_freqs_kHz[], float orig_AmdB[],  int K, float Wo, int L, int Fs, float rate_K_vec_corrected[]);
void batch_rate_K_dct2(C2CONST *c2const, MODEL model_frames[], int frames, int vq_en, int plots, int* voicing, float *mean_);
void rate_K_dct2(C2CONST *c2const, int n_block_frames, MODEL model_block[n_block_frames], WIDEBAND_MAP * wb_map);
void wideband_enc_dec(C2CONST *c2const, int n_block_frames, MODEL model_block[], WIDEBAND_MAP * wb_map,
        MODEL model_block_[], float * p_dct2_sd,  int * p_qn , float rate_K_surface_block[][C2WB_K], float rate_K_surface_block_[][C2WB_K]);
void codec2_decode_wb(struct CODEC2 *c2, short speech[], const unsigned char * bits);
void codec2_encode_wb(struct CODEC2 *c2, unsigned char * bits, short speech[]);
void experiment_rate_K_dct2(C2CONST *c2const, MODEL model_frames[], int frames);

#ifdef	__cplusplus
extern "C" {
#endif
#ifdef	__cplusplus
}
#endif

#endif	/* C2WIDEBAND_H */

