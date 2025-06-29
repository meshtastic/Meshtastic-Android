#
# Generated sources
#

set(D ${CMAKE_CURRENT_SOURCE_DIR}/codebook)

# lsp quantisers (2400/1400/1300 modes)
set(CODEBOOKS
    ${D}/lsp1.txt
    ${D}/lsp2.txt
    ${D}/lsp3.txt
    ${D}/lsp4.txt
    ${D}/lsp5.txt
    ${D}/lsp6.txt
    ${D}/lsp7.txt
    ${D}/lsp8.txt
    ${D}/lsp9.txt
    ${D}/lsp10.txt
)

# lspd quantisers (3200 mode)
set(CODEBOOKSD
    ${D}/dlsp1.txt
    ${D}/dlsp2.txt
    ${D}/dlsp3.txt
    ${D}/dlsp4.txt
    ${D}/dlsp5.txt
    ${D}/dlsp6.txt
    ${D}/dlsp7.txt
    ${D}/dlsp8.txt
    ${D}/dlsp9.txt
    ${D}/dlsp10.txt
)

# LSP VQ (1200 mode)
set(CODEBOOKSJMV
    ${D}/lspjmv1.txt
    ${D}/lspjmv2.txt
    ${D}/lspjmv3.txt
)

# Joint Wo-energy VQ (1200 mode)
set(CODEBOOKSGE ${D}/gecb.txt)

# 700C two stage VQ
set(CODEBOOKSNEWAMP1
    ${D}/train_120_1.txt
    ${D}/train_120_2.txt
)

set(CODEBOOKSNEWAMP1_ENERGY
    ${D}/newamp1_energy_q.txt
)

# 450 mode two stage VQ
set(CODEBOOKSNEWAMP2
    ${D}/codes_450.txt
)

set(CODEBOOKSNEWAMP2_ENERGY
    ${D}/newamp2_energy_q.txt
)

# when crosscompiling we need a native executable
if(CMAKE_CROSSCOMPILING)
    set(CMAKE_DISABLE_SOURCE_CHANGES OFF)
    include(ExternalProject)
    ExternalProject_Add(codec2_native
       SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/..
       BINARY_DIR ${CMAKE_CURRENT_BINARY_DIR}/codec2_native
       BUILD_COMMAND ${CMAKE_COMMAND} --build . --target generate_codebook
       INSTALL_COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_BINARY_DIR}/codec2_native/src/generate_codebook ${CMAKE_CURRENT_BINARY_DIR}
       BUILD_BYPRODUCTS ${CMAKE_CURRENT_BINARY_DIR}/generate_codebook
    )
    add_executable(generate_codebook IMPORTED)
    set_target_properties(generate_codebook PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_BINARY_DIR}/generate_codebook)
    add_dependencies(generate_codebook codec2_native)
    set(CMAKE_DISABLE_SOURCE_CHANGES ON)
else(CMAKE_CROSSCOMPILING)
# Build code generator binaries. These do not get installed.
    # generate_codebook
    add_executable(generate_codebook generate_codebook.c)
    target_link_libraries(generate_codebook m)
    # Make native builds available for cross-compiling.
    export(TARGETS generate_codebook
        FILE ${CMAKE_BINARY_DIR}/ImportExecutables.cmake)
endif(CMAKE_CROSSCOMPILING)


# codebook.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebook.c
    COMMAND generate_codebook lsp_cb ${CODEBOOKS} > ${CMAKE_CURRENT_BINARY_DIR}/codebook.c
    DEPENDS generate_codebook ${CODEBOOKS}
)

# codebookd.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebookd.c
    COMMAND generate_codebook lsp_cbd ${CODEBOOKSD} > ${CMAKE_CURRENT_BINARY_DIR}/codebookd.c
    DEPENDS generate_codebook ${CODEBOOKSD}
)

# codebookjmv.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebookjmv.c
    COMMAND generate_codebook lsp_cbjmv ${CODEBOOKSJMV} > ${CMAKE_CURRENT_BINARY_DIR}/codebookjmv.c
    DEPENDS generate_codebook ${CODEBOOKSJMV}
)

# codebookge.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebookge.c
    COMMAND generate_codebook ge_cb ${CODEBOOKSGE} > ${CMAKE_CURRENT_BINARY_DIR}/codebookge.c
    DEPENDS generate_codebook ${CODEBOOKSGE}
)

# codebooknewamp1.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebooknewamp1.c
    COMMAND generate_codebook newamp1vq_cb ${CODEBOOKSNEWAMP1} > ${CMAKE_CURRENT_BINARY_DIR}/codebooknewamp1.c
    DEPENDS generate_codebook ${CODEBOOKSNEWAMP1}
)

