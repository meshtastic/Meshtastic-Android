/*
 * Copyright (c) 2024 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.model

import com.geeksville.mesh.MeshProtos.HardwareModel
import com.geeksville.mesh.R
import kotlinx.serialization.Serializable

data class DeviceHardware(
    val hwModel: Int,
    val hwModelSlug: String,
    val architecture: String,
    val activelySupported: Boolean,
    val supportLevel: Int? = null,
    val displayName: String,
    val tags: List<String>? = listOf(),
    val image: Int,
    val requiresDfu: Boolean? = null,
)

@Serializable
data class DeviceHardwareDto(
    val hwModel: Int,
    val hwModelSlug: String,
    val platformioTarget: String,
    val architecture: String,
    val activelySupported: Boolean,
    val supportLevel: Int? = null,
    val displayName: String,
    val tags: List<String>? = listOf(),
    val images: List<String>? = listOf(),
    val requiresDfu: Boolean? = null,
) {
    fun toDeviceHardware() = DeviceHardware(
        hwModel = hwModel,
        hwModelSlug = hwModelSlug,
        architecture = architecture,
        activelySupported = activelySupported,
        supportLevel = supportLevel,
        displayName = displayName,
        tags = tags,
        image = HardwareModel.forNumber(hwModel).getDeviceVectorImage(),
        requiresDfu = requiresDfu
    )
}

@Suppress("CyclomaticComplexMethod")
private fun HardwareModel.getDeviceVectorImage(): Int = when (this) {
    HardwareModel.DIY_V1 -> R.drawable.hw_diy
    HardwareModel.HELTEC_HT62 -> R.drawable.hw_heltec_ht62_esp32c3_sx1262
    HardwareModel.HELTEC_MESH_NODE_T114 -> R.drawable.hw_heltec_mesh_node_t114
    HardwareModel.HELTEC_V3 -> R.drawable.hw_heltec_v3
    HardwareModel.HELTEC_VISION_MASTER_E213 -> R.drawable.hw_heltec_vision_master_e213
    HardwareModel.HELTEC_VISION_MASTER_E290 -> R.drawable.hw_heltec_vision_master_e290
    HardwareModel.HELTEC_VISION_MASTER_T190 -> R.drawable.hw_heltec_vision_master_t190
    HardwareModel.HELTEC_WIRELESS_PAPER -> R.drawable.hw_heltec_wireless_paper
    HardwareModel.HELTEC_WIRELESS_TRACKER -> R.drawable.hw_heltec_wireless_tracker
    HardwareModel.HELTEC_WIRELESS_TRACKER_V1_0 -> R.drawable.hw_heltec_wireless_tracker_v1_0
    HardwareModel.HELTEC_WSL_V3 -> R.drawable.hw_heltec_wsl_v3
    HardwareModel.NANO_G2_ULTRA -> R.drawable.hw_nano_g2_ultra
    HardwareModel.RPI_PICO -> R.drawable.hw_pico
    HardwareModel.NRF52_PROMICRO_DIY -> R.drawable.hw_promicro
    HardwareModel.RAK11310 -> R.drawable.hw_rak11310
    HardwareModel.RAK4631 -> R.drawable.hw_rak4631
    HardwareModel.RPI_PICO2 -> R.drawable.hw_rpipicow
    HardwareModel.SENSECAP_INDICATOR -> R.drawable.hw_seeed_sensecap_indicator
    HardwareModel.SEEED_XIAO_S3 -> R.drawable.hw_seeed_xiao_s3
    HardwareModel.STATION_G2 -> R.drawable.hw_station_g2
    HardwareModel.T_DECK -> R.drawable.hw_t_deck
    HardwareModel.T_ECHO -> R.drawable.hw_t_echo
    HardwareModel.T_WATCH_S3 -> R.drawable.hw_t_watch_s3
    HardwareModel.TBEAM -> R.drawable.hw_tbeam
    HardwareModel.LILYGO_TBEAM_S3_CORE -> R.drawable.hw_tbeam_s3_core
    HardwareModel.TLORA_C6 -> R.drawable.hw_tlora_c6
    HardwareModel.TLORA_T3_S3 -> R.drawable.hw_tlora_t3s3_v1
    HardwareModel.TLORA_V2_1_1P6 -> R.drawable.hw_tlora_v2_1_1_6
    HardwareModel.TLORA_V2_1_1P8 -> R.drawable.hw_tlora_v2_1_1_8
    HardwareModel.TRACKER_T1000_E -> R.drawable.hw_tracker_t1000_e
    HardwareModel.WIO_WM1110 -> R.drawable.hw_wio_tracker_wm1110
    HardwareModel.WISMESH_TAP -> R.drawable.hw_rak_wismeshtap
    else -> R.drawable.hw_unknown
}
