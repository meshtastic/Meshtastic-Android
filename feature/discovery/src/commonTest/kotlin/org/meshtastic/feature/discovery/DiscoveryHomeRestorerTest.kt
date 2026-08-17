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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.database.dao.DiscoveryDao
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.database.entity.DiscoverySessionStatus
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.testing.FakeMeshPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.Channel
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
                radioController = DiscoveryTestRadioController(),
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
                radioController = DiscoveryTestRadioController(),
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
                radioController = DiscoveryTestRadioController(),
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
                radioController = DiscoveryTestRadioController(),
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
    fun sameSessionIdOnDifferentDeviceReplacesPendingRestore() = runTest {
        val firstDevice = "x:FIRST"
        val secondDevice = "x:SECOND"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(firstDevice) }
        val restorer =
            DiscoveryHomeRestorer(
                radioController = DiscoveryTestRadioController(),
                serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Disconnected) },
                discoveryDao = mock<DiscoveryDao>(MockMode.autofill),
                applicationScope = applicationScope(backgroundScope),
                meshPrefs = meshPrefs,
            )
        val firstPlan =
            DiscoveryHomeRestorePlan(
                sessionId = 7L,
                deviceAddress = firstDevice,
                loraConfig = Config.LoRaConfig(use_preset = true),
                primaryChannel = null,
                restorePrimaryChannel = false,
                finalStatus = DiscoverySessionStatus.COMPLETE,
            )

        val first = restorer.schedule(firstPlan)
        meshPrefs.setDeviceAddress(secondDevice)
        val second = restorer.schedule(firstPlan.copy(deviceAddress = secondDevice))

        assertTrue(first.isCancelled, "a reused database row id must not retain the previous radio's ownership")
        assertNotSame(first, second)
        second.cancel()
    }

    @Test
    fun interruptedRecoverySwitchesActiveWatcherToNewlySelectedDevice() = runTest {
        val firstDevice = "x:FIRST"
        val secondDevice = "x:SECOND"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(firstDevice) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val discoveryDao = SharedInMemoryDiscoveryDao()
        listOf(firstDevice to "FIRST_HOME", secondDevice to "SECOND_HOME").forEach { (device, homePreset) ->
            discoveryDao.insertSession(
                DiscoverySessionEntity(
                    timestamp = 1L,
                    presetsScanned = "SHORT_FAST",
                    homePreset = homePreset,
                    completionStatus = DiscoverySessionStatus.INTERRUPTED,
                    deviceAddress = device,
                    homeLoraConfig = Config.LoRaConfig(use_preset = true),
                ),
            )
        }
        val firstRestore = CompletableDeferred<Boolean>()
        val scheduledDevices = mutableListOf<String>()
        val recovery =
            DiscoveryInterruptedSessionRecovery(
                serviceRepository = serviceRepository,
                discoveryDao = discoveryDao,
                meshPrefs = meshPrefs,
                isScanActive = { false },
                scheduleRestoreIfIdle = { session ->
                    scheduledDevices += checkNotNull(session.deviceAddress)
                    if (session.deviceAddress == firstDevice) firstRestore else CompletableDeferred(true)
                },
            )
        val restoredPresets = mutableListOf<String>()
        val watcher = launch { recovery.watch { restoredPresets += it } }
        runCurrent()

        meshPrefs.setDeviceAddress(secondDevice)
        runCurrent()

        assertEquals(listOf(firstDevice, secondDevice), scheduledDevices)
        assertEquals(listOf("SECOND_HOME"), restoredPresets)
        watcher.cancel()
        firstRestore.cancel()
    }

    @Test
    fun discoveryTestRadioControllerRejectsStaleDeviceBeforeWriting() = runTest {
        val radioController = DiscoveryTestRadioController().apply { selectedDeviceAddress = "x:CURRENT" }

        val restored =
            radioController.restoreLocalConfiguration(
                expectedDeviceAddress = "x:STALE",
                config = Config(lora = Config.LoRaConfig(use_preset = true)),
                primaryChannel = Channel(index = 0),
            )

        assertFalse(restored)
        assertTrue(radioController.channelWrites.isEmpty())
        assertTrue(radioController.configWrites.isEmpty())
    }

    @Test
    fun unsatisfiablePrimaryChannelRestoreStopsWithoutRetrying() = runTest {
        val device = "x:INVALID"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val radioController = DiscoveryTestRadioController()
        val discoveryDao = SharedInMemoryDiscoveryDao()
        val sessionId =
            discoveryDao.insertSession(
                DiscoverySessionEntity(
                    timestamp = 1L,
                    presetsScanned = "LONG_FAST",
                    homePreset = "LONG_FAST",
                    completionStatus = DiscoverySessionStatus.RESTORE_PENDING_FAILED,
                    deviceAddress = device,
                ),
            )
        val restorer =
            DiscoveryHomeRestorer(
                radioController = radioController,
                serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) },
                discoveryDao = discoveryDao,
                applicationScope = applicationScope(backgroundScope),
                meshPrefs = meshPrefs,
            )
        val plan =
            DiscoveryHomeRestorePlan(
                sessionId = sessionId,
                deviceAddress = device,
                loraConfig = Config.LoRaConfig(use_preset = true),
                primaryChannel = null,
                restorePrimaryChannel = true,
                finalStatus = DiscoverySessionStatus.FAILED,
            )

        val result = restorer.schedule(plan)
        runCurrent()

        assertTrue(result.isCompleted, "an invalid restore plan must terminate without entering the retry loop")
        assertFalse(result.await())
        assertTrue(radioController.configWrites.isEmpty())
        assertEquals(DiscoverySessionStatus.UNRESTORABLE, discoveryDao.getSession(sessionId)?.completionStatus)
        assertFalse(restorer.hasUnsatisfiedRestoreFor(device))
    }

    @Test
    fun transientWriteFailuresRetryWithBackoffWhileTheSameDeviceRemainsConnected() = runTest {
        val device = "x:RETRY"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioController =
            DiscoveryTestRadioController().apply {
                selectedDeviceAddress = device
                failLocalConfigWritesRemaining = 2
            }
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
        assertEquals(0, radioController.configWrites.size)
        advanceTimeBy(DiscoveryHomeRestorer.RETRY_DELAY_MS)
        runCurrent()
        assertEquals(0, radioController.configWrites.size, "the second retry must use exponential backoff")
        advanceTimeBy(DiscoveryHomeRestorer.RETRY_DELAY_MS)
        runCurrent()
        assertEquals(1, radioController.configWrites.size)
        advanceTimeBy(DiscoveryHomeRestorer.POST_RESTORE_SETTLE_DELAY_MS)
        runCurrent()

        assertTrue(result.await())
        assertEquals(DiscoverySessionStatus.COMPLETE, discoveryDao.getSession(sessionId)?.completionStatus)
    }

    @Test
    fun updateFinalStatusChangesAndCorrectsTheTerminalStatusPublishedByARestore() = runTest {
        val device = "x:STATUS"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioController = DiscoveryTestRadioController().apply { selectedDeviceAddress = device }
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

        restorer.updateFinalStatus(sessionId, DiscoverySessionStatus.STOPPED)

        assertEquals(DiscoverySessionStatus.STOPPED, discoveryDao.getSession(sessionId)?.completionStatus)
    }

    @Test
    @Suppress("LongMethod")
    fun ownershipRejectionDoesNotConsumeTheRestoreRetryBudget() = runTest {
        val device = "x:OWNERSHIP-LAG"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioController =
            DiscoveryTestRadioController().apply {
                selectedDeviceAddress = device
                acceptConditionalRestore = false
            }
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
        val result =
            restorer.schedule(
                DiscoveryHomeRestorePlan(
                    sessionId = sessionId,
                    deviceAddress = device,
                    loraConfig = loraConfig,
                    primaryChannel = null,
                    restorePrimaryChannel = false,
                    finalStatus = DiscoverySessionStatus.COMPLETE,
                ),
            )

        advanceTimeBy(DiscoveryHomeRestorer.RETRY_DELAY_MS * (DiscoveryHomeRestorer.MAX_RESTORE_ATTEMPTS + 1))
        runCurrent()

        assertFalse(result.isCompleted, "ownership rejection must remain recoverable")
        assertEquals(0, radioController.localConfigWriteAttempts)
        assertEquals(
            DiscoverySessionStatus.RESTORE_PENDING_COMPLETE,
            discoveryDao.getSession(sessionId)?.completionStatus,
        )

        radioController.acceptConditionalRestore = true
        advanceTimeBy(DiscoveryHomeRestorer.RETRY_DELAY_MS)
        runCurrent()
        advanceTimeBy(DiscoveryHomeRestorer.POST_RESTORE_SETTLE_DELAY_MS)
        runCurrent()

        assertTrue(result.await())
        assertEquals(DiscoverySessionStatus.COMPLETE, discoveryDao.getSession(sessionId)?.completionStatus)
    }

    @Test
    @Suppress("LongMethod")
    fun persistentOwnershipRejectionStopsWithoutMarkingTheSessionUnrestorable() = runTest {
        val device = "x:OWNERSHIP-STUCK"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioController =
            DiscoveryTestRadioController().apply {
                selectedDeviceAddress = device
                acceptConditionalRestore = false
            }
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
                applicationScope = applicationScope(this),
                meshPrefs = meshPrefs,
            )

        val result =
            restorer.schedule(
                DiscoveryHomeRestorePlan(
                    sessionId = sessionId,
                    deviceAddress = device,
                    loraConfig = loraConfig,
                    primaryChannel = null,
                    restorePrimaryChannel = false,
                    finalStatus = DiscoverySessionStatus.COMPLETE,
                ),
            )

        advanceUntilIdle()

        assertTrue(result.isCompleted, "persistent ownership rejection must not leave an unbounded retry task")
        assertFalse(result.await())
        assertEquals(0, radioController.localConfigWriteAttempts)
        assertEquals(
            DiscoverySessionStatus.RESTORE_PENDING_COMPLETE,
            discoveryDao.getSession(sessionId)?.completionStatus,
            "ownership rejection must stay recoverable instead of consuming the radio-write failure budget",
        )
    }

    @Test
    @Suppress("LongMethod")
    fun transientDisconnectFailuresDoNotConsumeTheActiveRetryBudget() = runTest {
        val device = "x:DISCONNECT-RETRY"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioController =
            DiscoveryTestRadioController().apply {
                selectedDeviceAddress = device
                failLocalConfigWritesRemaining = Int.MAX_VALUE
                onLocalConfigWriteAttempt = { serviceRepository.setConnectionState(ConnectionState.Disconnected) }
            }
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
        val result =
            restorer.schedule(
                DiscoveryHomeRestorePlan(
                    sessionId = sessionId,
                    deviceAddress = device,
                    loraConfig = loraConfig,
                    primaryChannel = null,
                    restorePrimaryChannel = false,
                    finalStatus = DiscoverySessionStatus.COMPLETE,
                ),
            )

        runCurrent()
        repeat(DiscoveryHomeRestorer.MAX_RESTORE_ATTEMPTS + 1) {
            assertFalse(result.isCompleted, "disconnect failures must remain recoverable")
            assertEquals(
                DiscoverySessionStatus.RESTORE_PENDING_COMPLETE,
                discoveryDao.getSession(sessionId)?.completionStatus,
            )
            serviceRepository.setConnectionState(ConnectionState.Connected)
            runCurrent()
        }

        radioController.failLocalConfigWritesRemaining = 0
        radioController.onLocalConfigWriteAttempt = null
        serviceRepository.setConnectionState(ConnectionState.Connected)
        runCurrent()
        advanceTimeBy(DiscoveryHomeRestorer.POST_RESTORE_SETTLE_DELAY_MS)
        runCurrent()

        assertTrue(result.await())
        assertEquals(DiscoverySessionStatus.COMPLETE, discoveryDao.getSession(sessionId)?.completionStatus)
    }

    @Test
    fun permanentWriteFailureStopsAfterBoundedAttemptsAndMarksSessionUnrestorable() = runTest {
        val device = "x:UNRESTORABLE"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioController =
            DiscoveryTestRadioController().apply {
                selectedDeviceAddress = device
                failLocalConfigWritesRemaining = Int.MAX_VALUE
            }
        val discoveryDao = SharedInMemoryDiscoveryDao()
        val loraConfig = Config.LoRaConfig(use_preset = true)
        val sessionId =
            discoveryDao.insertSession(
                DiscoverySessionEntity(
                    timestamp = 1L,
                    presetsScanned = "LONG_FAST",
                    homePreset = "LONG_FAST",
                    completionStatus = DiscoverySessionStatus.RESTORE_PENDING_FAILED,
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

        val result =
            restorer.schedule(
                DiscoveryHomeRestorePlan(
                    sessionId = sessionId,
                    deviceAddress = device,
                    loraConfig = loraConfig,
                    primaryChannel = null,
                    restorePrimaryChannel = false,
                    finalStatus = DiscoverySessionStatus.FAILED,
                ),
            )
        var retryWindowMs = 0L
        var retryDelayMs = DiscoveryHomeRestorer.RETRY_DELAY_MS
        repeat(DiscoveryHomeRestorer.MAX_RESTORE_ATTEMPTS - 1) {
            retryWindowMs += retryDelayMs
            retryDelayMs =
                (retryDelayMs * DiscoveryHomeRestorer.RETRY_BACKOFF_MULTIPLIER).coerceAtMost(
                    DiscoveryHomeRestorer.MAX_RETRY_DELAY_MS,
                )
        }
        advanceTimeBy(retryWindowMs)
        runCurrent()

        assertFalse(result.await())
        assertEquals(DiscoveryHomeRestorer.MAX_RESTORE_ATTEMPTS, radioController.localConfigWriteAttempts)
        assertEquals(DiscoverySessionStatus.UNRESTORABLE, discoveryDao.getSession(sessionId)?.completionStatus)
        assertFalse(restorer.hasUnsatisfiedRestoreFor(device))
        assertTrue(
            restorer.awaitBeforeScan(device),
            "a durably unrestorable restore must not leave a process-lifetime same-device admission barrier",
        )
    }

    @Test
    fun unrestorablePersistenceFailureKeepsSameDeviceBarrierRecoverable() = runTest {
        val device = "x:UNRESTORABLE-PERSISTENCE"
        val meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(device) }
        val serviceRepository = FakeServiceRepository().apply { setConnectionState(ConnectionState.Connected) }
        val radioController =
            DiscoveryTestRadioController().apply {
                selectedDeviceAddress = device
                failLocalConfigWritesRemaining = Int.MAX_VALUE
            }
        val backingDao = SharedInMemoryDiscoveryDao()
        val loraConfig = Config.LoRaConfig(use_preset = true)
        val sessionId =
            backingDao.insertSession(
                DiscoverySessionEntity(
                    timestamp = 1L,
                    presetsScanned = "LONG_FAST",
                    homePreset = "LONG_FAST",
                    completionStatus = DiscoverySessionStatus.RESTORE_PENDING_FAILED,
                    deviceAddress = device,
                    homeLoraConfig = loraConfig,
                ),
            )
        val discoveryDao =
            object : DiscoveryDao by backingDao {
                override suspend fun updateRecoverableSessionCompletionStatus(sessionId: Long, status: String): Int = 0
            }
        val restorer =
            DiscoveryHomeRestorer(
                radioController = radioController,
                serviceRepository = serviceRepository,
                discoveryDao = discoveryDao,
                applicationScope = applicationScope(backgroundScope),
                meshPrefs = meshPrefs,
            )

        val result =
            restorer.schedule(
                DiscoveryHomeRestorePlan(
                    sessionId = sessionId,
                    deviceAddress = device,
                    loraConfig = loraConfig,
                    primaryChannel = null,
                    restorePrimaryChannel = false,
                    finalStatus = DiscoverySessionStatus.FAILED,
                ),
            )
        var retryWindowMs = 0L
        var retryDelayMs = DiscoveryHomeRestorer.RETRY_DELAY_MS
        repeat(DiscoveryHomeRestorer.MAX_RESTORE_ATTEMPTS - 1) {
            retryWindowMs += retryDelayMs
            retryDelayMs =
                (retryDelayMs * DiscoveryHomeRestorer.RETRY_BACKOFF_MULTIPLIER).coerceAtMost(
                    DiscoveryHomeRestorer.MAX_RETRY_DELAY_MS,
                )
        }
        advanceTimeBy(retryWindowMs)
        runCurrent()

        assertFalse(result.await())
        assertEquals(DiscoverySessionStatus.RESTORE_PENDING_FAILED, backingDao.getSession(sessionId)?.completionStatus)
        assertTrue(restorer.hasUnsatisfiedRestoreFor(device))
        assertFalse(
            restorer.awaitBeforeScan(device),
            "a failed terminal-status write must keep the recoverable same-device barrier",
        )
    }
}
