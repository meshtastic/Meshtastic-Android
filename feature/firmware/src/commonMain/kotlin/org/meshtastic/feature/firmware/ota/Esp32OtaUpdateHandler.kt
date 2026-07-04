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
package org.meshtastic.feature.firmware.ota

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_update_connecting_attempt
import org.meshtastic.core.resources.firmware_update_downloading_percent
import org.meshtastic.core.resources.firmware_update_erasing
import org.meshtastic.core.resources.firmware_update_extracting
import org.meshtastic.core.resources.firmware_update_hash_rejected
import org.meshtastic.core.resources.firmware_update_not_found_in_release
import org.meshtastic.core.resources.firmware_update_ota_failed
import org.meshtastic.core.resources.firmware_update_ota_timeout
import org.meshtastic.core.resources.firmware_update_ota_unsupported_reason
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
private const val REBOOT_MODE_BLE = 1
private const val REBOOT_MODE_WIFI = 2

// Time to wait for the firmware to confirm OTA entry before tearing down the mesh transport.
// The firmware emits a ClientNotification ("Rebooting to <mode> OTA" on success, a "Cannot start OTA: ..." /
// "OTA Loader does not support ..." warning on rejection) before its 1 s scheduled reboot — so this only needs to
// cover BLE/TCP round-trip + device processing, not the reboot itself.
private const val OTA_PREFLIGHT_TIMEOUT_MS = 5000L

// Time to wait for BLE GATT to fully release after disconnecting mesh service
private const val GATT_RELEASE_DELAY_MS = 1000L

// Firmware emits one of these human-readable messages via meshtastic.ClientNotification before rebooting.
// "Rebooting to BLE OTA" / "Rebooting to WiFi OTA" → success. Any other OTA-containing message → rejection
// (e.g. "OTA Loader does not support BLE", "Cannot start OTA: Cannot find OTA Loader partition.",
// "Unable to switch to the OTA partition.").
private const val OTA_CONFIRM_PREFIX = "Rebooting to"
private const val OTA_KEYWORD = "OTA"

// A BLE target is a 6-octet MAC (e.g. AA:BB:CC:DD:EE:FF). Matching the exact MAC shape — not merely "contains a colon"
// — keeps an IPv6 WiFi target (which also has colons) from being misrouted to the BLE path.
private val MAC_ADDRESS_REGEX = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

