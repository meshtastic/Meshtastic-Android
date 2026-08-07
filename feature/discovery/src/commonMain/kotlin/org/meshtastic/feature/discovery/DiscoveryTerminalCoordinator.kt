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
    val generateAi: Boolean,
)

/** Serializes terminal scan cleanup and keeps persistence separate from radio restoration ownership. */
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
    )

    private val mutex = Mutex()
    private var terminalCompletion: Deferred<DiscoveryScanState.CompletionOutcome>? = null

    suspend fun resetForScan(): Boolean = mutex.withLock {
        if (terminalCompletion?.isCompleted == false) {
            Logger.w { "DiscoveryScanEngine: refusing reset while terminal cleanup is active" }
            false
        } else {
            terminalCompletion = null
            true
        }
    }

    suspend fun complete(
        request: DiscoveryTerminalRequest,
        beforeFinalize: suspend () -> Unit = {},
        generateAi: suspend () -> Unit = {},
    ): DiscoveryScanState.CompletionOutcome {
        val selection = terminalTask(request, beforeFinalize, generateAi)
        if (!selection.accepted) {
            Logger.w {
                "DiscoveryScanEngine: terminal request ${request.pendingStatus} joined an active terminal cleanup; " +
                    "its request-specific callbacks were not accepted"
            }
        }
        return selection.task.await()
    }

    private suspend fun terminalTask(
        request: DiscoveryTerminalRequest,
        beforeFinalize: suspend () -> Unit,
        generateAi: suspend () -> Unit,
    ): TerminalTaskSelection {
        val selection =
            mutex.withLock {
                val existing = terminalCompletion
                if (existing != null) {
                    TerminalTaskSelection(existing, accepted = false)
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
                    TerminalTaskSelection(newTask, accepted = true)
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
        cancelScan()
        val persistedStatus =
            if (request.restorePlan == null) {
                finalStatusForPendingRestore(request.pendingStatus, default = request.pendingStatus)
            } else {
                request.pendingStatus
            }
        val persistenceSucceeded =
            try {
                val beforeFinalizeSucceeded =
                    runBestEffort("dwell persistence failed during terminal cleanup", beforeFinalize)
                val terminalPersistSucceeded = persistTerminalSession(request.sessionId, persistedStatus)
                beforeFinalizeSucceeded && terminalPersistSucceeded
            } finally {
                // Preserve aggregate-before-final-status ordering, but make restore scheduling cancellation-safe so a
                // database cancellation cannot strand the radio on its scan configuration.
                withContext(NonCancellable) { request.restorePlan?.let { homeRestorer.schedule(it) } }
            }

        var outcome = outcomeAfterPersistence(request.outcome, persistenceSucceeded)

        if (request.awaitRestore && request.restorePlan != null && !homeRestorer.awaitForeground(request.restorePlan)) {
            outcome = DiscoveryScanState.CompletionOutcome.Failed
            homeRestorer.updateFinalStatus(request.sessionId, DiscoverySessionStatus.FAILED)
            persistPendingRestoreStatus(request.sessionId, DiscoverySessionStatus.RESTORE_PENDING_FAILED)
        }
        if (request.generateAi && outcome == DiscoveryScanState.CompletionOutcome.Success) {
            runBestEffort("AI summary generation failed", generateAi)
        }
        return outcome
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
            buildUpdatedSession(sessionId, status)?.let { updated ->
                discoveryDao.updateSession(updated)
                updated
            }
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
        updated?.let(onSessionUpdated)
        return updated != null
    }

    private suspend fun buildUpdatedSession(sessionId: Long, status: String): DiscoverySessionEntity? {
        val presetResults = discoveryDao.getPresetResults(sessionId)
        val session = discoveryDao.getSession(sessionId) ?: return null
        val avgChannelUtilization =
            presetResults
                .filter { it.uniqueNodes > 0 }
                .map { it.avgChannelUtilization }
                .average()
                .takeIf { !it.isNaN() } ?: 0.0
        return session.copy(
            totalUniqueNodes = discoveryDao.getUniqueNodeCount(sessionId),
            totalDwellSeconds = presetResults.sumOf { it.dwellDurationSeconds },
            totalMessages = presetResults.sumOf { it.messageCount },
            totalSensorPackets = presetResults.sumOf { it.sensorPacketCount },
            furthestNodeDistance = discoveryDao.getMaxDistance(sessionId) ?: 0.0,
            avgChannelUtilization = avgChannelUtilization,
            completionStatus = status,
        )
    }
}
