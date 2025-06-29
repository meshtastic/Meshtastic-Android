#!/bin/bash -x
# test_2020x.sh
# David Rowe Feb 2022
#
# Script to support testing experimental 2020A and 2020B modes and 700E control.

CODEC2_PATH=$HOME/codec2
PATH=$PATH:$CODEC2_PATH/build_linux/src:$CODEC2_PATH/build_linux/misc
FADING_DIR=$CODEC2_PATH/build_linux/unittest
No_AWGN=-20
No_AWGN_LOW=-17
No_Multipath=-25
serial=0
compressor_gain=6

# Approximation of Hilbert clipper analog compressor
function analog_compressor {
    input_file=$1
    output_file=$2
    gain=$3
    cat $input_file | ch - - 2>/dev/null | \
    ch - - --No -100 --clip 16384 --gain $gain 2>/dev/null | \
    # final line prints peak and CPAPR for SSB
    ch - - --clip 16384 |
    # manually adjusted to get similar peak levels for SSB and FreeDV
    sox -t .s16 -r 8000 -c 1 -v 0.85 - -t .s16 $output_file
}

function run_sim_ssb() {
    fullfile=$1
    filename=$(basename -- "$fullfile")
    extension="${filename##*.}"
    filename="${filename%.*}"
    channel=$2
    No=-100
    if [ "$channel" == "awgn" ]; then
        channel_opt=""
        No=$No_AWGN
    fi
    if [ "$channel" == "awgnlow" ]; then
        channel_opt=""
        No=$No_AWGN_LOW
    fi
    if [ "$channel" == "mpp" ] || [ "$channel" == "mpd" ]; then
        channel_opt='--'${channel}
        No=$No_Multipath
    fi
    fn=${filename}_ssb_${channel}.wav
    analog_compressor ${fullfile} ${filename}_ssb.raw ${compressor_gain}
    tmp=$(mktemp)
    ch ${filename}_ssb.raw $tmp --No $No ${channel_opt} --fading_dir ${FADING_DIR} 2>t.txt
    cat $tmp | sox -t .s16 -r 8000 -c 1 - ${fn} trim 0 6
    snr=$(cat t.txt | grep "SNR3k(dB):" | tr -s ' ' | cut -d' ' -f3)
    
    echo "<tr>"
    echo "<td><a href=\"${fn}\">${serial}</a></td><td>ssb</td><td></td><td></td><td>${channel}</td><td>${snr}</td>"
    echo "</tr>"
    serial=$((serial+1))
}

function run_sim() {
    fullfile=$1
    filename=$(basename -- "$fullfile")
    extension="${filename##*.}"
    filename="${filename%.*}"
    mode=$2
    if [ "$mode" == "700E" ] || [ "$mode" == "700D" ]; then
        rateHz=8000
    else
        rateHz=16000
    fi
    clip=$3
    if [ "$clip" == "clip" ]; then
        clipflag=1
        clip_html="yes"
    else
        clipflag=0
        clip_html="no"
    fi       
    channel=$4
    No=-100
    if [ "$channel" == "awgn" ]; then
        channel_opt=""
        No=$No_AWGN
    fi
    if [ "$channel" == "awgnlow" ]; then
        channel_opt=""
        No=$No_AWGN_LOW
    fi
    if [ "$channel" == "mpp" ] || [ "$channel" == "mpd" ]; then
        channel_opt='--'${channel}
        No=$No_Multipath
    fi

    indopt=$5
    indopt_flag=""
    indopt_html="no"
    indopt_str=""
    if [ "$indopt" == "indopt" ]; then
        indopt_flag="--indopt 1"
        indopt_str="_indopt"
        indopt_html="yes"
    fi
    if [ "$indopt" == "no_indopt" ]; then
        indopt_flag="--indopt 0"
        indopt_str="_no_indopt"
    fi
    
    fn=${filename}_${mode}_${clip}_${channel}${indopt_str}.wav
    tmp=$(mktemp)
    # note we let ch finish to get SNR stats (trim at end of sox causes an early termination)
    freedv_tx ${mode} ${fullfile} - --clip ${clipflag} ${indopt_flag} | \
    ch - $tmp --No $No ${channel_opt} --fading_dir ${FADING_DIR} 2>t.txt
    freedv_rx ${mode} ${indopt_flag} $tmp - | \
    sox -t .s16 -r ${rateHz} -c 1 - ${fn} trim 0 6
    snr=$(cat t.txt | grep "SNR3k(dB):" | tr -s ' ' | cut -d' ' -f3)

    echo "<tr>"
    echo "<td><a href=\"${fn}\">${serial}</a></td><td>${mode}</td><td>${clip_html}</td><td>${indopt_html}</td><td>${channel}</td><td>${snr}</td>"
    echo "</tr>"
    serial=$((serial+1))
}

# convert speech input file to format we need
SPEECH_IN_16k_WAV=~/Downloads/speech_orig_16k.wav 
SPEECH_IN_16k_RAW=speech_orig_16k.raw
SPEECH_IN_8k_RAW=speech_orig_8k.raw
sox $SPEECH_IN_16k_WAV -t .s16 $SPEECH_IN_16k_RAW
sox $SPEECH_IN_16k_WAV -t .s16 -r 8000 $SPEECH_IN_8k_RAW

echo "<html><table>"
echo "<tr><th>Serial</th><th>Mode</th><th>Clip</th><th>index_opt</th><th>Channel</th><th>SNR (dB)</th></tr>"

# run simulations

run_sim_ssb $SPEECH_IN_8k_RAW awgn
run_sim_ssb $SPEECH_IN_8k_RAW mpp
run_sim_ssb $SPEECH_IN_8k_RAW mpd

run_sim $SPEECH_IN_16k_RAW 2020 noclip clean
run_sim $SPEECH_IN_8k_RAW 700E clip clean

run_sim $SPEECH_IN_16k_RAW 2020 noclip awgn
run_sim $SPEECH_IN_16k_RAW 2020 noclip mpp
run_sim $SPEECH_IN_16k_RAW 2020 noclip mpd
run_sim $SPEECH_IN_16k_RAW 2020 clip awgn
run_sim $SPEECH_IN_16k_RAW 2020 clip mpp
run_sim $SPEECH_IN_16k_RAW 2020 clip mpd

run_sim $SPEECH_IN_16k_RAW 2020B clip awgn indopt
run_sim $SPEECH_IN_16k_RAW 2020B clip mpp  indopt
run_sim $SPEECH_IN_16k_RAW 2020B clip mpp  no_indopt
run_sim $SPEECH_IN_16k_RAW 2020B clip mpd  indopt
run_sim $SPEECH_IN_16k_RAW 2020B clip mpd  no_indopt

run_sim $SPEECH_IN_8k_RAW 700E clip awgn
run_sim $SPEECH_IN_8k_RAW 700E clip mpp
run_sim $SPEECH_IN_8k_RAW 700E clip mpd

# Low SNR samples
run_sim_ssb $SPEECH_IN_8k_RAW awgnlow
run_sim $SPEECH_IN_8k_RAW 700E clip awgnlow
run_sim $SPEECH_IN_16k_RAW 2020 clip awgnlow
run_sim $SPEECH_IN_16k_RAW 2020A clip awgnlow indopt

exit
