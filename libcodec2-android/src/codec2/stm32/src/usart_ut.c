/*
  usart_ut.c
  David Rowe May 2019

  Unit test for stm32 USART support.

  tio is useful to receive the serial strings:

    $ tio -m INLCRNL /dev/ttyUSB0 
*/

#include <stm32f4xx.h>
#include "stm32f4_usart.h"

void Delay(uint32_t nCount)
{
  while(nCount--)
  {
  }
}

int main(void){

 usart_init();

 while(1){
   usart_puts("Hello, World\n");
   Delay(0x3FFFFF);
 }
}
