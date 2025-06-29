/*---------------------------------------------------------------------------*\

  FILE........: tfreedv_data_channel
  AUTHOR......: Jeroen Vreeken
  DATE CREATED: May 3 2016

  Tests for the data channel code.
  Data channel frame behaviour is tested with test vectors.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2016 Jeroen Vreeken

  All rights reserved.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License version 2.1, as
  published by the Free Software Foundation.  This program is
  distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, see <http://www.gnu.org/licenses/>.
*/

#include "freedv_data_channel.h"

#include <stdio.h>
#include <string.h>

unsigned char test_header[] = { 0x11, 0x22, 0x33, 0x44, 0x55, 0x66 };
unsigned char bcast_header[] = { 0xff, 0xff, 0xff, 0xff, 0xff, 0xff };


struct testvec {
    char *testname;
    
    unsigned char *data;
    size_t data_size;
    
    size_t frame_size;
    
    unsigned char *frame_data;
    size_t frame_data_size;

    unsigned char *flags;
} testvec[] = {
    {
        "Regular packet, does not match header and no broadcast",
        (unsigned char[]){ 
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11, 0x12
        },
        0x12,
        8,
        (unsigned char[]){ 
            0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x01, 0x02,
            0x03, 0x04, 0x05, 0x06, 0x0d, 0x0e, 0x0f, 0x10,
            0x11, 0x12, 0x47, 0x6e
        },
        0x14,
        (unsigned char[]){ 0x00, 0x00, 0x04 },
    },
    {
        "Header",
        NULL,
        0,
        8,
        (unsigned char[]){ 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x5a, 0x60 },
        0x08,
        (unsigned char[]){ 0x08 },
    },
    {
        "Broadcast packet",
        (unsigned char[]){ 
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x11, 0x22,
            0x33, 0x44, 0x55, 0x66, 0x05, 0x06, 0x07, 0x08, 
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11
        },
        0x19,
        8,
        (unsigned char[]){ 
            0x05, 0x06, 0x07, 0x08, 
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11, 0x3c, 0xbe
        },
        0x0f,
        (unsigned char[]){ 0xc0, 0x07 },
    },
    {
        "Broadcast packet, header does not match",
        (unsigned char[]){ 
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xaa, 0x22,
            0xbb, 0xcc, 0xdd, 0xee, 0x05, 0x06, 0x07, 0x08, 
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11
        },
        0x19,
        8,
        (unsigned char[]){ 
            0xaa, 0x22,
            0xbb, 0xcc, 0xdd, 0xee, 0x05, 0x06, 0x07, 0x08, 
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11, 0x1a, 0x68
        },
        0x15,
        (unsigned char[]){ 0x40, 0x00, 0x05 },
    },
    {
        "Header 6 bytes",
        NULL,
        0,
        6,
        (unsigned char[]){ 0x11, 0x22, 0x33, 0x44, 0x55, 0x66 },
        0x06,
        (unsigned char[]){ 0x2f },
    },
    {
        "Broadcast packet (6 byte frames)",
        (unsigned char[]){ 
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x11, 0x22,
            0x33, 0x44, 0x55, 0x66, 0x05, 0x06, 0x07, 0x08, 
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11
        },
        0x19,
        6,
        (unsigned char[]){ 
            0x05, 0x06, 0x07, 0x08, 
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11, 0x3c, 0xbe
        },
        0x0f,
        (unsigned char[]){ 0xc0, 0x00, 0x03 },
    },
    {
        "Broadcast packet, header does not match (6 byte frames)",
        (unsigned char[]){ 
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xaa, 0x22,
            0xbb, 0xcc, 0xdd, 0xee, 0x05, 0x06, 0x07, 0x08, 
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11
        },
        0x19,
        6,
        (unsigned char[]){ 
            0xaa, 0x22,
            0xbb, 0xcc, 0xdd, 0xee, 0x05, 0x06, 0x07, 0x08, 
            0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10,
            0x11, 0x1a, 0x68
        },
        0x15,
        (unsigned char[]){ 0x40, 0x00, 0x00, 0x03 },
    },
};


static int ret = 0;
static int vector = 0;
static size_t frame_data_pos = 0;
static int rx_done = 0;

void *tx_cb_arg = (void*)0xaa55;
void *rx_cb_arg = (void*)0xbb44;

