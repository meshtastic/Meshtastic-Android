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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.testing.FakeMeshPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoveryTerminalCoordinatorTest {
    /** Adapts the finite test scope to the application-scope contract for coordinator-only tests. */
    private fun applicationScope(scope: CoroutineScope): ApplicationCoroutineScope =
        object : ApplicationCoroutineScope {
            override val coroutineContext = scope.coroutineContext
        }

    @Test
    @Suppress("LongMethod")
    fun joinedRestoreFailurePublishesTheDowngradedOutcome() = runTest {
        val device = "x:DEVICE"
        val discoveryDao = SharedInMemoryDiscoveryDao()
        val sessionId =
            discoveryDao.insertSession(
                DiscoverySessionEntity(
                    timestamp = 1L,
                    presetsScanned = "SHORT_FAST",
                    homePreset = "LONG_FAST",
                    completionStatus = DiscoverySessionStatus.IN_PROGRESS,
                    deviceAddress = device,
                ),
            )
        // Model a device switch so the shared restore resolves false immediately; retry policy is tested separately.
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress("x:OTHER") }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val appScope = applicationScope(this)
        val radioController = DiscoveryTestRadioController().apply { selectedDeviceAddress = device }
        val homeRestorer =
            DiscoveryHomeRestorer(
                radioController = radioController,
                serviceRepository = serviceRepository,
                discoveryDao = discoveryDao,
                applicationScope = appScope,
                meshPrefs = meshPrefs,
            )
        val publishedOutcomes = mutableListOf<DiscoveryScanState.CompletionOutcome>()
        val coordinator =
            DiscoveryTerminalCoordinator(
                discoveryDao = discoveryDao,
                homeRestorer = homeRestorer,
                applicationScope = appScope,
                onSessionUpdated = {},
                onTerminalCompleted = { publishedOutcomes += it },
                cancelScan = {},
            )
        val restorePlan =
            DiscoveryHomeRestorePlan(
                sessionId = sessionId,
                deviceAddress = device,
                loraConfig = Config.LoRaConfig(use_preset = true),
                primaryChannel = null,
                restorePrimaryChannel = false,
                finalStatus = DiscoverySessionStatus.STOPPED,
            )
        val ownerRequest =
            DiscoveryTerminalRequest(
                sessionId = sessionId,
                restorePlan = restorePlan,
                pendingStatus = DiscoverySessionStatus.RESTORE_PENDING_STOPPED,
                outcome = DiscoveryScanState.CompletionOutcome.Cancelled,
                awaitRestore = false,
                shouldGenerateAi = false,
            )
        val joinedRequest =
            ownerRequest.copy(
                pendingStatus = DiscoverySessionStatus.RESTORE_PENDING_COMPLETE,
                outcome = DiscoveryScanState.CompletionOutcome.Success,
                awaitRestore = true,
            )

        val owner = async(start = CoroutineStart.UNDISPATCHED) { coordinator.complete(ownerRequest) }
        val joined = async(start = CoroutineStart.UNDISPATCHED) { coordinator.complete(joinedRequest) }
        runCurrent()

        assertTrue(owner.isCompleted, "terminal owner did not complete")
        assertEquals(DiscoveryScanState.CompletionOutcome.Cancelled, owner.await())
        assertTrue(joined.isCompleted, "joined restore follow-up did not complete")
        assertEquals(DiscoveryScanState.CompletionOutcome.Failed, joined.await())
        assertEquals(
            listOf(DiscoveryScanState.CompletionOutcome.Cancelled, DiscoveryScanState.CompletionOutcome.Failed),
            publishedOutcomes,
        )
        assertEquals(
            DiscoverySessionStatus.RESTORE_PENDING_FAILED,
            discoveryDao.getSession(sessionId)?.completionStatus,
        )
    }

    @Test
    @Suppress("LongMethod")
    fun joinedOutcomeDoesNotOverwriteStateAfterTerminalReset() = runTest {
        val discoveryDao = SharedInMemoryDiscoveryDao()
        val appScope = applicationScope(this)
        val sessionId =
            discoveryDao.insertSession(
                DiscoverySessionEntity(
                    timestamp = 1L,
                    presetsScanned = "SHORT_FAST",
                    homePreset = "LONG_FAST",
                    completionStatus = DiscoverySessionStatus.IN_PROGRESS,
                ),
            )
        val homeRestorer =
            DiscoveryHomeRestorer(
                radioController = DiscoveryTestRadioController(),
                serviceRepository = FakeServiceRepository(),
                discoveryDao = discoveryDao,
                applicationScope = appScope,
                meshPrefs = FakeMeshPrefs(),
            )
        val publishedOutcomes = mutableListOf<DiscoveryScanState.CompletionOutcome>()
        val coordinator =
            DiscoveryTerminalCoordinator(
                discoveryDao = discoveryDao,
                homeRestorer = homeRestorer,
                applicationScope = appScope,
                onSessionUpdated = {},
                onTerminalCompleted = { publishedOutcomes += it },
                cancelScan = {},
            )
        val joinedEntered = CompletableDeferred<Unit>()
        val releaseJoined = CompletableDeferred<Unit>()
        val ownerRequest =
            DiscoveryTerminalRequest(
                sessionId = sessionId,
                restorePlan = null,
                pendingStatus = DiscoverySessionStatus.COMPLETE,
                outcome = DiscoveryScanState.CompletionOutcome.Success,
                awaitRestore = false,
                shouldGenerateAi = false,
            )
        val joinedRequest = ownerRequest.copy(pendingStatus = DiscoverySessionStatus.FAILED)

        val owner = async(start = CoroutineStart.UNDISPATCHED) { coordinator.complete(ownerRequest) }
        val joined =
            async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.complete(
                    joinedRequest,
                    beforeFinalize = {
                        joinedEntered.complete(Unit)
                        releaseJoined.await()
                        error("database unavailable")
                    },
                )
            }
        runCurrent()

        assertTrue(joinedEntered.isCompleted, "joined follow-up did not enter after owner completion")
        assertTrue(owner.isCompleted, "terminal owner did not complete before reset")
        assertEquals(DiscoveryScanState.CompletionOutcome.Success, owner.await())
        assertTrue(coordinator.resetForScan())
        releaseJoined.complete(Unit)
        runCurrent()

        assertTrue(joined.isCompleted, "joined follow-up did not complete after release")
        assertEquals(DiscoveryScanState.CompletionOutcome.Failed, joined.await())
        assertEquals(listOf(DiscoveryScanState.CompletionOutcome.Success), publishedOutcomes)
    }
}
