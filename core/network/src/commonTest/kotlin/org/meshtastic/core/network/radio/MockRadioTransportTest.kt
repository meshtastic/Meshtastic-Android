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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Demo Mode's contract with the app.
 *
 * The regression these tests exist for: the mock used to replay one combined frame array for *any* `want_config_id`, so
 * stage 2 re-sent `my_info`, which resets the app's handshake state machine and makes the stage-2 `config_complete_id`
 * get rejected. The handshake then never finished and Demo Mode showed an empty node list, no messages and an empty map
 * — in every build, debug included.
 */
class MockRadioTransportTest {

    private class RecordingCallback : RadioTransportCallback {
        var connects = 0
        val received = mutableListOf<FromRadio>()

        override fun onConnect() {
            connects++
        }

        override fun onDisconnect(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) =
            Unit

        override fun handleFromRadio(bytes: ByteArray) {
            received.add(FromRadio.ADAPTER.decode(bytes))
        }

        val nodeInfos
            get() = received.mapNotNull { it.node_info }

        val completions
            get() = received.mapNotNull { it.config_complete_id }

        val packets
            get() = received.mapNotNull { it.packet }

        fun packetsOn(portNum: PortNum) = packets.filter { it.decoded?.portnum == portNum }
    }

    /**
     * The transport must not share the test's own scope: its live-telemetry loop never completes, so `runTest` would
     * wait on it forever. A cancellable child scope on the test scheduler keeps virtual time shared but lets the test
     * end.
     */
    private fun TestScope.transportScope() = CoroutineScope(StandardTestDispatcher(testScheduler))

    @Test
    fun `start signals onConnect without emitting frames`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        try {
            MockRadioTransport(callback, scope, address = "").start()

            assertEquals(1, callback.connects)
            assertTrue(callback.received.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `config stage answers with my_info and its own nonce but never with node info`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        try {
            val transport = MockRadioTransport(callback, scope, address = "")

            transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE).encode())

            assertEquals(1, callback.received.count { it.my_info != null }, "stage 1 must announce our node")
            assertNotNull(callback.received.firstNotNullOfOrNull { it.metadata }, "stage 1 must send metadata")
            assertTrue(callback.received.any { it.config?.lora != null }, "stage 1 must send LoRa config")
            assertTrue(callback.received.any { it.channel != null }, "stage 1 must announce the primary channel")
            assertTrue(callback.nodeInfos.isEmpty(), "node info belongs to stage 2 only")
            assertEquals(listOf(HandshakeConstants.CONFIG_NONCE), callback.completions)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `node info stage does not re-send my_info because that would reset the handshake`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        try {
            val transport = MockRadioTransport(callback, scope, address = "")
            transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE).encode())
            callback.received.clear()

            transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE).encode())

            assertTrue(callback.received.none { it.my_info != null }, "re-sending my_info breaks stage 2")
            assertEquals(listOf(HandshakeConstants.NODE_INFO_NONCE), callback.completions)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `node info stage populates a mesh whose nodes all render`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        try {
            val transport = MockRadioTransport(callback, scope, address = "")
            transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE).encode())
            callback.received.clear()

            transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE).encode())

            val nodes = callback.nodeInfos
            assertTrue(nodes.size >= MIN_DEMO_NODES, "expected a populated node list, got ${nodes.size}")
            assertEquals(nodes.size, nodes.distinctBy { it.num }.size, "node numbers must be unique")
            nodes.forEach { node ->
                val user = assertNotNull(node.user, "node ${node.num} has no user")
                // An UNSET hardware model nulls the denormalised name columns, which flags the node as incomplete and
                // hides it from search.
                assertTrue(user.hw_model != HardwareModel.UNSET, "node ${node.num} has no hardware model")
                assertTrue(user.long_name.isNotBlank(), "node ${node.num} has no long name")
                assertTrue(user.short_name.isNotBlank(), "node ${node.num} has no short name")
                // Without last_heard every node reads as offline and vanishes under the "online only" filter.
                assertTrue(node.last_heard > 0, "node ${node.num} has no last_heard")
                val position = assertNotNull(node.position, "node ${node.num} has no position")
                assertTrue((position.latitude_i ?: 0) != 0, "node ${node.num} has no latitude")
                assertTrue((position.longitude_i ?: 0) != 0, "node ${node.num} has no longitude")
                assertNotNull(node.device_metrics?.battery_level, "node ${node.num} has no battery level")
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `an unrecognised want_config_id is ignored rather than answered with the wrong stage`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        try {
            val transport = MockRadioTransport(callback, scope, address = "")

            transport.handleSendToRadio(ToRadio(want_config_id = 1234).encode())

            assertTrue(callback.received.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `seeded traffic supplies a channel thread a direct thread positions and telemetry`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        try {
            val transport = MockRadioTransport(callback, scope, address = "")
            transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE).encode())
            val myNodeNum = assertNotNull(callback.received.firstNotNullOfOrNull { it.my_info }).my_node_num
            transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE).encode())
            callback.received.clear()

            testScheduler.advanceTimeBy(SEED_WINDOW_MS)

            val texts = callback.packetsOn(PortNum.TEXT_MESSAGE_APP)
            val broadcasts = texts.filter { it.to == BROADCAST_ADDR }
            val directs = texts.filter { it.to == myNodeNum }
            assertTrue(broadcasts.size >= 2, "expected a channel conversation, got ${broadcasts.size} messages")
            assertTrue(directs.isNotEmpty(), "expected a direct-message thread")
            // A broadcast has to land on the announced primary channel or it opens an unnamed "Channel N" thread.
            assertTrue(broadcasts.all { it.channel == 0 }, "channel messages must use the primary channel")
            assertEquals(texts.size, texts.distinctBy { it.id }.size, "duplicate packet ids are silently dropped")

            assertTrue(callback.packetsOn(PortNum.POSITION_APP).isNotEmpty(), "expected position packets")

            val telemetry = callback.packetsOn(PortNum.TELEMETRY_APP)
            assertTrue(telemetry.size >= 2, "expected device telemetry from several nodes")
            val decoded = telemetry.mapNotNull { it.decoded?.payload?.let(Telemetry.ADAPTER::decode) }
            assertTrue(decoded.any { it.device_metrics != null }, "expected device metrics")
            val environment = decoded.mapNotNull { it.environment_metrics }
            assertTrue(
                environment.any { it.temperature != null && it.relative_humidity != null },
                "the Environment tab needs temperature AND humidity on the same telemetry",
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `received packets look like direct LoRa receptions so signal metrics reach the node list`() = runTest {
        val callback = RecordingCallback()
        val scope = transportScope()
        try {
            val transport = MockRadioTransport(callback, scope, address = "")
            transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE).encode())
            transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE).encode())
            callback.received.clear()

            testScheduler.advanceTimeBy(SEED_WINDOW_MS)

            val positions = callback.packetsOn(PortNum.POSITION_APP)
            assertTrue(positions.isNotEmpty())
            // The app only harvests SNR/RSSI from LoRa packets, and only infers a hop count when hop_start is set.
            assertTrue(
                positions.all { it.transport_mechanism == MeshPacket.TransportMechanism.TRANSPORT_LORA },
                "packets left at the default transport are treated as internal and carry no signal info",
            )
            assertTrue(positions.all { it.hop_start > 0 }, "hop_start must be set for hop distance to be derivable")
            assertTrue(positions.all { (it.rx_time ?: 0) > 0 }, "rx_time drives lastHeard")
            assertTrue(positions.any { it.rx_snr != 0f }, "expected varied SNR readings")
            assertTrue(positions.any { (it.rx_rssi ?: 0) != 0 }, "expected varied RSSI readings")
            // At least one node must look like a direct neighbour, or nothing ever gets a signal reading at all.
            assertTrue(positions.any { it.hop_start == it.hop_limit }, "expected at least one direct neighbour")
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val BROADCAST_ADDR = -1
        const val MIN_DEMO_NODES = 8

        /** Comfortably longer than the seed pass, but shorter than the first live-telemetry tick. */
        const val SEED_WINDOW_MS = 10_000L
    }
}
