/*---------------------------------------------------------------------------*\

  FILE........: golay23.c
  AUTHOR......: Tomas Härdin & David Rowe
  DATE CREATED: 3 March 2013

  To test:

     src$ gcc golay23.c -o golay23 -Wall -O3 -DGOLAY23_UNITTEST                   && ./golay23
     src$ gcc golay23.c -o golay23 -Wall -O3 -DGOLAY23_UNITTEST -DRUN_TIME_TABLES && ./golay23
     src$ gcc golay23.c -o golay23 -Wall -O3 -DGOLAY23_UNITTEST -DNO_TABLES       && ./golay23

  To generate tables:
     src$ gcc golay23.c -o golay23 -Wall -O3 -DGOLAY23_MAKETABLES                 && ./golay23

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2016 Tomas Härdin & David Rowe

  All rights reserved.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License version 2.1, as
  published by the Free Software Foundation.  This program is
  distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not,see <http://www.gnu.org/licenses/>.
*/

#include <assert.h>

#ifdef GOLAY23_MAKETABLES
#define RUN_TIME_TABLES
#endif

#ifndef NO_TABLES
#ifdef RUN_TIME_TABLES
int static encoding_table[4096];
int static decoding_table[2048];
static int inited = 0;
#else
//default is to use precomputed tables
#include "golayenctable.h"
#include "golaydectable.h"
#endif
#endif

//since we want to avoid bit-reversing inside syndrome() we bit-reverse the polynomial instead
#define GOLAY_POLYNOMIAL    0xC75   //AE3 reversed

int golay23_syndrome(int c) {
    //could probably be done slightly smarter, but works
    int x;
    for (x = 11; x >= 0; x--) {
        if (c & ((1<<11) << x)) {
            c ^= GOLAY_POLYNOMIAL << x;
        }
    }
    return c;
}

#ifdef __GNUC__
#define popcount __builtin_popcount
#elif defined(__MSC_VER)
#include <intrin.h>
#define popcount __popcnt
#else
static int popcount(unsigned int c) {
    int ret = 0;
    while (c) {
        if (c & 1) {
            ret++;
        }
        c >>= 1;
    }
    return ret;
}
#endif

#if defined(NO_TABLES) || defined(RUN_TIME_TABLES)
static int golay23_encode_no_tables(int c) {
    c <<= 11;
    return golay23_syndrome(c) | c;
}
#endif

#ifdef NO_TABLES
static int unrotate(unsigned int c, int x) {
    return ((c << x) & 0x7FFFFF) | (c >> (23 - x));
}

static int golay23_decode_no_tables(int c) {
    //TODO: optimize?
    int x;
    c = unrotate(c, 12);

    for (x = 0; x < 23; x++) {
        int t;
        int s = golay23_syndrome(c);

        if (popcount(s) <= 3) {
            return unrotate(c ^ s, x) & 0xFFF;
        }

        for (t = 0; t < 23; t++) {
            int c2 = c ^ (1 << t);
            int s = golay23_syndrome(c2);

            if (popcount(s) <= 2) {
                return unrotate(c2 ^ s, x) & 0xFFF;
            }
        }

        //rotate
        c = (c >> 1) | ((c & 1) << 22);
    }

    //shouldn't reach here..
    assert("Something is wrong with golay23_decode_no_tables()..");
    return c & 0xFFF;
}
#endif

void golay23_init(void) {
#ifdef RUN_TIME_TABLES
    int x, y, z;
    inited = 1;
    for (x = 0; x < 4096; x++) {
        encoding_table[x] = golay23_encode_no_tables(x);
    }

    decoding_table[0] = 0;
    //1-bit errors
    for (x = 0; x < 23; x++) {
        int d = 1<<x;
        decoding_table[golay23_syndrome(d)] = d;
    }
    //2-bit errors
    for (x = 0; x < 22; x++) {
        for (y = x+1; y < 23; y++) {
            int d = (1<<x) | (1<<y);
            decoding_table[golay23_syndrome(d)] = d;
        }
    }
    //3-bit errors
    for (x = 0; x < 21; x++) {
        for (y = x+1; y < 22; y++) {
            for (z = y+1; z < 23; z++) {
                int d = (1<<x) | (1<<y) | (1<<z);
                decoding_table[golay23_syndrome(d)] = d;
            }
        }
    }
#endif
}