void tfreedv_data_callback_tx(void *arg, unsigned char *packet, size_t *size)
{
    if (tx_cb_arg != arg) {
        ret++;
        printf("FAIL: %s called with wrong argument value\n", __func__);
    }
    printf("--------------------------------------\n");
    printf("TX callback called for test %zd bytes data for test %d:\n'%s'\n",
        testvec[vector].data_size, vector,
        testvec[vector].testname);

    memcpy(packet, testvec[vector].data, testvec[vector].data_size);
    *size = testvec[vector].data_size;
    
    return;
}

void tfreedv_data_callback_rx(void *arg, unsigned char *packet, size_t size)
{
    if (rx_cb_arg != arg) {
        ret++;
        printf("FAIL: %s called with wrong argument value\n", __func__);
    }
    printf("RX callback called with %zd bytes\n", size);
    
    if (testvec[vector].data_size) {
        size_t data_size = testvec[vector].data_size;
        if (data_size != size) {
            printf("FAIL: Received size does not match test vector: %zd != %zd\n",
                size, data_size);
            ret++;
        } else {
            size_t i;
            for (i = 0; i < data_size; i++) {
                if (packet[i] != testvec[vector].data[i]) {
                    printf("FAIL: byte %zd does not match 0x%02x != 0x%02x\n",
                        i, packet[i], testvec[vector].data[i]);
                    ret++;
                }
            }
        }
    } else {
        if (size != 12) {
            printf("FAIL: Received header is not 12 bytes: %zd\n", size);
            ret++;
        } else {
            if (memcmp(packet, bcast_header, 6)) {
                printf("FAIL: Header is not a broadcast!\n");
                ret++;
	    }
            if (memcmp(packet+6, test_header, 6)) {
                printf("FAIL: Header does not match!\n");
                ret++;
            }
        }
    }
    
    rx_done = 1;
}

int main(int argc, char **argv)
{
    struct freedv_data_channel *fdc;
    
    fdc = freedv_data_channel_create();

    freedv_data_set_header(fdc, test_header);
    freedv_data_set_cb_tx(fdc, tfreedv_data_callback_tx, tx_cb_arg);
    freedv_data_set_cb_rx(fdc, tfreedv_data_callback_rx, rx_cb_arg);

    while (vector < sizeof(testvec)/sizeof(struct testvec)) {
        size_t frame_size = testvec[vector].frame_size;
        unsigned char frame[frame_size];
        int from, bcast, crc, end;
        int i;
        size_t check_size;
        unsigned char flags;
        int nr_frames;
	
        freedv_data_channel_tx_frame(fdc, frame, frame_size, &from, &bcast, &crc, &end);

        check_size = frame_size;
        if (frame_data_pos + check_size > testvec[vector].frame_data_size)
            check_size = testvec[vector].frame_data_size - frame_data_pos;
        
        flags = from * 0x80 + bcast * 0x40 + crc * 0x20 + end;
        printf("0x%02x:", flags);
        for (i = 0; i < check_size; i++) {
            if (frame[i] != testvec[vector].frame_data[frame_data_pos + i]) {
                printf(" [0x%02x!=0x%02x]", 
                    frame[i], testvec[vector].frame_data[frame_data_pos + i]);
                ret++;
            } else {
                printf(" 0x%02x", frame[i]);
            }
        }
        printf("\n");
        
        if (flags != testvec[vector].flags[frame_data_pos / frame_size]) {
            printf("FAIL: Flags byte does not match 0x%02x != 0x%02x\n",
                flags, testvec[vector].flags[frame_data_pos / frame_size]);
            ret++;
        }

        freedv_data_channel_rx_frame(fdc, frame, frame_size, from, bcast, crc, end);

        frame_data_pos += frame_size;

        nr_frames = freedv_data_get_n_tx_frames(fdc, frame_size);

        if (frame_data_pos >= testvec[vector].frame_data_size) {
    	    if (nr_frames) {
    	        printf("FAIL: nr_frames is not zero: %d\n", nr_frames);
    	    	ret++;
    	    }
            vector++;
            frame_data_pos = 0;
            if (!rx_done) {
                printf("FAIL: RX callback not executed\n");
                ret++;
            }
            rx_done = 0;
        } else {
            int vec_frames = (testvec[vector].frame_data_size - frame_data_pos);
            vec_frames /= frame_size;
            vec_frames++;
            if (nr_frames != vec_frames) {
                printf("FAIL: nr_frames != vec_frames: %d != %d\n", nr_frames, vec_frames);
                ret++;
            }
        }
    }

    freedv_data_channel_destroy(fdc);

    printf("--------------------------------------\n");
    printf("tfreedv_data_channel test result: ");
    if (ret) {
        printf("Failed %d\n", ret);
    } else {
        printf("Passed\n");
    }

    return ret;
}
