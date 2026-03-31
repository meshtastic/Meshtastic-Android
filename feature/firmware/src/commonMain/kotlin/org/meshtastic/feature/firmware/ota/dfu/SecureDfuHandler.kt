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
package org.meshtastic.feature.firmware.ota.dfu

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_update_connecting_attempt
import org.meshtastic.core.resources.firmware_update_downloading_percent
import org.meshtastic.core.resources.firmware_update_enabling_dfu
import org.meshtastic.core.resources.firmware_update_not_found_in_release
import org.meshtastic.core.resources.firmware_update_ota_failed
import org.meshtastic.core.resources.firmware_update_starting_dfu
import org.meshtastic.core.resources.firmware_update_uploading
import org.meshtastic.core.resources.firmware_update_validating
import org.meshtastic.core.resources.firmware_update_waiting_reboot
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.feature.firmware.DfuInternalState
import org.meshtastic.feature.firmware.FirmwareArtifact
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateHandler
import org.meshtastic.feature.firmware.FirmwareUpdateState
import org.meshtastic.feature.firmware.ProgressState

private const val PERCENT_MAX = 100
private const val GATT_RELEASE_DELAY_MS = 1_500L
private const val DFU_REBOOT_WAIT_MS = 3_000L
private const val RETRY_DELAY_MS = 2_000L
private const val CONNECT_ATTEMPTS = 4

/**
 * KMP [FirmwareUpdateHandler] for nRF52 devices using the Nordic Secure DFU protocol over Kable BLE.
 *
 * All platform I/O (zip extraction, file reading) is delegated to [FirmwareFileHandler].
 */
