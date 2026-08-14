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
package org.meshtastic.feature.discovery

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal data class DiscoveryHomeRestorePlan(
    val sessionId: Long,
    val deviceAddress: String?,
    val loraConfig: Config.LoRaConfig,
    val primaryChannel: ChannelSettings?,
    val restorePrimaryChannel: Boolean,
    val finalStatus: String,
)

private fun DiscoveryHomeRestorePlan.matchesDevice(deviceAddress: String?): Boolean =
    this.deviceAddress == deviceAddress

internal fun finalStatusForPendingRestore(
    completionStatus: String,
    default: String = DiscoverySessionStatus.RESTORED,
): String = when (completionStatus) {
    DiscoverySessionStatus.RESTORE_PENDING_STOPPED -> DiscoverySessionStatus.STOPPED
    DiscoverySessionStatus.RESTORE_PENDING_FAILED -> DiscoverySessionStatus.FAILED
    DiscoverySessionStatus.RESTORE_PENDING_COMPLETE -> DiscoverySessionStatus.COMPLETE
    else -> default
}

private suspend fun awaitRestoreResult(result: Deferred<Boolean>, timeout: kotlin.time.Duration): Boolean {
    val completed =
        withTimeoutOrNull(timeout) {
            // A superseded restore cancels its Deferred while the foreground waiter remains valid. Inspect that
            // cancellation explicitly so only cancellation of this waiting coroutine propagates.
            val attempt = runCatching { result.await() }
            val failure = attempt.exceptionOrNull()
            when {
                failure == null -> attempt.getOrDefault(false)

                failure is CancellationException -> {
                    currentCoroutineContext().ensureActive()
                    false
                }

                else -> {
                    Logger.w(failure) { "DiscoveryScanEngine: awaited home restore failed" }
                    false
                }
            }
        }
    return completed == true
}

internal suspend fun awaitForegroundRestore(result: Deferred<Boolean>): Boolean =
    awaitRestoreResult(result, DiscoveryHomeRestorer.FOREGROUND_RESTORE_TIMEOUT_MS.milliseconds)

