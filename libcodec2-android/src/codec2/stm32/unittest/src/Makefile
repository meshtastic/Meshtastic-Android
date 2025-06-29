# Makefile for stm32f4 Codec 2 unittest programs

# Include local definitions if they exist.
-include local.mak

###################################################

FLOAT_TYPE=hard

###################################################

CROSS_COMPILE ?= arm-none-eabi-
CC=$(BINPATH)$(CROSS_COMPILE)gcc
AS=$(BINPATH)$(CROSS_COMPILE)as
OBJCOPY=$(BINPATH)$(CROSS_COMPILE)objcopy
SIZE=$(BINPATH)$(CROSS_COMPILE)size
SUDO ?= sudo

###################################################

CFLAGS  = -std=gnu11 -O2 -g -Wall -DSTM32F40_41xxx -DCORTEX_M4
CFLAGS += -mlittle-endian -mthumb -mthumb-interwork -nostartfiles -mcpu=cortex-m4 -Wno-unused-function

ifeq ($(FLOAT_TYPE), hard)
CFLAGS += -fsingle-precision-constant -Wdouble-promotion
CFLAGS += -fdata-sections -ffunction-sections -Xlinker --gc-sections
CFLAGS += -mfpu=fpv4-sp-d16 -mfloat-abi=hard -D__FPU_PRESENT=1 -D__FPU_USED=1
else
CFLAGS += -msoft-float
endif

#CFLAGS += -DDEBUG_ALLOC

###################################################
# STM32F4 Standard Peripheral Library

PERIPHLIBDIR	?= STM32F4xx_DSP_StdPeriph_Lib
CMSIS		= $(PERIPHLIBDIR)/Libraries/CMSIS
STM32F4LIB	= $(PERIPHLIBDIR)/Libraries/STM32F4xx_StdPeriph_Driver
STM32F4TEMPLATE	= $(PERIPHLIBDIR)/Project/STM32F4xx_StdPeriph_Templates
DSPLIB          = $(PERIPHLIBDIR)/Libraries/CMSIS/DSP_Lib

CFLAGS		+= -DUSE_STDPERIPH_DRIVER -I$(STM32F4LIB)/inc -I$(STM32F4TEMPLATE)
CFLAGS		+= -I$(CMSIS)/Include -I$(CMSIS)/Device/ST/STM32F4xx/Include
CFLAGS		+= -DARM_MATH_CM4
CFLAGS		+= -DFDV_ARM_MATH
CFLAGS		+= -DSEMIHOST_USE_STDIO

# Precious files that should be preserved at all cost!
.PRECIOUS: dl/$(PERIPHLIBZIP)

STM32F4LIB_SRCS=\
$(STM32F4LIB)/src/misc.c\
$(STM32F4LIB)/src/stm32f4xx_adc.c\
$(STM32F4LIB)/src/stm32f4xx_can.c\
$(STM32F4LIB)/src/stm32f4xx_cec.c\
$(STM32F4LIB)/src/stm32f4xx_crc.c\
$(STM32F4LIB)/src/stm32f4xx_cryp_aes.c\
$(STM32F4LIB)/src/stm32f4xx_cryp.c\
$(STM32F4LIB)/src/stm32f4xx_cryp_des.c\
$(STM32F4LIB)/src/stm32f4xx_cryp_tdes.c\
$(STM32F4LIB)/src/stm32f4xx_dac.c\
$(STM32F4LIB)/src/stm32f4xx_dbgmcu.c\
$(STM32F4LIB)/src/stm32f4xx_dcmi.c\
$(STM32F4LIB)/src/stm32f4xx_dma2d.c\
$(STM32F4LIB)/src/stm32f4xx_dma.c\
$(STM32F4LIB)/src/stm32f4xx_exti.c\
$(STM32F4LIB)/src/stm32f4xx_flash.c\
$(STM32F4LIB)/src/stm32f4xx_flash_ramfunc.c\
$(STM32F4LIB)/src/stm32f4xx_fmpi2c.c\
$(STM32F4LIB)/src/stm32f4xx_fsmc.c\
$(STM32F4LIB)/src/stm32f4xx_gpio.c\
$(STM32F4LIB)/src/stm32f4xx_hash.c\
$(STM32F4LIB)/src/stm32f4xx_hash_md5.c\
$(STM32F4LIB)/src/stm32f4xx_hash_sha1.c\
$(STM32F4LIB)/src/stm32f4xx_i2c.c\
$(STM32F4LIB)/src/stm32f4xx_iwdg.c\
$(STM32F4LIB)/src/stm32f4xx_ltdc.c\
$(STM32F4LIB)/src/stm32f4xx_pwr.c\
$(STM32F4LIB)/src/stm32f4xx_qspi.c\
$(STM32F4LIB)/src/stm32f4xx_rcc.c\
$(STM32F4LIB)/src/stm32f4xx_rng.c\
$(STM32F4LIB)/src/stm32f4xx_rtc.c\
$(STM32F4LIB)/src/stm32f4xx_sai.c\
$(STM32F4LIB)/src/stm32f4xx_sdio.c\
$(STM32F4LIB)/src/stm32f4xx_spdifrx.c\
$(STM32F4LIB)/src/stm32f4xx_spi.c\
$(STM32F4LIB)/src/stm32f4xx_syscfg.c\
$(STM32F4LIB)/src/stm32f4xx_tim.c\
$(STM32F4LIB)/src/stm32f4xx_usart.c\
$(STM32F4LIB)/src/stm32f4xx_wwdg.c