# codebooknewamp1_energy.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebooknewamp1_energy.c
    COMMAND generate_codebook newamp1_energy_cb ${CODEBOOKSNEWAMP1_ENERGY} > ${CMAKE_CURRENT_BINARY_DIR}/codebooknewamp1_energy.c
    DEPENDS generate_codebook ${CODEBOOKSNEWAMP1_ENERGY}
)

# codebooknewamp2.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebooknewamp2.c
    COMMAND generate_codebook newamp2vq_cb ${CODEBOOKSNEWAMP2} > ${CMAKE_CURRENT_BINARY_DIR}/codebooknewamp2.c
    DEPENDS generate_codebook ${CODEBOOKSNEWAMP2}
)

# codebooknewamp2_energy.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebooknewamp2_energy.c
    COMMAND generate_codebook newamp2_energy_cb ${CODEBOOKSNEWAMP2_ENERGY} > ${CMAKE_CURRENT_BINARY_DIR}/codebooknewamp2_energy.c
    DEPENDS generate_codebook ${CODEBOOKSNEWAMP2_ENERGY}
)

#
# codec2 library sources
#

set(CODEC2_SRCS
    dump.c
    lpc.c
    nlp.c
    postfilter.c
    sine.c
    codec2.c
    codec2_fft.c
    cohpsk.c
    codec2_fifo.c
    fdmdv.c
    fm.c
    fsk.c
    fmfsk.c
    kiss_fft.c
    kiss_fftr.c
    linreg.c
    interp.c
    lsp.c
    mbest.c
    newamp1.c
    newamp2.c
    ofdm.c
    ofdm_mode.c
    phase.c
    quantise.c
    pack.c
    codebook.c
    codebookd.c
    codebookjmv.c
    codebookge.c
    codebooknewamp1.c
    codebooknewamp1_energy.c
    codebooknewamp2.c
    codebooknewamp2_energy.c
    golay23.c
    freedv_api.c
    freedv_1600.c
    freedv_700.c
    freedv_2020.c
    freedv_fsk.c
    freedv_vhf_framing.c
    freedv_data_channel.c
    varicode.c
    modem_stats.c
    ofdm.c
    mpdecode_core.c
    phi0.c
    gp_interleaver.c
    interldpc.c
    filter.c
    HRA_112_112.c
    HRA_56_56.c
    HRAb_396_504.c
    H_256_768_22.c
    H_256_512_4.c
    HRAa_1536_512.c
    H_128_256_5.c
    H_2064_516_sparse.c
    H_4096_8192_3d.c
    H_16200_9720.c
    H_1024_2048_4f.c
    ldpc_codes.c
    lpcnet_freq.c
    reliable_text.c
)

set(CODEC2_PUBLIC_HEADERS
    codec2.h
    codec2_fdmdv.h
    codec2_cohpsk.h
    codec2_fm.h
    codec2_ofdm.h
    fsk.h
    codec2_fifo.h
    comp.h
    modem_stats.h
    freedv_api.h
    reliable_text.h
    codec2_math.h
    ${CODEC2_VERSION_PATH}/version.h
)

#
# Debug options
#
#add_definitions(-DDEBUG_ALLOC)

#
# Setup the codec2 library
#
# Patch level version bumps should not change API/ABI.
set(SOVERSION "${CODEC2_VERSION_MAJOR}.${CODEC2_VERSION_MINOR}")
message(STATUS "codec2 version: ${CODEC2_VERSION}")
add_library(codec2 ${CODEC2_SRCS})
if(UNIX)
    target_link_libraries(codec2 PUBLIC m)
endif(UNIX)
if(LPCNET AND lpcnetfreedv_FOUND)
    target_link_libraries(codec2 PRIVATE lpcnetfreedv)
    list(APPEND CODEC2_PUBLIC_HEADERS ${CMAKE_SOURCE_DIR}/lpcnet/src/lpcnet_freedv.h)
endif()
include_directories(${CMAKE_CURRENT_SOURCE_DIR})
set_target_properties(codec2 PROPERTIES
    SOVERSION ${SOVERSION}
    PUBLIC_HEADER "${CODEC2_PUBLIC_HEADERS}"
)

# Required include directories for export
target_include_directories(codec2 INTERFACE
    $<INSTALL_INTERFACE:include/codec2>
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
    $<BUILD_INTERFACE:${CMAKE_BINARY_DIR}>
)

# Export config for import from full install
install(EXPORT codec2-config
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/codec2
)

# Export config for import from build tree.
export(TARGETS codec2
    FILE ${CMAKE_BINARY_DIR}/codec2.cmake
)

add_executable(c2enc c2enc.c)
target_link_libraries(c2enc codec2)

add_executable(c2dec c2dec.c)
target_link_libraries(c2dec codec2)

add_executable(c2sim c2sim.c sd.c)
target_link_libraries(c2sim codec2)

add_executable(fdmdv_get_test_bits fdmdv_get_test_bits.c fdmdv.c kiss_fft.c codec2_fft.c kiss_fftr.c)
target_link_libraries(fdmdv_get_test_bits m)

