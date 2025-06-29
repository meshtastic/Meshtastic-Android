/*---------------------------------------------------------------------------*\

  FILE........: freedv_data_rx.c
  AUTHOR......: Jeroen Vreeken
  DATE CREATED: May 2020

  Demo receive program for FreeDV API functions ignores everything but
  VHF packet data.

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
#include <stdint.h>
#include <stdbool.h>

#include "freedv_api.h"

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

// Decode the callsign in a MAC address
static int eth_ar_mac2call(char *callsign, int *ssid, bool *multicast, uint8_t mac[6])
{
	uint64_t add;
	int i;

	if (!memcmp(mac, (uint8_t[6]){ 0xff, 0xff, 0xff, 0xff, 0xff, 0xff}, 6)) {
		*ssid = 0;
		*multicast = true;
		strcpy(callsign, "*");
		return 0;
	}
	*multicast = mac[0] & 0x01;
	*ssid = (mac[0] & 0x3c) >> 2;
	add = (uint64_t)(mac[0] & 0xc0) << (40 - 6);
	add |= (uint64_t)mac[1] << 32;
	add |= (uint64_t)mac[2] << 24;
	add |= (uint64_t)mac[3] << 16;
	add |= (uint64_t)mac[4] << 8;
	add |= (uint64_t)mac[5];

	for (i = 0; i < 8; i++) {
		int c = add % 37;
		callsign[i] = alnum2code[c];
		add /= 37;
	}
	callsign[i] = 0;

	return 0;
}



/**********************************************************
	Data channel callback functions
 */


struct my_callback_state {
    FILE *fdataout;
    int calls;
};

/* Called when a packet has been received */
void my_datarx(void *callback_state, unsigned char *packet, size_t size) 
{
    struct my_callback_state* pstate = (struct my_callback_state*)callback_state;
    pstate->calls++;
    fprintf(pstate->fdataout, "%-4d", pstate->calls);

    if (pstate->fdataout != NULL) {
        size_t i;
	
	char callsign_to[9], callsign_from[9];
	int ssid_to, ssid_from;
	bool multicast_to, multicast_from;
	eth_ar_mac2call(callsign_to, &ssid_to, &multicast_to, packet);
	eth_ar_mac2call(callsign_from, &ssid_from, &multicast_from, packet + 6);
	
	if (multicast_from)
	    fprintf(pstate->fdataout, "Multicast");
	else
	    fprintf(pstate->fdataout, "%s-%d", callsign_from, ssid_from);

        printf(" -> ");

	if (multicast_to)
	    fprintf(pstate->fdataout, "Multicast");
	else
	    fprintf(pstate->fdataout, "%s-%d", callsign_to, ssid_to);
	
	printf(" (%zd bytes)", size);
	
	/* It could be just an identification frame */
	if (size < 14) {
	    fprintf(pstate->fdataout, " Identification");
	} else {
	    unsigned short ethertype = packet[12] << 8 | packet[13];
	    fprintf(pstate->fdataout, " EtherType 0x%04x", ethertype);
	    
	    if (ethertype == 0x7370)
	        fprintf(pstate->fdataout, " (FPRS)");
        }
        
	fprintf(pstate->fdataout, ":");

	for (i = 0; i < size; i++) {
	    if (i % 0x10 == 0)
	        fprintf(pstate->fdataout, "\n0x%04zx: ", i);
	    fprintf(pstate->fdataout, "0x%02x ", packet[i]);
	}
       	fprintf(pstate->fdataout, "\n");
    }
}

/* Called when a new packet can be send */
void my_datatx(void *callback_state, unsigned char *packet, size_t *size) {
    /* This should not happen while receiving.. */
    fprintf(stderr, "datarx callback called, this should not happen!\n");    
    *size = 0;
}


int main(int argc, char *argv[]) {
    FILE                      *fin;
    struct freedv             *freedv;
    int                        nin, frame = 0;
    struct my_callback_state   my_cb_state = {0};
    int                        mode;
    int                        verbose;
    int                        i;

    
    if (argc < 3) {
	printf("usage: %s 2400A|2400B|800XA InputModemSpeechFile\n"
               " \n", argv[0]);
	printf("e.g    %s 2400A data_fdmdv.raw\n", argv[0]);
	exit(1);
    }

    my_cb_state.fdataout = stdout;

    mode = -1;
    if (!strcmp(argv[1],"2400A"))
        mode = FREEDV_MODE_2400A;
    if (!strcmp(argv[1],"2400B"))
        mode = FREEDV_MODE_2400B;
    if (!strcmp(argv[1],"800XA"))
        mode = FREEDV_MODE_800XA;
    assert(mode != -1);

    if (strcmp(argv[2], "-")  == 0) fin = stdin;
    else if ( (fin = fopen(argv[2],"rb")) == NULL ) {
	fprintf(stderr, "Error opening input raw modem sample file: %s: %s.\n",
         argv[2], strerror(errno));
	exit(1);
    }

    verbose = 0;
    
    if (argc > 3) {
        for (i = 3; i < argc; i++) {
            if (strcmp(argv[i], "-v") == 0) {
                verbose = 1;
            }
            if (strcmp(argv[i], "-vv") == 0) {
                verbose = 2;
            }
        }
    }

    freedv = freedv_open(mode);
    assert(freedv != NULL);

    freedv_set_verbose(freedv, verbose);

    short speech_out[freedv_get_n_max_speech_samples(freedv)];
    short demod_in[freedv_get_n_max_modem_samples(freedv)];

    freedv_set_callback_data(freedv, my_datarx, my_datatx, &my_cb_state);

    /* Note we need to work out how many samples demod needs on each
       call (nin).  This is used to adjust for differences in the tx and rx
       sample clock frequencies.  Note also the number of output
       speech samples is time varying. */

    nin = freedv_nin(freedv);
    while(fread(demod_in, sizeof(short), nin, fin) == nin) {
        frame++;
        
        /* usual case: use the freedv_api to do everything: speech decoding, demodulating */
        // most common interface - real shorts in, real shorts out
        freedv_rx(freedv, speech_out, demod_in);

        nin = freedv_nin(freedv);
    }

    fclose(fin);
    fprintf(stderr, "frames decoded: %d\n", frame);

    freedv_close(freedv);
    return 0;
}

