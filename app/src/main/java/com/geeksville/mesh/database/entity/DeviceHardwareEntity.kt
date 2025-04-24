/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.model.DeviceHardware
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "device_hardware")
data class DeviceHardwareEntity(
    @PrimaryKey val hwModel: Int,
    @ColumnInfo(name = "hw_model_slug") val hwModelSlug: String,
    @ColumnInfo(name = "platformio_target") val platformioTarget: String,
    val architecture: String,
    @ColumnInfo(name = "actively_supported") val activelySupported: Boolean,
    @ColumnInfo(name = "support_level") val supportLevel: Int?,
    @ColumnInfo(name = "display_name") val displayName: String,
    val tags: List<String>?,
    val images: List<String>?,
    @ColumnInfo(name = "requires_dfu") val requiresDfu: Boolean?,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
)

fun NetworkDeviceHardware.asEntity() = DeviceHardwareEntity(
    hwModel = hwModel,
    hwModelSlug = hwModelSlug,
    architecture = architecture,
    activelySupported = activelySupported,
    supportLevel = supportLevel,
    displayName = displayName,
    tags = tags,
    images = images,
    requiresDfu = requiresDfu,
    platformioTarget = platformioTarget
)

fun DeviceHardwareEntity.asExternalModel() = DeviceHardware(
    hwModel = hwModel,
    hwModelSlug = hwModelSlug,
    architecture = architecture,
    activelySupported = activelySupported,
    supportLevel = supportLevel,
    displayName = displayName,
    tags = tags,
    image = getDrawableFrom(hwModel),
    requiresDfu = requiresDfu
)

@Serializable
data class NetworkDeviceHardware(
    val hwModel: Int,
    val hwModelSlug: String,
    val platformioTarget: String,
    val architecture: String,
    val activelySupported: Boolean,
    val supportLevel: Int?,
    val displayName: String,
    val tags: List<String>?,
    val images: List<String>?,
    val requiresDfu: Boolean?
)

@Suppress("CyclomaticComplexMethod")
private fun getDrawableFrom(hwModel: Int): Int = when (hwModel) {
    MeshProtos.HardwareModel.DIY_V1_VALUE -> R.drawable.hw_diy
    MeshProtos.HardwareModel.HELTEC_HT62_VALUE -> R.drawable.hw_heltec_ht62_esp32c3_sx1262
    MeshProtos.HardwareModel.HELTEC_MESH_NODE_T114_VALUE -> R.drawable.hw_heltec_mesh_node_t114
    MeshProtos.HardwareModel.HELTEC_V3_VALUE -> R.drawable.hw_heltec_v3_case
    MeshProtos.HardwareModel.HELTEC_VISION_MASTER_E213_VALUE -> R.drawable.hw_heltec_vision_master_e213
    MeshProtos.HardwareModel.HELTEC_VISION_MASTER_E290_VALUE -> R.drawable.hw_heltec_vision_master_e290
    MeshProtos.HardwareModel.HELTEC_VISION_MASTER_T190_VALUE -> R.drawable.hw_heltec_vision_master_t190
    MeshProtos.HardwareModel.HELTEC_WIRELESS_PAPER_VALUE -> R.drawable.hw_heltec_wireless_paper
    MeshProtos.HardwareModel.HELTEC_WIRELESS_TRACKER_VALUE -> R.drawable.hw_heltec_wireless_tracker
    MeshProtos.HardwareModel.HELTEC_WIRELESS_TRACKER_V1_0_VALUE -> R.drawable.hw_heltec_wireless_tracker_v1_0
    MeshProtos.HardwareModel.HELTEC_WSL_V3_VALUE -> R.drawable.hw_heltec_wsl_v3
    MeshProtos.HardwareModel.NANO_G2_ULTRA_VALUE -> R.drawable.hw_nano_g2_ultra
    MeshProtos.HardwareModel.RPI_PICO_VALUE -> R.drawable.hw_pico
    MeshProtos.HardwareModel.NRF52_PROMICRO_DIY_VALUE -> R.drawable.hw_promicro
    MeshProtos.HardwareModel.RAK11310_VALUE -> R.drawable.hw_rak11310
    MeshProtos.HardwareModel.RAK2560_VALUE -> R.drawable.hw_rak2560
    MeshProtos.HardwareModel.RAK4631_VALUE -> R.drawable.hw_rak4631_case
    MeshProtos.HardwareModel.RPI_PICO2_VALUE -> R.drawable.hw_rpipicow
    MeshProtos.HardwareModel.SENSECAP_INDICATOR_VALUE -> R.drawable.hw_seeed_sensecap_indicator
    MeshProtos.HardwareModel.SEEED_XIAO_S3_VALUE -> R.drawable.hw_seeed_xiao_s3
    MeshProtos.HardwareModel.STATION_G2_VALUE -> R.drawable.hw_station_g2
    MeshProtos.HardwareModel.T_DECK_VALUE -> R.drawable.hw_t_deck
    MeshProtos.HardwareModel.T_ECHO_VALUE -> R.drawable.hw_t_echo
    MeshProtos.HardwareModel.T_WATCH_S3_VALUE -> R.drawable.hw_t_watch_s3
    MeshProtos.HardwareModel.TBEAM_VALUE -> R.drawable.hw_tbeam
    MeshProtos.HardwareModel.LILYGO_TBEAM_S3_CORE_VALUE -> R.drawable.hw_tbeam_s3_core
    MeshProtos.HardwareModel.TLORA_C6_VALUE -> R.drawable.hw_tlora_c6
    MeshProtos.HardwareModel.TLORA_T3_S3_VALUE -> R.drawable.hw_tlora_t3s3_v1
    MeshProtos.HardwareModel.TLORA_V2_1_1P6_VALUE -> R.drawable.hw_tlora_v2_1_1_6
    MeshProtos.HardwareModel.TLORA_V2_1_1P8_VALUE -> R.drawable.hw_tlora_v2_1_1_8
    MeshProtos.HardwareModel.TRACKER_T1000_E_VALUE -> R.drawable.hw_tracker_t1000_e
    MeshProtos.HardwareModel.WIO_WM1110_VALUE -> R.drawable.hw_wio_tracker_wm1110
    MeshProtos.HardwareModel.WISMESH_TAP_VALUE -> R.drawable.hw_rak_wismeshtap
    else -> R.drawable.hw_unknown
}
