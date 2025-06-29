/*---------------------------------------------------------------------------*\

  FILE........: ch.c
  AUTHOR......: David Rowe
  DATE CREATED: May 2015

  Channel simulation program for testing command line versions of modems.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2015-2022 David Rowe

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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <errno.h>
#include <getopt.h>

#include "freedv_api.h"
#include "codec2_cohpsk.h"
#include "comp_prim.h"
#include "ht_coeff.h"
#include "ssbfilt_coeff.h"

#include "debug_alloc.h"

#define BUF_N            160
#define MPG_DELAY_MS     0.5
#define MPP_DELAY_MS     2.0
#define MPD_DELAY_MS     4.0

/* see instructions below for how to generate these files */

#define DEFAULT_FADING_DIR    "unittest"
#define MPG_FADING_FILE_NAME  "slow_fading_samples.float"
#define MPP_FADING_FILE_NAME  "fast_fading_samples.float"
#define MPD_FADING_FILE_NAME  "faster_fading_samples.float"

// Gaussian from uniform:
float gaussian(void) {
    double x = (double)rand() / RAND_MAX;
    double y = (double)rand() / RAND_MAX;
    double z = sqrt(-2 * log(x)) * cos(2 * M_PI * y);
    return sqrt(1./2.) * z;
}

// complex noise sample
COMP noise(void) {
    COMP n = {gaussian(),gaussian()};
    return n;
}

