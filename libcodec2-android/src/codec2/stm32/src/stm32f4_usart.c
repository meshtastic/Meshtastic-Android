/*
  stm32f4_usart.c
  David Rowe May 2019

  Basic USART tty support for the stm32.

  From:
    http://stm32projectconsulting.blogspot.com/2013/04/stm32f4-discovery-usart-example.html
*/

#include <stm32f4xx.h>
#include <stm32f4xx_usart.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include "stm32f4_usart.h"

#define MAX_FMT_SIZE 256

void usart_init(void){

 GPIO_InitTypeDef GPIO_InitStructure;
 USART_InitTypeDef USART_InitStructure;

 /* enable peripheral clock for USART3 */
 RCC_APB1PeriphClockCmd(RCC_APB1Periph_USART3, ENABLE);

 /* GPIOB clock enable */
 RCC_AHB1PeriphClockCmd(RCC_AHB1Periph_GPIOB, ENABLE);

 /* GPIOA Configuration:  USART3 TX on PB10 */
 GPIO_InitStructure.GPIO_Pin = GPIO_Pin_10;
 GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AF;
 GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
 GPIO_InitStructure.GPIO_OType = GPIO_OType_PP;
 GPIO_InitStructure.GPIO_PuPd = GPIO_PuPd_UP ;
 GPIO_Init(GPIOB, &GPIO_InitStructure);

 /* Connect USART3 pins to AF2 */
 // TX = PB10
 GPIO_PinAFConfig(GPIOB, GPIO_PinSource10, GPIO_AF_USART3);

 USART_InitStructure.USART_BaudRate = 115200;
 USART_InitStructure.USART_WordLength = USART_WordLength_8b;
 USART_InitStructure.USART_StopBits = USART_StopBits_1;
 USART_InitStructure.USART_Parity = USART_Parity_No;
 USART_InitStructure.USART_HardwareFlowControl = USART_HardwareFlowControl_None;
 USART_InitStructure.USART_Mode = USART_Mode_Tx;
 USART_Init(USART3, &USART_InitStructure);

 USART_Cmd(USART3, ENABLE); // enable USART3

}

void usart_puts(const char s[]) {
  for (int i=0; i<strlen(s); i++) {
    USART_SendData(USART3, s[i]);
    while (USART_GetFlagStatus(USART3, USART_FLAG_TC) == RESET);
  } 
}

int usart_printf(const char *fmt, ...) 
{
  char s[MAX_FMT_SIZE];
  va_list ap;
  va_start(ap, fmt);
  vsnprintf(s, MAX_FMT_SIZE, fmt, ap);
  va_end(ap);
  usart_puts(s);
  return 1;
}
