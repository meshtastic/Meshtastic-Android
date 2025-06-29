#!/usr/bin/env bash
# tnc1_high_snr.sh
#
#  HF TNC use case test 1
#  + Send unidirectional frames at high SNR over an alsa loopback virtual sound card
#  + Using the sound card can take some time, so implemented as a service to run automatically in background

NAME=tnc1
CODEC2_PATH=${HOME}/codec2
PIDFILE_TX=/tmp/${NAME}_tx.pid
PIDFILE_RX=/tmp/${NAME}_rx.pid
LOGFILE=${NAME}.log
PATH=${CODEC2_PATH}/build_linux/src:${PATH}
DELAY="${DELAY:-500}"
MAX_RUN_TIME=3600
# we use single frame bursts, so BURSTS==FRAME
BURSTS=$2

function run_tx {
    bursts=$1
    delay=$2
    freedv_data_raw_tx DATAC0 /dev/zero - --testframes ${bursts} --bursts ${bursts} --delay ${delay} | aplay --device="plughw:CARD=CHAT2,DEV=1" -f S16_LE
}

function start_rx_background {
    arecord --device="plughw:CARD=CHAT2,DEV=0" -f S16_LE -d $MAX_RUN_TIME | freedv_data_raw_rx DATAC0 - /dev/null --framesperburst 1 --vv --testframes &
    echo $!>${PIDFILE_RX}
}

function stop_service {
    echo "service stopping - bye!" 1>&2
    if [ -e ${PIDFILE_RX} ]; then
        pid_rx=$(cat ${PIDFILE_RX})
        rm ${PIDFILE_RX}
        kill ${pid_rx}
    fi

    if [ -e ${PIDFILE_TX} ]; then
        pid_tx=$(cat ${PIDFILE_TX})
        rm ${PIDFILE_TX}
        kill ${pid_tx}
    fi
}

function check_running {
    if [ -e ${PIDFILE_TX} ]; then
        echo "Tx already running... pid: ${PIDFILE_TX}"
        exit 1
    fi
    if [ -e ${PIDFILE_RX} ]; then
        echo "Rx already running... pid: ${PIDFILE_RX}"
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
        ( start_rx_background && sleep 1 && run_tx ${BURSTS} ${DELAY} && stop_service ) 2>${LOGFILE} &
        echo $!>${PIDFILE_TX}
        echo "Results in ${LOGFILE}"
        ;;
    start_verbose)
        set -x
        check_running
        check_alsa_loopback
        # Show all outputs and log output to stderr rather than logfile
        verbose=1
        start_rx_background && sleep 1 && run_tx ${BURSTS} ${DELAY} && stop_service
        ;;
    stop)
        stop_service
        ;;
    restart)
        $0 stop
        $0 start
        ;;
    status)
        if [ -e ${PIDFILE_TX} ]; then
            echo ${NAME} is running, pid=`cat ${PIDFILE_TX}`
        else
            echo ${NAME} is NOT running
            exit 1
        fi
        ;;
    *)
    echo "Usage: $0 {start|start_verbose|stop|restart|status} NumFrames"
    echo "   $0 start_verbose 1       - 1 frame packet verbose, logs to stderr"
    echo "   $0 start 5               - 5 frames, run as service in background, logs sent to ${LOGFILE}"
esac

exit 0 
