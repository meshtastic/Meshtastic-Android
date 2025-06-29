//==========================================================================
// Name:            varicode.h
// Purpose:         Varicode encoded and decode functions
// Created:         Nov 24, 2012
// Authors:         David Rowe
//
// To test:
//          $ gcc varicode.c -o varicode -DVARICODE_UNITTEST -Wall
//          $ ./varicode
//
// License:
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License version 2.1,
//  as published by the Free Software Foundation.  This program is
//  distributed in the hope that it will be useful, but WITHOUT ANY
//  WARRANTY; without even the implied warranty of MERCHANTABILITY or
//  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
//  License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program; if not, see <http://www.gnu.org/licenses/>.
//
//==========================================================================

#include <assert.h>
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "varicode.h"
#include "varicode_table.h"

#include "debug_alloc.h"


/*
  output is an unpacked array of bits of maximum size max_out.  Note
  unpacked arrays are a more suitable form for modulator input.

  Code 1 covers the entire ASCII char set.
*/

int varicode_encode1(short varicode_out[], char ascii_in[], int max_out, int n_in) {
    int            n_out, index, n_zeros, v_len;
    unsigned short byte1, byte2, packed;
    char           c;
    
    n_out = 0;

    while(n_in && (n_out < max_out)) {

        c = *ascii_in;
        if ((unsigned int)c >= 128) {
            c = ' ';
        }

        index = 2*(unsigned int)(c);
        assert(index <= 254);
        byte1 = varicode_table1[index];
        byte2 = varicode_table1[index+1];
        packed = (byte1 << 8) + byte2;

        //printf("n_in: %d ascii_in: %c index: %d packed 0x%x\n", n_in, *ascii_in, index, packed);
        ascii_in++;

        n_zeros = 0;
        v_len = 0;
        while ((n_zeros < 2) && (n_out < max_out) && (v_len <= VARICODE_MAX_BITS)) {
            if (packed & 0x8000) {
                *varicode_out = 1;
                n_zeros = 0;
            }
            else {
                *varicode_out = 0;
                n_zeros++;
            }
            //printf("packed: 0x%x *varicode_out: %d n_zeros: %d v_len: %d\n", packed, *varicode_out, n_zeros,v_len );
            packed <<= 1;
            varicode_out++;
            n_out++;
            v_len++;
        }
        assert(v_len <= VARICODE_MAX_BITS);

        n_in--;
    }

    return n_out;
}


/*
  Code 2 covers a subset, but is more efficient that Code 1 (282
  compared to 1315 bits on unittest) Unsupported characters are
  replaced by spaces.  We encode/decode two bits at a time.
*/

int varicode_encode2(short varicode_out[], char ascii_in[], int max_out, int n_in) {
    int            n_out, n_zeros, v_len, i;
    unsigned short packed;

    n_out = 0;

    while(n_in && (n_out < max_out)) {

        packed = varicode_table2[0]; // default to space if char not found

        // see if our character exists
        for(i=0; i<sizeof(varicode_table2); i+=2) {
            if (varicode_table2[i] == *ascii_in)
                packed = (unsigned short)varicode_table2[i+1] << 8;
        }

        //printf("n_in: %d ascii_in: %c index: %d packed 0x%x\n", n_in, *ascii_in, index, packed);
        ascii_in++;

        n_zeros = 0;
        v_len = 0;
        while ((n_zeros < 2) && (n_out < max_out) && (v_len <= VARICODE_MAX_BITS)) {
            if (packed & 0x8000)
                varicode_out[0] = 1;
            else
                varicode_out[0] = 0;

            if (packed & 0x4000)
                varicode_out[1] = 1;
            else
                varicode_out[1] = 0;

            if (packed & 0xc000)
                n_zeros = 0;
            else
                n_zeros += 2;

            //printf("packed: 0x%x *varicode_out: %d n_zeros: %d v_len: %d\n", packed, *varicode_out, n_zeros,v_len );
            packed <<= 2;
            varicode_out +=2;
            n_out += 2;
            v_len += 2;
        }
        assert(v_len <= VARICODE_MAX_BITS);

        n_in--;
    }

    assert((n_out % 2) == 0);  /* outputs two bits at a time */

    return n_out;
}

