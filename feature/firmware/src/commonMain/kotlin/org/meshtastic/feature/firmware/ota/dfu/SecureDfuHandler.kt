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

/** Legacy DFU can't resume, so retry the whole session this many times before giving up. */
private const val LEGACY_SESSION_ATTEMPTS = 3

/** Limited probe/fallback budget for inconclusive detection or alternate-protocol attempts. */
private const val LIMITED_SESSION_ATTEMPTS = 1

/**
 * Transport-dispatch key: which [DfuUploadTransport] implementation ([SecureDfuTransport] or [LegacyDfuTransport]) to
 * use, and therefore which DFU service UUID its scan/connect is filtering on.
 */
internal enum class DfuProtocolKind {
    SECURE,
    LEGACY,
}

/**
 * Outcome of bootloader service detection. Unlike [DfuProtocolKind] (which is the transport-dispatch key), this type
 * keeps the "neither service observed" case representable instead of silently coercing it to one protocol.
 */
internal sealed class BootloaderDetection {
    /** Legacy DFU service (`1530`) was observed in the advertisement. Treat the bootloader as Legacy DFU. */
    data object LegacyObserved : BootloaderDetection()

    /** Secure DFU service (`FE59`) was observed in the advertisement. Treat the bootloader as Secure DFU. */
    data object SecureObserved : BootloaderDetection()

    /**
     * Neither DFU service was conclusively observed inside the detection window. Causes of an inconclusive scan include
     * advertisement-interval timing, Android scan duty-cycling, OS-level advertisement cache, and the bootloader not
     * having resumed advertising yet after the buttonless reboot.
     */
    data object Unknown : BootloaderDetection()
}

internal sealed class DfuUploadResult {
    data object Success : DfuUploadResult()

    data class Failure(val error: Throwable, val protocolEngaged: Boolean) : DfuUploadResult()
}

/**
 * Owns fallback ordering and retry budget policy. Fallback is intentionally limited to failures that happen before the
 * selected transport has connected and engaged its DFU protocol session.
 */
internal class DfuFallbackCoordinator(private val detection: BootloaderDetection) {

    suspend fun execute(uploadWithRetry: suspend (DfuProtocolKind, Int) -> DfuUploadResult) {
        val orderedProtocols = detection.orderedProtocols()
        Logger.i { "DFU: detection=$detection → protocols=$orderedProtocols" }

        var primaryError: Throwable? = null
        for ((index, protocol) in orderedProtocols.withIndex()) {
            val isPrimary = index == 0
            val hasAlternateProtocol = index < orderedProtocols.lastIndex
            val sessionAttempts = sessionAttemptsFor(protocol, isPrimary)
            if (isPrimary) {
                Logger.i { "DFU: primary protocol=$protocol ($sessionAttempts session attempt(s))" }
            } else {
                Logger.w {
                    "DFU: falling back to alternate protocol=$protocol before protocol engagement " +
                        "(detection=$detection, sessionAttempts=$sessionAttempts)"
                }
            }

            when (val result = uploadWithRetry(protocol, sessionAttempts)) {
                DfuUploadResult.Success -> return

                is DfuUploadResult.Failure -> {
                    if (isPrimary) {
                        primaryError = result.error
                    }
                    if (!hasAlternateProtocol || !shouldTryAlternateAfterFailure(isPrimary, result.protocolEngaged)) {
                        throw primaryError.withSuppressedAlternate(result.error)
                    }
                    Logger.w {
                        "DFU: $protocol failed; trying alternate protocol (detection=$detection, " +
                            "protocolEngaged=${result.protocolEngaged}): ${result.error::class.simpleName}"
                    }
                }
            }
        }
        throw IllegalStateException("DFU fallback exhausted with non-empty protocol list (detection=$detection)")
    }

    /**
     * The ordered list of protocols to attempt for this detection outcome. The first element is the primary; the second
     * is the alternate, attempted only if the primary fails before protocol engagement.
     */
    private fun BootloaderDetection.orderedProtocols(): List<DfuProtocolKind> = when (this) {
        BootloaderDetection.LegacyObserved -> listOf(DfuProtocolKind.LEGACY, DfuProtocolKind.SECURE)
        BootloaderDetection.SecureObserved -> listOf(DfuProtocolKind.SECURE, DfuProtocolKind.LEGACY)
        BootloaderDetection.Unknown -> listOf(DfuProtocolKind.LEGACY, DfuProtocolKind.SECURE)
    }

    /**
     * Session retry budget for the given protocol in this detection context.
     * - Confirmed Legacy primary ([BootloaderDetection.LegacyObserved]): [LEGACY_SESSION_ATTEMPTS] (3) to cover the
     *   stale-session reset-prime recovery path.
     * - Unknown Legacy primary and alternate protocols: [LIMITED_SESSION_ATTEMPTS] (1) to keep speculative probes short
     *   enough that a Secure bootloader still has time to accept its fallback attempt.
     */
    private fun sessionAttemptsFor(protocol: DfuProtocolKind, isPrimary: Boolean): Int = when {
        protocol == DfuProtocolKind.LEGACY && isPrimary && detection == BootloaderDetection.LegacyObserved ->
            LEGACY_SESSION_ATTEMPTS

        else -> LIMITED_SESSION_ATTEMPTS
    }

