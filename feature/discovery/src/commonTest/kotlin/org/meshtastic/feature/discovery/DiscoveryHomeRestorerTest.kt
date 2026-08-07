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

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.testing.FakeMeshPrefs
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DiscoveryHomeRestorerTest {
    private fun applicationScope(scope: CoroutineScope): ApplicationCoroutineScope =
        object : ApplicationCoroutineScope {
            override val coroutineContext = scope.coroutineContext
        }

    @Test
    fun supersededRestoreReturnsFalseToForegroundWaiterWithoutCancellingIt() = runTest {
        val firstDevice = "x:FIRST"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(firstDevice) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Disconnected) }
        val appScope = applicationScope(backgroundScope)
        val restorer =
            DiscoveryHomeRestorer(
                radioController = FakeRadioController(),
                serviceRepository = serviceRepository,
                discoveryDao = mock<DiscoveryDao>(MockMode.autofill),
                applicationScope = appScope,
                meshPrefs = meshPrefs,
            )
        val plan =
            DiscoveryHomeRestorePlan(
                sessionId = 1L,
                deviceAddress = firstDevice,
                loraConfig = Config.LoRaConfig(use_preset = true),
                primaryChannel = null,
                restorePrimaryChannel = false,
                finalStatus = DiscoverySessionStatus.COMPLETE,
            )

        val foregroundWaiter = async { restorer.awaitForeground(plan) }
        runCurrent()
        assertFalse(foregroundWaiter.isCompleted, "the restore should be waiting for the disconnected device")

        assertTrue(restorer.awaitBeforeScan("x:SECOND"), "a different device may proceed after superseding the restore")
        assertFalse(
            foregroundWaiter.await(),
            "superseding a restore must return false instead of cancelling its waiter",
        )
    }

    @Test
    fun selectedDeviceChangeWakesDisconnectedRestoreWaiter() = runTest {
        val device = "x:FIRST"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Disconnected) }
        val restorer =
            DiscoveryHomeRestorer(
                radioController = FakeRadioController(),
                serviceRepository = serviceRepository,
                discoveryDao = mock<DiscoveryDao>(MockMode.autofill),
                applicationScope = applicationScope(backgroundScope),
                meshPrefs = meshPrefs,
            )
        val plan =
            DiscoveryHomeRestorePlan(
                sessionId = 1L,
                deviceAddress = device,
                loraConfig = Config.LoRaConfig(use_preset = true),
                primaryChannel = null,
                restorePrimaryChannel = false,
                finalStatus = DiscoverySessionStatus.COMPLETE,
            )

        val result = restorer.schedule(plan)
        runCurrent()
        assertFalse(result.isCompleted)

        meshPrefs.setDeviceAddress("x:SECOND")
        runCurrent()

        assertFalse(result.await())
    }

    @Test
    fun finalStatusMappingPreservesTerminalIntentAndDefaultsRecoveredStates() {
        assertEquals(
            DiscoverySessionStatus.COMPLETE,
            finalStatusForPendingRestore(DiscoverySessionStatus.RESTORE_PENDING_COMPLETE),
        )
        assertEquals(
            DiscoverySessionStatus.STOPPED,
            finalStatusForPendingRestore(DiscoverySessionStatus.RESTORE_PENDING_STOPPED),
        )
        assertEquals(
            DiscoverySessionStatus.FAILED,
            finalStatusForPendingRestore(DiscoverySessionStatus.RESTORE_PENDING_FAILED),
        )
        assertEquals(DiscoverySessionStatus.RESTORED, finalStatusForPendingRestore(DiscoverySessionStatus.IN_PROGRESS))
        assertEquals(DiscoverySessionStatus.RESTORED, finalStatusForPendingRestore(DiscoverySessionStatus.INTERRUPTED))
        assertEquals(
            DiscoverySessionStatus.FAILED,
            finalStatusForPendingRestore(DiscoverySessionStatus.INTERRUPTED, default = DiscoverySessionStatus.FAILED),
        )
    }

    @Test
    fun repeatedScheduleForSameActiveSessionIsIdempotent() = runTest {
        val device = "x:SAME"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Disconnected) }
        val appScope = applicationScope(backgroundScope)
        val restorer =
            DiscoveryHomeRestorer(
                radioController = FakeRadioController(),
                serviceRepository = serviceRepository,
                discoveryDao = mock<DiscoveryDao>(MockMode.autofill),
                applicationScope = appScope,
                meshPrefs = meshPrefs,
            )
        val plan =
            DiscoveryHomeRestorePlan(
                sessionId = 7L,
                deviceAddress = device,
                loraConfig = Config.LoRaConfig(use_preset = true),
                primaryChannel = null,
                restorePrimaryChannel = false,
                finalStatus = DiscoverySessionStatus.COMPLETE,
            )

        val first = restorer.schedule(plan)
        val second = restorer.schedule(plan)

        assertSame(first, second)
        first.cancel()
    }

    @Test
    fun schedulingDifferentSessionCancelsAndReplacesPendingRestore() = runTest {
        val device = "x:SUPERSEDE"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Disconnected) }
        val restorer =
            DiscoveryHomeRestorer(
                radioController = FakeRadioController(),
                serviceRepository = serviceRepository,
                discoveryDao = mock<DiscoveryDao>(MockMode.autofill),
                applicationScope = applicationScope(backgroundScope),
                meshPrefs = meshPrefs,
            )
        val firstPlan =
            DiscoveryHomeRestorePlan(
                sessionId = 1L,
                deviceAddress = device,
                loraConfig = Config.LoRaConfig(use_preset = true),
                primaryChannel = null,
                restorePrimaryChannel = false,
                finalStatus = DiscoverySessionStatus.COMPLETE,
            )

        val first = restorer.schedule(firstPlan)
        val second = restorer.schedule(firstPlan.copy(sessionId = 2L))

        assertTrue(first.isCancelled, "the superseded session must release its restore ownership")
        assertNotSame(first, second)
        second.cancel()
    }

    @Test
    fun unsatisfiablePrimaryChannelRestoreStopsWithoutRetrying() = runTest {
        val device = "x:INVALID"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val radioController = FakeRadioController()
        val restorer =
            DiscoveryHomeRestorer(
                radioController = radioController,
                serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) },
                discoveryDao = mock<DiscoveryDao>(MockMode.autofill),
                applicationScope = applicationScope(backgroundScope),
                meshPrefs = meshPrefs,
            )
        val plan =
            DiscoveryHomeRestorePlan(
                sessionId = 1L,
                deviceAddress = device,
                loraConfig = Config.LoRaConfig(use_preset = true),
                primaryChannel = null,
                restorePrimaryChannel = true,
                finalStatus = DiscoverySessionStatus.FAILED,
            )

        val result = restorer.schedule(plan)
        runCurrent()

        assertFalse(result.await())
        assertTrue(radioController.configWrites.isEmpty())
    }

    @Test
    fun queueRejectionRetriesWhileTheSameDeviceRemainsConnected() = runTest {
        val device = "x:RETRY"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioController = FakeRadioController().apply { rejectLocalConfigWritesRemaining = 1 }
        val discoveryDao = SharedInMemoryDiscoveryDao()
        val loraConfig = Config.LoRaConfig(use_preset = true)
        val sessionId =
            discoveryDao.insertSession(
                DiscoverySessionEntity(
                    timestamp = 1L,
                    presetsScanned = "LONG_FAST",
                    homePreset = "LONG_FAST",
                    completionStatus = DiscoverySessionStatus.RESTORE_PENDING_COMPLETE,
                    deviceAddress = device,
                    homeLoraConfig = loraConfig,
                ),
            )
        val restorer =
            DiscoveryHomeRestorer(
                radioController = radioController,
                serviceRepository = serviceRepository,
                discoveryDao = discoveryDao,
                applicationScope = applicationScope(backgroundScope),
                meshPrefs = meshPrefs,
            )
        val plan =
            DiscoveryHomeRestorePlan(
                sessionId = sessionId,
                deviceAddress = device,
                loraConfig = loraConfig,
                primaryChannel = null,
                restorePrimaryChannel = false,
                finalStatus = DiscoverySessionStatus.COMPLETE,
            )

        val result = restorer.schedule(plan)
        runCurrent()
        assertFalse(result.isCompleted)
        assertEquals(0, radioController.configWrites.size)

        advanceTimeBy(DiscoveryHomeRestorer.RETRY_DELAY_MS)
        runCurrent()
        assertEquals(1, radioController.configWrites.size)
        advanceTimeBy(DiscoveryHomeRestorer.POST_RESTORE_SETTLE_DELAY_MS)
        runCurrent()

        assertTrue(result.await())
        assertEquals(DiscoverySessionStatus.COMPLETE, discoveryDao.getSession(sessionId)?.completionStatus)
    }

    @Test
    fun updateFinalStatusChangesTheTerminalStatusPublishedByARunningRestore() = runTest {
        val device = "x:STATUS"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioController = FakeRadioController()
        val discoveryDao = SharedInMemoryDiscoveryDao()
        val loraConfig = Config.LoRaConfig(use_preset = true)
        val sessionId =
            discoveryDao.insertSession(
                DiscoverySessionEntity(
                    timestamp = 1L,
                    presetsScanned = "LONG_FAST",
                    homePreset = "LONG_FAST",
                    completionStatus = DiscoverySessionStatus.RESTORE_PENDING_COMPLETE,
                    deviceAddress = device,
                    homeLoraConfig = loraConfig,
                ),
            )
        val restorer =
            DiscoveryHomeRestorer(
                radioController = radioController,
                serviceRepository = serviceRepository,
                discoveryDao = discoveryDao,
                applicationScope = applicationScope(backgroundScope),
                meshPrefs = meshPrefs,
            )
        val plan =
            DiscoveryHomeRestorePlan(
                sessionId = sessionId,
                deviceAddress = device,
                loraConfig = loraConfig,
                primaryChannel = null,
                restorePrimaryChannel = false,
                finalStatus = DiscoverySessionStatus.COMPLETE,
            )

        val result = restorer.schedule(plan)
        runCurrent()
        assertFalse(result.isCompleted)
        restorer.updateFinalStatus(sessionId, DiscoverySessionStatus.FAILED)
        advanceTimeBy(DiscoveryHomeRestorer.POST_RESTORE_SETTLE_DELAY_MS)
        runCurrent()

        assertTrue(result.await())
        assertEquals(DiscoverySessionStatus.FAILED, discoveryDao.getSession(sessionId)?.completionStatus)
    }
}
