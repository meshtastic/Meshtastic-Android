#!/usr/bin/env bash
#
# David June 2019
# Tests 700D OFDM modem fading channel performance, using a simulated channel

results=$(mktemp)
fading_dir=$1
# BER should be around 4% for this test (it's better for larger interleavers but no one uses interleaving in practice)
ofdm_mod --in /dev/zero --ldpc 1 --testframes 60 --txbpf | ch - - --No -24 -f -10 --mpp --fading_dir $fading_dir | ofdm_demod --out /dev/null --testframes --verbose 2 --ldpc 1 2> $results
cat $results
cber=$(cat $results | sed -n "s/^Coded BER.* \([0-9..]*\) Tbits.*/\1/p")
python3 -c "import sys; sys.exit(0) if $cber<=0.05 else sys.exit(1)"