    private fun shouldTryAlternateAfterFailure(isPrimary: Boolean, protocolEngaged: Boolean): Boolean {
        if (!isPrimary) return false
        return when (detection) {
            // Unknown can mean the primary Legacy advertisement was missed, so a pre-engagement failure is the useful
            // fallback signal.
            BootloaderDetection.Unknown -> !protocolEngaged

            // Conclusive detections already observed the selected protocol's service. If it never engages, retrying the
            // opposite service is usually a long doomed scan. Keep fallback only as insurance for a stale/wrong
            // conclusive signal after the observed service was actually reached.
            BootloaderDetection.LegacyObserved,
            BootloaderDetection.SecureObserved,
            -> protocolEngaged
        }
    }

    private fun Throwable?.withSuppressedAlternate(alternateError: Throwable): Throwable {
        val primary = this ?: return alternateError
        if (primary !== alternateError) {
            primary.addSuppressed(alternateError)
        }
        return primary
    }
}

/** The DFU service UUID this protocol scans for, for diagnostic logging. Mirrors the transport's own scan filter. */
private fun DfuProtocolKind.serviceUuid(): Uuid = when (this) {
    DfuProtocolKind.LEGACY -> LegacyDfuUuids.SERVICE
    DfuProtocolKind.SECURE -> SecureDfuUuids.SERVICE
}

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
                // DfuFallbackCoordinator resolves the detection into an ordered protocol list and may try the
                // alternate protocol only before the selected protocol has connected and engaged its DFU session.
                DfuFallbackCoordinator(detection).execute { protocol, sessionAttempts ->
                    runDfuUploadWithRetry(protocol, target, pkg, sessionAttempts, updateState)
                }
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
     * coercing a missed Legacy scan into a Secure-DFU verdict. The fallback coordinator resolves a detection into an
     * ordered list of protocols to attempt, so a `Unknown` result no longer locks the handler into a single transport —
     * both protocols are tried (Legacy first) before the update is declared failed. `Unknown` means neither service was
     * observed in the detection windows.
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
        Logger.i { "DFU: detect — predicate matches original MAC and MAC+1" }

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
     * Run the connect + init + firmware upload, retrying the whole session up to [attempts] times. Each attempt uses a
     * fresh [DfuUploadTransport] (new GATT connection + re-handshake) since Legacy DFU can't resume mid-stream.
     *
     * Returns the last failure with whether this protocol ever connected and started its DFU session, allowing fallback
     * orchestration to switch protocols only for pre-engagement failures.
     */
    private suspend fun runDfuUploadWithRetry(
        protocol: DfuProtocolKind,
        target: String,
        pkg: DfuZipPackage,
        attempts: Int,
        updateState: (FirmwareUpdateState) -> Unit,
    ): DfuUploadResult {
        var lastError: Throwable? = null
        var protocolEngaged = false
        repeat(attempts) { i ->
            val attempt = i + 1
            Logger.i { "DFU: upload session attempt $attempt/$attempts ($protocol)" }
            when (val result = runUploadSession(protocol, target, pkg, updateState)) {
                DfuUploadResult.Success -> return result

                is DfuUploadResult.Failure -> {
                    lastError = result.error
                    protocolEngaged = protocolEngaged || result.protocolEngaged
                    Logger.w(result.error) {
                        "DFU: upload session $attempt/$attempts failed ($protocol): ${result.error::class.simpleName}"
                    }
                    // A stock bootloader holding a wedged session from an interrupted flash rejects START with
                    // INVALID_STATE, then goes unresponsive (the RESET can't land on that connection). A *fresh*
                    // connection is responsive up until START, so reset it there — the device reboots into a clean OTA
                    // session (GPREGRET OTA flag is retained) that the next attempt can flash normally.
                    if (result.error is LegacyDfuException.StaleSessionReset && attempt < attempts) {
                        resetStaleBootloader(protocol, target)
                    }
                    if (attempt < attempts) delay(SESSION_RETRY_DELAY_MS)
                }
            }
        }
        return DfuUploadResult.Failure(
            lastError ?: DfuException.TransferFailed("DFU upload failed after $attempts attempts ($protocol)"),
            protocolEngaged,
        )
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
    ): DfuUploadResult {
        val transport: DfuUploadTransport = createTransport(protocol, target)
        var completed = false
        var protocolEngaged = false
        try {
            connectWithRetry(transport, protocol, updateState)
            protocolEngaged = true

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
            return DfuUploadResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            return DfuUploadResult.Failure(e, protocolEngaged)
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
                    Logger.w(it) {
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

    private companion object {
        /**
         * Per-service scan timeout for bootloader protocol detection. Applied to both Legacy (1530) and Secure (FE59)
         * scans. Sequential, so worst-case detection is 2× this value.
         */
        private val DETECT_SCAN_TIMEOUT = 8.seconds

        /** Delay between whole-session retries (lets the bootloader settle / resume advertising). */
        private const val SESSION_RETRY_DELAY_MS = 2_000L

        /** Wait after a reset-prime RESET for the bootloader to reboot and re-advertise a clean OTA session. */
        private const val RESET_PRIME_REBOOT_WAIT_MS = 4_000L
    }
}
