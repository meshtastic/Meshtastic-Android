#!/usr/bin/python3

""" Plot phi0, and (later) anaylze approximations

"""

import numpy as np
import matplotlib.pyplot as plt

import math
import itertools
import sys

def impl_reference(x):
    if (x< 9.08e-5 ): r = 10
    else:
        z = math.exp(x)
        r = math.log( (z+1) / (z-1) )
    return(r)

####
# Table T1

t1_tbl = []  # A list of tuples (x, r)
t1_adj = 0.50  # Offset ratio for step of 1, estimate

def t1_loop(upper, lower, step):
  for i in range((int)(upper * step), (int)(lower * step), -1):
    x = i / step
    z = math.exp(x + (t1_adj / step))
    r = math.log((z+1)/(z-1))
    t1_tbl.append( (x, r) )
    print(x, r)

# 9:5 step 1
t1_loop(9, 4.999, 2)

# 5:1 step 1/16
t1_loop(4.999, 0.999, 16)

# 1/(2^0.5), 1/(2^1), 1/(2^1.5), ...
for i in range(1, 28):
    e = (2 ** (i/2))
    x = 1.0 / e
    z = math.exp(x + (0.19 / e))
    r = math.log((z+1)/(z-1))
    t1_tbl.append( (x, r) )

def impl_t1(x):
    if (x > 10): return(0)
    else:
        for t in t1_tbl:
            if (x > t[0]): return(t[1])
    return(10)



##########################
# Plot, scanning from 10 to 0
x_vals = np.logspace(1, -4, 2000, endpoint=False)
ref_vals = []
t1_vals = []
for x in x_vals:
    ref_vals.append(impl_reference(x))
    t1_vals.append(impl_t1(x))

# Sum errors
errsum = errsum2 = 0
for i in range(len(ref_vals)):
    error = t1_vals[i] - ref_vals[i]
    errsum += error
    errsum2 += error * error
print("Net error {}".format(errsum))
print("avg error {}".format(errsum/len(ref_vals)))
print("rms error {}".format(math.sqrt(errsum2/len(ref_vals))))

plt.xscale('log')
plt.plot(x_vals, ref_vals, 'g', x_vals, t1_vals, 'r')
plt.show()