STM32F4LIB_OBJS = $(STM32F4LIB_SRCS:.c=.o)

CMSIS_SRCS=\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_abs_f32.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_abs_q15.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_abs_q31.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_abs_q7.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_add_f32.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_add_q15.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_add_q31.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_add_q7.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_dot_prod_f32.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_dot_prod_q15.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_dot_prod_q31.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_dot_prod_q7.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_mult_f32.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_mult_q15.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_mult_q31.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_mult_q7.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_negate_f32.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_negate_q15.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_negate_q31.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_negate_q7.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_offset_f32.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_offset_q15.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_offset_q31.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_offset_q7.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_scale_f32.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_scale_q15.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_scale_q31.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_scale_q7.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_shift_q15.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_shift_q31.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_shift_q7.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_sub_f32.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_sub_q15.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_sub_q31.c\
$(CMSIS)/DSP_Lib/Source/BasicMathFunctions/arm_sub_q7.c\
$(CMSIS)/DSP_Lib/Source/CommonTables/arm_common_tables.c\
$(CMSIS)/DSP_Lib/Source/CommonTables/arm_const_structs.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_conj_f32.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_conj_q15.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_conj_q31.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_dot_prod_f32.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_dot_prod_q15.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_dot_prod_q31.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mag_f32.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mag_q15.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mag_q31.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mag_squared_f32.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mag_squared_q15.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mag_squared_q31.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mult_cmplx_f32.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mult_cmplx_q15.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mult_cmplx_q31.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mult_real_f32.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mult_real_q15.c\
$(CMSIS)/DSP_Lib/Source/ComplexMathFunctions/arm_cmplx_mult_real_q31.c\
$(CMSIS)/DSP_Lib/Source/ControllerFunctions/arm_pid_init_f32.c\
$(CMSIS)/DSP_Lib/Source/ControllerFunctions/arm_pid_init_q15.c\
$(CMSIS)/DSP_Lib/Source/ControllerFunctions/arm_pid_init_q31.c\
$(CMSIS)/DSP_Lib/Source/ControllerFunctions/arm_pid_reset_f32.c\
$(CMSIS)/DSP_Lib/Source/ControllerFunctions/arm_pid_reset_q15.c\
$(CMSIS)/DSP_Lib/Source/ControllerFunctions/arm_pid_reset_q31.c\
$(CMSIS)/DSP_Lib/Source/ControllerFunctions/arm_sin_cos_f32.c\
$(CMSIS)/DSP_Lib/Source/ControllerFunctions/arm_sin_cos_q31.c\
$(CMSIS)/DSP_Lib/Source/FastMathFunctions/arm_cos_f32.c\
$(CMSIS)/DSP_Lib/Source/FastMathFunctions/arm_cos_q15.c\
$(CMSIS)/DSP_Lib/Source/FastMathFunctions/arm_cos_q31.c\
$(CMSIS)/DSP_Lib/Source/FastMathFunctions/arm_sin_f32.c\
$(CMSIS)/DSP_Lib/Source/FastMathFunctions/arm_sin_q15.c\
$(CMSIS)/DSP_Lib/Source/FastMathFunctions/arm_sin_q31.c\
$(CMSIS)/DSP_Lib/Source/FastMathFunctions/arm_sqrt_q15.c\
$(CMSIS)/DSP_Lib/Source/FastMathFunctions/arm_sqrt_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_32x64_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_32x64_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_fast_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_fast_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_init_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df1_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df2T_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df2T_f64.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df2T_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_df2T_init_f64.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_stereo_df2T_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_biquad_cascade_stereo_df2T_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_fast_opt_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_fast_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_fast_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_opt_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_opt_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_partial_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_partial_fast_opt_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_partial_fast_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_partial_fast_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_partial_opt_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_partial_opt_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_partial_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_partial_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_partial_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_conv_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_correlate_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_correlate_fast_opt_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_correlate_fast_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_correlate_fast_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_correlate_opt_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_correlate_opt_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_correlate_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_correlate_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_correlate_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_decimate_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_decimate_fast_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_decimate_fast_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_decimate_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_decimate_init_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_decimate_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_decimate_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_decimate_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_fast_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_fast_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_init_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_init_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_interpolate_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_interpolate_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_interpolate_init_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_interpolate_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_interpolate_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_interpolate_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_lattice_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_lattice_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_lattice_init_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_lattice_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_lattice_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_lattice_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_sparse_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_sparse_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_sparse_init_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_sparse_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_sparse_init_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_sparse_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_sparse_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_fir_sparse_q7.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_iir_lattice_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_iir_lattice_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_iir_lattice_init_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_iir_lattice_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_iir_lattice_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_iir_lattice_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_init_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_norm_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_norm_init_f32.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_norm_init_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_norm_init_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_norm_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_norm_q31.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_q15.c\
$(CMSIS)/DSP_Lib/Source/FilteringFunctions/arm_lms_q31.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_add_f32.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_add_q15.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_add_q31.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_cmplx_mult_f32.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_cmplx_mult_q15.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_cmplx_mult_q31.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_init_f32.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_init_q15.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_init_q31.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_inverse_f32.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_inverse_f64.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_mult_f32.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_mult_fast_q15.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_mult_fast_q31.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_mult_q15.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_mult_q31.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_scale_f32.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_scale_q15.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_scale_q31.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_sub_f32.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_sub_q15.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_sub_q31.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_trans_f32.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_trans_q15.c\
$(CMSIS)/DSP_Lib/Source/MatrixFunctions/arm_mat_trans_q31.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_max_f32.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_max_q15.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_max_q31.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_max_q7.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_mean_f32.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_mean_q15.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_mean_q31.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_mean_q7.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_min_f32.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_min_q15.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_min_q31.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_min_q7.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_power_f32.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_power_q15.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_power_q31.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_power_q7.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_rms_f32.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_rms_q15.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_rms_q31.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_std_f32.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_std_q15.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_std_q31.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_var_f32.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_var_q15.c\
$(CMSIS)/DSP_Lib/Source/StatisticsFunctions/arm_var_q31.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_copy_f32.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_copy_q15.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_copy_q31.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_copy_q7.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_fill_f32.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_fill_q15.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_fill_q31.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_fill_q7.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_float_to_q15.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_float_to_q31.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_float_to_q7.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_q15_to_float.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_q15_to_q31.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_q15_to_q7.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_q31_to_float.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_q31_to_q15.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_q31_to_q7.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_q7_to_float.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_q7_to_q15.c\
$(CMSIS)/DSP_Lib/Source/SupportFunctions/arm_q7_to_q31.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_bitreversal.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_q15.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_q31.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix2_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix2_init_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix2_init_q15.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix2_init_q31.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix2_q15.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix2_q31.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix4_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix4_init_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix4_init_q15.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix4_init_q31.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix4_q15.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix4_q31.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_cfft_radix8_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_dct4_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_dct4_init_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_dct4_init_q15.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_dct4_init_q31.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_dct4_q15.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_dct4_q31.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_rfft_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_rfft_fast_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_rfft_fast_init_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_rfft_init_f32.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_rfft_init_q15.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_rfft_init_q31.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_rfft_q15.c\
$(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_rfft_q31.c\

CMSIS_OBJS = $(CMSIS_SRCS:.c=.o) $(CMSIS)/DSP_Lib/Source/TransformFunctions/arm_bitreversal2.o

###################################################
# Codec 2

CODEC2_DIR = ../../..
CODEC2_SRC = $(CODEC2_DIR)/src
CODEC2_BLD = $(CODEC2_DIR)/build_linux

CODEC2_SRCS=\
$(CODEC2_SRC)/lpc.c \
$(CODEC2_SRC)/nlp.c \
$(CODEC2_SRC)/postfilter.c \
$(CODEC2_SRC)/sine.c \
$(CODEC2_SRC)/codec2.c \
$(CODEC2_SRC)/codec2_fft.c \
$(CODEC2_SRC)/cohpsk.c \
$(CODEC2_SRC)/linreg.c \
$(CODEC2_SRC)/kiss_fft.c \
$(CODEC2_SRC)/kiss_fftr.c \
$(CODEC2_SRC)/interp.c \
$(CODEC2_SRC)/lsp.c \
$(CODEC2_SRC)/mbest.c \
$(CODEC2_SRC)/newamp1.c \
$(CODEC2_SRC)/phase.c \
$(CODEC2_SRC)/quantise.c \
$(CODEC2_SRC)/pack.c \
$(CODEC2_SRC)/codebook.c \
$(CODEC2_SRC)/codebookd.c \
$(CODEC2_SRC)/codebookjvm.c \
$(CODEC2_SRC)/codebookge.c \
$(CODEC2_SRC)/codebooknewamp1.c \
$(CODEC2_SRC)/codebooknewamp1_energy.c \
$(CODEC2_SRC)/dump.c \
$(CODEC2_SRC)/fdmdv.c \
$(CODEC2_SRC)/freedv_api.c \
$(CODEC2_SRC)/filter.c \
$(CODEC2_SRC)/varicode.c \
$(CODEC2_SRC)/golay23.c \
$(CODEC2_SRC)/fsk.c \
$(CODEC2_SRC)/fmfsk.c \
$(CODEC2_SRC)/freedv_vhf_framing.c \
$(CODEC2_SRC)/freedv_data_channel.c \
$(CODEC2_SRC)/ofdm.c \
$(CODEC2_SRC)/phi0.c \
$(CODEC2_SRC)/mpdecode_core.c \
$(CODEC2_SRC)/gp_interleaver.c \
$(CODEC2_SRC)/interldpc.c \
$(CODEC2_SRC)/HRA_112_112.c \

CFLAGS += -D__EMBEDDED__
CFLAGS += -I$(CODEC2_SRC)
CFLAGS += -I$(CODEC2_BLD)

###################################################
# Codec2/STM32

CODEC2_STM32_DIR := ../..
CODEC2_STM32_SRC = $(CODEC2_STM32_DIR)/src
CODEC2_STM32_HDR = $(CODEC2_STM32_DIR)/inc
CFLAGS += -I$(CODEC2_STM32_HDR)
CFLAGS += -T$(CODEC2_STM32_DIR)/stm32_flash.ld 

#enable this for dump files to help verify optimisation
#CFLAGS += -DDUMP
ASFLAGS += $(CFLAGS)

###################################################

ROOT=$(shell pwd)

# Library paths

LIBPATHS =

# Libraries to link

# Standard ARM semihosting
LIBS = -lg -lrdimon -lm --specs=rdimon.specs

# startup file

SRCS += startup_stm32f4xx.s 
SRCS += init.c 
SRCS += stm32f4_machdep.c
SRCS += $(CODEC2_STM32_SRC)/system_stm32f4xx.c

all: libstm32f4.a \
     tst_codec2_enc.bin tst_codec2_dec.bin \
     tst_ofdm_mod.bin tst_ofdm_demod.bin \
     tst_ldpc_enc.bin tst_ldpc_dec.bin \
     tst_api_mod.bin tst_api_demod.bin \
     tst_semihost.bin \
     tst_codec2_fft_init.bin

libstm32f4.a: $(CMSIS_OBJS) $(STM32F4LIB_OBJS)
	find -L $(PERIPHLIBDIR) -type f -name '*.o' -exec $(AR) crs libstm32f4.a {} ";"

# Kludgy target to build a file with CFLAGS -DPROFILE
%.profile.o: %.c
	$(CC) $(CPPFLAGS) $(CFLAGS) -DPROFILE -c -o $@ $<

# Rule for building .bin files from a .elf
%.bin: %.elf
	$(OBJCOPY) -O binary $< $@

#####%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%####
##### Used for debugging stdio (with semihosting) :
##
## TMP_NEWLIB_INCS = -INewlib
## TMP_NEWLIB_DEFS = -DARM_RDI_MONITOR
## TMP_NEWLIB_OBJS = Newlib/fread.o Newlib/refill.o Newlib/syscalls.o Newlib/stdio.o Newlib/readr.o Newlib/fclose.o Newlib/fflush.o
## 
## Newlib/fread.o: Newlib/fread.c
## 	$(CC) -c $(CFLAGS) $^ -o Newlib/fread.o $(TMP_NEWLIB_INCS) $(TMP_NEWLIB_DEFS)
## 
## Newlib/refill.o: Newlib/refill.c
## 	$(CC) -c $(CFLAGS) $^ -o Newlib/refill.o $(TMP_NEWLIB_INCS) $(TMP_NEWLIB_DEFS)
## 
## Newlib/syscalls.o: Newlib/syscalls.c
## 	$(CC) -c $(CFLAGS) $^ -o Newlib/syscalls.o $(TMP_NEWLIB_INCS) $(TMP_NEWLIB_DEFS)
## 
## Newlib/stdio.o: Newlib/stdio.c
## 	$(CC) -c $(CFLAGS) $^ -o Newlib/stdio.o $(TMP_NEWLIB_INCS) $(TMP_NEWLIB_DEFS)
## 
## Newlib/readr.o: Newlib/readr.c
## 	$(CC) -c $(CFLAGS) $^ -o Newlib/readr.o $(TMP_NEWLIB_INCS) $(TMP_NEWLIB_DEFS)
## 
#####%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%####

####################################################
# Test Programs

# -----------------------------------------------
TST_API_TX_SRCS=\
tst_api_tx.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c

TST_API_TX_SRCS += $(CODEC2_SRCS)
TST_API_TX_SRCS += $(SRCS)

tst_api_tx.elf: $(TST_API_TX_SRCS:.c=.profile.o) libstm32f4.a
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS)

