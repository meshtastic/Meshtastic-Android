# Building for the stm32

## Quickstart

1. Build codec2 (with -DUNITTEST=1) for your host system, see [codec2/README.md](../README.md)
   ```
   $ cd ~/codec2
   $ mkdir build_linux && cd build_linux && cmake -DUNITTEST=1 .. && make
   ```

2. Install a gcc arm toolchain:
   ```
   $ cd ~
   $ wget https://armkeil.blob.core.windows.net/developer/Files/downloads/gnu-rm/8-2018q4/gcc-arm-none-eabi-8-2018-q4-major-linux.tar.bz2
   $ tar xvjf gcc-arm-none-eabi-8-2018-q4-major-linux.tar.bz2
   $ export PATH=$HOME/gcc-arm-none-eabi-8-2018-q4-major/bin:$PATH
   ```

   NOTE: We do not recommend toolchains provided by popular
   distributions (e.g. the Ubuntu 18 gcc-arm-none-eabi package will not
   work).
   
3. Create a build directory (```/path/to/codec2/stm32``` recommended to support unit tests)
   ```
   $ cd /path/to/codec2/stm32
   $ mkdir build_stm32
   $ cd build_stm32
   ```
  
4. The STM32 Standard Peripheral Library is required. The download
   requires a registration on the STM website.  Save the zip file
   somewhere safe and then extract it anywhere you like. You will have
   to tell cmake where the unzipped library is by giving the variable
   PERIPHLIBDIR the location of top level directory, e.g. for version
   1.8.0 this is STM32F4xx_DSP_StdPeriph_Lib_V1.8.0.

   In this example we will assume the library has been unzipped in ~/Downloads.

5. Configure the build system by running cmake:

   ```
   $ cmake -DCMAKE_TOOLCHAIN_FILE=../cmake/STM32_Toolchain.cmake \
     -DPERIPHLIBDIR=~/Downloads/STM32F4xx_DSP_StdPeriph_Lib_V1.8.0 ..
   ```
   Or a more general case:
   ```
   $ cmake /path/to/codec2-dev/stm32 -DCMAKE_TOOLCHAIN_FILE=/path/to/codec2-dev/stm32/cmake/STM32_Toolchain.cmake \
     -DPERIPHLIBDIR=/path/to/unzipped/STM32F4xx_DSP_StdPeriph_Lib_Vx.x.x ..
   ```
   
6. Build binaries (including sm1000.bin)

   Finally:
   ```
   $ make
   ```
   To see all the details during compilation:
   ```
   $ make VERBOSE=1
   ```
   
## Flashing your SM1000

1. Power up your SM1000 with the PTT button down.  Then flash it with:
   
2. ```
   sudo dfu-util -d 0483:df11 -c 1 -i 0 -a 0 -s 0x08000000 -D sm1000.bin
   ```
3. Power cycle to reboot.
   
## Loading and Debugging stm32 programs

1. See unitest/README.md for information on how to set up openocd.

2. In one console Start openocd:
   ```
   $ openocd -f board/stm32f4discovery.cfg

   ```

3. In another start gdb:
   ```
   $ cd ~/codec2/stm32/build_stm32
   $ arm-none-eabi-gdb usart_ut.elf
   (gdb) target remote :3333
   <snip>
   (gdb) load
   <snip>
   (gdb) c
   
   ```

## Directories

Directory | Notes 
---|---
cmake | cmake support files for the stm32
doc | SM1000 documentation
inc | top level sm1000 source, drivers, and some legacy test code
src | top level sm1000 source, drivers, and some legacy test code
unittest | comprehensive set of automated unit tests for the stm32 700D port
