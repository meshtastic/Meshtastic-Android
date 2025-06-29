#!/usr/bin/env bash
# ota_auto.sh
#
# Run a single automated test and log results

timestamp=$(date +"%F-%T")
mkdir -p $timestamp
start_dir=$(pwd)
cd $timestamp
../ota_test.sh "$@" >> log.txt 2>&1
cd $start_dir
kiwi_sdr=$(head -n 1 ${timestamp}/log.txt)
mode=$(head -n 2 ${timestamp}/log.txt | tail -n 1)
result=$(awk '/FrmGd/{getline; print}' ${timestamp}/log.txt)
printf "%s %-25s %s %s\n" $timestamp $kiwi_sdr $mode "$result" >> log.txt