# -----------------------------------------------
TST_CODEC2_ENC_SRCS=\
tst_codec2_enc.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_CODEC2_ENC_SRCS += $(CODEC2_SRCS)
TST_CODEC2_ENC_SRCS += $(SRCS)
#
tst_codec2_enc.elf: $(TST_CODEC2_ENC_SRCS:.c=.profile.o) libstm32f4.a
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS) -Wl,-Map=tst_codec2_enc.map

# -----------------------------------------------
TST_CODEC2_DEC_SRCS=\
tst_codec2_dec.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_CODEC2_DEC_SRCS += $(CODEC2_SRCS)
TST_CODEC2_DEC_SRCS += $(SRCS)
#
tst_codec2_dec.elf: $(TST_CODEC2_DEC_SRCS:.c=.profile.o) libstm32f4.a
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS)

# -----------------------------------------------
TST_API_MOD_SRCS=\
tst_api_mod.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_API_MOD_SRCS += $(CODEC2_SRCS)
TST_API_MOD_SRCS += $(SRCS)
#
tst_api_mod.elf: $(TST_API_MOD_SRCS:.c=.profile.o) libstm32f4.a # $(TMP_NEWLIB_OBJS) 
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS)

# -----------------------------------------------
# TST_API_MOD_700D_PROFILE
#
TST_API_MOD_700D_PROFILE_SRCS=\
tst_api_mod_700d_profile.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_API_MOD_700D_PROFILE_SRCS += $(CODEC2_SRCS)
TST_API_MOD_700D_PROFILE_SRCS += $(SRCS)
#
tst_api_mod_700d_profile.elf: $(TST_API_MOD_700D_PROFILE_SRCS:.c=.profile.o) libstm32f4.a 
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS)

