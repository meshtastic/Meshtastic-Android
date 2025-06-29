# README_ofdm

An Orthogonal Frequency Division Multiplexed (OFDM) modem designed for digital voice over HF SSB.  Typical configuration for FreeDV 700D is 700 bit/s voice, a rate 0.5 LDPC code, and 1400 bit/s raw data rate over the channel.

The OFDM modem was first implemented in GNU Octave, then ported to C. Algorithm development is generally easier in Octave, but for real time work we need the C version.  Automated units tests ensure the operation of the Octave and C versions are identical.

## Credits

Steve, David, Don, Richard

## References

1. Spreadsheet describing the [waveform design](doc/modem_codec_frame_design.ods)  The OFDM tab descrives the baseline 700D OFDM waveform.

1. This modem can be used for sending [raw data frames](README_data.md) over HF channels.

1. [Towards FreeDV 700D](https://www.rowetel.com/?p=5573)

1. [FreeDV 700D - First Over The Air Tests](https://www.rowetel.com/?p=5630)

1. [Steve Ports an OFDM modem from Octave to C](https://www.rowetel.com/?p=5824)

1. [Modems for HF Digital Voice Part 1](http://www.rowetel.com/wordpress/?p=5420)

1. [Modems for HF Digital Voice Part 2](http://www.rowetel.com/wordpress/?p=5448)

# Examples

Built as part of codec2-dev, see [README](README.md) for build instructions.

1. Generate 10 seconds of test frame bits, modulate, and play audio
   out of sound device (SoX v14.4.2):
   ```
   $  build_linux/src$ ./ofdm_mod --in /dev/zero --testframes 10 | play --type s16 --rate 8000 --channels 2 -
   ```

1. Generate 10 seconds of uncoded test frame bits, modulate, demodulate, count errors:
   ```
   $  build_linux/src$ ./ofdm_mod --in /dev/zero --testframes 10 | ./ofdm_demod --out /dev/null --testframes --verbose 1 --log demod_dump.txt
   ```   
   Use Octave to look at plots of C modem operation:
   ```
     $ cd ../../octave
     $ octave --no-gui
     octave:1> ofdm_demod_c("../build_linux/src/demod_dump.txt")
   ```

1. Run Octave versions of mod and demod (called tx and rx to avoid namespace clashes in Octave):
   ```
   $ cd ~/octave
   $ octave --no-gui
   octave:1> ofdm_tx("ofdm_test.raw","700D",10)
   octave:1> ofdm_rx("ofdm_test.raw")
   ```
   The Octave modulator ofdm_tx can simulate channel impairments, for
   example AWGN noise at 4dB SNR:
   ```
     octave:1> ofdm_tx("ofdm_test.raw", "700D", 10, 4)
   ```
   The Octave versions use the same test frames as C so can interoperate.
   ```
     build_linux/src$ ./ofdm_demod --in ../../octave/ofdm_test.raw --out /dev/null --testframes --verbose 1
   ```

1. Run mod/demod with LDPC FEC; 60 seconds, 3dB SNR:
   ```
     octave:6> ofdm_ldpc_tx('ofdm_test.raw',"700D",60,3)
     octave:7> ofdm_ldpc_rx('ofdm_test.raw',"700D")
   ```
   C demodulator/LDPC decoder:
   ```   
   build_linux/src$ ./ofdm_demod --in ../../octave/ofdm_test.raw --out /dev/null --verbose 1 --testframes --ldpc
   ```

1. Pass Codec 2 700C compressed speech through OFDM modem:
   ```
   build_linux/src$ ./c2enc 700C ../../raw/ve9qrp_10s.raw - --bitperchar | ./ofdm_mod --ldpc | ./ofdm_demod --ldpc | ./c2dec 700C - - --bitperchar | play --type s16 --rate 8000 --channels 1 -
   ```

1. Listen to signal through simulated fading channel in C:
   ```
   build_linux/src$ ./c2enc 700C ../../raw/ve9qrp_10s.raw - --bitperchar | ./ofdm_mod --ldpc | ./ch - - --No -20 --mpg -f -5 | aplay -f S16
   ```

1. Run test frames through simulated channel in C:
   ```
   build_linux/src$ ./ofdm_mod --in /dev/zero --ldpc --testframes 20 | ./ch - - --No -24 -f -10 --mpp | ./ofdm_demod --out /dev/null --testframes --verbose 1 --ldpc
   ```

1. Run codec voice through simulated fast fading channel, just where it starts to fall over:
   ```
   build_linux/src$ ./c2enc 700C ../../raw/ve9qrp.raw - --bitperchar | ./ofdm_mod --ldpc | ./ch - - --No -24 -f -10 --mpp | ./ofdm_demod --ldpc --verbose 1 | ./c2dec 700C - - --bitperchar | aplay -f S16
   ```

1. FreeDV 1600 on the same channel conditions, roughly same quality at 8dB higher SNR:
   ```
   build_linux/src$ ./freedv_tx 1600 ../../raw/ve9qrp_10s.raw - | ./ch - - --No -30 -f -10 --mpp | ./freedv_rx 1600 - - | aplay -f S16
   ```

1. Using FreeDV API test programs:
   ```
   build_linux/src$ ./freedv_tx 700D ../../raw/hts1a.raw - --testframes | ./freedv_rx 700D - /dev/null --testframes
   build_linux/src$ ./freedv_tx 700D ../../raw/hts1a.raw - | ./freedv_rx 700D - - | aplay -f S16
   build_linux/src$ ./freedv_tx 700D ../../raw/ve9qrp.raw - | ./ch - - --No -26 -f -10 --mpp | ./freedv_rx 700D - - | aplay -f S16
   ```

## FreeDV 2020 extensions

1.  20.5ms symbol period, 31 carrier waveform, (504,396) code, but only 312 data bits used, so we don't send unused data bits.  This means we need less carriers (so more power per carrier), and code rate is increased slightly:
    ```
    build_linux/src$ ./ofdm_mod --in /dev/zero --testframes 300 --mode 2020 --ldpc 1 --verbose 1 | ./ch - - --No -22 -f 10 --ssbfilt 1 | ./ofdm_demod --out /dev/null --testframes --mode 2020 --verbose 1 --ldpc

    SNR3k(dB):  2.21 C/No: 37.0 PAPR:  9.6
    BER......: 0.0505 Tbits: 874020 Terrs: 44148
    Coded BER: 0.0096 Tbits: 649272 Terrs:  6230
    ```

## Acquisition tests

1. Acquisition (getting sync) can be problematic in fading channels. Some special tests have been developed, that measure acquisition time on off air 700D samples at different time offsets:
   ```
   octave:61> ofdm_ldpc_rx("../wav/vk2tpm_004.wav", "700D", "", 5, 4)
   build_linux/src$ ./ofdm_demod --in ../../wav/vk2tpm_004.wav --out /dev/null --verbose 2 --ldpc --start_secs 5 --len_secs 4
   ```

1. Different time offsets effectively tests the ability to sync on fading channel in different states.  Stats for a series of these tests can be obtained with:
   ```
   octave:61> ofdm_time_sync("../wav/vk2tpm_004.wav", 30)
   <snip>
   pass: 30 fails: 0 mean: 1.35 var 0.51
   ```

## Octave Acceptance Tests

Here are some useful tests for the Octave, uncoded modem.

The rate 1/2 LDPC code can correct up to about 10% raw BER, so a good test is to run the modem at Eb/No operating points that produce just less that BER=0.1. The BER2 measure truncates the effect of any start up transients, e.g. as the frequency offset is tracked out.

1. HF Multipath:
   ```
   octave:580> ofdm_tx("ofdm_test.raw","700D",60,2,'mpm',20)
   octave:581> ofdm_rx("ofdm_test.raw")
   BER2.: 0.0803 Tbits: 84728 Terrs:  6803
   ```

1. AWGN:
   ```
   octave:582> ofdm_tx("ofdm_test.raw","700D",60,-2,'awgn')
   octave:583> ofdm_rx("ofdm_test.raw")
   BER2.: 0.0885 Tbits: 84252 Terrs:  7459
   ```

## C Acceptance Tests

Here are some useful tests for the LDPC coded C version of the modem, useful to verify any changes.

1. AWGN channel, -2dB:
   ```
   ./ofdm_mod --in /dev/zero --ldpc --testframes 60 --txbpf | ./ch - - --No -20 -f -10 | ./ofdm_demod --out /dev/null --testframes --verbose 1 --ldpc

   SNR3k(dB): -1.85 C/No: 32.9 PAPR:  9.8
   BER......: 0.0815 Tbits: 98532 Terrs:  8031
   Coded BER: 0.0034 Tbits: 46368 Terrs:   157
   ```

1. Fading HF channel:
   ```
   ./ofdm_mod --in /dev/zero --ldpc --testframes 60 --txbpf | ./ch - - --No -24 -f -10 --fast | ./ofdm_demod --out /dev/null --testframes --verbose 1 --ldpc

   SNR3k(dB):  2.15 C/No: 36.9 PAPR:  9.8
   BER......: 0.1015 Tbits: 88774 Terrs:  9012
   Coded BER: 0.0445 Tbits: 41776 Terrs:  1860
   ```

   Note: 10% Raw BER operating point on both channels, as per design.

# Data Modes

The OFDM modem can also support datac1/datac2/datac3 modes for packet data.  The OFDM modem was originally designed for very short (28 bit) voice codec packets.  For data, packets of hundreds to thousands of bits a desirable so we can use long, powerful FEC codewords, and reduce overhead.  The datac1/datac2/datac3 QPSK modes are currently under development.

Here is an example of running the datac3 mode in a low SNR AWGN channel:

```
./src/ofdm_mod --mode datac3 --ldpc --in  /dev/zero --testframes 60 --verbose 1 | ./src/ch - - --No -20 | ./src/ofdm_demod --mode datac3 --ldpc --out /dev/null --testframes -v 1
<snip>
SNR3k(dB): -3.54 C/No: 31.2 PAPR: 10.4
BER......: 0.1082 Tbits: 36096 Terrs:  3905 Tpackets:    47
Coded BER: 0.0000 Tbits: 12032 Terrs:     0
```
Note despite the raw BER of 10%, 47/50 packets are received error free.

# C Code

| File | Description |
| :-- | :-- |
| ofdm.c | OFDM library |
| codec2_ofdm.h | API header file for OFDM library |
| ofdm_get_test_bits | Generate OFDM test frames |
| ofdm_mod | OFDM modulator command line program |
| ofdm_demod | OFDM demodulator command line program, supports uncoded (raw) and LDPC coded test frames, LDPC decoding of codec data, and can output LLRs to external LDPC decoder |
| ofdm_put_test_bits | Measure BER in OFDM test frames |
| unittest/tofdm | Run C port of modem to compare with octave version (see octave/tofdm) |
| ch | C channel simulator |

# Octave Scripts

| File | Description |
| :-- | :-- |
| ofdm_lib | OFDM library |
| ofdm_dev | Used for modem development, run various simulations |
| ofdm_tx | Modulate test frames to a file of sample, cam add channel impairments |
| ofdm_rx | Demod from a sample file and count errors |
| tofdm | Compares Octave and C ports of modem |
| ofdm_ldpc_tx | OFDM modulator with LDPC FEC |
| ofdm_ldpc_rx | OFDM demodulator with LDPC FEC |

## Specifications

Nominal FreeDV 700D configuration:

| Parameter | Value |
| :-- | :-- |
| Modem | OFDM, pilot assisted coherent QPSK |
| Payload bits/s | 700 |
| Text bits/s | 25 (note 4) |
| Unique Word | 10 bits |
| Carriers | 17 |
| RF bandwidth | 944 Hz |
| Symbol period | 18ms
| Cyclic Prefix | 2ms (note 1)
| Pilot rate | 1 in every 8 symbols |
| Frame Period | 160ms |
| FEC | rate 1/2 (224,112) LDPC |
| Operating point | |
|   AWGN | Eb/No -0.5dB SNR(3000Hz): -2.5dB (note 2) |
|   HF Multipath | Eb/No  4.0dB SNR(3000Hz):  2.0dB (note 3) |
|   Freq offset | +/- 60  Hz   (sync range) |
| Freq drift | +/- 0.2 Hz/s (for 0.5 dB loss) |
| Sample clock error | 1000 ppm |

Notes:

1. Modem can cope with up to 2ms of multipath
1.
   ```
   Ideal SNR(3000) = Eb/No + 10*log10(Rb/B)
                     = -1 + 10*log10(1400/3000)
                     = -4.3 dB
   ```
   So we have about 1.8dB overhead for synchronisation, implementation loss,  and the text channel.
1. "CCIR Poor" HF Multipath channel used for testing is two path, 1Hz Doppler, 1ms delay.
1. The text channel is an auxillary channel, unprotected by FEC, that typically carries callsign/location information for Ham stations.
