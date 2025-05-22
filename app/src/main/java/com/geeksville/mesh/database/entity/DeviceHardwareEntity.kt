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
import com.geeksville.mesh.model.DeviceHardware
import com.geeksville.mesh.network.model.NetworkDeviceHardware
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "device_hardware")
data class DeviceHardwareEntity(
    @ColumnInfo(name = "actively_supported") val activelySupported: Boolean,
    val architecture: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "has_ink_hud") val hasInkHud: Boolean? = null,
    @ColumnInfo(name = "has_mui") val hasMui: Boolean? = null,
    @PrimaryKey val hwModel: Int,
    @ColumnInfo(name = "hw_model_slug") val hwModelSlug: String,
    val images: List<String>?,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "partition_scheme") val partitionScheme: String? = null,
    @ColumnInfo(name = "platformio_target") val platformioTarget: String,
    @ColumnInfo(name = "requires_dfu") val requiresDfu: Boolean?,
    @ColumnInfo(name = "support_level") val supportLevel: Int?,
    val tags: List<String>?,
)

fun NetworkDeviceHardware.asEntity() = DeviceHardwareEntity(
    activelySupported = activelySupported,
    architecture = architecture,
    displayName = displayName,
    hasInkHud = hasInkHud,
    hasMui = hasMui,
    hwModel = hwModel,
    hwModelSlug = hwModelSlug,
    images = images,
    lastUpdated = System.currentTimeMillis(),
    partitionScheme = partitionScheme,
    platformioTarget = platformioTarget,
    requiresDfu = requiresDfu,
    supportLevel = supportLevel,
    tags = tags,
)

fun DeviceHardwareEntity.asExternalModel() = DeviceHardware(
    activelySupported = activelySupported,
    architecture = architecture,
    displayName = displayName,
    hasInkHud = hasInkHud,
    hasMui = hasMui,
    hwModel = hwModel,
    hwModelSlug = hwModelSlug,
    images = images,
    partitionScheme = partitionScheme,
    platformioTarget = platformioTarget,
    requiresDfu = requiresDfu,
    supportLevel = supportLevel,
    tags = tags,
)
