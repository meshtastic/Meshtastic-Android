/*---------------------------------------------------------------------------*\

  FILE........: thash.c
  AUTHOR......: David Rowe
  DATE CREATED: July 2020

  Simple test program for freeDV API get hash function

\*---------------------------------------------------------------------------*/

#include <stdio.h>
#include "freedv_api.h"

int main(void) { 
    printf("%s\n", freedv_get_hash());
    return 0;
}


