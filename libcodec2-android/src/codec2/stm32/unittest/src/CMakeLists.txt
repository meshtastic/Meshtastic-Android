set(STM32F4_SYSTEM_SRCS
../../src/system_stm32f4xx.c
../../src/memtools.c
../../src/stm32f4_machdep.c
startup_stm32f4xx.s
)

list(APPEND SEMIHOSTING_SRCS semihosting.c ${STM32F4_SYSTEM_SRCS})
list(APPEND SEMIHOSTING_PROFILE_LIBS codec2_prof stm32f4 CMSIS rdimon)
list(APPEND SEMIHOSTING_LIBS codec2 stm32f4 CMSIS rdimon)

macro(profiledSemihostedBin target)
	add_mapped_executable(${target} ${target}.c ${SEMIHOSTING_SRCS})
	target_link_libraries(${target} ${SEMIHOSTING_PROFILE_LIBS})
	target_compile_definitions(${target} PRIVATE "-DPROFILE -DSEMIHOST_USE_STDIO --specs=rdimon.specs")
	elf2bin(${target})
endmacro()

macro(semihostedBin target)
	add_mapped_executable(${target} ${target}.c ${SEMIHOSTING_SRCS})
	target_link_libraries(${target} ${SEMIHOSTING_LIBS})
	target_compile_definitions(${target} PRIVATE "-DSEMIHOST_USE_STDIO --specs=rdimon.specs")
	elf2bin(${target})
endmacro()

semihostedBin(tst_api_tx)
semihostedBin(tst_codec2_enc)
semihostedBin(tst_codec2_dec)
semihostedBin(tst_ofdm_mod)
profiledSemihostedBin(tst_ofdm_demod) 
semihostedBin(tst_ldpc_enc)
semihostedBin(tst_ldpc_dec)
semihostedBin(tst_api_mod)
profiledsemihostedBin(tst_api_demod)
semihostedBin(tst_semihost) 
semihostedBin(tst_codec2_fft_init)


add_test(NAME check_ram_limit
         COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/../scripts/check_ram_limit
         WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR})

add_test(NAME tst_ldpc_enc
         COMMAND sh -c "./run_stm32_tst tst_ldpc_enc ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_ldpc_dec_ideal
         COMMAND sh -c "./run_stm32_tst tst_ldpc_dec ideal ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_ldpc_dec_noise
         COMMAND sh -c "./run_stm32_tst tst_ldpc_dec noise ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_ldpc_dec_noise PROPERTIES DEPENDS tst_ldpc_dec_ideal)

add_test(NAME tst_ofdm_mod_plain
         COMMAND sh -c "./run_stm32_tst tst_ofdm_mod plain ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_ofdm_mod_ldpc
         COMMAND sh -c "./run_stm32_tst tst_ofdm_mod ldpc ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_ofdm_mod_ldpc PROPERTIES DEPENDS tst_ofdm_mod_plain)

add_test(NAME tst_ofdm_demod_quick
         COMMAND sh -c "./run_stm32_tst tst_ofdm_demod quick ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_ofdm_demod_ideal
         COMMAND sh -c "./run_stm32_tst tst_ofdm_demod ideal ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_ofdm_demod_ideal PROPERTIES DEPENDS tst_ofdm_demod_quick)

add_test(NAME tst_ofdm_demod_AWGN
         COMMAND sh -c "./run_stm32_tst tst_ofdm_demod AWGN ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_ofdm_demod_AWGN PROPERTIES DEPENDS tst_ofdm_demod_quick)

add_test(NAME tst_ofdm_demod_fade
         COMMAND sh -c "./run_stm32_tst tst_ofdm_demod fade ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_ofdm_demod_fade PROPERTIES DEPENDS tst_ofdm_demod_quick)

add_test(NAME tst_ofdm_demod_ldpc
         COMMAND sh -c "./run_stm32_tst tst_ofdm_demod ldpc ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_ofdm_demod_ldpc PROPERTIES DEPENDS tst_ofdm_demod_quick)

add_test(NAME tst_ofdm_demod_ldpc_AWGN
         COMMAND sh -c "./run_stm32_tst tst_ofdm_demod ldpc_AWGN ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_ofdm_demod_ldpc_AWGN PROPERTIES DEPENDS tst_ofdm_demod_quick)

add_test(NAME tst_ofdm_demod_ldpc_fade
         COMMAND sh -c "./run_stm32_tst tst_ofdm_demod ldpc_fade ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_ofdm_demod_ldpc_fade PROPERTIES DEPENDS tst_ofdm_demod_quick)

add_test(NAME tst_codec2_enc_1300
         COMMAND sh -c "./run_stm32_tst tst_codec2_enc 1300 ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_codec2_enc_700C
         COMMAND sh -c "./run_stm32_tst tst_codec2_enc 700C ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_codec2_enc_700C PROPERTIES DEPENDS tst_codec2_enc_1300)


add_test(NAME tst_codec2_dec_1300
         COMMAND sh -c "./run_stm32_tst tst_codec2_dec 1300 ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_codec2_dec_700C
         COMMAND sh -c "./run_stm32_tst tst_codec2_dec 700C ${UT_PARAMS} "
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)
set_tests_properties(tst_codec2_dec_700C PROPERTIES DEPENDS tst_codec2_dec_1300)

add_test(NAME tst_api_mod_700D_TEST
         COMMAND sh -c "./run_stm32_tst tst_api_mod 700D_TEST ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_mod_700D_CODEC
         COMMAND sh -c "./run_stm32_tst tst_api_mod 700D_CODEC ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_mod_700E_TEST
         COMMAND sh -c "./run_stm32_tst tst_api_mod 700E_TEST ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_mod_700E_CODEC
         COMMAND sh -c "./run_stm32_tst tst_api_mod 700E_CODEC ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_demod_700D_plain_test
         COMMAND sh -c "./run_stm32_tst tst_api_demod 700D_plain_test ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_demod_700D_AWGN_test
         COMMAND sh -c "./run_stm32_tst tst_api_demod 700D_AWGN_test ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_demod_700D_AWGN_codec
         COMMAND sh -c "./run_stm32_tst tst_api_demod 700D_AWGN_codec ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_demod_700E_plain_test
         COMMAND sh -c "./run_stm32_tst tst_api_demod 700E_plain_test ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_demod_700E_AWGN_test
         COMMAND sh -c "./run_stm32_tst tst_api_demod 700E_AWGN_test ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_demod_700E_AWGN_codec
         COMMAND sh -c "./run_stm32_tst tst_api_demod 700E_AWGN_codec ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

add_test(NAME tst_api_demod_1600_plain_codec
         COMMAND sh -c "./run_stm32_tst tst_api_demod 1600_plain_codec ${UT_PARAMS}"
         WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../scripts)

