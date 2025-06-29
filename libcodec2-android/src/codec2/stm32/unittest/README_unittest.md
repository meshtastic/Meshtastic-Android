# README Codec2 STM32 Unit Test
Don Reid 2018/2019

This is the unittest system for the stm32 implementation of
codec2/FreeDV which runs on Linux systems. It requires a STM32F4xx
processor development board connected to/having a ST-LINK, e.g. a
STM32F4 Discovery board.

## Quickstart

Requirements:
* python3/numpy
* arm-none-eabi-gdb install and in your path (see codec2/stm32/README.md)
* STM32F4xx_DSP_StdPeriph_Lib_V1.8.0 (see codec2/stm32/README.md)
* build openocd from source and have it in your path (see below)

Build codec2 for x86 Linux with unittests.  This generates several artifacts required for the stm32 tests:

```
$ cd ~/codec2
$ mkdir build_linux && cd build_linux && cmake -DUNITTEST=1 .. && make
```

Now build for the stm32, and run the stm32 ctests:
```
$ cd ~/codec2/stm32 && mkdir build_stm32 && cd build_stm32
$ cmake -DCMAKE_TOOLCHAIN_FILE=../cmake/STM32_Toolchain.cmake -DPERIPHLIBDIR=~/Downloads/STM32F4xx_DSP_StdPeriph_Lib_V1.8.0 ..
$ make
$ sudo apt install python3-numpy libncurses5
$ ctest -V
```

You should see tests executing (and passing). They are slow to execute
(30 to 180 seconds each), due to the speed of the semihosting system.

## If a Test fails

Explore the files in ```codec2/stm32/unittest/test_run/name_of_test```

When each test runs, a directory is created, and several log files generated.

## Running the stm32 Unit Tests

1. Tests can be run using the ctest utility (part of cmake)
   ```
   $ cd ~/codec2/stm32/build_stm32
   $ ctest
   ```  
   You can pass -V to see more output:
   ```
   $ ctest -V
   ``` 
   You can pass -R <pattern> to run test matching <pattern>. Please note,
   that some test have dependencies and will have to run other tests before
   being executed
   ```
   $ ctest -R ofdm
   ```
   To list the available ctests:
   ```
   $ ctest -N
   ```
1. To run a single test.  This test exercises the entire 700D receive side,
   and measures CPU load and memory:
   ```
   $ cd ~/codec2/stm32/unittest
   $ ./scripts/run_stm32_tst tst_ofdm_api_demod 700D_AWGN_codec
   ```
   In general:
   ```
   $ ./scripts/run_stm32_test <name_of_test> <test_option>
   ```

1. To run a test set (example):
   ```
   $ cd ~/codec2/stm32/unittest
   $ ./scripts/run_all_ldpc_tests
   ```
   In general: (codec2, ofdm, ldpc):
   ```
   $ ./scripts/run_all_<set_name>_tests
   ```

## When tests fail

1. If a test fails, explore the files in the ```test_run``` directory for that test.
1. Try building with ALLOC_DEBUG can be helpful with heap issues:
   ```
   $ CFLAGS=-DEBUG_ALLOC cmake -DCMAKE_TOOLCHAIN_FILE=../cmake/STM32_Toolchain.cmake \
     -DPERIPHLIBDIR=~/Downloads/STM32F4xx_DSP_StdPeriph_Lib_V1.8.0 ..
   ```

## Sequence of a Test

Consider the example:
```
build_stm32$ ctest -R tst_ldpc_dec_noise
```

1. The test is kicked off based on `src/CMakeLists.txt`, which calls `scipts/run_stm32_tst`
1. `run_stm32_tst` calls the test setup script, e.g. `tst_ldpc_dec_setup`.  Typically, this will run a host version to generate a reference.
1. `run_stm32_tst` runs the stm32 executable on the Discovery, e.g. `tst_ldpc_dec`, the source is in `src/tst_ldpc_dec.c`
1. The steup and check scripts can handle many sub cases, e.g. `ideal` and `noise`.
1. `run_stm32_tst` calls the test check script, e.g. `tst_ldpc_dec_check` which typically compares the host generated reference to the output from the stm32.
1. As the test runs, various files are generated in `test_run/tst_ldpc_dec_noise`
1. When debugging it's useful to run the ctest with the verbose option:
   ```
   $ ctest -V -R tst_ldpc_dec_noise
   ```
   Set the `-x` at the top of the scripts to trace execution:
   ```
   #!/bin/bash -x
   #
   # tst_ldpc_enc_check

   ```
      
## Directory Structure

   | Path | Description |
   | --- | --- |
   | `scripts`   | Scripts for unittest system
   | `src`       | stm32 C sources
   | `\src\CMakeLists.txt` | ctests are defined here 
   | `lib/python`| Python library files
   | `lib/octave`| Octave library files
   | `test_run`  | Files created by each test as it runs


