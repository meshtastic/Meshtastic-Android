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
package org.meshtastic.feature.firmware.ota

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_update_connecting_attempt
import org.meshtastic.core.resources.firmware_update_downloading_percent
import org.meshtastic.core.resources.firmware_update_erasing
import org.meshtastic.core.resources.firmware_update_extracting
import org.meshtastic.core.resources.firmware_update_hash_rejected
import org.meshtastic.core.resources.firmware_update_not_found_in_release
import org.meshtastic.core.resources.firmware_update_ota_failed
import org.meshtastic.core.resources.firmware_update_starting_ota
import org.meshtastic.core.resources.firmware_update_uploading
import org.meshtastic.core.resources.firmware_update_waiting_reboot
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.feature.firmware.FirmwareArtifact
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateHandler
import org.meshtastic.feature.firmware.FirmwareUpdateState
import org.meshtastic.feature.firmware.ProgressState
import org.meshtastic.feature.firmware.stripFormatArgs

private const val RETRY_DELAY = 2000L
private const val PERCENT_MAX = 100
private const val KIB_DIVISOR = 1024f

// Time to wait for OTA reboot packet to be sent before disconnecting mesh service
private const val PACKET_SEND_DELAY_MS = 2000L

// Time to wait for BLE GATT to fully release after disconnecting mesh service
private const val GATT_RELEASE_DELAY_MS = 1000L

/**
 * KMP handler for ESP32 firmware updates using the Unified OTA protocol. Supports both BLE and WiFi/TCP transports via
 * [UnifiedOtaProtocol].
 *
 * All platform I/O (file reading, content-resolver imports) is delegated to [FirmwareFileHandler].
 */
