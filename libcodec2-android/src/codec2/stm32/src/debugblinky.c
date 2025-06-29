/*---------------------------------------------------------------------------*\

  FILE........: debugblinky.c
  AUTHOR......: David Rowe
  DATE CREATED: 12 August 2014

  Configures GPIO pins used for debug blinkies

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

#include "stm32f4xx.h"

void init_debug_blinky(void) {
    GPIO_InitTypeDef GPIO_InitStruct;

    /* PE0-3 used to indicate activity, PE4-5 for SM2000 +12V rail switching */

    RCC_AHB1PeriphClockCmd(RCC_AHB1Periph_GPIOE, ENABLE);

    GPIO_InitStruct.GPIO_Pin = GPIO_Pin_0 | GPIO_Pin_1 | GPIO_Pin_2 | GPIO_Pin_3 | GPIO_Pin_4 | GPIO_Pin_5;
    GPIO_InitStruct.GPIO_Mode = GPIO_Mode_OUT;
    GPIO_InitStruct.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_InitStruct.GPIO_OType = GPIO_OType_PP;
    GPIO_InitStruct.GPIO_PuPd = GPIO_PuPd_NOPULL;
    GPIO_Init(GPIOE, &GPIO_InitStruct);
}

/* SM2000: 0 for +12V RX power, 1 for +12V TX power  */

void txrx_12V(int state) {
    if (state) {
        GPIOE->ODR &= ~(1 << 5); /* +12VRXENB off */
        GPIOE->ODR |=  (1 << 4); /* +12VTXENB on */
    }
    else {
        GPIOE->ODR &= ~(1 << 4); /* +12VTXENB off */
        GPIOE->ODR |=  (1 << 5); /* +12VRXENB on */
    }
}

