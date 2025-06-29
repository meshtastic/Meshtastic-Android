/*
  memtools.h
  June 2019

  Tools for anlysing and debugging memory on stm32.  See also debug_alloc.h
*/

#ifndef __MEMTOOLS__
#define __MEMTOOLS__
void memtools_find_unused( int (*printf_func)(const char *fmt, ...) );
register char * memtools_sp asm ("sp");
void memtools_isnan(float *vec, int n, char *label, int (*printf_func)(const char *fmt, ...));
#endif