@Suppress("TooManyFunctions", "LongParameterList")
@Single
class Esp32OtaUpdateHandler(
    private val firmwareRetriever: FirmwareRetriever,
    private val firmwareFileHandler: FirmwareFileHandler,
    private val radioController: RadioController,
    private val nodeRepository: NodeRepository,
    private val bleScanner: BleScanner,
    private val bleConnectionFactory: BleConnectionFactory,
    private val dispatchers: CoroutineDispatchers,
) : FirmwareUpdateHandler {

    /** Entry point for FirmwareUpdateHandler interface. Routes to BLE (MAC with colons) or WiFi (IP without). */
    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        target: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri?,
    ): FirmwareArtifact? = if (target.contains(":")) {
        startBleUpdate(release, hardware, target, updateState, firmwareUri)
    } else {
        startWifiUpdate(release, hardware, target, updateState, firmwareUri)
    }

    private suspend fun startBleUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri? = null,
    ): FirmwareArtifact? = performUpdate(
        release = release,
        hardware = hardware,
        updateState = updateState,
        firmwareUri = firmwareUri,
        transportFactory = { BleOtaTransport(bleScanner, bleConnectionFactory, address, dispatchers.default) },
        rebootMode = 1,
        connectionAttempts = 5,
    )

    private suspend fun startWifiUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        deviceIp: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri? = null,
    ): FirmwareArtifact? = performUpdate(
        release = release,
        hardware = hardware,
        updateState = updateState,
        firmwareUri = firmwareUri,
        transportFactory = { WifiOtaTransport(deviceIp, WifiOtaTransport.DEFAULT_PORT) },
        rebootMode = 2,
        connectionAttempts = 10,
    )

    private suspend fun performUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri?,
        transportFactory: () -> UnifiedOtaProtocol,
        rebootMode: Int,
        connectionAttempts: Int,
    ): FirmwareArtifact? {
        var cleanupArtifact: FirmwareArtifact? = null
        return try {
            withContext(ioDispatcher) {
                // Step 1: Get firmware file
                cleanupArtifact = obtainFirmwareFile(release, hardware, firmwareUri, updateState)
                val firmwareFile = cleanupArtifact ?: return@withContext null

                // Step 2: Read firmware once and calculate hash
                val firmwareBytes = firmwareFileHandler.readBytes(firmwareFile)
                val sha256Bytes = FirmwareHashUtil.calculateSha256Bytes(firmwareBytes)
                val sha256Hash = FirmwareHashUtil.bytesToHex(sha256Bytes)
                Logger.i { "ESP32 OTA: Firmware hash: $sha256Hash (${firmwareBytes.size} bytes)" }
                triggerRebootOta(rebootMode, sha256Bytes)

                // Step 3: Wait for packet to be sent, then disconnect mesh service
                // The packet needs ~1-2 seconds to be written and acknowledged over BLE
                delay(PACKET_SEND_DELAY_MS)
                disconnectMeshService()
                // Give BLE stack time to fully release the GATT connection
                delay(GATT_RELEASE_DELAY_MS)

                val transport = transportFactory()
                if (!connectToDevice(transport, connectionAttempts, updateState)) return@withContext null

                try {
                    executeOtaSequence(transport, firmwareBytes, sha256Hash, rebootMode, updateState)
                    firmwareFile
                } finally {
                    transport.close()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OtaProtocolException.HashRejected) {
            Logger.e(e) { "ESP32 OTA: Hash rejected by device" }
            updateState(FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_hash_rejected)))
            cleanupArtifact
        } catch (e: OtaProtocolException) {
            Logger.e(e) { "ESP32 OTA: Protocol error" }
            updateState(
                FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_ota_failed, e.message ?: "")),
            )
            cleanupArtifact
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.e(e) { "ESP32 OTA: Unexpected error" }
            updateState(
                FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_ota_failed, e.message ?: "")),
            )
            cleanupArtifact
        }
    }

    private suspend fun triggerRebootOta(mode: Int, hash: ByteArray?) {
        val myInfo = nodeRepository.myNodeInfo.value ?: return
        val myNodeNum = myInfo.myNodeNum
        Logger.i { "ESP32 OTA: Triggering reboot OTA mode $mode with hash" }
        radioController.requestRebootOta(radioController.getPacketId(), myNodeNum, mode, hash)
    }

    /**
     * Disconnect the mesh service BLE connection to free up the GATT for OTA. Setting device address to "n" (NOP
     * interface) cleanly disconnects without reconnection attempts.
     */
    private fun disconnectMeshService() {
        Logger.i { "ESP32 OTA: Disconnecting mesh service for OTA" }
        radioController.setDeviceAddress("n")
    }

    private suspend fun obtainFirmwareFile(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        firmwareUri: CommonUri?,
        updateState: (FirmwareUpdateState) -> Unit,
    ): FirmwareArtifact? {
        val downloadingMsg = getStringSuspend(Res.string.firmware_update_downloading_percent, 0).stripFormatArgs()

        updateState(
            FirmwareUpdateState.Downloading(
                ProgressState(message = UiText.DynamicString(downloadingMsg), progress = 0f),
            ),
        )

        return if (firmwareUri != null) {
            updateState(
                FirmwareUpdateState.Processing(
                    ProgressState(message = UiText.Resource(Res.string.firmware_update_extracting)),
                ),
            )
            firmwareFileHandler.importFromUri(firmwareUri)
        } else {
            val firmwareFile =
                firmwareRetriever.retrieveEsp32Firmware(release, hardware) { progress ->
                    val percent = (progress * PERCENT_MAX).toInt()
                    updateState(
                        FirmwareUpdateState.Downloading(
                            ProgressState(
                                message = UiText.DynamicString(downloadingMsg),
                                progress = progress,
                                details = "$percent%",
                            ),
                        ),
                    )
                }

            if (firmwareFile == null) {
                updateState(
                    FirmwareUpdateState.Error(
                        UiText.Resource(Res.string.firmware_update_not_found_in_release, hardware.displayName),
                    ),
                )
                null
            } else {
                firmwareFile
            }
        }
    }

    private suspend fun connectToDevice(
        transport: UnifiedOtaProtocol,
        attempts: Int,
        updateState: (FirmwareUpdateState) -> Unit,
    ): Boolean {
        // Show "waiting for reboot" state before first connection attempt
        updateState(
            FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_waiting_reboot))),
        )

        for (i in 1..attempts) {
            try {
                updateState(
                    FirmwareUpdateState.Processing(
                        ProgressState(UiText.Resource(Res.string.firmware_update_connecting_attempt, i, attempts)),
                    ),
                )
                transport.connect().getOrThrow()
                return true
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                if (i == attempts) throw e
                delay(RETRY_DELAY)
            }
        }
        return false
    }

    @Suppress("LongMethod")
    private suspend fun executeOtaSequence(
        transport: UnifiedOtaProtocol,
        firmwareData: ByteArray,
        sha256Hash: String,
        rebootMode: Int,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        val fileSize = firmwareData.size.toLong()
        // Start OTA handshake
        updateState(
            FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_starting_ota))),
        )
        transport
            .startOta(sizeBytes = fileSize, sha256Hash = sha256Hash) { status ->
                when (status) {
                    OtaHandshakeStatus.Erasing -> {
                        updateState(
                            FirmwareUpdateState.Processing(
                                ProgressState(UiText.Resource(Res.string.firmware_update_erasing)),
                            ),
                        )
                    }
                }
            }
            .getOrThrow()

        // Stream firmware data
        val uploadingMsg = UiText.Resource(Res.string.firmware_update_uploading)
        updateState(FirmwareUpdateState.Updating(ProgressState(uploadingMsg, 0f)))
        val chunkSize =
            if (rebootMode == 1) {
                BleOtaTransport.RECOMMENDED_CHUNK_SIZE
            } else {
                WifiOtaTransport.RECOMMENDED_CHUNK_SIZE
            }

        val throughputTracker = ThroughputTracker()
        transport
            .streamFirmware(
                data = firmwareData,
                chunkSize = chunkSize,
                onProgress = { progress ->
                    val bytesSent = (progress * firmwareData.size).toLong()
                    throughputTracker.record(bytesSent)

                    val percent = (progress * PERCENT_MAX).toInt()
                    val bytesPerSecond = throughputTracker.bytesPerSecond()

                    val speedText =
                        if (bytesPerSecond > 0) {
                            val kibPerSecond = bytesPerSecond.toFloat() / KIB_DIVISOR
                            val remainingBytes = firmwareData.size - bytesSent
                            val etaSeconds = remainingBytes.toFloat() / bytesPerSecond

                            "${NumberFormatter.format(kibPerSecond, 1)} KiB/s, ETA: ${etaSeconds.toInt()}s"
                        } else {
                            ""
                        }

                    updateState(
                        FirmwareUpdateState.Updating(
                            ProgressState(
                                message = uploadingMsg,
                                progress = progress,
                                details = "$percent% ($speedText)",
                            ),
                        ),
                    )
                },
            )
            .getOrThrow()
        Logger.i { "ESP32 OTA: Firmware stream completed" }

        updateState(FirmwareUpdateState.Success)
    }
}
