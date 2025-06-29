/*
  vq_mbest.c
  David Rowe Dec 2019

  Utility to perform a mbest VQ search on vectors from stdin, sending
  quantised vectors to stdout.
*/

#include <assert.h>
#include <getopt.h>
#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>
#include "mbest.h"

#define MAX_K       20
#define MAX_ENTRIES 4096
#define MAX_STAGES  5

void quant_mbest(float vec_out[],
                 int   indexes[],
                 float vec_in[],
                 int   num_stages,
                 float vqw[], float vq[], 
                 int   m[], int k,
                 int   mbest_survivors);

int verbose = 0;

int main(int argc, char *argv[]) {
    float vq[MAX_STAGES*MAX_K*MAX_ENTRIES];
    float vqw[MAX_STAGES*MAX_K*MAX_ENTRIES];
    int   m[MAX_STAGES];
    int   k=0, mbest_survivors=1, num_stages=0;
    char  fnames[256], fn[256], *comma, *p;
    FILE *fq;
    float lower = -1E32;
    int   st = -1;
    int   en = -1;
    int   num = INT_MAX;
    int   output_vec_usage = 0;
    
    int o = 0; int opt_idx = 0;
    while (o != -1) {
       static struct option long_opts[] = {
	   {"k",         required_argument, 0, 'k'},
	   {"quant",     required_argument, 0, 'q'},
	   {"mbest",     required_argument, 0, 'm'},
	   {"lower",     required_argument, 0, 'l'},
	   {"verbose",   required_argument, 0, 'v'},
           {"st",        required_argument, 0, 't'},
           {"en",        required_argument, 0, 'e'},
           {"num",       required_argument, 0, 'n'},
           {"vec_usage", no_argument, 0, 'u'},
	   {0, 0, 0, 0}
        };

        o = getopt_long(argc,argv,"hk:q:m:vt:e:n:u",long_opts,&opt_idx);
        switch (o) {
	case 'k':
	    k = atoi(optarg);
	    assert(k <= MAX_K);
	    break; 
	case 'q':
	    /* load up list of comma delimited file names */
            strcpy(fnames, optarg);
            p = fnames;
            num_stages = 0;
            do {
                assert(num_stages < MAX_STAGES);
                strcpy(fn, p);
                comma = strchr(fn, ',');
                if (comma) {
                    *comma = 0;
                    p = comma+1;
                }
                /* load quantiser file */
                fprintf(stderr, "stage: %d loading %s ... ", num_stages, fn);
                fq=fopen(fn, "rb");
                if (fq == NULL) {
                    fprintf(stderr, "Couldn't open: %s\n", fn);
                    exit(1);
                }
                /* count how many entries m of dimension k are in this VQ file */
                m[num_stages] = 0;
		float dummy[k];
                while (fread(dummy, sizeof(float), k, fq) == (size_t)k)
                    m[num_stages]++;
                assert(m[num_stages] <= MAX_ENTRIES);
                fprintf(stderr, "%d entries of vectors width %d\n", m[num_stages], k);
                /* now load VQ into memory */
                rewind(fq);                       
                int rd = fread(&vq[num_stages*k*MAX_ENTRIES], sizeof(float), m[num_stages]*k, fq);
                assert(rd == m[num_stages]*k);
                num_stages++;
                fclose(fq);
            } while(comma);
            break;
        case 'm':
            mbest_survivors = atoi(optarg);
            fprintf(stderr, "mbest_survivors = %d\n",  mbest_survivors);
            break;
        case 'n':
	    num = atoi(optarg);
            break;
        case 'l':
            lower = atof(optarg);
            break;
        case 't':
            st = atoi(optarg);
            break;
        case 'e':
            en = atoi(optarg);
            break;
        case 'u':
            output_vec_usage = 1;
            break;
	case 'v':
            verbose = 1;
            break;
	help:
	    fprintf(stderr, "\n");
            fprintf(stderr, "usage: %s -k dimension -q vq1.f32,vq2.f32,.... [Options]\n", argv[0]);
	    fprintf(stderr, "\n");
	    fprintf(stderr, "input vectors on stdin, output quantised vectors on stdout\n");
	    fprintf(stderr, "\n");
	    fprintf(stderr, "--lower lowermeanLimit   Only count vectors with average above this level in distortion calculations\n");
	    fprintf(stderr, "--mbest N                number of survivors at each stage, set to 0 for standard VQ search\n");
            fprintf(stderr, "--st    Kst              start vector element for error calculation (default 0)\n");
            fprintf(stderr, "--en    Ken              end vector element for error calculation (default K-1)\n");
	    fprintf(stderr, "--num   numToProcess     number of vectors to quantise (default to EOF)\n");
	    fprintf(stderr, "--vec_usage              Output a record of how many times each vector is used\n");
 	    fprintf(stderr, "-v                       Verbose\n");
            exit(1);
        }
    }

    if ((num_stages == 0) || (k == 0))
	goto help;

    /* default to measuring error on entire vector */
    if (st == -1) st = 0; 
    if (en == -1) en = k-1;

    float w[k];
    for(int i=0; i<st; i++)
        w[i] = 0.0;
    for(int i=st; i<=en; i++)
        w[i] = 1.0;
    for(int i=en+1; i<k; i++)
        w[i] = 0.0;

    /* apply weighting to codebook (rather than in search) */
    memcpy(vqw, vq, sizeof(vq));
    for(int s=0; s<num_stages; s++) {
        mbest_precompute_weight(&vqw[s*k*MAX_ENTRIES], w, k, m[s]);
    }
    
    int indexes[num_stages], nvecs = 0; int vec_usage[m[0]];
    for(int i=0; i<m[0]; i++) vec_usage[i] = 0;
    float target[k], quantised[k];
    float sqe = 0.0;
    while(fread(&target, sizeof(float), k, stdin) && (nvecs < num)) {
	for(int i=0; i<k; i++)
            target[i] *= w[i];
	int dont_count = 0;
	/* optional clamping to lower limit or mean */
	float mean = 0.0;
	for(int i=0; i<k; i++)
	    mean += target[i];
	mean /= k;         
	float difference = mean - lower;
	if (difference < 0.0) {
	    /* bring target up to lower clamping limit */
	    for(int i=0; i<k; i++)
		target[i] += -difference;
	    dont_count = 1;
	}
	quant_mbest(quantised, indexes, target, num_stages, vqw, vq, m, k, mbest_survivors);
	if (dont_count == 0) {
	    for(int i=st; i<=en; i++)
		sqe += pow(target[i]-quantised[i], 2.0);
	}
	fwrite(&quantised, sizeof(float), k, stdout);
	nvecs++;
	// count number f time each vector is used (just for first stage)
	vec_usage[indexes[0]]++;
    }

    fprintf(stderr, "MSE: %4.2f\n", sqe/(nvecs*(en-st+1)));

    if (output_vec_usage) {
      for(int i=0; i<m[0]; i++)
	fprintf(stderr, "%d\n", vec_usage[i]);
    }

    return 0;
}