# -----------------------------------------------
# TST_API_DEMOD
TST_API_DEMOD_SRCS=\
tst_api_demod.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
## (for debug use) sbrk_dbg.c \
#
TST_API_DEMOD_SRCS += $(CODEC2_SRCS)
TST_API_DEMOD_SRCS += $(SRCS)
#
tst_api_demod.elf: $(TST_API_DEMOD_SRCS:.c=.o) libstm32f4.a
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS) -Wl,-Map=tst_api_demod.map

# -----------------------------------------------
# TST_API_DEMOD_700D_PROFILE
api_demod_700d_in_10f:
	# Each frame of OFDM is 160ms which is 1280 speech samples of 2 bytes each.
	#
	dd bs=2560 count=100 if=../../../raw/hts1.raw of=tmp_spch_in.raw
	cohpsk_ch tmp_spch_in.raw tmp_modout.raw -20 -Fs 8000 -f -5 --raw_dir ../../../raw 
	freedv_tx 700D tmp_modout.raw api_demod_700d_in_10f --txbpf 0
#
api_demod_700d_in_10f.c: api_demod_700d_in_10f
	xxd -i api_demod_700d_in_10f > api_demod_700d_in_10f.c
#
TST_API_DEMOD_700D_PROFILE_SRCS=\
tst_api_demod_700d_profile.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
## (for debug use) sbrk_dbg.c \
#
tst_api_demod_700d_profile.profile.o: tst_api_demod_700d_profile.c api_demod_700d_in_10f.c
	$(CC) $(CPPFLAGS) $(CFLAGS) -DPROFILE -c -o $@ $<
