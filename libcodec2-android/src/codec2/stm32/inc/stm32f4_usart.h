/*---------------------------------------------------------------------------*\

  FILE........: stm32f4_usart.h
  AUTHOR......: David Rowe
  DATE CREATED: May 2019

  Basic USART tty support for the stm32.

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2019 David Rowe

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

#ifndef __STM32F4_USART__
#define __STM32F4_USART__

void usart_init();
void usart_puts(const char s[]);
int usart_printf(const char *fmt, ...);

#endif
