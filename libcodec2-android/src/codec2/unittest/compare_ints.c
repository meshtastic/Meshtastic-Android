/* compare ints - a test utility */

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <getopt.h>
#include <errno.h>
#include <inttypes.h>

/*  Declarations */

/* Globals */

/* Functions */
int get_data(FILE *f, int64_t *dd, int signed_flag, int bytes) {
    int res;
    int8_t   d_8;
    int16_t  d_16;
    uint8_t  d_u8;
    uint16_t d_u16;
    // TODO Loop on reads until, but catch EOF!!
    if (signed_flag) {
        switch (bytes) {
            case 1: 
                res = fread(&d_8, bytes, 1, f);
                *dd = d_8;
                break;
            case 2: 
                res = fread(&d_16, bytes, 1, f);
                *dd = d_16;
                break;
            default: 
                fprintf(stderr, "Error: unsupported size %d bytes\n", bytes);
                exit(1);
            }
        } 
    else {  // unsigned
        switch (bytes) {
            case 1: 
                res = fread(&d_u8, bytes, 1, f);
                *dd = d_u8;
                break;
            case 2: 
                res = fread(&d_u16, bytes, 1, f);
                *dd = d_u16;
                break;
            default: 
                fprintf(stderr, "Error: unsupported size %d bytes\n", bytes);
                exit(1);
            }
        }

    if (res != 1) return(0);
    else return(1);
    }


/* Main */

int main(int argc, char *argv[]) {

    char usage[] = "Usage: %s [-b size_in_bytes] [-c] [-s] [-t tolerance] [-n numerrorstoexit] file1 file2\n";

    int bytes = 1;
    int count_errors = 0;
    int signed_flag = 0;
    int tol = 1;
    int numerrorstoexit = -1;
    
    int opt;
    while ((opt = getopt(argc, argv, "b:cst:n:")) != -1) {
        switch (opt) {
            case 'b':
                bytes = atoi(optarg);
                break;
            case 'c':
                count_errors = 1;
                break;
            case 's':
                signed_flag = 1;
                break;
            case 'n':
                numerrorstoexit = atoi(optarg);
                break;
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

    // Convert inputs to SIGNED long values 
    int64_t data1, data2;

    int count = 0;
    int errors = 0;
    int rms_sum = 0;

    while (get_data(f1, &data1, signed_flag, bytes)) {
        if (!get_data(f2, &data2, signed_flag, bytes)) {
            fprintf(stderr, "Error: file2 is shorter\n");
            exit(1);
        }
        uint64_t err = llabs(data1 - data2);
        if (err > tol) {
            errors ++;
            printf("%d %" PRId64 " %" PRId64 "\n", count, data1, data2);
	    if (numerrorstoexit != -1)
	        if (errors > numerrorstoexit) {
		    printf("reached errors: %d, bailing!", numerrorstoexit);
		    exit(1);
		}
        }
        rms_sum += (err * err);
        count ++;
    }
    if (get_data(f2, &data2, signed_flag, bytes)) {
        fprintf(stderr, "Error: file1 is shorter\n");
        exit(1);
    }

    if (count_errors) exit(errors);
    else {
        if (errors) {
            printf("Fail: %d errors\n", errors);
            printf("      rms error = %f\n", ((double)rms_sum/count));
            exit(1);
            }
        else printf("Pass\n");
        exit(0);
        }

    } // main


/* vi:set ts=4 et sts=4: */
