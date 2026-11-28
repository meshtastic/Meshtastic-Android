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
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.Waypoint
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConfigBundle
import org.meshtastic.sdk.Frame
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.WireFraming
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.toFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.sdk.RadioTransport as SdkRadioTransport

class RadioSessionProjectionTest {

    private class Harness(scope: CoroutineScope, dataHandler: MeshDataHandler = mock(MockMode.autofill)) {
        val radio =
            FakeRadioInterfaceService(scope).apply {
                setDeviceAddress("tcp:projection")
                connect()
            }
        val session: RadioSessionContext = checkNotNull(radio.activeSession.value)
        val nodeRepository = FakeNodeRepository()
        val logs = FakeMeshLogRepository()
        val radioConfig = FakeRadioConfigRepository()
        val nodeManager =
            NodeManagerImpl(nodeRepository, mock(MockMode.autofill), radio, scope.asServiceScope()).apply {
                setMyNodeNum(LOCAL)
                setNodeDbReady(true)
                setAllowNodeDbWrites(true)
            }
        val configFlowManager =
            MeshConfigFlowManagerImpl(
                nodeManager = nodeManager,
                nodeRepository = nodeRepository,
                radioConfigRepository = radioConfig,
                serviceStateWriter = FakeServiceRepository(),
                analytics = mock(MockMode.autofill),
                notificationPrefs = FakeNotificationPrefs(),
                radioInterfaceService = radio,
            )
        val projection =
            radioSessionProjection(
                nodeManager,
                radio,
                scope,
                dataHandler = dataHandler,
                meshLogRepository = logs,
                nodeRepository = nodeRepository,
                configFlowManager = lazy { configFlowManager },
            )

        suspend fun packet(packet: MeshPacket) = projection.project(
            RadioSessionEvent.Packet(sessionId = SDK_SESSION, sequence = 1, packet = packet),
            session,
        )
    }

    @Test
    fun `the Ready snapshot writes the my_info row that the firmware edition is read from`() = runTest {
        val harness = Harness(backgroundScope)
        val myInfo =
            MyNodeInfo.Builder()
                .also {
                    it.my_node_num = LOCAL
                    it.firmware_edition = FirmwareEdition.DEFCON
                }
                .build()

        val installed =
            harness.projection.installReady(
                RadioSessionEvent.Ready(
                    SDK_SESSION,
                    myInfo,
                    configBundle = null,
                    channels = emptyList(),
                    nodes = emptyMap(),
                ),
                harness.session,
            )
        runCurrent()

        assertTrue(installed)
        val myInfoRows = harness.logs.currentLogs.filter { it.message_type == MeshLog.TYPE_MY_INFO }
        assertEquals(listOf(FirmwareEdition.DEFCON), myInfoRows.map { it.myNodeInfo?.firmware_edition })
    }

    @Test
    fun `a Ready without my_info is not installed and writes no my_info row`() = runTest {
        val harness = Harness(backgroundScope)

        val installed =
            harness.projection.installReady(
                RadioSessionEvent.Ready(
                    SDK_SESSION,
                    null,
                    configBundle = null,
                    channels = emptyList(),
                    nodes = emptyMap(),
                ),
                harness.session,
            )
        runCurrent()

        assertFalse(installed)
        assertEquals(emptyList(), harness.logs.currentLogs.filter { it.message_type == MeshLog.TYPE_MY_INFO })
    }

    @Test
    fun `a direct LoRa packet sets the sender's link metrics and a relayed one keeps them`() = runTest {
        val harness = Harness(backgroundScope)
        harness.nodeManager.updateNode(REMOTE) { it.copy(heardOnCurrentLora = false) }

        harness.packet(loraPacket(hopStart = 3, hopLimit = 3, rssi = -97, snr = 6.25f))
        harness.packet(loraPacket(hopStart = 3, hopLimit = 1, rssi = -120, snr = -12.5f))
        runCurrent()

        val node = harness.nodeManager.nodeDBbyNodeNum.getValue(REMOTE)
        assertEquals(-97, node.rssi)
        assertEquals(6.25f, node.snr)
        assertTrue(node.heardOnCurrentLora)
        assertEquals(MeshPacket.TransportMechanism.TRANSPORT_LORA.value, node.lastTransport)
    }

    @Test
    fun `a node removed by an acknowledged removeNode leaves memory and Room`() = runTest {
        val harness = Harness(backgroundScope)
        val node = Node(num = REMOTE)
        harness.nodeRepository.upsert(node)
        harness.nodeManager.updateNode(REMOTE) { node }

        harness.projection.project(
            RadioSessionEvent.NodeChanged(SDK_SESSION, sequence = 1, change = NodeChange.Removed(NodeId(REMOTE))),
            harness.session,
        )
        runCurrent()

        assertNull(harness.nodeManager.nodeDBbyNodeNum[REMOTE])
        assertNull(harness.nodeRepository.nodeDBbyNum.value[REMOTE])
    }

    /**
     * Shaped like firmware's `PhoneAPI::makeReplay*Packet` for a node whose `last_heard` is not a wall-clock epoch and
     * whose hop count is unknown: LoRa, no `rx_rssi`, no `rx_time`, `hop_start` and `hop_limit` both 0, and the stored
     * `snr`, which is 0 for a node never measured.
     */
    private fun neverHeardReplayFrom(from: Int) = MeshPacket.Builder()
        .also { wb ->
            wb.id = 1
            wb.from = from
            wb.to = LOCAL
            wb.transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA
            wb.rx_snr = 0f
            wb.decoded =
                Data.Builder()
                    .also { d ->
                        d.portnum = PortNum.TELEMETRY_APP
                        d.payload = ByteString.EMPTY
                    }
                    .build()
        }
        .build()

    private fun capturingDataHandler(handled: MutableList<MeshPacket>) = mock<MeshDataHandler>(MockMode.autofill).also {
        every { it.handleReceivedData(any(), any(), any(), any(), any()) } calls
            { args ->
                handled += args.arg<MeshPacket>(0)
            }
    }

    @Test
    fun `a node DB replay of a never-heard node reaches the handlers with no arrival time and stays unmeasured`() =
        runTest {
            val handled = mutableListOf<MeshPacket>()
            val harness = Harness(backgroundScope, capturingDataHandler(handled))
            harness.nodeManager.installNodeInfo(NodeInfo.Builder().also { it.num = REMOTE }.build())

            harness.packet(neverHeardReplayFrom(REMOTE))
            runCurrent()

            assertNull(handled.single().rx_time, "telemetry and position handlers must not see the phone's clock")
            val node = checkNotNull(harness.nodeManager.nodeDBbyNodeNum[REMOTE])
            assertNull(node.snrOrNull, "a replayed snr is the radio's stored value, not a reception")
            assertEquals(-1, node.hopsAway, "hop_start 0 without a bitfield is unknown, not direct")
        }

    @Test
    fun `a node DB replay of a known direct neighbor keeps its heard time and hops without writing its stored snr`() =
        runTest {
            val handled = mutableListOf<MeshPacket>()
            val harness = Harness(backgroundScope, capturingDataHandler(handled))
            harness.nodeManager.installNodeInfo(NodeInfo.Builder().also { it.num = REMOTE }.build())
            val lastHeard = nowSeconds.toInt() - 3_600
            // PhoneAPI::makeReplay*Packet for a node with a wall-clock last_heard and hops_away 0: rx_time is
            // last_heard, hop_start == hop_limit == the configured hop limit, rx_snr is the stored reading, no rx_rssi.
            val replay =
                neverHeardReplayFrom(REMOTE)
                    .newBuilder()
                    .also { wb ->
                        wb.rx_time = lastHeard
                        wb.hop_start = 3
                        wb.hop_limit = 3
                        wb.rx_snr = 6.25f
                    }
                    .build()

            harness.packet(replay)
            runCurrent()

            val node = checkNotNull(harness.nodeManager.nodeDBbyNodeNum[REMOTE])
            assertEquals(0, node.hopsAway)
            assertNull(node.snrOrNull, "a stored snr was measured earlier, maybe on other settings")
            assertEquals(lastHeard, handled.single().rx_time)
        }

