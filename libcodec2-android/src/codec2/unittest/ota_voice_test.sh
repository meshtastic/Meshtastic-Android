#!/usr/bin/env bash
# ota_voice_test.sh
#
# Automated Over The Air (OTA) voice test for FreeDV HF voice modes
#
# 1. Build codec2
# 2. Install kiwclient:
#    cd ~ && git clone git@github.com:jks-prv/kiwiclient.git
# 3. Install Hamlib cli tools, and add user to dialout group:
#      sudo adduser david dialout
# 4. To test rigctl:
#      echo "m" | rigctl -m 361 -r /dev/ttyUSB0
# 5. Adjust Tx drive so ALC is just being tickled, set desired RF power:
# ../build_linux/src/freedv_tx 2020 ~/Downloads/speech_orig_16k.wav - | aplay -f S16_LE --device="plughw:CARD=CODEC,DEV=0"
# 6. Sample command line:
#      ./ota_voice_test.sh ~/Downloads/speech_orig_16k.wav -m 700E -i ~/Downloads/vk5dgr_testing_8k.wav sdr.ironstonerange.com -p 8074

MY_PATH=`dirname $0`
BUILD_PATH=`echo $MY_PATH/../build_*/src`
PATH=${PATH}:${BUILD_PATH}:${HOME}/kiwiclient
CODEC2=${MY_PATH}/..

kiwi_url=""
port=8074
freq_kHz="7177"
tx_only=0
Nbursts=5
mode="700D"
model=361
gain=6
serialPort="/dev/ttyUSB0"
rxwavefile=0
soundDevice="plughw:CARD=CODEC,DEV=0"
txstats=0
stationid=""

function print_help {
    echo
    echo "Automated Over The Air (OTA) voice test for FreeDV HF voice modes"
    echo
    echo "  usage ./ota_voice_test.sh [options] SpeechWaveFile [kiwi_url]"
    echo "  or:"
    echo "  usage ./ota_voice_test.sh -r rxWaveFile"
    echo
    echo "    -c dev                    The sound device (in ALSA format on Linux, CoreAudio for macOS)"
    echo "    -d                        debug mode; trace script execution"
    echo "    -g                        SSB (analog) compressor gain"
    echo "    -i StationIDWaveFile      Prepend this file to identify transmission (should be 8KHz mono)"
    echo "    -m mode   700c|700d|700e"
    echo "    -o model                  select radio model number ('rigctl -l' to list)"
    echo "    -p port                   kiwi_url port to use (default 8073)."
    echo "    -r                        Rx wave file mode: Rx process supplied rx wave file"
    echo "    -s SerialPort             The serial port (or hostname:port) to control SSB radio,"
    echo "                              default /dev/ttyUSB0"
    echo "    -t                        Tx only, useful for manually observing SDRs"
    echo "    -x                        Generate tx.wav file and exit"
    echo
    exit
}

# Approximation of Hilbert clipper type compressor.  Could do with some HF boost
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

function run_rigctl {
    command=$1
    model=$2
    echo $command | rigctl -m $model -r $serialPort > /dev/null
    if [ $? -ne 0 ]; then
        echo "Can't talk to Tx"
        clean_up
        exit 1
    fi
}

function clean_up {
    echo "killing KiwiSDR process"
    kill ${kiwi_pid}
    wait ${kiwi_pid} 2>/dev/null
    exit 1
}

function process_rx {
    echo "Process receiver sample"
    rx=$1
    # generate spectrogram
    echo "pkg load signal; warning('off', 'all'); \
          s=load_raw('${rx}'); \
          plot_specgram(s, 8000, 200, 3000); print('spec.jpg', '-djpg'); \
          quit" | octave-cli -p ${CODEC2}/octave -qf > /dev/null
    # attempt to decode
    freedv_rx ${mode} ${rx} - -v --passthroughgain 1.0 2>rx_stats.txt | sox -t .s16 -r $speechFs -c 1 - rx_freedv.wav
    cat rx_stats.txt | tr -s ' ' | cut -f5 -d' ' | awk '$0==($0+0)' > sync.txt
    cat rx_stats.txt | tr -s ' ' | cut -f10 -d' ' | awk '$0==($0+0)' > snr.txt
    # time domain plot of output speech, SNR, and sync
    echo "pkg load signal; warning('off', 'all'); \
          s=load_raw('rx_freedv.wav'); snr=load('snr.txt'); sync=load('sync.txt'); \
          subplot(211); plot(s); subplot(212); x=1:length(sync); plotyy(x,snr,x,sync); \
          ylim([-5 15]); ylabel('SNR (dB)'); grid; \
          print('time_snr.jpg', '-djpg'); \
          printf('Nsync: %3d\n', sum(sync)); \
          snr_valid = snr(find(snr != -5.0)); \
          if length(snr_valid) printf('SNRav: %5.2f\n', mean(snr_valid)); else printf('SNRav: %5.2f\n', -5); end;
          quit" | octave-cli -p ${CODEC2}/octave -qf
}

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"
case $key in
    -d)
        set -x	
        shift
    ;;
    -f)
        freq_kHz="$2"	
        shift
        shift
    ;;
    -g)
        gain="$2"	
        shift
        shift
    ;;
    -i)
        stationid="$2"	
        shift
        shift
    ;;
    -o)
        model="$2"	
        shift
        shift
    ;;
    -m)
        mode="$2"	
        shift
        shift
    ;;
    -p)
        port="$2"	
        shift
        shift
    ;;
    -t)
        tx_only=1	
        shift
    ;;
    -r)
        rxwavefile=1	
        shift
    ;;
    -x)
        txstats=1	
        shift
    ;;
    -c)
        soundDevice="$2"
        shift
        shift
    ;;
    -s)
        serialPort="$2"
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

