/*---------------------------------------------------------------------------*\

  FILE........: stm32f4_dac.c
  AUTHOR......: David Rowe
  DATE CREATED: 1 June 2013

  DAC driver module for STM32F4. DAC1 is connected to pin PA4, DAC2
  is connected to pin PA5.

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
#include "stm32f4xx.h"
#include "codec2_fifo.h"
#include "stm32f4_dac.h"
#include "debugblinky.h"

/* write to these registers for 12 bit left aligned data, as per data sheet
   make sure 4 least sig bits set to 0 */

#define DAC_DHR12R1_ADDRESS    0x40007408
#define DAC_DHR12R2_ADDRESS    0x40007414

#define DAC_MAX      4096            /* maximum amplitude */

/* y=mx+c mapping of samples16 bit shorts to DAC samples.  Table: 74
   of data sheet indicates With DAC buffer on, DAC range is limited to
   0x0E0 to 0xF1C at VREF+ = 3.6 V, we have Vref=3.3V which is close.
 */

#define M ((3868.0-224.0)/65536.0)
#define C 2047.0

static struct FIFO *dac1_fifo;
static struct FIFO *dac2_fifo;

static unsigned short dac1_buf[DAC_BUF_SZ];
static unsigned short dac2_buf[DAC_BUF_SZ];

static void tim6_config(int fs_divisor);
static void dac1_config(void);
static void dac2_config(void);

int dac_underflow;

// You can optionally supply your own storage for the FIFO buffers bu1 and buf2,
// or set them to NULL and they will be malloc-ed for you
void dac_open(int fs_divisor, int fifo_size, short *buf1, short *buf2) {

    memset(dac1_buf, 32768, sizeof(short)*DAC_BUF_SZ);
    memset(dac2_buf, 32768, sizeof(short)*DAC_BUF_SZ);

    /* Create fifos */

    if ((buf1 == NULL) && (buf2 == NULL)) {
        dac1_fifo = codec2_fifo_create(fifo_size);
        dac2_fifo = codec2_fifo_create(fifo_size);
    } else {
        dac1_fifo = codec2_fifo_create_buf(fifo_size, buf1);
        dac2_fifo = codec2_fifo_create_buf(fifo_size, buf2);
    }

    /* Turn on the clocks we need -----------------------------------------------*/

    /* DMA1 clock enable */
    RCC_AHB1PeriphClockCmd(RCC_AHB1Periph_DMA1, ENABLE);
    /* GPIOA clock enable (to be used with DAC) */
    RCC_AHB1PeriphClockCmd(RCC_AHB1Periph_GPIOA, ENABLE);
    /* DAC Periph clock enable */
    RCC_APB1PeriphClockCmd(RCC_APB1Periph_DAC, ENABLE);

    /* GPIO Pin configuration DAC1->PA.4, DAC2->PA.5 configuration --------------*/

    GPIO_InitTypeDef GPIO_InitStructure;
    GPIO_InitStructure.GPIO_Pin = GPIO_Pin_4 | GPIO_Pin_5;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AN;
    GPIO_InitStructure.GPIO_PuPd = GPIO_PuPd_NOPULL;
    GPIO_Init(GPIOA, &GPIO_InitStructure);

    /* Timer and DAC 1 & 2 Configuration ----------------------------------------*/

    tim6_config(fs_divisor);
    dac1_config();
    dac2_config();

    init_debug_blinky();
}

/* Call these functions to send samples to the DACs.  For your
   convenience they accept signed 16 bit samples.  You can optionally
   limit how much data to store in the fifo */

int dac1_write(short buf[], int n, int limit) {
    /* artificial limit < FIFO size */
    if (limit) {
        if ((codec2_fifo_used(dac1_fifo) + n) <= limit)
            return codec2_fifo_write(dac1_fifo, buf, n);
        else
            return -1;
    }
    /* normal operation */
    return codec2_fifo_write(dac1_fifo, buf, n);
}

int dac2_write(short buf[], int n, int limit) {
    /* artificial limit < FIFO size */
    if (limit) {
        if ((codec2_fifo_used(dac2_fifo) + n) <= limit)
            return codec2_fifo_write(dac2_fifo, buf, n);
        else
            return -1;
    }
    /* normal operation */
    return codec2_fifo_write(dac2_fifo, buf, n);
}

int dac1_free() {
    return codec2_fifo_free(dac1_fifo);
}

int dac2_free() {
    return codec2_fifo_free(dac2_fifo);
}

static void tim6_config(int fs_divisor)
{
  TIM_TimeBaseInitTypeDef    TIM_TimeBaseStructure;

  /* TIM6 Periph clock enable */
  RCC_APB1PeriphClockCmd(RCC_APB1Periph_TIM6, ENABLE);

  /* --------------------------------------------------------

  TIM6 input clock (TIM6CLK) is set to 2 * APB1 clock (PCLK1), since
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
  TIM_TimeBaseInit(TIM6, &TIM_TimeBaseStructure);

  /* TIM6 TRGO selection */

  TIM_SelectOutputTrigger(TIM6, TIM_TRGOSource_Update);

  /* TIM6 enable counter */

  TIM_Cmd(TIM6, ENABLE);
}

