/* compare floats - a test utility */

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <getopt.h>
#include <math.h>
#include <errno.h>

/*  Declarations */

/* Globals */

/* Main */

int main(int argc, char *argv[]) {

    char usage[] = "Usage: %s [-t tolerance] file1 file2\n";

    float tol = .001;

    int opt;
    while ((opt = getopt(argc, argv, "t:")) != -1) {
        switch (opt) {
            case 't':
                tol = atof(optarg);
                break;
            default:
                fprintf(stderr, usage, argv[0]);
                exit(1);
            }
        }

    if ((optind + 2) > argc) {
        fprintf(stderr, usage, argv[0]);
        exit(1);
        }
    char *fname1 = argv[optind++];
    char *fname2 = argv[optind++];

    FILE *f1 = fopen(fname1, "rb");
    if (f1 == NULL) {
        fprintf(stderr, "Error opening file1 \"%s\": ", fname1);
        perror(NULL);
        exit(1);
        }

    FILE *f2 = fopen(fname2, "rb");
    if (f2 == NULL) {
        fprintf(stderr, "Error opening file2 \"%s\": ", fname2);
        perror(NULL);
        exit(1);
        }

    float data1, data2;
    int count = 0;
    int errors = 0;
    double rms_sum = 0;

    while (fread(&data1, sizeof(float), 1, f1)) {
        if (!fread(&data2, sizeof(float), 1, f2)) {
            fprintf(stderr, "Error: file2 is shorter!");
            exit(1);
            }
        float err = fabsf((data1 - data2) / data1);
        if (err > tol) {
            errors ++;
            printf("%d %g %g %g\n", count, data1, data2, err);
            }
        rms_sum += (err * err);
        count ++;
        }
    if (fread(&data2, sizeof(float), 1, f2)) {
        fprintf(stderr, "Error: file1 is shorter\n");
        exit(1);
        }

    if (errors) {
        printf("Fail: %d errors\n", errors);
        printf("      rms error = %g\n", ((double)rms_sum/count));
        exit(1);
        }
    else printf("Pass\n");
    exit(0);

    } // main


/* vi:set ts=4 et sts=4: */
