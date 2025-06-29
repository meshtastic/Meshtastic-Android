#
# stm32f4 support for Codec2
#
# CMake configuration contributed by Richard Shaw (KF5OIM)
# Please report questions, comments, problems, or patches to the freetel
# mailing list: https://lists.sourceforge.net/lists/listinfo/freetel-codec2
#
set(ARM_GCC_BIN "" CACHE STRING "Path to the bin directory of your arm-eabi-none-gcc (optional)")
project(stm32f4 C ASM)

if(CMAKE_CROSSCOMPILING)
    message(STATUS "We are cross compiling...")
else()
    message(STATUS "Performing standard host build...")
endif()

cmake_minimum_required(VERSION 2.8)

include(cmake/gencodebooks.cmake)

#
# Prevent in-source builds
# If an in-source build is attempted, you will still need to clean up a few
# files manually.
#
set(CMAKE_DISABLE_SOURCE_CHANGES ON)
set(CMAKE_DISABLE_IN_SOURCE_BUILD ON)
if("${CMAKE_SOURCE_DIR}" STREQUAL "${CMAKE_BINARY_DIR}")
  message(FATAL_ERROR "In-source builds in ${CMAKE_BINARY_DIR} are not "
   "allowed, please remove ./CMakeCache.txt and ./CMakeFiles/, create a "
   "separate build directory and run cmake from there.")
endif("${CMAKE_SOURCE_DIR}" STREQUAL "${CMAKE_BINARY_DIR}")

#include(cmake/STM32_Toolchain.cmake)

###################################################

set(FLOAT_TYPE "hard" CACHE STRING "Floating point: defaults to hard.")
set(CMAKE_TOOLCHAIN_FILE "../stm32/cmake/STM32_Toolchain.cmake" CACHE STRING "Toolchain defs")

###################################################

#
# Find the git hash if this is a working copy.
#
if(EXISTS ${CMAKE_SOURCE_DIR}/.git)
    find_package(Git QUIET)
    if(Git_FOUND)
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" describe --always HEAD
            WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}"
            RESULT_VARIABLE res
            OUTPUT_VARIABLE FREEDV_HASH
            ERROR_QUIET
            OUTPUT_STRIP_TRAILING_WHITESPACE)
        message(STATUS "freedv-gui current git hash: ${FREEDV_HASH}")
        add_definitions(-DGIT_HASH="${FREEDV_HASH}")
    else()
        message(WARNING "Git not found. Can not determine current commit hash.")
        add_definitions(-DGIT_HASH="Unknown")
    endif()
else()
        add_definitions(-DGIT_HASH="None")
endif()

# Set default C flags.
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -std=gnu11 -mlittle-endian -mthumb -mthumb-interwork --specs=nano.specs -u_printf_float -mcpu=cortex-m4 -ffunction-sections -fdata-sections -O3")

add_definitions(-DSTM32F40_41xxx -DCORTEX_M4 -D__EMBEDDED__)
add_definitions(-DFREEDV_MODE_EN_DEFAULT=0 -DFREEDV_MODE_1600_EN=1 -DFREEDV_MODE_700D_EN=1 -DFREEDV_MODE_700E_EN=1 -DCODEC2_MODE_EN_DEFAULT=0 -DCODEC2_MODE_1300_EN=1 -DCODEC2_MODE_700C_EN=1)

if(FLOAT_TYPE STREQUAL "hard")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsingle-precision-constant -Wdouble-promotion -mfpu=fpv4-sp-d16 -mfloat-abi=hard -D__FPU_PRESENT=1 -D__FPU_USED=1")
    #CFLAGS += -fsingle-precision-constant
else()
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -msoft-float")
endif()

option(VERIFY_OPT "Enable this for dump files to help verify optimization" OFF)
if(VERIFY_OPT)
    add_definitions(-DDUMP)
endif()

# Set default build type
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE "Debug")
endif()
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    link_libraries(g m)
else()
    link_libraries(c m)
endif()

# Setup defaults that can't be set in the toolchain file
set(CMAKE_EXE_LINKER_FLAGS "-u_init -T${CMAKE_SOURCE_DIR}/stm32_flash.ld -Xlinker --gc-sections")
set(CMAKE_EXECUTABLE_SUFFIX_C ".elf")
set(CMAKE_EXECUTABLE_SUFFIX_CXX ".elf")
set(CMAKE_EXECUTABLE_SUFFIX_ASM ".elf")
set(CMAKE_ASM_FLAGS "${CMAKE_C_FLAGS} -x assembler-with-cpp")

