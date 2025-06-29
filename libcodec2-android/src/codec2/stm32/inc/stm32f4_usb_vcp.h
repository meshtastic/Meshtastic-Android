/*---------------------------------------------------------------------------*\

  FILE........: stm32f4_usb_vcp.h
  AUTHOR......: David Rowe
  DATE CREATED: 4 Sep 2014

  USB Virtual COM Port (VCP) module.

\*---------------------------------------------------------------------------*/

#ifndef __STM32F4_USB_VCP__
#define __STM32F4_USB_VCP__

#include <stdint.h>

void usb_vcp_init(void);

int VCP_get_char(uint8_t *buf);
int VCP_get_string(uint8_t *buf);
void VCP_put_char(uint8_t buf);
void VCP_send_str(uint8_t* buf);
void VCP_send_buffer(uint8_t* buf, int len);

#endif
