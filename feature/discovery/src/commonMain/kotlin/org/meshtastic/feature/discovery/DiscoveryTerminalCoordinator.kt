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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus

internal data class DiscoveryTerminalRequest(
    val sessionId: Long,
    val restorePlan: DiscoveryHomeRestorePlan?,
    val pendingStatus: String,
    val outcome: DiscoveryScanState.CompletionOutcome,
    val awaitRestore: Boolean,
    val shouldGenerateAi: Boolean,
)

/** Serializes terminal scan cleanup and keeps persistence separate from radio restoration ownership. */
@Suppress("TooManyFunctions")
internal class DiscoveryTerminalCoordinator(
    private val discoveryDao: DiscoveryDao,
    private val homeRestorer: DiscoveryHomeRestorer,
    private val applicationScope: ApplicationCoroutineScope,
    private val onSessionUpdated: (DiscoverySessionEntity) -> Unit,
    private val onTerminalCompleted: (DiscoveryScanState.CompletionOutcome) -> Unit,
    private val cancelScan: suspend () -> Unit,
) {
    private data class TerminalTaskSelection(
        val task: Deferred<DiscoveryScanState.CompletionOutcome>,
        val accepted: Boolean,
        val ownerRequest: DiscoveryTerminalRequest,
    )

    private data class SessionAggregates(
        val totalUniqueNodes: Int,
        val totalDwellSeconds: Long,
        val totalMessages: Int,
        val totalSensorPackets: Int,
        val furthestNodeDistance: Double,
        val avgChannelUtilization: Double,
    )

    private val mutex = Mutex()
    private var terminalCompletion: Deferred<DiscoveryScanState.CompletionOutcome>? = null
    private var terminalRequest: DiscoveryTerminalRequest? = null
    private var terminalRestoreTask: Deferred<Boolean>? = null
    private var terminalRestoreSessionId: Long? = null

    suspend fun resetForScan(): Boolean = mutex.withLock {
        if (terminalCompletion?.isCompleted == false) {
            Logger.w { "DiscoveryScanEngine: refusing reset while terminal cleanup is active" }
            false
        } else {
            terminalCompletion = null
            terminalRequest = null
            terminalRestoreTask = null
            terminalRestoreSessionId = null
            true
        }
    }

    suspend fun complete(
        request: DiscoveryTerminalRequest,
        beforeFinalize: suspend () -> Unit = {},
        generateAi: suspend () -> Unit = {},
    ): DiscoveryScanState.CompletionOutcome {
        val selection = terminalTask(request, beforeFinalize, generateAi)
        val sharedOutcome = selection.task.await()
        // Joined callers with the same data-class value share the owner's work; changing request equality changes
        // this deduplication contract.
        if (selection.accepted || request == selection.ownerRequest) return sharedOutcome

        Logger.w {
            "DiscoveryScanEngine: terminal request ${request.pendingStatus} joined an active terminal cleanup; " +
                "running its required follow-up work after the shared cleanup"
        }
        val joinedOutcome = runJoinedWork(request, beforeFinalize, generateAi, sharedOutcome)
        publishJoinedOutcomeIfCurrent(selection.task, sharedOutcome, joinedOutcome)
        return joinedOutcome
    }

    private suspend fun terminalTask(
        request: DiscoveryTerminalRequest,
        beforeFinalize: suspend () -> Unit,
        generateAi: suspend () -> Unit,
    ): TerminalTaskSelection {
        val selection =
            mutex.withLock {
                val existing = terminalCompletion?.takeIf { !it.isCompleted }
                if (existing != null) {
                    TerminalTaskSelection(existing, accepted = false, ownerRequest = checkNotNull(terminalRequest))
                } else {
                    val newTask =
                        applicationScope.async(start = CoroutineStart.LAZY) {
                            var publishedOutcome = fallbackOutcome(request.outcome)
                            try {
                                runTerminalCleanup(request, beforeFinalize, generateAi).also { publishedOutcome = it }
                            } finally {
                                onTerminalCompleted(publishedOutcome)
                            }
                        }
                    terminalCompletion = newTask
                    terminalRequest = request
                    TerminalTaskSelection(newTask, accepted = true, ownerRequest = request)
                }
            }
        // The task can acquire the scan-engine mutex through beforeFinalize; never start it under this mutex.
        if (selection.accepted) selection.task.start()
        return selection
    }

    private fun fallbackOutcome(requested: DiscoveryScanState.CompletionOutcome): DiscoveryScanState.CompletionOutcome =
        if (requested == DiscoveryScanState.CompletionOutcome.Success) {
            DiscoveryScanState.CompletionOutcome.Failed
        } else {
            requested
        }

    private suspend fun runTerminalCleanup(
        request: DiscoveryTerminalRequest,
        beforeFinalize: suspend () -> Unit,
        generateAi: suspend () -> Unit,
    ): DiscoveryScanState.CompletionOutcome {
        val cancellationSucceeded = runBestEffort("scan cancellation failed during terminal cleanup", cancelScan)
        val persistedStatus =
            if (request.restorePlan == null) {
                finalStatusForPendingRestore(request.pendingStatus, default = request.pendingStatus)
            } else {
                request.pendingStatus
            }
        var restoreTask: Deferred<Boolean>? = null
        val persistenceSucceeded =
            try {
                val beforeFinalizeSucceeded =
                    runBestEffort("dwell persistence failed during terminal cleanup", beforeFinalize)
                val terminalPersistSucceeded = persistTerminalSession(request.sessionId, persistedStatus)
                cancellationSucceeded && beforeFinalizeSucceeded && terminalPersistSucceeded
            } finally {
                // Preserve aggregate-before-final-status ordering, but make restore scheduling cancellation-safe so a
                // database cancellation cannot strand the radio on its scan configuration. Capture the task once so
                // foreground waiting cannot accidentally schedule a second restore after a fast completion.
                withContext(NonCancellable) {
                    restoreTask = request.restorePlan?.let { homeRestorer.schedule(it) }
                    mutex.withLock {
                        terminalRestoreTask = restoreTask
                        terminalRestoreSessionId = request.sessionId.takeIf { restoreTask != null }
                    }
                }
            }

        var outcome = outcomeAfterPersistence(request.outcome, persistenceSucceeded)

        if (request.awaitRestore && restoreTask != null && !awaitForegroundRestore(checkNotNull(restoreTask))) {
            outcome = DiscoveryScanState.CompletionOutcome.Failed
            homeRestorer.updateFinalStatus(request.sessionId, DiscoverySessionStatus.FAILED)
            persistPendingRestoreStatus(request.sessionId, DiscoverySessionStatus.RESTORE_PENDING_FAILED)
        }
        if (request.shouldGenerateAi && outcome == DiscoveryScanState.CompletionOutcome.Success) {
            runBestEffort("AI summary generation failed", generateAi)
        }
        return outcome
    }

    private suspend fun runJoinedWork(
        request: DiscoveryTerminalRequest,
        beforeFinalize: suspend () -> Unit,
        generateAi: suspend () -> Unit,
        sharedOutcome: DiscoveryScanState.CompletionOutcome,
    ): DiscoveryScanState.CompletionOutcome {
        val beforeFinalizeSucceeded = runBestEffort("joined dwell persistence failed", beforeFinalize)
        val aggregateSucceeded = persistSessionAggregates(request.sessionId)
        var outcome = outcomeAfterPersistence(sharedOutcome, beforeFinalizeSucceeded && aggregateSucceeded)
        if (request.awaitRestore) {
            val restoreTask =
                mutex.withLock { terminalRestoreTask.takeIf { terminalRestoreSessionId == request.sessionId } }
            if (restoreTask != null && !awaitForegroundRestore(restoreTask)) {
                outcome = DiscoveryScanState.CompletionOutcome.Failed
                homeRestorer.updateFinalStatus(request.sessionId, DiscoverySessionStatus.FAILED)
                persistPendingRestoreStatus(request.sessionId, DiscoverySessionStatus.RESTORE_PENDING_FAILED)
            }
        }
        if (request.shouldGenerateAi && outcome == DiscoveryScanState.CompletionOutcome.Success) {
            runBestEffort("AI summary generation failed", generateAi)
        }
        return outcome
    }

    /** Republishes a corrected joined outcome only while the completed task still owns terminal state. */
    private suspend fun publishJoinedOutcomeIfCurrent(
        task: Deferred<DiscoveryScanState.CompletionOutcome>,
        sharedOutcome: DiscoveryScanState.CompletionOutcome,
        joinedOutcome: DiscoveryScanState.CompletionOutcome,
    ) {
        if (joinedOutcome == sharedOutcome) return
        mutex.withLock { if (terminalCompletion === task) onTerminalCompleted(joinedOutcome) }
    }

    private fun outcomeAfterPersistence(
        requested: DiscoveryScanState.CompletionOutcome,
        persistenceSucceeded: Boolean,
    ): DiscoveryScanState.CompletionOutcome =
        if (!persistenceSucceeded && requested == DiscoveryScanState.CompletionOutcome.Success) {
            DiscoveryScanState.CompletionOutcome.Failed
        } else {
            requested
        }

    private suspend fun runBestEffort(message: String, block: suspend () -> Unit): Boolean {
        val result = safeCatching { block() }
        val failure = result.exceptionOrNull()
        if (failure != null) {
            Logger.e(failure) { "DiscoveryScanEngine: $message" }
        }
        return failure == null
    }

    private suspend fun persistPendingRestoreStatus(sessionId: Long, status: String): Boolean {
        if (sessionId == 0L) return true
        return persistAndPublish("pending restore status persistence failed") {
            discoveryDao.updateRecoverableSessionCompletionStatus(sessionId, status)
            discoveryDao.getSession(sessionId)
        }
    }

    private suspend fun persistTerminalSession(sessionId: Long, status: String): Boolean {
        if (sessionId == 0L) return true
        return persistAndPublish("terminal session persistence failed") {
            val aggregates = calculateSessionAggregates(sessionId) ?: return@persistAndPublish null
            updateSessionAggregates(sessionId, aggregates)
            discoveryDao.updateRecoverableSessionCompletionStatus(sessionId, status)
            discoveryDao.getSession(sessionId)
        }
    }

    private suspend fun persistSessionAggregates(sessionId: Long): Boolean {
        if (sessionId == 0L) return true
        return persistAndPublish("terminal aggregate persistence failed") {
            val aggregates = calculateSessionAggregates(sessionId) ?: return@persistAndPublish null
            updateSessionAggregates(sessionId, aggregates)
            discoveryDao.getSession(sessionId)
        }
    }

    private suspend fun persistAndPublish(
        failureMessage: String,
        update: suspend () -> DiscoverySessionEntity?,
    ): Boolean {
        val result = safeCatching { update() }
        val failure = result.exceptionOrNull()
        if (failure != null) Logger.e(failure) { "DiscoveryScanEngine: $failureMessage" }
        val updated = result.getOrNull()
        if (failure == null && updated == null) {
            Logger.e { "DiscoveryScanEngine: $failureMessage; the session row was not found" }
        }
        updated?.let(onSessionUpdated)
        return updated != null
    }

    private suspend fun calculateSessionAggregates(sessionId: Long): SessionAggregates? {
        if (discoveryDao.getSession(sessionId) == null) return null
        val presetResults = discoveryDao.getPresetResults(sessionId)
        val avgChannelUtilization =
            presetResults
                .filter { it.uniqueNodes > 0 }
                .map { it.avgChannelUtilization }
                .average()
                .takeIf { !it.isNaN() } ?: 0.0
        return SessionAggregates(
            totalUniqueNodes = discoveryDao.getUniqueNodeCount(sessionId),
            totalDwellSeconds = presetResults.sumOf { it.dwellDurationSeconds },
            totalMessages = presetResults.sumOf { it.messageCount },
            totalSensorPackets = presetResults.sumOf { it.sensorPacketCount },
            furthestNodeDistance = discoveryDao.getMaxDistance(sessionId) ?: 0.0,
            avgChannelUtilization = avgChannelUtilization,
        )
    }

    private suspend fun updateSessionAggregates(sessionId: Long, aggregates: SessionAggregates) {
        discoveryDao.updateSessionAggregates(
            sessionId = sessionId,
            totalUniqueNodes = aggregates.totalUniqueNodes,
            totalDwellSeconds = aggregates.totalDwellSeconds,
            totalMessages = aggregates.totalMessages,
            totalSensorPackets = aggregates.totalSensorPackets,
            furthestNodeDistance = aggregates.furthestNodeDistance,
            avgChannelUtilization = aggregates.avgChannelUtilization,
        )
    }
}
