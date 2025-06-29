# FreeDV Technology

FreeDV is an open source digital voice protocol that integrates modems, speech codecs, and FEC.

On transmit, FreeDV converts speech to a modem signal you can send over a radio channel.  On receive, FreeDV takes off air modem signals and converts them to speech samples.

FreeDV is available as a GUI application, an open source library (FreeDV API), and in hardware (the SM1000 FreeDV adaptor).  FreeDV is part of the Codec 2 project.

This document gives an overview of the technology inside FreeDV, and some additional notes on building/using the FreeDV 2020 and 2400A/2400B modes.

![FreeDV mode knob](http://www.rowetel.com/images/codec2/mode_dv.jpg)

## FreeDV API

The general programming model is:
  ```
  speech samples -> FreeDV encode -> modulated samples (send over radio) -> FreeDV decode -> speech samples
  ```

The `codec2/demo` directory provides simple FreeDV API demo programs written in C and Python to help you get started, for example:

```
cd codec2/build_linux
cat ../raw/ve9qrp_10s.raw | ./demo/freedv_700d_tx | ./demo/freedv_700d_rx | aplay -f S16_LE
```

The current demo programs are as follows:

| Program | Description |
| --- | --- |
| [c2demo.c](demo/c2demo.c) | Encode and decode speech with Codec 2 |
| [freedv_700d_tx.c](demo/freedv_700d_tx.c) | Transmit a voice signal using the FreeDV API |
| [freedv_700d_rx.c](demo/freedv_700d_rx.c) | Receive a voice signal using the FreeDV API |
| [freedv_700d_rx.py](demo/freedv_700d_rx.py) | Receive a voice signal using the FreeDV API in Python |
| [freedv_datac1_tx.c](demo/freedv_datac1_tx.c) | Transmit raw data frames using the FreeDV API |
| [freedv_datac1_rx.c](demo/freedv_datac1_rx.c) | Receive raw data frames using the FreeDV API |
| [freedv_datac0c1_tx.c](demo/freedv_datac0c1_tx.c) | Transmit two types of raw data frames using the FreeDV API |
| [freedv_datac0c1_rx.c](demo/freedv_datac0c1_rx.c) | Receive two types of raw data frames using the FreeDV API |

So also [freedv_api.h](src/freedv_api.h) and [freedv_api.c](src/freedv_api.c) for the full list of API functions.  Only a small set of these functions are needed for basic FreeDV use, please see the demo programs for minimal examples.

The full featured command line demo programs [freedv_tx.c](src/freedv_tx.c) & [freedv_rx.c](src/freedv_rx.c) demonstrate many features of the API:

```
$ ./freedv_tx 1600 ../../raw/hts1.raw - | ./freedv_rx 1600 - - | aplay -f S16_LE
$ cat freedv_rx_log.txt
```

Speech samples are input to the API as 16 bit signed integers.  Modulated samples can be in real 16 bit signed integer or complex float. The expected sample rates can be found with `freedv_get_speech_sample_rate()` and `freedv_get_modem_sample_rate()`. These are typically 8000 Hz but can vary depending on the current FreeDV mode.

## FreeDV HF Modes

These are designed for use with a HF SSB radio.

| Mode | Date | Codec | Modem | RF BW | Raw bits/s | FEC | Text bits/s | SNR min | Multipath |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1600  | 2012 | Codec2 1300 | 14 DQPSK + 1 DBPSK pilot carrier | 1125 | 1600 | Golay (23,12) | 25 | 4 | poor |
| 700C  | 2017 | Codec2 700C | 14 carrier coherent QPSK + diversity | 1500 | 1400 | - | - | 2 | good |
| 700D  | 2018 | Codec2 700C | 17 carrier coherent OFDM/QPSK | 1000 | 1900 | LDPC (224,112)  | 25 | -2 | fair |
| 700E  | 2020 | Codec2 700C | 21 carrier coherent OFDM/QPSK | 1500 | 3000 | LDPC (112,56)  | 25 | 1 | good |
| 2020  | 2019 | LPCNet 1733 | 31 carrier coherent OFDM/QPSK | 1600 | 3000 | LDPC (504,396)  | 22.2 | 2 | poor |
| 2020A | 2022 | LPCNet 1733 | 31 carrier coherent OFDM/QPSK | 1600 | 3000 | LDPC (504,396) unequal  | 22.2 | 2 | fair |
| 2020B | 2022 | LPCNet 1733 | 29 carrier coherent OFDM/QPSK | 2100 | 4100 | LDPC (112,56) unequal  | 22.2 | 3 | good |

Notes:

1. *Raw bits/s* is the number of payload bits/s carried over the channel by the modem.  This consists of codec frames, FEC parity bits, unprotected text, and synchronisation information such as pilot and unique word bits.  The estimates are open to interpretation for the OFDM waveforms due to pilot symbol and cyclic prefix considerations (see spreadsheet).

1. *RF BW* is the bandwidth of the RF signal over the air.  FreeDV is more bandwidth efficient than SSB.

1. *Multipath* is the relative resilience of the mode to multipath fading, the biggest problem digital voice faces on HF radio channels.  Analog SSB would be rated as "good".

1. *Text* is a side channel for low bit rate text such as your location and call sign.  It is generally unprotected by FEC, and encoded with varicode. The exception is if reliable_text support is turned on (see reliable_text.c/h); this results in text protected by LDPC(112,56) FEC with interleaving.

1. *SNR Min* is for an AWGN channel (no multipath/fading).

1. All of the modems use multiple parallel carriers running at a low symbol rate of around 50 Hz.  This helps combat the effects of multipath channels.

1. Some of the Codec 2 modes (2400/1300/700C etc) happen to match the name of a FreeDV mode.  For example FreeDV 700C uses Codec 2 700C for voice compression. However FreeDV 700D *also* uses Codec 2 700C for voice compression, but has a very different modem waveform to FreeDV 700C.  Sorry for the confusing nomenclature.

1. Coherent demodulation gives much better performance than differential, at the cost of some additional complexity.  Pilot symbols are transmitted regularly to allow the demod to estimate the reference phase of each carrier.

1. The 1600 and 700C waveforms use parallel tone modems, later modes use OFDM.  OFDM gives tighter carrier packing which allows higher bit rates, but tends to suffer more from frequency offsets and delay spread.

1. At medium to high SNRs, FreeDV 700C performs well (better than 700D) on fast fading multipath channels with large delay spread due its parallel tone design and high pilot symbol rate.  It employs transmit diversity which delivers BER performance similar to modes using FEC.  FreeDV 700C also has a short frame (40ms), so syncs fast with low latency.  Fast sync is useful on marginal channels that move between unusable and barely usable.

1. FreeDV 700D uses an OFDM modem and was optimised for low SNR channels, with strong FEC but a low pilot symbol rate and modest (2ms) cyclic prefix which means its performance degrades on multipath channels with fast (> 1Hz) fading.  The use of strong FEC makes this mode quite robust to other channel impairments, such as static crashes, urban HF noise, and in-band interference.

1. FEC was added fairly recently to FreeDV modes.  The voice codecs we use work OK at bit error rates of a few %, and packet error rates of 10%. Raw bit error rates on multipath channels often exceed 10%.  For reasonable latency (say 40ms) we need small codewords. Thus to be useful we require a FEC code that works at over 10% raw BER, has 1% output (coded) bit error rate, and a codeword of around 100 bits.  Digital voice has unusual requirements, most FEC codes are designed for data which is intolerant of any bit errors, and few operate over 10% raw BER.  Powerful FEC codes have long block lengths (1000's of bits) which leads to long latency.  However LDPC codes come close, and can also "clean up" other channel errors caused by static and interference.  The use of OFDM means we now have "room" for the extra bits required for FEC, so there is little cost in adding it, apart from latency.

1. 2020A and 2020B use unequal error protection, only 11 bits from each 52 bit vocoder frame are protected by FEC.  This provides strong protection of the most important bits.  The effect is a gentle "slope" in the speech quality versus SNR curve.  These modes will work at lower SNRs that 2020, but will still have some audible errors even at high SNRs.  2020B has a modem waveform similar to 700E - a high pilot symbol rate so it operates on fast fading channels.  Compared to 2020 it has a shorter frame duration (90ms), lower latency and faster sync, but requires a few more dB SNR.

## FreeDV VHF Modes

These modes use constant amplitude modulation like FSK or FM, and are designed for VHF and above.  However 800XA can be run over HF or VHF on a SSB radio.

| Mode | Date | Codec2 | Modem | RF BW | Raw bits/s | FEC | Text bits/s |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2400A | 2016 | 1300 | 4FSK | 5kHz | 2400 | Golay (23,12) | 50 |
| 2400B | 2016 | 1300 | baseband/analog FM | analog FM | 2400 | Golay (23,12) | 50 |
| 800XA | 2017 | 700C |  4FSK | 2000 | 800 | - | N |
| FSK_LDPC | 2020 | - | 2 or 4 FSK |  user defined | user defined | LDPC | - | - |

The FSK_LDPC mode is used for data, and has user defined bit rate and a variety of LDPC codes available.  It is discussed in [README_data](README_data.md)

## FreeDV 2400A and 2400B modes

FreeDV 2400A and FreeDV 2400B are modes designed for VHF radio. FreeDV 2400A is designed for SDR radios (it has a 5 kHz RF bandwidth), however FreeDV 2400B is designed to pass through commodity FM radios.

Demos of FreeDV 2400A and 2400B:
```
$ ./freedv_tx 2400A ../../raw/ve9qrp_10s.raw - | ./freedv_rx 2400A - - | play -t .s16 -r 8000 -
$ ./freedv_tx 2400B ../../raw/ve9qrp_10s.raw - | ./freedv_rx 2400B - - | play -t .s16 -r 8000 -
```
Note for FreeDV 2400A/2400B the modem signal sample rate is 48kHz.  To
listen to the modem tones from FreeDV 2400B, or play them into a FM HT
mic input:
```
$ ./freedv_tx 2400B ../../raw/ve9qrp_10s.raw - | play -t .s16 -r 48000 -
```
Simulate FreeDV 2400B passing through a 300 to 3000 Hz audio path using sox to filter:
```
$  ./freedv_tx 2400B ../../raw/ve9qrp_10s.raw - | sox -t .s16 -r 48000 - -t .s16 - sinc 300-3000 | ./freedv_rx 2400B - - | play -t .s16 -r 8000 -
```

## FreeDV 2020 support (building with LPCNet)

1. Build codec2 initially without LPCNet
   ```
   $ cd ~
   $ git clone https://github.com/drowe67/codec2.git
   $ cd codec2 && mkdir build_linux && cd build_linux
   $ cmake ../
   $ make
   ```

1. Build LPCNet:
   ```
   $ cd ~
   $ git clone https://github.com/drowe67/LPCNet
   $ cd LPCNet && mkdir build_linux && cd build_linux
   $ cmake -DCODEC2_BUILD_DIR=~/codec2/build_linux ../
   $ make
   ```

1. (Re)build Codec 2 with LPCNet support:
   ```
   $ cd ~/codec2/build_linux && rm -Rf *
   $ cmake -DLPCNET_BUILD_DIR=~/LPCNet/build_linux ..
   $ make
   ```

## FreeDV 2020 tests with FreeDV API

```
$ cat ~/LPCNet/wav/wia.wav | ~/LPCNet/build_linux/src/lpcnet_enc -s | ./ofdm_mod --mode 2020 --ldpc --verbose 1 | ./ofdm_demod --mode 2020 --verbose 1 --ldpc | ~/LPCNet/build_linux/src/lpcnet_dec -s | aplay -f S16_LE -r 16000
```
Listen the reference tx:
```
$ cat ~/LPCNet/wav/wia.wav | ~/LPCNet/build_linux/src/lpcnet_enc -s | ./ofdm_mod --mode 2020 --ldpc --verbose 1 | aplay -f S16_LE
```

Listen the freedv_tx:
```
$ ./freedv_tx 2020 ~/LPCNet/wav/wia.wav - | aplay -f S16_LE
```

FreeDV API tx, with reference rx from above:
```
$ ./freedv_tx 2020 ~/LPCNet/wav/wia.wav - | ./ofdm_demod --mode 2020 --verbose 1 --ldpc | ~/LPCNet/build_linux/src/lpcnet_dec -s | aplay -f S16_LE -r 16000
```

FreeDV API tx and rx:
```
$ ./freedv_tx 2020 ~/LPCNet/wav/all.wav - | ./freedv_rx 2020 - - | aplay -f S16_LE -r 16000
$ ./freedv_tx 2020 ~/LPCNet/wav/all.wav - --testframes | ./freedv_rx 2020 - /dev/null --testframes -vv
```

Simulated HF slow fading channel, 10.8dB SNR:
```
$ ./freedv_tx 2020 ~/LPCNet/wav/all.wav - | ./ch - - --No -30 --slow | ./freedv_rx 2020 - - | aplay -f S16_LE -r 16000
```
It falls down quite a bit with fast fading (--fast):

AWGN (noise but no fading) channel, 2.8dB SNR:
```
$ ./freedv_tx 2020 ~/LPCNet/wav/all.wav - | ./ch - - --No -22 | ./freedv_rx 2020 - - | aplay -f S16_LE -r 16000
```

## Command lines for PER testing 700D/700E PER with clipper

AWGN:
```
$ ./src/freedv_tx 700D ../raw/ve9qrp.raw - --clip 0 --testframes | ./src/ch - - --No -16 | ./src/freedv_rx 700D - /dev/null --testframes
```
MultiPath Poor (MPP):
```
$ ./src/freedv_tx 700D ../raw/ve9qrp.raw - --clip 0 --testframes | ./src/ch - - --No -24 --mpp --fading_dir unittest | ./src/freedv_rx 700D - /dev/null --testframes
```

Adjust `--clip [0|1]` and `No` argument of `ch` to obtain a PER of just less than 0.1, and note the SNR and PAPR reported by `ch`.  The use of the `ve9qrp` samples makes the test run for a few minutes, in order to get reasonable multipath channel results.

Low SNR MPP channel 2020B command line:
```
cat ~/LPCNet/wav/all.wav | ~/LPCNet/build_linux/src/lpcnet_enc -x | ./src/ofdm_mod --mode 2020B --ldpc --clip --txbpf | ./src/ch - - --No -22 --mpd | ./src/ofdm_demod --mode 2020B --verbose 1 --ldpc | ~/LPCNet/build_linux/src/lpcnet_dec -x | aplay -f S16_LE -r 16000
```

## Reading Further

1. [FreeDV web site](http://freedv.org)
1. [FreeDV GUI User Manual](https://github.com/drowe67/freedv-gui/blob/master/USER_MANUAL.md)
1. [Codec 2](http://rowetel.com/codec2.html)
1. FreeDV can also be used for data [README_data](https://github.com/drowe67/codec2/blob/master/README_data.md)
1. [FreeDV 1600 specification](https://freedv.org/freedv-specification)
1. [FreeDV 700C blog post](http://www.rowetel.com/wordpress/?p=5456)
1. [FreeDV 700D Released blog post](http://www.rowetel.com/wordpress/?p=6103)
1. [FreeDV 2020 blog post](http://www.rowetel.com/wordpress/?p=6747)
1. [FreeDV 2400A blog post](http://www.rowetel.com/?p=5119)
1. [FreeDV 2400A & 2400B](http://www.rowetel.com/?p=5219)
1. Technical information on various modem waveforms in the [modem codec frame design spreadsheet](https://github.com/drowe67/codec2/blob/master/doc/modem_codec_frame_design.ods)
1. [Modems for HF Digital Voice Part 1](http://www.rowetel.com/wordpress/?p=5420)
1. [Modems for HF Digital Voice Part 2](http://www.rowetel.com/wordpress/?p=5448)
1. [FDMDV modem README](README_fdmdv.md)
1. [OFDM modem README](README_ofdm.md)
1. Many blog posts in the [rowetel.com blog archives](http://www.rowetel.com/?page_id=6172)

