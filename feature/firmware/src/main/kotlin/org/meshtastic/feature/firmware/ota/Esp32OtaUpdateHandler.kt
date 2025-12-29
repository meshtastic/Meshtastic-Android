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
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateState
import java.io.File
import javax.inject.Inject

/** Handler for ESP32 firmware updates using the Unified OTA protocol. Supports both BLE and WiFi/TCP transports. */
class Esp32OtaUpdateHandler
@Inject
constructor(
    private val firmwareRetriever: FirmwareRetriever,
    private val serviceRepository: ServiceRepository,
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

            // Step 3: Connect to device via BLE
            updateState(FirmwareUpdateState.Processing("Connecting to device..."))

            // Trigger OTA mode first with the firmware hash
            val sha256Bytes = FirmwareHashUtil.calculateSha256Bytes(firmwareFile)
            val sha256Hash = FirmwareHashUtil.calculateSha256(firmwareFile)
            Logger.i { "ESP32 OTA: Firmware hash: $sha256Hash" }
            triggerRebootOta(1, sha256Bytes) // 1 = BLE

            val bluetoothDevice =
                getBluetoothDevice(address)
                    ?: throw OtaProtocolException.ConnectionFailed("Invalid Bluetooth address: $address")

            val transport = BleOtaTransport(context, bluetoothDevice)

            // Give the device time to reboot if it's currently in normal mode
            var connected = false
            for (i in 1..5) {
                try {
                    updateState(FirmwareUpdateState.Processing("Connecting to device (attempt $i/5)..."))
                    transport.connect().getOrThrow()
                    connected = true
                    break
                } catch (e: Exception) {
                    if (i == 5) throw e
                    kotlinx.coroutines.delay(2000)
                }
            }

            try {
                // Step 4: Send VERSION command
                updateState(FirmwareUpdateState.Processing("Checking device version..."))
                val versionInfo = transport.sendVersion().getOrThrow()
                Logger.i {
                    "ESP32 OTA: Device version - HW: ${versionInfo.hwVersion}, FW: ${versionInfo.fwVersion}"
                }

                // Step 5: Start OTA update
                updateState(FirmwareUpdateState.Processing("Starting OTA update..."))
                transport.startOta(firmwareFile.length(), sha256Hash).getOrThrow()

                // Step 6: Stream firmware
                updateState(FirmwareUpdateState.Updating(0f, "Uploading firmware..."))
                val firmwareData = firmwareFile.readBytes()
                transport
                    .streamFirmware(
                        data = firmwareData,
                        chunkSize = BleOtaTransport.RECOMMENDED_CHUNK_SIZE,
                        onProgress = { progress ->
                            val percent = (progress * 100).toInt()
                            updateState(FirmwareUpdateState.Updating(progress, "Uploading firmware... $percent%"))
                        },
                    )
                    .getOrThrow()

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

            // Step 3: Connect to device via WiFi/TCP
            updateState(FirmwareUpdateState.Processing("Connecting to device via WiFi..."))

            // Trigger OTA mode first with the firmware hash
            val sha256Bytes = FirmwareHashUtil.calculateSha256Bytes(firmwareFile)
            val sha256Hash = FirmwareHashUtil.calculateSha256(firmwareFile)
            Logger.i { "ESP32 OTA: Firmware hash: $sha256Hash" }
            triggerRebootOta(2, sha256Bytes) // 2 = WiFi

            val transport = WifiOtaTransport(deviceIp)

            // Give the device time to reboot and start the OTA listener on port 3232
            var connected = false
            for (i in 1..10) {
                try {
                    updateState(FirmwareUpdateState.Processing("Connecting to device via WiFi (attempt $i/10)..."))
                    transport.connect().getOrThrow()
                    connected = true
                    break
                } catch (e: Exception) {
                    Logger.w { "ESP32 OTA: Connection attempt $i failed: ${e.message}" }
                    if (i == 10) throw e
                    kotlinx.coroutines.delay(2000) // 2 seconds between retries
                }
            }

            try {
                // Step 4: Send VERSION command
                updateState(FirmwareUpdateState.Processing("Checking device version..."))
                val versionInfo = transport.sendVersion().getOrThrow()
                Logger.i {
                    "ESP32 OTA: Device version - HW: ${versionInfo.hwVersion}, FW: ${versionInfo.fwVersion}"
                }

                // Step 5: Start OTA update
                updateState(FirmwareUpdateState.Processing("Starting OTA update..."))
                transport.startOta(firmwareFile.length(), sha256Hash).getOrThrow()

                // Step 6: Stream firmware
                updateState(FirmwareUpdateState.Updating(0f, "Uploading firmware..."))
                val firmwareData = firmwareFile.readBytes()
                transport
                    .streamFirmware(
                        data = firmwareData,
                        chunkSize = WifiOtaTransport.RECOMMENDED_CHUNK_SIZE,
                        onProgress = { progress ->
                            val percent = (progress * 100).toInt()
                            updateState(FirmwareUpdateState.Updating(progress, "Uploading firmware... $percent%"))
                        },
                    )
                    .getOrThrow()

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

    /** Download firmware binary for ESP32 devices. ESP32 uses .bin files instead of .zip files. */
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

    /** Get firmware file from a content URI. */
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

    /** Get BluetoothDevice from MAC address. */
    private fun getBluetoothDevice(address: String): BluetoothDevice? = try {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        adapter?.getRemoteDevice(address)
    } catch (e: Exception) {
        Logger.e(e) { "ESP32 OTA: Failed to get Bluetooth device" }
        null
    }

    /**
     * Trigger the device to reboot into OTA mode.
     *
     * @param mode 1 for BLE, 2 for WiFi
     * @param hash firmware SHA256 hash
     */
    private fun triggerRebootOta(mode: Int, hash: ByteArray?) {
        val service = serviceRepository.meshService
        if (service == null) {
            Logger.w { "ESP32 OTA: MeshService not available, skipping OTA reboot trigger" }
            return
        }

        try {
            val myInfo = service.getMyNodeInfo()
            if (myInfo != null) {
                Logger.i { "ESP32 OTA: Triggering reboot OTA mode $mode with hash" }
                service.requestRebootOta(service.getPacketId(), myInfo.myNodeNum, mode, hash)
            }
        } catch (e: Exception) {
            Logger.e(e) { "ESP32 OTA: Failed to trigger reboot OTA" }
        }
    }
}
