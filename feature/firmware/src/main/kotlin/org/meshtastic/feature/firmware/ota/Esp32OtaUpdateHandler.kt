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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareUpdateState
import java.io.File
import javax.inject.Inject

/**
 * Handler for ESP32 firmware updates using the Unified OTA protocol.
 * Supports both BLE and WiFi/TCP transports.
 */
class Esp32OtaUpdateHandler
@Inject
constructor(
    private val fileHandler: FirmwareFileHandler,
    @ApplicationContext private val context: Context,
) {

    /**
     * Start ESP32 OTA update via BLE.
     * 
     * @param release Firmware release to install
     * @param hardware Device hardware information
     * @param address Bluetooth device address
     * @param updateState Callback to update UI state
     * @param firmwareUri Optional pre-downloaded firmware URI
     * @return Downloaded firmware file, or null if using provided URI
     */
    suspend fun startBleUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri? = null,
    ): File? = try {
        withContext(Dispatchers.IO) {
            // Step 1: Get firmware file
            val firmwareFile = if (firmwareUri != null) {
                updateState(FirmwareUpdateState.Processing("Loading firmware..."))
                getFirmwareFromUri(firmwareUri)
            } else {
                downloadFirmware(release, hardware, updateState)
            }

            if (firmwareFile == null) {
                updateState(FirmwareUpdateState.Error("Failed to obtain firmware file"))
                return@withContext null
            }

            // Step 2: Calculate SHA-256 hash
            updateState(FirmwareUpdateState.Processing("Calculating firmware hash..."))
            val sha256Hash = FirmwareHashUtil.calculateSha256(firmwareFile)
            Logger.i { "ESP32 OTA: Firmware hash: $sha256Hash" }

            // Step 3: Connect to device via BLE
            updateState(FirmwareUpdateState.Processing("Connecting to device..."))
            val bluetoothDevice = getBluetoothDevice(address)
                ?: throw OtaProtocolException.ConnectionFailed("Invalid Bluetooth address: $address")

            val transport = BleOtaTransport(context, bluetoothDevice)
            transport.connect().getOrThrow()

            try {
                // Step 4: Send VERSION command
                updateState(FirmwareUpdateState.Processing("Checking device version..."))
                val versionInfo = transport.sendVersion().getOrThrow()
                Logger.i { "ESP32 OTA: Device version - HW: ${versionInfo.hwVersion}, FW: ${versionInfo.fwVersion}" }

                // Step 5: Start OTA update
                updateState(FirmwareUpdateState.Processing("Starting OTA update..."))
                transport.startOta(firmwareFile.length(), sha256Hash).getOrThrow()

                // Step 6: Stream firmware
                updateState(FirmwareUpdateState.Updating(0f, "Uploading firmware..."))
                val firmwareData = firmwareFile.readBytes()
                transport.streamFirmware(
                    data = firmwareData,
                    chunkSize = BleOtaTransport.RECOMMENDED_CHUNK_SIZE,
                    onProgress = { progress ->
                        val percent = (progress * 100).toInt()
                        updateState(FirmwareUpdateState.Updating(progress, "Uploading firmware... $percent%"))
                    }
                ).getOrThrow()

                // Step 7: Reboot device
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
                "Firmware hash rejected. Device may require hash provisioning or bootloader update."
            )
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

    /**
     * Start ESP32 OTA update via WiFi/TCP.
     * 
     * @param release Firmware release to install
     * @param hardware Device hardware information
     * @param deviceIp Device IP address on local network
     * @param updateState Callback to update UI state
     * @param firmwareUri Optional pre-downloaded firmware URI
     * @return Downloaded firmware file, or null if using provided URI
     */
    suspend fun startWifiUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        deviceIp: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri? = null,
    ): File? = try {
        withContext(Dispatchers.IO) {
            // Step 1: Get firmware file
            val firmwareFile = if (firmwareUri != null) {
                updateState(FirmwareUpdateState.Processing("Loading firmware..."))
                getFirmwareFromUri(firmwareUri)
            } else {
                downloadFirmware(release, hardware, updateState)
            }

            if (firmwareFile == null) {
                updateState(FirmwareUpdateState.Error("Failed to obtain firmware file"))
                return@withContext null
            }

            // Step 2: Calculate SHA-256 hash
            updateState(FirmwareUpdateState.Processing("Calculating firmware hash..."))
            val sha256Hash = FirmwareHashUtil.calculateSha256(firmwareFile)
            Logger.i { "ESP32 OTA: Firmware hash: $sha256Hash" }

            // Step 3: Connect to device via WiFi/TCP
            updateState(FirmwareUpdateState.Processing("Connecting to device via WiFi..."))
            val transport = WifiOtaTransport(deviceIp)
            transport.connect().getOrThrow()

            try {
                // Step 4: Send VERSION command
                updateState(FirmwareUpdateState.Processing("Checking device version..."))
                val versionInfo = transport.sendVersion().getOrThrow()
                Logger.i { "ESP32 OTA: Device version - HW: ${versionInfo.hwVersion}, FW: ${versionInfo.fwVersion}" }

                // Step 5: Start OTA update
                updateState(FirmwareUpdateState.Processing("Starting OTA update..."))
                transport.startOta(firmwareFile.length(), sha256Hash).getOrThrow()

                // Step 6: Stream firmware
                updateState(FirmwareUpdateState.Updating(0f, "Uploading firmware..."))
                val firmwareData = firmwareFile.readBytes()
                transport.streamFirmware(
                    data = firmwareData,
                    chunkSize = WifiOtaTransport.RECOMMENDED_CHUNK_SIZE,
                    onProgress = { progress ->
                        val percent = (progress * 100).toInt()
                        updateState(FirmwareUpdateState.Updating(progress, "Uploading firmware... $percent%"))
                    }
                ).getOrThrow()

                // Step 7: Reboot device
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
                "Firmware hash rejected. Device may require hash provisioning or bootloader update."
            )
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

    /**
     * Download firmware binary for ESP32 devices.
     * ESP32 uses .bin files instead of .zip files.
     */
    private suspend fun downloadFirmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        updateState: (FirmwareUpdateState) -> Unit,
    ): File? {
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
        if (target.isEmpty()) {
            Logger.e { "ESP32 OTA: No platformio target or hwModelSlug" }
            return null
        }

        // Construct firmware URL
        // Format: https://github.com/meshtastic/firmware/releases/download/v2.x.x/firmware-<target>-<version>-update.bin
        val version = release.id.removePrefix("v")
        val fileName = "firmware-$target-$version-update.bin"
        val baseUrl = "https://github.com/meshtastic/firmware/releases/download/${release.id}"
        val firmwareUrl = "$baseUrl/$fileName"

        Logger.i { "ESP32 OTA: Downloading from $firmwareUrl" }

        // Check if URL exists
        if (!fileHandler.checkUrlExists(firmwareUrl)) {
            Logger.w { "ESP32 OTA: Firmware not found at $firmwareUrl" }
            return null
        }

        // Download the file
        return fileHandler.downloadFile(
            url = firmwareUrl,
            fileName = fileName,
            onProgress = { progress ->
                updateState(FirmwareUpdateState.Downloading(progress))
            }
        )
    }

    /**
     * Get firmware file from a content URI.
     */
    private suspend fun getFirmwareFromUri(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val tempFile = File(context.cacheDir, "firmware_update/ota_firmware.bin")
            tempFile.parentFile?.mkdirs()
            
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Logger.e(e) { "ESP32 OTA: Failed to read firmware from URI" }
            null
        }
    }

    /**
     * Get BluetoothDevice from MAC address.
     */
    private fun getBluetoothDevice(address: String): BluetoothDevice? {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.getRemoteDevice(address)
        } catch (e: Exception) {
            Logger.e(e) { "ESP32 OTA: Failed to get Bluetooth device" }
            null
        }
    }
}