## Running the tests remotely

If the stm32 hardware is connected on a different pc with linux, the tests can be run remotely.
Test will run slower, roughly 3 times.

1. You have to build OpenOCD on the remote machine with the STM32 board. It must be built from 
   (https://github.com/db4ple/openocd.git). 
1. You don't need OpenOCD installed on your build pc.
1. You have to be able to run ssh with public key authentication using ssh-agent so that
   you can ssh into the remote machine without entering a password. 
1. You have to call ctest with the proper UT_SSH_PARAMS settings, e.g.
```
UT_SSH_PARAMS="-p 22 -q remoteuser@remotemachine" ctest -V
```

## Debug and Semihosting

These tests required a connection from the arm-none-eabi-gdb debugger
to the stm32 hardware. For this we use a recent version of
OpenOCD. Running tests with the stm32 hardware connected to a remote
machine via ssh is possible. This works only with a patched (fixed)
OpenOCD, see below.

## OpenOCD

We recommend OpenOCD instead of stlink. 

Most linux distributions use old packages for openocd, so you should
build it from the git source. If your test runs fail with error
messages regarding SYS_FLEN not support (see openocd_stderr.log in the
test_run directories), your openocd is too old. Make sure to have the
right openocd first in the PATH if you have multiple openocd
installed!

It is strongly recommended to build OpenOCD from sources, see below.

## Building OpenOCD

OpenOCD needs to be built from the source.

If you want to use openocd remotely via SSH, you have to use currently the patched
source from (https://github.com/db4ple/openocd.git) instead of the official repository.

1.
    The executable is placed in /usr/local/bin ! Make sure to have no
    other openocd installed (check output of `which openocd` to be 
    /usr/local/bin)

	```Bash
      sudo apt install libusb-1.0-0-dev libtool pkg-config autoconf automake texinfo
      git clone https://git.code.sf.net/p/openocd/code openocd-code
      cd openocd-code
      ./bootstrap
      ./configure
      sudo make install
      which openocd
	
      sudo cp contrib/60-openocd.rules /etc/udev/rules.d/
      sudo udevadm control --reload-rules
      {un plug/plug-in stm32 Discovery}
	```

2. Plug in a stm32 development board and test:

```
   $ openocd -f board/stm32f4discovery.cfg

   Open On-Chip Debugger 0.10.0+dev-00796-ga4ac5615 (2019-04-12-21:58)
   Licensed under GNU GPL v2
   For bug reports, read
   http://openocd.org/doc/doxygen/bugs.html
   Info : The selected transport took over low-level target control. The results might differ compared to plain JTAG/SWD
   adapter speed: 2000 kHz
   adapter_nsrst_delay: 100
   none separate
   srst_only separate srst_nogate srst_open_drain connect_deassert_srst
   Info : Listening on port 6666 for tcl connections
   Info : Listening on port 4444 for telnet connections
   Info : clock speed 2000 kHz
   Info : STLINK V2J33S0 (API v2) VID:PID 0483:3748
   Info : Target voltage: 2.871855
   Info : stm32f4x.cpu: hardware has 6 breakpoints, 4 watchpoints
   Info : Listening on port 3333 for gdb connections
```

## st-util (deprecated)

Most distributions don't have stutil included. Easiest way is to build it from
the github sources.

The source can be downloaded from:

       (https://github.com/texane/stlink)

After compiling it can be installed anywhere, as long as it is in the PATH.  The program is in
`build/Release/src/gdbserver/st-util`.

The newlib stdio functions (open, fread, fwrite, flush, fclose, etc.) send
some requests that this tool does not recognize and those messages will appear
in the output of st-util.  They can be ignored.

1. Build from github

```Bash
  git clone https://github.com/texane/stlink
  cd stlink
  make
  sudo cp ./etc/udev/rules.d/49-stlinkv2.rules /etc/udev/rules.d/
  sudo udevadm control --reload-rules
```
3. Add the st-util util to your $PATH, if not installed in the default location ( /usr/local/bin )

4. Plug in a stm32 development board and test:

```
  $ st-util

  st-util 1.4.0-47-gae717b9
  2018-12-29T06:52:16 INFO usb.c: -- exit_dfu_mode
  2018-12-29T06:52:16 INFO common.c: Loading device parameters....
  2018-12-29T06:52:16 INFO common.c: Device connected is: F4 device, id 0x10016413
  2018-12-29T06:52:16 INFO common.c: SRAM size: 0x30000 bytes (192 KiB), Flash: 0x100000 bytes (1024 KiB) in pages of 16384 bytes
  2018-12-29T06:52:16 INFO gdb-server.c: Chip ID is 00000413, Core ID is  2ba01477.G
  2018-12-29T06:52:16 INFO gdb-server.c: Listening at *:4242...
```




# vi:set ts=3 et sts=3:
