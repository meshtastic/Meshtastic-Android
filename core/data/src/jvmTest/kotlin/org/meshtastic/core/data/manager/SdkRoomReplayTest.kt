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

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.data.datasource.SwitchingNodeInfoReadDataSource
import org.meshtastic.core.data.repository.MeshLogRepositoryImpl
import org.meshtastic.core.data.repository.PacketRepositoryImpl
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.util.MeshDataMapper
import org.meshtastic.core.network.radio.ReplayRadioTransport
import org.meshtastic.core.repository.ActiveConversationTracker
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.MeshBeaconPrefs
import org.meshtastic.core.repository.MeshBeaconRepository
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionLease
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Data
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.sdk.RadioTransport
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.WireFraming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.meshtastic.proto.Channel as ProtoChannel

/** Local consumer proof using the real session projection, repositories, and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class SdkRoomReplayTest {
    @Test
    fun `SDK replay persists the retained handshake text`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(main = dispatcher, io = dispatcher, default = dispatcher)
        val asset = replayAsset()
        val session = RadioSessionContext(1, "replay:one")
        val sdk = getInMemoryDatabaseBuilder().build()
        try {
            val sdkIngress = RoomIngress(backgroundScope, dispatchers, sdk, session)
            val startedAt = nowSeconds.toInt()
            replaySdk(asset, backgroundScope, sdkIngress, session)

            // The SDK stamps a packet without rx_time with its own clock when it folds it into the sender's node.
            assertTrue(sdkIngress.peerLastHeard() >= startedAt, "the projected NodeChanged reached the node database")

            val expected =
                ReplayResult(
                    LOCAL_NODE,
                    listOf(LOCAL_NODE, PEER_NODE),
                    listOf(CHANNEL_NAME),
                    listOf(PACKET_ID to TEXT),
                    listOf(TEXT),
                )
            assertEquals(expected, sdkIngress.result())
            assertTrue(sdkIngress.dataHandlerSessions.isNotEmpty(), "packets reach the data handler")
            assertTrue(sdkIngress.dataHandlerSessions.all { it == session })
            assertTrue(sdkIngress.receiveActivityReports > 0, "packets light the receive activity indicator")
        } finally {
            sdk.close()
        }
    }

    @Test
    fun `a delayed SDK packet from a deselected session cannot enter the replacement database`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(main = dispatcher, io = dispatcher, default = dispatcher)
        val previous = RadioSessionContext(1, "replay:previous")
        val replacement = RadioSessionContext(2, "replay:replacement")
        val previousDb = getInMemoryDatabaseBuilder().build()
        val replacementDb = getInMemoryDatabaseBuilder().build()
        try {
            val ingress = RoomIngress(backgroundScope, dispatchers, previousDb, previous)
            val packetWaiting = CompletableDeferred<Unit>()
            val releasePacket = CompletableDeferred<Unit>()
            val delivery = launch {
                replaySdk(replayAsset(), backgroundScope, ingress, previous, awaitNotification = false) {
                    packetWaiting.complete(Unit)
                    releasePacket.await()
                }
            }
            packetWaiting.await()
            ingress.select(replacementDb, replacement)
            ingress.install(myInfo, nodes, listOf(primaryChannel))
            releasePacket.complete(Unit)
            delivery.join()

            assertEquals(emptyList(), ingress.dataHandlerSessions, "the stale packet reached the data handler")
            assertEquals(0, ingress.receiveActivityReports, "the stale packet lit the receive activity indicator")
            assertTrue(previousDb.packetDao().getAllPacketsSnapshot().isEmpty())
            assertEquals(emptyList(), ingress.result().messages)
            assertEquals(emptyList(), ingress.result().notifications)
        } finally {
            previousDb.close()
            replacementDb.close()
        }
    }

    private suspend fun replaySdk(
        asset: ByteArray,
        scope: CoroutineScope,
        ingress: RoomIngress,
        session: RadioSessionContext,
        awaitNotification: Boolean = true,
        beforePacket: suspend () -> Unit = {},
    ) {
        val client =
            RadioClient.Builder()
                .transport(ReplayRadioTransport(scope, session.address, asset, packetDelayMs = 0))
                .storage(InMemoryStorageProvider())
                .coroutineContext(scope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .sessionReceive()
                .build()
        val processed = CompletableDeferred<Unit>()
        val delivery = scope.launch {
            var sdkSession: Long? = null
            assertNotNull(client.sessionEvents).collect { event ->
                when (event) {
                    is RadioSessionEvent.Ready -> {
                        ingress.install(
                            assertNotNull(event.myInfo),
                            event.nodes.values.toList(),
                            assertNotNull(event.channels),
                        )
                        sdkSession = event.sessionId
                    }

                    is RadioSessionEvent.Packet -> {
                        assertEquals(sdkSession, event.sessionId)
                        beforePacket()
                        ingress.receive(event, session)
                        processed.complete(Unit)
                    }

                    is RadioSessionEvent.NodeDbRefreshed,
                    is RadioSessionEvent.NodeChanged,
                    is RadioSessionEvent.ConfigChanged,
                    is RadioSessionEvent.ChannelsChanged,
                    is RadioSessionEvent.Event,
                    -> {
                        assertEquals(sdkSession, event.sessionId)
                        ingress.receive(event, session)
                    }

                    is RadioSessionEvent.Lockdown -> Unit

                    is RadioSessionEvent.Ended -> assertEquals(sdkSession, event.sessionId)
                }
            }
        }
        try {
            client.connect()
            processed.await()
            if (awaitNotification) {
                ingress.notified.await()
                ingress.peerChanged.await()
            }
        } finally {
            client.disconnect()
            delivery.cancelAndJoin()
        }
    }

    private class RoomIngress(
        scope: CoroutineScope,
        dispatchers: CoroutineDispatchers,
        database: MeshtasticDatabase,
        session: RadioSessionContext,
    ) {
        private val selectedSession = MutableStateFlow(session)
        private val channelSet = MutableStateFlow(ChannelSet.Builder().build())
        private val db = ReplayDatabaseProvider(database)
        private val radio = mock<RadioInterfaceService>(MockMode.autofill)
        private val notificationManager = mock<MeshNotificationManager>(MockMode.autofill)
        private val nodeManager =
            NodeManagerImpl(FakeNodeRepository(), notificationManager, radio, scope.asServiceScope())
        private val notifications = mutableListOf<String>()
        val dataHandlerSessions = mutableListOf<RadioSessionContext>()
        var receiveActivityReports = 0
            private set

        val notified = CompletableDeferred<Unit>()
        val peerChanged = CompletableDeferred<Unit>()
        private val projection: RadioSessionProjection

        init {
            bindSessionAdmission()
            everySuspend {
                notificationManager.updateMessageNotification(any(), any(), any(), any(), any(), any())
            } calls
                {
                    check(nodeManager.isNodeDbReady.value) {
                        "Message notification was published before Room was ready"
                    }
                    notifications += it.args[2] as String
                    notified.complete(Unit)
                }
            val config = mock<RadioConfigRepository>(MockMode.autofill)
            every { config.channelSetFlow } returns channelSet
            val configFlowManager =
                MeshConfigFlowManagerImpl(
                    nodeManager = nodeManager,
                    nodeRepository = FakeNodeRepository(),
                    radioConfigRepository = config,
                    serviceStateWriter = mock(MockMode.autofill),
                    analytics = mock(MockMode.autofill),
                    notificationPrefs = FakeNotificationPrefs(),
                    radioInterfaceService = radio,
                    scope = scope.asServiceScope(),
                )
            val eventHandler =
                RadioSessionEventHandler(
                    serviceStateWriter = mock(MockMode.autofill),
                    configFlowManager = lazy { configFlowManager },
                    mqttManager = mock(MockMode.autofill),
                    nodeManager = nodeManager,
                    serviceNotifications = notificationManager,
                    firmwareUpdateStatusRepository = FirmwareUpdateStatusRepository(),
                    radioInterfaceService = radio,
                    scope = scope.asServiceScope(),
                )
                    .apply { titleResolver = { it.key } }
            projection =
                createRoomReplayProjection(
                    scope,
                    dispatchers,
                    db,
                    nodeManager,
                    radio,
                    config,
                    notificationManager,
                    configFlowManager = lazy { configFlowManager },
                    eventHandler = lazy { eventHandler },
                    onReceivedData = { dataHandlerSessions += it },
                )
        }

        private fun bindSessionAdmission() {
            every { radio.reportReceiveActivity() } calls { receiveActivityReports++ }
            every { radio.isSessionActive(any()) } calls { selectedSession.value == it.args[0] }
            everySuspend { radio.runWhileSessionActive(any(), any(), any()) } calls
                {
                    val admitted = selectedSession.value == it.args[0]
                    if (admitted) {
                        @Suppress("UNCHECKED_CAST")
                        val block = it.args[2] as suspend () -> Unit
                        block()
                    }
                    admitted
                }
            everySuspend { radio.runWithSessionLease(any(), any()) } calls
                {
                    val requested = it.args[0] as RadioSessionContext
                    val admitted = selectedSession.value == requested
                    if (admitted) {
                        @Suppress("UNCHECKED_CAST")
                        val block = it.args[1] as suspend (RadioSessionLease) -> Unit
                        block(
                            object : RadioSessionLease {
                                override val session = requested

                                override fun isCurrent() = selectedSession.value == requested
                            },
                        )
                    }
                    admitted
                }
        }

        fun select(database: MeshtasticDatabase, session: RadioSessionContext) {
            nodeManager.clear()
            selectedSession.value = session
            db.currentDb.value = database
        }

        suspend fun install(info: MyNodeInfo, nodes: List<NodeInfo>, channels: List<ProtoChannel>) {
            db.withDb { database ->
                database
                    .nodeInfoDao()
                    .installConfig(
                        MyNodeEntity(info.my_node_num, null, null, false, false, 0, 0, 0, 8, false),
                        nodes.map { NodeEntity(num = it.num, user = assertNotNull(it.user)) },
                    )
            }
            channelSet.value =
                ChannelSet.Builder()
                    .also { set ->
                        set.settings = channels.sortedBy { it.index }.map { assertNotNull(it.settings) }
                    }
                    .build()
            nodes.forEach(nodeManager::installNodeInfo)
            nodeManager.setMyNodeNum(info.my_node_num)
            nodeManager.setNodeDbReady(true)
            nodeManager.setAllowNodeDbWrites(true)
        }

        suspend fun receive(event: RadioSessionEvent, session: RadioSessionContext) {
            projection.project(event, session)
            val peerUpdate = (event as? RadioSessionEvent.NodeChanged)?.change as? NodeChange.Updated
            if (peerUpdate?.node?.num == PEER_NODE) peerChanged.complete(Unit)
        }

        fun peerLastHeard(): Int = nodeManager.nodeDBbyNodeNum.getValue(PEER_NODE).lastHeard

        suspend fun result(): ReplayResult = db.withReadDb { database ->
            ReplayResult(
                database.nodeInfoDao().getMyNodeEntity()?.myNodeNum,
                database.nodeInfoDao().getAllNodesSnapshot().map { it.num }.sorted(),
                channelSet.value.settings.map { it.name },
                database.packetDao().getAllPacketsSnapshot().map { it.packetId to it.data.text },
                notifications.toList(),
            )
        }
    }

    private data class ReplayResult(
        val myNode: Int?,
        val nodes: List<Int>,
        val channelNames: List<String>,
        val messages: List<Pair<Int, String?>>,
        val notifications: List<String>,
    )

    private class ReplayDatabaseProvider(database: MeshtasticDatabase) : DatabaseProvider {
        override val currentDb = MutableStateFlow(database)

        override fun <T> observeCurrentDb(name: String?, query: (MeshtasticDatabase) -> Flow<T>): Flow<T> =
            currentDb.flatMapLatest(query)

        override suspend fun <T> withReadDb(block: suspend (MeshtasticDatabase) -> T): T = block(currentDb.value)

        override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T = block(currentDb.value)
    }

    internal class SdkReplayTransport(scope: CoroutineScope, address: String, asset: ByteArray) : RadioTransport {
        override val identity = TransportIdentity(address)
        override val state = MutableStateFlow<TransportState>(TransportState.Disconnected)
        private val frames = Channel<Frame>(Channel.UNLIMITED)
        private var nodesOnlyRequests = 0
        private val replay = ReplayRadioTransport(scope, address, asset, packetDelayMs = 0)

        override suspend fun connect() {
            replay.connect()
            state.value = TransportState.Connected
        }

        override suspend fun send(frame: Frame) {
            if (frame.bytes.size > WireFraming.HEADER_SIZE) {
                val payload = frame.bytes.substring(WireFraming.HEADER_SIZE).toByteArray()
                val request = ToRadio.ADAPTER.decode(payload)
                if (request.want_config_id == HandshakeConstants.NODE_INFO_NONCE && ++nodesOnlyRequests > 1) {
                    val refreshed =
                        NodeInfo.Builder()
                            .also { node ->
                                node.num = REFRESH_NODE
                                node.user =
                                    User.Builder()
                                        .also { user ->
                                            user.id = "!00000063"
                                            user.long_name = "Refreshed peer"
                                        }
                                        .build()
                            }
                            .build()
                    // Firmware opens a nodes-only dump with its own node_info (PhoneAPI STATE_SEND_OWN_NODEINFO).
                    deliver(FromRadio.Builder().also { it.node_info = nodes.first() }.build().encode())
                    deliver(FromRadio.Builder().also { it.node_info = refreshed }.build().encode())
                    deliver(
                        FromRadio.Builder()
                            .also { it.config_complete_id = HandshakeConstants.NODE_INFO_NONCE }
                            .build()
                            .encode(),
                    )
                    return
                }
            }
            replay.send(frame)
        }

        private fun deliver(payload: ByteArray) {
            val bytes =
                Buffer()
                    .apply {
                        writeByte(WireFraming.MAGIC_0.toInt())
                        writeByte(WireFraming.MAGIC_1.toInt())
                        writeShort(payload.size)
                        write(payload)
                    }
                    .readByteString()
            check(frames.trySend(Frame(bytes)).isSuccess)
        }

        override fun frames() = merge(replay.frames(), frames.receiveAsFlow())

        override suspend fun disconnect() {
            replay.disconnect()
            frames.close()
            state.value = TransportState.Disconnected
        }
    }

    internal companion object {
        const val LOCAL_NODE = 1
        const val PEER_NODE = 42
        const val REFRESH_NODE = 99
        const val BROADCAST = -1
        const val PACKET_ID = 123
        const val LOCAL_ID = "!00000001"
        const val PEER_ID = "!0000002a"
        const val TEXT = "Replay during handshake"
        const val CHANNEL_NAME = "Replay channel"
        val file =
            FileInfo.Builder()
                .also {
                    it.file_name = "replay.txt"
                    it.size_bytes = 1024
                }
                .build()
        val regionPresets = LoRaRegionPresetMap.Builder().build()
        val myInfo = MyNodeInfo.Builder().also { it.my_node_num = LOCAL_NODE }.build()
        val peer =
            User.Builder()
                .also {
                    it.id = PEER_ID
                    it.long_name = "Replay peer"
                }
                .build()
        val primaryChannel =
            ProtoChannel.Builder()
                .also {
                    it.role = ProtoChannel.Role.PRIMARY
                    it.settings = ChannelSettings.Builder().also { settings -> settings.name = CHANNEL_NAME }.build()
                }
                .build()
        val nodes =
            listOf(
                NodeInfo.Builder()
                    .also {
                        it.num = LOCAL_NODE
                        it.user = User.Builder().also { user -> user.id = LOCAL_ID }.build()
                    }
                    .build(),
                NodeInfo.Builder()
                    .also {
                        it.num = PEER_NODE
                        it.user = peer
                    }
                    .build(),
            )

        fun replayAsset(): ByteArray = Buffer()
            .apply {
                val packet =
                    MeshPacket.Builder()
                        .also {
                            it.id = PACKET_ID
                            it.from = PEER_NODE
                            it.to = BROADCAST
                            it.decoded =
                                Data.Builder()
                                    .also { data ->
                                        data.portnum = PortNum.TEXT_MESSAGE_APP
                                        data.payload = TEXT.encodeUtf8()
                                    }
                                    .build()
                        }
                        .build()
                val config = FromRadio.Builder().also { it.my_info = myInfo }.build()
                val channel = FromRadio.Builder().also { it.channel = primaryChannel }.build()
                val duringHandshake = FromRadio.Builder().also { it.packet = packet }.build()
                val manifest = FromRadio.Builder().also { it.fileInfo = file }.build()
                val presets = FromRadio.Builder().also { it.region_presets = regionPresets }.build()
                val configuration = listOf(config, manifest, presets, channel, duringHandshake)
                writeInt(configuration.size)
                configuration.forEach { envelope -> writeEnvelope(envelope) }
                writeInt(nodes.size)
                nodes.forEach { node ->
                    val envelope = FromRadio.Builder().also { it.node_info = node }.build()
                    writeEnvelope(envelope)
                }
            }
            .readByteArray()

        fun Buffer.writeEnvelope(envelope: FromRadio) {
            val bytes = envelope.encode()
            writeInt(bytes.size).write(bytes)
        }
    }
}

@Suppress("LongParameterList")
internal fun createRoomReplayProjection(
    scope: CoroutineScope,
    dispatchers: CoroutineDispatchers,
    db: DatabaseProvider,
    nodeManager: NodeManager,
    radio: RadioInterfaceService,
    config: RadioConfigRepository,
    notificationManager: MeshNotificationManager,
    configFlowManager: Lazy<MeshConfigFlowManagerImpl> = lazy { error("config install is not under test") },
    eventHandler: Lazy<RadioSessionEventHandler> = lazy { error("session events are not under test") },
    onReceivedData: (RadioSessionContext) -> Unit = {},
): RadioSessionProjection {
    val packetRepository = PacketRepositoryImpl(db, dispatchers)
    val logPrefs = mock<MeshLogPrefs>(MockMode.autofill)
    every { logPrefs.loggingEnabled } returns MutableStateFlow(true)
    val logs = MeshLogRepositoryImpl(db, dispatchers, logPrefs, SwitchingNodeInfoReadDataSource(db))
    val beaconPrefs = mock<MeshBeaconPrefs>(MockMode.autofill)
    every { beaconPrefs.storedBeacons } returns MutableStateFlow(emptyList())
    val serviceScope = scope.asServiceScope()
    val data =
        MeshDataHandlerImpl(
            nodeManager = nodeManager,
            serviceStateWriter = mock(MockMode.autofill),
            packetRepository = lazy { packetRepository },
            serviceNotifications = notificationManager,
            analytics = mock(MockMode.autofill),
            dataMapper = MeshDataMapper(nodeManager),
            tracerouteHandler = mock(MockMode.autofill),
            neighborInfoHandler = mock(MockMode.autofill),
            radioConfigRepository = config,
            messageFilter = mock(MockMode.autofill),
            storeForwardHandler = mock(MockMode.autofill),
            telemetryHandler = mock(MockMode.autofill),
            remoteShellHandler = mock(MockMode.autofill),
            collectorRegistry = DiscoveryPacketCollectorRegistryImpl(),
            geofenceMonitor =
            GeofenceMonitor(
                packetRepository = lazy { packetRepository },
                nodeManager = nodeManager,
                serviceNotifications = notificationManager,
                crossingStore = GeofenceCrossingStore(),
                notificationPrefs = FakeNotificationPrefs(),
                radioInterfaceService = radio,
                scope = serviceScope,
            ),
            meshBeaconRepository = MeshBeaconRepository(beaconPrefs, scope),
            radioInterfaceService = radio,
            activeConversationTracker = ActiveConversationTracker(),
            scope = serviceScope,
        )
    val recordingData =
        object : MeshDataHandler by data {
            override fun handleReceivedData(
                packet: MeshPacket,
                myNodeNum: Int,
                session: RadioSessionContext,
                logUuid: String?,
                logInsertJob: Job?,
            ) {
                onReceivedData(session)
                data.handleReceivedData(packet, myNodeNum, session, logUuid, logInsertJob)
            }
        }
    return radioSessionProjection(
        nodeManager,
        radio,
        scope,
        dataHandler = recordingData,
        meshLogRepository = logs,
        serviceStateWriter = mock(MockMode.autofill),
        configFlowManager = configFlowManager,
        eventHandler = eventHandler,
    )
}
