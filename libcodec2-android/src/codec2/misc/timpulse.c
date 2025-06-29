/*
  timpulse.c
  David Rowe Dec 2019

  Generate a synthetic speech signal from a sum of sinusoids.  Generates a known test
  signals for phaseNN and ampNN projects.
*/

#include <assert.h>
#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#include <getopt.h>

#define FS 8000

int main(int argc, char *argv[]) {
    short buf[FS] = {0};
    float f0 = 60.0;
    float n0 = 0.0;
    int   Nsecs = 1;
    int   randf0 = 0;
    int   filter = 0;
    int   rande = 0;
    
    int o = 0;
    int opt_idx = 0;
    while( o != -1 ) {
        static struct option long_opts[] = {
            {"help",   no_argument,       0, 'h'},
            {"n0",     required_argument, 0, 'n'},
            {"f0",     required_argument, 0, 'f'},
            {"secs",   required_argument, 0, 's'},
            {"randf0", no_argument, 0, 'r'},
            {"rande",  required_argument, 0, 'e'},
            {"filter", no_argument, 0, 'i'},
            {0, 0, 0, 0}
        };
        
        o = getopt_long(argc,argv,"hn:f:s:r",long_opts,&opt_idx);
        
        switch(o) {
        case 'n':
	    n0 = atof(optarg);
            break;
        case 'f':
            f0 = atof(optarg);
	    break;
        case 's':
            Nsecs = atoi(optarg);
	    break;
        case 'r':
            randf0 = 1;
	    break;
        case 'i':
            filter = 1;
	    break;
        case 'e':
            rande = atoi(optarg);
	    break;
        case '?':
        case 'h':
	    fprintf(stderr,
		    "usage: %s\n"
		    "[--f0 f0Hz]          fixed F0\n" 
                    "[--n0 samples]       time offset\n" 
                    "[--secs Nsecs]       number of seconds to generate\n"
	            "[--randf0]           choose a random F0 every second\n"
	            "[--rande Ndiscrete]  choose a random frame energy every second, Ndiscrete values\n"
		    "\n", argv[0]);
	    exit(1);      
	break;
        }
    }

    int t = 0;
    float A = 100.0;
    
    /* optionally filter with 2nd order system */
    float alpha = 0.25*M_PI, gamma=0.99;
    float a[2] = {-2.0*gamma*cos(alpha), gamma*gamma};
    float mem[2] = {0};
    
    for (int j=0; j<Nsecs; j++) {
	if (rande) {
	    float AdB_min = 20.0*log10(100.0);
	    float AdB_step = 6.0;
	    float num_values = rande;

	    // discrete RV between 0..1
	    float r = (float)rand()/RAND_MAX;
	    r = floor(r*num_values);
	    
	    float AdB = AdB_min + r*AdB_step;
	    A = pow(10.0,AdB/20.0);
	    fprintf(stderr, "r: %f AdB: %f A: %f\n", r, AdB, A);
	}
	if (randf0) {
	    float pitch_period = FS/400.0 + (FS/80.0 - FS/400.0)*rand()/RAND_MAX;
	    f0 = (float)FS/pitch_period;
	    //fprintf(stderr, "P: %f f0: %f\n", pitch_period, f0);
	}
	float Wo = 2.0*M_PI*f0/FS;
	int L = M_PI/Wo;
	float e = 0.0;
	for(int i=0; i<FS; i++) {
	    buf[i] = 0;
	    // 1/sqrt(L) term makes power constant across Wo
	    for(int m=1; m<L; m++)
		buf[i] += (A/sqrt(L))*cos(m*Wo*(t + n0));
	    e += pow(buf[i], 2.0);
	    t++;
	}
	//fprintf(stderr, "e (dB): %f\n", 10*log10(e));
	if (filter) {
	    for(int i=0; i<FS; i++) {
		float x = (float)buf[i];
		float y = (x - mem[0]*a[0] - mem[1]*a[1]);
		mem[1] = mem[0]; mem[0] = y;
		buf[i] = (short)y;
	    }
	}
    
	fwrite(buf, sizeof(short), FS, stdout);
    }
    
    return 0;
}
