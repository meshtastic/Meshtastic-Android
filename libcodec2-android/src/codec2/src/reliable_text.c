//==========================================================================
// Name:            reliable_text.c
//
// Purpose:         Handles reliable text (e.g. text with FEC).
// Created:         August 15, 2021
// Authors:         Mooneer Salem
//
// License:
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU Lesser General Public License version 2.1,
//  as published by the Free Software Foundation.  This program is
//  distributed in the hope that it will be useful, but WITHOUT ANY
//  WARRANTY; without even the implied warranty of MERCHANTABILITY or
//  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
//  License for more details.
//
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program; if not, see <http://www.gnu.org/licenses/>.
//
//==========================================================================

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <assert.h>
#include "freedv_api.h"
#include "freedv_api_internal.h"
#include "reliable_text.h"
#include "ldpc_codes.h"
#include "ofdm_internal.h"
#include "gp_interleaver.h"

#define LDPC_TOTAL_SIZE_BITS (112)

#define RELIABLE_TEXT_UW_LENGTH_BITS (16)
#define RELIABLE_TEXT_MAX_ZEROES_IN_UW (4)

#define RELIABLE_TEXT_MAX_LENGTH (8)
#define RELIABLE_TEXT_CRC_LENGTH (1)
#define RELIABLE_TEXT_MAX_RAW_LENGTH (RELIABLE_TEXT_MAX_LENGTH + RELIABLE_TEXT_CRC_LENGTH)

/* Two bytes of text/CRC equal four bytes of LDPC(112,56). */
#define RELIABLE_TEXT_BYTES_PER_ENCODED_SEGMENT (8)

/* Internal definition of reliable_text_t. */
typedef struct 
{
    on_text_rx_t text_rx_callback;
    void* callback_state;
    
    char tx_text[LDPC_TOTAL_SIZE_BITS + RELIABLE_TEXT_UW_LENGTH_BITS];
    int tx_text_index;
    int tx_text_length;
    
    char inbound_pending_bits[RELIABLE_TEXT_UW_LENGTH_BITS + LDPC_TOTAL_SIZE_BITS];
    _Complex float inbound_pending_syms[(RELIABLE_TEXT_UW_LENGTH_BITS + LDPC_TOTAL_SIZE_BITS) / 2];
    float inbound_pending_amps[(RELIABLE_TEXT_UW_LENGTH_BITS + LDPC_TOTAL_SIZE_BITS) / 2];
    int bit_index;
    int sym_index;

    int has_successfully_decoded;
    
    struct LDPC ldpc;
    struct freedv* fdv;
} reliable_text_impl_t;

// 6 bit character set for text field use:
// 0: ASCII null
// 1-9: ASCII 38-47
// 10-19: ASCII '0'-'9'
// 20-46: ASCII 'A'-'Z'
// 47: ASCII ' '
static void convert_callsign_to_ota_string_(const char* input, char* output, int maxLength)
{
    assert(input != NULL);
    assert(output != NULL);
    assert(maxLength >= 0);
    
    int outidx = 0;
    for (size_t index = 0; index < maxLength; index++)
    {
        if (input[index] == 0) break;
        
        if (input[index] >= 38 && input[index] <= 47)
        {
            output[outidx++] = input[index] - 37;
        }
        else if (input[index] >= '0' && input[index] <= '9')
        {
            output[outidx++] = input[index] - '0' + 10;
        }
        else if (input[index] >= 'A' && input[index] <= 'Z')
        {
            output[outidx++] = input[index] - 'A' + 20;
        }
        else if (input[index] >= 'a' && input[index] <= 'z')
        {
            output[outidx++] = toupper(input[index]) - 'A' + 20;
        }
    }
    output[outidx] = 0;
}

static void convert_ota_string_to_callsign_(const char* input, char* output, int maxLength)
{
    assert(input != NULL);
    assert(output != NULL);
    assert(maxLength >= 0);
    
    int outidx = 0;
    for (size_t index = 0; index < maxLength; index++)
    {
        if (input[index] == 0) break;
        
        if (input[index] >= 1 && input[index] <= 9)
        {
            output[outidx++] = input[index] + 37;
        }
        else if (input[index] >= 10 && input[index] <= 19)
        {
            output[outidx++] = input[index] - 10 + '0';
        }
        else if (input[index] >= 20 && input[index] <= 46)
        {
            output[outidx++] = input[index] - 20 + 'A';
        }
    }
    output[outidx] = 0;
}