    @Test
    fun `a live direct packet records a 0 dB snr and 0 hops as readings`() = runTest {
        val harness = Harness(backgroundScope)
        harness.nodeManager.installNodeInfo(NodeInfo.Builder().also { it.num = REMOTE }.build())

        harness.packet(loraPacket(hopStart = 3, hopLimit = 3, rssi = -80, snr = 0f))
        runCurrent()

        val node = checkNotNull(harness.nodeManager.nodeDBbyNodeNum[REMOTE])
        assertEquals(0f, node.snrOrNull)
        assertEquals(-80, node.rssiOrNull)
        assertEquals(0, node.hopsAway)
    }

    @Test
    fun `a zero hop packet from a sender that stamps an empty bitfield is a direct neighbor`() = runTest {
        val harness = Harness(backgroundScope)
        harness.nodeManager.installNodeInfo(NodeInfo.Builder().also { it.num = REMOTE }.build())

        harness.packet(loraPacket(hopStart = 0, hopLimit = 0, rssi = -80, bitfield = 0))
        runCurrent()

        val node = checkNotNull(harness.nodeManager.nodeDBbyNodeNum[REMOTE])
        assertEquals(0, node.hopsAway)
    }

    @Test
    fun `a malformed waypoint from the mesh drops only that packet`() = runTest {
        val dataHandler = mock<MeshDataHandler>(MockMode.autofill)
        // MeshDataHandlerImpl decodes a WAYPOINT_APP payload with Waypoint.ADAPTER.decode, which throws on bad bytes.
        every { dataHandler.handleReceivedData(any(), any(), any(), any(), any()) } calls
            { args ->
                val decoded = checkNotNull(args.arg<MeshPacket>(0).decoded)
                if (decoded.portnum == PortNum.WAYPOINT_APP) check(Waypoint.ADAPTER.decode(decoded.payload).id >= 0)
            }
        val harness = Harness(backgroundScope, dataHandler)
        // A waypoint id varint whose continuation bit promises a byte that never comes.
        val truncated = byteArrayOf(0x08, 0xFF.toByte()).toByteString()

        harness.packet(loraPacket(hopStart = 3, hopLimit = 3, port = PortNum.WAYPOINT_APP, payload = truncated))
        harness.packet(loraPacket(hopStart = 3, hopLimit = 3, rssi = -97, snr = 6.25f))
        runCurrent()

        assertEquals(-97, harness.nodeManager.nodeDBbyNodeNum.getValue(REMOTE).rssi)
    }

    @Test
    fun `range test and MQTT packets get the app's hop counts`() = runTest {
        val harness = Harness(backgroundScope)

        harness.packet(loraPacket(hopStart = 3, hopLimit = 1, port = PortNum.RANGE_TEST_APP, from = RANGE_TESTER))
        harness.packet(loraPacket(hopStart = 3, hopLimit = 1, viaMqtt = true, from = MQTT_PEER))
        harness.packet(loraPacket(hopStart = 3, hopLimit = 1, from = RELAYED_PEER))
        runCurrent()

        val nodes = harness.nodeManager.nodeDBbyNodeNum
        assertEquals(0, nodes.getValue(RANGE_TESTER).hopsAway)
        assertEquals(-1, nodes.getValue(MQTT_PEER).hopsAway)
        assertEquals(2, nodes.getValue(RELAYED_PEER).hopsAway)
    }

    @Test
    fun `every received packet lights the receive activity indicator`() = runTest {
        val harness = Harness(backgroundScope)
        // The SDK offers only decoded packets; a local admin reply arrives with from 0 and updates no sender node.
        val localReply = loraPacket(hopStart = 0, hopLimit = 0, port = PortNum.ADMIN_APP, from = 0)

        harness.packet(localReply)
        harness.packet(loraPacket(hopStart = 3, hopLimit = 3))
        runCurrent()

        assertEquals(2, harness.radio.receiveActivityReports)
    }

    @Test
    fun `a config change is logged without the bluetooth pin while the radio config keeps it`() = runTest {
        val harness = Harness(backgroundScope)
        val bluetooth =
            Config.Builder()
                .also { c ->
                    c.bluetooth =
                        Config.BluetoothConfig.Builder()
                            .also { b ->
                                b.enabled = true
                                b.fixed_pin = SECRET_PIN
                            }
                            .build()
                }
                .build()
        val bundle =
            ConfigBundle(
                myInfo = MyNodeInfo.Builder().also { it.my_node_num = LOCAL }.build(),
                metadata = DeviceMetadata.Builder().build(),
                configs = listOf(bluetooth),
                moduleConfigs = emptyList(),
            )

        harness.projection.project(RadioSessionEvent.ConfigChanged(SDK_SESSION, sequence = 1, bundle), harness.session)
        runCurrent()

        val row = harness.logs.currentLogs.single { it.message_type == "Config" }
        assertEquals(0, row.fromRadio.config?.bluetooth?.fixed_pin)
        assertFalse(row.raw_message.contains(SECRET_PIN.toString()))
        assertEquals(SECRET_PIN, harness.radioConfig.lastSetLocalConfig?.bluetooth?.fixed_pin)
    }

    @Test
    fun `an acknowledged local config write reaches the radio config through the SDK session`() = runTest {
        val harness = Harness(backgroundScope)
        val (link, client) = sdkSession(harness, handshakeConfig = lora(Config.LoRaConfig.ModemPreset.LONG_FAST))
        assertEquals(lora(Config.LoRaConfig.ModemPreset.LONG_FAST), harness.radioConfig.lastSetLocalConfig)

        val written = lora(Config.LoRaConfig.ModemPreset.MEDIUM_FAST)
        val write = async { client.admin.setConfig(written) }
        runCurrent()
        link.injectRoutingAck(link.outboundPackets().last { it.admin()?.set_config != null }.id, fromNode = LOCAL)
        assertTrue(write.await() is AdminResult.Success)
        runCurrent()

        assertEquals(written, harness.radioConfig.lastSetLocalConfig)
        client.disconnect()
    }

    @Test
    fun `an acknowledged local channel write reaches the radio config through the SDK session`() = runTest {
        val harness = Harness(backgroundScope)
        val (link, client) = sdkSession(harness, handshakeConfig = lora(Config.LoRaConfig.ModemPreset.LONG_FAST))
        val settings = ChannelSettings.Builder().also { it.name = "Away" }.build()
        val channel =
            Channel.Builder()
                .also {
                    it.index = 1
                    it.role = Channel.Role.SECONDARY
                    it.settings = settings
                }
                .build()

        val write = async { client.admin.setChannel(channel) }
        runCurrent()
        link.injectRoutingAck(link.outboundPackets().last { it.admin()?.set_channel != null }.id, fromNode = LOCAL)
        assertTrue(write.await() is AdminResult.Success)
        runCurrent()

        assertEquals(settings, harness.radioConfig.channelSetFlow.first().settings.getOrNull(1))
        client.disconnect()
    }

