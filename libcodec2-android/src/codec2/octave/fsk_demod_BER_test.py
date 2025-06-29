#!/usr/bin/env python3
#
#   Perform automated Eb/N0 testing of the C-implementation of fsk_mod / fsk_demod
#
#   Based on the analysis performed here: https://github.com/projecthorus/radiosonde_auto_rx/blob/master/auto_rx/test/notes/2019-03-03_generate_lowsnr_validation.md
#
#   Copyright (C) 2020  Mark Jessop <vk5qi@rfhead.net>
#   Released under GNU GPL v3 or later
#
#   Requirements:
#       - csdr must be installed and available on the path. https://github.com/ha7ilm/csdr
#       - The following utilities from codec2 need to be built:
#           - fsk_get_test_bits, fsk_put_test_bits
#           - fsk_mod, fsk_demod
#       - Create the directories: 'samples' and 'generated' in this directory (octave)
#
import json
import logging
import os
import time
import traceback
import subprocess
import sys
import argparse

import numpy as np
import matplotlib.pyplot as plt
import scipy.signal
import scipy.interpolate


# Variables you will want to adjust:

# Eb/N0 Range to test:
# Default: 0 through 5 dB in 0.5 db steps, then up to 20 db in 1db steps.
EBNO_RANGE = np.append(np.arange(0, 5, 0.5), np.arange(5, 20.5, 1))

# Baud rates to test:
BAUD_RATES = [100, 50, 25]

# Order of the FSK signal (2 or 4)
FSK_ORDER = 4

# Test Length (bits)
TEST_LENGTH = 2e4

# Pseudorandom sequence length to generate test frames.
# NOTE: BER results are quite dependent on the frame length and threshold parameters.
FRAME_LENGTH = 2000

# Frame threshold detection. This has the effect of setting an upper bound on the BER.
FRAME_THRESHOLD = 0.4

# Allow a reduction in 'expected' bits of this value, as we expect the modem to need
# some time to 'spin up' the estimators.
FRAME_IGNORE = FRAME_LENGTH

# IF sample rate
SAMPLE_RATE = 48000

# Frequency estimator limits
ESTIMATOR_LOWER_LIMIT = 100
ESTIMATOR_UPPER_LIMIT = int(SAMPLE_RATE/2 - 1000)

# Frequency of the low tone (Hz)
LOW_TONE = 2000

# Tone spacing (Hz)
TONE_SPACING = 270

# Mask Estimator
MASK_ESTIMATOR = True

# Switch to 'Low Bit-Rate' mode below this baud rate.
#LBR_BREAK_POINT = 600 # No more LBR mode

# Halt simulation for a particular baud rate when the BER drops below this level.
BER_BREAK_POINT = 1e-4

# If enabled, calculate Frequency Estimator error
FEST_ERROR = True

# Frequency estimator error calculation threshold (*Rs)
FEST_THRESHOLD = 0.2

# Enable doppler shift.
# NOTE: This will apply up to +/- 6kHz of doppler shift to the test signal,
# emulating transmission through a LEO linear transponder.
# You will need to set the modem centre frequency and parameters such that
# the modem signal will be contained within a 1-22 kHz modem RX passband.
# For 100 baud Horus Binary testing, I use:
# LOW_TONE = 10000
# TONE_SPACING = 250
# The TEST_LENGTH setting must also be long enough so that the test modem file
# is at least 780 seconds long. For 100 baud 4FSK, a TEST_LENGTH of 2e6 is enough.
DOPPLER_ENABLED = False
DOPPLER_FILE = "doppler.npz"  # generate using sat_doppler.py


STATS_OUTPUT = True

# Where to place the initial test samples.
SAMPLE_DIR = "./samples"

# Where to place the generated low-SNR samples.
GENERATED_DIR = "./generated"

# Location of the codec2 utils
CODEC2_UTILS = "../build/src"


THEORY_EBNO = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
THEORY_BER_4 = [
    0.22934,
    0.18475,
    0.13987,
    0.09772,
    0.06156,
    0.03395,
    0.01579,
    0.00591,
    0.00168,
    3.39e-4,
    4.44e-5,
    # 3.38e-6,
    # 1.30e-7,
    # 2.16e-9,
    # 1.23e-11,
    # 1.85e-14,
    # 5.13e-18,
    # 1.71e-22
]
THEORY_BER_2 = [
    0.30327,
    0.26644,
    0.22637,
    0.18438,
    0.14240,
    0.10287,
    0.06831,
    0.04080,
    0.02132,
    0.00942,
    0.00337,
]

#
# Functions to read files and add noise.
#


def load_sample(filename, loadreal=True):
    # If loading real samples (which is what fsk_mod outputs), apply a hilbert transform to get an analytic signal.
    if loadreal:
        return scipy.signal.hilbert(np.fromfile(filename, dtype="f4"))
    else:
        return np.fromfile(filename, dtype="c8")