static char calculateCRC8_(char* input, int length)
{
    assert(input != NULL);
    assert(length >= 0);
    
    unsigned char generator = 0x1D;
    unsigned char crc = 0x00; /* start with 0 so first byte can be 'xored' in */

    while (length > 0)
    {
        unsigned char ch = *input++;
        length--;

        // Break out if we see a null.
        if (ch == 0) break;
        
        crc ^= ch; /* XOR-in the next input byte */
        
        for (int i = 0; i < 8; i++)
        {
            if ((crc & 0x80) != 0)
            {
                crc = (unsigned char)((crc << 1) ^ generator);
            }
            else
            {
                crc <<= 1;
            }
        }
    }

    return crc;
}

static int reliable_text_ldpc_decode(reliable_text_impl_t* obj, char* dest)
{
    assert(obj != NULL);
    assert(dest != NULL);
    
    char* src = &obj->inbound_pending_bits[RELIABLE_TEXT_UW_LENGTH_BITS];
    char deinterleavedBits[LDPC_TOTAL_SIZE_BITS];
    _Complex float deinterleavedSyms[LDPC_TOTAL_SIZE_BITS / 2];
    float deinterleavedAmps[LDPC_TOTAL_SIZE_BITS / 2];
    float incomingData[LDPC_TOTAL_SIZE_BITS];
    float llr[LDPC_TOTAL_SIZE_BITS];
    unsigned char output[LDPC_TOTAL_SIZE_BITS];
    int parityCheckCount = 0;
    
    if (obj->bit_index == obj->sym_index * 2)
    {
        // Use soft decision for the LDPC decoder.
        
        int Npayloadsymsperpacket = LDPC_TOTAL_SIZE_BITS / 2;
        
        // Deinterleave symbols
        gp_deinterleave_comp ((COMP*)deinterleavedSyms, (COMP*)&obj->inbound_pending_syms[RELIABLE_TEXT_UW_LENGTH_BITS/2], Npayloadsymsperpacket);
        gp_deinterleave_float(deinterleavedAmps, &obj->inbound_pending_amps[RELIABLE_TEXT_UW_LENGTH_BITS/2], Npayloadsymsperpacket);
        
        float EsNo = 3.0; // note: constant from freedv_700.c
        
        symbols_to_llrs(llr, (COMP*)deinterleavedSyms, deinterleavedAmps,
                        EsNo, obj->fdv->ofdm->mean_amp, Npayloadsymsperpacket);
    }
    else
    {
        // Deinterlace the received bits.
        gp_deinterleave_bits(deinterleavedBits, src, LDPC_TOTAL_SIZE_BITS / 2);
        
        // We don't have symbol data (likely due to incorrect mode), so we fall back
        // to hard decision.
        for (int bitIndex = 0; bitIndex < LDPC_TOTAL_SIZE_BITS; bitIndex++)
        {
            //fprintf(stderr, "rx bit %d: %d\n", bitIndex, deinterleavedBits[bitIndex]);
            
            // Map to value expected by sd_to_llr()
            incomingData[bitIndex] = 1.0 - 2.0 * deinterleavedBits[bitIndex];
        }
    
        sd_to_llr(llr, incomingData, LDPC_TOTAL_SIZE_BITS);
    }
    run_ldpc_decoder(&obj->ldpc, output, llr, &parityCheckCount);
    
    // Data is valid if BER < 0.2
    float ber_est = (float)(obj->ldpc.NumberParityBits - parityCheckCount)/obj->ldpc.NumberParityBits;
    int result = (ber_est < 0.2);
        
    //fprintf(stderr, "BER est: %f\n", ber_est);
    if (result)
    {        
        memset(dest, 0, RELIABLE_TEXT_BYTES_PER_ENCODED_SEGMENT);
        
        for (int bitIndex = 0; bitIndex < 8; bitIndex++)
        {
            if (output[bitIndex])
                dest[0] |= 1 << bitIndex;
        }
        for (int bitIndex = 8; bitIndex < (LDPC_TOTAL_SIZE_BITS / 2); bitIndex++)
        {
            int bitsSinceCrc = bitIndex - 8;
            if (output[bitIndex])
                dest[1 + (bitsSinceCrc / 6)] |= (1 << (bitsSinceCrc % 6));
        }
    }
    
    return result;
}

