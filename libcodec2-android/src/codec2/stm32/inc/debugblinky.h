/*---------------------------------------------------------------------------*\

  FILE........: debugblinky.h
  AUTHOR......: David Rowe
  DATE CREATED: 12 August 2014

  Configures Port E GPIO pins used for debug blinkies, and control lines
  for SM2000 +12V switching.

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

#ifndef __DEBUGBLINKY__
#define __DEBUGBLINKY__

void init_debug_blinky(void);
void txrx_12V(int state);

#endif
