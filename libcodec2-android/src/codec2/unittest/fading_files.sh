#!/usr/bin/env bash
#
# Generate fading files used for channel simulation

output_path=$1
echo "Generating fading files ......"
cmd='cd ../octave; pkg load signal; ch_fading("'${output_path}'/fast_fading_samples.float", 8000, 1.0, 8000*60)'
octave --no-gui -qf --eval "$cmd"
[ ! $? -eq 0 ] && { echo "octave failed to run correctly .... exiting"; exit 1; }
cmd='cd ../octave; pkg load signal; ch_fading("'${output_path}'/faster_fading_samples.float", 8000, 2.0, 8000*60)'
octave --no-gui -qf --eval "$cmd"
[ ! $? -eq 0 ] && { echo "octave failed to run correctly .... exiting"; exit 1; }
exit 0