static void reliable_text_freedv_callback_rx_sym(void *state, _Complex float sym, float amp)
{
    reliable_text_impl_t* obj = (reliable_text_impl_t*)state;
    assert(obj != NULL);
    
    // Save the symbol. We'll use it during the bit handling below.
    obj->inbound_pending_syms[obj->sym_index] = (complex float)sym;
    obj->inbound_pending_amps[obj->sym_index++] = amp;
    
    //fprintf(stderr, "Got sym: %f, amp: %f\n", sym, amp);
}

static int check_uw(reliable_text_impl_t* obj)
{
    assert(obj != NULL);
    
    // Count number of errors in UW.
    int num_zeroes = 0;
    for (int bit = 0; bit < RELIABLE_TEXT_UW_LENGTH_BITS; bit++)
    {
        if (obj->inbound_pending_bits[bit] ^ 1)
        {
            num_zeroes++;
        }
    }
    return num_zeroes <= RELIABLE_TEXT_MAX_ZEROES_IN_UW;
}

static void reliable_text_freedv_callback_rx(void *state, char chr)
{
    //fprintf(stderr, "char: %d\n", (chr & 0x3F));
    
    reliable_text_impl_t* obj = (reliable_text_impl_t*)state;
    assert(obj != NULL);
    
    // No need to further process if we got a valid string already.
    if (obj->has_successfully_decoded)
    {
        return;
    }
    
    // Append character to the end of the symbol list.
    obj->inbound_pending_bits[obj->bit_index++] = chr;
    
    // Verify UW and data.
    if (obj->bit_index >= RELIABLE_TEXT_UW_LENGTH_BITS + LDPC_TOTAL_SIZE_BITS)
    {
        int uw_bits_valid = check_uw(obj);
    
        // Only verify data if UW is valid.
        int resync = !uw_bits_valid;
        if (uw_bits_valid)
        {
            // We have all the bits we need, so we're ready to decode.
            char decodedStr[RELIABLE_TEXT_MAX_RAW_LENGTH + 1];
            char rawStr[RELIABLE_TEXT_MAX_RAW_LENGTH + 1];
            memset(rawStr, 0, RELIABLE_TEXT_MAX_RAW_LENGTH + 1);
            memset(decodedStr, 0, RELIABLE_TEXT_MAX_RAW_LENGTH + 1);
            
            if (reliable_text_ldpc_decode(obj, rawStr) != 0)
            {
                // BER is under limits.
                convert_ota_string_to_callsign_(&rawStr[RELIABLE_TEXT_CRC_LENGTH], &decodedStr[RELIABLE_TEXT_CRC_LENGTH], RELIABLE_TEXT_MAX_LENGTH);
                decodedStr[0] = rawStr[0]; // CRC
        
                // Get expected and actual CRC.
                unsigned char receivedCRC = decodedStr[0];
                unsigned char calcCRC = calculateCRC8_(&rawStr[RELIABLE_TEXT_CRC_LENGTH], RELIABLE_TEXT_MAX_LENGTH);
    
                //fprintf(stderr, "rxCRC: %d, calcCRC: %d, decodedStr: %s\n", receivedCRC, calcCRC, &decodedStr[RELIABLE_TEXT_CRC_LENGTH]);
                if (receivedCRC == calcCRC)
                {
                    // We got a valid string. Call assigned callback.
                    obj->has_successfully_decoded = 1;
                    obj->text_rx_callback(obj, &decodedStr[RELIABLE_TEXT_CRC_LENGTH], strlen(&decodedStr[RELIABLE_TEXT_CRC_LENGTH]), obj->callback_state);
                }
                
                // Reset UW decoding for next callsign.
                obj->bit_index = 0;
                obj->sym_index = 0;
                memset(&obj->inbound_pending_syms, 0, sizeof(complex float)*LDPC_TOTAL_SIZE_BITS/2);
                memset(&obj->inbound_pending_amps, 0, sizeof(float)*LDPC_TOTAL_SIZE_BITS/2);
                memset(&obj->inbound_pending_bits, 0, LDPC_TOTAL_SIZE_BITS + RELIABLE_TEXT_UW_LENGTH_BITS);
            }
            else
            {
                // It's possible that we didn't actually sync on UW after all.
                // Shift existing UW back 1 bit (or 2 if OFDM), add the bit(s)
                // from the data portion to UW, and try again next bit(s) we receive.
                resync = 1;
            }
        }
        
        if (resync)
        {
            obj->bit_index--;
            memmove(&obj->inbound_pending_bits[0], &obj->inbound_pending_bits[1], RELIABLE_TEXT_UW_LENGTH_BITS + LDPC_TOTAL_SIZE_BITS - 1);
            if (obj->sym_index > 0)
            {
                memmove(&obj->inbound_pending_bits[0], &obj->inbound_pending_bits[1], RELIABLE_TEXT_UW_LENGTH_BITS + LDPC_TOTAL_SIZE_BITS - 1);
                memmove(&obj->inbound_pending_syms[0], &obj->inbound_pending_syms[1], sizeof(_Complex float)*((RELIABLE_TEXT_UW_LENGTH_BITS + LDPC_TOTAL_SIZE_BITS)/2 - 1));
                memmove(&obj->inbound_pending_amps[0], &obj->inbound_pending_amps[1], sizeof(float)*((RELIABLE_TEXT_UW_LENGTH_BITS + LDPC_TOTAL_SIZE_BITS)/2 - 1));
                obj->bit_index--;
                obj->sym_index--;
            }
        }
    }
}

