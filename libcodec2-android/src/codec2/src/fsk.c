/*---------------------------------------------------------------------------*\

  FILE........: fsk.c
  AUTHOR......: Brady O'Brien & David Rowe
  DATE CREATED: 7 January 2016

  C Implementation of 2/4FSK modulator/demodulator, based on octave/fsk_lib.m

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2016-2020 David Rowe

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

/*---------------------------------------------------------------------------*\

                               DEFINES

\*---------------------------------------------------------------------------*/

/* Define this to enable EbNodB estimate */
/* This needs square roots, may take more cpu time than it's worth */
#define EST_EBNO

/* This is a flag for the freq. estimator to use a precomputed/rt computed hann window table
   On platforms with slow cosf, this will produce a substantial speedup at the cost of a small
    amount of memory 
*/
#define USE_HANN_TABLE

/* This flag turns on run-time hann table generation. If USE_HANN_TABLE is unset,
    this flag has no effect. If USE_HANN_TABLE is set and this flag is set, the
    hann table will be allocated and generated when fsk_init or fsk_init_hbr is 
    called. If this flag is not set, a hann function table of size fsk->Ndft MUST
    be provided. On small platforms, this can be used with a precomputed table to
    save memory at the cost of flash space.
*/
#define GENERATE_HANN_TABLE_RUNTIME

/* Turn off table generation if on cortex M4 to save memory */
#ifdef CORTEX_M4
#undef USE_HANN_TABLE
#endif

/*---------------------------------------------------------------------------*\

                               INCLUDES

\*---------------------------------------------------------------------------*/

#include <assert.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>

#include "fsk.h"
#include "comp_prim.h"
#include "kiss_fftr.h"
#include "modem_probe.h"

/*---------------------------------------------------------------------------*\

                               FUNCTIONS

\*---------------------------------------------------------------------------*/

static void stats_init(struct FSK *fsk);

#ifdef USE_HANN_TABLE
/*
   This is used by fsk_create and fsk_create_hbr to generate a hann function
   table
*/
static void fsk_generate_hann_table(struct FSK* fsk){
    int Ndft = fsk->Ndft;
    size_t i;

    for(i=0; i<Ndft; i++){
        fsk->hann_table[i] = 0.5 - 0.5 * cosf(2.0 * M_PI * (float)i / (float) (Ndft-1));
    }  
}
#endif

/*---------------------------------------------------------------------------*\

  FUNCTION....: fsk_create_core
  AUTHOR......: Brady O'Brien
  DATE CREATED: 7 January 2016
  
  In this version of the demod the standard/hbr modes have been
  largely combined at they shared so much common code.  The
  fsk_create/fsk_create_hbr function interface has been retained to
  maximise compatability with existing applications.

\*---------------------------------------------------------------------------*/

struct FSK * fsk_create_core(int Fs, int Rs, int M, int P, int Nsym, int f1_tx, int tone_spacing)
{
    struct FSK *fsk;
    int i;

    /* Check configuration validity */
    assert(Fs > 0);
    assert(Rs > 0);
    assert(P > 0);
    assert(Nsym > 0);
    /* Ts (Fs/Rs) must be an integer */
    assert( (Fs%Rs) == 0 );
    /* Ts/P (Fs/Rs/P) must be an integer */
    assert( ((Fs/Rs)%P) == 0 );
    /* If P is too low we don't have a good choice of timing offsets to choose from */
    assert( P >= 4 );
    assert( M==2 || M==4);
    
    fsk = (struct FSK*) calloc(1, sizeof(struct FSK)); assert(fsk != NULL);
     
    // Need enough bins to within 10% of tone centre
    float bin_width_Hz = 0.1*Rs;
    float Ndft = (float)Fs/bin_width_Hz;
    Ndft = pow(2.0, ceil(log2(Ndft)));
    
    /* Set constant config parameters */
    fsk->Fs = Fs;
    fsk->Rs = Rs;
    fsk->Ts = Fs/Rs;
    fsk->burst_mode = 0;
    fsk->P = P;
    fsk->Nsym = Nsym;
    fsk->N = fsk->Ts*fsk->Nsym;
    fsk->Ndft = Ndft;
    fsk->tc = 0.1;
    fsk->Nmem = fsk->N+(2*fsk->Ts);
    fsk->f1_tx = f1_tx;
    fsk->tone_spacing = tone_spacing;
    fsk->nin = fsk->N;
    fsk->lock_nin = 0;
    fsk->mode = M==2 ? MODE_2FSK : MODE_4FSK;
    fsk->Nbits = M==2 ? fsk->Nsym : fsk->Nsym*2;
    fsk->est_min = 0;
    fsk->est_max = Fs;
    fsk->est_space = 0.75*Rs;
    fsk->freq_est_type = 0;
    
