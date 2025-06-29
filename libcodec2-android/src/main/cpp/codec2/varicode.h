//==========================================================================
// Name:            varicode.h
// Purpose:         Varicode encoded and decode functions
// Created:         Nov 24, 2012
// Authors:         David Rowe
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

#ifndef __VARICODE__
#define __VARICODE__

#ifdef __cplusplus
extern "C" {

#endif

#define VARICODE_MAX_BITS (10+2) /* max varicode bits for each ascii character */
                                 /* 10 bits for code plus 2 0 bits for inter-character space */

struct VARICODE_DEC {
    int            state;
    int            n_zeros;
    int            v_len;
    unsigned short packed;
    int            code_num;
    int            n_in;
    int            in[2];
};

int varicode_encode(short varicode_out[], char ascii_in[], int max_out, int n_in, int code_num);
void varicode_decode_init(struct VARICODE_DEC *dec_states, int code_num);
int varicode_decode(struct VARICODE_DEC *dec_states, char ascii_out[], short varicode_in[], int max_out, int n_in);
void varicode_set_code_num(struct VARICODE_DEC *dec_states, int code_num);

#ifdef __cplusplus
}
#endif

#endif
