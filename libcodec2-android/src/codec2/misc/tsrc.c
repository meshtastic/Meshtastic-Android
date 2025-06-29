/*
   tsrc.c
   David Rowe
   Sat Nov 3 2012

   Unit test for libresample code.

   build: gcc tsrc.c -o tsrc -lm -lsamplerate

  */

#include <assert.h>
#include <math.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <samplerate.h>
#include <unistd.h>

#define N    10000                   /* processing buffer size */

void display_help(void) {
    fprintf(stderr, "\nusage: tsrc inputRawFile OutputRawFile OutSampleRatio [-l] [-c]\n");
    fprintf(stderr, "\nUse - for stdin/stdout\n\n");
    fprintf(stderr, "-l fast linear resampler\n");
    fprintf(stderr, "-c complex (two channel) resampling\n\n");
}

int main(int argc, char *argv[]) {
    FILE       *fin, *fout;
    short       in_short[N], out_short[N];
    float       in[N], out[N];
    SRC_STATE  *src;
    SRC_DATA    data;
    int         error, nin, nremaining, i;

    if (argc < 3) {
	display_help();
	exit(1);
    }

    if (strcmp(argv[1], "-") == 0) 
        fin = stdin;
    else
        fin = fopen(argv[1], "rb");
    assert(fin != NULL);

    if (strcmp(argv[2], "-") == 0) 
        fout = stdout;
    else
        fout = fopen(argv[2], "wb");
    assert(fout != NULL);

    data.data_in = in;
    data.data_out = out;
    data.end_of_input = 0;
    data.src_ratio = atof(argv[3]);

    int channels = 1;
    int resampler = SRC_SINC_FASTEST;
    int opt;
    while ((opt = getopt(argc, argv, "lc")) != -1) {
        switch (opt) {
        case 'l': resampler = SRC_LINEAR; break;
        case 'c': channels = 2; break;
        default:
            display_help();
            exit(1);
        }
    }

    data.input_frames = N/channels;
    data.output_frames = N/channels;

    src = src_new(resampler, channels, &error);
    assert(src != NULL);

    int total_in = 0;
    int total_out = 0;

    nin = data.input_frames;
    nremaining = 0;
    while(fread(&in_short[nremaining*channels], sizeof(short)*channels, nin, fin) == nin) {
	src_short_to_float_array(in_short, in, N);
	error = src_process(src, &data);
        assert(error == 0);
	src_float_to_short_array(out, out_short, data.output_frames_gen*channels);

	fwrite(out_short, sizeof(short), data.output_frames_gen*channels, fout);
        if (fout == stdout) fflush(stdout);

        nremaining = data.input_frames - data.input_frames_used;
        nin = data.input_frames_used;
	//fprintf(stderr, "input frames: %d output_frames %d nremaining: %d\n", 
        //        (int)data.input_frames_used, (int)data.output_frames_gen, nremaining);
        for(i=0; i<nremaining*channels; i++)
            in_short[i] = in_short[i+nin*channels];

        total_in  += data.input_frames_used;
        total_out += data.output_frames_gen;
    }

    //fprintf(stderr, "total_in: %d total_out: %d\n", total_in, total_out);

    fclose(fout);
    fclose(fin);

    return 0;
}
