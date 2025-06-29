#!/usr/bin/env bash
# tnc4_high_snr_ping.sh
#
#  HF TNC use case test 4
#  + Terminal 1 sends frames to Terminal 2
#  + Terminal 2 receives frames and re-transmits them back to Terminal 1
#  + Terminal 1 and 2 count number of frames received (see logfiles)
#  + The modem samples are sent over virtual sound cards, this runs in real time, which
#    can be slow for tests involving many packets.  Therefore this test is implemented as a
#    service script.

NAME=tnc4
CODEC2_PATH=${HOME}/codec2
PIDFILE_TX1=/tmp/${NAME}_tx1.pid
PIDFILE_RX1=/tmp/${NAME}_rx1.pid
PIDFILE_RX2=/tmp/${NAME}_rx2.pid
LOGFILE1=${NAME}_1.log
LOGFILE2=${NAME}_2.log
PATH=${CODEC2_PATH}/build_linux/src:${PATH}
DELAY="${DELAY:-500}"
MAX_RUN_TIME=3600
# in this version we use single frame bursts, so BURSTS==FRAMES
BURSTS=$2
MODE=DATAC0

function tx1 {
    freedv_data_raw_tx ${MODE} /dev/zero - --testframes ${BURSTS} --bursts ${BURSTS} --delay ${DELAY} | aplay --device="plughw:CARD=CHAT2,DEV=1" -f S16_LE
}

function rx2_background {
    # re-transmit any frames we receive
    ( arecord --device="plughw:CARD=CHAT2,DEV=0" -f S16_LE -d $MAX_RUN_TIME | \
    freedv_data_raw_rx ${MODE} - - --framesperburst 1 --vv --testframes | \
    freedv_data_raw_tx ${MODE} - - --delay ${DELAY} | \
    aplay --device="plughw:CARD=CHAT1,DEV=1" -f S16_LE ) 2>${LOGFILE2} & 
    # killing arecord kills the entire pipeline
    echo $(pidof arecord)>${PIDFILE_RX2}
}

function rx1_background {
    arecord --device="plughw:CARD=CHAT1,DEV=0" -f S16_LE -d $MAX_RUN_TIME | freedv_data_raw_rx ${MODE} - /dev/null --framesperburst 1 --vv --testframes &
    echo $!>${PIDFILE_RX1}
}

function stop_process {
    if [ -e ${1} ]; then
        pid=$(cat ${1})
        rm ${1}
        kill ${pid}
    fi    
}

function stop_service {
    echo "service stopping - bye!" 1>&2
    stop_process ${PIDFILE_RX1}
    stop_process ${PIDFILE_RX2}
    stop_process ${PIDFILE_TX1}
}

function check_running {
    if [ -e ${PIDFILE_TX1} ]; then
        echo "Tx already running... pid: ${PIDFILE_TX1}"
        exit 1
    fi
    if [ -e ${PIDFILE_RX1} ]; then
        echo "Rx1 already running... pid: ${PIDFILE_RX1}"
        exit 1
    fi
    if [ -e ${PIDFILE_RX2} ]; then
        echo "Rx2 already running... pid: ${PIDFILE_RX2}"
        exit 1
    fi
}

function check_alsa_loopback {
    lsmod | grep snd_aloop >> /dev/null
    if [ $? -eq 1 ]; then
      echo "ALSA loopback device not present.  Please install with:"
      echo
      echo "  sudo modprobe snd-aloop index=1,2 enable=1,1 pcm_substreams=1,1 id=CHAT1,CHAT2"
      exit 1
    fi  
}

case "$1" in 
    start)
        check_running
        check_alsa_loopback
        rx2_background
        ( rx1_background && sleep 1 && tx1 && sleep 5 && stop_service ) 2>${LOGFILE1} &
        echo $!>${PIDFILE_TX1}
        echo "Results for terminal 1 in ${LOGFILE1} and terminal 2 in ${LOGFILE2}"
        ;;
    stop)
        stop_service
        ;;
    restart)
        $0 stop
        $0 start
        ;;
    status)
        if [ -e ${PIDFILE_TX1} ]; then
            echo ${NAME} is running, pid=`cat ${PIDFILE_TX1}`
        else
            echo ${NAME} is NOT running
            exit 1
        fi
        ;;
    *)
    echo "Usage: $0 {start|start_verbose|stop|restart|status} NumFrames"
    echo "   $0 start 5    - test ping over 5 frames; logs sent to ${LOGFILE1} and ${LOGFILE2}"
esac

exit 0 
