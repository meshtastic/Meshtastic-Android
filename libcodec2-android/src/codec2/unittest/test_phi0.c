/*
  FILE...: test_phi0.c
  AUTHOR.: Matthew C. Valenti, Rohit Iyer Seshadri, David Rowe, Don Reid
  CREATED: Sep 2018

  Compare new generated phi0 function to what was originally in mpdecode_core.c
*/

#include <math.h>
#include <stdlib.h>
#include <stdio.h>

#include "phi0.h"


/* Original Phi function */
static float phi0_orig( float x ) {
  float z;

  if (x>10)
    return( 0 );
  else if (x< 9.08e-5 )
    return( 10 );
  else if (x > 9)
    return( 1.6881e-4 );
  /* return( 1.4970e-004 ); */
  else if (x > 8)
    return( 4.5887e-4 );
  /* return( 4.0694e-004 ); */
  else if (x > 7)
    return( 1.2473e-3 );
  /* return( 1.1062e-003 ); */
  else if (x > 6)
    return( 3.3906e-3 );
  /* return( 3.0069e-003 ); */
  else if (x > 5)
    return( 9.2168e-3 );
  /* return( 8.1736e-003 ); */
  else {
    z = (float) exp(x);
    return( (float) log( (z+1)/(z-1) ) ); 
  }
}

////////////////////////////////////////////////////
// Main
int main(void) {

    float xf;
    float error;
    int errsum = 0;
    int errsum2 = 0;
    int errcnt = 0;

    for (xf=10.5f; xf>5e-5f; xf = xf * 0.9) {

        float orig = phi0_orig(xf);
        float new  = phi0(xf);

        error = new - orig;
        printf("%10.4f: %10.6f - %10.6f = %10.6f", xf, new, orig, error);
        if ((error >= 0.001) && (error >= (orig * 0.1))) printf(" ****");
        printf("\n");

        errsum += error;
        errsum2 += error * error;
        errcnt ++;

    }

    printf("Net error %f\n", (double)errsum);
    printf("avg error %f\n", (double)errsum/errcnt);
    printf("rms error %f\n", (double)sqrt(errsum2/errcnt));

    return(0);
}

/* vi:set ts=4 et sts=4: */
