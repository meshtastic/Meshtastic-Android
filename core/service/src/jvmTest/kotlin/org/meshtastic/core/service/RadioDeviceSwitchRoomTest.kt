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
package org.meshtastic.core.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.di.DatabaseDataStore
import org.meshtastic.core.database.di.asDatabaseDataStore
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeMeshPrefs
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeRadioTransport
import org.meshtastic.core.testing.FakeUiPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RadioDeviceSwitchRoomTest {

    @Test
    fun deviceSwitchDrainsAdmittedWritesBeforeChangingDatabases() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher)
        val prefsFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "radio-switch-${Uuid.random()}.preferences_pb"
        val datastoreJob = SupervisorJob()
        val datastore =
            PreferenceDataStoreFactory.createWithPath(
                scope = CoroutineScope(datastoreJob + dispatcher),
                produceFile = { prefsFile },
            )
        val databases = InMemoryDatabaseManager(datastore.asDatabaseDataStore(), dispatchers)
        val lifecycleOwner =
            object : LifecycleOwner {
                override val lifecycle =
                    LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }
            }
        Dispatchers.setMain(dispatcher)
        val radio = createRadio(dispatchers, lifecycleOwner.lifecycle)
        try {
            databases.switchActiveDatabase(FIRST_ADDRESS)
            val firstDatabase = databases.currentDb.value
            radio.connect()
            runCurrent()
            val firstSession = assertNotNull(radio.activeSession.value)
            assertEquals(FIRST_ADDRESS, firstSession.address)
            val controller = createController(radio, databases)

            val admitted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val firstWrite = async {
                radio.runWithSessionLease(firstSession) { lease ->
                    admitted.complete(Unit)
                    release.await()
                    assertTrue(lease.isCurrent())
                    // Enter Room only after the switch has begun. The radio lease must protect even this writer.
                    databases.withDb { it.nodeInfoDao().upsert(NodeEntity(num = 101)) }
                }
            }
            admitted.await()

            val switch = async { controller.setDeviceAddress(SECOND_ADDRESS) }
            try {
                runCurrent()
                assertFalse(radio.isSessionActive(firstSession))
                assertFalse(switch.isCompleted)
                assertEquals(firstSession, radio.activeSession.value)
                assertEquals(FIRST_ADDRESS, radio.currentDeviceAddressFlow.value)
                assertEquals(firstDatabase, databases.currentDb.value)
            } finally {
                release.complete(Unit)
            }

            assertTrue(firstWrite.await())
            switch.await()
            runCurrent()

            val secondSession = assertNotNull(radio.activeSession.value)
            assertEquals(SECOND_ADDRESS, secondSession.address)
            assertTrue(secondSession.generation > firstSession.generation)
            val secondDatabase = databases.currentDb.value
            assertNotSame(firstDatabase, secondDatabase)
            assertFalse(radio.runWithSessionLease(firstSession) { error("stale session admitted a Room write") })
            assertTrue(
                radio.runWithSessionLease(secondSession) {
                    databases.withDb { it.nodeInfoDao().upsert(NodeEntity(num = 202)) }
                },
            )
            assertEquals(listOf(101), firstDatabase.nodeInfoDao().getAllNodesSnapshot().map(NodeEntity::num))
            assertEquals(listOf(202), secondDatabase.nodeInfoDao().getAllNodesSnapshot().map(NodeEntity::num))
        } finally {
            radio.disconnect()
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.DESTROYED
            Dispatchers.resetMain()
            databases.close()
            datastoreJob.cancelAndJoin()
            FileSystem.SYSTEM.delete(prefsFile)
        }
    }

    private fun createRadio(dispatchers: CoroutineDispatchers, lifecycle: Lifecycle): SharedRadioInterfaceService {
        val network = mock<NetworkRepository>(MockMode.autofill)
        every { network.networkAvailable } returns MutableStateFlow(true)
        every { network.resolvedList } returns MutableSharedFlow()
        val factory = mock<RadioTransportFactory>(MockMode.autofill)
        every { factory.isAddressValid(any()) } returns true
        every { factory.mockTransportEnabled } returns MutableStateFlow(false)
        every { factory.createTransport(any(), any()) } calls { FakeRadioTransport() }
        return SharedRadioInterfaceService(
            dispatchers = dispatchers,
            bluetoothRepository = FakeBluetoothRepository(),
            networkRepository = network,
            serialDevicePresence =
            object : SerialDevicePresence {
                override val deviceKeys = MutableStateFlow(emptySet<String>())
            },
            processLifecycle = lifecycle,
            radioPrefs = FakeRadioPrefs().apply { setDevAddr(FIRST_ADDRESS) },
            transportFactory = factory,
            analytics = mock(MockMode.autofill),
            radioOperationLock = RadioOperationLock(),
        )
    }

    private fun TestScope.createController(
        radio: SharedRadioInterfaceService,
        databaseManager: DatabaseManager,
    ): RadioControllerImpl {
        val nodeManager = mock<NodeManager>(MockMode.autofill)
        every { nodeManager.connectionIdentity } returns MutableStateFlow(null)
        return RadioControllerImpl(
            serviceRepository = ServiceRepositoryImpl(),
            nodeRepository = mock(MockMode.autofill),
            commandSender = mock(MockMode.autofill),
            nodeManager = nodeManager,
            radioInterfaceService = radio,
            locationManager = mock(MockMode.autofill),
            packetRepository = lazy { mock(MockMode.autofill) },
            dataHandler = lazy { mock(MockMode.autofill) },
            analytics = mock(MockMode.autofill),
            meshPrefs = FakeMeshPrefs().apply { setDeviceAddress(FIRST_ADDRESS) },
            uiPrefs = FakeUiPrefs(),
            databaseManager = databaseManager,
            serviceNotifications = mock(MockMode.autofill),
            meshConfigFlowManager = lazy { mock(MockMode.autofill) },
            radioOperationLock = RadioOperationLock(),
            radioConfigRepository = mock(MockMode.autofill),
            scope = backgroundScope.asServiceScope(),
            deviceAddressChangeHook = DeviceAddressChangeHook {},
        )
    }

    private class InMemoryDatabaseManager(
        datastore: DatabaseDataStore,
        private val testDispatchers: CoroutineDispatchers,
    ) : DatabaseManager(datastore, testDispatchers) {
        override fun buildDatabase(dbName: String): MeshtasticDatabase =
            getInMemoryDatabaseBuilder().setQueryCoroutineContext(testDispatchers.io).build()

        override fun createPoolLane(): CoroutineDispatcher = testDispatchers.io

        override fun applyPendingRestore(dbName: String) = Unit

        override fun isMarkedCorrupt(dbName: String): Boolean = false

        override fun schedulePostSwitchMaintenance(dbName: String, db: MeshtasticDatabase) = Unit
    }

    private companion object {
        const val FIRST_ADDRESS = "t192.0.2.1"
        const val SECOND_ADDRESS = "t192.0.2.2"
    }
}
