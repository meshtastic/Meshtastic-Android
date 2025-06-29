/*
  memtools.h
  June 2019

  Tools for looking at memory on the stm32.  See also debug_alloc.h
*/

#include <sys/types.h>
#include <math.h>
#include "memtools.h"

/* startup_stm32f4xx.s has been modified to fill RAM segment from bss up with 0x0x55555555 */

void memtools_find_unused( int (*printf_func)(const char *fmt, ...) ) {
    int32_t *p, *start;
    int found = 0;
    
    (*printf_func)("chunks of RAM segment > 256 bytes containing start up pattern:\n");

    /* count down from top of memory through stack, empty memory, then to heap */
    for (p =(int32_t*)0x20000000; p<(int32_t*)0x20020000; p++) {
        if (found == 0) {
            if (*p == 0x55555555) {
                start = p;
                found = 1;
            }
        }
    
        if (found == 1) {
            if (*p != 0x55555555) {
                found = 0;
                int bytes = (void*)p - (void*)start;
                if (bytes >= 0x100)
		  (*printf_func)("  start: 0x%x  end: 0x%x  bytes: %d\n", (int) start, (int)p, bytes);
            }
        }
    }

}

void memtools_isnan(float *vec, int n, char *label, int (*printf_func)(const char *fmt, ...)) {
  int count = 0;
  for(int i=0; i<n; i++) {
    if (isnan(vec[i])) {
      (*printf_func)("%s memtools_isnan: %d %p\n", label, i, &vec[i]);
      if (count++ == 5) return;
    }
  }
}

