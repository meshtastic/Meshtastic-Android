#!/usr/bin/env bash
#
# Summarise tests to date into one directory to allow easy browsing

src=/home/david/Downloads/speech_orig_16k.wav
length_src=$(sox $src -n stat 2>&1  | grep Length | sed -r 's/.*\:\s*//')

dir=voice_summary
mkdir -p ${dir}
time_snr_files=$(find . -name time_snr.jpg | sort)
p=$(pwd)
serial=0

echo "<table>"
echo "<tr>"
echo "<th>Serial</th><th>Mode</th><th>KiwiSDR</th><th>Rx</th><th>AnDV</th><th>DV</th><th>Spectrogram</th><th>SNR</th>"
echo "</tr>"

for f in $time_snr_files
do
    d=$(echo $f | sed -r 's/\.\/(.*)\/time_snr.jpg/\1/')
    sdr_url=$(head ${d}/log.txt -n 1)
    sdr="unk"
    case $sdr_url in
        "kiwisdr.areg.org.au")
            sdr="areg"
            ;;
        "sdr-amradioantennas.com")
            sdr="am"
            ;;
        "vk6qs.proxy.kiwisdr.com")
            sdr="vk6qs"
            ;;
        "sdr.ironstonerange.com")
            sdr="iron"
            ;;
        "kk6pr.ddns.net")
            sdr="kk6pr"
            ;;
        "kiwisdr.owdjim.gen.nz")
            sdr="marahau"
            ;;
        "kiwisdrzl1kfm.ddns.net")
            sdr="zl1kfm"
            ;;
        *)
            echo "Unknown Kiwi SDR"
            ;;
    esac
    mode=$(head ${d}/log.txt -n 2 | tail -n 1)
    serial_str=$(printf "%04d" $serial)
    #echo $serial_str $d $sdr $mode
    echo "<tr>"
    echo "<td>$serial</td>"
    echo "<td>$mode</td>"
    echo "<td>$sdr</td>"

    f=${dir}/${serial_str}_${d}_${sdr}_${mode}
    f1=${serial_str}_${d}_${sdr}_${mode}

    cp ${d}/rx.wav ${f}_rx.wav
    echo "<td><a href=\"${f1}_rx.wav\">Rx</td>"

    cp ${d}/rx_freedv.wav ${f}_rx_freedv.wav
    echo "<td><a href=\"${f1}_rx_freedv.wav\">AnDV</td>"       

    length_f=$(sox ${f}_rx_freedv.wav -n stat 2>&1  | grep Length | sed -r 's/.*\:\s*//')
    start_dv=$(python -c "print(${length_f}-${length_src}-2)")
    sox ${d}/rx_freedv.wav ${f}_rx_freedv_dv.wav trim $start_dv $length_src
    echo "<td><a href=\"${f1}_rx_freedv_dv.wav\">DV</td>"    

    cp ${d}/spec.jpg ${f}_spec.jpg
    echo "<td><img src=\"${f1}_spec.jpg\" width="200" height="200" /></td>"    
    cp ${d}/time_snr.jpg ${f}_time_snr.jpg
    echo "<td><img src=\"${f1}_time_snr.jpg\" width="200" height="200" /></td>"    
    echo "</tr>"
    serial=$((serial + 1))
done   

echo "</table>"
