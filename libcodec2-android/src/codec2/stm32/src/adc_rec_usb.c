/*---------------------------------------------------------------------------*\

  FILE........: adc_rec_usb.c
  AUTHOR......: David Rowe
  DATE CREATED: Nov 2015

  Records a 16 kHz sample rate raw file from one of the ADC channels,
  which are connected to pins PA1 (ADC1) and PA2 (ADC2).  Uploads to the
  host PC via the STM32F4 USB port, which appears as /dev/ttyACM0.

  On the SM1000:
    ADC1 -> PA1 -> "from radio"
    ADC2 -> PA2 -> "mic amp"

  I used this to record:
    $ sudo dd if=/dev/ttyACM0 of=test.raw count=100

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2015 David Rowe

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

#include <stdlib.h>
#include <math.h>
#include "stm32f4_adc.h"
#include "stm32f4_usb_vcp.h"
#include "sm1000_leds_switches.h"

#define  N  (ADC_BUF_SZ*6)

/* test tone parameters */

#define  FREQ 999.0    /* make sure no alignment with frame boundaries */
#define  FS   16000.0
#define  AMP  10000.0

extern int adc_overflow1;
extern int adc_overflow2;

int main(void){
    short  buf[N];
    #ifdef TEST_TONE
    float  phase = 0.0;
    float  sam;
    int    i;
    #endif

    usb_vcp_init();
    adc_open(ADC_FS_96KHZ, 4*N, NULL, NULL);
    sm1000_leds_switches_init();

    /* set up test buffer, lets us test USB comms indep of ADC, record to a file
       then play back/examine waveform to make sure no clicks */

    while(1) {
        while(adc1_read(buf, N) == -1);

        #ifdef TEST_TONE
        for(i=0; i<N; i++) {
            phase += 2.0*M_PI*FREQ/FS;
            phase -= 2.0*M_PI*floor(phase/(2.0*M_PI));
            sam = AMP*cos(phase);
            buf[i] = (short)sam;
        }
        #endif

        led_pwr(1);
        VCP_send_buffer((uint8_t*)buf, sizeof(buf));
        led_pwr(0);
    }
}