int main(int argc, char *argv[])
{
    FILE          *fin, *ffading, *fout;
    char	        *fading_dir;
    float          NodB, foff_hz;
    int            fading_en, nhfdelay;

    short          buf[BUF_N];
    float          htbuf[HT_N+BUF_N];
    COMP           ch_in[BUF_N];
    COMP           ch_fdm[BUF_N];
    COMP           ssbfiltbuf[SSBFILT_N+BUF_N];
    COMP           ssbfiltout[BUF_N];

    COMP           phase_ch;
    float          No, variance;
    COMP           scaled_noise;
    float          hf_gain;
    COMP          *ch_fdm_delay = NULL, aspread, aspread_2ms, delayed, direct;
    float          tx_pwr, tx_pwr_fade, noise_pwr, user_multipath_delay;
    int            frames, i, j, k, Fs, ret, nclipped, noutclipped, ssbfilt_en, complex_out, ctest;
    float          sam, peak, clip, papr, CNo, snr3k, gain;

    if (argc < 3) {
    helpmsg:
        fprintf(stderr, "Command line channel simulation tool.\n"
                        "\n"
                        "usage: %s InputRealModemRawFile OutputRealModemRawFile [Options]\n"
                        "\n"
                        "  real int16 input -> Gain -> Hilbert Transform -> clipper -> freq shift ->\n"
                        "  Multipath -> AWGN noise -> SSB filter -> real int16 output\n"
                        "\n"
                        "[--clip int16]         Hilbert clipper (clip complex signal magnitude, default 32767)\n"
                        "[--complexout]         Optional int16 IQ complex output (default real int16)\n"
                        "[--ctest]              Check PAPR is around 0dB, used to support ctests\n"
                        "[--freq FoffHz]        Frequency offset (default 0Hz)\n"
                        "[--fading_dir Path]    path to multipath fading files (default 'unittest')\n"
                        "[--Fs SampleRateHz]    Sample rate of simulation (default 8000 Hz)\n"
                        "[--gain G]             Linear gain (default 1.0)\n"
                        "[--mpg]                Multipath good 0.1Hz Doppler, 0.5ms delay\n"
                        "[--mpp]                Multipath poor 1.0Hz Doppler, 1.0ms delay\n"
                        "[--mpd]                Multipath disturbed 2.0Hz Doppler, 2.0ms delay\n"
                        "[--ssbfilt 0|1]        SSB bandwidth filter (default 1 on)\n"
                        "[--mulipath_delay ms]  Optionally adjust multipath delay\n"
                        "[--No dBHz]            AWGN Noise density dB/Hz (default -100)"
                        "\n"
                , argv[0]);
        exit(1);
    }

    if (strcmp(argv[1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[1],"rb")) == NULL ) {
        fprintf(stderr, "ch: Error opening input modem raw file: %s: %s.\n",
                argv[1], strerror(errno));
        exit(1);
    }

    if (strcmp(argv[2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[2],"wb")) == NULL ) {
        fprintf(stderr, "ch: Error opening output modem raw file: %s: %s.\n",
                argv[2], strerror(errno));
        exit(1);
    }

    NodB = -100;
    Fs = 8000; foff_hz = 0.0; fading_en = 0; ctest = 0;
    clip =32767; gain = 1.0;
    ssbfilt_en = 1; complex_out = 0;
    fading_dir = strdup(DEFAULT_FADING_DIR); user_multipath_delay = -1.0;

    int o = 0;
    int opt_idx = 0;
    while( o != -1 ){
        static struct option long_opts[] = {
            {"complexout",      no_argument,        0, 'o'},
            {"ctest",           no_argument,        0, 't'},
            {"clip",            required_argument,  0, 'c'},
            {"fading_dir",      required_argument,  0, 'u'},
            {"freq",            required_argument,  0, 'f'},
            {"Fs",              required_argument,  0, 'r'},
            {"gain",            required_argument,  0, 'g'},
            {"ssbfilt",         required_argument,  0, 's'},
            {"help",            no_argument,        0, 'h'},
            {"mpg",             no_argument,        0, 'i'},
            {"mpp",             no_argument,        0, 'p'},
            {"mpd",             no_argument,        0, 'd'},
            {"multipath_delay", required_argument,  0, 'm'},
            {"No",              required_argument,  0, 'n'},
            {0, 0, 0, 0}
        };

        o = getopt_long(argc,argv,"c:df:g:im:n:opr:s:tu:h",long_opts,&opt_idx);
        
        switch(o) {
        case 'c':
            clip = atof(optarg);
            break;
        case 'd':
            fading_en = 3;
            break;
        case 'f':
            foff_hz = atof(optarg);
            break;
        case 'g':
            gain = atof(optarg);
            break;
        case 'i':
            fading_en = 1;
            break;
        case 'm':
            user_multipath_delay = atof(optarg);
            break;
        case 'n':
            NodB = atof(optarg);
            break;
        case 'o':
            complex_out = 1;
            break;
        case 'p':
            fading_en = 2;
            break;
        case 'r':
            Fs = atoi(optarg);
            break;
        case 's':
            ssbfilt_en = atoi(optarg);
            break;
        case 't':
            ctest = 1;
            break;
        case 'u':
            fading_dir = strdup(optarg);
            break;
        case 'h':
        case '?':
            goto helpmsg;
            break;
        }
    }

    phase_ch.real = 1.0; phase_ch.imag = 0.0;

    /*  N = var = NoFs */

    // arbitrary noise scaling, to maintain backwards compatability with many tests.  TODO make the No
    // units more sensible, and fix all the tests that depend on this scaling
    No = pow(10.0, NodB/10.0)*1000*1000;
    variance = Fs*No;

    tx_pwr = tx_pwr_fade = noise_pwr = 0.0;
    noutclipped = 0; nclipped = 0;
    peak = 0.0;

    /* init HF fading model */

    ffading = NULL;
    nhfdelay = 0;
    if (fading_en) {
        char fname[256];

        if (fading_en == 1) {
            sprintf(fname, "%s/%s", fading_dir, MPG_FADING_FILE_NAME);
            ffading = fopen(fname, "rb");
            if (ffading == NULL) {
            cant_load_fading_file:
                fprintf(stderr, "-----------------------------------------------------\n");
                fprintf(stderr, "ch ERROR: Can't find fading file: %s\n", fname);
                fprintf(stderr, "\nAdjust path --fading_dir or use GNU Octave to generate:\n\n");
            gen_fading_file:
                fprintf(stderr, "$ octave --no-gui\n");
                fprintf(stderr, "octave:24> pkg load signal\n");
                fprintf(stderr, "octave:24> time_secs=60\n");
                fprintf(stderr, "octave:25> ch_fading(\"faster_fading_samples.float\", 8000, 2.0, 8000*time_secs)\n");
                fprintf(stderr, "octave:26> ch_fading(\"fast_fading_samples.float\", 8000, 1.0, 8000*time_secs)\n");
                fprintf(stderr, "octave:27> ch_fading(\"slow_fading_samples.float\", 8000, 0.1, 8000*time_secs)\n");
                fprintf(stderr, "-----------------------------------------------------\n");
                exit(1);
            }
            nhfdelay = floor(MPG_DELAY_MS*Fs/1000);
        }

        if (fading_en == 2) {
            sprintf(fname, "%s/%s", fading_dir, MPP_FADING_FILE_NAME);
            ffading = fopen(fname, "rb");
            if (ffading == NULL) goto cant_load_fading_file;
            nhfdelay = floor(MPP_DELAY_MS*Fs/1000);
        }

        if (fading_en == 3) {
	          sprintf(fname, "%s/%s", fading_dir, MPD_FADING_FILE_NAME);
            ffading = fopen(fname, "rb");
            if (ffading == NULL) goto cant_load_fading_file;
            nhfdelay = floor(MPD_DELAY_MS*Fs/1000);
        }

        ch_fdm_delay = (COMP*)MALLOC((nhfdelay+COHPSK_NOM_SAMPLES_PER_FRAME)*sizeof(COMP));
        assert(ch_fdm_delay != NULL);
        for(i=0; i<nhfdelay+COHPSK_NOM_SAMPLES_PER_FRAME; i++) {
            ch_fdm_delay[i].real = 0.0;
            ch_fdm_delay[i].imag = 0.0;
        }

        /* optionally override delay from command line */
        if (user_multipath_delay >= 0.0) nhfdelay = floor(user_multipath_delay*Fs/1000);

        /* first values in file are HF gains */

        for (i=0; i<4; i++)
            ret = fread(&hf_gain, sizeof(float), 1, ffading);
        //fprintf(stderr, "hf_gain: %f\n", hf_gain);
    }

    assert(HT_N == sizeof(ht_coeff)/sizeof(COMP));
    for(i=0; i<HT_N; i++) {
        htbuf[i] = 0.0;
    }
    for(i=0; i<SSBFILT_N; i++) {
        ssbfiltbuf[i].real = 0.0; ssbfiltbuf[i].imag = 0.0;
    }
    COMP lo_phase = {1.0,0.0};
    COMP lo_freq;
    lo_freq.real = cos(2.0*M_PI*SSBFILT_CENTRE/Fs);
    lo_freq.imag = sin(2.0*M_PI*SSBFILT_CENTRE/Fs);

    fprintf(stderr, "ch: Fs: %d NodB: %4.2f foff: %4.2f Hz fading: %d nhfdelay: %d clip: %4.2f ssbfilt: %d complexout: %d\n",
            Fs, NodB, foff_hz, fading_en, nhfdelay, clip, ssbfilt_en, complex_out);

    /* --------------------------------------------------------*\
	                          Main Loop
    \*---------------------------------------------------------*/

    frames = 0;
    while(fread(buf, sizeof(short), BUF_N, fin) == BUF_N) {
        frames++;

        /* Hilbert Transform to produce complex signal so we can do
           single sided freq shifts, HF channel models, and analog
           compression.  Allows us to use real signal I/O.

           As the real and imag filters both have unity gain, ch_in[]
           has twice the power of the real input signal buf[].
        */

        for(i=0, j=HT_N; i<BUF_N; i++,j++) {

            htbuf[j] = (float)buf[i]*gain;

            /* FIR filter with HT to get imag, just delay to get real */

            ch_in[i].real = 0.0;
            ch_in[i].imag = 0.0;
            for(k=0; k<HT_N; k++) {
                ch_in[i].real += htbuf[j-k]*ht_coeff[k].real;
                ch_in[i].imag += htbuf[j-k]*ht_coeff[k].imag;
            }
            //printf("%d %f %f\n", i, ch_in[i].real, ch_in[i].imag);
        }
        assert(j <= (BUF_N+HT_N));

        /* update HT memory */
        for(i=0; i<HT_N; i++)
           htbuf[i] = htbuf[i+BUF_N];

        /* --------------------------------------------------------*\
   	                    Clipping  mag of complex signal
        \*---------------------------------------------------------*/

        for(i=0; i<BUF_N; i++) {
            float mag = sqrt(ch_in[i].real*ch_in[i].real + ch_in[i].imag*ch_in[i].imag);
            float angle = atan2(ch_in[i].imag, ch_in[i].real);
            if (mag > clip) {
              mag = clip;
              nclipped++;
            }
            tx_pwr += mag*mag;
            if (mag > peak) { peak = mag; /*fprintf(stderr, "%f\n",mag);*/ }
            ch_in[i].real = mag*cos(angle);
            ch_in[i].imag = mag*sin(angle);
        }

	/* --------------------------------------------------------*\
	                         Channel
	\*---------------------------------------------------------*/

        fdmdv_freq_shift_coh(ch_fdm, ch_in, foff_hz, Fs, &phase_ch, BUF_N);

        /* optional HF fading -------------------------------------*/

        if (fading_en) {

            /* update delayed signal buffer */

            for(i=0; i<nhfdelay; i++)
                ch_fdm_delay[i] = ch_fdm_delay[i+BUF_N];
            for(j=0; j<BUF_N; i++, j++)
                ch_fdm_delay[i] = ch_fdm[j];

            /* combine direct and delayed paths, both multiplied by
               "spreading" (Doppler) functions */

            for(i=0; i<BUF_N; i++) {
                ret = fread(&aspread, sizeof(COMP), 1, ffading);
                if (ret == 0) {
                    fprintf(stderr, "ch: Fading file finished - simulation stopping.  You may need more samples:\n");
                    goto gen_fading_file;
                    }
                ret = fread(&aspread_2ms, sizeof(COMP), 1, ffading);
                if (ret == 0) {
                    fprintf(stderr, "ch: Fading file finished - simulation stopping.  You may need more samples:\n");
                    goto gen_fading_file;
                }
                //printf("%f %f %f %f\n", aspread.real, aspread.imag, aspread_2ms.real, aspread_2ms.imag);

                direct    = cmult(aspread, ch_fdm[i]);
                delayed   = cmult(aspread_2ms, ch_fdm_delay[i]);
                ch_fdm[i] = fcmult(hf_gain, cadd(direct, delayed));
            }
        }

        /* Measure power after fading model to make sure average pwr
           is the same as AWGN channels. We only output the real
           signal, which is half the power. */

        for(i=0; i<BUF_N; i++) {
            tx_pwr_fade += pow(ch_fdm[i].real, 2.0);
        }

        /* AWGN noise ------------------------------------------*/

        for(i=0; i<BUF_N; i++) {
            COMP n = noise();
            scaled_noise = fcmult(sqrt(variance), n);
            ch_fdm[i] = cadd(ch_fdm[i], scaled_noise);
            noise_pwr += pow(scaled_noise.real, 2.0) + pow(scaled_noise.imag, 2.0);
        }

        /* FIR filter to simulate (a rather flat) SSB filter. We
           filter the complex signal by shifting it down to DC and
           using real coefficients. */

        for(i=0, j=SSBFILT_N; i<BUF_N; i++,j++) {
            if (ssbfilt_en) {
                ssbfiltbuf[j] = cmult(ch_fdm[i], cconj(lo_phase));
                ssbfiltout[i].real = 0.0; ssbfiltout[i].imag = 0.0;
                for(k=0; k<SSBFILT_N; k++) {
                    ssbfiltout[i].real += ssbfiltbuf[j-k].real*ssbfilt_coeff[k];
                    ssbfiltout[i].imag += ssbfiltbuf[j-k].imag*ssbfilt_coeff[k];
                }
                ssbfiltout[i] = cmult(ssbfiltout[i], lo_phase);
                lo_phase = cmult(lo_phase, lo_freq);
            }
            else {
                ssbfiltout[i] = ch_fdm[i];
            }
        }

        /* update SSB filter memory */
        for(i=0; i<SSBFILT_N; i++)
           ssbfiltbuf[i] = ssbfiltbuf[i+BUF_N];

        int nout = (complex_out+1)*BUF_N;
        short bufout[nout], *pout=bufout;
        for(i=0; i<BUF_N; i++) {
            sam = ssbfiltout[i].real;
            if (sam >  32767.0) { noutclipped++; sam = 32767.0; }
            if (sam < -32767.0) { noutclipped++; sam = -32767.0; }
            *pout++ = sam;
            if (complex_out) {
                sam = ssbfiltout[i].imag;
                if (sam >  32767.0) { noutclipped++; sam = 32767.0; }
                if (sam < -32767.0) { noutclipped++; sam = -32767.0; }
                *pout++ = sam;
            }
        }

        fwrite(bufout, sizeof(short), nout, fout);

        /* if this is in a pipeline, we probably don't want the usual
           buffering to occur */

        if (fout == stdout) fflush(stdout);
    }

    fclose(fin);
    fclose(fout);

    int nsamples = frames*BUF_N;
    papr = 10*log10(peak*peak/(tx_pwr/nsamples));
    CNo = 10*log10(tx_pwr/(noise_pwr/(Fs)));
    snr3k = CNo - 10*log10(3000);
    float outclipped_percent = noutclipped*100.0/nsamples;
    fprintf(stderr, "ch: SNR3k(dB): %8.2f  C/No....: %8.2f\n", snr3k, CNo);
    fprintf(stderr, "ch: peak.....: %8.2f  RMS.....: %8.2f   CPAPR.....: %5.2f \n", peak, sqrt(tx_pwr/nsamples), papr);
    fprintf(stderr, "ch: Nsamples.: %8d  clipped.: %8.2f%%  OutClipped: %5.2f%%\n",
                    nsamples, nclipped*100.0/nsamples, outclipped_percent);
    if (outclipped_percent > 0.1) fprintf(stderr, "ch: WARNING output clipping\n");

    if (ffading != NULL) fclose(ffading);
    if (ch_fdm_delay != NULL) FREE(ch_fdm_delay);
    if (ctest) {
        /* special ctest mode: check CPAPR is around 0dB */
        if (fabs(papr) < 0.7) return 0; else return 1;
    }
    else return 0;
}
