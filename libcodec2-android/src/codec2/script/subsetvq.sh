#!/bin/bash
# subsetvq.sh
# David Rowe August 2021
#
# Script to support:
# 1. Subset VQ training and listening
# 1. Training Trellis Vector Quantiser for Codec 2 newamp1, supports octave/trellis.m
# 2. VQ sorting/optimisation experiments, octave/vq_compare.m

TRAIN=~/Downloads/all_speech_8k.sw
CODEC2_PATH=$HOME/codec2
PATH=$PATH:$CODEC2_PATH/build_linux/src:$CODEC2_PATH/build_linux/misc
K=20
Kst=2
Ken=16

# train a new VQ and generate quantised training material
function train() {
  fullfile=$TRAIN
  filename=$(basename -- "$fullfile")
  extension="${filename##*.}"
  filename="${filename%.*}"
  
  c2sim $fullfile --rateK --rateKout ${filename}.f32
  echo "ratek=load_f32('../build_linux/${filename}.f32',20); vq_700c_eq; ratek_lim=limit_vec(ratek, 0, 40); save_f32('../build_linux/${filename}_lim.f32', ratek_lim); quit" | \
  octave -p ${CODEC2_PATH}/octave -qf
  vqtrain ${filename}_lim.f32 $K 4096 vq_stage1.f32 -s 1e-3 --st $Kst --en $Ken

  # VQ the training file
  cat ${filename}_lim.f32 | vq_mbest --st $Kst --en $Ken -k $K -q vq_stage1.f32 > ${filename}_test.f32
}

function listen_vq() {
  vq_fn=$1
  dec=$2
  EbNodB=$3
  fullfile=$4
  filename=$(basename -- "$fullfile")
  extension="${filename##*.}"
  filename="${filename%.*}"
  
  fullfile_out=$5
  do_trellis=$6
  sox_options='-t raw -e signed-integer -b 16' 
  sox $fullfile $sox_options - | c2sim - --rateK --rateKout ${filename}.f32
  
  echo "ratek=load_f32('../build_linux/${filename}.f32',20); vq_700c_eq; ratek_lim=limit_vec(ratek, 0, 40); save_f32('../build_linux/${filename}_lim.f32', ratek_lim); quit" | \
  octave -p ${CODEC2_PATH}/octave -qf

  if [ "$do_trellis" -eq 0 ]; then
     echo "pkg load statistics; vq_compare(action='vq_file', '${vq_fn}', ${dec}, ${EbNodB}, '${filename}_lim.f32', '${filename}_test.f32'); quit" \ |
     octave -p ${CODEC2_PATH}/octave -qf
  else
     echo "pkg load statistics; trellis; vq_file('${vq_fn}', ${dec}, ${EbNodB}, '${filename}_lim.f32', '${filename}_test.f32'); quit" \ |
     octave -p ${CODEC2_PATH}/octave -qf
  fi
  
  if [ "$fullfile_out" = "aplay" ]; then
     sox $fullfile $sox_options - | c2sim - --rateK --rateKin ${filename}_test.f32 -o - | aplay -f S16_LE
  else
     sox $fullfile $sox_options - | c2sim - --rateK --rateKin ${filename}_test.f32 -o - | sox -t .s16 -r 8000 -c 1 - ${fullfile_out}
  fi
     
}

function print_help {
    echo
    echo "Trellis/VQ optimisation support script"
    echo
    echo "  usage ./train_trellis.sh [-x] [-t] [-v vq.f32 in.wav out.wav] [-e EbNodB] [-d dec]"
    echo
    echo "    -x                         debug mode; trace script execution"
    echo "    -t                         train VQ and generate a fully quantised version of training vectors"
    echo "    -v  vq.f32 in.wav out.wav  synthesise an output file out.wav from in.raw, using the VQ vq.f32"
    echo "    -v  vq.f32 in.wav aplay    synthesise output, play immediately using aplay, using the VQ vq.f32"
    echo "    -e  EbNodB                 Eb/No in dB for AWGn channel simulation (error insertion)"
    echo "    -d  dec                    decimation/interpolation rate"
    echo "    -r                         use trellis decoder"
    echo
    exit
}

# command line arguments to select function

if [ $# -lt 1 ]; then
    print_help
fi

do_train=0
do_vq=0
do_trellis=0
EbNodB=100
dec=1
POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"
case $key in
    -x)
        set -x	
        shift
    ;;
    -t)
        do_train=1
	shift
    ;;
    -v)
        do_vq=1
	vq_fn="$2"
	in_wav="$3"
	out_wav="$4"
	shift
	shift
	shift
	shift
    ;;
    -r)
        do_trellis=1
	shift
    ;;
    -d)
	dec="$2"
	shift
	shift	
	;;
    -e)
	EbNodB="$2"
	shift
	shift	
	;;
    -h)
        print_help	
    ;;
    *)
    POSITIONAL+=("$1") # save it in an array for later
    shift
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

if [ $do_train -eq 1 ]; then
    train
fi

if [ $do_vq -eq 1 ]; then
  listen_vq ${vq_fn} ${dec} ${EbNodB} ${in_wav} ${out_wav} ${do_trellis}
fi