/* Code 3 simply allows the modem to pass incoming/outgoing bits to/from higher levels
   in the code. */
int varicode_encode3(short varicode_out[], char ascii_in[], int max_out, int n_in)
{
    // We only support one bit at a time.
    assert(max_out >= 1 && n_in == 1);
    
    varicode_out[0] = ascii_in[0] != 0;
    return 1;
}

int varicode_encode(short varicode_out[], char ascii_in[], int max_out, int n_in, int code_num) {

    assert((code_num == 1) || (code_num == 2) || (code_num == 3));

    if (code_num == 1)
        return varicode_encode1(varicode_out, ascii_in, max_out, n_in);
    else if (code_num == 2)
        return varicode_encode2(varicode_out, ascii_in, max_out, n_in);
    else
        return varicode_encode3(varicode_out, ascii_in, max_out, n_in);
}


void varicode_decode_init(struct VARICODE_DEC *dec_states, int code_num)
{
    assert((code_num == 1) || (code_num == 2) || (code_num == 3));

    dec_states->state = 0;
    dec_states->n_zeros = 0;
    dec_states->v_len = 0;
    dec_states->packed = 0;
    dec_states->code_num = code_num;
    dec_states->n_in = 0;
    dec_states->in[0] = dec_states->in[1] = 0;
}


void varicode_set_code_num(struct VARICODE_DEC *dec_states, int code_num)
{
    assert((code_num == 1) || (code_num == 2) || (code_num == 3));
    dec_states->code_num = code_num;
}


/* Code 1 decode function, accepts one bit at a time */

static int decode_one_bit(struct VARICODE_DEC *s, char *single_ascii, short varicode_in, int long_code)
{
    int            found=0, i;
    unsigned short byte1, byte2;

    //printf("decode_one_bit : state: %d varicode_in: %d packed: 0x%x n_zeros: %d\n",
    //       s->state, varicode_in, s->packed, s->n_zeros);

    if (s->state == 0) {
        if (!varicode_in)
            return 0;
        else
            s->state = 1;
    }

    if (s->state == 1) {
        if (varicode_in) {
            s->packed |= (0x8000 >> s->v_len);
            s->n_zeros = 0;
        }
        else {
            s->n_zeros++;
        }
        s->v_len++;
        found = 0;

        /* end of character code */

        if (s->n_zeros == 2) {
            if (s->v_len) {
                /* run thru table but note with bit errors we might not actually find a match */

                byte1 = s->packed >> 8;
                //printf("looking for byte1 : 0x%x ... ", byte1);
                byte2 = s->packed & 0xff;

                for(i=0; i<128; i++) {
                    if ((byte1 == varicode_table1[2*i]) && (byte2 == varicode_table1[2*i+1])) {
                        found = 1;
                        *single_ascii = i;
                    }
                }
            }
            varicode_decode_init(s, s->code_num);
        }

        /* code can run too long if we have a bit error */

        if (s->v_len > VARICODE_MAX_BITS)
            varicode_decode_init(s, s->code_num);
    }

    return found;
}


/* Code 2 decode function, accepts two bits at a time */

static int decode_two_bits(struct VARICODE_DEC *s, char *single_ascii, short varicode_in1, short varicode_in2)
{
    int            found=0, i;
    unsigned short byte1;

    if (s->state == 0) {
        if (!(varicode_in1 || varicode_in2))
            return 0;
        else
            s->state = 1;
    }

    if (s->state == 1) {
        if (varicode_in1)
            s->packed |= (0x8000 >> s->v_len);
        if (varicode_in2)
            s->packed |= (0x4000 >> s->v_len);
        if (varicode_in1 || varicode_in2)
            s->n_zeros = 0;
        else
            s->n_zeros+=2;

        s->v_len+=2;

        found = 0;

        /* end of character code */

        if (s->n_zeros == 2) {
            if (s->v_len) {
                /* run thru table but note with bit errors we might not actually find a match */

                byte1 = s->packed >> 8;
                //printf("looking for byte1 : 0x%x ... ", byte1);
                for(i=0; i<sizeof(varicode_table2); i+=2) {
                    //printf("byte1: 0x%x 0x%x\n", byte1, (unsigned char)varicode_table2[i+1]);
                    if (byte1 == (unsigned char)varicode_table2[i+1]) {
                        found = 1;
                        *single_ascii = varicode_table2[i];
                        //printf("found: %d i=%d char=%c ", found, i, *single_ascii);
                    }
                }
            }
            varicode_decode_init(s, s->code_num);
        }

        /* code can run too long if we have a bit error */

        if (s->v_len > VARICODE_MAX_BITS)
            varicode_decode_init(s, s->code_num);
    }

    return found;
}


