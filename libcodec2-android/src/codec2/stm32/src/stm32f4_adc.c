/*---------------------------------------------------------------------------*\

  FILE........: stm32f4_adc.c
  AUTHOR......: David Rowe
  DATE CREATED: 4 June 2013

  Two channel ADC driver module for STM32F4.  Pin PA1 connects to ADC1, pin
  PA2 connects to ADC2.

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

#include <assert.h>
#include <stdlib.h>
#include <string.h>

#include "stm32f4xx_adc.h"
#include "stm32f4xx_gpio.h"
#include "stm32f4xx_rcc.h"

#include "codec2_fifo.h"
#include "stm32f4_adc.h"
#include "debugblinky.h"

struct FIFO *adc1_fifo;
struct FIFO *adc2_fifo;
unsigned short adc_buf[ADC_BUF_SZ];
int adc_overflow1, adc_overflow2;
int half,full;

#define ADCx_DR_ADDRESS          ((uint32_t)0x4001204C)
#define DMA_CHANNELx             DMA_Channel_0
#define DMA_STREAMx              DMA2_Stream0
#define ADCx                     ADC1

void adc_configure();

static void tim2_config(int fs_divisor);

// You can optionally supply your own storage for the FIFO buffers bu1 and buf2,
// or set them to NULL and they will be malloc-ed for you
void adc_open(int fs_divisor, int fifo_sz, short *buf1, short *buf2) {
    if (buf1 == NULL) {
        adc1_fifo = codec2_fifo_create(fifo_sz);
        adc2_fifo = codec2_fifo_create(fifo_sz);
    } else {
        adc1_fifo = codec2_fifo_create_buf(fifo_sz, buf1);
        adc2_fifo = codec2_fifo_create_buf(fifo_sz, buf2);
    }
    
    tim2_config(fs_divisor);
    adc_configure();
    init_debug_blinky();
}

/* n signed 16 bit samples in buf[] if return != -1 */

int adc1_read(short buf[], int n) {
    return codec2_fifo_read(adc1_fifo, buf, n);
}

/* n signed 16 bit samples in buf[] if return != -1 */

int adc2_read(short buf[], int n) {
    return codec2_fifo_read(adc2_fifo, buf, n);
}

/* Returns number of signed 16 bit samples in the FIFO currently */
int adc1_samps(){
	return codec2_fifo_used(adc1_fifo);
}

/* Returns number of signed 16 bit samples in the FIFO currently */
int adc2_samps(){
	return codec2_fifo_used(adc2_fifo);
}

static void tim2_config(int fs_divisor)
{
  TIM_TimeBaseInitTypeDef    TIM_TimeBaseStructure;

  /* TIM2 Periph clock enable */
  RCC_APB1PeriphClockCmd(RCC_APB1Periph_TIM2, ENABLE);

  /* --------------------------------------------------------

  TIM2 input clock (TIM2CLK) is set to 2 * APB1 clock (PCLK1), since
  APB1 prescaler is different from 1 (see system_stm32f4xx.c and Fig
  13 clock tree figure in DM0031020.pdf).

     Sample rate Fs = 2*PCLK1/TIM_ClockDivision
                    = (HCLK/2)/TIM_ClockDivision

  ----------------------------------------------------------- */

  /* Time base configuration */

  TIM_TimeBaseStructInit(&TIM_TimeBaseStructure);
  TIM_TimeBaseStructure.TIM_Period = fs_divisor - 1;
  TIM_TimeBaseStructure.TIM_Prescaler = 0;
  TIM_TimeBaseStructure.TIM_ClockDivision = 0;
  TIM_TimeBaseStructure.TIM_CounterMode = TIM_CounterMode_Up;
  TIM_TimeBaseInit(TIM2, &TIM_TimeBaseStructure);

  /* TIM2 TRGO selection */

  TIM_SelectOutputTrigger(TIM2, TIM_TRGOSource_Update);

  /* TIM2 enable counter */

  TIM_Cmd(TIM2, ENABLE);
}


