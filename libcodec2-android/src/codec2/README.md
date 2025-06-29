# Codec 2 README

Codec 2 is an open source (LGPL 2.1) low bit rate speech codec: http://rowetel.com/codec2.html

Also included:

  + The FreeDV API for digital voice over radio. FreeDV is an open source digital voice protocol that integrates modems, codecs, and FEC [README_freedv](README_freedv.md)
  + APIs for raw and Ethernet packet data over radio [README_data](README_data.md)
  + High performance coherent OFDM modem for HF channels [README_ofdm](README_ofdm.md)
  + High performance non-coherent FSK modem [README_fsk](README_fsk.md)
  + An STM32 embedded version of FreeDV 1600/700D/700E for the [SM1000](stm32/README.md)
  + Coherent PSK modem [README_cohpsk](README_cohpsk.md) for HF channels
  + FDMDV DPSK modem [README_fdmdv](README_fdmdv.md) for HF channels

## Quickstart

1. Install packages (Debian/Ubuntu):
   ```
   sudo apt install git build-essential cmake
   ```
   Fedora/RH distros:
   ```
   sudo dnf groupinstall "Development Tools" "C Development Tools and Libraries"
   sudo dnf install cmake
   ```
   
1. Build Codec 2:
   ```
   git clone https://github.com/drowe67/codec2.git
   cd codec2
   mkdir build_linux
   cd build_linux
   cmake ..
   make
   ```

1. Listen to Codec 2:
   ```
   cd codec2/build_linux
   ./demo/c2demo ../raw/hts1a.raw hts1a_c2.raw
   aplay -f S16_LE ../raw/hts1a.raw
   aplay -f S16_LE hts1a_c2.raw
   ```
1. Compress, decompress and then play a file using Codec 2 at 2400 bit/s:
   ```
   ./src/c2enc 2400 ../raw/hts1a.raw hts1a_c2.bit
   ./src/c2dec 2400 hts1a_c2.bit hts1a_c2_2400.raw 
   ```
   which can be played with:
   ```
   aplay -f S16_LE hts1a_c2_2400.raw
   ```
   Or using Codec 2 using 700C (700 bits/s):
   ```
   ./src/c2enc 700C ../raw/hts1a.raw hts1a_c2.bit
   ./src/c2dec 700C hts1a_c2.bit hts1a_c2_700.raw
   aplay -f S16_LE hts1a_c2_700.raw
   ```
1. If you prefer a one-liner without saving to files:
   ```
   ./src/c2enc 1300 ../raw/hts1a.raw - | ./src/c2dec 1300 - - | aplay -f S16_LE
   ```

1. Or you can use your microphone and headphones to encode and listen to the result on the fly:
   ```
   br=1300; arecord -f S16_LE -c 1 -r 8000 | ./src/c2enc $br - - | ./src/c2dec $br - - | aplay -f S16_LE -
   ```
   
## FreeDV 2020 support (building with LPCNet)

1. Build codec2 initially without LPCNet
   ```
   cd ~
   git clone https://github.com/drowe67/codec2.git
   cd codec2 && mkdir build_linux && cd build_linux
   cmake ../
   make
   ```

1. Build LPCNet:
   ```
   cd ~
   git clone https://github.com/drowe67/LPCNet
   cd LPCNet && mkdir build_linux && cd build_linux
   cmake -DCODEC2_BUILD_DIR=~/codec2/build_linux ../ 
   make
   ```

1. (Re)build Codec 2 with LPCNet support:
   ```
   cd ~/codec2/build_linux && rm -Rf *
   cmake -DLPCNET_BUILD_DIR=~/LPCNet/build_linux ..
   make
   ```

## Programs

+ See `demo` directory for simple examples of using Codec and the FreeDV API.

+ `c2demo` encodes a file of speech samples, then decodes them and saves the result.

+ `c2enc` encodes a file of speech samples to a compressed file of encoded bits.  `c2dec` decodes a compressed file of bits to a file of speech samples.

+ `c2sim` is a simulation/development version of Codec 2.  It allows selective use of the various Codec 2 algorithms.  For example switching phase modelling or quantisation on and off.

+ `freedv_tx` & `freedv_rx` are command line implementations of the FreeDV protocol, which combines Codec 2, modems, and Forward Error Correction (FEC).
  
+ `cohpsk_*` are coherent PSK (COHPSK) HF modem command line programs.

+ `fdmdv_*` are differential PSK HF modem command line programs (README_fdmdv).

+ `fsk_*` are command line programs for a non-coherent FSK modem (README_fsk).

+ `ldpc_*` are LDPC encoder/decoder command line programs, based on the CML library.

+ `ofdm_*` are OFDM PSK HF modem command line programs (README_ofdm).

## Building and Running Unit Tests

CTest is used as a test framework, with support from [GNU Octave](https://www.gnu.org/software/octave/) scripts.

1. Install GNU Octave and libraries on Ubuntu with:
   ```
   sudo apt install octave octave-common octave-signal liboctave-dev gnuplot python3-numpy sox valgrind
   ```
1. To build and run the tests:
   ```
   cd ~/codec2
   rm -Rf build_linux && mkdir build_linux
   cd build_linux
   cmake -DUNITTEST=1 ..
   make
   ```

1. To just run tests without rebuilding:
   ```
   ctest
   ```

1. To get a verbose run (e.g. for test debugging):
   ```
   ctest -V
   ```

1. To just run a single test:
   ```
   ctest -R test_OFDM_modem_octave_port
   ```

1. To list the available tests:
   ```
   ctest -N
   ```

1. Many Octave scripts rely on the CML LDPC library.  To run these from the Octave CLI, you need to set
   the `CML_PATH` environment variable.  A convenient way to do this is using a `.octaverc` file
   in your `codec/octave` directory.  For example on a Linux machine, create a `.octaverc` file:
   ```
   setenv("CML_PATH","../build_linux/cml")
   ```  
   
## Directories
```
cmake       - cmake support files
demo        - Simple Codec 2 and FreeDv API demo applications
misc        - misc C programs that have been useful in development,
              not reqd for Codec 2 release. Part of Debug build.
octave      - Octave scripts used to support development
script      - shell scripts for playing and converting raw files
src         - C source code for Codec 2, FDMDV modem, COHPSK modem, FreeDV API
raw         - speech files in raw format (16 bits signed linear 8 kHz)
stm32       - STM32F4 microcontroller and SM1000 FreeDV Adaptor support
unittest    - Code to perform and support testing. Part of Debug build.
wav         - speech files in wave file format
```
## GDB and Dump Files

1. To compile with debug symbols for using gdb:
   ```
   cd ~/codec2
   rm -Rf build_linux && mkdir build_linux
   cd build_linux
   CFLAGS=-g cmake ..
   make
   ```

1. For dump file support (dump data from c2sim for input to Octave development scripts):
   ```
   cd ~/codec2
   rm -Rf build_linux && mkdir build_linux
   cd build_linux
   CFLAGS=-DDUMP cmake ..
   make
   ```

## Building for Windows on a Linux machine

On Ubuntu 17 and above:
   ```
   sudo apt-get install mingw-w64
   mkdir build_windows && cd build_windows
   cmake .. -DCMAKE_TOOLCHAIN_FILE=/home/david/freedv-dev/cmake/Toolchain-Ubuntu-mingw32.cmake -DUNITTEST=FALSE -DGENERATE_CODEBOOK=/home/david/codec2/build_linux/src/generate_codebook 
   make
   ```
   
## Building for Windows on a Windows machine

 ```
 mkdir build_windows (Or what ever you want to call your build dir)
 cmake -G "MinGW Makefiles" -D CMAKE_MAKE_PROGRAM=mingw32-make.exe
 Or if you use ninja for building cmake -G "Ninja" ..
 mingw32-make or ninja  depends on what you used in the last command
 wait for it to build.
 ```


## Including Codec 2 in an Android project

In an Android Studio 'NDK' project (a project that uses 'native' code)
Codec 2 can be added to the project in the following way.

1. Add the Codec 2 source tree to your app (e.g. in app/src/main/codec2)
   (e.g. as a git sub-module).

1. Add Codec 2 to the CMakeList.txt (app/src/main/cpp/CMakeLists.txt):

    ```
    # Sets lib_src_DIR to the path of the target CMake project.
    set( codec2_src_DIR ../codec2/ )
    # Sets lib_build_DIR to the path of the desired output directory.
    set( codec2_build_DIR ../codec2/ )
    file(MAKE_DIRECTORY ${codec2_build_DIR})

    add_subdirectory( ${codec2_src_DIR} ${codec2_build_DIR} )

    include_directories(
	    ${codec2_src_DIR}/src
	    ${CMAKE_CURRENT_BINARY_DIR}/../codec2
    )
    ```
     
1. Add Codec 2 to the target_link_libraries in the same file.

## Building Codec 2 for Microcontrollers

Codec 2 requires a hardware Floating Point Unit (FPU) to run in real time.

Two build options have been added to support building on microcontrollers:
1. Setting the `cmake` variable MICROCONTROLLER_BUILD disables position independent code (-fPIC is not used).  This was required for the IMRT1052 used in Teensy 4/4.1).

1. On ARM machines, setting the C Flag \_\_EMBEDDED\_\_ and linking with the ARM CMSIS library will improve performance on ARM-based microcontrollers. \_\_REAL\_\_ and FDV\_ARM\_MATH are additional ARM-specific options that can be set to improve performance if required, especially with OFDM modes.

A CMakeLists.txt example for a microcontroller is below:

```
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
set(MICROCONTROLLER_BUILD 1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mlittle-endian -ffunction-sections -fdata-sections -g -O3")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -ffunction-sections -fdata-sections")

add_definitions(-DCORTEX_M7 -D__EMBEDDED__)
add_definitions(-DFREEDV_MODE_EN_DEFAULT=0 -DFREEDV_MODE_1600_EN=1 -DFREEDV_MODE_700D_EN=1 -DFREEDV_MODE_700E_EN=1 -DCODEC2_MODE_EN_DEFAULT=0 -DCODEC2_MODE_1300_EN=1 -DCODEC2_MODE_700C_EN=1)
                    
FetchContent_Declare(codec2
    GIT_REPOSITORY https://github.com/drowe67/codec2.git
    GIT_TAG origin/master
    GIT_SHALLOW ON
    GIT_PROGRESS ON
)
FetchContent_GetProperties(codec2)
if(NOT ${codec2_POPULATED})
    FetchContent_Populate(codec2)
endif()
set(CMAKE_REQUIRED_FLAGS "")

set(LPCNET OFF CACHE BOOL "")
add_subdirectory(${codec2_SOURCE_DIR} ${codec2_BINARY_DIR} EXCLUDE_FROM_ALL)
```

## Building Debian packages

To build Debian packages, simply run the "cpack" command after running "make". This will generate the following packages:

+ codec2: Contains the .so and .a files for linking/executing applications dependent on Codec2.
* codec2-dev: Contains the header files for development using Codec2.

Once generated, they can be installed with "dpkg -i" (once LPCNet is installed). If LPCNet is not desired, CMakeLists.txt can be modified to remove that dependency.
