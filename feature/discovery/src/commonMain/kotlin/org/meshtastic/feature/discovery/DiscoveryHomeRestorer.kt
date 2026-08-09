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
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
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

private val FOREGROUND_RESTORE_TIMEOUT = 90.seconds

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
    awaitRestoreResult(result, FOREGROUND_RESTORE_TIMEOUT)

/** Owns process-lifetime restoration of the radio configuration captured before a discovery scan. */
internal class DiscoveryHomeRestorer(
    private val radioController: RadioController,
    private val serviceRepository: ServiceRepository,
    private val discoveryDao: DiscoveryDao,
    private val applicationScope: ApplicationCoroutineScope,
    private val meshPrefs: MeshPrefs,
) {
    private class RestoreStatus(initial: String) {
        private val status = atomic(initial)

        var value: String
            get() = status.value
            set(value) {
                status.value = value
            }
    }

    private data class PendingRestore(
        val plan: DiscoveryHomeRestorePlan,
        val result: Deferred<Boolean>,
        val finalStatus: RestoreStatus,
    )

    private val pendingMutex = Mutex()
    private var pendingRestore: PendingRestore? = null

    /** A same-device scan cannot retune until a previously scheduled home restore has completed. */
    suspend fun awaitBeforeScan(deviceAddress: String?): Boolean {
        val pending = pendingMutex.withLock { pendingRestore }
        return when {
            pending == null -> true

            pending.plan.deviceAddress != deviceAddress -> {
                pending.result.cancel()
                pendingMutex.withLock { if (pendingRestore === pending) pendingRestore = null }
                true
            }

            else -> {
                Logger.i { "DiscoveryScanEngine: waiting for pending home restore before starting a new scan" }
                val restored = awaitRestoreResult(pending.result, START_WAIT_TIMEOUT)
                if (!restored) Logger.w { "DiscoveryScanEngine: home restore is still pending; deferring new scan" }
                restored
            }
        }
    }

    /**
     * Non-suspending ownership check used while the scan engine holds its mutex. A concurrent mutation is a conflict.
     */
    fun hasPendingRestoreFor(deviceAddress: String?): Boolean {
        if (!pendingMutex.tryLock()) return true
        return try {
            pendingRestore?.let { !it.result.isCompleted && it.plan.deviceAddress == deviceAddress } == true
        } finally {
            pendingMutex.unlock()
        }
    }

    /** Registers a restore in the application scope. Repeated scheduling of the same active plan is idempotent. */
    suspend fun schedule(plan: DiscoveryHomeRestorePlan): Deferred<Boolean> {
        var superseded: PendingRestore? = null
        var created = false
        val pending =
            pendingMutex.withLock {
                val existing = pendingRestore
                if (existing != null && !existing.result.isCompleted && existing.plan.sessionId == plan.sessionId) {
                    existing
                } else {
                    superseded = existing?.takeUnless { it.result.isCompleted }
                    val finalStatus = RestoreStatus(plan.finalStatus)
                    val result =
                        applicationScope.async(start = CoroutineStart.LAZY) { restoreUntilComplete(plan, finalStatus) }
                    PendingRestore(plan, result, finalStatus).also {
                        pendingRestore = it
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
                applicationScope.launch {
                    pendingMutex.withLock {
                        if (pendingRestore === pending && pending.result.isCompleted) pendingRestore = null
                    }
                }
            }
            pending.result.start()
        }
        return pending.result
    }

    /** Changes the status a still-running restore will publish after it succeeds. */
    suspend fun updateFinalStatus(sessionId: Long, finalStatus: String) {
        pendingMutex.withLock {
            pendingRestore?.takeIf { it.plan.sessionId == sessionId }?.finalStatus?.value = finalStatus
        }
    }

    /** Gives normal scan completion a bounded foreground opportunity while the process-lifetime job keeps running. */
    suspend fun awaitForeground(plan: DiscoveryHomeRestorePlan): Boolean =
        awaitRestoreResult(schedule(plan), FOREGROUND_RESTORE_TIMEOUT)

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

    private suspend fun restoreUntilComplete(plan: DiscoveryHomeRestorePlan, finalStatus: RestoreStatus): Boolean {
        if (plan.restorePrimaryChannel && plan.primaryChannel == null) {
            Logger.e {
                "DiscoveryScanEngine: primary-channel restore required but no channel captured " +
                    "for session ${plan.sessionId}; abandoning restore"
            }
            return false
        }
        var restored = false
        var retryDelayMs = RETRY_DELAY_MS
        while (currentCoroutineContext().isActive && plan.matchesDevice(meshPrefs.deviceAddress.value) && !restored) {
            val attempt = safeCatching { awaitConnected(plan) && applyHomeConfiguration(plan) }
            val failure = attempt.exceptionOrNull()
            restored = attempt.getOrDefault(false)
            if (restored) {
                finalizeRecoveredSessionBestEffort(plan.sessionId, finalStatus.value)
            } else if (plan.matchesDevice(meshPrefs.deviceAddress.value)) {
                when (failure) {
                    is PacketQueueRejectedException -> {
                        Logger.w(failure) {
                            "DiscoveryScanEngine: home restore rejected; retrying when admission recovers"
                        }
                        if (serviceRepository.connectionState.value is ConnectionState.Connected) {
                            delay(retryDelayMs)
                        } else {
                            awaitConnected(plan)
                        }
                    }

                    null -> delay(retryDelayMs)

                    else -> {
                        Logger.w(failure) { "DiscoveryScanEngine: home restore failed; waiting for reconnect" }
                        awaitReconnect(plan)
                    }
                }
                retryDelayMs = (retryDelayMs * RETRY_BACKOFF_MULTIPLIER).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
        return restored
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

    private suspend fun awaitReconnect(plan: DiscoveryHomeRestorePlan) {
        if (serviceRepository.connectionState.value is ConnectionState.Connected) {
            combine(serviceRepository.connectionState, meshPrefs.deviceAddress) { state, address ->
                state !is ConnectionState.Connected || !plan.matchesDevice(address)
            }
                .first { it }
        }
        if (plan.matchesDevice(meshPrefs.deviceAddress.value)) awaitConnected(plan)
    }

    private suspend fun applyHomeConfiguration(plan: DiscoveryHomeRestorePlan): Boolean {
        var ownsDevice = plan.matchesDevice(meshPrefs.deviceAddress.value)
        if (ownsDevice && plan.restorePrimaryChannel) {
            val settings = checkNotNull(plan.primaryChannel) { "validated restore plan lost its primary channel" }
            radioController.setLocalChannel(Channel(index = 0, role = Channel.Role.PRIMARY, settings = settings))
            ownsDevice = plan.matchesDevice(meshPrefs.deviceAddress.value)
        }
        if (ownsDevice) {
            radioController.setLocalConfig(Config(lora = plan.loraConfig))
            Logger.i { "DiscoveryScanEngine: restored original LoRa config for session ${plan.sessionId}" }
            delay(POST_RESTORE_SETTLE_DELAY_MS)
            ownsDevice = plan.matchesDevice(meshPrefs.deviceAddress.value)
        }
        return ownsDevice
    }

    private suspend fun finalizeRecoveredSessionBestEffort(sessionId: Long, finalStatus: String) {
        val result = safeCatching { discoveryDao.updateRecoverableSessionCompletionStatus(sessionId, finalStatus) }
        val failure = result.exceptionOrNull()
        if (failure != null) {
            Logger.e(failure) {
                "DiscoveryScanEngine: radio restored but terminal session persistence failed; keeping recovery row"
            }
        }
    }

    internal companion object {
        const val RETRY_DELAY_MS = 1_000L
        internal const val MAX_RETRY_DELAY_MS = 30_000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2L
        const val POST_RESTORE_SETTLE_DELAY_MS = 3_000L
        private val START_WAIT_TIMEOUT = 15.seconds
    }
}