# Check build flags

message(STATUS "CMAKE_C_FLAGS: " ${CMAKE_C_FLAGS})
message(STATUS "CMAKE_ASM_FLAGS: " ${CMAKE_ASM_FLAGS})

###################################################

# STM32F4 Standard Peripheral Library

include(cmake/STM32_Lib.cmake)

###################################################

# Macro for elf->bin
macro(elf2bin target)
    add_custom_command(TARGET ${target}
    POST_BUILD COMMAND ${CMAKE_OBJCOPY} -O binary ${target}.elf ${target}.bin && ${CMAKE_OBJCOPY} -O ihex ${target}.elf ${target}.hex
    COMMENT "Creating binary for ${target}")
    set_source_files_properties(${target}.bin PROPERTIES GENERATED TRUE)
    set_property(DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}" APPEND PROPERTY
    ADDITIONAL_MAKE_CLEAN_FILES ${target}.bin ${target}.hex)
endmacro()

# This macro just adds generation of a map file with the same name as the executable and .map suffix
# to the linker command line. This works in older Cmake version (versions >= 3.13 have target_link_options)
# it should be a one to one replacement for add_executable
macro(add_mapped_executable target)
    add_executable(${target} ${ARGN})
    target_link_libraries(${target} "-Wl,-Map=$<TARGET_PROPERTY:NAME>.map")
    set_source_files_properties(${target}.map PROPERTIES GENERATED TRUE)
    set_property(DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}" APPEND PROPERTY
    ADDITIONAL_MAKE_CLEAN_FILES ${target}.map)
endmacro()

include(CTest)
include_directories(../src ../unittest inc ${PROJECT_BINARY_DIR})

add_subdirectory(unittest/src)

# Codec 2

# Output path is such that #include <codec2/version.h> in codec2.h works
set(CODEC2_VERSION_PATH "${PROJECT_BINARY_DIR}/codec2")
configure_file ("${PROJECT_SOURCE_DIR}/../cmake/version.h.in"
                "${CODEC2_VERSION_PATH}/version.h" )


set(CODEC2_SRC ../src)
set(CODEC2_GEN_CODEBOOK_SRC ../build/src)
set(CODEC2_SRCS
${CODEC2_SRC}/lpc.c
${CODEC2_SRC}/nlp.c
${CODEC2_SRC}/postfilter.c
${CODEC2_SRC}/sine.c
${CODEC2_SRC}/codec2.c
${CODEC2_SRC}/codec2_fft.c
${CODEC2_SRC}/gp_interleaver.c
${CODEC2_SRC}/interldpc.c
${CODEC2_SRC}/kiss_fft.c
${CODEC2_SRC}/kiss_fftr.c
${CODEC2_SRC}/interp.c
${CODEC2_SRC}/lsp.c
${CODEC2_SRC}/ofdm.c
${CODEC2_SRC}/ofdm_mode.c
${CODEC2_SRC}/phase.c
${CODEC2_SRC}/quantise.c
${CODEC2_SRC}/pack.c
${CODEC2_SRC}/dump.c
${CODEC2_SRC}/cohpsk.c
${CODEC2_SRC}/fdmdv.c
${CODEC2_SRC}/freedv_api.c
${CODEC2_SRC}/freedv_1600.c
${CODEC2_SRC}/freedv_700.c
${CODEC2_SRC}/freedv_2020.c
${CODEC2_SRC}/freedv_fsk.c
${CODEC2_SRC}/filter.c
${CODEC2_SRC}/varicode.c
${CODEC2_SRC}/golay23.c
${CODEC2_SRC}/freedv_data_channel.c
${CODEC2_SRC}/newamp1.c
${CODEC2_SRC}/mbest.c
${CODEC2_SRC}/HRA_112_112.c
${CODEC2_SRC}/HRA_56_56.c
${CODEC2_SRC}/linreg.c
${CODEC2_SRC}/mpdecode_core.c
${CODEC2_SRC}/ldpc_codes.c
${CODEC2_SRC}/phi0.c
${CODEC2_SRC}/HRAb_396_504.c
${CODEC2_SRC}/codec2_math_arm.c
codebook.c
codebookd.c
codebookjmv.c
codebookge.c
codebooknewamp1_energy.c
codebooknewamp1.c
)