    //fprintf(stderr, "C.....: M: %d Fs: %d Rs: %d Ts: %d nsym: %d nbit: %d N: %d Ndft: %d fmin: %d fmax: %d\n",
    //       M, fsk->Fs, fsk->Rs, fsk->Ts, fsk->Nsym, fsk->Nbits, fsk->N, fsk->Ndft, fsk->est_min, fsk->est_max);
    /* Set up rx state */
    for(i=0; i<M; i++)
        fsk->phi_c[i] = comp_exp_j(0);
    fsk->f_dc = (COMP*)malloc(M*fsk->Nmem*sizeof(COMP)); assert(fsk->f_dc != NULL);
    for(i=0; i<M*fsk->Nmem; i++)
        fsk->f_dc[i] = comp0();
        
    fsk->fft_cfg = kiss_fft_alloc(Ndft,0,NULL,NULL); assert(fsk->fft_cfg != NULL);    
    fsk->Sf = (float*)malloc(sizeof(float)*fsk->Ndft); assert(fsk->Sf != NULL);
    for(i=0;i<Ndft;i++)fsk->Sf[i] = 0;
    
    #ifdef USE_HANN_TABLE
        #ifdef GENERATE_HANN_TABLE_RUNTIME
            fsk->hann_table = (float*)malloc(sizeof(float)*fsk->Ndft); assert(fsk->hann_table != NULL);
            fsk_generate_hann_table(fsk);
        #else
            fsk->hann_table = NULL;
        #endif
    #endif
    
    
    fsk->norm_rx_timing = 0;
    
    /* Set up tx state */
    fsk->tx_phase_c = comp_exp_j(0);
    
    /* Set up demod stats */
    fsk->EbNodB = 0;
    
    for( i=0; i<M; i++)
        fsk->f_est[i] = 0; 
    
    fsk->ppm = 0;
    
    fsk->stats = (struct MODEM_STATS*)malloc(sizeof(struct MODEM_STATS)); assert(fsk->stats != NULL);
    stats_init(fsk);
    fsk->normalise_eye = 1;
    
    return fsk;
}

/*---------------------------------------------------------------------------* \

  FUNCTION....: fsk_create
  AUTHOR......: Brady O'Brien
  DATE CREATED: 7 January 2016
  
  Create and initialize an instance of the FSK modem. Returns a pointer
  to the modem state/config struct. One modem config struct may be used
  for both mod and demod.

  If you are not intending to use the modulation functions, you can
  set f1_tx to FSK_NONE.

\*---------------------------------------------------------------------------*/

struct FSK * fsk_create(int Fs, int Rs, int M, int tx_f1, int tx_fs) {
    return fsk_create_core(Fs, Rs, M, FSK_DEFAULT_P, FSK_DEFAULT_NSYM, tx_f1, tx_fs);
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: fsk_create_hbr
  AUTHOR......: Brady O'Brien
  DATE CREATED: 11 February 2016
  
  Alternate version of create allows user defined oversampling P and
  averaging window Nsym.  In the current version of the demod it's
  simply an alias for the default core function.

  P is the oversampling rate of the internal demod processing, which
  happens at Rs*P Hz.  We filter the tones at P different timing
  offsets, and choose the best one.  P should be >=8, so we have a
  choice of at least 8 timing offsets.  This may require some
  adjustment of Fs and Rs, as Fs/Rs/P must be an integer.

  Nsym is the number of symbols we average demod parameters like
  symbol timing over.

\*---------------------------------------------------------------------------*/

struct FSK * fsk_create_hbr(int Fs, int Rs, int M, int P, int Nsym, int f1_tx, int tone_spacing) {
    return fsk_create_core(Fs, Rs, M, P, Nsym, f1_tx, tone_spacing);
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: fsk_destroy
  AUTHOR......: Brady O'Brien
  DATE CREATED: 11 February 2016
  