#
TST_API_DEMOD_700D_PROFILE_SRCS += $(CODEC2_SRCS)
TST_API_DEMOD_700D_PROFILE_SRCS += $(SRCS)
#
tst_api_demod_700d_profile.elf: $(TST_API_DEMOD_700D_PROFILE_SRCS:.c=.profile.o) libstm32f4.a
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS)

# -----------------------------------------------
TST_OFDM_MOD_SRCS=\
tst_ofdm_mod.c \
semihosting.c \
$(CODEC2_SRC)/ofdm.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c

TST_OFDM_MOD_SRCS += $(CODEC2_SRCS)
TST_OFDM_MOD_SRCS += $(SRCS)

tst_ofdm_mod.elf: $(TST_OFDM_MOD_SRCS:.c=.profile.o) libstm32f4.a
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS)

# -----------------------------------------------
TST_OFDM_DEMOD_SRCS=\
tst_ofdm_demod.c \
semihosting.c \
$(CODEC2_SRC)/ofdm.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_OFDM_DEMOD_SRCS += $(CODEC2_SRCS)
TST_OFDM_DEMOD_SRCS += $(SRCS)
#
tst_ofdm_demod.elf: $(TST_OFDM_DEMOD_SRCS:.c=.profile.o) libstm32f4.a
	$(CC) $(CFLAGS) $^ -o $@ $(LIBPATHS) $(LIBS) -Wl,-Map=tst_ofdm_demod.map

