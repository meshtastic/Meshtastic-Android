#!/usr/bin/env bash
# Shell script version of ofdm_time_sync()
# David June 2019
# Tests ofdm modem sync time, using real, off air files

onerun=$(mktemp)
results=$(mktemp)

# bunch of runs at different time offsets into a sample off air file with low SNR and semi-staionary fading
for start_secs in `seq 0 29`;
do
    if [ "$1" = "700D" ]; then
        ofdm_demod --in ../wav/vk2tpm_004.wav --out /dev/null --verbose 2 --ldpc \
                   --start_secs $start_secs --len_secs 5 2>/dev/null > $onerun
    fi;
    if [ "$1" = "2020" ]; then
        ofdm_demod --mode 2020 --in ../wav/david4.wav --out /dev/null --verbose 2 --ldpc \
                   --start_secs $start_secs  --len_secs 5 2>/dev/null > $onerun
    fi;
    [ ! $? -eq 0 ] && { echo "error running ofdm_demod"; exit 1; }
    cat $onerun | sed -n "s/time_to_sync: \([0-9..]*\)/\1/p" >> $results
done
# a pass is we never take longer than 5 secs to sync (mean is much smaller)
python3 -c "
import sys; import numpy as np
x=np.loadtxt(\"$results\")
fails=sum(x == -1)
print(\"fails: %d mean: %5.2f var: %5.2f \" % (fails, np.mean(x), np.var(x)))
sys.exit(0) if fails==0 else sys.exit(1)
"
