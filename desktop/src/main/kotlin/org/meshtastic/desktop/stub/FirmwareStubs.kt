package org.meshtastic.desktop.stub

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.feature.firmware.DfuInternalState
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareUpdateManager
import org.meshtastic.feature.firmware.FirmwareUpdateState
import org.meshtastic.feature.firmware.FirmwareUsbManager

class NoopFirmwareUpdateManager : FirmwareUpdateManager {
    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri?,
    ): String? = null

    override fun dfuProgressFlow(): Flow<DfuInternalState> = emptyFlow()
}

class NoopFirmwareUsbManager : FirmwareUsbManager {
    override fun deviceDetachFlow(): Flow<Unit> = emptyFlow()
}

class NoopFirmwareFileHandler : FirmwareFileHandler {
    override fun cleanupAllTemporaryFiles() {}

    override suspend fun checkUrlExists(url: String): Boolean = false

    override suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): String? = null

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): String? = null

    override suspend fun extractFirmwareFromZip(
        zipFilePath: String,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): String? = null

    override suspend fun getFileSize(path: String): Long = 0L

    override suspend fun deleteFile(path: String) {}

    override suspend fun copyFileToUri(sourcePath: String, destinationUri: CommonUri): Long = 0L

    override suspend fun copyUriToUri(sourceUri: CommonUri, destinationUri: CommonUri): Long = 0L
}
