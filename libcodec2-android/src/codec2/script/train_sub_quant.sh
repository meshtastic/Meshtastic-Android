#!/bin/bash -x
# train_sub_quant.sh
# David Rowe May 2021
#
# Training and testing Vector Quantisers (VQ) for Codec 2 newamp1, in
# this case training on a subset

TRAIN=~/Downloads/all_speech_8k.sw
CODEC2_PATH=$HOME/codec2
PATH=$PATH:$CODEC2_PATH/build_linux/src:$CODEC2_PATH/build_linux/misc
K=20
Kst=2
Ken=16

# train a new VQ
function train() {
  fullfile=$TRAIN
  filename=$(basename -- "$fullfile")
  extension="${filename##*.}"
  filename="${filename%.*}"
  
  c2sim $fullfile --rateK --rateKout ${filename}.f32
  echo "ratek=load_f32('../build_linux/${filename}.f32',20); vq_700c_eq; ratek_lim=limit_vec(ratek, 0, 40); save_f32('../build_linux/${filename}_lim.f32', ratek_lim); quit" | \
  octave -p ${CODEC2_PATH}/octave -qf
  vqtrain ${filename}_lim.f32 $K 4096 vq_stage1.f32 -s 1e-3 --st $Kst --en $Ken
}

function listen() {
  fullfile=$1
  filename=$(basename -- "$fullfile")
  extension="${filename##*.}"
  filename="${filename%.*}"
  
  c2sim $fullfile --rateK --rateKout ${filename}.f32
  echo "ratek=load_f32('../build_linux/${filename}.f32',20); vq_700c_eq; ratek_lim=limit_vec(ratek, 0, 40); save_f32('../build_linux/${filename}_lim.f32', ratek_lim); quit" | \
  octave -p ${CODEC2_PATH}/octave -qf
  cat ${filename}_lim.f32 | vq_mbest --st $Kst --en $Ken -k $K -q vq_stage1.f32 > ${filename}_test.f32
  c2sim $fullfile --rateK --rateKin ${filename}_test.f32 -o - | sox -t .s16 -r 8000 -c 1 - ${filename}_sub.wav
  c2sim $fullfile --rateK --newamp1vq -o - | sox -t .s16 -r 8000 -c 1 - ${filename}_newamp1.wav
}

# choose which function to run here
train
# these two samples are inside training database
listen ~/Downloads/fish_8k.sw
listen ~/Downloads/cap_8k.sw
# two samples from outside training database
listen $CODEC2_PATH/raw/big_dog.raw
listen $CODEC2_PATH/raw/hts2a.raw
# these two samples are inside training database, but with LPF at 3400 Hz outside of subset
listen ~/Downloads/fish_8k_lp.sw
listen ~/Downloads/cap_8k_lp.sw