void adc_configure(){
    ADC_InitTypeDef  ADC_init_structure;
    GPIO_InitTypeDef GPIO_initStructre;
    DMA_InitTypeDef  DMA_InitStructure;
    NVIC_InitTypeDef NVIC_InitStructure;

    // Clock configuration

    RCC_APB2PeriphClockCmd(RCC_APB2Periph_ADC1,ENABLE);
    RCC_AHB1PeriphClockCmd(RCC_AHB1ENR_GPIOAEN,ENABLE);
    RCC_AHB1PeriphClockCmd(RCC_AHB1Periph_DMA2, ENABLE);

    // Analog pin configuration ADC1->PA1, ADC2->PA2

    GPIO_initStructre.GPIO_Pin =  GPIO_Pin_1 | GPIO_Pin_2;
    GPIO_initStructre.GPIO_Mode = GPIO_Mode_AN;
    GPIO_initStructre.GPIO_PuPd = GPIO_PuPd_NOPULL;
    GPIO_Init(GPIOA,&GPIO_initStructre);

    // ADC structure configuration

    ADC_DeInit();
    ADC_init_structure.ADC_DataAlign = ADC_DataAlign_Left;
    ADC_init_structure.ADC_Resolution = ADC_Resolution_12b;
    ADC_init_structure.ADC_ContinuousConvMode = DISABLE;
    ADC_init_structure.ADC_ExternalTrigConv = ADC_ExternalTrigConv_T2_TRGO;
    ADC_init_structure.ADC_ExternalTrigConvEdge = ADC_ExternalTrigConvEdge_Rising;
    ADC_init_structure.ADC_NbrOfConversion = 2;
    ADC_init_structure.ADC_ScanConvMode = ENABLE;
    ADC_Init(ADCx,&ADC_init_structure);

    // Select the channel to be read from

    ADC_RegularChannelConfig(ADCx,ADC_Channel_1,1,ADC_SampleTime_144Cycles);
    ADC_RegularChannelConfig(ADCx,ADC_Channel_2,2,ADC_SampleTime_144Cycles);
    //ADC_VBATCmd(ENABLE);

    /* DMA  configuration **************************************/

    DMA_DeInit(DMA_STREAMx);
    DMA_InitStructure.DMA_Channel = DMA_CHANNELx;
    DMA_InitStructure.DMA_PeripheralBaseAddr = (uint32_t)ADCx_DR_ADDRESS;
    DMA_InitStructure.DMA_Memory0BaseAddr = (uint32_t)adc_buf;
    DMA_InitStructure.DMA_DIR = DMA_DIR_PeripheralToMemory;
    DMA_InitStructure.DMA_BufferSize = ADC_BUF_SZ;
    DMA_InitStructure.DMA_PeripheralInc = DMA_PeripheralInc_Disable;
    DMA_InitStructure.DMA_MemoryInc = DMA_MemoryInc_Enable;
    DMA_InitStructure.DMA_PeripheralDataSize = DMA_PeripheralDataSize_HalfWord;
    DMA_InitStructure.DMA_MemoryDataSize = DMA_MemoryDataSize_HalfWord;
    DMA_InitStructure.DMA_Mode = DMA_Mode_Circular;
    DMA_InitStructure.DMA_Priority = DMA_Priority_High;
    DMA_InitStructure.DMA_FIFOMode = DMA_FIFOMode_Disable;
    DMA_InitStructure.DMA_FIFOThreshold = DMA_FIFOThreshold_HalfFull;
    DMA_InitStructure.DMA_MemoryBurst = DMA_MemoryBurst_Single;
    DMA_InitStructure.DMA_PeripheralBurst = DMA_PeripheralBurst_Single;
    DMA_Init(DMA_STREAMx, &DMA_InitStructure);

    /* Enable DMA request after last transfer (Single-ADC mode) */

    ADC_DMARequestAfterLastTransferCmd(ADCx, ENABLE);

    /* Enable ADC1 DMA */

    ADC_DMACmd(ADCx, ENABLE);

    /* DMA2_Stream0 enable */

    DMA_Cmd(DMA_STREAMx, ENABLE);

    /* Enable DMA Half & Complete interrupts */

    DMA_ITConfig(DMA2_Stream0, DMA_IT_TC | DMA_IT_HT, ENABLE);

    /* Enable the DMA Stream IRQ Channel */

    NVIC_InitStructure.NVIC_IRQChannel = DMA2_Stream0_IRQn;
    NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 0;
    NVIC_InitStructure.NVIC_IRQChannelSubPriority = 0;
    NVIC_InitStructure.NVIC_IRQChannelCmd = ENABLE;
    NVIC_Init(&NVIC_InitStructure);

    // Enable and start ADC conversion

    ADC_Cmd(ADC1,ENABLE);
    ADC_SoftwareStartConv(ADC1);
}

/*
  This function handles DMA Stream interrupt request.
*/

void DMA2_Stream0_IRQHandler(void) {
    int i, j, sam;
    short signed_buf1[ADC_BUF_SZ/2];
    short signed_buf2[ADC_BUF_SZ/2];

    GPIOE->ODR |= (1 << 0);

    /* Half transfer interrupt */

    if(DMA_GetITStatus(DMA2_Stream0, DMA_IT_HTIF0) != RESET) {
        half++;

        /* convert to signed */

        for(i=0, j=0; i<ADC_BUF_SZ/2; i+=2,j++) {
            sam = (int)adc_buf[i] - 32768;
            signed_buf1[j] = sam;
            sam = (int)adc_buf[i+1] - 32768;
            signed_buf2[j] = sam;
        }
        /* write first half to fifo */

        if (codec2_fifo_write(adc1_fifo, signed_buf1, ADC_BUF_SZ/4) == -1) {
            adc_overflow1++;
        }
        if (codec2_fifo_write(adc2_fifo, signed_buf2, ADC_BUF_SZ/4) == -1) {
            adc_overflow2++;
        }

        /* Clear DMA Stream Transfer Complete interrupt pending bit */

        DMA_ClearITPendingBit(DMA2_Stream0, DMA_IT_HTIF0);
    }

    /* Transfer complete interrupt */

    if(DMA_GetITStatus(DMA2_Stream0, DMA_IT_TCIF0) != RESET) {
        full++;

        /* convert to signed */

        for(i=0, j=0; i<ADC_BUF_SZ/2; i+=2,j++) {
            sam = (int)adc_buf[ADC_BUF_SZ/2 + i] - 32768;
            signed_buf1[j] = sam;
            sam = (int)adc_buf[ADC_BUF_SZ/2 + i+1] - 32768;
            signed_buf2[j] = sam;
        }

        /* write second half to fifo */

        if (codec2_fifo_write(adc1_fifo, signed_buf1, ADC_BUF_SZ/4) == -1) {
            adc_overflow1++;
        }
        if (codec2_fifo_write(adc2_fifo, signed_buf2, ADC_BUF_SZ/4) == -1) {
            adc_overflow2++;
        }

        /* Clear DMA Stream Transfer Complete interrupt pending bit */

        DMA_ClearITPendingBit(DMA2_Stream0, DMA_IT_TCIF0);
    }

    GPIOE->ODR &= ~(1 << 0);
}