// print vector debug function

void pv(char s[], float v[], int k) {
    int i;
    if (verbose) {
        fprintf(stderr, "%s",s);
        for(i=0; i<k; i++)
            fprintf(stderr, "%4.2f ", v[i]);
        fprintf(stderr, "\n");
    }
}

// mbest algorithm version, backported from LPCNet/src

void quant_mbest(float vec_out[],
                 int   indexes[],
                 float vec_in[],
                 int   num_stages,
                 float vqw[], float vq[],
                 int   m[], int k,
                 int   mbest_survivors)
{
    float err[k], se1;
    int i,j,s,s1,ind;
    
    struct MBEST *mbest_stage[num_stages];
    int index[num_stages];
    float target[k];
    
    for(i=0; i<num_stages; i++) {
        mbest_stage[i] = mbest_create(mbest_survivors);
        index[i] = 0;
    }

    se1 = 0.0;
    for(i=0; i<k; i++) {
        err[i] = vec_in[i];
        se1 += err[i]*err[i];
    }
    se1 /= k;
                
    /* now quantise err[] using multi-stage mbest search, preserving
       mbest_survivors at each stage */
    
    mbest_search(vqw, err, k, m[0], mbest_stage[0], index);
    if (verbose) mbest_print("Stage 1:", mbest_stage[0]);
    
    for(s=1; s<num_stages; s++) {

        /* for each candidate in previous stage, try to find best vector in next stage */
        for (j=0; j<mbest_survivors; j++) {
            /* indexes that lead us this far */
            for(s1=0; s1<s; s1++) {
                index[s1+1] = mbest_stage[s-1]->list[j].index[s1];
            }
            /* target is residual err[] vector given path to this candidate */
            for(i=0; i<k; i++)
                target[i] = err[i];
            for(s1=0; s1<s; s1++) {
                ind = index[s-s1];
                if (verbose) fprintf(stderr, "   s: %d s1: %d s-s1: %d ind: %d\n", s,s1,s-s1,ind);
                for(i=0; i<k; i++) {
                    target[i] -= vqw[s1*k*MAX_ENTRIES+ind*k+i];
                }
            }
            pv("   target: ", target, k);
            mbest_search(&vqw[s*k*MAX_ENTRIES], target, k, m[s], mbest_stage[s], index);
        }
        char str[80]; sprintf(str,"Stage %d:", s+1);
        if (verbose) mbest_print(str, mbest_stage[s]);
    }

    for(s=0; s<num_stages; s++) {
        indexes[s] = mbest_stage[num_stages-1]->list[0].index[num_stages-1-s];
    }

    /* OK put it all back together using best survivor */
    for(i=0; i<k; i++)
        vec_out[i] = 0.0;
    for(s=0; s<num_stages; s++) {
        int ind = indexes[s];
        float se2 = 0.0;
        for(i=0; i<k; i++) {
            err[i] -= vqw[s*k*MAX_ENTRIES+ind*k+i];
            vec_out[i] += vq[s*k*MAX_ENTRIES+ind*k+i];
            se2 += err[i]*err[i];
        }
        se2 /= k;
        pv("    err: ", err, k);
        if (verbose) fprintf(stderr, "    se2: %f\n", se2);
    }
    pv("  vec_out: ",vec_out, k);

    pv("\n  vec_in: ", vec_in, k);
    pv("  vec_out: ", vec_out, k);
    pv("    err: ", err, k);
    if (verbose) fprintf(stderr, "    se1: %f\n", se1);

    for(i=0; i<num_stages; i++)
        mbest_destroy(mbest_stage[i]);
}
