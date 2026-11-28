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
import androidx.lifecycle.coroutineScope
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.data.datasource.SwitchingChannelSetDataSource
import org.meshtastic.core.data.datasource.SwitchingNodeInfoReadDataSource
import org.meshtastic.core.data.datasource.SwitchingNodeInfoWriteDataSource
import org.meshtastic.core.data.radio.RadioSessionPipeline
import org.meshtastic.core.data.repository.NodeRepositoryImpl
import org.meshtastic.core.data.repository.PacketRepositoryImpl
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
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.RadioLinks
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionHost
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.repository.StoredPassphrase
import org.meshtastic.core.service.ServiceRepositoryImpl
import org.meshtastic.core.service.SharedRadioInterfaceService
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.proto.Data
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.transport.tcp.TcpTransport
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
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

    @Test
    fun sdkTcpRestartsAfterRebootDuringInitialConfiguration() = runBlocking {
        withReplayTcpPeer(lockdownPassphrase = TEST_PASSPHRASE, rebootBeforeConfig = true) { port ->
            exerciseLockdown(port, autoUnlock = true)
        }
    }

    @Test
    fun sdkTcpLocalDeliveryUpdatesRoomWithoutRoutingEcho() = runBlocking {
        withReplayTcpPeer(
            lockdownPassphrase = TEST_PASSPHRASE,
            localDeliveryPacketId = LOCAL_DELIVERY_PACKET_ID,
        ) { port ->
            exerciseLockdown(port, autoUnlock = true, verifyLocalDelivery = true)
        }
    }

    @Test
    fun sdkTcpRemoteAckSettlesRoomRowAndLogsTheSentPacket() = runBlocking {
        withReplayTcpPeer(lockdownPassphrase = TEST_PASSPHRASE, remoteAckNode = REMOTE_NODE) { port ->
            exerciseLockdown(port, autoUnlock = true, verifyRemoteAck = true)
        }
    }

    @Test
    fun sdkTcpListsEachHandshakeFileOnceAfterTheHeldEventsFlush() = runBlocking {
        withReplayTcpPeer(lockdownPassphrase = TEST_PASSPHRASE) { port ->
            exerciseLockdown(port, autoUnlock = true, verifySettledManifest = true)
        }
    }

    private suspend fun exerciseLockdown(
        port: Int,
        autoUnlock: Boolean,
        verifyLocalDelivery: Boolean = false,
        verifyRemoteAck: Boolean = false,
        verifySettledManifest: Boolean = false,
    ) {
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
        val state = ServiceRepositoryImpl()
        val radio = createRadio(lifecycle.lifecycle, dispatchers, address, state) { ingress.tcpLink(port, it) }
        ingress = RoomIngress(scope, lifecycle.lifecycle, dispatchers, databases, radio, passphraseStore, state)
        val lockdownHistory = CopyOnWriteArrayList<LockdownState>()
        scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            ingress.lockdownState.collect { lockdownHistory += it }
        }
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
                if (autoUnlock) {
                    assertTrue(
                        lockdownHistory.none { it is LockdownState.Locked },
                        "auto-unlock showed $lockdownHistory",
                    )
                }
                assertEquals(listOf(SdkRoomReplayTest.file), ingress.files.first())
                assertEquals(SdkRoomReplayTest.regionPresets, ingress.regionPresets.first())
                assertEquals(SdkRoomReplayTest.deviceUiConfig, ingress.deviceUi.first())
                if (verifySettledManifest) {
                    // The SDK flushes the held FileInfoReceived before the held text packet, so the text's
                    // notification means the file event was handled.
                    assertEquals(SdkRoomReplayTest.TEXT, ingress.notified.await())
                    assertEquals(listOf(SdkRoomReplayTest.file), ingress.files.first())
                }
                val readySession = assertNotNull(radio.activeSession.value)
                assertEquals(session.address, readySession.address)
                // A session the radio rebooted out of before any event never reached the app; the client's next one
                // did.
                assertEquals(session, readySession)
                databases.withReadDb { db ->
                    assertEquals(SdkRoomReplayTest.LOCAL_NODE, db.nodeInfoDao().getMyNodeEntity()?.myNodeNum)
                }
                if (verifyLocalDelivery) ingress.assertLocalDelivery(LOCAL_DELIVERY_PACKET_ID)
                if (verifyRemoteAck) {
                    val packetId = ingress.assertRemoteAck(REMOTE_NODE)
                    // The SDK's outbound stream is what writes the sent row, under the local node.
                    var sent: MeshPacket? = null
                    while (sent == null) {
                        sent = databases.withReadDb { db ->
                            db.meshLogDao().getAllLogsSnapshot().firstNotNullOfOrNull { log ->
                                log.fromRadio.packet?.takeIf { log.fromNum == 0 && it.id == packetId }
                            }
                        }
                        if (sent == null) delay(10.milliseconds)
                    }
                    assertEquals(REMOTE_NODE, sent.to)
                    assertEquals(PortNum.TEXT_MESSAGE_APP, sent.decoded?.portnum)
                    assertEquals(REPLY_ID, sent.decoded?.reply_id)
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
        val radio = createRadio(lifecycle, dispatchers, address, ServiceRepositoryImpl()) { SessionOnlyLink() }
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
        private val state: ServiceRepositoryImpl = ServiceRepositoryImpl(),
    ) {
        val notified = CompletableDeferred<String>()
        private val notifications = mock<MeshNotificationManager>(MockMode.autofill)
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
                radio,
            )
        private val installer =
            MeshConfigFlowManagerImpl(
                nodeManager,
                nodeRepository,
                config,
                mock(MockMode.autofill),
                FakeNotificationPrefs(),
                radio,
            )
        private val outboundRepository = PacketRepositoryImpl(databases, dispatchers)
        private val outgoing =
            PacketHandlerImpl(
                lazy { outboundRepository },
                databases,
                radio,
                state,
                nodeManager,
                serviceScope,
            )

        suspend fun assertLocalDelivery(packetId: Int) {
            val port = PortNum.TEXT_MESSAGE_APP
            val localNode = SdkRoomReplayTest.LOCAL_NODE
            val id =
                outboundRepository.savePacket(
                    myNodeNum = localNode,
                    contactKey = SdkRoomReplayTest.LOCAL_ID,
                    packet =
                    DataPacket(
                        from = SdkRoomReplayTest.LOCAL_ID,
                        to = SdkRoomReplayTest.LOCAL_ID,
                        bytes = null,
                        dataType = port.value,
                        id = packetId,
                        status = MessageStatus.QUEUED,
                    ),
                    receivedTime = 0,
                )
            val packet =
                MeshPacket.Builder()
                    .also {
                        it.id = packetId
                        it.from = localNode
                        it.to = localNode
                        it.want_ack = true
                        it.decoded = Data.Builder().also { data -> data.portnum = port }.build()
                    }
                    .build()
            assertTrue(outgoing.sendToRadio(packet))
            while (outboundRepository.getPacketByPersistedId(id)?.status != MessageStatus.DELIVERED) {
                delay(10.milliseconds)
            }
        }

        /** Sends a reply to [remote] from a stored row and returns its ID once the remote ACK has settled the row. */
        suspend fun assertRemoteAck(remote: Int): Int {
            val packetId = outgoing.generatePacketId()
            val remoteId = NodeAddress.numToDefaultId(remote)
            val packet =
                DataPacket(
                    from = SdkRoomReplayTest.LOCAL_ID,
                    to = remoteId,
                    bytes = "hi".encodeUtf8(),
                    dataType = PortNum.TEXT_MESSAGE_APP.value,
                    id = packetId,
                    status = MessageStatus.QUEUED,
                    replyId = REPLY_ID,
                    wantAck = true,
                )
            val id =
                outboundRepository.savePacket(
                    myNodeNum = SdkRoomReplayTest.LOCAL_NODE,
                    contactKey = "0$remoteId",
                    packet = packet,
                    receivedTime = 0,
                )
            outgoing.sendData(packet)
            // An ACK from the addressed node is a delivery, and the relay that carried it back is recorded.
            while (outboundRepository.getPacketByPersistedId(id)?.status != MessageStatus.RECEIVED) {
                delay(10.milliseconds)
            }
            assertEquals(REMOTE_ACK_RELAY, outboundRepository.getPacketByPersistedId(id)?.relayNode)
            return packetId
        }

        private val eventHandler =
            RadioSessionEventHandler(
                serviceStateWriter = state,
                mqttManager = mock(MockMode.autofill),
                nodeManager = nodeManager,
                serviceNotifications = notifications,
                firmwareUpdateStatusRepository = FirmwareUpdateStatusRepository(),
                radioInterfaceService = radio,
                scope = serviceScope,
            )

        private val projection =
            createRoomReplayProjection(
                scope,
                dispatchers,
                databases,
                nodeManager,
                radio,
                config,
                notifications,
                configFlowManager = lazy { installer },
                eventHandler = lazy { eventHandler },
            )

        private val connectionManager = mock<MeshConnectionManager>(MockMode.autofill)
        val appConnection = state.connectionState
        val lockdownState = state.lockdownState
        val files = config.fileManifestFlow
        val regionPresets = config.loraRegionPresetMapFlow
        val deviceUi = config.deviceUIConfigFlow
        val lockdown =
            LockdownCoordinatorImpl(
                state,
                passphraseStore,
                radio,
                lazy { connectionManager },
            )

        /** The production link: the app's pipeline over an SDK client that owns reconnecting. */
        fun tcpLink(port: Int, linkScope: CoroutineScope): RadioLink {
            val transport = TcpTransport("127.0.0.1", port)
            return RadioSessionPipeline(lazy { projection }, lazy { connectionManager }, lazy { lockdown })
                .link(transport.identity, ble = false, scope = linkScope, provider = { _, _ -> transport })
        }

        init {
            everySuspend { notifications.updateMessageNotification(any(), any(), any(), any(), any(), any()) } calls
                {
                    check(nodeManager.isNodeDbReady.value)
                    notified.complete(it.args[2] as String)
                }
        }

        suspend fun installSnapshot(session: RadioSessionContext, ready: RadioSessionEvent.Ready): Boolean {
            assertNotNull(ready.myInfo)
            assertNotNull(ready.channels)
            return installer.installRadioSnapshot(session, ready)
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
                client.sessionEvents.collect { event ->
                    when (event) {
                        is RadioSessionEvent.Ready -> {
                            assertTrue(installSnapshot(session, event))
                            assertTrue(nodeManager.isNodeDbReady.value)
                            sdkSession = event.sessionId
                            installed.complete(event)
                        }

                        is RadioSessionEvent.Packet,
                        is RadioSessionEvent.NodeChanged,
                        is RadioSessionEvent.ConfigChanged,
                        is RadioSessionEvent.ChannelsChanged,
                        -> {
                            assertEquals(sdkSession, event.sessionId)
                            projection.project(event, session)
                        }

                        is RadioSessionEvent.Lockdown,
                        is RadioSessionEvent.Event,
                        -> Unit

                        is RadioSessionEvent.NodeDbRefreshed -> {
                            assertEquals(sdkSession, event.sessionId)
                            installer.installRefreshedNodes(session, event.nodes.values.toList())
                            assertTrue(nodeManager.isNodeDbReady.value)
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

        suspend fun destroy() {
            val scope = lifecycle.coroutineScope
            lifecycle.currentState = Lifecycle.State.DESTROYED
            // Collectors finish cancelling on Room's threads; one still running would resume on a reset main
            // dispatcher.
            scope.coroutineContext[Job]?.join()
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
        stateWriter: ServiceStateWriter,
        openLink: (CoroutineScope) -> RadioLink,
    ): SharedRadioInterfaceService {
        val network = mock<NetworkRepository>(MockMode.autofill)
        every { network.networkAvailable } returns MutableStateFlow(true)
        every { network.resolvedList } returns MutableSharedFlow()
        val links =
            object : RadioLinks {
                override val supportedDeviceTypes = listOf(DeviceType.TCP)
                override val mockTransportEnabled = MutableStateFlow(false)
                override val isReplayTransportAvailable = false

                override fun isAddressValid(address: String?): Boolean = address != null

                override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String =
                    "${interfaceId.id}$rest"

                override fun open(address: String, scope: CoroutineScope): RadioLink = openLink(scope)
            }
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
            links = links,
            analytics = mock(MockMode.autofill),
            radioOperationLock = RadioOperationLock(),
            serviceStateWriter = stateWriter,
        )
    }

    /** A link whose pipeline only opens a session, for tests that drive their own SDK client. */
    private class SessionOnlyLink : RadioLink {
        override val client: RadioClient =
            RadioClient.Builder()
                .transport(FakeRadioTransport(TransportIdentity("fake:session")))
                .storage(InMemoryStorageProvider())
                .build()

        override suspend fun connect() = Unit

        override suspend fun disconnect() = Unit

        override suspend fun run(host: RadioSessionHost) {
            host.openSession()
            awaitCancellation()
        }
    }

    private companion object {
        const val TEST_PASSPHRASE = "example"
        const val LOCAL_DELIVERY_PACKET_ID = 7123
        const val REMOTE_NODE = 0x0BADF00D
        const val REPLY_ID = 77
    }
}
