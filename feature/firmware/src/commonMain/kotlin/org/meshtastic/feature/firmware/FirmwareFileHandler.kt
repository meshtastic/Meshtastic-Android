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

interface FirmwareFileHandler {
    fun cleanupAllTemporaryFiles()

    suspend fun checkUrlExists(url: String): Boolean

    suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): String?

    suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String? = null,
    ): String?

    suspend fun extractFirmwareFromZip(
        zipFilePath: String,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String? = null,
    ): String?

    suspend fun getFileSize(path: String): Long

    suspend fun deleteFile(path: String)

    suspend fun copyFileToUri(sourcePath: String, destinationUri: CommonUri): Long

    suspend fun copyUriToUri(sourceUri: CommonUri, destinationUri: CommonUri): Long
}
