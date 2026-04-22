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
import org.meshtastic.feature.firmware.FirmwareArtifact
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareRetriever
import org.meshtastic.feature.firmware.FirmwareUpdateHandler
import org.meshtastic.feature.firmware.FirmwareUpdateState
import org.meshtastic.feature.firmware.ProgressState
import org.meshtastic.feature.firmware.ota.ThroughputTracker
import org.meshtastic.feature.firmware.ota.calculateMacPlusOne
import org.meshtastic.feature.firmware.ota.scanForBleDevice
import org.meshtastic.feature.firmware.stripFormatArgs
import kotlin.time.Duration.Companion.seconds

private const val PERCENT_MAX = 100
private const val GATT_RELEASE_DELAY_MS = 1_500L
private const val DFU_REBOOT_WAIT_MS = 3_000L
private const val RETRY_DELAY_MS = 2_000L
private const val CONNECT_ATTEMPTS = 4
private const val KIB_DIVISOR = 1024f

/**
 * KMP [FirmwareUpdateHandler] for nRF52 devices.
 *
 * Despite its historical name, this handler now drives **both** Nordic Secure DFU (service `FE59`) and Nordic Legacy
 * DFU / Adafruit `BLEDfu` (service `1530`). After triggering the buttonless reboot it sniffs which DFU service the
 * bootloader exposes and dispatches to the matching [DfuUploadTransport] implementation.
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
    private val dispatchers: CoroutineDispatchers,
) : FirmwareUpdateHandler {

    @Suppress("LongMethod")
    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        target: String,
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
                updateState(
                    FirmwareUpdateState.Processing(
                        ProgressState(UiText.Resource(Res.string.firmware_update_enabling_dfu)),
                    ),
                )
                radioController.setDeviceAddress("n")
                delay(GATT_RELEASE_DELAY_MS)

                // The trigger always uses SecureDfuTransport — it speaks both Secure (FE59) and Legacy (1530)
                // buttonless triggers and falls back automatically (commit f26f610c0).
                val triggerTransport = SecureDfuTransport(bleScanner, bleConnectionFactory, target, dispatchers.default)
                try {
                    triggerTransport.triggerButtonlessDfu().onFailure { e ->
                        Logger.w(e) { "DFU: Buttonless trigger failed ($e) — device may already be in DFU mode" }
                    }
                } finally {
                    withContext(NonCancellable) { triggerTransport.close() }
                }
                delay(DFU_REBOOT_WAIT_MS)

                // ── 4. Service detection: which DFU protocol does the bootloader speak? ─
                val protocol = detectBootloaderProtocol(target, updateState)
                Logger.i { "DFU: Bootloader protocol detected: $protocol" }
                val transport: DfuUploadTransport =
                    when (protocol) {
                        DfuProtocolKind.LEGACY ->
                            LegacyDfuTransport(bleScanner, bleConnectionFactory, target, dispatchers.default)
                        DfuProtocolKind.SECURE ->
                            SecureDfuTransport(bleScanner, bleConnectionFactory, target, dispatchers.default)
                    }

                var completed = false
                try {
                    // ── 5. Connect to device in DFU mode ─────────────────────────────
                    if (!connectWithRetry(transport, updateState)) return@withContext null

                    // ── 6. Init packet ────────────────────────────────────────────
                    updateState(
                        FirmwareUpdateState.Processing(
                            ProgressState(UiText.Resource(Res.string.firmware_update_starting_dfu)),
                        ),
                    )
                    Logger.i {
                        "DFU: Sending init packet (${pkg.initPacket.size} bytes) and firmware " +
                            "(${pkg.firmware.size} bytes) via $protocol"
                    }
                    transport.transferInitPacket(pkg.initPacket).getOrThrow()

                    // ── 7. Firmware ───────────────────────────────────────────────
                    val uploadMsg = UiText.Resource(Res.string.firmware_update_uploading)
                    updateState(FirmwareUpdateState.Updating(ProgressState(uploadMsg, 0f)))

                    val firmwareSize = pkg.firmware.size
                    val throughputTracker = ThroughputTracker()

                    transport
                        .transferFirmware(pkg.firmware) { progress ->
                            val pct = (progress * PERCENT_MAX).toInt()
                            val bytesSent = (progress * firmwareSize).toLong()
                            throughputTracker.record(bytesSent)

                            val bytesPerSecond = throughputTracker.bytesPerSecond()
                            val speedKib = bytesPerSecond.toFloat() / KIB_DIVISOR

                            val details = buildString {
                                append("$pct%")
                                if (speedKib > 0f) {
                                    val remainingBytes = firmwareSize - bytesSent
                                    val etaSeconds = remainingBytes.toFloat() / bytesPerSecond
                                    append(
                                        " (${NumberFormatter.format(speedKib, 1)} " +
                                            "KiB/s, ETA: ${etaSeconds.toInt()}s)",
                                    )
                                }
                            }

                            updateState(FirmwareUpdateState.Updating(ProgressState(uploadMsg, progress, details)))
                        }
                        .getOrThrow()

                    // ── 8. Validate ───────────────────────────────────────────────
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
                        if (!completed) transport.abort()
                        transport.close()
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

    /**
     * Detect which DFU protocol the bootloader speaks by scanning for advertised service UUIDs. We scan for the legacy
     * service (1530) first with a short timeout — Adafruit/oltaco bootloaders always advertise it, while Nordic Secure
     * bootloaders never do, so a hit unambiguously means Legacy. Miss ⇒ assume Secure (preserves current behavior on
     * unaffected devices).
     */
    private suspend fun detectBootloaderProtocol(
        target: String,
        updateState: (FirmwareUpdateState) -> Unit,
    ): DfuProtocolKind {
        updateState(
            FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_waiting_reboot))),
        )
        val targetAddresses = setOf(target, calculateMacPlusOne(target))
        val legacyHit =
            scanForBleDevice(
                scanner = bleScanner,
                tag = "DFU detect",
                serviceUuid = LegacyDfuUuids.SERVICE,
                retryCount = 1,
                retryDelay = 0.seconds,
                scanTimeout = DETECT_SCAN_TIMEOUT,
                predicate = { it.address in targetAddresses },
            )
        return if (legacyHit != null) DfuProtocolKind.LEGACY else DfuProtocolKind.SECURE
    }

    private suspend fun connectWithRetry(
        transport: DfuUploadTransport,
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
            val result = transport.connectToDfuMode()
            if (result.isSuccess) {
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

        val downloadingMsg = getStringSuspend(Res.string.firmware_update_downloading_percent, 0).stripFormatArgs()

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

    /** Result of [detectBootloaderProtocol]. */
    internal enum class DfuProtocolKind {
        SECURE,
        LEGACY,
    }

    private companion object {
        /** Detection scan timeout — short because we only want to confirm/refute an advertised legacy service. */
        private val DETECT_SCAN_TIMEOUT = 8.seconds
    }
}
