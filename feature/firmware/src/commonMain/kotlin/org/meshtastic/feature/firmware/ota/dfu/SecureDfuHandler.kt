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
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_update_connecting_attempt
import org.meshtastic.core.resources.firmware_update_downloading_percent
import org.meshtastic.core.resources.firmware_update_enabling_dfu
import org.meshtastic.core.resources.firmware_update_not_found_in_release
import org.meshtastic.core.resources.firmware_update_ota_failed
import org.meshtastic.core.resources.firmware_update_slow_bootloader_hint
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
import org.meshtastic.feature.firmware.ota.formatTransferProgress
import org.meshtastic.feature.firmware.ota.retryWithDelay
import org.meshtastic.feature.firmware.ota.scanForBleDevice
import org.meshtastic.feature.firmware.stripFormatArgs
import kotlin.time.Duration.Companion.seconds

private const val PERCENT_MAX = 100
private const val GATT_RELEASE_DELAY_MS = 1_500L
private const val DFU_REBOOT_WAIT_MS = 3_000L
private const val RETRY_DELAY_MS = 2_000L
private const val CONNECT_ATTEMPTS = 4

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

                // NOTE: do NOT drop the bond for a same-address Legacy bootloader. When Meshtastic triggers
                // buttonless DFU it hands the app's bond keys to the bootloader (peer_data), and the Adafruit
                // bootloader then advertises the DFU service on the SAME address using whitelist filtering
                // (BLE_GAP_ADV_FP_FILTER_BOTH) keyed to the bonded peer. Removing the bond strips the phone's
                // identity so it no longer matches the whitelist — the phone then can't connect at all. The
                // shared LTK also lets the DFU link encrypt cleanly (verified on-air: AES-128, keySize 16), so the
                // bond must be KEPT, mirroring Nordic's DfuServiceInitiator.setKeepBond(true)/setRestoreBond(true).

                // Legacy DFU has no resume, so a failed session is retried whole (fresh transport + reconnect +
                // re-handshake), mirroring Nordic's DFU library ("the Legacy DFU will start again"). A stock
                // bootloader that leaves the first session's control-point handshake unanswered often responds
                // after a clean reconnect. Secure DFU resumes in place, so it runs a single session.
                val sessionAttempts = if (protocol == DfuProtocolKind.LEGACY) LEGACY_SESSION_ATTEMPTS else 1
                runDfuUploadWithRetry(protocol, target, pkg, sessionAttempts, updateState)
                zipFile
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

    /**
     * Run the connect + init + firmware upload, retrying the whole session up to [attempts] times. Each attempt uses a
     * fresh [DfuUploadTransport] (new GATT connection + re-handshake) since Legacy DFU can't resume mid-stream.
     */
    private suspend fun runDfuUploadWithRetry(
        protocol: DfuProtocolKind,
        target: String,
        pkg: DfuZipPackage,
        attempts: Int,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        var lastError: Throwable? = null
        repeat(attempts) { i ->
            val attempt = i + 1
            Logger.i { "DFU: upload session attempt $attempt/$attempts" }
            try {
                runUploadSession(protocol, target, pkg, updateState)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                lastError = e
                Logger.w(e) { "DFU: upload session $attempt/$attempts failed: ${e.message}" }
                // A stock bootloader holding a wedged session from an interrupted flash rejects START with
                // INVALID_STATE, then goes unresponsive (the RESET can't land on that connection). A *fresh*
                // connection is responsive up until START, so reset it there — the device reboots into a clean OTA
                // session (GPREGRET OTA flag is retained) that the next attempt can flash normally.
                if (e is LegacyDfuException.StaleSessionReset && attempt < attempts) {
                    resetStaleBootloader(protocol, target)
                }
                if (attempt < attempts) delay(SESSION_RETRY_DELAY_MS)
            }
        }
        throw lastError ?: DfuException.TransferFailed("DFU upload failed after $attempts attempts")
    }

    private fun createTransport(protocol: DfuProtocolKind, target: String): DfuUploadTransport = when (protocol) {
        DfuProtocolKind.LEGACY -> LegacyDfuTransport(bleScanner, bleConnectionFactory, target, dispatchers.default)
        DfuProtocolKind.SECURE -> SecureDfuTransport(bleScanner, bleConnectionFactory, target, dispatchers.default)
    }

    /**
     * Reboot a bootloader wedged in a stale DFU session. Connects a fresh transport (which is responsive before any
     * START) and issues RESET (0x06) via [DfuUploadTransport.abort], then waits for the reboot + re-advertise. Best
     * effort: any failure here is non-fatal — the caller retries the upload regardless.
     */
    private suspend fun resetStaleBootloader(protocol: DfuProtocolKind, target: String) {
        Logger.i { "DFU: reset-priming stale bootloader before retry" }
        val transport = createTransport(protocol, target)
        try {
            transport
                .connectToDfuMode()
                .onSuccess {
                    transport.abort()
                    Logger.i { "DFU: reset-prime RESET sent; waiting for clean reboot" }
                }
                .onFailure { Logger.w(it) { "DFU: reset-prime connect failed: ${it.message}" } }
        } finally {
            withContext(NonCancellable) { transport.close() }
        }
        delay(RESET_PRIME_REBOOT_WAIT_MS)
    }

    /** A single connect + init-packet + firmware-upload session over a fresh transport; always cleans up. */
    private suspend fun runUploadSession(
        protocol: DfuProtocolKind,
        target: String,
        pkg: DfuZipPackage,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        val transport: DfuUploadTransport = createTransport(protocol, target)
        var completed = false
        try {
            connectWithRetry(transport, updateState)

            updateState(
                FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_starting_dfu))),
            )
            Logger.i {
                "DFU: Sending init packet (${pkg.initPacket.size} bytes) and firmware " +
                    "(${pkg.firmware.size} bytes) via $protocol"
            }
            transport.transferInitPacket(pkg.initPacket).getOrThrow()

            val uploadMsg = UiText.Resource(Res.string.firmware_update_uploading)
            val slowHint =
                if (transport.isLowSpeedTransfer) {
                    UiText.Resource(Res.string.firmware_update_slow_bootloader_hint)
                } else {
                    null
                }
            updateState(FirmwareUpdateState.Updating(ProgressState(uploadMsg, 0f, hint = slowHint)))

            val firmwareSize = pkg.firmware.size
            val throughputTracker = ThroughputTracker()
            transport
                .transferFirmware(pkg.firmware) { progress ->
                    val bytesSent = (progress * firmwareSize).toLong()
                    throughputTracker.record(bytesSent)
                    val details = formatTransferProgress(progress, firmwareSize, throughputTracker.bytesPerSecond())
                    updateState(
                        FirmwareUpdateState.Updating(ProgressState(uploadMsg, progress, details, hint = slowHint)),
                    )
                }
                .getOrThrow()

            updateState(
                FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_validating))),
            )

            completed = true
            updateState(FirmwareUpdateState.Success(wasLowSpeedTransfer = transport.isLowSpeedTransfer))
        } finally {
            withContext(NonCancellable) {
                if (!completed) transport.abort()
                transport.close()
            }
        }
    }

    private suspend fun connectWithRetry(transport: DfuUploadTransport, updateState: (FirmwareUpdateState) -> Unit) {
        updateState(
            FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_waiting_reboot))),
        )
        retryWithDelay(
            attempts = CONNECT_ATTEMPTS,
            retryDelayMillis = RETRY_DELAY_MS,
            onAttempt = { attempt ->
                updateState(
                    FirmwareUpdateState.Processing(
                        ProgressState(
                            UiText.Resource(
                                Res.string.firmware_update_connecting_attempt,
                                attempt,
                                CONNECT_ATTEMPTS,
                            ),
                        ),
                    ),
                )
            },
            block = { attempt ->
                transport.connectToDfuMode().onFailure {
                    Logger.w { "DFU: Connect attempt $attempt/$CONNECT_ATTEMPTS failed: ${it.message}" }
                }
            },
        )
            .getOrElse {
                throw DfuException.ConnectionFailed(
                    "Failed to connect to DFU device after $CONNECT_ATTEMPTS attempts",
                    it,
                )
            }
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

        /** Legacy DFU can't resume, so retry the whole session this many times before giving up. */
        private const val LEGACY_SESSION_ATTEMPTS = 3

        /** Delay between whole-session retries (lets the bootloader settle / resume advertising). */
        private const val SESSION_RETRY_DELAY_MS = 2_000L

        /** Wait after a reset-prime RESET for the bootloader to reboot and re-advertise a clean OTA session. */
        private const val RESET_PRIME_REBOOT_WAIT_MS = 4_000L
    }
}
