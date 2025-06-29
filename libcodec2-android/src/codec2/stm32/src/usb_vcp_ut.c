/*---------------------------------------------------------------------------*\

  FILE........: usb_vcp_ut.c
  AUTHOR......: xenovacivus
  DATE CREATED: 31 August 2014

  USB Virtual COM Port (VCP) unit test that I found here:

    https://github.com/xenovacivus/STM32DiscoveryVCP

  Remarkably, it compiled and ran first time, and even the LEDs blink
  as advertised, they just happen to match the LEDs on the SM1000!
  However the speed was capped at about 130 kB/s.  After a lot of
  messing around I found suggestions in the comments from a similar
  library here:

    http://stm32f4-discovery.com/2014/08/library-24-virtual-com-port-vcp-stm32f4xx/

  The key was changing APP_RX_DATA_SIZE in usbd_conf.h to 10000.  I
  guess the previous size of 2048 was constraing the length of USB
  packets, and the USB overhead meant slow throughput.  I could
  achieve a max of 450 kB/s with this change, about 1/3 of the
  theoretical 1.5 MB/s max for USB FS (12 Mbit/s).

  I used this to test grabbing data from the STM32F4 Discovery:
    $ sudo dd if=/dev/ttyACM0 of=/dev/null count=100
    4+96 records in
    44+1 records out
    22615 bytes (23 kB) copied, 0.150884 s, 150 kB/s

  However I occasionally see:
    $ sudo dd if=/dev/ttyACM0 of=/dev/null count=100
      dd: failed to open ‘/dev/ttyACM0’: Device or resource busy

  Googling found some suggestion that this is due to "modem manager", however I
  removed MM and the problem still exists.

\*---------------------------------------------------------------------------*/

#include <stm32f4xx.h>
#include <stm32f4xx_gpio.h>
#include "stm32f4_usb_vcp.h"
#include "sm1000_leds_switches.h"

volatile uint32_t ticker, buf_ticker;

#define N 640*6

short buf[N];

int main(void) {
    int i;

    for(i=0; i<N; i++)
        buf[i] = 0;
    
    sm1000_leds_switches_init();
    usb_vcp_init();
    SysTick_Config(SystemCoreClock/1000);

    while (1) {

        /* Blink the discovery red LED at 1Hz */

        if (ticker > 500) {
            GPIOD->BSRRH = GPIO_Pin_13;
        }
        if (ticker > 1000) {
            ticker = 0;
            GPIOD->BSRRL = GPIO_Pin_13;
        }

        /* Every 40ms send a buffer, simulates 16 bit samples at Fs=96kHz */

        if (buf_ticker > 40) {
            buf_ticker = 0;
            led_pwr(1);
            VCP_send_buffer((uint8_t*)buf, sizeof(buf));
            led_pwr(0);
        }

    }

    return 0;
}

/*
 * Interrupt Handler
 */

void SysTick_Handler(void)
{
	ticker++;
        buf_ticker++;
}

