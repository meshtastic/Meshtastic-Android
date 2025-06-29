/*---------------------------------------------------------------------------*\

  FILE........: codec2_fifo.h
  AUTHOR......: David Rowe
  DATE CREATED: Oct 15 2012

  A FIFO design useful in gluing the FDMDV modem and codec together in
  integrated applications.

  The name codec2_fifo.h is used to make it unique when "make
  installed".

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2012 David Rowe

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

#ifndef __FIFO__
#define __FIFO__

#ifdef __cplusplus
extern "C" {
#endif

struct FIFO;

struct FIFO *codec2_fifo_create(int nshort);
struct FIFO *codec2_fifo_create_buf(int nshort, short* buf);
void codec2_fifo_destroy(struct FIFO *fifo);
int codec2_fifo_write(struct FIFO *fifo, short data[], int n);
int codec2_fifo_read(struct FIFO *fifo, short data[], int n);

/* Return the number of bytes stored in the FIFO */
int codec2_fifo_used(const struct FIFO * const fifo);

/* Return the space available in the FIFO */
int codec2_fifo_free(const struct FIFO * const fifo);

#ifdef __cplusplus
}
#endif

#endif
