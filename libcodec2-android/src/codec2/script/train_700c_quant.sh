#!/bin/bash -x
# train_700C_quant.sh
# David Rowe May 2019
#
# Training a Vector Quantiser (VQ) for Codec 2 700C
# This is a two stage VQ with 512 entries (9 bits) per stage
# Also used to support other VQ experiments, such as the effect of the
# post filter and an experimental equaliser, see octave/vq_700c_eq.m

SRC=~/Downloads/all_speech_8k.sw
CODEC2_BUILD=/home/david/codec2/build_linux
K=20
SAMPLES=~/tmp/c2vec_pass2

# train a new VQ
function train() {
  # c2enc can dump "feature vectors" that contain the current VQ input
  $CODEC2_BUILD/src/c2enc 700C $SRC /dev/null --mlfeat feat.f32
  # extract VQ input as training data, then train two stage VQ
  $CODEC2_BUILD/misc/extract -s 1 -e $K -t 41 feat.f32 stage0_in.f32
  $CODEC2_BUILD/misc/vqtrain stage0_in.f32 $K 512 vq_stage1.f32 -s 1e-3 -r stage1_in.f32
  $CODEC2_BUILD/misc/vqtrain stage1_in.f32 $K 512 vq_stage2.f32 -s 1e-3
}

# encode/decode a file with the stock codec2 700C VQ
function test_a() {
  b=$(basename "$1" .raw)
  $CODEC2_BUILD/src/c2enc 700C $1'.raw' - --var | $CODEC2_BUILD/src/c2dec 700C - - | sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$b'_a.wav'
}

# stock 700C VQ like test_a, but no postfilter
function test_a_nopf() {
  b=$(basename "$1" .raw)
  $CODEC2_BUILD/src/c2enc 700C $1'.raw' - --var | $CODEC2_BUILD/src/c2dec 700C - - --nopf | \
  sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$b'_a_nopf.wav'
}

# encode/decode a file with the new VQ we just trained above
function test_b() {
  b=$(basename "$1" .raw)
  $CODEC2_BUILD/src/c2enc 700C $1'.raw' - --loadcb 1 vq_stage1.f32 --loadcb 2 vq_stage2.f32 --var | \
  $CODEC2_BUILD/src/c2dec 700C - - --loadcb 1 vq_stage1.f32 --loadcb 2 vq_stage2.f32 | sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$b'_b.wav'
}

# just like b but no postfilter
function test_b_nopf() {
  b=$(basename "$1" .raw)
  $CODEC2_BUILD/src/c2enc 700C $1'.raw' - --loadcb 1 vq_stage1.f32 --loadcb 2 vq_stage2.f32 --var | \
  $CODEC2_BUILD/src/c2dec 700C - - --loadcb 1 vq_stage1.f32 --loadcb 2 vq_stage2.f32 $2 --nopf | \
  sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$b'_b_nopf.wav'
}

# pass an unquantised rate K vector through to the decoder as a control
function test_uq() {
  b=$(basename "$1" .raw)
  $CODEC2_BUILD/src/c2enc 700C $1'.raw' $b'.bin' --mlfeat $b'_feat.f32'
  $CODEC2_BUILD/misc/extract -s 1 -e 20 -t 41 $b'_feat.f32' $b'_ratek.f32'
  $CODEC2_BUILD/src/c2dec 700C $b'.bin' - --loadratek $b'_ratek.f32' | sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$b'_uq.wav'
}

# extract features for use in octave octave/vq_700c_eq.m
function feat() {
  RAW_FILES="../raw/hts1a ../raw/hts2a ../raw/vk5qi ../raw/cq_ref ../raw/ve9qrp_10s $HOME/Downloads/ma01_01 $HOME/Downloads/c01_01_8k $HOME/Downloads/cq_freedv_8k $HOME/Downloads/cq_freedv_8k_lfboost $HOME/Downloads/cq_freedv_8k_hfcut "
  for f in $RAW_FILES
  do
    b=$(basename "$f" .raw)
    $CODEC2_BUILD/src/c2enc 700C $f'.raw' $b'.bin' --mlfeat $b'_feat.f32'
  done
}

# generate a bunch of test samples for a listening test
function listen() {
  RAW_FILES="../raw/hts1a ../raw/hts2a ../raw/vk5qi ../raw/cq_ref ../raw/ve9qrp_10s $HOME/Downloads/ma01_01 $HOME/Downloads/c01_01_8k $HOME/Downloads/cq_freedv_8k"
  for f in $RAW_FILES
  do
    test_a $f
    test_a_nopf $f
    test_b $f
    test_b_nopf $f
    test_uq $f
  done
}

# Generate a bunch of test samples for VQ equalisation listening tests.  Assumes
# Octave has generated rate K quantised .f32 files 
function listen_vq_eq() {
  FILES="hts1a hts2a vk5qi cq_ref ve9qrp_10s ma01_01 c01_01_8k cq_freedv_8k cq_freedv_8k_lfboost cq_freedv_8k_hfcut"
  for f in $FILES
  do
    # try equaliser wth train_120 VQ
    $CODEC2_BUILD/src/c2dec 700C $f'.bin' - --loadratek $f'_vq2.f32' | sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$f'_vq2.wav'
    $CODEC2_BUILD/src/c2dec 700C $f'.bin' - --loadratek $f'_vq2_eq1.f32' | sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$f'_vq2_eq1.wav'
    $CODEC2_BUILD/src/c2dec 700C $f'.bin' - --loadratek $f'_vq2_eq2.f32' | sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$f'_vq2_eq2.wav'
    # try equaliser wth train_all_speech VQ
    #$CODEC2_BUILD/src/c2dec 700C $f'.bin' - --loadratek $f'_vq2_as.f32' | sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$f'_vq2_as.wav'
    #$CODEC2_BUILD/src/c2dec 700C $f'.bin' - --loadratek $f'_vq2_as_eq.f32' | sox -q -t .s16 -c 1 -r 8000 -b 16  - $SAMPLES/$f'_vq2_as_eq.wav'
  done
}

mkdir -p $SAMPLES

# choose which function to run here
#train
#listen
#feat
listen_vq_eq