    /** A real SDK client handshaken over a scripted link, its session events projected the way the app does. */
    private suspend fun TestScope.sdkSession(
        harness: Harness,
        handshakeConfig: Config,
    ): Pair<FakeRadioTransport, RadioClient> {
        val link = FakeRadioTransport(TransportIdentity("fake:session"), autoHandshake = true, nodeNum = LOCAL)
        val client =
            RadioClient.Builder()
                .transport(ConfigDownloadTransport(link, handshakeConfig))
                .storage(InMemoryStorageProvider())
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .sessionReceive()
                .build()
        val events = client.sessionEvents
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { event ->
                if (event is RadioSessionEvent.Ready) {
                    harness.projection.installReady(event, harness.session)
                } else {
                    harness.projection.project(event, harness.session)
                }
            }
        }
        client.connect()
        runCurrent()
        return link to client
    }

    @Test
    fun `a received admin packet is logged with its passkey cleared and reaches the data handler intact`() = runTest {
        val handled = mutableListOf<MeshPacket>()
        val dataHandler = mock<MeshDataHandler>(MockMode.autofill)
        every { dataHandler.handleReceivedData(any(), any(), any(), any(), any()) } calls
            { args ->
                handled += args.arg<MeshPacket>(0)
            }
        val harness = Harness(backgroundScope, dataHandler)
        val secret = ByteArray(32) { 0x7A }.toByteString()
        val adminPayload =
            AdminMessage.ADAPTER.encodeByteString(AdminMessage.Builder().also { it.session_passkey = secret }.build())

        harness.packet(loraPacket(hopStart = 3, hopLimit = 3, port = PortNum.ADMIN_APP, payload = adminPayload))
        runCurrent()

        val row = harness.logs.currentLogs.single { it.message_type == "Packet" }
        val loggedAdmin = AdminMessage.ADAPTER.decode(checkNotNull(row.fromRadio.packet?.decoded).payload)
        assertEquals(ByteString.EMPTY, loggedAdmin.session_passkey)
        assertFalse(row.raw_message.contains(secret.hex()))
        assertEquals(listOf(adminPayload), handled.map { it.decoded?.payload })
    }

    @Test
    fun `a packet the SDK queued is logged as our outgoing row with its passkey cleared`() = runTest {
        val harness = Harness(backgroundScope)
        val secret = ByteArray(32) { 0x7A }.toByteString()
        val adminPayload =
            AdminMessage.ADAPTER.encodeByteString(AdminMessage.Builder().also { it.session_passkey = secret }.build())
        val queued =
            loraPacket(hopStart = 3, hopLimit = 3, port = PortNum.ADMIN_APP, payload = adminPayload, from = LOCAL)
                .newBuilder()
                .also {
                    it.id = 321
                    it.to = REMOTE
                }
                .build()

        harness.projection.onOutboundPacket(queued, harness.session)
        runCurrent()

        // The request logs read outgoing rows under the local node and match replies by the packet's id and to.
        val row = harness.logs.currentLogs.single { it.message_type == "Packet" }
        assertEquals(MeshLog.NODE_NUM_LOCAL, row.fromNum)
        assertEquals(PortNum.ADMIN_APP.value, row.portNum)
        val logged = checkNotNull(row.fromRadio.packet)
        assertEquals(321, logged.id)
        assertEquals(REMOTE, logged.to)
        assertEquals(
            ByteString.EMPTY,
            AdminMessage.ADAPTER.decode(checkNotNull(logged.decoded).payload).session_passkey,
        )
        assertFalse(row.raw_message.contains(secret.hex()))
    }

    private fun lora(preset: Config.LoRaConfig.ModemPreset) = Config.Builder()
        .also { c ->
            c.lora =
                Config.LoRaConfig.Builder()
                    .also {
                        it.use_preset = true
                        it.modem_preset = preset
                    }
                    .build()
        }
        .build()

    private fun MeshPacket.admin(): AdminMessage? =
        decoded?.takeIf { it.portnum == PortNum.ADMIN_APP }?.let { AdminMessage.ADAPTER.decode(it.payload) }

    /** Sends [config] in the config download ahead of my_info; the SDK's fake transport downloads no config. */
    private class ConfigDownloadTransport(private val link: FakeRadioTransport, private val config: Config) :
        SdkRadioTransport by link {
        private var configSent = false

        override suspend fun send(frame: Frame) {
            val bytes = frame.bytes.toByteArray()
            val wantsConfig =
                ToRadio.ADAPTER.decode(bytes.copyOfRange(WireFraming.HEADER_SIZE, bytes.size)).want_config_id != null
            if (wantsConfig && !configSent) {
                configSent = true
                link.injectFrame(FromRadio.Builder().also { it.config = config }.build().toFrame())
            }
            link.send(frame)
        }
    }

    @Suppress("LongParameterList")
    private fun loraPacket(
        hopStart: Int,
        hopLimit: Int,
        rssi: Int? = null,
        snr: Float = 0f,
        port: PortNum = PortNum.PRIVATE_APP,
        payload: ByteString = ByteString.EMPTY,
        viaMqtt: Boolean = false,
        from: Int = REMOTE,
        bitfield: Int? = null,
    ) = MeshPacket.Builder()
        .also {
            it.from = from
            it.to = LOCAL
            it.hop_start = hopStart
            it.hop_limit = hopLimit
            it.rx_rssi = rssi
            it.rx_snr = snr
            it.via_mqtt = viaMqtt
            it.transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA
            it.decoded =
                Data.Builder()
                    .also { data ->
                        data.portnum = port
                        data.payload = payload
                        data.bitfield = bitfield
                    }
                    .build()
        }
        .build()

    private companion object {
        const val LOCAL = 0x0a0b0c0d
        const val REMOTE = 0x1234
        const val RANGE_TESTER = 0x2001
        const val MQTT_PEER = 0x2002
        const val RELAYED_PEER = 0x2003
        const val SDK_SESSION = 1L
        const val SECRET_PIN = 424242
    }
}
