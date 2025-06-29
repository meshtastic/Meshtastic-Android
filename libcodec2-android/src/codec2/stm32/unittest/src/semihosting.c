#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <errno.h>

#include "semihosting.h"

extern void initialise_monitor_handles(void);
extern int errno;

int semihosting_init(void) {

    initialise_monitor_handles();    
    setvbuf(stderr, NULL, _IOLBF, 256);    
    return(0);

}

/* vi:set ts=4 et sts=4: */