int  golay23_encode(int c) {
    assert(c >= 0 && c <= 0xFFF);
#ifdef RUN_TIME_TABLES
    assert(inited);
#endif

#ifdef NO_TABLES
    return golay23_encode_no_tables(c);
#else
    return encoding_table[c];
#endif
}

int  golay23_decode(int c) {
    assert(c >= 0 && c <= 0x7FFFFF);
#ifdef RUN_TIME_TABLES
    assert(inited);
#endif

#ifdef NO_TABLES
    //duplicate old golay23_decode()'s shift
    return unrotate(golay23_decode_no_tables(c), 11);
#else
    //message is shifted 11 places left in the return value
    return c ^ decoding_table[golay23_syndrome(c)];
#endif
}

int  golay23_count_errors(int recd_codeword, int corrected_codeword) {
    return popcount(recd_codeword ^ corrected_codeword);
}

/**
 * Table generation and testing code below
 */

#ifdef GOLAY23_MAKETABLES
#include <stdio.h>

int main() {
    int x;
    //generate and dump
    golay23_init();

    FILE *enc = fopen("golayenctable.h", "w");
    FILE *dec = fopen("golaydectable.h", "w");

    fprintf(enc, "/* Generated by golay23.c -DGOLAY23_MAKETABLE */\n\
\n\
const int static encoding_table[]={\n");
    for (x = 0; x < 4096; x++) {
        fprintf(enc, x < 4095 ? "  0x%x,\n" : "  0x%x\n", encoding_table[x]);
    }
    fprintf(enc, "};\n");

    fprintf(dec, "/* Generated by golay23.c -DGOLAY23_MAKETABLE */\n\
\n\
const int static decoding_table[]={\n");
    for (x = 0; x < 2048; x++) {
        fprintf(dec, x < 2047 ? "  0x%x,\n" : "  0x%x\n", decoding_table[x]);
    }
    fprintf(dec, "};\n");

    fclose(enc);
    fclose(dec);

    return 0;
}

#elif defined(GOLAY23_UNITTEST)
#include <stdio.h>
#include <stdlib.h>
#include <memory.h>

int main() {
    int c;

    golay23_init();

    //keep track of whether every single codeword has been checked
    char *checkmask = malloc(1<<23);
    memset(checkmask, 0, 1<<23);

    //step through all possible messages
    for (c = 0; c < (1<<12); c++) {
        int g23 = golay23_encode(c);
        int x,y,z;
        checkmask[g23] = 1;
        int c2 = golay23_decode(g23) >> 11;

        printf("%03x -> %06x %03x\n", c, g23, c2);

        if (c != c2) {
            printf("Bad!\n");
            exit(1);
        }

        //test the code by flipping every combination of one, two and three bits
        for (x = 0; x < 23; x++) {
            int flipped = g23 ^ (1<<x);
            checkmask[flipped] = 1;
            int c2 = golay23_decode(flipped) >> 11;
            if (c != c2) {
                printf("Bad!\n");
                
                exit(1);
            }
        }
        
        for (x = 0; x < 22; x++) {
            for (y = x+1; y < 23; y++) {
                int flipped = g23 ^ (1<<x) ^ (1<<y);
                checkmask[flipped] = 1;
                int c2 = golay23_decode(flipped) >> 11;
                if (c != c2) {
                    printf("Bad!\n");
                    
                    exit(1);
                }
            }
        }
        
        for (x = 0; x < 21; x++) {
            for (y = x+1; y < 22; y++) {
                for (z = y+1; z < 23; z++) {
                    int flipped = g23 ^ (1<<x) ^ (1<<y) ^ (1<<z);
                    checkmask[flipped] = 1;
                    int c2 = golay23_decode(flipped) >> 11;
                    if (c != c2) {
                        printf("Bad!\n");
                        exit(1);
                    }
                }
            }
        }
    }

    //did we check every codeword?
    for (c = 0; c < (1<<23); c++) {
        if (checkmask[c] != 1) {
            printf("%06x unchecked!\n", c);
            exit(1);
        }
    }

    printf("Everything checks out\n");
    free(checkmask);
    return 0;
}
#endif