/** Owns process-lifetime restoration of the radio configuration captured before a discovery scan. */
@Suppress("TooManyFunctions")
internal class DiscoveryHomeRestorer(
    private val radioController: RadioController,
    private val serviceRepository: ServiceRepository,
    private val discoveryDao: DiscoveryDao,
    private val applicationScope: ApplicationCoroutineScope,
    private val meshPrefs: MeshPrefs,
) {
    /** Guarded by [pendingMutex]. Retains a completed restore long enough to correct a racing terminal outcome. */
    private class RestoreState(var finalStatus: String, var persistedFinalStatus: String? = null) {
        val completedSuccessfully = MutableStateFlow<Boolean?>(null)
    }

    private data class RestoreRetryState(
        var writeAttempts: Int = 0,
        var ownershipRechecksRemaining: Int = MAX_OWNERSHIP_RECHECKS,
        var writeRetryDelayMs: Long = RETRY_DELAY_MS,
    )

    private data class PendingRestore(
        val plan: DiscoveryHomeRestorePlan,
        val result: Deferred<Boolean>,
        val state: RestoreState,
    )

    // Lock order invariant: acquire persistenceMutex before pendingMutex. Never acquire persistenceMutex while holding
    // pendingMutex. pendingRestoreSnapshot mirrors the guarded reference for non-suspending admission rechecks only.
    private val pendingMutex = Mutex()
    private val persistenceMutex = Mutex()
    private var pendingRestore: PendingRestore? = null
    private val pendingRestoreSnapshot = MutableStateFlow<PendingRestore?>(null)

    /** Caller must hold [pendingMutex]. */
    private fun publishPendingRestore(value: PendingRestore?) {
        pendingRestore = value
        pendingRestoreSnapshot.value = value
    }

    /** A same-device scan cannot retune until a previously scheduled home restore has succeeded. */
    suspend fun awaitBeforeScan(deviceAddress: String?): Boolean {
        val pending = pendingMutex.withLock { pendingRestore }
        return when {
            pending == null -> true

            pending.plan.deviceAddress != deviceAddress -> {
                pending.result.cancel()
                pendingMutex.withLock { if (pendingRestore === pending) publishPendingRestore(null) }
                true
            }

            else -> {
                if (!pending.result.isCompleted) {
                    Logger.i { "DiscoveryScanEngine: waiting for pending home restore before starting a new scan" }
                }
                val restored = awaitRestoreResult(pending.result, START_WAIT_TIMEOUT)
                if (!restored) {
                    if (pending.result.isCompleted) {
                        Logger.e {
                            "DiscoveryScanEngine: previous home restore did not complete successfully; " +
                                "refusing a same-device scan"
                        }
                    } else {
                        Logger.w { "DiscoveryScanEngine: home restore is still pending; deferring new scan" }
                    }
                }
                restored
            }
        }
    }

    /**
     * Non-suspending scan-engine recheck used while its mutex prevents a new recovery restore from racing admission. A
     * completed restore remains blocking unless it completed successfully.
     */
    fun hasUnsatisfiedRestoreFor(deviceAddress: String?): Boolean {
        val pending = pendingRestoreSnapshot.value?.takeIf { it.plan.deviceAddress == deviceAddress }
        return pending?.let { !it.result.isCompleted || it.state.completedSuccessfully.value != true } ?: false
    }

    /** Registers a restore in the application scope. Repeated scheduling of the same active plan is idempotent. */
    suspend fun schedule(plan: DiscoveryHomeRestorePlan): Deferred<Boolean> {
        var superseded: PendingRestore? = null
        var created = false
        val pending =
            pendingMutex.withLock {
                val existing = pendingRestore
                if (
                    existing != null &&
                    !existing.result.isCompleted &&
                    existing.plan.sessionId == plan.sessionId &&
                    existing.plan.deviceAddress == plan.deviceAddress
                ) {
                    existing
                } else {
                    superseded = existing?.takeUnless { it.result.isCompleted }
                    val state = RestoreState(plan.finalStatus)
                    val result =
                        applicationScope.async(start = CoroutineStart.LAZY) {
                            restoreUntilComplete(plan, state).also { state.completedSuccessfully.value = it }
                        }
                    PendingRestore(plan, result, state).also {
                        publishPendingRestore(it)
                        created = true
                    }
                }
            }
        superseded?.result?.cancel()
        if (created) {
            pending.result.invokeOnCompletion { cause ->
                if (cause != null && cause !is CancellationException) {
                    Logger.e(cause) { "DiscoveryScanEngine: background home restore failed unexpectedly" }
                }
            }
            pending.result.start()
        }
        return pending.result
    }

    /** Changes the status a running or just-completed restore publishes, correcting a racing terminal write. */
    suspend fun updateFinalStatus(sessionId: Long, finalStatus: String) {
        val pending =
            pendingMutex.withLock {
                val current = pendingRestore?.takeIf { it.plan.sessionId == sessionId } ?: return@withLock null
                current.state.finalStatus = finalStatus
                current
            } ?: return
        val correction =
            persistenceMutex.withLock {
                val shouldCorrect =
                    pendingMutex.withLock {
                        pendingRestore === pending &&
                            pending.state.finalStatus == finalStatus &&
                            pending.state.persistedFinalStatus?.let { it != finalStatus } == true
                    }
                if (!shouldCorrect) return@withLock null

                val result = safeCatching { discoveryDao.updateSessionCompletionStatus(sessionId, finalStatus) }
                if (result.getOrNull() == 1) {
                    pendingMutex.withLock {
                        if (pendingRestore === pending) pending.state.persistedFinalStatus = finalStatus
                    }
                }
                result
            }
        val correctionFailure = correction?.exceptionOrNull()
        when {
            correctionFailure != null ->
                Logger.e(correctionFailure) {
                    "DiscoveryScanEngine: failed to correct the restored session's terminal status"
                }

            correction != null && correction.getOrNull() != 1 ->
                Logger.w { "DiscoveryScanEngine: terminal status correction did not update session $sessionId" }
        }
    }

    /** Gives normal scan completion a bounded foreground opportunity while the process-lifetime job keeps running. */
    suspend fun awaitForeground(plan: DiscoveryHomeRestorePlan): Boolean =
        awaitRestoreResult(schedule(plan), FOREGROUND_RESTORE_TIMEOUT_MS.milliseconds)

    /** Registers a persisted interrupted/pending session restore without waiting for it to finish. */
    suspend fun schedulePersistedSession(session: DiscoverySessionEntity): Deferred<Boolean>? {
        val loraConfig = session.homeLoraConfig ?: return null
        return schedule(
            DiscoveryHomeRestorePlan(
                sessionId = session.id,
                deviceAddress = session.deviceAddress,
                loraConfig = loraConfig,
                primaryChannel = session.homePrimaryChannel,
                restorePrimaryChannel = session.homePrimaryChannel != null,
                finalStatus = finalStatusForPendingRestore(session.completionStatus),
            ),
        )
    }

    private suspend fun restoreUntilComplete(plan: DiscoveryHomeRestorePlan, state: RestoreState): Boolean {
        if (plan.restorePrimaryChannel && plan.primaryChannel == null) {
            Logger.e {
                "DiscoveryScanEngine: primary-channel restore required but no channel captured " +
                    "for session ${plan.sessionId}; abandoning restore"
            }
            markUnrestorableAndReleaseBarrier(plan)
            return false
        }
        val retries = RestoreRetryState()
        var restored = false
        var shouldContinue = true
        while (
            currentCoroutineContext().isActive &&
            plan.matchesDevice(meshPrefs.deviceAddress.value) &&
            !restored &&
            shouldContinue
        ) {
            if (awaitConnected(plan)) {
                val attempt = safeCatching { applyHomeConfiguration(plan) }
                val failure = attempt.exceptionOrNull()
                restored = attempt.getOrDefault(false)
                shouldContinue =
                    when {
                        restored -> {
                            finalizeRecoveredSessionBestEffort(plan.sessionId, state)
                            false
                        }

                        failure == null -> retryAfterOwnershipRejection(plan, retries)

                        else -> retryAfterWriteFailure(plan, retries, failure)
                    }
            }
        }
        return restored
    }

    /** Gives transport and selected-device ownership a short window to converge without retrying forever. */
    private suspend fun retryAfterOwnershipRejection(
        plan: DiscoveryHomeRestorePlan,
        retries: RestoreRetryState,
    ): Boolean {
        if (retries.ownershipRechecksRemaining == MAX_OWNERSHIP_RECHECKS) {
            Logger.i {
                "DiscoveryScanEngine: home restore rejected by device ownership for session " +
                    "${plan.sessionId}; re-checking selection"
            }
        }
        val shouldRetry = retries.ownershipRechecksRemaining > 0
        if (shouldRetry) {
            retries.ownershipRechecksRemaining--
            delay(RETRY_DELAY_MS)
        } else {
            Logger.w {
                "DiscoveryScanEngine: home restore still lacks device ownership for session " +
                    "${plan.sessionId}; leaving it recoverable for a later connection change"
            }
        }
        return shouldRetry
    }

    /** Retries real radio writes only while this restore still owns the connected device. */
    private suspend fun retryAfterWriteFailure(
        plan: DiscoveryHomeRestorePlan,
        retries: RestoreRetryState,
        failure: Throwable,
    ): Boolean {
        val stillOwnsConnectedDevice =
            plan.matchesDevice(meshPrefs.deviceAddress.value) &&
                serviceRepository.connectionState.value is ConnectionState.Connected
        if (!stillOwnsConnectedDevice) return true

        retries.ownershipRechecksRemaining = MAX_OWNERSHIP_RECHECKS
        retries.writeAttempts++
        val attemptsExhausted = retries.writeAttempts >= MAX_RESTORE_ATTEMPTS
        Logger.w(failure) {
            if (attemptsExhausted) {
                "DiscoveryScanEngine: final home restore attempt failed"
            } else {
                "DiscoveryScanEngine: home restore attempt failed; retrying when available"
            }
        }
        return if (attemptsExhausted) {
            Logger.e {
                "DiscoveryScanEngine: home restore exhausted $MAX_RESTORE_ATTEMPTS attempts for session " +
                    "${plan.sessionId}; marking it unrestorable"
            }
            markUnrestorableAndReleaseBarrier(plan)
            false
        } else {
            delay(retries.writeRetryDelayMs)
            retries.writeRetryDelayMs =
                (retries.writeRetryDelayMs * RETRY_BACKOFF_MULTIPLIER).coerceAtMost(MAX_RETRY_DELAY_MS)
            true
        }
    }

    private suspend fun awaitConnected(plan: DiscoveryHomeRestorePlan): Boolean {
        if (!plan.matchesDevice(meshPrefs.deviceAddress.value)) return false
        if (serviceRepository.connectionState.value !is ConnectionState.Connected) {
            combine(serviceRepository.connectionState, meshPrefs.deviceAddress) { state, address ->
                state is ConnectionState.Connected || !plan.matchesDevice(address)
            }
                .first { it }
        }
        return plan.matchesDevice(meshPrefs.deviceAddress.value) &&
            serviceRepository.connectionState.value is ConnectionState.Connected
    }

    private suspend fun applyHomeConfiguration(plan: DiscoveryHomeRestorePlan): Boolean {
        val primaryChannel =
            plan.primaryChannel
                ?.takeIf { plan.restorePrimaryChannel }
                ?.let { Channel(index = 0, role = Channel.Role.PRIMARY, settings = it) }
        val restored =
            radioController.restoreLocalConfiguration(
                expectedDeviceAddress = plan.deviceAddress,
                config = Config(lora = plan.loraConfig),
                primaryChannel = primaryChannel,
            )
        if (!restored) return false
        Logger.i { "DiscoveryScanEngine: restored original LoRa config for session ${plan.sessionId}" }
        delay(POST_RESTORE_SETTLE_DELAY_MS)
        return true
    }

    private suspend fun finalizeRecoveredSessionBestEffort(sessionId: Long, state: RestoreState) {
        val result =
            persistenceMutex.withLock {
                val finalStatus = pendingMutex.withLock { state.finalStatus }
                val persistence = safeCatching {
                    discoveryDao.updateRecoverableSessionCompletionStatus(sessionId, finalStatus)
                }
                if (persistence.getOrNull() == 1) {
                    pendingMutex.withLock { state.persistedFinalStatus = finalStatus }
                }
                persistence
            }
        val failure = result.exceptionOrNull()
        if (failure != null) {
            Logger.e(failure) {
                "DiscoveryScanEngine: radio restored but terminal session persistence failed; keeping recovery row"
            }
        } else if (result.getOrNull() != 1) {
            Logger.w {
                "DiscoveryScanEngine: session $sessionId was no longer recoverable; terminal status not written"
            }
        }
    }

    /**
     * Makes an unrecoverable restore terminal in both durable and in-memory ownership. The admission barrier is
     * released only after the terminal status is persisted; if persistence fails, the recoverable row and barrier
     * remain available for a later recovery attempt.
     */
    private suspend fun markUnrestorableAndReleaseBarrier(plan: DiscoveryHomeRestorePlan) {
        if (!markUnrestorableBestEffort(plan.sessionId)) return
        pendingMutex.withLock { if (pendingRestore?.plan == plan) publishPendingRestore(null) }
    }

    private suspend fun markUnrestorableBestEffort(sessionId: Long): Boolean {
        val result =
            persistenceMutex.withLock {
                safeCatching {
                    discoveryDao.updateRecoverableSessionCompletionStatus(
                        sessionId,
                        DiscoverySessionStatus.UNRESTORABLE,
                    )
                }
            }
        val failure = result.exceptionOrNull()
        return when {
            failure != null -> {
                Logger.e(failure) { "DiscoveryScanEngine: failed to persist unrestorable session status" }
                false
            }

            result.getOrNull() != 1 -> {
                Logger.w {
                    "DiscoveryScanEngine: session $sessionId was no longer recoverable; unrestorable not written"
                }
                false
            }

            else -> true
        }
    }

    internal companion object {
        const val FOREGROUND_RESTORE_TIMEOUT_MS = 90_000L
        const val RETRY_DELAY_MS = 1_000L
        internal const val MAX_RETRY_DELAY_MS = 30_000L
        internal const val MAX_RESTORE_ATTEMPTS = 7
        internal const val MAX_OWNERSHIP_RECHECKS = 15
        internal const val RETRY_BACKOFF_MULTIPLIER = 2L
        const val POST_RESTORE_SETTLE_DELAY_MS = 3_000L
        private val START_WAIT_TIMEOUT = 15.seconds
    }
}