set(STM32F4_ADAC_SRCS
src/stm32f4_adc.c 
src/stm32f4_dac.c 
../src/codec2_fifo.c
)
add_library(stm32f4_adac STATIC ${STM32F4_ADAC_SRCS})
add_library(codec2 STATIC ${CODEC2_SRCS})
add_library(codec2_prof STATIC ${CODEC2_SRCS})
target_compile_definitions(codec2_prof PRIVATE PROFILE)

set(SYSTEM_SRCS
src/system_stm32f4xx.c
src/startup_stm32f4xx.s
)

add_library(sm1000base STATIC src/sm1000_leds_switches.c src/debugblinky.c ${SYSTEM_SRCS})

set(PROFILE_SYSTEM_SRCS
src/stm32f4_machdep.c
)

list(APPEND PROFILE_SYSTEM_SRCS ${SYSTEM_SRCS})

#----------------------------

set(DAC_UT_SRCS
src/dac_ut.c
)
add_mapped_executable(dac_ut ${DAC_UT_SRCS})
target_link_libraries(dac_ut stm32f4_adac stm32f4 sm1000base)
elf2bin(dac_ut)

#----------------------------

set(USART_UT_SRCS
src/stm32f4_usart.c
src/usart_ut.c
)
add_mapped_executable(usart_ut ${USART_UT_SRCS})
target_link_libraries(usart_ut stm32f4 sm1000base)
elf2bin(usart_ut)

#----------------------------

set(USB_VCP
usb_conf/usb_bsp.c
usb_conf/usbd_desc.c
usb_conf/usbd_usr.c
usb_lib/cdc/usbd_cdc_core.c
usb_lib/cdc/usbd_cdc_vcp.c
usb_lib/core/usbd_core.c
usb_lib/core/usbd_ioreq.c
usb_lib/core/usbd_req.c
usb_lib/otg/usb_core.c
usb_lib/otg/usb_dcd.c
usb_lib/otg/usb_dcd_int.c)

set(USB_VCP_UT
src/usb_vcp_ut.c
src/stm32f4_usb_vcp.c
)

list(APPEND USB_VCP_UT ${USB_VCP})

add_definitions(-DUSE_USB_OTG_FS -DUSE_ULPI_PHY)
include_directories(usb_conf usb_lib/cdc usb_lib/core usb_lib/otg)

add_mapped_executable(usb_vcp_ut ${USB_VCP_UT})
target_link_libraries(usb_vcp_ut stm32f4 sm1000base)
elf2bin(usb_vcp_ut)

set(ADC_REC_USB_SRCS
src/adc_rec_usb.c
src/stm32f4_usb_vcp.c
)

add_mapped_executable(adc_rec_usb ${ADC_REC_USB_SRCS} ${USB_VCP})
target_link_libraries(adc_rec_usb stm32f4_adac stm32f4 sm1000base)
elf2bin(adc_rec_usb)

#----------------------------

set(SM1000_LEDS_SWITCHES_UT_SRCS
src/sm1000_leds_switches_ut.c
src/sm1000_leds_switches.c
)
add_mapped_executable(sm1000_leds_switches_ut ${SM1000_LEDS_SWITCHES_UT_SRCS})
target_link_libraries(sm1000_leds_switches_ut stm32f4 sm1000base)
elf2bin(sm1000_leds_switches_ut)

#----------------------------

set(SM1000_SRCS
src/sm1000_main.c
src/tone.c
src/sfx.c
src/sounds.c
src/morse.c
src/menu.c
src/tot.c
src/sm1000_leds_switches.c
../src/codec2_fifo.c
src/debugblinky.c
src/stm32f4_vrom.c
src/stm32f4_usart.c
src/memtools.c
)

list(APPEND SM1000_SRCS ${CODEC2_SRCS})

add_mapped_executable(sm1000v5 ${SM1000_SRCS} ${SYSTEM_SRCS})
target_link_libraries(sm1000v5 stm32f4_adac stm32f4 CMSIS)
target_compile_options(sm1000v5 PRIVATE "-O3")
elf2bin(sm1000v5)
