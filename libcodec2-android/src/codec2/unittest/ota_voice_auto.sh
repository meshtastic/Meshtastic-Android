#!/usr/bin/env bash
# ota_voice_auto.sh
#
# Run a single automated voice test, files are put in a time stamped
# directory, and summarised in single line in the log file
# log_voice.txt.  Designed to be used from cron.

# use crontab -e to edit cron for currrent user, sample entry:

#       m     h   dom  mon  dow   command
#    */10  6-12    24    4    *   cd codec2/unittest; ./ota_voice_auto.sh ~/your_speech_file.s16 your.kiwi.sdr
 
timestamp=$(date +"%F-%T")
mkdir -p $timestamp
start_dir=$(pwd)
cd $timestamp
../ota_voice_test.sh "$@" > log.txt 2>&1
cd $start_dir
kiwi_sdr=$(head -n 1 ${timestamp}/log.txt)
mode=$(head -n 2 ${timestamp}/log.txt | tail -n 1)
Nsync=$(cat ${timestamp}/log.txt | grep Nsync | tr -s ' ' | cut -d' ' -f2)
SNRav=$(cat ${timestamp}/log.txt | grep SNRav | tr -s ' ' | cut -d' ' -f2)
printf "%s %-25s %s %3d %5.2f\n" $timestamp $kiwi_sdr $mode $Nsync $SNRav >> log_voice.txt
