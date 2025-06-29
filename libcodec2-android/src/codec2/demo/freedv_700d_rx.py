#!/usr/bin/env python3
'''
  Demo receive program for FreeDV API 700D mode.

  cd ~/codec2/build_linux
  cat ../raw/ve9qrp_10s.raw | ./demo/freedv_700d_tx | ../demo/freedv_700d_rx.py | aplay -f S16_LE

  Credits: Thanks DJ2LS, xssfox, VK5QI
'''

import ctypes
from ctypes import *
import sys
import pathlib
import platform

if platform.system() == 'Darwin':
    libname = pathlib.Path().absolute() / "src/libcodec2.dylib" 
else:
    libname = pathlib.Path().absolute() / "src/libcodec2.so" 

# See: https://docs.python.org/3/library/ctypes.html
 
c_lib = ctypes.CDLL(libname)

c_lib.freedv_open.argype = [c_int]
c_lib.freedv_open.restype = c_void_p

c_lib.freedv_get_n_max_speech_samples.argtype = [c_void_p]
c_lib.freedv_get_n_max_speech_samples.restype = c_int

c_lib.freedv_nin.argtype = [c_void_p]
c_lib.freedv_nin.restype = c_int

c_lib.freedv_rx.argtype = [c_void_p, c_char_p, c_char_p]
c_lib.freedv_rx.restype = c_int

FREEDV_MODE_700D = 7 # from freedv_api.h             
freedv = cast(c_lib.freedv_open(FREEDV_MODE_700D), c_void_p)

n_max_speech_samples = c_lib.freedv_get_n_max_speech_samples(freedv)
speech_out = create_string_buffer(2*n_max_speech_samples)

while True:
    nin = c_lib.freedv_nin(freedv)
    demod_in = sys.stdin.buffer.read(nin*2)
    if len(demod_in) == 0: quit()
    nout = c_lib.freedv_rx(freedv, speech_out, demod_in)
    sys.stdout.buffer.write(speech_out[:nout*2])
