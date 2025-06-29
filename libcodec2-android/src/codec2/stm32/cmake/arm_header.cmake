CMAKE_MINIMUM_REQUIRED(VERSION 2.8.4)
 
#custom command to use objcopy to create .bin files out of ELF files
function(make_mbed_firmware INPUT)
              add_custom_command(TARGET ${INPUT}
                      COMMAND arm-none-eabi-objcopy -O binary ${INPUT} ${INPUT}_${MBED_TARGET}.bin
                      COMMENT "objcopying to make mbed compatible firmware")
              set_directory_properties(PROPERTIES ADDITIONAL_MAKE_CLEAN_FILES ${INPUT}_${MBED_TARGET}.bin)
endfunction(make_mbed_firmware)
 
#assume we're using an LPC1768 model if it's not specified by -DMBED_TARGET= 
 if( NOT MBED_TARGET  MATCHES "LPC1768" AND NOT MBED_TARGET MATCHES "LPC2368" AND NOT MBED_TARGET MATCHES "LPC11U24")
   message(STATUS "invalid or no mbed target specified. Options are LPC1768, LPC2368 or LPC11U24. Assuming LPC1768 for now.
            Target may be specified using -DMBED_TARGET=")
   set(MBED_TARGET "LPC1768")
endif( NOT MBED_TARGET  MATCHES "LPC1768" AND NOT MBED_TARGET MATCHES "LPC2368" AND NOT MBED_TARGET MATCHES "LPC11U24")
 
set(MBED_INCLUDE "${CMAKE_SOURCE_DIR}/mbed/${MBED_TARGET}/GCC_CS/")
 
#setup target specific object files
if(MBED_TARGET MATCHES "LPC1768")
    set(MBED_PREFIX "LPC17")
    set(CORE "cm3")
    set(CHIP ${MBED_INCLUDE}sys.o
        ${MBED_INCLUDE}startup_LPC17xx.o)
elseif(MBED_TARGET MATCHES "LPC2368")
    set(CHIP  ${MBED_INCLUDE}vector_functions.o
          ${MBED_INCLUDE}vector_realmonitor.o
          ${MBED_INCLUDE}vector_table.o)
    set(MBED_PREFIX "LPC23")
    set(CORE "arm7")
elseif(MBED_TARGET MATCHES "LPC11U24")
    set(CHIP    ${MBED_INCLUDE}sys.o
        ${MBED_INCLUDE}startup_LPC11xx.o)
       set(CORE "cm0")
       set(MBED_PREFIX "LPC11U")
endif(MBED_TARGET MATCHES "LPC1768")
 
#setup precompiled mbed files which will be needed for all projects
set(CHIP     ${CHIP}
        ${MBED_INCLUDE}system_${MBED_PREFIX}xx.o
        ${MBED_INCLUDE}cmsis_nvic.o
        ${MBED_INCLUDE}core_${CORE}.o)
 
#force min size build type
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE MinSizeRel CACHE STRING
        "Choose the type of build, options are: None Debug Release RelWithDebInfo MinSizeRel."
        FORCE)
endif(NOT CMAKE_BUILD_TYPE)
 
#set correct linker script
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} \"-T${CMAKE_SOURCE_DIR}/mbed/${MBED_TARGET}/GCC_CS/${MBED_TARGET}.ld\" -static")
 
#find CodeSourcery Toolchain for appropriate include dirs
find_path(CSPATH arm-none-eabi-g++ PATHS ENV)
message(STATUS "${CSPATH} is where CodeSourcery is installed")
 
#setup directories for  appropriate  C, C++, mbed libraries and includes
include_directories(${MBED_INCLUDE})
include_directories(mbed)
include_directories(${CSPATH}/../arm-none-eabi/include)
include_directories(${CSPATH}/../arm-none-eabi/include/c++/4.6.1)
link_directories(${MBED_INCLUDE})