# -----------------------------------------------
# TST_OFDM_MOD_STACK
#
ofdm_mod_ref_10f: 
	$(CODEC2_BLD)/src/ofdm_mod /dev/zero ofdm_mod_ref_10f --testframes 1 --ldpc
#
ofdm_mod_ref_10f.c: ofdm_mod_ref_10f
	xxd -g2 -e -i ofdm_mod_ref_10f > ofdm_mod_ref_10f.c
#
tst_ofdm_mod_stack.profile.o: tst_ofdm_mod_stack.c ofdm_mod_ref_10f.c
	$(CC) $(CPPFLAGS) $(CFLAGS) -DPROFILE -c -o $@ $<
#
TST_OFDM_MOD_STACK_SRCS=\
tst_ofdm_mod_stack.c \
function_trace.c \
$(CODEC2_SRC)/ofdm.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_OFDM_MOD_STACK_SRCS += $(CODEC2_SRCS)
TST_OFDM_MOD_STACK_SRCS += $(SRCS)
#
tst_ofdm_mod_stack.elf: $(TST_OFDM_MOD_STACK_SRCS:.c=.o) libstm32f4.a
	$(CC) $(CFLAGS) $^ -o $@ $(LIBPATHS) $(LIBS) -Wl,-Map=tst_ofdm_mod_stack.map

