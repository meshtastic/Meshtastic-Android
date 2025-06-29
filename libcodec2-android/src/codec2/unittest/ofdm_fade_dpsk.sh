#!/usr/bin/env bash
#
# David Sep 2019
# Tests 2020 OFDM modem fading channel performance in DPSK mode, using a simulated faster (2Hz) high SNR fading channel

fading_dir=$1
results=$(mktemp)

# Coded BER should be < 1% for this test
ofdm_mod --in /dev/zero --testframes 300 --mode 2020 --ldpc --verbose 1 --dpsk | \
ch - - --No -40 -f 10 --ssbfilt 1 --mpd --fading_dir $fading_dir --multipath_delay 2 | \
ofdm_demod --out /dev/null --testframes --mode 2020 --verbose 1 --ldpc --dpsk 2> $results
cat $results
cber=$(cat $results | sed -n "s/^Coded BER.* \([0-9..]*\) Tbits.*/\1/p")
python3 -c "import sys; sys.exit(0) if $cber<=0.05 else sys.exit(1)"
