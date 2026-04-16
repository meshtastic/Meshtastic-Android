/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.meshtastic.core.takserver

import org.meshtastic.proto.CotHow
import org.meshtastic.proto.CotType

/**
 * Maps CoT type strings (e.g. "a-f-G-U-C") to CotType enum values and back.
 */
internal object TakV2TypeMapper {

    private val stringToType: Map<String, CotType> = mapOf(
        "a-f-G-U-C" to CotType.CotType_a_f_G_U_C,
        "a-f-G-U-C-I" to CotType.CotType_a_f_G_U_C_I,
        "a-n-A-C-F" to CotType.CotType_a_n_A_C_F,
        "a-n-A-C-H" to CotType.CotType_a_n_A_C_H,
        "a-n-A-C" to CotType.CotType_a_n_A_C,
        "a-f-A-M-H" to CotType.CotType_a_f_A_M_H,
        "a-f-A-M" to CotType.CotType_a_f_A_M,
        "a-h-A-M-F-F" to CotType.CotType_a_h_A_M_F_F,
        "a-u-A-C" to CotType.CotType_a_u_A_C,
        "t-x-d-d" to CotType.CotType_t_x_d_d,
        "b-t-f" to CotType.CotType_b_t_f,
        "b-r-f-h-c" to CotType.CotType_b_r_f_h_c,
        "b-a-o-pan" to CotType.CotType_b_a_o_pan,
        "b-a-o-opn" to CotType.CotType_b_a_o_opn,
        "a-f-G" to CotType.CotType_a_f_G,
        "a-f-G-U" to CotType.CotType_a_f_G_U,
        "a-h-G" to CotType.CotType_a_h_G,
        "a-u-G" to CotType.CotType_a_u_G,
        "a-n-G" to CotType.CotType_a_n_G,
        "b-m-r" to CotType.CotType_b_m_r,
        "b-m-p-s-p-i" to CotType.CotType_b_m_p_s_p_i,
        "u-d-f" to CotType.CotType_u_d_f,
        "a-f-A-C-F" to CotType.CotType_a_f_A_C_F,
        "a-f-A" to CotType.CotType_a_f_A,
        "a-f-G-E-S" to CotType.CotType_a_f_G_E_S,
        "b-m-p-s-p-loc" to CotType.CotType_b_m_p_s_p_loc,
        "b-i-v" to CotType.CotType_b_i_v,
    )

    private val typeToString: Map<CotType, String> =
        stringToType.entries.associate { (k, v) -> v to k }

    private val stringToHow: Map<String, CotHow> = mapOf(
        "h-e" to CotHow.CotHow_h_e,
        "m-g" to CotHow.CotHow_m_g,
        "h-g-i-g-o" to CotHow.CotHow_h_g_i_g_o,
        "m-r" to CotHow.CotHow_m_r,
    )

    private val howToStr: Map<CotHow, String> =
        stringToHow.entries.associate { (k, v) -> v to k }

    fun cotTypeFromString(s: String): CotType = stringToType[s] ?: CotType.CotType_Other

    fun cotTypeToString(type: CotType): String? = typeToString[type]

    fun cotHowFromString(s: String): CotHow = stringToHow[s] ?: CotHow.CotHow_Unspecified

    fun cotHowToString(how: CotHow): String? = howToStr[how]
}
