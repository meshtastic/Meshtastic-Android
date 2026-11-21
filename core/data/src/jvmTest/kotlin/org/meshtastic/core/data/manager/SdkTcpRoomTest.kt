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
package org.meshtastic.core.data.manager

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.data.datasource.SwitchingChannelSetDataSource
import org.meshtastic.core.data.datasource.SwitchingNodeInfoReadDataSource
import org.meshtastic.core.data.datasource.SwitchingNodeInfoWriteDataSource
import org.meshtastic.core.data.repository.NodeRepositoryImpl
import org.meshtastic.core.data.repository.RadioConfigRepositoryImpl
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.di.DatabaseDataStore
import org.meshtastic.core.database.di.asDatabaseDataStore
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.datastore.LocalConfigDataSource
import org.meshtastic.core.datastore.LocalStatsDataSource
import org.meshtastic.core.datastore.ModuleConfigDataSource
import org.meshtastic.core.datastore.di.CoreLocalConfigDataStore
import org.meshtastic.core.datastore.di.CoreModuleConfigDataStore
import org.meshtastic.core.datastore.store.Store
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.repository.ReceivedRadioFrame
import org.meshtastic.core.repository.StoredPassphrase
import org.meshtastic.core.service.ServiceRepositoryImpl
import org.meshtastic.core.service.SharedRadioInterfaceService
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeRadioTransport
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.sdk.transport.tcp.TcpTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SdkTcpRoomTest {

    @Test
    fun sdkTcpInstallsRoomSnapshotBeforePacketDelivery() = runBlocking {
        val liveHost = System.getenv("MESHTASTIC_SDK_TCP_HOST")
        if (liveHost == null) {
            withReplayTcpPeer { port -> exerciseConnection("127.0.0.1", port, expectReplayText = true) }
        } else {
            val port = System.getenv("MESHTASTIC_SDK_TCP_PORT")?.toInt() ?: 4403
            exerciseConnection(liveHost, port, expectReplayText = false)
        }
    }

    @Test
    fun sdkTcpPromptsForLockdownBeforeInstallingRoomSnapshot() = runBlocking {
        withReplayTcpPeer(lockdownPassphrase = TEST_PASSPHRASE) { port -> exerciseLockdown(port, autoUnlock = false) }
    }

    @Test
    fun sdkTcpReplaysSavedPassphraseBeforeInstallingRoomSnapshot() = runBlocking {
        withReplayTcpPeer(lockdownPassphrase = TEST_PASSPHRASE) { port -> exerciseLockdown(port, autoUnlock = true) }
    }

    private suspend fun exerciseLockdown(port: Int, autoUnlock: Boolean) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val dispatchers =
            CoroutineDispatchers(io = Dispatchers.IO, main = Dispatchers.Main, default = Dispatchers.Default)
        val prefs = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "sdk-lockdown-${Uuid.random()}.preferences_pb"
        val store = PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { prefs })
        val databases = TestDatabases(store.asDatabaseDataStore(), dispatchers)
        val lifecycle = TestLifecycleOwner()
        val address = "t127.0.0.1:$port"
        val passphraseStore = mock<LockdownPassphraseStore>(MockMode.autofill)
        every { passphraseStore.getPassphrase(address) } returns
            if (autoUnlock) StoredPassphrase(TEST_PASSPHRASE, 5, 0) else null
        lateinit var ingress: RoomIngress
        val radio =
            createRadio(lifecycle.lifecycle, dispatchers, address) { service ->
                ingress.createSdkTransport(service, port)
            }
        ingress = RoomIngress(scope, lifecycle.lifecycle, dispatchers, databases, radio, passphraseStore)
        try {
            withTimeout(10.seconds) {
                databases.switchActiveDatabase(address)
                radio.connect()
                val session = radio.activeSession.filterNotNull().first()
                if (!autoUnlock) {
                    ingress.lockdownState.first { it is LockdownState.Locked }
                    databases.withReadDb { db -> assertNull(db.nodeInfoDao().getMyNodeEntity()) }
                    assertTrue(ingress.lockdown.submitPassphrase(TEST_PASSPHRASE, 5, 0, 0, false))
                }
                ingress.appConnection.first { it == ConnectionState.Connected }
                assertEquals(LockdownState.Unlocked, ingress.lockdownState.value)
                assertEquals(session, radio.activeSession.value)
                databases.withReadDb { db ->
                    assertEquals(SdkRoomReplayTest.LOCAL_NODE, db.nodeInfoDao().getMyNodeEntity()?.myNodeNum)
                }
                radio.disconnect()
                assertEquals(LockdownState.None, ingress.lockdownState.value)
            }
        } finally {
            radio.disconnect()
            lifecycle.destroy()
            databases.close()
            job.cancelAndJoin()
            FileSystem.SYSTEM.delete(prefs)
            Dispatchers.resetMain()
        }
    }

    private suspend fun exerciseConnection(host: String, port: Int, expectReplayText: Boolean) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val dispatchers =
            CoroutineDispatchers(io = Dispatchers.IO, main = Dispatchers.Main, default = Dispatchers.Default)
        val prefs = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "sdk-tcp-${Uuid.random()}.preferences_pb"
        val store = PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { prefs })
        val databases = TestDatabases(store.asDatabaseDataStore(), dispatchers)
        val lifecycleOwner = TestLifecycleOwner()
        val lifecycle = lifecycleOwner.lifecycle
        val address = "t$host:$port"
        val radio = createRadio(lifecycle, dispatchers, address)
        try {
            withTimeout(30.seconds) {
                databases.switchActiveDatabase(address)
                radio.connect()
                val session = radio.activeSession.filterNotNull().first()
                assertEquals(address, session.address)
                val ingress = RoomIngress(scope, lifecycle, dispatchers, databases, radio)
                val snapshot = ingress.connect(host, port, session, expectReplayText)
                val ownNode = assertNotNull(snapshot.myInfo).my_node_num
                databases.withReadDb { db ->
                    val localNode = assertNotNull(db.nodeInfoDao().getMyNodeEntity())
                    assertEquals(ownNode, localNode.myNodeNum)
                    assertEquals(
                        snapshot.configBundle?.metadata?.firmware_version?.takeIf(String::isNotBlank),
                        localNode.firmwareVersion,
                    )
                    val storedNodes = db.nodeInfoDao().getAllNodesSnapshot().map { it.num }.toSet()
                    assertTrue(storedNodes.containsAll(snapshot.nodes.values.map { it.num }))
                    val storedChannels = assertNotNull(db.channelSetDao().get()).channelSet
                    assertTrue(storedChannels.settings.isNotEmpty())
                    if (expectReplayText) {
                        assertTrue(SdkRoomReplayTest.REFRESH_NODE in storedNodes)
                        assertEquals(SdkRoomReplayTest.CHANNEL_NAME, storedChannels.settings.first().name)
                        assertTrue(
                            db.packetDao().getAllPacketsSnapshot().any {
                                it.packetId == SdkRoomReplayTest.PACKET_ID && it.data.text == SdkRoomReplayTest.TEXT
                            },
                        )
                    }
                }
                radio.disconnect()
                val replacementAddress = "t192.0.2.2"
                databases.switchActiveDatabase(replacementAddress)
                assertTrue(radio.setDeviceAddress(replacementAddress))
                val replacement = radio.activeSession.filterNotNull().first { it.address == replacementAddress }
                assertTrue(replacement.generation > session.generation)
                assertFalse(ingress.installSnapshot(session, snapshot))
                databases.withReadDb { db -> assertNull(db.nodeInfoDao().getMyNodeEntity()) }
            }
        } finally {
            radio.disconnect()
            lifecycleOwner.destroy()
            databases.close()
            job.cancelAndJoin()
            FileSystem.SYSTEM.delete(prefs)
            Dispatchers.resetMain()
        }
    }

    private class RoomIngress(
        private val scope: CoroutineScope,
        lifecycle: Lifecycle,
        dispatchers: CoroutineDispatchers,
        databases: DatabaseManager,
        private val radio: SharedRadioInterfaceService,
        passphraseStore: LockdownPassphraseStore = mock(MockMode.autofill),
    ) {
        private val notified = CompletableDeferred<String>()
        private val notifications = mock<MeshNotificationManager>(MockMode.autofill)
        private val state = ServiceRepositoryImpl()
        private val serviceScope = scope.asServiceScope()
        private val readNodes = SwitchingNodeInfoReadDataSource(databases)
        private val nodeRepository =
            NodeRepositoryImpl(
                lifecycle,
                readNodes,
                SwitchingNodeInfoWriteDataSource(databases, dispatchers),
                dispatchers,
                mock<LocalStatsDataSource>(MockMode.autofill).also { source ->
                    every { source.localStatsFlow } returns MutableStateFlow(LocalStats.Builder().build())
                },
            )
        private val nodeManager = NodeManagerImpl(nodeRepository, notifications, radio, serviceScope)
        private val config =
            RadioConfigRepositoryImpl(
                nodeRepository,
                SwitchingChannelSetDataSource(databases, dispatchers),
                LocalConfigDataSource(
                    object : ConfigStore<LocalConfig>(LocalConfig.Builder().build()), CoreLocalConfigDataStore {},
                ),
                ModuleConfigDataSource(
                    object :
                        ConfigStore<LocalModuleConfig>(LocalModuleConfig.Builder().build()),
                        CoreModuleConfigDataStore {},
                ),
            )
        private val installer =
            MeshConfigFlowManagerImpl(
                nodeManager,
                lazy { error("SDK TCP must own the handshake") },
                nodeRepository,
                config,
                state,
                mock(MockMode.autofill),
                mock(MockMode.autofill),
                DataLayerHeartbeatSender(mock(MockMode.autofill)),
                FakeNotificationPrefs(),
                radio,
                serviceScope,
            )
        private val processor =
            createRoomReplayProcessor(scope, dispatchers, databases, nodeManager, radio, config, notifications)

        private val connectionManager = mock<MeshConnectionManager>(MockMode.autofill)
        val appConnection = state.connectionState
        val lockdownState = state.lockdownState
        val lockdown =
            LockdownCoordinatorImpl(
                state,
                CommandSenderImpl(
                    mock(MockMode.autofill),
                    nodeManager,
                    config,
                    mock(MockMode.autofill),
                    mock(MockMode.autofill),
                    mock(MockMode.autofill),
                    mock(MockMode.autofill),
                    mock(MockMode.autofill),
                    serviceScope,
                    radio,
                ),
                passphraseStore,
                radio,
                lazy { connectionManager },
            )

        fun createSdkTransport(service: RadioInterfaceService, port: Int): RadioTransport = SdkPhoneApiRadioTransport(
            { TcpTransport("127.0.0.1", port) },
            service,
            lazy { installer },
            lazy { connectionManager },
            lazy { processor },
            lazy { nodeManager },
            lazy { lockdown },
        )

        init {
            everySuspend { notifications.updateMessageNotification(any(), any(), any(), any(), any(), any()) } calls
                {
                    check(state.connectionState.value == ConnectionState.Connected)
                    notified.complete(it.args[2] as String)
                }
        }

        suspend fun installSnapshot(session: RadioSessionContext, ready: RadioSessionEvent.Ready): Boolean {
            val config = ready.configBundle
            return installer.installRadioSnapshot(
                session,
                RadioHandshakeSnapshot(
                    myInfo = assertNotNull(ready.myInfo),
                    metadata = config?.metadata,
                    channels = assertNotNull(ready.channels),
                    configs = config?.configs.orEmpty(),
                    moduleConfigs = config?.moduleConfigs.orEmpty(),
                    deviceUiConfig = config?.deviceUIConfig,
                    nodes = ready.nodes.values.toList(),
                ),
            )
        }

        suspend fun connect(
            host: String,
            port: Int,
            session: RadioSessionContext,
            expectReplayText: Boolean,
        ): RadioSessionEvent.Ready = coroutineScope {
            val client =
                RadioClient.Builder()
                    .transport(TcpTransport(host, port))
                    .storage(InMemoryStorageProvider())
                    .coroutineContext(scope.coroutineContext)
                    .autoSyncTimeOnConnect(false)
                    .sessionReceive()
                    .build()
            val installed = CompletableDeferred<RadioSessionEvent.Ready>()
            val refreshed = CompletableDeferred<Unit>()
            val reader = launch {
                var sdkSession: Long? = null
                assertNotNull(client.sessionEvents).collect { event ->
                    when (event) {
                        is RadioSessionEvent.Ready -> {
                            assertTrue(installSnapshot(session, event))
                            assertEquals(ConnectionState.Connected, state.connectionState.value)
                            sdkSession = event.sessionId
                            installed.complete(event)
                        }

                        is RadioSessionEvent.Packet -> {
                            assertEquals(sdkSession, event.sessionId)
                            val payload = FromRadio.Builder().also { it.packet = event.packet }.build().encode()
                            processor.handleFromRadio(
                                ReceivedRadioFrame(payload.toByteString(), session),
                                nodeManager.myNodeNum.value,
                            )
                        }

                        is RadioSessionEvent.Lockdown -> Unit

                        is RadioSessionEvent.Envelope -> error("Envelopes were disabled")

                        is RadioSessionEvent.NodeDbRefreshed -> {
                            assertEquals(sdkSession, event.sessionId)
                            installer.installRefreshedNodes(session, event.nodes.values.toList())
                            assertEquals(ConnectionState.Connected, state.connectionState.value)
                            refreshed.complete(Unit)
                        }

                        is RadioSessionEvent.Ended -> sdkSession = null
                    }
                }
            }
            try {
                client.connect()
                val snapshot = installed.await()
                if (expectReplayText) {
                    assertEquals(SdkRoomReplayTest.TEXT, notified.await())
                    assertTrue(client.requestNodeDbRefresh())
                    refreshed.await()
                }
                snapshot
            } finally {
                client.disconnect()
                reader.cancelAndJoin()
            }
        }
    }

    private open class ConfigStore<T>(initial: T) : Store<T> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(initial)
        override val data = state

        override suspend fun updateData(transform: (T) -> T): T = mutex.withLock {
            transform(state.value).also { state.value = it }
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }

        fun destroy() {
            lifecycle.currentState = Lifecycle.State.DESTROYED
        }
    }

    private class TestDatabases(prefs: DatabaseDataStore, dispatchers: CoroutineDispatchers) :
        DatabaseManager(prefs, dispatchers) {
        override fun buildDatabase(dbName: String): MeshtasticDatabase = getInMemoryDatabaseBuilder().build()

        override fun applyPendingRestore(dbName: String) = Unit

        override fun isMarkedCorrupt(dbName: String): Boolean = false

        override fun schedulePostSwitchMaintenance(dbName: String, db: MeshtasticDatabase) = Unit
    }

    private fun createRadio(
        lifecycle: Lifecycle,
        dispatchers: CoroutineDispatchers,
        address: String,
        createTransport: (RadioInterfaceService) -> RadioTransport = { FakeRadioTransport() },
    ): SharedRadioInterfaceService {
        val network = mock<NetworkRepository>(MockMode.autofill)
        every { network.networkAvailable } returns MutableStateFlow(true)
        every { network.resolvedList } returns MutableSharedFlow()
        val factory = mock<RadioTransportFactory>(MockMode.autofill)
        every { factory.isAddressValid(any()) } returns true
        every { factory.mockTransportEnabled } returns MutableStateFlow(false)
        every { factory.createTransport(any(), any()) } calls { createTransport(it.args[1] as RadioInterfaceService) }
        return SharedRadioInterfaceService(
            dispatchers = dispatchers,
            bluetoothRepository = FakeBluetoothRepository(),
            networkRepository = network,
            serialDevicePresence =
            object : SerialDevicePresence {
                override val deviceKeys = MutableStateFlow(emptySet<String>())
            },
            processLifecycle = lifecycle,
            radioPrefs = FakeRadioPrefs().apply { setDevAddr(address) },
            transportFactory = factory,
            analytics = mock(MockMode.autofill),
            radioOperationLock = RadioOperationLock(),
        )
    }

    private companion object {
        const val TEST_PASSPHRASE = "example"
    }
}
