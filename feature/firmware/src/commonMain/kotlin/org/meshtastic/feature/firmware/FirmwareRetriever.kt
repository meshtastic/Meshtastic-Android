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
package org.meshtastic.feature.firmware

import org.koin.core.annotation.Single
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware

/** Retrieves firmware files, either by direct download or by extracting from a release asset. */
@Single
class FirmwareRetriever(private val fileHandler: FirmwareFileHandler) {
    suspend fun retrieveOtaFirmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): FirmwareArtifact? = retrieveOtaFirmware(fileHandler, release, hardware, onProgress)

    suspend fun retrieveUsbFirmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): FirmwareArtifact? = retrieveUsbFirmware(fileHandler, release, hardware, onProgress)

    suspend fun retrieveEsp32Firmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): FirmwareArtifact? = retrieveEsp32Firmware(fileHandler, release, hardware, onProgress)
}
