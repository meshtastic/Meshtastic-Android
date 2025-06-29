#!/usr/bin/env bash
#
# ofdm_phase_est_bw.sh
# David August 2019

# Tests 2020 OFDM modem phase est bandwidth mode option. Locking the
# phase est bandwidth to "high" is useful for High SNR channels with
# fast fading or high phase noise.  In this test we show that with
# high bandwidth phase est mode, the BER is < 5% for the "--mpp" (1
# Hz fading) channel model on a fairly high SNR channel.
#
# To run manually outside of ctest:
#   $ cd codec2/unittest
#   $ PATH=$PATH:../build_linux/src ./ofdm_phase_est_bw.sh

results=$(mktemp)
fading_dir=$1
# BER should be < 5% for this test
ofdm_mod --in /dev/zero --testframes 300 --mode 2020 --ldpc --verbose 0 | \
ch - - --No -40 -f 10 --ssbfilt 1 --mpp --fading_dir $fading_dir | \
ofdm_demod --out /dev/null --testframes --mode 2020 --verbose 2 --ldpc --bandwidth 1 2> $results
cat $results
cber=$(cat $results | sed -n "s/^Coded BER.* \([0-9..]*\) Tbits.*/\1/p")
python3 -c "import sys; sys.exit(0) if $cber<=0.05 else sys.exit(1)"
