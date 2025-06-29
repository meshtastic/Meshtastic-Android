/*---------------------------------------------------------------------------*\

  FILE........: stm32f4_dac.h
  AUTHOR......: David Rowe
  DATE CREATED: 1 June 2013

  Two channel FIFO buffered DAC driver module for STM32F4.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2013 David Rowe

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

#ifndef __STM32F4_DAC__
#define __STM32F4_DAC__

#define DAC_BUF_SZ   320

/* divisors for various sample rates */

#define DAC_FS_8KHZ 10500
#define DAC_FS_16KHZ 5250
#define DAC_FS_48KHZ 1750
#define DAC_FS_96KHZ 875

void dac_open(int fs_divisor, int fifo_sz, short *buf1, short *buf2);
int dac1_write(short buf[], int n, int limit); /* DAC1 pin PA4 */
int dac1_free();
int dac2_write(short buf[], int n, int limit); /* DAC2 pin PA5 */
int dac2_free();

#endif