static char reliable_text_freedv_callback_tx(void *state)
{
    reliable_text_impl_t* obj = (reliable_text_impl_t*)state;
    assert(obj != NULL);
    
    char ret = obj->tx_text[obj->tx_text_index];
    obj->tx_text_index = (obj->tx_text_index + 1) % (obj->tx_text_length);
    
    //fprintf(stderr, "char: %d\n", ret);
    return ret;
}

reliable_text_t reliable_text_create()
{
    reliable_text_impl_t* ret = calloc(1, sizeof(reliable_text_impl_t));
    assert(ret != NULL);
    
    // Load LDPC code into memory.
    int code_index = ldpc_codes_find("HRA_56_56");
    memcpy(&ret->ldpc, &ldpc_codes[code_index], sizeof(struct LDPC));
    
    return (reliable_text_t)ret;
}

void reliable_text_destroy(reliable_text_t ptr)
{
    assert(ptr != NULL);
    
    reliable_text_unlink_from_freedv(ptr);
    free(ptr);
}

void reliable_text_reset(reliable_text_t ptr)
{
    reliable_text_impl_t* impl = (reliable_text_impl_t*)ptr;
    assert(impl != NULL);
    
    impl->bit_index = 0;
    impl->sym_index = 0;
    impl->has_successfully_decoded = 0;
    memset(&impl->inbound_pending_syms, 0, sizeof(complex float)*LDPC_TOTAL_SIZE_BITS/2);
    memset(&impl->inbound_pending_amps, 0, sizeof(float)*LDPC_TOTAL_SIZE_BITS/2);
    memset(&impl->inbound_pending_bits, 0, LDPC_TOTAL_SIZE_BITS + RELIABLE_TEXT_UW_LENGTH_BITS);
}

