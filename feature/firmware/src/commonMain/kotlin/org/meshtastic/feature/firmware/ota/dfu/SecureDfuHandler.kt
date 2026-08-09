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
 * Real upload attempts a confirmed Legacy primary is allowed before declaring failure. Legacy DFU has no resume, so a
 * failed upload requires a whole fresh session (new transport + reconnect + re-handshake). Stale-session cleanup cycles
 * do NOT consume this budget (see [MAX_LEGACY_STALE_RESETS]).
 */
internal const val LEGACY_SESSION_ATTEMPTS = 3

/**
 * Maximum number of stale-session cleanup cycles a Legacy run will perform. A [LegacyDfuException.StaleSessionReset]
 * surfaces when the bootloader rejects START_DFU with INVALID_STATE (a half-finished previous session is wedged in the
 * bootloader). The cleanup connects a fresh transport, RESETs, and waits for the bootloader to reboot into a clean OTA
 * session. This budget is separate from [LEGACY_SESSION_ATTEMPTS] so a run that legitimately needs to reset-prime more
 * than once (e.g. a mid-stream drop that left the bootloader wedged twice in a row) is not punished by losing an upload
 * attempt for a cleanup that did not actually try to upload.
 */
internal const val MAX_LEGACY_STALE_RESETS = 2

/** Limited probe/fallback budget for inconclusive detection or alternate-protocol attempts. */
internal const val LIMITED_SESSION_ATTEMPTS = 1

/**
 * Two-stage session budget.
 *
 * [preEngagementAttempts] limits speculative connection attempts before the selected DFU service has been confirmed.
 * [engagedAttempts] applies after a connection to that protocol's DFU service succeeds. Engagement means the selected
 * protocol's DFU service was connected to; it does not by itself universally prevent fallback (see
 * [DfuFallbackCoordinator] for the fallback policy). Secure always carries [preEngagementAttempts] == [engagedAttempts]
 * so engagement never expands its budget.
 */
internal data class DfuAttemptBudget(val preEngagementAttempts: Int, val engagedAttempts: Int) {
    init {
        require(preEngagementAttempts in 1..engagedAttempts) {
            "preEngagementAttempts ($preEngagementAttempts) must be in 1..engagedAttempts ($engagedAttempts)"
        }
    }
}

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
 * Owns fallback ordering and retry budget policy. Whether an alternate protocol is tried after a primary failure
 * depends on the [detection] outcome and engagement state — see [shouldTryAlternateAfterFailure].
 */
internal class DfuFallbackCoordinator(private val detection: BootloaderDetection) {

