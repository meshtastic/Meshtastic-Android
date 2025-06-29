/* 
  FILE...: ldpc_enc.c
  AUTHOR.: Don Reid
  CREATED: Aug 2018

  Add noise to LDPC soft decision samples for testing.  Simulates use
  of LDPC code with PSK modem.
*/

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <errno.h>

int main(int argc, char *argv[]) {
    FILE        *fin, *fout;
    float	datain, dataout;

    if (argc < 3) {
        fprintf(stderr, "\n");
        fprintf(stderr, "usage: %s InputFile OutputFile NodB\n", argv[0]);
        fprintf(stderr, "\n");
        exit(1);
    }

    if (strcmp(argv[1], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[1],"rb")) == NULL ) {
        fprintf(stderr, "Error opening input bit file: %s: %s.\n",
                argv[1], strerror(errno));
        exit(1);
    }
        
    if (strcmp(argv[2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[2],"wb")) == NULL ) {
        fprintf(stderr, "Error opening output bit file: %s: %s.\n",
                argv[2], strerror(errno));
        exit(1);
    }

    double NodB = atof(argv[3]);
    double No = pow(10.0, NodB/10.0);
    double sum_xx = 0; double sum_x = 0.0; long n = 0;
    
    fprintf(stderr, "Uncoded PSK Eb/No simulation:\n");
    fprintf(stderr, "No    = % 4.2f dB (%4.2f linear)\n", NodB, No);
    fprintf(stderr, "Eb    = % 4.2f dB (%4.2f linear)\n", 0.0, 1.0);
    fprintf(stderr, "Eb/No = %4.2f dB (%4.2f linear)\n", -NodB, pow(10,-NodB/10.0));
    
    while (fread(&datain, sizeof(float), 1, fin) == 1) {

	// Gaussian from uniform:
	double x = (double)rand() / RAND_MAX;
        double y = (double)rand() / RAND_MAX;
        double z = sqrt(-2 * log(x)) * cos(2 * M_PI * y);

	double noise = sqrt(No/2) * z;
	dataout = datain + noise;

        fwrite(&dataout, sizeof(float), 1, fout);        

        // keep running stats to calculate actual noise variance (power)
        
        sum_xx += noise*noise;
        sum_x  += noise;
        n++;
    }

    fclose(fin);  
    fclose(fout); 

    double noise_var = (n * sum_xx - sum_x * sum_x) / (n * (n - 1));
    fprintf(stderr, "measured double sided (real) noise power: %f\n", noise_var);
 
    return 0;
}
