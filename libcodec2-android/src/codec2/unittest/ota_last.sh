#!/usr/bin/env bash
#
# Present summary info from the n-th latest OTA HF data test

function print_help {
    echo
    echo "Summary of last automated Over The Air (OTA) test"
    echo
    echo "  usage ./ota_last.sh [options]"
    echo
    echo "    -a       show scatter diagram"
    echo "    -p       play the received wave file"
    echo "    -n N     select N-th from last file"
    echo "    -s       display spectrogram"
}

show_spec=0
show_scatter=0
play_file=0
N=1

while [[ $# -gt 0 ]]
do
key="$1"
case $key in
    -n)
        N="$2"
        shift
        shift
    ;;
    -a)
        show_scatter=1
        shift
    ;;
    -s)
        show_spec=1
        shift
    ;;
    -p)
        play_file=1
        shift
    ;;
    -h)
        print_help	
    ;;
esac
done

# cat the log from the selected test
directory=$(ls -td 2021* | head -n ${N} | tail -n 1)
echo ${directory}
cat ${directory}/log.txt

# optionally show a few plots

if [ $show_spec -eq 1 ]; then
    if [ -f ${directory}/spec.png ]; then
        eog ${directory}/spec.png
    else
        eog ${directory}/spec.jpg
    fi
fi

if [ $show_scatter -eq 1 ]; then
    eog ${directory}/scatter.png
fi

if [ $play_file -eq 1 ]; then
    play ${directory}/rx.wav
fi