internal fun isBleMacAddress(target: String): Boolean = MAC_ADDRESS_REGEX.matches(target)

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
    private val serviceRepository: ServiceRepository,
    private val bleScanner: BleScanner,
    private val bleConnectionFactory: BleConnectionFactory,
    private val dispatchers: CoroutineDispatchers,
) : FirmwareUpdateHandler {

    internal var otaPreflightTimeoutMs: Long = OTA_PREFLIGHT_TIMEOUT_MS

    /** Entry point for FirmwareUpdateHandler interface. Routes to BLE (target is a MAC) or WiFi (anything else). */
    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        target: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri?,
    ): FirmwareArtifact? = if (isBleMacAddress(target)) {
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
        rebootMode = REBOOT_MODE_BLE,
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
        rebootMode = REBOOT_MODE_WIFI,
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

                // Clear any stale notification from a previous attempt so the firmware's response is always
                // treated as a fresh emission (StateFlow conflates identical values — a retry would otherwise
                // time out waiting for a value that the flow already holds).
                serviceRepository.clearClientNotification()

                // Snapshot the current notification message BEFORE sending the OTA trigger. After the clear
                // above this is null today, but capturing it remains defense-in-depth: if the clear is ever
                // removed or the value is repopulated between the clear and the trigger, the predicate must
                // still distinguish the response from a pre-existing value.
                val baselineMessage = serviceRepository.clientNotification.value?.message
                triggerRebootOta(rebootMode, sha256Bytes)

                // Step 3: Preflight — wait for the firmware to confirm OTA entry BEFORE tearing down the mesh
                // transport. The mesh ACK only confirms packet delivery; it does NOT mean the device's OTA loader
                // supports the requested transport. The firmware surfaces loader-capability failures as a
                // meshtastic.ClientNotification warning ("OTA Loader does not support <mode>", etc.) emitted before
                // its scheduled reboot, so we race that signal against a bounded timeout. On rejection or timeout we
                // fail fast and leave the mesh connection intact — no disconnect, no OTA-transport retry loop.
                if (runOtaPreflight(rebootMode, baselineMessage, updateState) !is OtaPreflightResult.Confirmed) {
                    return@withContext cleanupArtifact
                }

                val transport = transportFactory()
                connectToDevice(transport, connectionAttempts, updateState)

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
        radioController.requestRebootOta(radioController.generatePacketId(), myNodeNum, mode, hash)
    }

    /**
     * Disconnect the mesh service BLE connection to free up the GATT for OTA. Setting device address to "n" (NOP
     * interface) cleanly disconnects without reconnection attempts.
     */
    private suspend fun disconnectMeshService() {
        Logger.i { "ESP32 OTA: Disconnecting mesh service for OTA" }
        radioController.setDeviceAddress("n")
    }

    private suspend fun obtainFirmwareFile(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        firmwareUri: CommonUri?,
        updateState: (FirmwareUpdateState) -> Unit,
    ): FirmwareArtifact? {
        // safeCatchingAll swallows Skiko ExceptionInInitializerError on headless JVM tests where
        // compose-resources can't load native libs. Production resolves the localized string normally;
        // tests fall back to empty and the Downloading state still emits with a blank message.
        val downloadingMsg =
            safeCatchingAll { getStringSuspend(Res.string.firmware_update_downloading_percent, 0) }
                .onFailure { e -> Logger.w(e) { "ESP32 OTA: Failed to resolve downloading message string" } }
                .getOrDefault("")
                .stripFormatArgs()

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

    @Suppress("ThrowsCount") // CancellationException rethrow + two post-confirm failure paths
    private suspend fun connectToDevice(
        transport: UnifiedOtaProtocol,
        attempts: Int,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        // Show "waiting for reboot" state before first connection attempt
        updateState(
            FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_waiting_reboot))),
        )

        retryWithDelay(
            attempts = attempts,
            retryDelayMillis = RETRY_DELAY,
            onAttempt = { i ->
                updateState(
                    FirmwareUpdateState.Processing(
                        ProgressState(UiText.Resource(Res.string.firmware_update_connecting_attempt, i, attempts)),
                    ),
                )
            },
        ) {
            transport.connect()
        }
            .getOrThrow()
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
            if (rebootMode == REBOOT_MODE_BLE) {
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
                    val details =
                        formatTransferProgress(progress, firmwareData.size, throughputTracker.bytesPerSecond())
                    updateState(
                        FirmwareUpdateState.Updating(
                            ProgressState(message = uploadingMsg, progress = progress, details = details),
                        ),
                    )
                },
            )
            .getOrThrow()
        Logger.i { "ESP32 OTA: Firmware stream completed" }

        updateState(FirmwareUpdateState.Success())
    }

    /**
     * Outcome of waiting for the device to confirm OTA entry after [triggerRebootOta]. The firmware emits a single
     * [org.meshtastic.proto.ClientNotification] before its scheduled reboot covering all cases: success ("Rebooting to
     * <BLE|WiFi> OTA") or rejection (any other OTA-containing message — partition missing, loader incompatible, switch
     * failed). See firmware `AdminModule.cpp` `ota_request_tag` handler.
     */
    private sealed interface OtaPreflightResult {
        /** Firmware confirmed it is rebooting into the OTA loader. */
        data object Confirmed : OtaPreflightResult

        /** Firmware rejected the OTA request; [message] is the verbatim rejection reason. */
        data class Rejected(val message: String) : OtaPreflightResult

        /** No ClientNotification arrived within the bound — firmware may be silent or the link dropped. */
        data object Timeout : OtaPreflightResult
    }

    /**
     * Run the OTA preflight gate. On [OtaPreflightResult.Confirmed] this releases the mesh transport (with the GATT
     * release delay) and returns Confirmed. On rejection/timeout it emits the appropriate [FirmwareUpdateState.Error]
     * and returns the failure result so the caller can abort while preserving the cleanup artifact (and the mesh
     * transport, which is NOT touched on the failure paths).
     */
    private suspend fun runOtaPreflight(
        rebootMode: Int,
        baselineMessage: String?,
        updateState: (FirmwareUpdateState) -> Unit,
    ): OtaPreflightResult =
        when (val preflight = awaitOtaConfirmation(otaPreflightTimeoutMs, rebootMode, baselineMessage)) {
            is OtaPreflightResult.Confirmed -> {
                Logger.i { "ESP32 OTA: Preflight confirmed; releasing mesh transport for OTA" }
                disconnectMeshService()
                // Give BLE stack time to fully release the GATT connection
                delay(GATT_RELEASE_DELAY_MS)
                preflight
            }

            is OtaPreflightResult.Rejected -> {
                Logger.w { "ESP32 OTA: Firmware rejected OTA entry (${preflight.message}); mesh transport preserved" }
                updateState(
                    FirmwareUpdateState.Error(
                        UiText.Resource(Res.string.firmware_update_ota_unsupported_reason, preflight.message),
                    ),
                )
                preflight
            }

            is OtaPreflightResult.Timeout -> {
                Logger.w {
                    "ESP32 OTA: No firmware confirmation within ${otaPreflightTimeoutMs}ms; mesh transport preserved"
                }
                updateState(FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_ota_timeout)))
                preflight
            }
        }

    /**
     * Race the firmware's OTA-entry ClientNotification against [timeoutMs]. The [baselineMessage] is captured by the
     * caller BEFORE the OTA trigger is sent, so a stale notification already held in the StateFlow doesn't get mistaken
     * for the response.
     */
    private suspend fun awaitOtaConfirmation(
        timeoutMs: Long,
        rebootMode: Int,
        baselineMessage: String?,
    ): OtaPreflightResult {
        val modeName = otaModeName(rebootMode)
        val matched =
            withTimeoutOrNull(timeoutMs) {
                serviceRepository.clientNotification.first { cn ->
                    val msg = cn?.message
                    msg != null && msg != baselineMessage && msg.contains(OTA_KEYWORD)
                }
            }
        return when {
            matched == null -> OtaPreflightResult.Timeout

            // Defense-in-depth: require both the canonical "Rebooting to" prefix AND the requested mode name in the
            // message. Firmware is consistent today, but this guards against a future firmware bug that acks the wrong
            // mode — a wrong-mode confirmation would route the host to a transport the device did not enter.
            matched.message.startsWith(OTA_CONFIRM_PREFIX) && matched.message.contains(modeName) -> {
                Logger.i { "ESP32 OTA: Firmware confirmed $modeName OTA entry: ${matched.message}" }
                OtaPreflightResult.Confirmed
            }

            else -> OtaPreflightResult.Rejected(matched.message)
        }
    }

    private fun otaModeName(rebootMode: Int): String = when (rebootMode) {
        REBOOT_MODE_BLE -> "BLE"
        REBOOT_MODE_WIFI -> "WiFi"
        else -> rebootMode.toString()
    }
}
