#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <errno.h>

#include "semihosting.h"

#include "stm32f4xx_conf.h"
#include "stm32f4xx.h"
#include "machdep.h"

#define min(a, b) ((a < b) ? (a) : (b))

int main(int argc, char *argv[]) {

    semihosting_init();

    printf("semihosting test - stdout\n");
    fprintf(stderr, "semihosting test - stderr\n");

    uint8_t buf[128];
    int count;
    int i;

    FILE *fin = fopen("stm_in.raw", "rb");
    if (!fin) {
        fprintf(stderr, "Error %d opening fin\n", errno);
    }
    setbuf(fin, NULL);

    FILE *fout = fopen("stm_out.raw", "wb");
    if (!fout) {
        fprintf(stderr, "Error %d opening fout\n", errno);
    }
    setbuf(fout, NULL);

    // Unrolled while loop for simpler debugging:
    // Pass 0: expect 16 bytes 00-0f
    printf("Pass 0: feof(fin) = %d\n", feof(fin));
    count = fread(&buf[0], 1, 16, fin);
    printf("read %d bytes: ", count);
    for (i=0; i<count; i++) printf(" %02x", buf[i]);
    printf("\nfeof(fin) = %d\n", feof(fin));
    for (i=0; i<min(count, 16); i++) buf[i] = ~buf[i];
    if (count) count = fwrite(&buf[0], 1, count, fout);
    printf("Wrote %d bytes\n\n", count);

    // Pass 1: expect 16 bytes 10-1f
    printf("Pass 1: feof(fin) = %d\n", feof(fin));
    count = fread(&buf[0], 1, 16, fin);
    printf("read %d bytes: ", count);
    for (i=0; i<count; i++) printf(" %02x", buf[i]);
    printf("\nfeof(fin) = %d\n", feof(fin));
    for (i=0; i<min(count, 16); i++) buf[i] = ~buf[i];
    if (count) count = fwrite(&buf[0], 1, count, fout);
    printf("Wrote %d bytes\n\n", count);

    // Pass 2: expect 3 bytes 20-22
    printf("Pass 2: feof(fin) = %d\n", feof(fin));
    count = fread(&buf[0], 1, 16, fin);
    printf("read %d bytes: ", count);
    for (i=0; i<count; i++) printf(" %02x", buf[i]);
    printf("\nfeof(fin) = %d\n", feof(fin));
    for (i=0; i<min(count, 16); i++) buf[i] = ~buf[i];
    if (count) count = fwrite(&buf[0], 1, count, fout);
    printf("Wrote %d bytes\n\n", count);
    
    // Pass 3: expect 0 result (EOF)
    printf("Pass 3: feof(fin) = %d\n", feof(fin));
    count = fread(&buf[0], 1, 16, fin);
    printf("read %d bytes: ", count);
    for (i=0; i<count; i++) printf(" %02x", buf[i]);
    printf("\nfeof(fin) = %d\n", feof(fin));
    for (i=0; i<min(count, 16); i++) buf[i] = ~buf[i];
    if (count) count = fwrite(&buf[0], 1, count, fout);
    printf("Wrote %d bytes\n\n", count);
    

    fclose(fin);
    fclose(fout);

    printf("End of test\n");
    fflush(stdout);
    fflush(stderr);
    
    return 0;
}

/* vi:set ts=4 et sts=4: */
