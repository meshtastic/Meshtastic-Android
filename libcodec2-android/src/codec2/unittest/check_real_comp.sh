#!/usr/bin/env bash
# check_real_comp.sh
# Check the output of freedv_tx() and the real part of freedv_comptx() match,
# as they use different code paths. Run from codec2/unittest, set path to
# include codec2/build/misc and codec2/build/unittest

set -x
cat ../raw/ve9qrp_10s.raw | freedv_700d_tx > tx_700d.int16
cat ../raw/ve9qrp_10s.raw | freedv_700d_comptx > tx_700d.iq16

echo "tx_real=load_raw('tx_700d.int16'); tx_comp=load_raw('tx_700d.iq16'); \
      tx_comp=tx_comp(1:2:end)+j*tx_comp(2:2:end); \
      diff = sum(real(tx_comp)-tx_real); printf('diff: %f\n', diff); \
      if diff < 1, quit(0), end; \
      quit(1)" | octave-cli -p ../octave -qf
