set(CMAKE_SYSTEM_NAME Generic)
set(CMAKE_SYSTEM_PROCESSOR arm)
set(CMAKE_SYSTEM_VERSION 1)
set(CMAKE_ASM_FLAGS "${CFLAGS} -x assembler-with-cpp")
 
# specify the cross compiler
set(CMAKE_C_COMPILER ${ARM_GCC_BIN}arm-none-eabi-gcc)
set(CMAKE_CXX_COMPILER ${ARM_GCC_BIN}arm-none-eabi-cpp)
set(CMAKE_ASM ${ARM_GCC_BIN}arm-none-eabi-as)
set(CMAKE_OBJCOPY ${ARM_GCC_BIN}arm-none-eabi-objcopy)
set(CMAKE_C_FLAGS_INIT "-specs=nosys.specs" CACHE STRING "Required compiler init flags")
set(CMAKE_CXX_FLAGS_INIT "-specs=nosys.specs" CACHE STRING "Required compiler init flags")
## https://stackoverflow.com/questions/10599038/can-i-skip-cmake-compiler-tests-or-avoid-error-unrecognized-option-rdynamic
set(CMAKE_SHARED_LIBRARY_LINK_C_FLAGS "")
set(CMAKE_SHARED_LIBRARY_LINK_CXX_FLAGS "")
