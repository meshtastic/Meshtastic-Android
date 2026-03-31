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
package org.meshtastic.desktop.stub

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.feature.firmware.DfuInternalState
import org.meshtastic.feature.firmware.FirmwareArtifact
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareUpdateManager
import org.meshtastic.feature.firmware.FirmwareUpdateState
import org.meshtastic.feature.firmware.FirmwareUsbManager

@Deprecated("Use DesktopFirmwareUpdateManager from feature:firmware jvmMain instead")
class NoopFirmwareUpdateManager : FirmwareUpdateManager {
    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri?,
    ): FirmwareArtifact? = null

    override fun dfuProgressFlow(): Flow<DfuInternalState> = emptyFlow()
}

@Deprecated("Use DesktopFirmwareUsbManager from feature:firmware jvmMain instead")
class NoopFirmwareUsbManager : FirmwareUsbManager {
    override fun deviceDetachFlow(): Flow<Unit> = emptyFlow()
}

@Deprecated("Use JvmFirmwareFileHandler from feature:firmware jvmMain instead")
@Suppress("EmptyFunctionBlock")
class NoopFirmwareFileHandler : FirmwareFileHandler {
    override fun cleanupAllTemporaryFiles() {}

    override suspend fun checkUrlExists(url: String): Boolean = false

    override suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): FirmwareArtifact? =
        null

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = null

    override suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = null

    override suspend fun getFileSize(file: FirmwareArtifact): Long = 0L

    override suspend fun deleteFile(file: FirmwareArtifact) {}

    override suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long = 0L
}