void reliable_text_set_string(reliable_text_t ptr, const char* str, int strlength)
{
    reliable_text_impl_t* impl = (reliable_text_impl_t*)ptr;
    assert(impl != NULL);
    
    char tmp[RELIABLE_TEXT_MAX_RAW_LENGTH + 1];
    memset(tmp, 0, RELIABLE_TEXT_MAX_RAW_LENGTH + 1);
    
    convert_callsign_to_ota_string_(str, &tmp[RELIABLE_TEXT_CRC_LENGTH], strlength < RELIABLE_TEXT_MAX_LENGTH ? strlength : RELIABLE_TEXT_MAX_LENGTH);
    
    int txt_length = strlen(&tmp[RELIABLE_TEXT_CRC_LENGTH]);
    if (txt_length >= RELIABLE_TEXT_MAX_LENGTH)
    {
        txt_length = RELIABLE_TEXT_MAX_LENGTH;
    }
    impl->tx_text_length = RELIABLE_TEXT_UW_LENGTH_BITS + LDPC_TOTAL_SIZE_BITS;
    impl->tx_text_index = 0;
    unsigned char crc = calculateCRC8_(&tmp[RELIABLE_TEXT_CRC_LENGTH], txt_length);
    tmp[0] = crc;
    
    // Encode block of text using LDPC(112,56).
    unsigned char ibits[LDPC_TOTAL_SIZE_BITS / 2];
    unsigned char pbits[LDPC_TOTAL_SIZE_BITS / 2];
    memset(ibits, 0, LDPC_TOTAL_SIZE_BITS / 2);
    memset(pbits, 0, LDPC_TOTAL_SIZE_BITS / 2);
    for (int index = 0; index < 8; index++)
    {
        if (tmp[0] & (1 << index)) ibits[index] = 1;
    }

    // Pack 6 bit characters into single LDPC block.
    for (int ibitsBitIndex = 8; ibitsBitIndex < (LDPC_TOTAL_SIZE_BITS / 2); ibitsBitIndex++)
    {
        int bitsFromCrc = ibitsBitIndex - 8;
        unsigned int byte = tmp[RELIABLE_TEXT_CRC_LENGTH + bitsFromCrc / 6];
        unsigned int bitToCheck = bitsFromCrc % 6;
        //fprintf(stderr, "bit index: %d, byte: %x, bit to check: %d, result: %d\n", ibitsBitIndex, byte, bitToCheck, (byte & (1 << bitToCheck)) != 0);
        
        if (byte & (1 << bitToCheck))
        {
            ibits[ibitsBitIndex] = 1;
        }
    }
    
    encode(&impl->ldpc, ibits, pbits);  
    
    // Split LDPC encoded bits into individual bits, with the first RELIABLE_TEXT_UW_LENGTH_BITS being UW.
    char tmpbits[LDPC_TOTAL_SIZE_BITS];
    
    memset(impl->tx_text, 1, RELIABLE_TEXT_UW_LENGTH_BITS);
    memset(impl->tx_text + RELIABLE_TEXT_UW_LENGTH_BITS, 0, LDPC_TOTAL_SIZE_BITS);
    memcpy(&tmpbits[0], &ibits[0], LDPC_TOTAL_SIZE_BITS / 2);
    memcpy(&tmpbits[LDPC_TOTAL_SIZE_BITS / 2], &pbits[0], LDPC_TOTAL_SIZE_BITS / 2);
    
    // Interleave the bits together to enhance fading performance.
    gp_interleave_bits(&impl->tx_text[RELIABLE_TEXT_UW_LENGTH_BITS], tmpbits, LDPC_TOTAL_SIZE_BITS / 2);
}

void reliable_text_use_with_freedv(reliable_text_t ptr, struct freedv* fdv, on_text_rx_t text_rx_fn, void* state)
{
    reliable_text_impl_t* impl = (reliable_text_impl_t*)ptr;
    assert(impl != NULL);
    
    impl->callback_state = state;
    impl->text_rx_callback = text_rx_fn;
    impl->fdv = fdv;
    freedv_set_callback_txt(fdv, reliable_text_freedv_callback_rx, reliable_text_freedv_callback_tx, impl);
    freedv_set_callback_txt_sym(fdv, reliable_text_freedv_callback_rx_sym, impl);
    
    // Use code 3 for varicode en/decode and handle all framing at this level.
    varicode_set_code_num(&fdv->varicode_dec_states, 3);
}

struct freedv* reliable_text_get_freedv_obj(reliable_text_t ptr)
{
    reliable_text_impl_t* impl = (reliable_text_impl_t*)ptr;
    assert(impl != NULL);

    return impl->fdv;
}

void reliable_text_unlink_from_freedv(reliable_text_t ptr)
{
    reliable_text_impl_t* impl = (reliable_text_impl_t*)ptr;
    assert(impl != NULL);
    
    if (impl->fdv)
    {
        freedv_set_callback_txt(impl->fdv, NULL, NULL, NULL);
        freedv_set_callback_txt_sym(impl->fdv, NULL, NULL);
        varicode_set_code_num(&impl->fdv->varicode_dec_states, 1);
        impl->fdv = NULL;
    }
}