# -----------------------------------------------
# TST_OFDM_DEMOD_STACK
ofdm_demod_in_10f:
	$(CODEC2_BLD)/src/ofdm_get_test_bits - -f 10 | \
	$(CODEC2_BLD)/src/ofdm_mod - ofdm_demod_in_10f
#
ofdm_demod_in_10f.c: ofdm_demod_in_10f
	xxd -i ofdm_demod_in_10f > ofdm_demod_in_10f.c
#
ofdm_demod_ref_10f: ofdm_demod_in_10f
	$(CODEC2_BLD)/src/ofdm_demod ofdm_demod_in_10f ofdm_demod_ref_10f
#
ofdm_demod_ref_10f.c: ofdm_demod_ref_10f
	xxd -i ofdm_demod_ref_10f > ofdm_demod_ref_10f.c
#
tst_ofdm_demod_stack.o: ofdm_demod_in_10f.c ofdm_demod_ref_10f.c
#
TST_OFDM_DEMOD_STACK_SRCS=\
tst_ofdm_demod_stack.c \
$(CODEC2_SRC)/ofdm.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_OFDM_DEMOD_STACK_SRCS += $(CODEC2_SRCS)
TST_OFDM_DEMOD_STACK_SRCS += $(SRCS)
#
tst_ofdm_demod_stack.elf: $(TST_OFDM_DEMOD_STACK_SRCS:.c=.o) libstm32f4.a
	$(CC) $(CFLAGS) $^ -o $@ $(LIBPATHS) $(LIBS) -Wl,-Map=tst_ofdm_demod_stack.map

# -----------------------------------------------
# Not working yet!
TST_LDPC_ENC_SRCS= \
tst_ldpc_enc.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_LDPC_ENC_SRCS += $(CODEC2_SRCS)
TST_LDPC_ENC_SRCS += $(SRCS)
#
TST_LDPC_ENC_HDR= \
$(CODEC2_INC)/mpdecode_code_test.h
#
tst_ldpc_enc.elf: $(TST_LDPC_ENC_SRCS:.c=.profile.o) libstm32f4.a $(TST_LDPC_ENC_HRDS)
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS)

# -----------------------------------------------
# Not working yet!
TST_LDPC_DEC_SRCS= \
tst_ldpc_dec.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_LDPC_DEC_SRCS += $(CODEC2_SRCS)
TST_LDPC_DEC_SRCS += $(SRCS)
#
TST_LDPC_DEC_HDR= \
$(CODEC2_INC)/mpdecode_code.h
#
tst_ldpc_dec.elf: $(TST_LDPC_DEC_SRCS:.c=.profile.o) libstm32f4.a $(TST_LDPC_DEC_HRDS)
	$(CC) $(CFLAGS) -DPROFILE $^ -o $@ $(LIBPATHS) $(LIBS) -Wl,-Map=tst_ldpc_dec.map

# -----------------------------------------------
TST_SEMIHOST_SRCS=\
tst_semihost.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_SEMIHOST_SRCS += $(CODEC2_SRCS)
TST_SEMIHOST_SRCS += $(SRCS)
#
tst_semihost.elf: $(TST_SEMIHOST_SRCS:.c=.o) libstm32f4.a #$(TMP_NEWLIB_OBJS) 
	$(CC) $(CFLAGS) $^ -o $@ $(LIBPATHS) $(LIBS)

# -----------------------------------------------
TST_CODEC2_FFT_INIT_SRCS=\
tst_codec2_fft_init.c \
semihosting.c \
$(CODEC2_STM32_SRC)/system_stm32f4xx.c
#
TST_CODEC2_FFT_INIT_SRCS += $(CODEC2_SRCS)
TST_CODEC2_FFT_INIT_SRCS += $(SRCS)
#
tst_codec2_fft_init.elf: $(TST_CODEC2_FFT_INIT_SRCS:.c=.o) libstm32f4.a
	$(CC) $(CFLAGS) $^ -o $@ $(LIBPATHS) $(LIBS)


###################################################

clean:
	rm -f *.elf *.bin
	rm -f libstm32f4.a
	rm -f $(CMSIS_OBJS) $(STM32F4LIB_OBJS) $(CODEC2_SRC)/*.o
	find . -type f -name '*.o' | xargs rm -f