int varicode_decode1(struct VARICODE_DEC *dec_states, char ascii_out[], short varicode_in[], int max_out, int n_in) {
    int            output, n_out;
    char           single_ascii = 0;

    n_out = 0;

    //printf("varicode_decode: n_in: %d\n", n_in);

    while(n_in && (n_out < max_out)) {
        output = decode_one_bit(dec_states, &single_ascii, varicode_in[0], 0);
        varicode_in++;
        n_in--;

        if (output) {
            *ascii_out++ = single_ascii;
            n_out++;
        }
    }

    return n_out;
}


int varicode_decode2(struct VARICODE_DEC *dec_states, char ascii_out[], short varicode_in[], int max_out, int n_in) {
    int            output, n_out;
    char           single_ascii = 0;

    n_out = 0;

    //printf("varicode_decode2: n_in: %d varicode_in[0] %d dec_states->n_in: %d\n", n_in, varicode_in[0], dec_states->n_in);
    //printf("%d ", varicode_in[0]);
    while(n_in && (n_out < max_out)) {

        // keep two bit buffer so we can process two at a time

        dec_states->in[0] = dec_states->in[1];
        dec_states->in[1] = varicode_in[0];
        dec_states->n_in++;
        varicode_in++;
        n_in--;

        if (dec_states->n_in == 2) {
            output = decode_two_bits(dec_states, &single_ascii, dec_states->in[0], dec_states->in[1]);

            dec_states->n_in = 0;

            if (output) {
                //printf("  output: %d single_ascii: 0x%x %c\n", output, (int)single_ascii, single_ascii);
                *ascii_out++ = single_ascii;
                n_out++;
            }
        }
    }

    return n_out;
}

int varicode_decode3(struct VARICODE_DEC *dec_states, char ascii_out[], short varicode_in[], int max_out, int n_in) 
{
    // We only handle one bit at a time.
    assert(max_out == 1 && n_in == 1);
    
    ascii_out[0] = varicode_in[0] != 0;
    return 1;
}

int varicode_decode(struct VARICODE_DEC *dec_states, char ascii_out[], short varicode_in[], int max_out, int n_in) {
    if (dec_states->code_num == 1)
        return varicode_decode1(dec_states, ascii_out, varicode_in, max_out, n_in);
    else if (dec_states->code_num == 2)
        return varicode_decode2(dec_states, ascii_out, varicode_in, max_out, n_in);
    else
        return varicode_decode3(dec_states, ascii_out, varicode_in, max_out, n_in);
}


