/*---------------------------------------------------------------------------*\

  FILE........: tfreedv_2400B_rawdata.c
  AUTHOR......: Jeroen Vreeken
  DATE CREATED: 24 May 2020

  FreeDV 2400B rawdata test.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2020 Jeroen Vreeken <jeroen@vreeken.net>

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.


  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, see <http://www.gnu.org/licenses/>.
*/

#include <stdio.h>
#include "freedv_api.h"
#include "assert.h"

int main(int argc, char **argv)
{
    struct freedv *f;
    int i;

    printf("freedv_api tests for mode 2400B\n");

    printf("freedv_open(FREEDV_MODE_2400B) ");
    f = freedv_open(FREEDV_MODE_2400B);
    assert(f != NULL);
    printf("Passed\n");

    printf("freedv_get_mode() ");
    int mode = freedv_get_mode(f);
    assert(mode == FREEDV_MODE_2400B);
    printf("Passed\n");

    printf("freedv_get_n_max_modem_samples() ");
    int max_samples = freedv_get_n_max_modem_samples(f);
    assert(max_samples == 1930);
    printf("%d Passed\n", max_samples);

    printf("freedv_get_n_nom_modem_samples() ");
    int nom_samples = freedv_get_n_nom_modem_samples(f);
    assert(nom_samples == 1920);
    printf("%d Passed\n", nom_samples);

    printf("freedv_get_n_speech_samples() ");
    int speech_samples = freedv_get_n_speech_samples(f);
    assert(speech_samples == 320);
    printf("%d Passed\n", speech_samples);

    printf("freedv_get_n_bits_per_codec_frame() ");
    int codec_bits = freedv_get_bits_per_codec_frame(f);
    assert(codec_bits == 52);
    printf("%d Passed\n", codec_bits);

    printf("freedv_get_n_bits_per_modem_frame() ");
    int frame_bits = freedv_get_bits_per_modem_frame(f);
    assert(frame_bits == 52);
    printf("%d Passed\n", frame_bits);

    printf("freedv_rawdatatx()/freedv_rawdatarx() ");
    int frames = 0;
    int fails = 0;
    {
        short mod[nom_samples * 10];
	/* Note: A codec frame is only 6.5 bytes!
	   so the seventh byte will be half empty!
	 */
        unsigned char payload[7] = { 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x70 };
        for (i = 0; i < 10; i ++) {
	    freedv_rawdatatx(f, mod + i * nom_samples, payload);
        }
        int nin = 0;
        for (i = 0; i < nom_samples * 9; i += nin) {
            nin = freedv_nin(f);
	    unsigned char payload_rx[7] = {0};
            int r = freedv_rawdatarx(f, payload_rx, mod + i);
            if (r) {
	        int b;
                for (b = 0; b < 7; b++) {
	    	    if (payload[b] != payload_rx[b]) {
		        printf("Received codec bits 0x%02x do not match expected 0x%02x\n", payload_rx[b], payload[b]);
		        fails++;
                    }
                }
	        frames++;
	    }
        }
    }
    if (!frames) {
    	printf("Did not decode any frames successfully\n");
	goto fail;
    }

    printf("Tests passed\n");
    return 0;
fail:
    printf("Test failed\n");
    return 1;
}
