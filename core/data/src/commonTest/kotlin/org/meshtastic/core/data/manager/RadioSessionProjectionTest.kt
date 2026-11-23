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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakePacketHandler
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
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
                packetHandler = FakePacketHandler(),
                notificationPrefs = FakeNotificationPrefs(),
                radioInterfaceService = radio,
                scope = scope.asServiceScope(),
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
        val link = FakeRadioTransport(TransportIdentity("fake:config"), autoHandshake = true, nodeNum = LOCAL)
        val client =
            RadioClient.Builder()
                .transport(ConfigDownloadTransport(link, lora(Config.LoRaConfig.ModemPreset.LONG_FAST)))
                .storage(InMemoryStorageProvider())
                .coroutineContext(backgroundScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .sessionReceive()
                .build()
        val events = checkNotNull(client.sessionEvents)
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { if (it !is RadioSessionEvent.Ready) harness.projection.project(it, harness.session) }
        }
        client.connect()
        runCurrent()
        assertNull(harness.radioConfig.lastSetLocalConfig)

        val written = lora(Config.LoRaConfig.ModemPreset.MEDIUM_FAST)
        val write = async { client.admin.setConfig(written) }
        runCurrent()
        val request = link.outboundPackets().last { it.admin()?.set_config != null }
        link.injectRoutingAck(request.id, fromNode = LOCAL)
        assertTrue(write.await() is AdminResult.Success)
        runCurrent()

        assertEquals(written, harness.radioConfig.lastSetLocalConfig)
        client.disconnect()
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
