/*---------------------------------------------------------------------------*\

  FILE........: sm1000_leds_switches.c
  AUTHOR......: David Rowe
  DATE CREATED: 18 July 2014

  Functions for controlling LEDs and reading switches on the SM1000.

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

#define _CPTT          GPIO_Pin_10
#define LED_PWR        GPIO_Pin_12
#define LED_PTT        GPIO_Pin_13
#define LED_RT         GPIO_Pin_14
#define LED_ERR        GPIO_Pin_15
#define SWITCH_PTT     GPIO_Pin_7
#define SWITCH_SELECT  GPIO_Pin_0
#define SWITCH_BACK    GPIO_Pin_1
#define EXT_PTT        GPIO_Pin_8

#include <stm32f4xx.h>
#include <stm32f4xx_gpio.h>
#include "sm1000_leds_switches.h"

void sm1000_leds_switches_init(void) {
    GPIO_InitTypeDef GPIO_InitStruct;

    RCC_AHB1PeriphClockCmd(RCC_AHB1Periph_GPIOD, ENABLE);

    /* output pins */

    GPIO_InitStruct.GPIO_Pin = LED_PWR | LED_PTT | LED_RT | LED_ERR | _CPTT;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_OUT;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_2MHz;
    GPIO_InitStruct.GPIO_OType = GPIO_OType_PP;
    GPIO_InitStruct.GPIO_PuPd = GPIO_PuPd_NOPULL;
    GPIO_Init(GPIOD, &GPIO_InitStruct);

    /* input pins */

    GPIO_InitStruct.GPIO_Pin = SWITCH_PTT | SWITCH_SELECT | SWITCH_BACK;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IN;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_2MHz;
    GPIO_InitStruct.GPIO_PuPd = GPIO_PuPd_NOPULL; /* we have our own external pull ups */
    GPIO_Init(GPIOD, &GPIO_InitStruct);

    GPIO_InitStruct.GPIO_Pin = EXT_PTT;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_IN;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_2MHz;
    GPIO_InitStruct.GPIO_PuPd = GPIO_PuPd_UP;     /* use internal pull up */
    GPIO_Init(GPIOD, &GPIO_InitStruct);


}

void led_pwr(int state) {
    if (state > 0)
        GPIOD->ODR |= (1 << 12);
    else if (state < 0)
        GPIOD->ODR ^= (1 << 12);
    else
        GPIOD->ODR &= ~(1 << 12);
}

void led_ptt(int state) {
    if (state > 0)
        GPIOD->ODR |= (1 << 13);
    else if (state < 0)
        GPIOD->ODR |= (1 << 13);
    else
        GPIOD->ODR &= ~(1 << 13);
}

void led_rt(int state) {
    if (state > 0)
        GPIOD->ODR |= (1 << 14);
    else if (state < 0)
        GPIOD->ODR ^= (1 << 14);
    else
        GPIOD->ODR &= ~(1 << 14);
}

void led_err(int state) {
    if (state > 0)
        GPIOD->ODR |= (1 << 15);
    else if (state < 0)
        GPIOD->ODR ^= (1 << 15);
    else
        GPIOD->ODR &= ~(1 << 15);
}

void not_cptt(int state) {
    if (state)
        GPIOD->ODR |= (1 << 10);
    else
        GPIOD->ODR &= ~(1 << 10);
}

int switch_ptt(void) {
    return GPIOD->IDR & (1 << 7);
}

int switch_select(void) {
    return GPIOD->IDR & (1 << 0);
}

int switch_back(void) {
    return GPIOD->IDR & (1 << 1);
}

int ext_ptt(void) {
    return GPIOD->IDR & (1 << 8);
}

/*
  FUNCTION: ColorfulRingOfDeath()
  AUTHOR..: xenovacivus

  Colourful ring of death, blink LEDs like crazy forever if something
  really nasty happens.  Adapted from USB Virtual COM Port (VCP)
  module adapted from code I found here:

    https://github.com/xenovacivus/STM32DiscoveryVCP

  Call this to indicate a failure.  Blinks the STM32F4 discovery LEDs
  in sequence.  At 168Mhz, the blinking will be very fast - about 5
  Hz.  Keep that in mind when debugging, knowing the clock speed
  might help with debugging.
*/

int mycode; /* examine this with debugger if it dies */

void ColorfulRingOfDeath(int code) {
    mycode = code;
    uint16_t ring = 1;
    while (1) {
        uint32_t count = 0;
        while (count++ < 5000000);

        GPIOD->BSRRH = (ring << 12);
        ring = ring << 1;
        if (ring >= 1<<4) {
            ring = 1;
        }
        GPIOD->BSRRL = (ring << 12);
    }
}
void HardFault_Handler(void) { ColorfulRingOfDeath(1); }
void MemManage_Handler(void) { ColorfulRingOfDeath(2); }
void BusFault_Handler(void)  { ColorfulRingOfDeath(3); }
void UsageFault_Handler(void){ ColorfulRingOfDeath(4); }


void switch_tick(struct switch_t* const sw)
{
    if (sw->sw != sw->raw) {
        /* State transition, reset timer */
        if (sw->state == SW_STEADY)
            sw->last = sw->sw;
        sw->state = SW_DEBOUNCE;
        sw->timer = DEBOUNCE_DELAY;
        sw->sw = sw->raw;
    } else if (sw->state == SW_DEBOUNCE) {
        if (sw->timer > 0) {
            /* Steady so far, keep waiting */
            sw->timer--;
        } else {
            /* Steady state reached */
            sw->state = SW_STEADY;
        }
    } else if (sw->sw) {
        /* Hold state.  Yes this will wrap, but who cares? */
        sw->timer++;
    }
}

void switch_update(struct switch_t* const sw, uint8_t state)
{
    sw->raw = state;
    if (sw->raw == sw->sw)
        return;

    if (sw->state == SW_STEADY)
        sw->last = sw->sw;
    sw->timer = DEBOUNCE_DELAY;
    sw->sw = sw->raw;
    sw->state = SW_DEBOUNCE;
}

uint32_t switch_pressed(const struct switch_t* const sw)
{
    if ((sw->state == SW_STEADY) && sw->sw)
        return sw->timer;
    return 0;
}

int switch_released(const struct switch_t* const sw)
{
    if (sw->state != SW_STEADY)
        return 0;
    if (!sw->last)
        return 0;
    if (sw->sw)
        return 0;
    return 1;
}

void switch_ack(struct switch_t* const sw)
{
    if (sw->state == SW_STEADY)
        sw->last = sw->sw;
}
