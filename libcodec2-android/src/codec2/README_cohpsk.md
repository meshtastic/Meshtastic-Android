# README_cohpsk.md

## Introduction

## Quickstart

1. BER test in AWGN channel with just less that 2% average bit error rate:

   ```
    $ ./cohpsk_get_test_bits - 5600 | ./cohpsk_mod - - | ./ch - - --No -30 --Fs 7500 | ./cohpsk_demod - - | ./cohpsk_put_test_bits -
    <snip>
    SNR3k(dB):  3.41 C/No: 38.2 PAPR:  8.1 
    BER: 0.017 Nbits: 5264 Nerrors: 92

  ```

2. Plot some of the demod internal states, used to chase down freq offset problemL

   ```
    $ cd build_linux/src
    $ ./cohpsk_get_test_bits - 5600 | ./cohpsk_mod - -  | ./ch - - --No -40 -f -20 --Fs 7500 | ./cohpsk_demod -o cohpsk_demod.txt - - | ./cohpsk_put_test_bits -
    $ cd ../../octave
    $ octave --no-gui
    $ cohpsk_demod_plot("../build_linux/src/cohpsk_demod.txt")    
   ```

3. Run Octave<-> tests

   ```
   $ cd ~/codec2/build_linux/unittest
   $ ./tochpsk
   $ cd ~/codec2/octave
   $ octave --no-gui
   octave> tcohpsk
   ```
   
## References

## C Code

## Octave Scripts


