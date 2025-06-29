/*---------------------------------------------------------------------------*\

  FILE........: freedv_data_tx.c
  AUTHOR......: Jeroen Vreeken
  DATE CREATED: May 2020

  Demo VHF packet data transmit program for FreeDV API functions.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2020 Jeroen Vreeken

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

#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <ctype.h>
#include <stdint.h>
#include <stdbool.h>

#include "freedv_api.h"
#include "codec2.h"


/**********************************************************
	Encoding an ITU callsign (and 4 bit secondary station ID to a valid MAC address.
	http://dmlinking.net/eth_ar.html
 */

// Lookup table for valid callsign characters
static char alnum2code[37] = {
	'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
	'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
	'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 
	0
};

// Encode a callsign and ssid into a valid MAC address
static int eth_ar_call2mac(uint8_t mac[6], char *callsign, int ssid, bool multicast)
{
	uint64_t add = 0;
	int i;
	
	if (ssid > 15 || ssid < 0)
		return -1;
	
	for (i = 7; i >= 0; i--) {
		char c;
		
		if (i >= strlen(callsign)) {
			c = 0;
		} else {
			c = toupper(callsign[i]);
		}
		
		int j;
		
		for (j = 0; j < sizeof(alnum2code); j++) {
			if (alnum2code[j] == c)
				break;
		}
		if (j == sizeof(alnum2code))
			return -1;
		
		add *= 37;
		add += j;
	}
	
	mac[0] = ((add >> (40 - 6)) & 0xc0) | (ssid << 2) | 0x02 | multicast;
	mac[1] = (add >> 32) & 0xff;
	mac[2] = (add >> 24) & 0xff;
	mac[3] = (add >> 16) & 0xff;
	mac[4] = (add >> 8) & 0xff;
	mac[5] = add & 0xff;

	return 0;
}


/**********************************************************
	Data channel callback functions
 */


struct my_callback_state {
    int calls;
    
    unsigned char mac[6];
};

/*
	Called when a packet has been received 
	Should not be called in this tx-only test program
 */
void my_datarx(void *callback_state, unsigned char *packet, size_t size) 
{
    /* This should not happen while sending... */
    fprintf(stderr, "datarx callback called, this should not happen!\n");    
}


/* 
	Called when a new packet can be send.
	
	callback_state	Private state variable, not touched by freedv.
	packet		Data array where new packet data is expected
	size		Available size in packet. On return the actual size of the packet
 */
void my_datatx(void *callback_state, unsigned char *packet, size_t *size) 
{
    static int data_type;
    struct my_callback_state *my_cb_state = callback_state;
    my_cb_state->calls++;
    
    /* Data could come from a network interface, here we just make up some */
    
    if (data_type % 4 == 1) {
        /* 
	    Generate a packet with simple test pattern (counting
	 */
    
        /* Send a packet with data */
        int i;

	/* Destination: broadcast */
	memset(packet, 0xff, 6);
	/* Source: our eth_ar encoded callsign+ssid */
	memcpy(packet+6, my_cb_state->mac, 6);
	/* Ether type: experimental (since this is just a test pattern) */
	packet[12] = 0x01;
	packet[13] = 0x01;

	for (i = 0; i < 64; i++)
	    packet[i + 14] = i;
        *size = i + 14;
    } else if (data_type % 4 == 2) {
        /*
	    Generate an FPRS position report
	 */
	 
	/* Destination: broadcast */
	memset(packet, 0xff, 6);
	/* Source: our eth_ar encoded callsign+ssid */
	memcpy(packet+6, my_cb_state->mac, 6);
	/* Ether type: FPRS */
	packet[12] = 0x73;
	packet[13] = 0x70;
    
        packet[14] = 0x07; // Position element Lon 86.925026 Lat 27.987850
	packet[15] = 0x3d; // 
	packet[16] = 0xd0;
	packet[17] = 0x37;
	packet[18] = 0xd0 | 0x08 | 0x01;
	packet[19] = 0x3e;
	packet[20] = 0x70;
	packet[21] = 0x85;
    
        *size = 22;
    } else {
        /* 
	   Set size to zero, the freedv api will insert a header frame 
	   This is usefull for identifying ourselves 
	 */
        *size = 0;
    }

    data_type++;
}


int main(int argc, char *argv[]) {
    FILE                     *fout;
    short                    *mod_out;
    struct freedv            *freedv;
    struct my_callback_state  my_cb_state;
    int                       mode;
    int                       n_nom_modem_samples;
    int                       i;
    int                       n_packets = 20;
    char                     *callsign = "NOCALL";
    int                       ssid = 0;
    bool                      multicast = false;

    if (argc < 3) {
        printf("usage: %s 2400A|2400B|800XA OutputModemRawFile\n"
	       " [--frames nr] [--callsign callsign] [--ssid ssid] [--mac-multicast 0|1]\n", argv[0]);
        printf("e.g    %s 2400A data_fdmdv.raw\n", argv[0]);
        exit(1);
    }

    mode = -1;
    if (!strcmp(argv[1],"2400A"))
        mode = FREEDV_MODE_2400A;
    if (!strcmp(argv[1],"2400B"))
        mode = FREEDV_MODE_2400B;
    if (!strcmp(argv[1],"800XA"))
        mode = FREEDV_MODE_800XA;
    if (mode == -1) {
        fprintf(stderr, "Error in mode: %s\n", argv[1]);
        exit(0);
    }

    if (strcmp(argv[2], "-") == 0) fout = stdout;
    else if ( (fout = fopen(argv[2],"wb")) == NULL ) {
        fprintf(stderr, "Error opening output modem sample file: %s: %s.\n", argv[3], strerror(errno));
        exit(1);
    }

    if (argc > 3) {
        for (i = 3; i < argc; i++) {
            if (strcmp(argv[i], "--packets") == 0) {
                n_packets = atoi(argv[i+1]);
            }
            if (strcmp(argv[i], "--callsign") == 0) {
                callsign = argv[i+1];
            }
            if (strcmp(argv[i], "--ssid") == 0) {
                ssid = atoi(argv[i+1]);
            }
            if (strcmp(argv[i], "--mac-multicast") == 0) {
                multicast = atoi(argv[i+1]);
            }
	}
    }

    freedv = freedv_open(mode);
    assert(freedv != NULL);

    /* Generate our address */
    eth_ar_call2mac(my_cb_state.mac, callsign, ssid, multicast);

    freedv_set_data_header(freedv, my_cb_state.mac);

    freedv_set_verbose(freedv, 1);
    
    n_nom_modem_samples = freedv_get_n_nom_modem_samples(freedv);
    mod_out = (short*)malloc(sizeof(short)*n_nom_modem_samples);
    assert(mod_out != NULL);

    /* set up callback for data packets */
    freedv_set_callback_data(freedv, my_datarx, my_datatx, &my_cb_state);

    /* OK main loop */

    /* We will loop untill the tx callback has been called n_packets times
       After that we continue untill everything is transmitted, as a data 
       packet might be transmitted in multiple freedv frames.
     */
    while (my_cb_state.calls <= n_packets || freedv_data_ntxframes(freedv)) {
        freedv_datatx(freedv, mod_out);

        fwrite(mod_out, sizeof(short), n_nom_modem_samples, fout);

        
        /* if this is in a pipeline, we probably don't want the usual
           buffering to occur */
        if (fout == stdout) fflush(stdout);
    }

    free(mod_out);
    freedv_close(freedv);
    fclose(fout);

    fclose(stdin);
    fclose(stderr);

    return 0;
}