  Call this to free all memory and shut down the modem.

\*---------------------------------------------------------------------------*/

void fsk_destroy(struct FSK *fsk){
    free(fsk->Sf);
    free(fsk->f_dc);
    free(fsk->fft_cfg);
    free(fsk->stats);
    free(fsk->hann_table);
    free(fsk);
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: fsk_mod
  AUTHOR......: Brady O'Brien
  DATE CREATED: 11 February 2016
  
  FSK modulator function, real valued output samples with amplitude 2.

\*---------------------------------------------------------------------------*/

void fsk_mod(struct FSK *fsk,float fsk_out[], uint8_t tx_bits[], int nbits) {
    COMP tx_phase_c = fsk->tx_phase_c;    /* Current complex TX phase */
    int f1_tx = fsk->f1_tx;               /* '0' frequency */
    int tone_spacing = fsk->tone_spacing; /* space between frequencies */
    int Ts = fsk->Ts;                     /* samples-per-symbol */
    int Fs = fsk->Fs;                     /* sample freq */
    int M = fsk->mode;
    COMP dosc_f[M];                       /* phase shift per sample */
    COMP dph;                             /* phase shift of current bit */
    size_t i,j,m,bit_i,sym;

    /* trap these parametrs being set to FSK_UNUSED, then calling mod */
    assert(f1_tx > 0);
    assert(tone_spacing > 0);
    
    /* Init the per sample phase shift complex numbers */
    for( m=0; m<M; m++){
        dosc_f[m] = comp_exp_j(2*M_PI*((float)(f1_tx+(tone_spacing*m))/(float)(Fs)));
    }
    
    bit_i = 0;
    int nsym = nbits/(M>>1);
    for(i=0; i<nsym; i++) {
        sym = 0;
        /* Pack the symbol number from the bit stream */
        for( m=M; m>>=1; ){
            uint8_t bit = tx_bits[bit_i];
            bit = (bit==1)?1:0;
            sym = (sym<<1)|bit;
            bit_i++;
        }
        /* Look up symbol phase shift */
        dph = dosc_f[sym];
        /* Spin the oscillator for a symbol period */
        for(j=0; j<Ts; j++){
            tx_phase_c = cmult(tx_phase_c,dph);
            fsk_out[i*Ts+j] = 2*tx_phase_c.real;
        }
    
    }
    
    /* Normalize TX phase to prevent drift */
    tx_phase_c = comp_normalize(tx_phase_c);
    
    /* save TX phase */
    fsk->tx_phase_c = tx_phase_c;
    
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: fsk_mod_c
  AUTHOR......: Brady O'Brien
  DATE CREATED: 11 February 2016
  
  FSK modulator function, complex valued output samples with magnitude 1.

\*---------------------------------------------------------------------------*/

void fsk_mod_c(struct FSK *fsk,COMP fsk_out[], uint8_t tx_bits[], int nbits) {
    COMP tx_phase_c = fsk->tx_phase_c;    /* Current complex TX phase */
    int f1_tx = fsk->f1_tx;               /* '0' frequency */
    int tone_spacing = fsk->tone_spacing; /* space between frequencies */
    int Ts = fsk->Ts;                     /* samples-per-symbol */
    int Fs = fsk->Fs;                     /* sample freq */
    int M = fsk->mode;
    COMP dosc_f[M];                       /* phase shift per sample */
    COMP dph;                             /* phase shift of current bit */
    size_t i,j,bit_i,sym;
    int m;
    
    /* trap these parametrs being set to FSK_UNUSED, then calling mod */
    assert(f1_tx > 0);
    assert(tone_spacing > 0);

    /* Init the per sample phase shift complex numbers */
    for( m=0; m<M; m++){
        dosc_f[m] = comp_exp_j(2*M_PI*((float)(f1_tx+(tone_spacing*m))/(float)(Fs)));
    }
    
    bit_i = 0;
    int nsym = nbits/(M>>1);
    for(i=0; i<nsym; i++) {
        sym = 0;
        /* Pack the symbol number from the bit stream */
        for( m=M; m>>=1; ){
            uint8_t bit = tx_bits[bit_i];
            bit = (bit==1)?1:0;
            sym = (sym<<1)|bit;
            bit_i++;
        }
        /* Look up symbol phase shift */
        dph = dosc_f[sym];
        /* Spin the oscillator for a symbol period */
        for(j=0; j<Ts; j++){
            tx_phase_c = cmult(tx_phase_c,dph);
            fsk_out[i*Ts+j] = tx_phase_c;
        }
    }
    
    /* Normalize TX phase to prevent drift */
    tx_phase_c = comp_normalize(tx_phase_c);
    
    /* save TX phase */
    fsk->tx_phase_c = tx_phase_c;
    
}


/*---------------------------------------------------------------------------*\

  FUNCTION....: fsk_mod_ext_vco
  AUTHOR......: David Rowe
  DATE CREATED: February 2018
  
  Modulator that assume an external VCO.  The output is a voltage
  that changes for each symbol.

\*---------------------------------------------------------------------------*/

void fsk_mod_ext_vco(struct FSK *fsk, float vco_out[], uint8_t tx_bits[], int nbits) {
    int f1_tx = fsk->f1_tx;                   /* '0' frequency */
    int tone_spacing = fsk->tone_spacing;     /* space between frequencies */
    int Ts = fsk->Ts;                         /* samples-per-symbol */
    int M = fsk->mode;
    int i, j, m, sym, bit_i;
    
    /* trap these parametrs being set to FSK_UNUSED, then calling mod */
    assert(f1_tx > 0);
    assert(tone_spacing > 0);

    bit_i = 0;
    int nsym = nbits/(M>>1);
    for(i=0; i<nsym; i++) {
        /* generate the symbol number from the bit stream, 
           e.g. 0,1 for 2FSK, 0,1,2,3 for 4FSK */

        sym = 0;

        /* unpack the symbol number from the bit stream */

        for( m=M; m>>=1; ){
            uint8_t bit = tx_bits[bit_i];
            bit = (bit==1)?1:0;
            sym = (sym<<1)|bit;
            bit_i++;
        }

        /* 
           Map 'sym' to VCO frequency
           Note: drive is inverted, a higher tone drives VCO voltage lower
         */

        //fprintf(stderr, "i: %d sym: %d freq: %f\n", i, sym, f1_tx + tone_spacing*(float)sym);
        for(j=0; j<Ts; j++) {
            vco_out[i*Ts+j] = f1_tx + tone_spacing*(float)sym;
        }
    }
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: fsk_nin
  AUTHOR......: Brady O'Brien
  DATE CREATED: 11 February 2016
  
  Call me before each call to fsk_demod() to determine how many new
  samples you should pass in.  the number of samples will vary due to
  timing variations.

\*---------------------------------------------------------------------------*/

uint32_t fsk_nin(struct FSK *fsk){
    return (uint32_t)fsk->nin;
}

/*
 * Internal function to estimate the frequencies of the FSK tones.
 * This is split off because it is fairly complicated, needs a bunch of memory, and probably
 * takes more cycles than the rest of the demod.
 * Parameters:
 * fsk - FSK struct from demod containing FSK config
 * fsk_in - block of samples in this demod cycles, must be nin long
 * freqs - Array for the estimated frequencies
 * M - number of frequency peaks to find
 */
void fsk_demod_freq_est(struct FSK *fsk, COMP fsk_in[], float *freqs, int M) {
    int Ndft = fsk->Ndft;
    int Fs = fsk->Fs;
    int nin = fsk->nin;
    size_t i,j;
    float hann;
    float max;
    int imax;
    kiss_fft_cfg fft_cfg = fsk->fft_cfg;
    int freqi[M];
    int st,en,f_zero;
    
    kiss_fft_cpx *fftin  = (kiss_fft_cpx*)malloc(sizeof(kiss_fft_cpx)*Ndft);
    kiss_fft_cpx *fftout = (kiss_fft_cpx*)malloc(sizeof(kiss_fft_cpx)*Ndft);
    
    st = (fsk->est_min*Ndft)/Fs + Ndft/2; if (st < 0) st = 0;
    en = (fsk->est_max*Ndft)/Fs + Ndft/2; if (en > Ndft) en = Ndft;
    //fprintf(stderr, "min: %d max: %d st: %d en: %d\n", fsk->est_min, fsk->est_max, st, en);
    
    f_zero = (fsk->est_space*Ndft)/Fs;
    //fprintf(stderr, "fsk->est_space: %d f_zero = %d\n", fsk->est_space, f_zero);

    int numffts = floor((float)nin/(Ndft/2)) - 1;
    for(j=0; j<numffts; j++){
        int a = j*Ndft/2;
        //fprintf(stderr, "numffts: %d j: %d a: %d\n", numffts, (int)j, a);
        /* Copy FSK buffer into reals of FFT buffer and apply a hann window */
        for(i=0; i<Ndft; i++){
            #ifdef USE_HANN_TABLE
            hann = fsk->hann_table[i];
            #else
            hann = 0.5 - 0.5 * cosf(2.0 * M_PI * (float)i / (float) (fft_samps-1));
            #endif
            fftin[i].r = hann*fsk_in[i+a].real;
            fftin[i].i = hann*fsk_in[i+a].imag;
        }

        /* Do the FFT */
        kiss_fft(fft_cfg,fftin,fftout);

        /* FFT shift to put DC bin at Ndft/2 */
        kiss_fft_cpx tmp;
        for(i=0; i<Ndft/2; i++) {
            tmp = fftout[i];
            fftout[i] = fftout[i+Ndft/2];
            fftout[i+Ndft/2] = tmp;
        }
        
        /* Find the magnitude^2 of each freq slot */
        for(i=0; i<Ndft; i++) {
            fftout[i].r = (fftout[i].r*fftout[i].r) + (fftout[i].i*fftout[i].i) ;
        }
        
        /* Mix back in with the previous fft block */
        /* Copy new fft est into imag of fftout for frequency divination below */
        float tc = fsk->tc;
        for(i=0; i<Ndft; i++){
            fsk->Sf[i] = (fsk->Sf[i]*(1-tc)) + (sqrtf(fftout[i].r)*tc);
            fftout[i].i = fsk->Sf[i];
        }
    }
    
    modem_probe_samp_f("t_Sf",fsk->Sf,Ndft);
    
    max = 0;
    /* Find the M frequency peaks here */
    for(i=0; i<M; i++){
        imax = 0;
        max = 0;
        for(j=st;j<en;j++){
            if(fftout[j].i > max){
                max = fftout[j].i;
                imax = j;
            }
        }
        /* Blank out FMax +/-Fspace/2 */
        int f_min, f_max;
        f_min = imax - f_zero;
        f_min = f_min < 0 ? 0 : f_min;
        f_max = imax + f_zero;
        f_max = f_max > Ndft ? Ndft : f_max;
        for(j=f_min; j<f_max; j++)
            fftout[j].i = 0;
        
        /* Stick the freq index on the list */
        freqi[i] = imax - Ndft/2;
    }
    
    /* Gnome sort the freq list */
    /* My favorite sort of sort*/
    i = 1;
    while(i<M){
        if(freqi[i] >= freqi[i-1]) i++;
        else{
            j = freqi[i];
            freqi[i] = freqi[i-1];
            freqi[i-1] = j;
            if(i>1) i--;
        }
    }

    /* Convert freqs from indices to frequencies */
    for(i=0; i<M; i++){
        freqs[i] = (float)(freqi[i])*((float)Fs/(float)Ndft);
    }

    /* Search for each tone method 2 - correlate with mask with non-zero entries at tone spacings ----- */

    /* construct mask */
    float mask[Ndft];
    for(i=0; i<Ndft; i++) mask[i] = 0.0;
    for(i=0;i<3; i++) mask[i] = 1.0;
    int bin=0;
    for(int m=1; m<=M-1; m++) {
        bin = round((float)m*fsk->tone_spacing*Ndft/Fs)-1;
        for(i=bin; i<=bin+2; i++) mask[i] = 1.0;
    }
    int len_mask = bin+2+1;

    #ifdef MODEMPROBE_ENABLE
    modem_probe_samp_f("t_mask",mask,len_mask);
    #endif

    /* drag mask over Sf, looking for peak in correlation */
    int b_max = st; float corr_max = 0.0;
    float *Sf = fsk->Sf;
    for (int b=st; b<en-len_mask; b++) {
        float corr = 0.0;
        for(i=0; i<len_mask; i++)
            corr += mask[i] * Sf[b+i];
        if (corr > corr_max) {
            corr_max = corr;
            b_max = b;
        }
    }
    float foff = (b_max-Ndft/2)*Fs/Ndft;
    //fprintf(stderr, "fsk->tone_spacing: %d\n",fsk->tone_spacing);
    for (int m=0; m<M; m++)
        fsk->f2_est[m] = foff + m*fsk->tone_spacing;

    #ifdef MODEMPROBE_ENABLE
    modem_probe_samp_f("t_f2_est",fsk->f2_est,M);
    #endif

    free(fftin);
    free(fftout);
}

/* core demodulator function */
void fsk_demod_core(struct FSK *fsk, uint8_t rx_bits[], float rx_filt[], COMP fsk_in[]){
    int N = fsk->N;
    int Ts = fsk->Ts;
    int Rs = fsk->Rs;
    int Fs = fsk->Fs;
    int nsym = fsk->Nsym;
    int nin = fsk->nin;
    int P = fsk->P;
    int Nmem = fsk->Nmem;
    int M = fsk->mode;
    size_t i,j,m;
    float ft1;
    
    COMP t[M];          /* complex number temps */
    COMP t_c;           /* another complex temp */
    COMP *phi_c = fsk->phi_c;  
    COMP *f_dc = fsk->f_dc;  
    COMP phi_ft;        
    int nold = Nmem-nin;
    
    COMP dphift;
    float rx_timing,norm_rx_timing,old_norm_rx_timing,d_norm_rx_timing,appm;

    float fc_avg,fc_tx;
    float meanebno,stdebno,eye_max;
    int neyesamp,neyeoffset;
    
    #ifdef MODEMPROBE_ENABLE
    #define NMP_NAME 26
    char mp_name_tmp[NMP_NAME+1]; /* Temporary string for modem probe trace names */
    #endif

    /* Estimate tone frequencies */
    fsk_demod_freq_est(fsk,fsk_in,fsk->f_est,M);
    #ifdef MODEMPROBE_ENABLE
    modem_probe_samp_f("t_f_est",fsk->f_est,M);
    #endif
    float *f_est;
    if (fsk->freq_est_type)
        f_est = fsk->f2_est;
    else
        f_est = fsk->f_est;
      
    /* update filter (integrator) memory by shifting in nin samples */
    for(m=0; m<M; m++) {
        for(i=0,j=Nmem-nold; i<nold; i++,j++)
            f_dc[m*Nmem+i] = f_dc[m*Nmem+j];
    }
  
    /* freq shift down to around DC, ensuring continuous phase from last frame */
    COMP dphi_m;
    for(m=0; m<M; m++) {
        dphi_m = comp_exp_j(2*M_PI*((f_est[m])/(float)(Fs)));
        for(i=nold,j=0; i<Nmem; i++,j++) {
            phi_c[m] = cmult(phi_c[m],dphi_m);
            f_dc[m*Nmem+i] = cmult(fsk_in[j],cconj(phi_c[m]));
        }
        phi_c[m] = comp_normalize(phi_c[m]);
        #ifdef MODEMPROBE_ENABLE
        snprintf(mp_name_tmp,NMP_NAME,"t_f%zd_dc",m+1);
        modem_probe_samp_c(mp_name_tmp,&f_dc[m*Nmem],Nmem);
        #endif
    }

    /* integrate over symbol period at a variety of offsets */
    COMP f_int[M][(nsym+1)*P];
    for(i=0; i<(nsym+1)*P; i++) {
        int st = i*Ts/P;
        int en = st+Ts-1;
        for(m=0; m<M; m++) {
            f_int[m][i] = comp0();
            for(j=st; j<=en; j++)
                f_int[m][i] = cadd(f_int[m][i], f_dc[m*Nmem+j]);
        }
    }

    #ifdef MODEMPROBE_ENABLE
    for(m=0; m<M; m++) {
        snprintf(mp_name_tmp,NMP_NAME,"t_f%zd_int",m+1);
        modem_probe_samp_c(mp_name_tmp,&f_int[m][0],(nsym+1)*P);
    }    
    #endif                       
        
    /* Fine Timing Estimation */
    /* Apply magic nonlinearity to f1_int and f2_int, shift down to 0, 
     * extract angle */
     
    /* Figure out how much to spin the oscillator to extract magic spectral line */
    dphift = comp_exp_j(2*M_PI*((float)(Rs)/(float)(P*Rs)));
    phi_ft.real = 1;
    phi_ft.imag = 0;
    t_c=comp0();
    for(i=0; i<(nsym+1)*P; i++){
        /* Get abs^2 of fx_int[i], and add 'em */
        ft1 = 0;
        for( m=0; m<M; m++){
            ft1 += (f_int[m][i].real*f_int[m][i].real) + (f_int[m][i].imag*f_int[m][i].imag);
        }
        
        /* Down shift and accumulate magic line */
        t_c = cadd(t_c,fcmult(ft1,phi_ft));

        /* Spin the oscillator for the magic line shift */
        phi_ft = cmult(phi_ft,dphift);
    }

    /* Check for NaNs in the fine timing estimate, return if found */
    /* otherwise segfaults happen */
    if( isnan(t_c.real) || isnan(t_c.imag)){
        return;
    } 

    /* Get the magic angle */
    norm_rx_timing =  atan2f(t_c.imag,t_c.real)/(2*M_PI);
    rx_timing = norm_rx_timing*(float)P;
    
    old_norm_rx_timing = fsk->norm_rx_timing;
    fsk->norm_rx_timing = norm_rx_timing;
    
    /* Estimate sample clock offset */
    d_norm_rx_timing = norm_rx_timing - old_norm_rx_timing;
    
    /* Filter out big jumps in due to nin change */
    if(fabsf(d_norm_rx_timing) < .2){
        appm = 1e6*d_norm_rx_timing/(float)nsym;
        fsk->ppm = .9*fsk->ppm + .1*appm;
    }
    
    /* Figure out how many samples are needed the next modem cycle */
    /* Unless we're in burst mode or nin locked */
    if(!fsk->burst_mode && !fsk->lock_nin) {
        if(norm_rx_timing > 0.25)
            fsk->nin = N+Ts/4;
        else if(norm_rx_timing < -0.25)
            fsk->nin = N-Ts/4;
        else
            fsk->nin = N;
    }

    modem_probe_samp_f("t_norm_rx_timing",&(norm_rx_timing),1);
    modem_probe_samp_i("t_nin",&(fsk->nin),1);
    
    /* Re-sample the integrators with linear interpolation magic */
    int low_sample = (int)floorf(rx_timing);
    float fract = rx_timing - (float)low_sample;
    int high_sample = (int)ceilf(rx_timing);
 
    /* Vars for finding the max-of-4 for each bit */
    float tmax[M];
    
    #ifdef EST_EBNO
    meanebno = 0;
    stdebno = 0;
    #endif

    float rx_nse_pow = 1E-12; float rx_sig_pow = 0.0;
    for(i=0; i<nsym; i++) {

        /* resample at ideal sampling instant */
        int st = (i+1)*P;
        for( m=0; m<M; m++) {
            t[m] =           fcmult(1-fract,f_int[m][st+low_sample]);
            t[m] = cadd(t[m],fcmult(  fract,f_int[m][st+high_sample]));
            /* Figure mag^2 of each resampled fx_int */
            tmax[m] = (t[m].real*t[m].real) + (t[m].imag*t[m].imag);
        }

        /* hard decision decoding of bits */
        float max = tmax[0]; /* Maximum for figuring correct symbol */
        float min = tmax[0];
        int sym = 0; /* Index of maximum */
        for( m=0; m<M; m++){
            if(tmax[m]>max){
                max = tmax[m];
                sym = m;
            }
            if(tmax[m]<min){
                min = tmax[m];
            }
        }
        
        if(rx_bits != NULL){
            /* Get bits for 2FSK and 4FSK */
            if(M==2){
                rx_bits[i] = sym==1;                /* 2FSK. 1 bit per symbol */
            }else if(M==4){
                rx_bits[(i*2)+1] = (sym&0x1);       /* 4FSK. 2 bits per symbol */
                rx_bits[(i*2)  ] = (sym&0x2)>>1;
            }
        }
        
        /* Optionally output filter magnitudes for soft decision/LLR
           calculation.  Update SNRest always as this is a useful
           alternative to the earlier EbNo estimator below */
        float sum = 0.0;
        for(m=0; m<M; m++) {
            if (rx_filt != NULL) rx_filt[m*nsym+i] = sqrtf(tmax[m]);
            sum += tmax[m];
        }
        rx_sig_pow += max;
        rx_nse_pow += (sum-max)/(M-1);

        /* Accumulate resampled int magnitude for EbNodB estimation */
        /* Standard deviation is calculated by algorithm devised by crafty soviets */
        #ifdef EST_EBNO
        /* Accumulate the square of the sampled value */
        ft1 = max;
        stdebno += ft1;
        
        /* Figure the abs value of the max tone */
        meanebno += sqrtf(ft1);
        #endif
    }

    fsk->rx_sig_pow = rx_sig_pow = rx_sig_pow/nsym;
    fsk->rx_nse_pow = rx_nse_pow = rx_nse_pow/nsym;
    fsk->v_est = sqrt(rx_sig_pow-rx_nse_pow);
    fsk->SNRest = rx_sig_pow/rx_nse_pow;
    
    #ifdef EST_EBNO    
    /* Calculate mean for EbNodB estimation */
    meanebno = meanebno/(float)nsym;
    
    /* Calculate the std. dev for EbNodB estimate */
    stdebno = (stdebno/(float)nsym) - (meanebno*meanebno);
    /* trap any negative numbers to avoid NANs flowing through */
    if (stdebno > 0.0) {
        stdebno = sqrt(stdebno);
    } else {
        stdebno = 0.0;
    }
        
    fsk->EbNodB = -6+(20*log10f((1e-6+meanebno)/(1e-6+stdebno)));
    #else
    fsk->EbNodB = 1;
    #endif
    
    /* Write some statistics to the stats struct */

    /* Save clock offset in ppm */
    fsk->stats->clock_offset = fsk->ppm;
        
    /* Calculate and save SNR from EbNodB estimate */

    fsk->stats->snr_est = .5*fsk->stats->snr_est + .5*fsk->EbNodB;//+ 10*log10f(((float)Rs)/((float)Rs*M));
        
    /* Save rx timing */
    fsk->stats->rx_timing = (float)rx_timing;
        
    /* Estimate and save frequency offset */
    fc_avg = fc_tx = 0.0;
    for(int m=0; m<M; m++) {
        fc_avg += f_est[m]/M;
        fc_tx  += (fsk->f1_tx + m*fsk->tone_spacing)/M;
    }
    fsk->stats->foff = fc_tx-fc_avg;
    
    /* Take a sample for the eye diagrams ---------------------------------- */

    /* due to oversample rate P, we have too many samples for eye
       trace.  So lets output a decimated version.  We use 2P
       as we want two symbols worth of samples in trace  */
#ifndef __EMBEDDED__
    int neyesamp_dec = ceil(((float)P*2)/MODEM_STATS_EYE_IND_MAX);
    neyesamp = (P*2)/neyesamp_dec;
    assert(neyesamp <= MODEM_STATS_EYE_IND_MAX);
    fsk->stats->neyesamp = neyesamp;

    neyeoffset = high_sample+1;
    
    int eye_traces = MODEM_STATS_ET_MAX/M;
    int ind;
    
    fsk->stats->neyetr = fsk->mode*eye_traces;
    for( i=0; i<eye_traces; i++){
        for ( m=0; m<M; m++){
            for(j=0; j<neyesamp; j++) {
               /*
                  2*P*i...........: advance two symbols for next trace
                  neyeoffset......: centre trace on ideal timing offset, peak eye opening
                  j*neweyesamp_dec: For 2*P>MODEM_STATS_EYE_IND_MAX advance through integrated 
                                    samples newamp_dec at a time so we dont overflow rx_eye[][]
               */
               ind = 2*P*(i+1) + neyeoffset + j*neyesamp_dec;
               assert((i*M+m) < MODEM_STATS_ET_MAX);
               assert(ind >= 0);
               assert(ind < (nsym+1)*P);
               fsk->stats->rx_eye[i*M+m][j] = cabsolute(f_int[m][ind]);
            }
        }
    }
        
    if (fsk->normalise_eye) {
        eye_max = 0;
        /* Normalize eye to +/- 1 */
        for(i=0; i<M*eye_traces; i++)
            for(j=0; j<neyesamp; j++)
                if(fabsf(fsk->stats->rx_eye[i][j])>eye_max)
                    eye_max = fabsf(fsk->stats->rx_eye[i][j]);
        
        for(i=0; i<M*eye_traces; i++)
            for(j=0; j<neyesamp; j++)
                fsk->stats->rx_eye[i][j] = fsk->stats->rx_eye[i][j]/eye_max;
    }

    fsk->stats->nr = 0;
    fsk->stats->Nc = 0;

    for(i=0; i<M; i++)
        fsk->stats->f_est[i] = f_est[i];
#endif // !__EMBEDDED__
 
    /* Dump some internal samples */
    modem_probe_samp_f("t_EbNodB",&(fsk->EbNodB),1);
    modem_probe_samp_f("t_ppm",&(fsk->ppm),1);
    modem_probe_samp_f("t_rx_timing",&(rx_timing),1);
}

/*---------------------------------------------------------------------------*\

  FUNCTION....: fsk_demod
  AUTHOR......: Brady O'Brien
  DATE CREATED: 11 February 2016
  
  FSK demodulator functions:

    fsk_demod...: complex samples in, bits out
    fsk_demos_sd: complex samples in, soft decision symbols out

\*---------------------------------------------------------------------------*/

void fsk_demod(struct FSK *fsk, uint8_t rx_bits[], COMP fsk_in[]) {
    fsk_demod_core(fsk,rx_bits,NULL,fsk_in);
}

void fsk_demod_sd(struct FSK *fsk, float rx_filt[], COMP fsk_in[]){
    fsk_demod_core(fsk,NULL,rx_filt,fsk_in);
}

/* make sure stats have known values in case monitoring process reads stats before they are set */
static void stats_init(struct FSK *fsk) {
    /* Take a sample for the eye diagrams */
    int i,j,m;
    int P = fsk->P;
    int M = fsk->mode;

    /* due to oversample rate P, we have too many samples for eye
       trace.  So lets output a decimated version */

    /* asserts below as we found some problems over-running eye matrix */
    
    /* TODO: refactor eye tracing code here and in fsk_demod */
#ifndef __EMBEDDED__    
    int neyesamp_dec = ceil(((float)P*2)/MODEM_STATS_EYE_IND_MAX);
    int neyesamp = (P*2)/neyesamp_dec;
    assert(neyesamp <= MODEM_STATS_EYE_IND_MAX);
    fsk->stats->neyesamp = neyesamp;
    
    int eye_traces = MODEM_STATS_ET_MAX/M;
   
    fsk->stats->neyetr = fsk->mode*eye_traces;
    for(i=0; i<eye_traces; i++) {
        for (m=0; m<M; m++){
            for(j=0; j<neyesamp; j++) {
                assert((i*M+m) < MODEM_STATS_ET_MAX);
                fsk->stats->rx_eye[i*M+m][j] = 0;
           }
        }
    }
#endif // !__EMBEDDED__

    fsk->stats->rx_timing = fsk->stats->snr_est = 0;
    
}


/* Set the FSK modem into burst demod mode */

void fsk_enable_burst_mode(struct FSK *fsk){
    fsk->nin = fsk->N;
    fsk->burst_mode = 1;
}

void fsk_clear_estimators(struct FSK *fsk){
    int i;
    /* Clear freq estimator state */
    for(i=0; i < (fsk->Ndft); i++){
        fsk->Sf[i] = 0;
    }
    /* Reset timing diff correction */
    fsk->nin = fsk->N;
}

void fsk_get_demod_stats(struct FSK *fsk, struct MODEM_STATS *stats){
    /* copy from internal stats, note we can't overwrite stats completely
       as it has other states rqd by caller, also we want a consistent
       interface across modem types for the freedv_api.
    */

    stats->clock_offset = fsk->stats->clock_offset;
    stats->snr_est = fsk->stats->snr_est;           // TODO: make this SNR not Eb/No
    stats->rx_timing = fsk->stats->rx_timing;
    stats->foff = fsk->stats->foff;
#ifndef __EMBEDDED__
    stats->neyesamp = fsk->stats->neyesamp;
    stats->neyetr = fsk->stats->neyetr;
    memcpy(stats->rx_eye, fsk->stats->rx_eye, sizeof(stats->rx_eye));
    memcpy(stats->f_est, fsk->stats->f_est, fsk->mode*sizeof(float));
#endif // !__EMBEDDED__
 
    /* these fields not used for FSK so set to something sensible */

    stats->sync = 0;
    stats->nr = fsk->stats->nr;
    stats->Nc = fsk->stats->Nc;
}

/*
 * Set the minimum and maximum frequencies at which the freq. estimator can find tones
 */
void fsk_set_freq_est_limits(struct FSK *fsk, int est_min, int est_max){
    assert(fsk != NULL);
    assert(est_min >= -fsk->Fs/2);
    assert(est_max <=  fsk->Fs/2);
    assert(est_max > est_min);
    fsk->est_min = est_min;
    fsk->est_max = est_max;
}

void fsk_stats_normalise_eye(struct FSK *fsk, int normalise_enable) {
    assert(fsk != NULL);
    fsk->normalise_eye = normalise_enable;
}

void fsk_set_freq_est_alg(struct FSK *fsk, int est_type) {
    assert(fsk != NULL);
    fsk->freq_est_type = est_type;
}







