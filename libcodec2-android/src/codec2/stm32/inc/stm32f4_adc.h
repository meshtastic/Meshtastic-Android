/*---------------------------------------------------------------------------*\

  FILE........: stm32f4_adc.h
  AUTHOR......: David Rowe
  DATE CREATED: 30 May 2014

  Two channel FIFO buffered ADC driver module for STM32F4.

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

#ifndef __STM32F4_ADC__
#define __STM32F4_ADC__

#define ADC_BUF_SZ   320

/* divisors for various sample rates */

#define ADC_FS_8KHZ 10500
#define ADC_FS_16KHZ 5250
#define ADC_FS_48KHZ 1750
#define ADC_FS_96KHZ 875

void adc_open(int fs_divisor, int fifo_sz, short *buf1, short *buf2);
int adc1_read(short buf[], int n); /* ADC1 Pin PA1 */
int adc2_read(short buf[], int n); /* ADC2 Pin PA2 */
int adc1_samps();
int adc2_samps();

#endif
