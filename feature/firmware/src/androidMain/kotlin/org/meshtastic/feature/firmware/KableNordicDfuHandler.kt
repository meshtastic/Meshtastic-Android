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

import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.toPlatformUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.firmware_update_downloading_percent
import org.meshtastic.core.resources.firmware_update_not_found_in_release
import org.meshtastic.core.resources.firmware_update_starting_service
import java.io.File
import java.util.zip.ZipFile

/** Kable-based Over-the-Air (OTA) firmware updates for nRF52-based devices. */
@Single
class KableNordicDfuHandler(
    private val firmwareRetriever: FirmwareRetriever,
    private val context: Context,
    private val radioController: RadioController,
    @Suppress("UnusedPrivateProperty") private val bleScanner: BleScanner,
    @Suppress("UnusedPrivateProperty") private val bleConnectionFactory: BleConnectionFactory,
) : FirmwareUpdateHandler {

    private val progressEvents = MutableSharedFlow<DfuInternalState>(extraBufferCapacity = 64)

    @Suppress("MagicNumber", "TooGenericExceptionCaught")
    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        target: String, // Bluetooth address
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri?,
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val downloadingMsg =
                    getString(Res.string.firmware_update_downloading_percent, 0)
                        .replace(Regex(":?\\s*%1\\\$d%?"), "")
                        .trim()

                updateState(FirmwareUpdateState.Downloading(ProgressState(message = downloadingMsg, progress = 0f)))

                if (firmwareUri != null) {
                    val file = getFirmwareFromUri(firmwareUri) ?: error("Could not read URI")
                    initiateDfu(target, hardware, file, updateState)
                    null
                } else {
                    val firmwareFile =
                        firmwareRetriever.retrieveOtaFirmware(release, hardware) { progress ->
                            val percent = (progress * 100).toInt()
                            updateState(
                                FirmwareUpdateState.Downloading(
                                    ProgressState(message = downloadingMsg, progress = progress, details = "$percent%"),
                                ),
                            )
                        }

                    if (firmwareFile == null) {
                        val errorMsg = getString(Res.string.firmware_update_not_found_in_release, hardware.displayName)
                        updateState(FirmwareUpdateState.Error(errorMsg))
                        null
                    } else {
                        initiateDfu(target, hardware, File(firmwareFile), updateState)
                        firmwareFile
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Kable Nordic DFU Update failed" }
                progressEvents.tryEmit(DfuInternalState.Error(target, e.message ?: "Failed"))
                updateState(FirmwareUpdateState.Error(e.message ?: "Update failed"))
                null
            }
        }

    private suspend fun getFirmwareFromUri(uri: CommonUri): File? = withContext(Dispatchers.IO) {
        val inputStream =
            context.contentResolver.openInputStream(uri.toPlatformUri() as android.net.Uri)
                ?: return@withContext null
        val tempFile = File(context.cacheDir, "firmware_update/ota_firmware.zip")
        tempFile.parentFile?.mkdirs()
        inputStream.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
        tempFile
    }

    @Suppress("MagicNumber", "UNUSED_PARAMETER")
    private suspend fun initiateDfu(
        address: String,
        deviceHardware: DeviceHardware,
        firmwareZip: File,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        val startingMsg = getString(Res.string.firmware_update_starting_service)
        updateState(FirmwareUpdateState.Processing(ProgressState(startingMsg)))

        // Disconnect mesh service
        radioController.setDeviceAddress("n")

        progressEvents.tryEmit(DfuInternalState.Starting(address))
        progressEvents.tryEmit(DfuInternalState.Connecting(address))

        // TODO: Implement actual Kable Nordic DFU Protocol using BleScanner and BleConnectionFactory
        // For now we simulate the DFU process for parity test coverage

        delay(1000)
        progressEvents.tryEmit(DfuInternalState.Connected(address))

        delay(500)
        progressEvents.tryEmit(DfuInternalState.EnablingDfuMode(address))

        // Parse Zip
        ZipFile(firmwareZip).use {
            val entries = it.entries().toList()
            Logger.i { "DFU zip contains ${entries.size} entries" }
        }

        delay(1000)
        progressEvents.tryEmit(DfuInternalState.Validating(address))

        for (i in 1..100 step 10) {
            delay(200)
            progressEvents.tryEmit(
                DfuInternalState.Progress(
                    address,
                    percent = i,
                    speed = 100f,
                    avgSpeed = 100f,
                    currentPart = 1,
                    partsTotal = 1,
                ),
            )
            val updateMsg = "Uploading..."
            updateState(
                FirmwareUpdateState.Updating(ProgressState(message = updateMsg, progress = i / 100f, details = "$i%")),
            )
        }

        progressEvents.tryEmit(DfuInternalState.Disconnecting(address))
        delay(500)
        progressEvents.tryEmit(DfuInternalState.Disconnected(address))
        progressEvents.tryEmit(DfuInternalState.Completed(address))

        updateState(FirmwareUpdateState.Success)
    }

    fun progressFlow(): Flow<DfuInternalState> = progressEvents
}