def save_sample(data, filename):
    # We have to make sure to convert to complex64..
    data.astype(dtype="c8").tofile(filename)

    # TODO: Allow saving as complex s16 - see view solution here: https://stackoverflow.com/questions/47086134/how-to-convert-a-numpy-complex-array-to-a-two-element-float-array


def apply_doppler(data, dopplerfile, fs=48000):
    """ Apply a doppler curve to an input data stream """

    npzfile = np.load(dopplerfile)

    _time = npzfile["arr_0"]
    _doppler = npzfile["arr_1"]

    if len(data) < _time[-1] * fs:
        print("Input data length too short - use more bits!")

    # Clip data length if its too long for the input doppler data
    if len(data) > _time[-1] * fs:
        data = data[: int(_time[-1] * fs)]

    # Interpolate the doppler data
    _interp = scipy.interpolate.interp1d(_time, _doppler, kind="cubic")

    _timesteps = np.arange(0, len(data)) / fs
    _interp_doppler = _interp(_timesteps)

    # This isn't working properly.
    phase = np.cumsum(_interp_doppler / fs)
    mixed = data * np.exp(1.0j * 2.0 * np.pi * phase)

    return mixed


def calculate_variance(data, threshold=-100.0):
    # Calculate the variance of a set of radiosonde samples.
    # Optionally use a threshold to limit the sample the variance
    # is calculated over to ones that actually have sonde packets in them.

    _data_log = 20 * np.log10(np.abs(data))

    # MSE is better than variance as a power estimate, as it counts DC
    data_thresh = data[_data_log > threshold]
    return np.mean(data_thresh * np.conj(data_thresh))


def add_noise(
    data,
    variance,
    baud_rate,
    ebno,
    fs=96000,
    bitspersymbol=1.0,
    normalise=True,
    real=False,
):
    # Add calibrated noise to a sample.

    # Calculate Eb/No in linear units.
    _ebno = 10.0 ** ((ebno) / 10.0)

    # Calculate the noise variance we need to add
    _noise_variance = variance * fs / (baud_rate * _ebno * bitspersymbol)

    # If we are working with real samples, we need to halve the noise contribution.
    if real:
        _noise_variance = _noise_variance * 0.5

    # Generate complex random samples
    _rand_i = np.sqrt(_noise_variance / 2.0) * np.random.randn(len(data))
    _rand_q = np.sqrt(_noise_variance / 2.0) * np.random.randn(len(data))

    _noisy = data + (_rand_i + 1j * _rand_q)

    if normalise:
        # print("Normalised to 1.0")
        return _noisy / np.max(np.abs(_noisy))
    else:
        return _noisy


def generate_lowsnr(sample, outfile, fs, baud, ebno, order):
    """ Generate a low SNR test file  """

    if order == 2:
        _bits_per_symbol = 1
    else:
        _bits_per_symbol = 2

    _var = calculate_variance(sample)

    _noisy = add_noise(sample, _var, baud, ebno, fs, _bits_per_symbol)

    save_sample(_noisy, outfile)

    return outfile


#
#   Functions to deal with codec2 utils
#


def generate_fsk(baud):
    """ Generate a set of FSK data """

    # Calculate the number of bits we need to generate to get out desired test length.
    if FSK_ORDER == 2:
        _num_bits = TEST_LENGTH * baud
    else:
        _num_bits = TEST_LENGTH * baud * 2

    _num_bits = TEST_LENGTH

    _filename = "%s/fsk_%d_%d_%d_f.bin" % (SAMPLE_DIR, FSK_ORDER, SAMPLE_RATE, baud)

    # Generate the command we need to make:
    _cmd = (
        "%s/fsk_get_test_bits - %d %d | %s/fsk_mod %d %d %d %d %d - - | csdr convert_s16_f > %s"
        % (
            CODEC2_UTILS,
            _num_bits,
            FRAME_LENGTH,
            CODEC2_UTILS,
            FSK_ORDER,
            SAMPLE_RATE,
            baud,
            LOW_TONE,
            TONE_SPACING,
            _filename,
        )
    )

    print(_cmd)

    print("Generating test signal: %d-FSK, %d baud" % (FSK_ORDER, baud))

    # Run the command.
    try:
        _start = time.time()
        _output = subprocess.check_output(_cmd, shell=True, stderr=None)
        _output = _output.decode()
    except:
        # traceback.print_exc()
        _output = "error"

    _runtime = time.time() - _start

    print("Finished generating test signal.")

    return _filename


