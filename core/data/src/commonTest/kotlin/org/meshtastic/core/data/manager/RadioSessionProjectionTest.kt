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
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeCommandSender
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioSessionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadioSessionProjectionTest {

    private class Harness(scope: CoroutineScope) {
        val radio =
            FakeRadioInterfaceService(scope).apply {
                setDeviceAddress("tcp:projection")
                connect()
            }
        val session: RadioSessionContext = checkNotNull(radio.activeSession.value)
        val nodeRepository = FakeNodeRepository()
        val logs = FakeMeshLogRepository()
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
                radioConfigRepository = FakeRadioConfigRepository(),
                serviceStateWriter = FakeServiceRepository(),
                analytics = mock(MockMode.autofill),
                commandSender = FakeCommandSender(),
                notificationPrefs = FakeNotificationPrefs(),
                radioInterfaceService = radio,
                scope = scope.asServiceScope(),
            )
        val projection =
            radioSessionProjection(
                nodeManager,
                radio,
                scope,
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

    private fun loraPacket(hopStart: Int, hopLimit: Int, rssi: Int, snr: Float) = MeshPacket.Builder()
        .also {
            it.from = REMOTE
            it.to = LOCAL
            it.hop_start = hopStart
            it.hop_limit = hopLimit
            it.rx_rssi = rssi
            it.rx_snr = snr
            it.transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA
            it.decoded = Data.Builder().also { data -> data.portnum = PortNum.PRIVATE_APP }.build()
        }
        .build()

    private companion object {
        const val LOCAL = 0x0a0b0c0d
        const val REMOTE = 0x1234
        const val SDK_SESSION = 1L
    }
}
