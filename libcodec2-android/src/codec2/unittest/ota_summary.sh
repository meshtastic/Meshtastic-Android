#!/usr/bin/env bash
#
# Summarise tests to date

CODEC2=${HOME}/codec2

function print_help {
    echo
    echo "Automated Over The Air (OTA) data test for FreeDV OFDM HF data modems"
    echo
    echo "  usage ./ota_summary.sh [-t]"
    echo
    echo "  -t create/update thumbnail directory"
    exit 0
}

thumbnails=0
while [[ $# -gt 0 ]]
do
key="$1"
case $key in
    -t)
        thumbnails=1
        shift
    ;;
    -h)
        print_help	
    ;;
esac
done

total_bytes=$(cat log.txt | tr -s ' ' | cut -f6 -d' ' | awk '$0==($0+0)' | paste -sd+ | bc)
printf "total bytes: %'d\n" ${total_bytes}

# collect SNR averages from log.txt and generate a histogram
ota_snrs=mktemp
cat log.txt | tr -s ' ' | cut -f7 -d' ' | awk '$0==($0+0)'| sed '/-nan/d'  > ${ota_snrs}
echo "warning('off', 'all'); \
      snr=load('${ota_snrs}'); \
      hist(snr); \
      print('snr_hist.png','-dpng'); \
      quit" | octave-cli -qf > /dev/null

# option to put small versions of spec/scatter in one dir

if [ $thumbnails -ne 0 ]; then
    mkdir -p thumbnails
    spec_files=$(find . -name spec.jpg -o -name spec.png)
    for f in $spec_files
    do
        d=$(echo $f | sed -r 's/\.(.*)\//\1_/')
        echo $f thumbnails${d}
        cp $f thumbnails${d}
    done   
fi