def process_fsk(
    filename, baud, complex_samples=True, override_bits=None, stats=False, statsfile=""
):
    """ Run a fsk file through fsk_demod """

    _estim_limits = "-b %d -u %d " % (ESTIMATOR_LOWER_LIMIT, ESTIMATOR_UPPER_LIMIT)

    if MASK_ESTIMATOR:
        _mask = "--mask %d " % TONE_SPACING
    else:
        _mask = ""

    if complex_samples:
        _cpx = "--cs16 "
    else:
        _cpx = ""

    if stats:
        _stats_file = GENERATED_DIR + "/" + statsfile + ".stats"
        _stats = "--stats=50 "
    else:
        _stats = ""
        _stats_file = None

    _cmd = "cat %s | csdr convert_f_s16 | %s/fsk_demod %s%s%s%s%d %d %d - - " % (
        filename,
        CODEC2_UTILS,
        _mask,
        _estim_limits,
        _cpx,
        _stats,
        FSK_ORDER,
        SAMPLE_RATE,
        baud,
    )

    if stats:
        _cmd += "2> %s" % _stats_file

    _cmd += "| %s/fsk_put_test_bits -f %d -t %.2f - 2>&1" % (
        CODEC2_UTILS,
        FRAME_LENGTH,
        FRAME_THRESHOLD,
    )

    # print("Processing %s" % filename)

    print(_cmd)

    # Run the command.
    try:
        _start = time.time()
        _output = subprocess.check_output(_cmd, shell=True)
        _output = _output.decode()
    except subprocess.CalledProcessError as e:
        _output = e.output.decode()
    except:
        traceback.print_exc()
        _output = "error"
        print("Run failed!")
        return (-1, _stats_file)

    _runtime = time.time() - _start

    # Try to grab last line of the stderr outout
    try:
        _last_line = _output.split("\n")[-3]
    except:
        # Lack of a line indicates that we have decoded no data. return a BER of 1.
        print("No bits decoded.")
        return (1.0, _stats_file)

    # Detect no decoded bits when feeding in custom put_bits parameters.
    if "Using" in _last_line:
        print("No bits decoded.")
        return (1.0, _stats_file)

    # Example line:
    # [0009] BER 0.000, bits tested  18000, bit errors      0 errs:    0
    # [0009] BER 0.000, bits tested  18000, bit errors    0
    # PASS
    #

    # split into fields
    _fields = _last_line.split()

    # Extract number of bits and errors
    _bits = float(_fields[5][:-1])  # remove the trailing comma
    _errors = float(_fields[8])

    print("Bits: %d, Errors: %d, Raw BER: %.8f" % (_bits, _errors, _errors / _bits))

    if override_bits != None:
        if _bits < override_bits:
            print("Demod got %d bits, but we sent %d bits." % (_bits, override_bits))
            _errors += override_bits - _bits

    # Calculate and return BER
    _ber = _errors / _bits

    if _ber > 1.0:
        _ber = 1.0

    return (_ber, _stats_file)


def read_stats(filename, sps = 50):
    """ Read in a statistics file, and re-organise it for easier calculations """

    _output = {
        'ebno': [],
        'f1_est': [],
        'f2_est': [],
        'f3_est': [],
        'f4_est': [],
        'ppm': [],
        'time': []
    }

    with open(filename, 'r') as _f:
        for _line in _f:
            if _line[0] != '{':
                    continue

            try:
                _data = json.loads(_line)
            except Exception as e:
                #print("Line parsing error: %s" % str(e))
                continue

            _output['ebno'].append(_data['EbNodB'])
            _output['f1_est'].append(_data['f1_est'])
            _output['f2_est'].append(_data['f2_est'])

            if 'f3_est' in _data:
                _output['f3_est'].append(_data['f3_est'])
                _output['f4_est'].append(_data['f4_est'])

            _output['ppm'].append(_data['ppm'])

            if _output['time'] == []:
                _output['time'] = [0]
            else:
                _output['time'].append(_output['time'][-1]+1.0/sps)

    return _output


def freq_est_error(data, Rs):
    """ Calculate the frequency estimator error """

    _threshold = FEST_THRESHOLD*Rs

    _total_points = len(data['f1_est'])*FSK_ORDER

    _errors = 0

    _errors += np.sum(np.abs(np.array(data['f1_est'])-LOW_TONE) > _threshold)
    _errors += np.sum(np.abs(np.array(data['f2_est'])-LOW_TONE-TONE_SPACING) > _threshold)

    if FSK_ORDER == 4:
        _errors += np.sum(np.abs(np.array(data['f3_est'])-LOW_TONE-TONE_SPACING*2) > _threshold)
        _errors += np.sum(np.abs(np.array(data['f4_est'])-LOW_TONE-TONE_SPACING*3) > _threshold)


    return _errors/_total_points


