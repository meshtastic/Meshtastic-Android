#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "t_helpers.h"

void test(char * tfn)
{
    fn = tfn;
    printf("========================================\n");
    printf("test function: %s\n", fn);
    printf("========================================\n");
}

void test_failed()
{
    printf("Failed to calculate %s.\n", fn);
    exit(1);
}

void test_failed_s(char * expected, char * res)
{

    printf("Failed to calculate %s.\n", fn);

    printf("expected: %s\ngot: %s\n", expected, res);
    exit(1);
}

void test_failed_f(float expected, float res)
{

    printf("Failed to calculate %s.\n", fn);
    printf("expected: %f\ngot: %f\n", expected, res);
    exit(1);
}

