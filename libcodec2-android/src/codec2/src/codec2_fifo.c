/*---------------------------------------------------------------------------*\

  FILE........: codec2_fifo.c
  AUTHOR......: David Rowe
  DATE CREATED: Oct 15 2012

  A FIFO design useful in gluing the FDMDV modem and codec together in
  integrated applications.  The unittest/tfifo indicates these
  routines are thread safe without the need for syncronisation
  object, e.g. a different thread can read and write to a fifo at the
  same time.

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

#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include "codec2_fifo.h"

struct FIFO {
    short *buf;
    short *pin;
    short *pout;
    int    nshort;
};

// standard create function
struct FIFO *codec2_fifo_create(int nshort) {
    short *buf = (short*)malloc(sizeof(short)*nshort);
    assert(buf != NULL);
    return codec2_fifo_create_buf(nshort, buf);
}

// alternate create function where buffer is externally supplied
struct FIFO *codec2_fifo_create_buf(int nshort, short* buf) {
    struct FIFO *fifo;
    assert(buf != NULL);
    fifo = (struct FIFO *)malloc(sizeof(struct FIFO));
    assert(fifo != NULL);

    fifo->buf = buf;
    fifo->pin = fifo->buf;
    fifo->pout = fifo->buf;
    fifo->nshort = nshort;

    return fifo;
}

void codec2_fifo_destroy(struct FIFO *fifo) {
    assert(fifo != NULL);
    free(fifo->buf);
    free(fifo);
}

int codec2_fifo_write(struct FIFO *fifo, short data[], int n) {
    int            i;
    short         *pdata;
    short         *pin = fifo->pin;

    assert(fifo != NULL);
    assert(data != NULL);

    if (n > codec2_fifo_free(fifo)) {
	return -1;
    }
    else {

	/* This could be made more efficient with block copies
	   using memcpy */

	pdata = data;
	for(i=0; i<n; i++) {
	    *pin++ = *pdata++;
	    if (pin == (fifo->buf + fifo->nshort))
		pin = fifo->buf;
	}
	fifo->pin = pin;
    }

    return 0;
}

int codec2_fifo_read(struct FIFO *fifo, short data[], int n)
{
    int            i;
    short         *pdata;
    short         *pout = fifo->pout;

    assert(fifo != NULL);
    assert(data != NULL);

    if (n > codec2_fifo_used(fifo)) {
	return -1;
    }
    else {

	/* This could be made more efficient with block copies
	   using memcpy */

	pdata = data;
	for(i=0; i<n; i++) {
	    *pdata++ = *pout++;
	    if (pout == (fifo->buf + fifo->nshort))
		pout = fifo->buf;
	}
	fifo->pout = pout;
    }

    return 0;
}

int codec2_fifo_used(const struct FIFO * const fifo)
{
    short         *pin = fifo->pin;
    short         *pout = fifo->pout;
    unsigned int   used;

    assert(fifo != NULL);
    if (pin >= pout)
        used = pin - pout;
    else
        used = fifo->nshort + (unsigned int)(pin - pout);

    return used;
}

int codec2_fifo_free(const struct FIFO * const fifo)
{
    // available storage is one less than nshort as prd == pwr
    // is reserved for empty rather than full

    return fifo->nshort - codec2_fifo_used(fifo) - 1;
}