if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="FSK modem BER simulations")
    parser.add_argument("--test", action="store_true", help="run automated test")
    args = parser.parse_args()

    if args.test:
        # test the AWGN channel simulation.  We use BPSK that's phase shifted to exercise the
        # complex maths a bit

        nb_bits = 100000
        EbNo = 4
        tx_bits = np.random.randint(2, size=nb_bits)
        tx_bpsk_symbols = (2 * tx_bits - 1) * np.exp(1j * np.pi / 3)
        tx_power = calculate_variance(tx_bpsk_symbols)

        # check calculate_variance()
        assert tx_power < 1.1 and tx_power > 0.9

        # BPSK modem simulation
        rx_bpsk_symbols = add_noise(tx_bpsk_symbols, tx_power, 1, EbNo, 1, 1)
        rx_bpsk_symbols = rx_bpsk_symbols * np.exp(-1j * np.pi / 3)
        rx_bits = np.array([1 if np.real(s) > 0 else 0 for s in rx_bpsk_symbols])
        nb_errors = np.sum(np.bitwise_xor(tx_bits, rx_bits))
        ber = nb_errors / nb_bits

        # set limit of +/- 0.25dB on BER
        EbNo_lin_upper = 10 ** ((EbNo - 0.25) / 10)
        bpsk_ber_theory_upper = 0.5 * scipy.special.erfc(np.sqrt(EbNo_lin_upper))
        EbNo_lin_lower = 10 ** ((EbNo + 0.25) / 10)
        bpsk_ber_theory_lower = 0.5 * scipy.special.erfc(np.sqrt(EbNo_lin_lower))
        print(
            "nb_errors: %d ber: %4.3f ber_lower_limit: %4.3f ber_upper_limit: %4.3f"
            % (nb_errors, ber, bpsk_ber_theory_lower, bpsk_ber_theory_upper)
        )
        assert ber < bpsk_ber_theory_upper and ber > bpsk_ber_theory_lower
        print("AWGN channel simulation test PASSED!")
        exit()

    plot_data = {}

    for _baud in BAUD_RATES:

        _file = generate_fsk(_baud)

        print("Loading file and converting to complex.")
        _sample = load_sample(_file)

        if DOPPLER_ENABLED:
            print("Applying Doppler.")
            _sample = apply_doppler(_sample, DOPPLER_FILE)
            print("Done.")
            _override_bits = _baud * (len(_sample) / SAMPLE_RATE) - FRAME_IGNORE
        else:
            _override_bits = TEST_LENGTH - FRAME_IGNORE

        _temp_file = "%s/temp.bin" % GENERATED_DIR

        _ebnos = []
        _bers = []
        _fest_err = []

        for _ebno in EBNO_RANGE:
            generate_lowsnr(_sample, _temp_file, SAMPLE_RATE, _baud, _ebno, FSK_ORDER)

            _ber, _stats_file = process_fsk(
                _temp_file,
                _baud,
                override_bits=_override_bits,
                stats=STATS_OUTPUT,
                statsfile="fsk_%d_%.1f" % (_baud, _ebno),
            )

            print("%.1f, %.8f" % (_ebno, _ber))

            _ebnos.append(_ebno)
            _bers.append(_ber)

            if FEST_ERROR:
                _stats = read_stats(_stats_file)
                _fest_err.append(freq_est_error(_stats, _baud))

            # Halt the simulation if the BER drops below our break point.
            if _ber < BER_BREAK_POINT:
                break

        plot_data[_baud] = {"baud": _baud, "ebno": _ebnos, "ber": _bers, "fest_err":_fest_err}
        print(plot_data[_baud])

        # plt.semilogy(plot_data[_baud]['ebno'], plot_data[_baud]['ber'], label="Simulated - %d bd" % _baud)

    plt.figure()

    print(plot_data)

    for _b in plot_data:
        plt.semilogy(
            plot_data[_b]["ebno"], plot_data[_b]["ber"], label="Simulated - %d bd" % _b
        )

    if FSK_ORDER == 2:
        plt.semilogy(THEORY_EBNO, THEORY_BER_2, label="Theory")
    else:
        plt.semilogy(THEORY_EBNO, THEORY_BER_4, label="Theory")

    plt.xlabel("Eb/N0 (dB)")
    plt.ylabel("BER")

    # Crop plot to reasonable limits
    plt.ylim(1e-3, 1)
    plt.xlim(0, 10)

    plt.title("fsk_demod %d-FSK BER performance" % FSK_ORDER)
    plt.grid()
    plt.legend()

    if FEST_ERROR:
        plt.figure()

        for _b in plot_data:
            plt.plot(
                plot_data[_b]["ebno"], plot_data[_b]["fest_err"], label="Simulated - %d bd" % _b
            )
        
        plt.title("Frequency Estimator Error")
        plt.grid()
        plt.legend()

    plt.show()