@Single
class SecureDfuHandler(
    private val firmwareRetriever: FirmwareRetriever,
    private val firmwareFileHandler: FirmwareFileHandler,
    private val radioController: RadioController,
    private val bleScanner: BleScanner,
    private val bleConnectionFactory: BleConnectionFactory,
) : FirmwareUpdateHandler {

    private val _progressFlow = MutableSharedFlow<DfuInternalState>(extraBufferCapacity = 32)

    /**
     * Intermediate DFU states (connecting, enabling DFU mode, validating…). Terminal states (success / error) are
     * reported via the `updateState` callback.
     */
    val progressFlow: Flow<DfuInternalState> = _progressFlow.asSharedFlow()

    @Suppress("LongMethod")
    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        target: String, // Bluetooth MAC address
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri?,
    ): FirmwareArtifact? {
        var cleanupArtifact: FirmwareArtifact? = null
        return try {
            withContext(ioDispatcher) {
                // ── 1. Obtain the .zip file ──────────────────────────────────────
                cleanupArtifact = obtainZipFile(release, hardware, firmwareUri, updateState)
                val zipFile = cleanupArtifact ?: return@withContext null

                // ── 2. Extract .dat and .bin from zip ────────────────────────────
                updateState(
                    FirmwareUpdateState.Processing(
                        ProgressState(UiText.Resource(Res.string.firmware_update_starting_dfu)),
                    ),
                )
                val entries = firmwareFileHandler.extractZipEntries(zipFile)
                val pkg = parseDfuZipEntries(entries)

                // ── 3. Disconnect mesh service, trigger buttonless DFU ───────────
                emit(DfuInternalState.EnablingDfuMode(target))
                updateState(
                    FirmwareUpdateState.Processing(
                        ProgressState(UiText.Resource(Res.string.firmware_update_enabling_dfu)),
                    ),
                )
                radioController.setDeviceAddress("n")
                delay(GATT_RELEASE_DELAY_MS)

                var transport: SecureDfuTransport? = null
                var completed = false
                try {
                    transport = SecureDfuTransport(bleScanner, bleConnectionFactory, target)

                    transport.triggerButtonlessDfu().onFailure { e ->
                        Logger.w(e) { "DFU: Buttonless trigger failed ($e) — device may already be in DFU mode" }
                    }
                    delay(DFU_REBOOT_WAIT_MS)

                    // ── 4. Connect to device in DFU mode ─────────────────────────────
                    if (!connectWithRetry(transport, target, updateState)) return@withContext null

                    // ── 5. Init packet ────────────────────────────────────────────
                    updateState(
                        FirmwareUpdateState.Processing(
                            ProgressState(UiText.Resource(Res.string.firmware_update_starting_dfu)),
                        ),
                    )
                    transport.transferInitPacket(pkg.initPacket).getOrThrow()

                    // ── 6. Firmware ───────────────────────────────────────────────
                    val uploadMsg = UiText.Resource(Res.string.firmware_update_uploading)
                    updateState(FirmwareUpdateState.Updating(ProgressState(uploadMsg, 0f)))
                    emit(DfuInternalState.Progress(target, 0, 0f, 0f, 1, 1))

                    val firmwareSize = pkg.firmware.size
                    val throughputTracker = org.meshtastic.feature.firmware.ota.ThroughputTracker()

                    transport
                        .transferFirmware(pkg.firmware) { progress ->
                            val pct = (progress * PERCENT_MAX).toInt()
                            val bytesSent = (progress * firmwareSize).toLong()
                            throughputTracker.record(bytesSent)

                            val bytesPerSec = throughputTracker.bytesPerSecond()
                            val speedBytesPerMs = bytesPerSec.toFloat() / 1000f

                            updateState(FirmwareUpdateState.Updating(ProgressState(uploadMsg, progress, "$pct%")))
                            emit(DfuInternalState.Progress(target, pct, speedBytesPerMs, speedBytesPerMs, 1, 1))
                        }
                        .getOrThrow()

                    // ── 7. Validate ───────────────────────────────────────────────
                    emit(DfuInternalState.Validating(target))
                    updateState(
                        FirmwareUpdateState.Processing(
                            ProgressState(UiText.Resource(Res.string.firmware_update_validating)),
                        ),
                    )

                    completed = true
                    updateState(FirmwareUpdateState.Success)
                    zipFile
                } finally {
                    // Send ABORT if cancelled mid-transfer, then always clean up.
                    // NonCancellable ensures this runs even when the coroutine is being cancelled.
                    withContext(NonCancellable) {
                        if (!completed) transport?.abort()
                        transport?.close()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DfuException) {
            Logger.e(e) { "DFU: Protocol error" }
            updateState(
                FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_ota_failed, e.message ?: "")),
            )
            cleanupArtifact
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.e(e) { "DFU: Unexpected error" }
            updateState(
                FirmwareUpdateState.Error(UiText.Resource(Res.string.firmware_update_ota_failed, e.message ?: "")),
            )
            cleanupArtifact
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun connectWithRetry(
        transport: SecureDfuTransport,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
    ): Boolean {
        updateState(
            FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_waiting_reboot))),
        )
        for (attempt in 1..CONNECT_ATTEMPTS) {
            updateState(
                FirmwareUpdateState.Processing(
                    ProgressState(
                        UiText.Resource(Res.string.firmware_update_connecting_attempt, attempt, CONNECT_ATTEMPTS),
                    ),
                ),
            )
            emit(DfuInternalState.Connecting(address))
            val result = transport.connectToDfuMode()
            if (result.isSuccess) {
                emit(DfuInternalState.Connected(address))
                return true
            }
            Logger.w { "DFU: Connect attempt $attempt/$CONNECT_ATTEMPTS failed: ${result.exceptionOrNull()?.message}" }
            if (attempt < CONNECT_ATTEMPTS) delay(RETRY_DELAY_MS)
        }
        return false
    }

    private suspend fun obtainZipFile(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        firmwareUri: CommonUri?,
        updateState: (FirmwareUpdateState) -> Unit,
    ): FirmwareArtifact? {
        if (firmwareUri != null) {
            return FirmwareArtifact(uri = firmwareUri, fileName = firmwareUri.pathSegments.lastOrNull())
        }

        val downloadingMsg =
            getStringSuspend(Res.string.firmware_update_downloading_percent, 0)
                .replace(Regex(":?\\s*%1\\\$d%?"), "")
                .trim()

        updateState(
            FirmwareUpdateState.Downloading(
                ProgressState(message = UiText.DynamicString(downloadingMsg), progress = 0f),
            ),
        )

        val path =
            firmwareRetriever.retrieveOtaFirmware(release, hardware) { progress ->
                val pct = (progress * PERCENT_MAX).toInt()
                updateState(
                    FirmwareUpdateState.Downloading(
                        ProgressState(UiText.DynamicString(downloadingMsg), progress, "$pct%"),
                    ),
                )
            }

        if (path == null) {
            updateState(
                FirmwareUpdateState.Error(
                    UiText.Resource(Res.string.firmware_update_not_found_in_release, hardware.displayName),
                ),
            )
        }
        return path
    }

    private fun emit(state: DfuInternalState) {
        _progressFlow.tryEmit(state)
    }
}