static void dac1_config(void)
{
  DAC_InitTypeDef  DAC_InitStructure;
  DMA_InitTypeDef  DMA_InitStructure;
  NVIC_InitTypeDef NVIC_InitStructure;

  /* DAC channel 1 Configuration */

  /*
     This line fixed a bug that cost me 5 days, bad wave amplitude
     value, and some STM32F4 periph library bugs caused triangle wave
     geneartion to be enable resulting in a low level tone on the
     SM1000, that we thought was caused by analog issues like layour
     or power supply biasing
  */
  DAC_StructInit(&DAC_InitStructure);

  DAC_InitStructure.DAC_Trigger = DAC_Trigger_T6_TRGO;
  DAC_InitStructure.DAC_WaveGeneration = DAC_WaveGeneration_None;
  DAC_InitStructure.DAC_OutputBuffer = DAC_OutputBuffer_Enable;
  DAC_Init(DAC_Channel_1, &DAC_InitStructure);

  /* DMA1_Stream5 channel7 configuration **************************************/
  /* Table 35 page 219 of the monster data sheet */

  DMA_DeInit(DMA1_Stream5);
  DMA_InitStructure.DMA_Channel = DMA_Channel_7;
  DMA_InitStructure.DMA_PeripheralBaseAddr = (uint32_t)DAC_DHR12R1_ADDRESS;
  DMA_InitStructure.DMA_Memory0BaseAddr = (uint32_t)dac1_buf;
  DMA_InitStructure.DMA_DIR = DMA_DIR_MemoryToPeripheral;
  DMA_InitStructure.DMA_BufferSize = DAC_BUF_SZ;
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
  DMA_Init(DMA1_Stream5, &DMA_InitStructure);

  /* Enable DMA Half & Complete interrupts */

  DMA_ITConfig(DMA1_Stream5, DMA_IT_TC | DMA_IT_HT, ENABLE);

  /* Enable the DMA Stream IRQ Channel */

  NVIC_InitStructure.NVIC_IRQChannel = DMA1_Stream5_IRQn;
  NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 0;
  NVIC_InitStructure.NVIC_IRQChannelSubPriority = 0;
  NVIC_InitStructure.NVIC_IRQChannelCmd = ENABLE;
  NVIC_Init(&NVIC_InitStructure);

  /* Enable DMA1_Stream5 */

  DMA_Cmd(DMA1_Stream5, ENABLE);

  /* Enable DAC Channel 1 */

  DAC_Cmd(DAC_Channel_1, ENABLE);

  /* Enable DMA for DAC Channel 1 */

  DAC_DMACmd(DAC_Channel_1, ENABLE);
}

static void dac2_config(void)
{
  DAC_InitTypeDef  DAC_InitStructure;
  DMA_InitTypeDef DMA_InitStructure;
  NVIC_InitTypeDef NVIC_InitStructure;

  /* DAC channel 2 Configuration (see notes in dac1_config() above) */

  DAC_StructInit(&DAC_InitStructure);
  DAC_InitStructure.DAC_Trigger = DAC_Trigger_T6_TRGO;
  DAC_InitStructure.DAC_WaveGeneration = DAC_WaveGeneration_None;
  DAC_InitStructure.DAC_OutputBuffer = DAC_OutputBuffer_Enable;
  DAC_Init(DAC_Channel_2, &DAC_InitStructure);

  /* DMA1_Stream6 channel7 configuration **************************************/

  DMA_DeInit(DMA1_Stream6);
  DMA_InitStructure.DMA_Channel = DMA_Channel_7;
  DMA_InitStructure.DMA_PeripheralBaseAddr = (uint32_t)DAC_DHR12R2_ADDRESS;
  DMA_InitStructure.DMA_Memory0BaseAddr = (uint32_t)dac2_buf;
  DMA_InitStructure.DMA_DIR = DMA_DIR_MemoryToPeripheral;
  DMA_InitStructure.DMA_BufferSize = DAC_BUF_SZ;
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
  DMA_Init(DMA1_Stream6, &DMA_InitStructure);

  /* Enable DMA Half & Complete interrupts */

  DMA_ITConfig(DMA1_Stream6, DMA_IT_TC | DMA_IT_HT, ENABLE);

  /* Enable the DMA Stream IRQ Channel */

  NVIC_InitStructure.NVIC_IRQChannel = DMA1_Stream6_IRQn;
  NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 0;
  NVIC_InitStructure.NVIC_IRQChannelSubPriority = 0;
  NVIC_InitStructure.NVIC_IRQChannelCmd = ENABLE;
  NVIC_Init(&NVIC_InitStructure);

  /* Enable DMA1_Stream6 */

  DMA_Cmd(DMA1_Stream6, ENABLE);

  /* Enable DAC Channel 2 */

  DAC_Cmd(DAC_Channel_2, ENABLE);

  /* Enable DMA for DAC Channel 2 */

  DAC_DMACmd(DAC_Channel_2, ENABLE);

}

