#
# Generated sources
#

set(D ${CMAKE_CURRENT_SOURCE_DIR}/../src/codebook)

# lsp quantisers

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

# lspd quantisers

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

set(CODEBOOKSJMV
    ${D}/lspjmv1.txt
    ${D}/lspjmv2.txt
    ${D}/lspjmv3.txt
)

set(CODEBOOKSMEL
    ${D}/mel1.txt
    ${D}/mel2.txt
    ${D}/mel3.txt
    ${D}/mel4.txt
    ${D}/mel5.txt
    ${D}/mel6.txt
)

set(CODEBOOKSLSPMELVQ
    ${D}/lspmelvq1.txt
    ${D}/lspmelvq2.txt
    ${D}/lspmelvq3.txt
)

set(CODEBOOKSGE ${D}/gecb.txt)

set(CODEBOOKSNEWAMP1
    ${D}/train_120_1.txt
    ${D}/train_120_2.txt
)

set(CODEBOOKSNEWAMP1_ENERGY
    ${D}/newamp1_energy_q.txt
)

set(CODEBOOKSNEWAMP2
    ${D}/codes_450.txt
)

set(CODEBOOKSNEWAMP2_ENERGY
    ${D}/newamp2_energy_q.txt
)

# when crosscompiling we need a native executable
if(CMAKE_CROSSCOMPILING)
    include(ExternalProject)
    set(SOURCE_DIR ${CMAKE_SOURCE_DIR}/..)
    ExternalProject_Add(codec2_native
       SOURCE_DIR ${SOURCE_DIR}
       BINARY_DIR ${CMAKE_BINARY_DIR}/src/codec2_native
       CONFIGURE_COMMAND ${CMAKE_COMMAND} ${SOURCE_DIR}
       BUILD_COMMAND ${CMAKE_COMMAND} --build ${CMAKE_BINARY_DIR}/src/codec2_native --target generate_codebook
       INSTALL_COMMAND ${CMAKE_COMMAND} -E copy src/generate_codebook ${CMAKE_CURRENT_BINARY_DIR}
    )
    add_executable(generate_codebook IMPORTED)
    set_target_properties(generate_codebook 
        PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_BINARY_DIR}/generate_codebook)
    add_dependencies(generate_codebook codec2_native)
else(CMAKE_CROSSCOMPILING)
# Build code generator binaries. These do not get installed.
    # generate_codebook
    add_executable(generate_codebook generate_codebook.c)
    target_link_libraries(generate_codebook ${CMAKE_REQUIRED_LIBRARIES})
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


# codebookmel.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebookmel.c
    COMMAND generate_codebook mel_cb ${CODEBOOKSMEL} > ${CMAKE_CURRENT_BINARY_DIR}/codebookmel.c
    DEPENDS generate_codebook ${CODEBOOKSMEL}
)

# codebooklspmelvq.c
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/codebooklspmelvq.c
    COMMAND generate_codebook lspmelvq_cb ${CODEBOOKSLSPMELVQ} > ${CMAKE_CURRENT_BINARY_DIR}/codebooklspmelvq.c
    DEPENDS generate_codebook ${CODEBOOKSLSPMELVQ}
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

