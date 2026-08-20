/*
 * Copyright (c) 2026 Meshtastic LLC
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

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.meshtastic.core.model.BootloaderOtaQuirk
import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.model.SoftDeviceVariantEntry

/** Lenient so decoding a cached column survives a model that gained fields since it was written (forward-compat). */
private val entityJson = Json { ignoreUnknownKeys = true }

/**
 * Single-row cache of the nRF52 bootloader/OTA quirk catalog (`/resource/bootloaderOtaQuirks`), pre-serialized rather
 * than modeled as per-row tables — the only two readers (`applyBootloaderQuirk`/`applySoftDeviceVariant` in
 * `DeviceHardwareRepositoryImpl`) always want the whole envelope and filter it in Kotlin, so a granular schema would
 * add migration surface for no query anyone runs. [id] is always [SINGLETON_ID]; this table only ever holds one row.
 */
@Serializable
@Entity(tableName = "bootloader_ota_quirks_cache")
data class BootloaderOtaQuirksCacheEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "devices_json") val devicesJson: String = "[]",
    @ColumnInfo(name = "soft_device_variants_json") val softDeviceVariantsJson: String = "[]",
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

fun BootloaderOtaQuirksResponse.asEntity() = BootloaderOtaQuirksCacheEntity(
    devicesJson = entityJson.encodeToString(devices),
    softDeviceVariantsJson = entityJson.encodeToString(softDeviceVariants),
)

// A malformed column value decodes to an empty list rather than propagating the failure — consistent with the
// bundled-asset seed path, and safe for the same reason: an empty softDeviceVariants list still resolves every
// hwModel to `null`, which refuses.
fun BootloaderOtaQuirksCacheEntity.asExternalModel() = BootloaderOtaQuirksResponse(
    devices =
    runCatching { entityJson.decodeFromString<List<BootloaderOtaQuirk>>(devicesJson) }
        .getOrDefault(emptyList()),
    softDeviceVariants =
    runCatching { entityJson.decodeFromString<List<SoftDeviceVariantEntry>>(softDeviceVariantsJson) }
        .getOrDefault(emptyList()),
)