    suspend fun execute(uploadWithRetry: suspend (DfuProtocolKind, DfuAttemptBudget) -> DfuUploadResult) {
        val orderedProtocols = detection.orderedProtocols()
        Logger.i { "DFU: detection=$detection → protocols=$orderedProtocols" }

        var primaryError: Throwable? = null
        for ((index, protocol) in orderedProtocols.withIndex()) {
            val isPrimary = index == 0
            val hasAlternateProtocol = index < orderedProtocols.lastIndex
            val budget = budgetFor(protocol, isPrimary)
            if (isPrimary) {
                Logger.i {
                    "DFU: primary protocol=$protocol " +
                        "(upload attempts pre=${budget.preEngagementAttempts}, engaged=${budget.engagedAttempts})"
                }
            } else {
                Logger.w { "DFU: trying alternate protocol=$protocol (detection=$detection, budget=$budget)" }
            }

            when (val result = uploadWithRetry(protocol, budget)) {
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
     * is the alternate. Whether the alternate is reached depends on [shouldTryAlternateAfterFailure], not ordering.
     */
    private fun BootloaderDetection.orderedProtocols(): List<DfuProtocolKind> = when (this) {
        BootloaderDetection.LegacyObserved -> listOf(DfuProtocolKind.LEGACY, DfuProtocolKind.SECURE)
        BootloaderDetection.SecureObserved -> listOf(DfuProtocolKind.SECURE, DfuProtocolKind.LEGACY)
        BootloaderDetection.Unknown -> listOf(DfuProtocolKind.LEGACY, DfuProtocolKind.SECURE)
    }

    /**
     * Two-stage upload-attempt budget for the given protocol in this detection context.
     * - Confirmed Legacy primary ([BootloaderDetection.LegacyObserved]): full [LEGACY_SESSION_ATTEMPTS] (3) budget
     *   before AND after engagement — the protocol was conclusively observed.
     * - Unknown Legacy primary: [LIMITED_SESSION_ATTEMPTS] (1) pre-engagement probe so Secure fallback stays timely,
     *   promoted to [LEGACY_SESSION_ATTEMPTS] (3) once Legacy engages and is conclusively identified.
     * - Legacy alternate (e.g. SecureObserved → Legacy fallback): same Unknown-style two-stage budget — pre-engagement
     *   probe of 1, full Legacy budget of 3 once engaged.
     * - Secure: 1/1 — never expanded by engagement, since the Secure recovery path is single-shot.
     */
    private fun budgetFor(protocol: DfuProtocolKind, isPrimary: Boolean): DfuAttemptBudget = when {
        protocol == DfuProtocolKind.SECURE ->
            DfuAttemptBudget(
                preEngagementAttempts = LIMITED_SESSION_ATTEMPTS,
                engagedAttempts = LIMITED_SESSION_ATTEMPTS,
            )

        protocol == DfuProtocolKind.LEGACY && isPrimary && detection == BootloaderDetection.LegacyObserved ->
            DfuAttemptBudget(
                preEngagementAttempts = LEGACY_SESSION_ATTEMPTS,
                engagedAttempts = LEGACY_SESSION_ATTEMPTS,
            )

        // Unknown Legacy primary and Legacy alternate: speculative probe that promotes on engagement.
        else ->
            DfuAttemptBudget(
                preEngagementAttempts = LIMITED_SESSION_ATTEMPTS,
                engagedAttempts = LEGACY_SESSION_ATTEMPTS,
            )
    }

    private fun shouldTryAlternateAfterFailure(isPrimary: Boolean, protocolEngaged: Boolean): Boolean {
        if (!isPrimary) return false
        return when (detection) {
            // Unknown detection: Legacy is tried first, Secure fallback only if Legacy fails before engagement.
            BootloaderDetection.Unknown -> !protocolEngaged

            // Conclusive detection: a pre-engagement failure of the observed protocol does NOT immediately try the
            // opposite protocol. Alternate fallback is retained only after engagement failure — insurance against a
            // stale or misleading conclusive detection.
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
 * Drives the bounded Legacy/Secure DFU upload retry loop. Extracted from [SecureDfuHandler.runDfuUploadWithRetry] so
 * the retry policy can be unit-tested without bringing up the full BLE stack — callers supply a [runUploadSession]
 * lambda that returns the next [DfuUploadResult] and a [resetStaleBootloader] lambda that performs the fresh-connection
 * RESET-prime cycle.
 *
 * Policy:
 * - Active attempt limit starts at [DfuAttemptBudget.preEngagementAttempts]; the first [DfuUploadResult.Failure] whose
 *   `protocolEngaged=true` promotes the active limit to [DfuAttemptBudget.engagedAttempts] BEFORE the budget-exhaustion
 *   check runs, so an engaged Legacy probe receives its full retry budget.
 * - Non-[LegacyDfuException.StaleSessionReset] failures consume one upload attempt.
 * - A [LegacyDfuException.StaleSessionReset] consumes one of [maxStaleResets], NOT an upload attempt — the cleanup
 *   cycle never tried to upload.
 * - After the first [LegacyDfuException.MidStreamDisconnect] on Legacy, every subsequent Legacy attempt uses
 *   [LegacyDfuStreamProfile.RECOVERY]; a stale-session alone never switches the profile.
 * - All exit paths are bounded: the loop stops when the upload-attempt budget is exhausted or the stale-response budget
 *   is exceeded. Up to [maxStaleResets] reset-prime cleanups run; the next stale response terminates without another
 *   cleanup.
 * - [DfuProtocolKind.SECURE] carries pre==engaged budgets, so engagement promotion is a no-op there.
 *
 * If a mid-stream drop preceded the terminal failure, the first [LegacyDfuException.MidStreamDisconnect] is attached as
 * a suppressed exception on the surfaced error so the underlying cause stays visible.
 */
@Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod")
internal suspend fun runDfuRetryLoop(
    protocol: DfuProtocolKind,
    budget: DfuAttemptBudget,
    maxStaleResets: Int,
    runUploadSession: suspend (LegacyDfuStreamProfile) -> DfuUploadResult,
    resetStaleBootloader: suspend () -> Unit,
    interAttemptDelay: suspend () -> Unit,
): DfuUploadResult {
    var uploadAttempts = 0
    var staleResets = 0
    var midStreamSeen = false
    var firstMidStreamError: LegacyDfuException.MidStreamDisconnect? = null
    var lastError: Throwable? = null
    var protocolEngaged = false
    var activeAttempts = budget.preEngagementAttempts

    while (uploadAttempts < activeAttempts) {
        var skipInterAttemptDelay = false

        val profile =
            if (protocol == DfuProtocolKind.LEGACY && midStreamSeen) {
                LegacyDfuStreamProfile.RECOVERY
            } else {
                LegacyDfuStreamProfile.NORMAL
            }

        uploadAttempts++
        Logger.i { "DFU: upload attempt $uploadAttempts/$activeAttempts ($protocol, profile=$profile)" }
        when (val result = runUploadSession(profile)) {
            DfuUploadResult.Success -> return result

            is DfuUploadResult.Failure -> {
                lastError = result.error
                val wasEngaged = protocolEngaged
                protocolEngaged = protocolEngaged || result.protocolEngaged

                // Promote the upload budget the moment a session first reports protocolEngaged. Promotion happens
                // BEFORE the budget-exhaustion check (the while condition on the next iteration) so an engaged
                // Legacy probe receives its full retry budget. For Secure (pre==engaged) this is a no-op.
                if (!wasEngaged && protocolEngaged && activeAttempts < budget.engagedAttempts) {
                    activeAttempts = budget.engagedAttempts
                    Logger.i {
                        "DFU: protocol engaged; promoting upload budget " +
                            "(${budget.preEngagementAttempts} → ${budget.engagedAttempts}) ($protocol)"
                    }
                }

                // Legacy-only stale-session handling. StaleSessionReset consumes a stale-reset cycle, NOT an upload
                // attempt — the cycle never tried to upload.
                if (protocol == DfuProtocolKind.LEGACY && result.error is LegacyDfuException.StaleSessionReset) {
                    uploadAttempts--
                    staleResets++
                    if (staleResets > maxStaleResets) {
                        Logger.w {
                            "DFU: stale cleanup exhausted ($staleResets > $maxStaleResets) — giving up on $protocol"
                        }
                        break
                    }
                    if (midStreamSeen) {
                        // Expected: a stale session is the natural residue of a mid-stream drop, so an informational
                        // log keeps the operator calm.
                        Logger.i {
                            "DFU: stale cleanup cycle $staleResets/$maxStaleResets after mid-stream drop " +
                                "(expected) ($protocol)"
                        }
                    } else {
                        // Unexpected: the very first session came up stale with no preceding drop, which usually
                        // means the device was stranded in the bootloader before this update began.
                        Logger.w {
                            "DFU: stale cleanup cycle $staleResets/$maxStaleResets — unexpected initial stale " +
                                "session ($protocol)"
                        }
                    }
                    resetStaleBootloader()
                    skipInterAttemptDelay = true
                }

                if (!skipInterAttemptDelay) {
                    // Mid-stream disconnect: switch subsequent Legacy uploads to the recovery profile and surface the
                    // host in-flight offset in the log so it is not hidden by later cleanup failures.
                    val error = result.error
                    if (protocol == DfuProtocolKind.LEGACY && error is LegacyDfuException.MidStreamDisconnect) {
                        if (!midStreamSeen) {
                            midStreamSeen = true
                            firstMidStreamError = error
                        }
                        Logger.w(error) {
                            "DFU: mid-stream disconnect at host in-flight offset " +
                                "${error.bytesSent}/${error.totalBytes} " +
                                "(state=${error.connectionState}); subsequent $protocol attempts will use " +
                                "${LegacyDfuStreamProfile.RECOVERY}"
                        }
                    } else {
                        Logger.w(error) {
                            "DFU: upload attempt $uploadAttempts/$activeAttempts failed ($protocol): " +
                                "${error::class.simpleName}"
                        }
                    }

                    if (uploadAttempts < activeAttempts) interAttemptDelay()
                }
            }
        }
    }

    val error = lastError ?: DfuException.TransferFailed("DFU upload failed after $activeAttempts attempts ($protocol)")
    // Keep the original mid-stream failure visible even when the terminal failure is a later cleanup/transfer error.
    firstMidStreamError?.let { drop ->
        if (error !== drop) {
            error.addSuppressed(drop)
        }
    }
    return DfuUploadResult.Failure(error, protocolEngaged)
}

/**
 * Cleanup policy for a DFU upload session's transport. Extracted from [SecureDfuHandler.runUploadSession]'s finally
 * block so the skip-abort / always-close contract can be tested directly.
 *
 * Semantics:
 * - Executes in [NonCancellable] so close always runs even if the caller was cancelled.
 * - Skips [DfuUploadTransport.abort] after a completed session — nothing to abort.
 * - Skips [DfuUploadTransport.abort] after a [LegacyDfuException.StaleSessionReset] — some Legacy variants become
 *   unresponsive after INVALID_STATE, so the same-connection RESET may block or be ineffective; the fresh-connection
 *   reset-prime path owns recovery.
 * - Skips [DfuUploadTransport.abort] after a [LegacyDfuException.MidStreamDisconnect] — the link has already dropped,
 *   so there is no live connection over which to deliver an abort.
 * - Attempts [DfuUploadTransport.abort] after a [DfuException.ConnectionFailed] — [DfuException.ConnectionFailed] is
 *   produced by [connectWithRetry] for any exhausted connection or setup failure, including cases where a GATT
 *   connection was established but a subsequent step (e.g. unsupported bootloader version, profile setup) failed. A
 *   usable connection may still exist, so the bounded best-effort abort is attempted before close.
 * - Otherwise calls [DfuUploadTransport.abort], then [DfuUploadTransport.close] in a nested finally.
 * - [DfuUploadTransport.abort]'s own cancellation/Error propagation contract is preserved: the [NonCancellable] context
 *   prevents the outer coroutine cancellation from interrupting close, but an [Error] thrown by abort still propagates
 *   after close runs.
 */
internal suspend fun cleanupDfuSessionTransport(
    transport: DfuUploadTransport,
    completed: Boolean,
    sessionFailure: Throwable?,
) {
    withContext(NonCancellable) {
        val skipAbort =
            sessionFailure is LegacyDfuException.StaleSessionReset ||
                sessionFailure is LegacyDfuException.MidStreamDisconnect
        try {
            if (!completed && !skipAbort) transport.abort()
        } finally {
            transport.close()
        }
    }
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
                // DfuFallbackCoordinator resolves the detection into an ordered protocol list; whether the alternate
                // is tried depends on detection-specific engagement rules (shouldTryAlternateAfterFailure).
                DfuFallbackCoordinator(detection).execute { protocol, budget ->
                    runDfuUploadWithRetry(protocol, target, pkg, budget, updateState)
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
     * Run the connect + init + firmware upload, retrying within the [DfuFallbackCoordinator] budget. Delegates the
     * bounded retry logic to [runDfuRetryLoop] (testable without bringing up the full BLE stack); the lambdas here
     * supply the real [runUploadSession] and [resetStaleBootloader] implementations.
     */
    private suspend fun runDfuUploadWithRetry(
        protocol: DfuProtocolKind,
        target: String,
        pkg: DfuZipPackage,
        budget: DfuAttemptBudget,
        updateState: (FirmwareUpdateState) -> Unit,
    ): DfuUploadResult = runDfuRetryLoop(
        protocol = protocol,
        budget = budget,
        maxStaleResets = MAX_LEGACY_STALE_RESETS,
        runUploadSession = { profile -> runUploadSession(protocol, target, pkg, profile, updateState) },
        resetStaleBootloader = { resetStaleBootloader(protocol, target) },
        interAttemptDelay = { delay(SESSION_RETRY_DELAY_MS) },
    )

    private fun createTransport(
        protocol: DfuProtocolKind,
        target: String,
        legacyProfile: LegacyDfuStreamProfile = LegacyDfuStreamProfile.NORMAL,
    ): DfuUploadTransport = when (protocol) {
        // Legacy transport receives the selected stream profile; default NORMAL keeps prior behavior for any caller
        // that does not pass an explicit profile (e.g. reset-prime, which is a control-point operation only).
        DfuProtocolKind.LEGACY ->
            LegacyDfuTransport(bleScanner, bleConnectionFactory, target, dispatchers.default, legacyProfile)

        DfuProtocolKind.SECURE -> SecureDfuTransport(bleScanner, bleConnectionFactory, target, dispatchers.default)
    }

    /**
     * Reboot a bootloader wedged in a stale DFU session. Connects a fresh transport (which is responsive before any
     * START) and issues RESET (0x06) via the bounded [DfuUploadTransport.abort], then closes the connection and waits
     * for the configured [RESET_PRIME_REBOOT_WAIT_MS] reboot/re-advertisement interval. Operational [Exception]s during
     * abort are best-effort (swallowed by [DfuUploadTransport.abort]); structured-concurrency cancellation and [Error]
     * subtypes propagate per the abort contract.
     */
    private suspend fun resetStaleBootloader(protocol: DfuProtocolKind, target: String) {
        Logger.i { "DFU: reset-priming stale bootloader before retry" }
        val transport = createTransport(protocol, target)
        try {
            transport
                .connectToDfuMode()
                .onSuccess {
                    transport.abort()
                    Logger.i { "DFU: reset-prime RESET attempted; disconnecting and waiting for re-advertisement" }
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
        legacyProfile: LegacyDfuStreamProfile,
        updateState: (FirmwareUpdateState) -> Unit,
    ): DfuUploadResult {
        val transport: DfuUploadTransport = createTransport(protocol, target, legacyProfile)
        var completed = false
        var protocolEngaged = false
        var sessionFailure: Throwable? = null
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
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            sessionFailure = e
            return DfuUploadResult.Failure(e, protocolEngaged)
        } finally {
            cleanupDfuSessionTransport(transport, completed, sessionFailure)
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