# determine sample rate of freedv_tx/freedv_rx
speechFs=8000
if [ "$mode" == "2020" ] || [ "$mode" == "2020B" ]; then
   speechFs=16000
fi

if [ $rxwavefile -eq 1 ]; then
    process_rx $1
    exit 0
fi

speechfile="$1"
if [ ! -f $speechfile ]; then
    echo "Can't find input speech wave file: ${speechfile}!"
    exit 1
fi

if [ $tx_only -eq 0 ]; then
    if [ $# -lt 1 ]; then
        print_help
    fi
    kiwi_url="$2"
    echo $kiwi_url
fi

# create Tx file ------------------------
echo $mode

# create compressed analog
speechfile_raw_8k=$(mktemp)
comp_in=$(mktemp)
speech_comp=$(mktemp)
speech_freedv=$(mktemp)
# If 16kHz input files for 2020x, we need an 8kHz version for SSB
sox $speechfile -r 8000 -t .s16 -c 1 $speechfile_raw_8k
if [ -z $stationid ]; then
    cp $speechfile_raw_8k $comp_in
else
    # append station ID and apply analog compression
    stationid_raw_8k=$(mktemp)
    sox $stationid -r 8000 -t .s16 -c 1 $stationid_raw_8k
    cat  $stationid_raw_8k $speechfile_raw_8k> $comp_in
fi
analog_compressor $comp_in $speech_comp $gain

# create modulated FreeDV, with compressor enabled
sox $speechfile -t .s16 -r $speechFs - | freedv_tx $mode - $speech_freedv --clip 1
cat $speech_comp $speech_freedv > tx.raw
sox -t .s16 -r 8000 -c 1 tx.raw tx.wav

if [ $txstats -eq 1 ]; then
    # ch just used to monitor observe peak and RMS level
    ch $speech_freedv /dev/null
    # time domain plot of tx signal
    echo "pkg load signal; warning('off', 'all'); \
          s=load_raw('tx.raw'); plot(s); \
          print('tx.jpg', '-djpg'); \
          quit" | octave-cli -p ${CODEC2}/octave -qf > /dev/null
    exit 0
fi

# kick off KiwiSDR ----------------------------

usb_lsb=$(python3 -c "print('usb') if ${freq_kHz} >= 10000 else print('lsb')")
if [ $tx_only -eq 0 ]; then
    # clean up any kiwiSDR processes if we get a ctrl-C
    trap clean_up SIGHUP SIGINT SIGTERM

    echo -n "waiting for KiwiSDR "
    # start recording from remote kiwisdr
    kiwi_stdout=$(mktemp)
    kiwirecorder.py -s $kiwi_url -p ${port} -f $freq_kHz -m ${usb_lsb} -r 8000 --filename=rx --time-limit=300 >$kiwi_stdout &
    kiwi_pid=$!

    # wait for kiwi to start recording
    timeout_counter=0
    until grep -q -i 'Block: ' $kiwi_stdout
    do
        timeout_counter=$((timeout_counter+1))
        if [ $timeout_counter -eq 10 ]; then
            echo "can't connect to ${kiwi_url}"
            kill ${kiwi_pid}
            wait ${kiwi_pid} 2>/dev/null
            exit 1
        fi
        echo -n "."
        sleep 1
    done
    echo
fi

# transmit using local SSB radio
echo "Tx data signal"
freq_Hz=$((freq_kHz*1000))
usb_lsb_upper=$(echo ${usb_lsb} | awk '{print toupper($0)}')
run_rigctl "\\set_mode PKT${usb_lsb_upper} 0" $model
run_rigctl "\\set_freq ${freq_Hz}" $model
run_rigctl "\\set_ptt 1" $model
if [ `uname` == "Darwin" ]; then
    AUDIODEV="${soundDevice}" play -t raw -b 16 -c 1 -r 8000 -e signed-integer --endian little tx.raw 
else
    aplay --device="${soundDevice}" -f S16_LE tx.raw 2>/dev/null
fi
if [ $? -ne 0 ]; then
    run_rigctl "\\set_ptt 0" $model
    clean_up
    echo "Problem running aplay!"
    echo "Is ${soundDevice} configured as the default sound device in Settings-Sound?"
    exit 1
fi
run_rigctl "\\set_ptt 0" $model

if [ $tx_only -eq 0 ]; then
    sleep 2
    echo "Stopping KiwiSDR"
    kill ${kiwi_pid}
    wait ${kiwi_pid} 2>/dev/null

    process_rx rx.wav
fi

