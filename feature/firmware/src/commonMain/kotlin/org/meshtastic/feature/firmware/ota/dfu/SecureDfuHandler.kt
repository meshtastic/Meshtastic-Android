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
import kotlin.uuid.Uuid

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
@Suppress("TooManyFunctions")
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

                // ── 4. Service detection: which DFU service does the bootloader advertise? ─
                val detection = detectBootloaderProtocol(target, updateState)
                Logger.i { "DFU: Bootloader detection = $detection" }

                // NOTE: do NOT drop the bond for a same-address Legacy bootloader. When Meshtastic triggers
                // buttonless DFU it hands the app's bond keys to the bootloader (peer_data), and the Adafruit
                // bootloader then advertises the DFU service on the SAME address using whitelist filtering
                // (BLE_GAP_ADV_FP_FILTER_BOTH) keyed to the bonded peer. Removing the bond strips the phone's
                // identity so it no longer matches the whitelist — the phone then can't connect at all. The
                // shared LTK also lets the DFU link encrypt cleanly (verified on-air: AES-128, keySize 16), so the
                // bond must be KEPT, mirroring Nordic's DfuServiceInitiator.setKeepBond(true)/setRestoreBond(true).
                // This applies to BOTH protocols and to the fallback path — the bond is never removed or refreshed
                // anywhere in this handler.

                // Legacy DFU has no resume, so a confirmed Legacy session is retried whole (fresh transport +
                // reconnect + re-handshake), mirroring Nordic's DFU library ("the Legacy DFU will start again"). A
                // stock bootloader that leaves the first session's control-point handshake unanswered often responds
                // after a clean reconnect. Secure DFU resumes in place, so it runs a single session.
                // runDfuUploadWithFallback resolves the detection into an ordered protocol list and may try the
                // alternate protocol only before the selected protocol has connected and engaged its DFU session.
                runDfuUploadWithFallback(detection, target, pkg, updateState)
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
     * Detect which DFU service the bootloader advertises by scanning first for the Legacy (`1530`) service UUID. If
     * Legacy is observed, return immediately; otherwise scan for the Secure (`FE59`) service UUID. The scans run
     * sequentially because BLE scan is a shared OS resource and parallel scans are unreliable across Android versions.
     *
     * Returns a [BootloaderDetection] that keeps the inconclusive case (`Unknown`) *representable* instead of silently
     * coercing a missed Legacy scan into a Secure-DFU verdict. The caller ([runDfuUploadWithFallback]) resolves a
     * detection into an ordered list of protocols to attempt, so a `Unknown` result no longer locks the handler into a
     * single transport — both protocols are tried (Legacy first) before the update is declared failed. `Unknown` means
     * neither service was observed in the detection windows.
     */
    private suspend fun detectBootloaderProtocol(
        target: String,
        updateState: (FirmwareUpdateState) -> Unit,
    ): BootloaderDetection {
        updateState(
            FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_waiting_reboot))),
        )
        val macPlusOne = calculateMacPlusOne(target)
        val targetAddresses = setOf(target, macPlusOne)
        Logger.i { "DFU: detect target address set = {original MAC, MAC+1}" }

        Logger.i { "DFU: detect — scanning for Legacy DFU service (${LegacyDfuUuids.SERVICE})" }
        val legacyHit =
            scanForBleDevice(
                scanner = bleScanner,
                tag = "DFU detect (legacy)",
                serviceUuid = LegacyDfuUuids.SERVICE,
                retryCount = 1,
                retryDelay = 0.seconds,
                scanTimeout = DETECT_SCAN_TIMEOUT,
                predicate = { it.address in targetAddresses },
            )

        if (legacyHit != null) {
            Logger.i {
                "DFU: detect — Legacy service observed; skipping Secure DFU scan (Legacy wins per orderedProtocols)"
            }
            return BootloaderDetection.LegacyObserved
        }

        Logger.i { "DFU: detect — scanning for Secure DFU service (${SecureDfuUuids.SERVICE})" }
        val secureHit =
            scanForBleDevice(
                scanner = bleScanner,
                tag = "DFU detect (secure)",
                serviceUuid = SecureDfuUuids.SERVICE,
                retryCount = 1,
                retryDelay = 0.seconds,
                scanTimeout = DETECT_SCAN_TIMEOUT,
                predicate = { it.address in targetAddresses },
            )

        val detection = if (secureHit != null) BootloaderDetection.SecureObserved else BootloaderDetection.Unknown
        Logger.i { "DFU: detect — secureHit=${secureHit != null}, result=$detection" }
        return detection
    }

    /**
     * Drive the upload, translating a [BootloaderDetection] into an ordered list of [DfuProtocolKind]s and trying each
     * in turn. The primary protocol runs first with a budget from [sessionAttemptsFor]: full Legacy recovery is
     * reserved for observed Legacy bootloaders, while inconclusive probes and alternate-protocol fallback use a limited
     * budget. On a failure that occurred before the selected protocol connected and engaged its DFU session, the
     * alternate protocol is attempted as a last resort before the failure is surfaced to the user.
     *
     * Protocol-level failures are never switched: once [TransferProgression.protocolEngaged] is `true`, any failure
     * rethrows immediately and the existing per-protocol retry/abort/cleanup paths take over.
     *
     * Per-session transport cleanup is handled entirely by [runUploadSession]'s `NonCancellable` finally block — the
     * failed transport of the previous protocol is already `abort()`ed and `close()`d before this loop iterates, so no
     * transport is left alive across protocols.
     */
    @Suppress("ThrowsCount")
    private suspend fun runDfuUploadWithFallback(
        detection: BootloaderDetection,
        target: String,
        pkg: DfuZipPackage,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        val orderedProtocols = detection.orderedProtocols()
        Logger.i { "DFU: detection=$detection → protocols=$orderedProtocols" }

        var lastError: Throwable? = null
        for ((index, protocol) in orderedProtocols.withIndex()) {
            val isPrimary = index == 0
            val hasAlternateProtocol = index < orderedProtocols.lastIndex
            val sessionAttempts = sessionAttemptsFor(protocol, detection, isPrimary)
            val progression = TransferProgression()
            if (isPrimary) {
                Logger.i { "DFU: primary protocol=$protocol ($sessionAttempts session attempt(s))" }
            } else {
                Logger.w {
                    "DFU: falling back to alternate protocol=$protocol before protocol engagement " +
                        "(detection=$detection, sessionAttempts=$sessionAttempts)"
                }
            }
            try {
                runDfuUploadWithRetry(protocol, target, pkg, sessionAttempts, progression, updateState)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                lastError = e
                if (progression.protocolEngaged) throw e
                if (!hasAlternateProtocol) continue
                // The failed transport for this protocol was already abort()+close()'d by runUploadSession's
                // NonCancellable finally, so we can safely iterate to the alternate protocol on the next loop turn.
                Logger.w(e) {
                    "DFU: $protocol failed before protocol engagement (detection=$detection); " +
                        "will try alternate protocol"
                }
            }
        }
        throw lastError ?: DfuException.TransferFailed("DFU upload failed after exhausting all protocols ($detection)")
    }

    /**
     * The ordered list of protocols to attempt for this detection outcome. The first element is the primary; the second
     * is the alternate, attempted only if the primary fails *before* protocol engagement (see
     * [runDfuUploadWithFallback]).
     *
     * `Unknown` resolves to a limited Legacy probe first because the missed-detection population can be Legacy, then
     * Secure promptly if Legacy does not connect.
     */
    private fun BootloaderDetection.orderedProtocols(): List<DfuProtocolKind> = when (this) {
        BootloaderDetection.LegacyObserved -> listOf(DfuProtocolKind.LEGACY, DfuProtocolKind.SECURE)
        BootloaderDetection.SecureObserved -> listOf(DfuProtocolKind.SECURE, DfuProtocolKind.LEGACY)
        BootloaderDetection.Unknown -> listOf(DfuProtocolKind.LEGACY, DfuProtocolKind.SECURE)
    }

    private fun sessionAttemptsFor(protocol: DfuProtocolKind, detection: BootloaderDetection, isPrimary: Boolean): Int =
        when {
            detection == BootloaderDetection.LegacyObserved && protocol == DfuProtocolKind.LEGACY && isPrimary ->
                LEGACY_SESSION_ATTEMPTS

            else -> LIMITED_SESSION_ATTEMPTS
        }

    /**
     * The DFU service UUID this protocol scans for, for diagnostic logging. Mirrors the transport's own scan filter.
     */
    private fun DfuProtocolKind.serviceUuid(): Uuid = when (this) {
        DfuProtocolKind.LEGACY -> LegacyDfuUuids.SERVICE
        DfuProtocolKind.SECURE -> SecureDfuUuids.SERVICE
    }

    /**
     * Run the connect + init + firmware upload, retrying the whole session up to [attempts] times. Each attempt uses a
     * fresh [DfuUploadTransport] (new GATT connection + re-handshake) since Legacy DFU can't resume mid-stream.
     *
     * [progression] is threaded into [runUploadSession] so the outer [runDfuUploadWithFallback] can authorize (or
     * refuse) a fallback to the alternate protocol based on whether this protocol has connected and started its DFU
     * session.
     */
    private suspend fun runDfuUploadWithRetry(
        protocol: DfuProtocolKind,
        target: String,
        pkg: DfuZipPackage,
        attempts: Int,
        progression: TransferProgression,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        var lastError: Throwable? = null
        repeat(attempts) { i ->
            val attempt = i + 1
            Logger.i { "DFU: upload session attempt $attempt/$attempts ($protocol)" }
            try {
                runUploadSession(protocol, target, pkg, progression, updateState)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                lastError = e
                Logger.w(e) { "DFU: upload session $attempt/$attempts failed ($protocol): ${e.message}" }
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
        throw lastError ?: DfuException.TransferFailed("DFU upload failed after $attempts attempts ($protocol)")
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
        progression: TransferProgression,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        val transport: DfuUploadTransport = createTransport(protocol, target)
        var completed = false
        try {
            connectWithRetry(transport, protocol, updateState)
            progression.protocolEngaged = true

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

    private suspend fun connectWithRetry(
        transport: DfuUploadTransport,
        protocol: DfuProtocolKind,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        val serviceUuid = protocol.serviceUuid()
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
                Logger.i {
                    "DFU: Connect attempt $attempt/$CONNECT_ATTEMPTS via $protocol (scan service=$serviceUuid)"
                }
                transport.connectToDfuMode().onFailure {
                    Logger.w {
                        "DFU: Connect attempt $attempt/$CONNECT_ATTEMPTS via $protocol failed: ${it.message}"
                    }
                }
            },
        )
            .getOrElse {
                throw DfuException.ConnectionFailed(
                    "Failed to connect to DFU device via $protocol after $CONNECT_ATTEMPTS attempts",
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

    /**
     * Transport-dispatch key: which [DfuUploadTransport] implementation ([SecureDfuTransport] or [LegacyDfuTransport])
     * to use, and therefore which DFU service UUID its scan/connect is filtering on.
     *
     * Note: [detectBootloaderProtocol] now returns a [BootloaderDetection] (a richer type that keeps the "neither
     * service observed" case representable). [DfuProtocolKind] is derived from a [BootloaderDetection] via
     * [BootloaderDetection.orderedProtocols] inside [runDfuUploadWithFallback].
     */
    internal enum class DfuProtocolKind {
        SECURE,
        LEGACY,
    }

    /**
     * Outcome of [detectBootloaderProtocol]. Unlike [DfuProtocolKind] (which is the transport-dispatch key), this type
     * keeps the "neither service observed" case *representable* instead of silently coercing it to one of the two
     * protocols. The caller turns a [BootloaderDetection] into an ordered list of [DfuProtocolKind]s and may try each
     * in turn before declaring the update failed — see [runDfuUploadWithFallback].
     */
    internal sealed class BootloaderDetection {
        /** Legacy DFU service (`1530`) was observed in the advertisement. Treat the bootloader as Legacy DFU. */
        data object LegacyObserved : BootloaderDetection()

        /** Secure DFU service (`FE59`) was observed in the advertisement. Treat the bootloader as Secure DFU. */
        data object SecureObserved : BootloaderDetection()

        /**
         * Neither DFU service was conclusively observed inside the detection window. Causes of an inconclusive scan
         * include advertisement-interval timing, Android scan duty-cycling, OS-level advertisement cache, and the
         * bootloader not having resumed advertising yet after the buttonless reboot. Do NOT lock the handler into a
         * single protocol in this state — try both, Legacy first, before giving up.
         */
        data object Unknown : BootloaderDetection()
    }

    /**
     * Mutable flag threaded through [runUploadSession] so [runDfuUploadWithFallback] can tell whether the current
     * protocol has connected and started its DFU session. Fallback to the alternate protocol is authorized only while
     * this stays `false`; init-packet, handshake, object, firmware, validation, and abort failures belong to the
     * engaged protocol and are not switched to another protocol.
     */
    private class TransferProgression {
        /** Set to `true` immediately after [connectWithRetry] succeeds and before protocol-level DFU work starts. */
        var protocolEngaged: Boolean = false
    }

    private companion object {
        /** Detection scan timeout — short because we only want to confirm/refute an advertised legacy service. */
        private val DETECT_SCAN_TIMEOUT = 8.seconds

        /** Legacy DFU can't resume, so retry the whole session this many times before giving up. */
        private const val LEGACY_SESSION_ATTEMPTS = 3

        /** Limited probe/fallback budget for inconclusive detection or alternate-protocol attempts. */
        private const val LIMITED_SESSION_ATTEMPTS = 1

        /** Delay between whole-session retries (lets the bootloader settle / resume advertising). */
        private const val SESSION_RETRY_DELAY_MS = 2_000L

        /** Wait after a reset-prime RESET for the bootloader to reboot and re-advertise a clean OTA session. */
        private const val RESET_PRIME_REBOOT_WAIT_MS = 4_000L
    }
}
