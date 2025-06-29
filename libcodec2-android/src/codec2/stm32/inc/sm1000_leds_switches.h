/*---------------------------------------------------------------------------*\

  FILE........: sm1000_leds_switches.h
  AUTHOR......: David Rowe
  DATE CREATED: 18 July 2014

  Functions for controlling LEDs and reading switches on SM1000.

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

#ifndef __LEDS_SWITCHES__
#define __LEDS_SWITCHES__

#include <stdint.h>

void sm1000_leds_switches_init(void);

#define	LED_ON	1	/*!< Turn LED on */
#define LED_OFF	0	/*!< Turn LED off */
#define LED_INV	-1	/*!< Invert LED state */

void led_pwr(int state);
void led_ptt(int state);
void led_rt(int state);
void led_err(int state);
void not_cptt(int state);

int switch_ptt(void);
int switch_select(void);
int switch_back(void);
int ext_ptt(void);

#define DEBOUNCE_DELAY 50 /*!< Delay to wait while switch bounces */

#define SW_STEADY   0   /*!< Switch is in steady-state */
#define SW_DEBOUNCE 1   /*!< Switch is being debounced */

/*! Switch debounce and logic handling */
struct switch_t {
    /*! Debounce/hold timer */
    uint32_t    timer;
    /*! Current/debounced observed switch state */
    uint8_t     sw;
    /*! Raw observed switch state (during debounce) */
    uint8_t     raw;
    /*! Last steady-state switch state */
    uint8_t     last;
    /*! Debouncer state */
    uint8_t     state;
};

/*! Update the state of a switch */
void switch_update(struct switch_t* const sw, uint8_t state);

/*! Acknowledge the current state of the switch */
void switch_ack(struct switch_t* const sw);

/*! Return how long the switch has been pressed in ticks. */
uint32_t switch_pressed(const struct switch_t* const sw);

/*! Return non-zero if the switch has been released. */
int switch_released(const struct switch_t* const sw);

/*! Count the tick timers on the switches. */
void switch_tick(struct switch_t* const sw);

void ColorfulRingOfDeath(int code);

#endif
