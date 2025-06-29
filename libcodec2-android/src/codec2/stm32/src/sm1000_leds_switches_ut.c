/*---------------------------------------------------------------------------*\

  FILE........: sm1000_leds_switches_ut.c
  AUTHOR......: David Rowe
  DATE CREATED: August 5 2014

  Unit Test program for the SM1000 switches and LEDs driver.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2014 David Rowe

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
#include "sm1000_leds_switches.h"

int main(void) {
    sm1000_leds_switches_init();

    while(1) {
        led_pwr(switch_select());
        led_ptt(switch_ptt());
        led_rt(switch_back());
        led_err(!switch_back());
    }
}