#ifdef VARICODE_UNITTEST
void test_varicode(int code_num) {
    char *ascii_in;
    short *varicode;
    int  i, n_varicode_bits_out, n_ascii_chars_out, length, half, n_out, j, len;
    char *ascii_out;
    struct VARICODE_DEC dec_states;

    if (code_num == 1) {
        printf("long code:\n");
        length = sizeof(varicode_table1)/2;
    }
    else {
        printf("short code:\n");
        length = sizeof(varicode_table2)/2;
    }
    //length = 10;
    ascii_in = (char*)MALLOC(length);
    varicode = (short*)MALLOC(VARICODE_MAX_BITS*sizeof(short)*length);
    ascii_out = (char*)MALLOC(length);

    // 1. test all Varicode codes -------------------------------------------------------------

    if (code_num == 1) {
        for(i=0; i<length; i++)
            ascii_in[i] = (char)i;
    }
    else {
        for(i=0; i<length; i++)
            ascii_in[i] = varicode_table2[2*i];
    }
    //printf("  ascii_in: %s\n", ascii_in);
    n_varicode_bits_out = varicode_encode(varicode, ascii_in, VARICODE_MAX_BITS*length, length, code_num);

    printf("  n_varicode_bits_out: %d\n", n_varicode_bits_out);
    //for(i=0; i<n_varicode_bits_out; i++) {
    //    printf("%d \n", varicode[i]);
    //}

    // split decode in half to test how it preserves state between calls

    varicode_decode_init(&dec_states, code_num);
    half = n_varicode_bits_out/2;
    n_ascii_chars_out  = varicode_decode(&dec_states, ascii_out, varicode, length, half);
    // printf("  n_ascii_chars_out: %d\n", n_ascii_chars_out);

    n_ascii_chars_out += varicode_decode(&dec_states, &ascii_out[n_ascii_chars_out],
                                         &varicode[half], length-n_ascii_chars_out, n_varicode_bits_out - half);
    assert(n_ascii_chars_out == length);

    printf("  n_ascii_chars_out: %d\n", n_ascii_chars_out);
    printf("  average bits/character: %3.2f\n", (float)n_varicode_bits_out/n_ascii_chars_out);

    //printf("ascii_out: %s\n", ascii_out);

    if (memcmp(ascii_in, ascii_out, length) == 0)
        printf("  Test 1 Pass\n");
    else
        printf("  Test 1 Fail\n");

    // 2. Test some ascii with a run of zeros -----------------------------------------------------

    sprintf(ascii_in, "CQ CQ CQ this is VK5DGR");

    assert(strlen(ascii_in) < length);
    if (code_num == 2)
        for(i=0; i<strlen(ascii_in); i++)
            ascii_in[i] = tolower(ascii_in[i]);

    for(i=0; i<3; i++) {
        n_varicode_bits_out = varicode_encode(varicode, ascii_in, VARICODE_MAX_BITS*length, strlen(ascii_in), code_num);
        n_ascii_chars_out   = varicode_decode(&dec_states, ascii_out, varicode, length, n_varicode_bits_out);
        ascii_out[n_ascii_chars_out] = 0;

        printf("  ascii_out: %s\n", ascii_out);
        if (strcmp(ascii_in, ascii_out) == 0)
            printf("  Test 2 Pass\n");
        else
            printf("  Test 2 Fail\n");

        memset(varicode, 0, sizeof(short)*20);
        n_ascii_chars_out = varicode_decode(&dec_states, ascii_out, varicode, length, 20);
        assert(n_ascii_chars_out == 0);
    }

    // 3. Test receiving one bit at a time -----------------------------------------------------

    sprintf(ascii_in, "s=vk5dgr qth=adelaide");
    len = strlen(ascii_in);
    ascii_in[len] = 13;
    ascii_in[len+1] = 0;

    assert(strlen(ascii_in) < length);
    if (code_num == 2)
        for(i=0; i<strlen(ascii_in); i++)
            ascii_in[i] = tolower(ascii_in[i]);

    for(i=0; i<3; i++) {
        n_varicode_bits_out = varicode_encode(varicode, ascii_in, VARICODE_MAX_BITS*length, strlen(ascii_in), code_num);
        printf("n_varicode_bits_out: %d\n", n_varicode_bits_out);

        n_ascii_chars_out = 0;
        for(j=0; j<n_varicode_bits_out; j++) {
            n_out = varicode_decode(&dec_states, &ascii_out[n_ascii_chars_out], &varicode[j], 1, 1);
            if (n_out)
                n_ascii_chars_out++;
        }
        ascii_out[n_ascii_chars_out] = 0;

        printf("  ascii_out: %s\n", ascii_out);
        if (strcmp(ascii_in, ascii_out) == 0)
            printf("  Test 3 Pass\n");
        else
            printf("  Test 3 Fail\n");
    }

    FREE(ascii_in);
    FREE(ascii_out);
    FREE(varicode);
}

int main(void) {
    test_varicode(1);
    test_varicode(2);
    return 0;
}
#endif
