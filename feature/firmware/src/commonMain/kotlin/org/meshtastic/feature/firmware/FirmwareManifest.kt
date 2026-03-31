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
package org.meshtastic.feature.firmware

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kotlin model for `.mt.json` firmware manifest files published alongside each firmware binary
 * since v2.7.17.
 *
 * The manifest is per-target, per-version and describes every partition image for a given device.
 * During ESP32 WiFi OTA we fetch the manifest on-demand, locate the `app0` partition entry, and
 * use its [FirmwareManifestFile.name] as the exact filename to download.
 *
 * Example URL:
 * ```
 * https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/
 *   firmware-2.7.17/firmware-t-deck-2.7.17.mt.json
 * ```
 */
@Serializable
internal data class FirmwareManifest(
    @SerialName("hwModel") val hwModel: String = "",
    val architecture: String = "",
    @SerialName("platformioTarget") val platformioTarget: String = "",
    val mcu: String = "",
    val files: List<FirmwareManifestFile> = emptyList(),
)

/**
 * A single partition file entry inside a [FirmwareManifest].
 *
 * @property name Filename of the binary (e.g. `firmware-t-deck-2.7.17.bin`).
 * @property partName Partition role: `app0` (main firmware — the OTA target), `app1` (OTA loader),
 *   or `spiffs` (filesystem image).
 * @property md5 MD5 hex digest of the binary content.
 * @property bytes Size of the binary in bytes.
 */
@Serializable
internal data class FirmwareManifestFile(
    val name: String,
    @SerialName("part_name") val partName: String = "",
    val md5: String = "",
    val bytes: Long = 0L,
)
