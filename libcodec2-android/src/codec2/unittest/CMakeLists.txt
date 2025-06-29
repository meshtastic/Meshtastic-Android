add_definitions(-DFLOATING_POINT -DVAR_ARRAYS)
include_directories(../src)

add_executable(tfdmdv tfdmdv.c ../src/fdmdv.c ../src/kiss_fft.c ../src/octave.c)
target_link_libraries(tfdmdv codec2)

add_executable(tcohpsk tcohpsk.c ../src/cohpsk.c ../src/octave.c)
target_link_libraries(tcohpsk codec2)

add_executable(tfsk tfsk.c ../src/kiss_fft.c ../src/kiss_fftr.c ../src/octave.c ../src/modem_probe.c)
target_link_libraries(tfsk m)

add_executable(tfreedv_data_channel tfreedv_data_channel.c ../src/freedv_data_channel.c)

add_executable(tfmfsk tfmfsk.c ../src/octave.c ../src/modem_probe.c)
target_link_libraries(tfmfsk m)

add_executable(tdeframer tdeframer.c)
target_link_libraries(tdeframer m codec2)

add_definitions(-DMODEMPROBE_ENABLE -DXXXXX)

add_executable(tofdm tofdm.c ../src/octave.c)
target_link_libraries(tofdm m codec2)

add_executable(tofdm_acq tofdm_acq.c ../src/octave.c)
target_link_libraries(tofdm_acq m codec2)

add_executable(tesno_est tesno_est.c)
target_link_libraries(tesno_est m codec2)

if(UNIX) # Uses pthreads
add_executable(tfifo tfifo.c ../src/codec2_fifo.c)
target_link_libraries(tfifo codec2 ${CMAKE_THREAD_LIBS_INIT})
endif()

add_executable(fdmdv_mem fdmdv_mem.c)

add_executable(ofdm_mem ofdm_mem.c ../src/ofdm.c ../src/octave.c ../src/kiss_fft.c ../src/modem_probe.c ../src/mpdecode_core.c ../src/phi0.c ../src/filter.c)
target_link_libraries(ofdm_mem m)

add_library(function_trace STATIC ../unittest/function_trace.c)

add_executable(ofdm_stack ofdm_stack.c ../src/ofdm.c ../src/octave.c ../src/kiss_fft.c ../src/modem_probe.c ../src/mpdecode_core.c ../src/phi0.c ../src/filter.c)
if (CMAKE_C_COMPILER MATCHES "gcc$")
    target_link_libraries(ofdm_stack function_trace m -no-pie "-Wl,-Map=ofdm_stack.map")
    target_compile_options(ofdm_stack PUBLIC -fstack-usage -finstrument-functions)
else()
    target_link_libraries(ofdm_stack function_trace m -no-pie)
    target_compile_options(ofdm_stack PUBLIC -finstrument-functions)
endif()
add_definitions(-D__UNITTEST__)

add_executable(tnewamp1 tnewamp1.c ../src/quantise.c ../src/newamp1.c ../src/mbest.c ../src/kiss_fft.c ../src/sine.c ../src/nlp.c ../src/dump.c ../src/octave.c ${CODEBOOKS})
target_link_libraries(tnewamp1 codec2)

add_executable(compare_ints compare_ints.c)

add_executable(compare_floats compare_floats.c)

add_executable(test_phi0 test_phi0.c ../src/phi0.c)
target_link_libraries(test_phi0 m)

add_executable(tst_codec2_fft_init tst_codec2_fft_init.c)
target_link_libraries(tst_codec2_fft_init m codec2)

add_executable(tvq_mbest tvq_mbest.c)

add_executable(tfreedv_800XA_rawdata tfreedv_800XA_rawdata.c)
target_link_libraries(tfreedv_800XA_rawdata codec2)

add_executable(tfreedv_2400A_rawdata tfreedv_2400A_rawdata.c)
target_link_libraries(tfreedv_2400A_rawdata codec2)

add_executable(tfreedv_2400B_rawdata tfreedv_2400B_rawdata.c)
target_link_libraries(tfreedv_2400B_rawdata codec2)

add_executable(tfsk_llr tfsk_llr.c)
target_link_libraries(tfsk_llr codec2 m)

add_executable(thash thash.c)
target_link_libraries(thash codec2 m)

add_executable(tqam16 tqam16.c)
target_link_libraries(tqam16 codec2 m)

add_executable(t16_8 t16_8.c ../src/fdmdv.c ../src/kiss_fft.c)
target_link_libraries(t16_8 codec2)

add_executable(t16_8_short t16_8_short.c ../src/fdmdv.c ../src/kiss_fft.c)
target_link_libraries(t16_8_short codec2)


add_executable(t48_8 t48_8.c ../src/fdmdv.c ../src/kiss_fft.c)
target_link_libraries(t48_8 codec2)

add_executable(t48_8_short t48_8_short.c ../src/fdmdv.c ../src/kiss_fft.c)
target_link_libraries(t48_8_short codec2)

# Build CML as part of unit test setup
find_program(OCTAVE_CMD octave-cli REQUIRED)
message("Octave command: ${OCTAVE_CMD}")

include(ExternalProject)
set(CML_PATH ${CMAKE_CURRENT_BINARY_DIR}/../cml)
ExternalProject_Add(cml
   GIT_REPOSITORY https://github.com/drowe67/cml.git
   SOURCE_DIR ${CML_PATH}
   BUILD_IN_SOURCE 1
   CONFIGURE_COMMAND true # No configuration required
   BUILD_COMMAND cd ${CMAKE_CURRENT_BINARY_DIR}/../cml && make
   INSTALL_COMMAND true # No installation required
)

# Create fading files (used for channel simulation) as part of unit test setup
add_custom_target(fading_files ALL
  DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/fast_fading_samples.float
  DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/faster_fading_samples.float
  )
add_custom_command(
  OUTPUT  ${CMAKE_CURRENT_BINARY_DIR}/fast_fading_samples.float
  OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/faster_fading_samples.float
  COMMAND cd ${CMAKE_CURRENT_SOURCE_DIR} && ./fading_files.sh ${CMAKE_CURRENT_BINARY_DIR}
)

add_executable(freedv_700d_comptx freedv_700d_comptx.c)
add_executable(freedv_700d_comprx freedv_700d_comprx.c)

if(LPCNET AND lpcnetfreedv_FOUND)
    target_link_libraries(freedv_700d_comptx m codec2 lpcnetfreedv)
    target_link_libraries(freedv_700d_comprx m codec2 lpcnetfreedv)
    list(APPEND CODEC2_PUBLIC_HEADERS ${CMAKE_SOURCE_DIR}/lpcnet/src/lpcnet_freedv.h)
else()
    target_link_libraries(freedv_700d_comptx m codec2)
    target_link_libraries(freedv_700d_comprx m codec2)
endif()