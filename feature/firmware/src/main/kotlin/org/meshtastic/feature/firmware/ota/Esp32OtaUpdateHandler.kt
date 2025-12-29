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

package org.meshtastic.feature.firmware.ota

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateHandler
import org.meshtastic.feature.firmware.FirmwareUpdateState
import java.io.File
import javax.inject.Inject

/**
 * Handler for ESP32 firmware updates using the Unified OTA protocol. Supports both BLE and WiFi/TCP transports via
 * UnifiedOtaProtocol.
 */
class Esp32OtaUpdateHandler
@Inject
constructor(
    private val firmwareRetriever: FirmwareRetriever,
    private val serviceRepository: ServiceRepository,
    private val centralManager: CentralManager,
    @ApplicationContext private val context: Context,
) : FirmwareUpdateHandler {

    /** Entry point for FirmwareUpdateHandler interface. Decides between BLE and WiFi based on target format. */
    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        target: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri?,
    ): File? = if (target.contains(":")) {
        startBleUpdate(release, hardware, target, updateState, firmwareUri)
    } else {
        startWifiUpdate(release, hardware, target, updateState, firmwareUri)
    }

    suspend fun startBleUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri? = null,
    ): File? = performUpdate(
        release = release,
        hardware = hardware,
        updateState = updateState,
        firmwareUri = firmwareUri,
        transportFactory = { BleOtaTransport(centralManager, address) },
        rebootMode = 1,
        connectionAttempts = 5,
    )

    suspend fun startWifiUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        deviceIp: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri? = null,
    ): File? = performUpdate(
        release = release,
        hardware = hardware,
        updateState = updateState,
        firmwareUri = firmwareUri,
        transportFactory = { WifiOtaTransport(deviceIp) },
        rebootMode = 2,
        connectionAttempts = 10,
    )

    private suspend fun performUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri?,
        transportFactory: () -> UnifiedOtaProtocol,
        rebootMode: Int,
        connectionAttempts: Int,
    ): File? = try {
        withContext(Dispatchers.IO) {
            // Step 1: Get firmware file
            val firmwareFile =
                if (firmwareUri != null) {
                    updateState(FirmwareUpdateState.Processing("Loading firmware..."))
                    getFirmwareFromUri(firmwareUri)
                } else {
                    downloadFirmware(release, hardware, updateState)
                }

            if (firmwareFile == null) {
                updateState(FirmwareUpdateState.Error("Failed to obtain firmware file"))
                return@withContext null
            }

            // Step 2: Calculate Hash and Trigger Reboot
            val sha256Bytes = FirmwareHashUtil.calculateSha256Bytes(firmwareFile)
            val sha256Hash = FirmwareHashUtil.calculateSha256(firmwareFile)
            Logger.i { "ESP32 OTA: Firmware hash: $sha256Hash" }
            triggerRebootOta(rebootMode, sha256Bytes)

            val transport = transportFactory()

            // Step 3: Connect
            var connected = false
            for (i in 1..connectionAttempts) {
                try {
                    updateState(
                        FirmwareUpdateState.Processing("Connecting to device (attempt $i/$connectionAttempts)..."),
                    )
                    transport.connect().getOrThrow()
                    connected = true
                    break
                } catch (e: Exception) {
                    if (i == connectionAttempts) throw e
                    delay(2000)
                }
            }

            try {
                // Step 4: Version Check
                updateState(FirmwareUpdateState.Processing("Checking device version..."))
                val versionInfo = transport.sendVersion().getOrThrow()
                Logger.i {
                    "ESP32 OTA: Device version - HW: ${versionInfo.hwVersion}, FW: ${versionInfo.fwVersion}"
                }

                // Step 5: Start OTA
                updateState(FirmwareUpdateState.Processing("Starting OTA update..."))
                transport
                    .startOta(firmwareFile.length(), sha256Hash) { status ->
                        updateState(FirmwareUpdateState.Processing(status))
                    }
                    .getOrThrow()

                // Step 6: Stream
                updateState(FirmwareUpdateState.Updating(0f, "Uploading firmware..."))
                val firmwareData = firmwareFile.readBytes()
                val chunkSize =
                    if (rebootMode == 1) {
                        BleOtaTransport.RECOMMENDED_CHUNK_SIZE
                    } else {
                        WifiOtaTransport.RECOMMENDED_CHUNK_SIZE
                    }

                val startTime = System.currentTimeMillis()
                transport
                    .streamFirmware(
                        data = firmwareData,
                        chunkSize = chunkSize,
                        onProgress = { progress ->
                            val currentTime = System.currentTimeMillis()
                            val elapsedSeconds = (currentTime - startTime) / 1000f
                            val percent = (progress * 100).toInt()

                            val speedText =
                                if (elapsedSeconds > 0) {
                                    val bytesSent = (progress * firmwareData.size).toLong()
                                    val kibPerSecond = (bytesSent / 1024f) / elapsedSeconds
                                    val remainingBytes = firmwareData.size - bytesSent
                                    val etaSeconds =
                                        if (kibPerSecond > 0) (remainingBytes / 1024f) / kibPerSecond else 0f

                                    String.format("%.1f KiB/s, ETA: %ds", kibPerSecond, etaSeconds.toInt())
                                } else {
                                    ""
                                }

                            updateState(
                                FirmwareUpdateState.Updating(
                                    progress,
                                    "Uploading firmware... $percent% ($speedText)",
                                ),
                            )
                        },
                    )
                    .getOrThrow()

                // Step 7: Final Reboot
                updateState(FirmwareUpdateState.Processing("Rebooting device..."))
                transport.reboot().getOrThrow()

                updateState(FirmwareUpdateState.Success)
                firmwareFile
            } finally {
                transport.close()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OtaProtocolException.HashRejected) {
        Logger.e(e) { "ESP32 OTA: Hash rejected by device" }
        updateState(
            FirmwareUpdateState.Error(
                "Firmware hash rejected. Device may require hash provisioning or bootloader update.",
            ),
        )
        null
    } catch (e: OtaProtocolException) {
        Logger.e(e) { "ESP32 OTA: Protocol error" }
        updateState(FirmwareUpdateState.Error("OTA update failed: ${e.message}"))
        null
    } catch (e: Exception) {
        Logger.e(e) { "ESP32 OTA: Unexpected error" }
        updateState(FirmwareUpdateState.Error("Update failed: ${e.message}"))
        null
    }

    private suspend fun downloadFirmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        updateState: (FirmwareUpdateState) -> Unit,
    ): File? {
        updateState(FirmwareUpdateState.Downloading(0f))
        return firmwareRetriever.retrieveEsp32Firmware(release, hardware) { progress ->
            updateState(FirmwareUpdateState.Downloading(progress))
        }
    }

    private suspend fun getFirmwareFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val tempFile = File(context.cacheDir, "firmware_update/ota_firmware.bin")
            tempFile.parentFile?.mkdirs()
            inputStream.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
            tempFile
        } catch (e: Exception) {
            Logger.e(e) { "ESP32 OTA: Failed to read firmware from URI" }
            null
        }
    }

    private fun triggerRebootOta(mode: Int, hash: ByteArray?) {
        val service = serviceRepository.meshService ?: return
        try {
            val myInfo = service.getMyNodeInfo() ?: return
            Logger.i { "ESP32 OTA: Triggering reboot OTA mode $mode with hash" }
            service.requestRebootOta(service.getPacketId(), myInfo.myNodeNum, mode, hash)
        } catch (e: Exception) {
            Logger.e(e) { "ESP32 OTA: Failed to trigger reboot OTA" }
        }
    }
}
