/*---------------------------------------------------------------------------*\

  FILE........: octave.c
  AUTHOR......: David Rowe
  DATE CREATED: April 28 2012

  Functions to save C arrays in GNU Octave matrix format.  The output text
  file can be directly read into Octave using "load filename".

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

#include <stdio.h>
#include <stdarg.h>

#include "octave.h"

#ifdef ARM_MATH_CM4
#include "Trace.h"
#endif

#define OCTAVE_BUFSIZE 2048


void flush_buffer(FILE* f, char* buffer,size_t* buf_idx_ptr)
{
#ifdef ARM_MATH_CM4
    trace_write(buffer,*buf_idx_ptr);
#else
    fwrite(buffer,*buf_idx_ptr,1,f);
#endif
    *buf_idx_ptr = 0;
}

void handle_buffer(FILE* f, char* buffer,const size_t max_buf, size_t* buf_idx_ptr, size_t l)
{
    *buf_idx_ptr += l;
    if (*buf_idx_ptr > max_buf - 64)
    {
        flush_buffer(f, buffer,buf_idx_ptr);
    }
}

signed int printf_buffer(FILE* f, char* buffer,const size_t max_buf, size_t* buf_idx_ptr, const char *pFormat, ...)
{
    va_list    ap;
    signed int rc;

    va_start(ap, pFormat);
    rc = vsnprintf(&buffer[*buf_idx_ptr], max_buf - *buf_idx_ptr, pFormat, ap);
    va_end(ap);
    if (rc>0)
    {
        handle_buffer(f, buffer,max_buf,buf_idx_ptr,rc);
    }
    return rc;
}


void printf_header(FILE* f, char* buffer,const size_t max_buf, size_t* buf_idx_ptr, const char *name, const char *dtype, int rows, int cols, int isFloat)
{
#ifdef ARM_MATH_CM4
    printf_buffer(f, buffer, OCTAVE_BUFSIZE, buf_idx_ptr, "# hex: %s\n", isFloat?"true":"false");
#endif    
    printf_buffer(f, buffer, OCTAVE_BUFSIZE, buf_idx_ptr, "# name: %s\n", name);
    printf_buffer(f, buffer, OCTAVE_BUFSIZE, buf_idx_ptr, "# type: %s\n",dtype);
    printf_buffer(f, buffer, OCTAVE_BUFSIZE, buf_idx_ptr, "# rows: %d\n", rows);
    printf_buffer(f, buffer, OCTAVE_BUFSIZE, buf_idx_ptr, "# columns: %d\n", cols);
}
void octave_save_int(FILE *f, char name[], int data[], int rows, int cols)
{
    int r,c;
    char buffer[OCTAVE_BUFSIZE];
    size_t buf_idx = 0;

    printf_header(f, buffer, OCTAVE_BUFSIZE, &buf_idx, name, "matrix", rows, cols, 0);

    for(r=0; r<rows; r++) {
        for(c=0; c<cols; c++)
            printf_buffer(f, buffer, OCTAVE_BUFSIZE, &buf_idx, " %d", data[r*cols+c]);
        printf_buffer(f, buffer, OCTAVE_BUFSIZE, &buf_idx, "\n");
    }

    printf_buffer(f, buffer, OCTAVE_BUFSIZE, &buf_idx, "\n\n");
    flush_buffer(f, buffer, &buf_idx);
}

void octave_save_float(FILE *f, char name[], float data[], int rows, int cols, int col_len)
{
    int r,c;
    char buffer[OCTAVE_BUFSIZE];
    size_t buf_idx = 0;

    printf_header(f, buffer, OCTAVE_BUFSIZE, &buf_idx, name, "matrix", rows, cols, 1);

    for(r=0; r<rows; r++) {
        for(c=0; c<cols; c++)
            printf_buffer(f, buffer, OCTAVE_BUFSIZE, &buf_idx, " %f", data[r*col_len+c]);
        printf_buffer(f, buffer, OCTAVE_BUFSIZE, &buf_idx, "\n");
    }

    printf_buffer(f, buffer, OCTAVE_BUFSIZE, &buf_idx, "\n\n");
    flush_buffer(f, buffer, &buf_idx);
}


void octave_save_complex(FILE *f, char name[], COMP data[], int rows, int cols, int col_len)
{
    int r,c;
    char buffer[OCTAVE_BUFSIZE];
    size_t buf_idx = 0;

    printf_header(f, buffer, OCTAVE_BUFSIZE, &buf_idx, name, "complex matrix", rows, cols, 1);

    for(r=0; r<rows; r++) {

        for(c=0; c<cols; c++)
        {
            printf_buffer(f, buffer, OCTAVE_BUFSIZE, &buf_idx, " (%f,%f)", data[r*col_len+c].real, data[r*col_len+c].imag);
        }
        printf_buffer(f, buffer, OCTAVE_BUFSIZE, &buf_idx, "\n");
    }
    printf_buffer(f, buffer, OCTAVE_BUFSIZE, &buf_idx, "\n\n");
    flush_buffer(f, buffer, &buf_idx);
}