/******************************************************************************/
/*                 STM32F4xx Peripherals Interrupt Handlers                   */
/*  Add here the Interrupt Handler for the used peripheral(s) (PPP), for the  */
/*  available peripheral interrupt handler's name please refer to the startup */
/*  file (startup_stm32f40xx.s/startup_stm32f427x.s).                         */
/******************************************************************************/

/*
  This function handles DMA1 Stream 5 interrupt request for DAC1.
*/

void DMA1_Stream5_IRQHandler(void) {
    int i, j, sam;
    short signed_buf[DAC_BUF_SZ/2];

    GPIOE->ODR |= (1 << 1);

    /* Transfer half empty interrupt - refill first half */

    if(DMA_GetITStatus(DMA1_Stream5, DMA_IT_HTIF5) != RESET) {
        /* fill first half from fifo */

        if (codec2_fifo_read(dac1_fifo, signed_buf, DAC_BUF_SZ/2) == -1) {
            memset(signed_buf, 0, sizeof(short)*DAC_BUF_SZ/2);
            dac_underflow++;
        }

        /* convert to unsigned */

        for(i=0; i<DAC_BUF_SZ/2; i++) {
            sam = (int)(M*(float)signed_buf[i] + C);
            dac1_buf[i] = (unsigned short)sam;
        }

        /* Clear DMA Stream Transfer Complete interrupt pending bit */

        DMA_ClearITPendingBit(DMA1_Stream5, DMA_IT_HTIF5);
    }

    /* Transfer complete interrupt - refill 2nd half */

    if(DMA_GetITStatus(DMA1_Stream5, DMA_IT_TCIF5) != RESET) {
        /* fill second half from fifo */

        if (codec2_fifo_read(dac1_fifo, signed_buf, DAC_BUF_SZ/2) == -1) {
            memset(signed_buf, 0, sizeof(short)*DAC_BUF_SZ/2);
            dac_underflow++;
        }

        /* convert to unsigned */

        for(i=0, j=DAC_BUF_SZ/2; i<DAC_BUF_SZ/2; i++,j++) {
            sam = (int)(M*(float)signed_buf[i] + C);
            dac1_buf[j] = (unsigned short)sam;
        }

        /* Clear DMA Stream Transfer Complete interrupt pending bit */

        DMA_ClearITPendingBit(DMA1_Stream5, DMA_IT_TCIF5);
    }

    GPIOE->ODR &= ~(1 << 1);
}

/*
  This function handles DMA1 Stream 6 interrupt request for DAC2.
*/

void DMA1_Stream6_IRQHandler(void) {
    int i, j, sam;
    short signed_buf[DAC_BUF_SZ/2];

    GPIOE->ODR |= (1 << 2);

    /* Transfer half empty interrupt - refill first half */

    if(DMA_GetITStatus(DMA1_Stream6, DMA_IT_HTIF6) != RESET) {
        /* fill first half from fifo */

        if (codec2_fifo_read(dac2_fifo, signed_buf, DAC_BUF_SZ/2) == -1) {
            memset(signed_buf, 0, sizeof(short)*DAC_BUF_SZ/2);
            dac_underflow++;
        }

        /* convert to unsigned */

        for(i=0; i<DAC_BUF_SZ/2; i++) {
            sam = (int)(M*(float)signed_buf[i] + C);
            dac2_buf[i] = (unsigned short)sam;
        }

        /* Clear DMA Stream Transfer Complete interrupt pending bit */

        DMA_ClearITPendingBit(DMA1_Stream6, DMA_IT_HTIF6);
    }

    /* Transfer complete interrupt - refill 2nd half */

    if(DMA_GetITStatus(DMA1_Stream6, DMA_IT_TCIF6) != RESET) {
        /* fill second half from fifo */

        if (codec2_fifo_read(dac2_fifo, signed_buf, DAC_BUF_SZ/2) == -1) {
            memset(signed_buf, 0, sizeof(short)*DAC_BUF_SZ/2);
            dac_underflow++;
        }

        /* convert to unsigned  */

        for(i=0, j=DAC_BUF_SZ/2; i<DAC_BUF_SZ/2; i++,j++) {
            sam = (int)(M*(float)signed_buf[i] + C);
            dac2_buf[j] = (unsigned short)sam;
        }

        /* Clear DMA Stream Transfer Complete interrupt pending bit */

        DMA_ClearITPendingBit(DMA1_Stream6, DMA_IT_TCIF6);
    }

    GPIOE->ODR &= ~(1 << 2);
}