add_executable(fdmdv_mod fdmdv_mod.c fdmdv.c kiss_fft.c codec2_fft.c kiss_fftr.c)
target_link_libraries(fdmdv_mod m)

add_executable(fdmdv_demod fdmdv_demod.c fdmdv.c kiss_fft.c octave.c modem_stats.c codec2_fft.c kiss_fftr.c)
target_link_libraries(fdmdv_demod m)

add_executable(fdmdv_put_test_bits fdmdv_put_test_bits.c fdmdv.c kiss_fft.c codec2_fft.c kiss_fftr.c)
target_link_libraries(fdmdv_put_test_bits m)

add_executable(fdmdv_channel fdmdv_channel.c)
target_link_libraries(fdmdv_channel codec2)

add_executable(insert_errors insert_errors.c)
target_link_libraries(insert_errors)

add_executable(freedv_tx freedv_tx.c)
target_link_libraries(freedv_tx codec2)

add_executable(freedv_rx freedv_rx.c)
target_link_libraries(freedv_rx codec2)

add_executable(freedv_data_raw_tx freedv_data_raw_tx.c)
target_link_libraries(freedv_data_raw_tx codec2)

add_executable(freedv_data_raw_rx freedv_data_raw_rx.c octave.c)
target_link_libraries(freedv_data_raw_rx codec2)

add_executable(freedv_data_tx freedv_data_tx.c)
target_link_libraries(freedv_data_tx codec2)

add_executable(freedv_data_rx freedv_data_rx.c)
target_link_libraries(freedv_data_rx codec2)

add_executable(freedv_mixed_tx freedv_mixed_tx.c)
target_link_libraries(freedv_mixed_tx codec2)

add_executable(freedv_mixed_rx freedv_mixed_rx.c)
target_link_libraries(freedv_mixed_rx codec2)

add_executable(fsk_mod fsk_mod.c)
target_link_libraries(fsk_mod codec2)

add_executable(fsk_mod_ext_vco fsk_mod_ext_vco.c)
target_link_libraries(fsk_mod_ext_vco m)

add_executable(fsk_demod fsk_demod.c modem_probe.c octave.c)
target_link_libraries(fsk_demod codec2)

add_executable(fsk_get_test_bits fsk_get_test_bits.c)
target_link_libraries(fsk_get_test_bits)

add_executable(fsk_put_test_bits fsk_put_test_bits.c)
target_link_libraries(fsk_put_test_bits codec2)

add_executable(framer framer.c)
target_link_libraries(framer)

add_executable(deframer deframer.c)
target_link_libraries(deframer)

add_executable(fm_demod fm_demod.c fm.c)
target_link_libraries(fm_demod m)

add_executable(cohpsk_mod cohpsk_mod.c)
target_link_libraries(cohpsk_mod codec2)

add_executable(ofdm_get_test_bits ofdm_get_test_bits.c)
target_link_libraries(ofdm_get_test_bits codec2)

add_executable(ofdm_put_test_bits ofdm_put_test_bits.c)
target_link_libraries(ofdm_put_test_bits codec2)

add_executable(ofdm_mod ofdm_mod.c)
target_link_libraries(ofdm_mod codec2)

add_executable(ofdm_demod ofdm_demod.c octave.c)
target_link_libraries(ofdm_demod codec2)

add_executable(fmfsk_mod fmfsk_mod.c)
target_link_libraries(fmfsk_mod codec2)

add_executable(fmfsk_demod fmfsk_demod.c modem_probe.c octave.c)
target_link_libraries(fmfsk_demod codec2)

add_executable(vhf_deframe_c2 vhf_deframe_c2.c)
target_link_libraries(vhf_deframe_c2  codec2)

add_executable(vhf_frame_c2 vhf_frame_c2.c)
target_link_libraries(vhf_frame_c2  codec2)

add_executable(cohpsk_demod cohpsk_demod.c octave.c)
target_link_libraries(cohpsk_demod codec2)

add_executable(cohpsk_get_test_bits cohpsk_get_test_bits.c)
target_link_libraries(cohpsk_get_test_bits codec2)

add_executable(cohpsk_put_test_bits cohpsk_put_test_bits.c octave.c)
target_link_libraries(cohpsk_put_test_bits codec2)

add_executable(ch ch.c)
target_link_libraries(ch codec2)

add_executable(tollr tollr.c)

add_executable(ldpc_noise ldpc_noise.c)
target_link_libraries(ldpc_noise m)

add_executable(ldpc_enc ldpc_enc.c)
target_link_libraries(ldpc_enc codec2)

add_executable(ldpc_dec ldpc_dec.c)
target_link_libraries(ldpc_dec codec2)

install(TARGETS codec2 EXPORT codec2-config
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR} COMPONENT lib
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR} COMPONENT lib
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    PUBLIC_HEADER DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/codec2 COMPONENT dev
)
