#!/usr/bin/env bash
# test_700c_eq.sh
# make sure 700C EQ is reducing VQ distortion

results=$(mktemp)

c2enc 700C ../raw/kristoff.raw /dev/null --var 2> $results
var=$(cat $results | sed -n "s/.*var: \([0-9..]*\) .*/\1/p")
c2enc 700C ../raw/kristoff.raw /dev/null --var --eq 2> $results
var_eq=$(cat $results | sed -n "s/.*var: \([0-9..]*\) .*/\1/p")
printf "var: %5.2f var_eq: %5.2f\n" $var $var_eq
python3 -c "import sys; sys.exit(0) if $var_eq<=$var else sys.exit(1)"
