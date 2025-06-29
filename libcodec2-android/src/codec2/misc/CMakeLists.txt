add_definitions(-DFLOATING_POINT -DVAR_ARRAYS)
include_directories(../src)

add_executable(mksine mksine.c)
target_link_libraries(mksine m)

add_executable(16_8_short 16_8_short.c ../src/fdmdv.c ../src/kiss_fft.c)
target_link_libraries(16_8_short codec2)

add_executable(extract extract.c)

add_executable(vqtrain vqtrain.c)
target_link_libraries(vqtrain m)

add_executable(raw2h raw2h.c)
target_link_libraries(raw2h codec2)

add_executable(tnlp tnlp.c)
target_link_libraries(tnlp codec2)

add_executable(tlininterp tlininterp.c)

add_executable(tdec tdec.c)

add_executable(timpulse timpulse.c)
target_link_libraries(timpulse m)

add_executable(vq_mbest vq_mbest.c)
target_link_libraries(vq_mbest codec2)

add_executable(vq_binary_switch vq_binary_switch.c)
target_link_libraries(vq_binary_switch m)

add_executable(pre pre.c)
target_link_libraries(pre codec2)
