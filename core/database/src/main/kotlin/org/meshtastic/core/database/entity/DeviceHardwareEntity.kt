/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.util.nowMillis

@Serializable
@Entity(tableName = "device_hardware")
data class DeviceHardwareEntity(
    @ColumnInfo(name = "actively_supported") val activelySupported: Boolean,
    val architecture: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "has_ink_hud") val hasInkHud: Boolean? = null,
    @ColumnInfo(name = "has_mui") val hasMui: Boolean? = null,
    val hwModel: Int,
    @ColumnInfo(name = "hw_model_slug") val hwModelSlug: String,
    val images: List<String>?,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = nowMillis,
    @ColumnInfo(name = "partition_scheme") val partitionScheme: String? = null,
    @PrimaryKey @ColumnInfo(name = "platformio_target") val platformioTarget: String,
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
    lastUpdated = nowMillis,
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
    requiresBootloaderUpgradeForOta = null,
    bootloaderInfoUrl = null,
    supportLevel = supportLevel,
    tags = tags,
)
