/*---------------------------------------------------------------------------*\

  FILE........: tnewamp1.c
  AUTHOR......: David Rowe
  DATE CREATED: Jan 2017

  Tests for the C version of the newamp1 amplitude modelling used for
  700c.  This program outputs a file of Octave vectors that are loaded
  and automatically tested against the Octave version of the modem by
  the Octave script tnewamp1.m

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2017 David Rowe

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

#include "defines.h"
#include "codec2_fft.h"
#include "sine.h"
#include "nlp.h"
#include "dump.h"
#include "octave.h"
#include "newamp1.h"
#include "quantise.h"

#define FRAMES 300

int main(int argc, char *argv[]) {
    int Fs = 8000;
    C2CONST c2const = c2const_create(Fs, N_S);
    int   n_samp = c2const.n_samp;
    int   m_pitch = c2const.m_pitch;
    short buf[n_samp];	        /* input/output buffer                   */
    float Sn[m_pitch];	        /* float input speech samples            */
    COMP  Sw[FFT_ENC];	        /* DFT of Sn[]                           */
    codec2_fft_cfg fft_fwd_cfg; /* fwd FFT states                        */
    float w[m_pitch];	        /* time domain hamming window            */
    float W[FFT_ENC];	        /* DFT of w[]                            */
    MODEL model;
    void *nlp_states;
    codec2_fft_cfg phase_fft_fwd_cfg, phase_fft_inv_cfg;
    float pitch, prev_f0;
    int   i,m,f,k;
    
    if (argc != 2) {
        printf("usage: ./tnewamp1 RawFile\n");
        exit(1);
    }
    nlp_states = nlp_create(&c2const);
    prev_f0 = 1.0/P_MAX_S;
    fft_fwd_cfg = codec2_fft_alloc(FFT_ENC, 0, NULL, NULL); 
    make_analysis_window(&c2const,fft_fwd_cfg, w, W);

    phase_fft_fwd_cfg = codec2_fft_alloc(NEWAMP1_PHASE_NFFT, 0, NULL, NULL);
    phase_fft_inv_cfg = codec2_fft_alloc(NEWAMP1_PHASE_NFFT, 1, NULL, NULL);

    for(i=0; i<m_pitch; i++) {
	Sn[i] = 1.0;
    }

    int K = 20;
    float rate_K_sample_freqs_kHz[K];
    float model_octave[FRAMES][MAX_AMP+2];    // model params in matrix format, useful for C <-> Octave  
    float rate_K_surface[FRAMES][K];          // rate K vecs for each frame, form a surface that makes pretty graphs
    float rate_K_surface_no_mean[FRAMES][K];  // mean removed surface  
    float rate_K_surface_no_mean_[FRAMES][K]; // quantised mean removed surface  
    float mean[FRAMES];
    float mean_[FRAMES];
    float rate_K_surface_[FRAMES][K];         // quantised rate K vecs for each frame
    float interpolated_surface_[FRAMES][K];   // dec/interpolated surface
    //int   voicing[FRAMES];
    int   voicing_[FRAMES];
    float model_octave_[FRAMES][MAX_AMP+2];
    COMP  H[FRAMES][MAX_AMP];
    int indexes[FRAMES][NEWAMP1_N_INDEXES];
    float se = 0.0;
    float eq[K];
        
    for(k=0; k<K; k++)
        eq[k] = 0.0;
    
    for(f=0; f<FRAMES; f++) {
        for(m=0; m<MAX_AMP+2; m++) {
            model_octave[f][m] = 0.0;
            model_octave_[f][m] = 0.0;
        }
        for(m=0; m<MAX_AMP; m++) {
            H[f][m].real = 0.0;
            H[f][m].imag = 0.0;
        }
        for(k=0; k<K; k++)
            interpolated_surface_[f][k] = 0.0;
        voicing_[f] = 0;
    }

    mel_sample_freqs_kHz(rate_K_sample_freqs_kHz, K, ftomel(200.0), ftomel(3700.0));

    //for(int k=0; k<K; k++)
    //    printf("k: %d sf: %f\n", k, rate_K_sample_freqs_kHz[k]);

    FILE *fin = fopen(argv[1], "rb");
    if (fin == NULL) {
        fprintf(stderr, "Problem opening hts1.raw\n");
        exit(1);
    }

    int M = 4; 

    for(f=0; f<FRAMES; f++) {
        assert(fread(buf,sizeof(short),n_samp,fin) == n_samp);

        /* shift buffer of input samples, and insert new samples */

	for(i=0; i<m_pitch-n_samp; i++) {
	    Sn[i] = Sn[i+n_samp];
	}
	for(i=0; i<n_samp; i++) {
	    Sn[i+m_pitch-n_samp] = buf[i];
        }

	/* Estimate Sinusoidal Model Parameters ----------------------*/

	nlp(nlp_states, Sn, n_samp, &pitch, Sw, W, &prev_f0);
	model.Wo = TWO_PI/pitch;

	dft_speech(&c2const, fft_fwd_cfg, Sw, Sn, w);
	two_stage_pitch_refinement(&c2const, &model, Sw);
	estimate_amplitudes(&model, Sw, W, 1);
        est_voicing_mbe(&c2const, &model, Sw, W);
        //voicing[f] = model.voiced;

        /* newamp1  processing ----------------------------------------*/

        newamp1_model_to_indexes(&c2const, 
                                 &indexes[f][0], 
                                 &model, 
                                 &rate_K_surface[f][0], 
                                 rate_K_sample_freqs_kHz,
                                 K,
                                 &mean[f],
                                 &rate_K_surface_no_mean[f][0],
                                 &rate_K_surface_no_mean_[f][0],
                                 &se,
                                 eq, 0);

        newamp1_indexes_to_rate_K_vec(&rate_K_surface_[f][0],
                                      &rate_K_surface_no_mean_[f][0],
                                      rate_K_sample_freqs_kHz,
                                      K,
                                      &mean_[f],
                                      &indexes[f][0], NULL, 1);

        #ifdef VERBOSE
        fprintf(stderr,"f: %d Wo: %4.3f L: %d v: %d\n", f, model.Wo, model.L, model.voiced);
        if ((f % M) == 0) {
            for(i=0; i<5; i++) {
                fprintf(stderr,"  %5.3f", rate_K_surface_[f][i]);
            }
            fprintf(stderr,"\n");
            fprintf(stderr,"  %d %d %d %d\n", indexes[f][0], indexes[f][1], indexes[f][2], indexes[f][3]);
        }
        #endif
        /* log vectors */
 
        model_octave[f][0] = model.Wo;
        model_octave[f][1] = model.L;
        for(m=1; m<=model.L; m++) {
            model_octave[f][m+1] = model.A[m];
        }        
    }

    /* Decoder */

    MODEL model__[M];
    float prev_rate_K_vec_[K];
    COMP  HH[M][MAX_AMP+1];
    float Wo_left;
    int   voicing_left;

    /* initial conditions */

    for(k=0; k<K; k++)
        prev_rate_K_vec_[k] = 0.0;
    voicing_left = 0;
    Wo_left = 2.0*M_PI/100.0;

    /* decoder runs on every M-th frame, 25Hz frame rate, offset at
       start is to minimise processing delay (thanks Jeroen!) */

    fprintf(stderr,"\n");
    for(f=M-1; f<FRAMES; f+=M) {

        float a_interpolated_surface_[M][K];
        newamp1_indexes_to_model(&c2const,
                                 model__,
                                 (COMP*)HH,
                                 (float*)a_interpolated_surface_,
                                 prev_rate_K_vec_,
                                 &Wo_left,
                                 &voicing_left,
                                 rate_K_sample_freqs_kHz, 
                                 K,
                                 phase_fft_fwd_cfg, 
                                 phase_fft_inv_cfg,
                                 &indexes[f][0],
                                 NULL, 1);

        #ifdef VERBOSE
        fprintf(stderr,"f: %d\n", f);
        fprintf(stderr,"  %d %d %d %d\n", indexes[f][0], indexes[f][1], indexes[f][2], indexes[f][3]);
        for(i=0; i<M; i++) {
            fprintf(stderr,"  Wo: %4.3f L: %d v: %d\n", model__[i].Wo, model__[i].L, model__[i].voiced);
        }
        fprintf(stderr,"  rate_K_vec: ");
        for(i=0; i<5; i++) {
            fprintf(stderr,"%5.3f  ", prev_rate_K_vec_[i]);
        }
        fprintf(stderr,"\n");
        fprintf(stderr,"  Am H:\n");

        for(m=0; m<M; m++) {
            fprintf(stderr,"    ");  
            for(i=1; i<=5; i++) {
                fprintf(stderr,"%5.1f (%5.3f %5.3f)  ", model__[m].A[i], HH[m][i].real, HH[m][i].imag);
            }
            fprintf(stderr,"\n");
        }

        fprintf(stderr,"\n\n");
        #endif
        
        //if (f == 7)
        //  exit(0);

        /* with f == 0, we don't store ouput, but memories are updated, helps to match
           what happens in Codec 2 mode */

        if (f >= M) {
           for(i=0; i<M; i++) {
               for(k=0; k<K; k++) {
                   interpolated_surface_[f-M+i][k] = a_interpolated_surface_[i][k];
               }
           }
          
           /* store test vectors */

            for(i=f-M, m=0; i<f; i++,m++) {
                model_octave_[i][0] = model__[m].Wo;
                model_octave_[i][1] = model__[m].L; 
                voicing_[i] = model__[m].voiced;
            }

            int j;
            for(i=f-M, j=0; i<f; i++,j++) {
                for(m=1; m<=model__[j].L; m++) {
                    model_octave_[i][m+1] = model__[j].A[m]; 
                    H[i][m-1] = HH[j][m];// aH[m];
                }
            }
        }
    }

    fclose(fin);

    /* save vectors in Octave format */

    FILE *fout = fopen("tnewamp1_out.txt","wt");
    assert(fout != NULL);
    fprintf(fout, "# Created by tnewamp1.c\n");
    octave_save_float(fout, "rate_K_surface_c", (float*)rate_K_surface, FRAMES, K, K);
    octave_save_float(fout, "mean_c", (float*)mean, 1, FRAMES, 1);
    octave_save_float(fout, "eq_c", eq, 1, K, K);
    octave_save_float(fout, "rate_K_surface_no_mean_c", (float*)rate_K_surface_no_mean, FRAMES, K, K);
    octave_save_float(fout, "rate_K_surface_no_mean__c", (float*)rate_K_surface_no_mean_, FRAMES, K, K);
    octave_save_float(fout, "mean__c", (float*)mean_, FRAMES, 1, 1);
    octave_save_float(fout, "rate_K_surface__c", (float*)rate_K_surface_, FRAMES, K, K);
    octave_save_float(fout, "interpolated_surface__c", (float*)interpolated_surface_, FRAMES, K, K);
    octave_save_float(fout, "model_c", (float*)model_octave, FRAMES, MAX_AMP+2, MAX_AMP+2);
    octave_save_float(fout, "model__c", (float*)model_octave_, FRAMES, MAX_AMP+2, MAX_AMP+2);
    octave_save_int(fout, "voicing__c", (int*)voicing_, 1, FRAMES);
    octave_save_complex(fout, "H_c", (COMP*)H, FRAMES, MAX_AMP, MAX_AMP);
    fclose(fout);

    printf("Done! Now run\n  octave:1> tnewamp1(\"../path/to/build_linux/src/hts1a\", \"../path/to/build_linux/unittest\")\n");
    return 0;
}

