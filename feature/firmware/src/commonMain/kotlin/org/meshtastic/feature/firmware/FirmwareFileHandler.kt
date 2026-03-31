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

import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.DeviceHardware

@Suppress("TooManyFunctions")
interface FirmwareFileHandler {
    fun cleanupAllTemporaryFiles()

    suspend fun checkUrlExists(url: String): Boolean

    suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): FirmwareArtifact?

    suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String? = null,
    ): FirmwareArtifact?

    suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String? = null,
    ): FirmwareArtifact?

    suspend fun getFileSize(file: FirmwareArtifact): Long

    /** Read the raw bytes of a [FirmwareArtifact]. */
    suspend fun readBytes(artifact: FirmwareArtifact): ByteArray

    /**
     * Copy a platform URI into a temporary [FirmwareArtifact] so it can be read with [readBytes]. Returns `null` when
     * the URI cannot be resolved.
     */
    suspend fun importFromUri(uri: CommonUri): FirmwareArtifact?

    /**
     * Extract all entries from a zip [artifact] into a `Map<entryName, bytes>`. Used by the DFU handler to parse Nordic
     * DFU packages.
     */
    suspend fun extractZipEntries(artifact: FirmwareArtifact): Map<String, ByteArray>

    suspend fun deleteFile(file: FirmwareArtifact)

    suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long
}
