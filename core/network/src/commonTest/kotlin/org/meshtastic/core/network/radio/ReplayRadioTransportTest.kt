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

import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.ToRadio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReplayRadioTransportTest {

    private val configFrames = listOf(FromRadio(my_info = MyNodeInfo(my_node_num = 1)))
    private val nodeFrames =
        listOf(FromRadio(node_info = NodeInfo(num = 42)), FromRadio(node_info = NodeInfo(num = 43)))
    private val packetFrames = (1..3).map { FromRadio(packet = MeshPacket(id = it)) }

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
    }

    /** Encodes the three sections in the `replay_server.py --export` asset format. */
    private fun asset(
        config: List<FromRadio> = configFrames,
        nodes: List<FromRadio> = nodeFrames,
        packets: List<FromRadio> = packetFrames,
    ): ByteArray {
        val buffer = Buffer()
        fun writeFrames(frames: List<FromRadio>, withCount: Boolean) {
            if (withCount) buffer.writeInt(frames.size)
            frames.forEach {
                val bytes = it.encode()
                buffer.writeInt(bytes.size)
                buffer.write(bytes)
            }
        }
        writeFrames(config, withCount = true)
        writeFrames(nodes, withCount = true)
        writeFrames(packets, withCount = false)
        return buffer.readByteArray()
    }

    @Test
    fun `start signals onConnect without emitting frames`() = runTest {
        val callback = RecordingCallback()
        val transport = ReplayRadioTransport(callback, this, address = "", frames = asset(), packetDelayMs = 0)

        transport.start()

        assertEquals(1, callback.connects)
        assertTrue(callback.received.isEmpty())
    }

    @Test
    fun `config nonce is answered with config frames and the echoed nonce only`() = runTest {
        val callback = RecordingCallback()
        val transport = ReplayRadioTransport(callback, this, address = "", frames = asset(), packetDelayMs = 0)

        transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE).encode())
        testScheduler.advanceUntilIdle()

        assertEquals(configFrames + FromRadio(config_complete_id = HandshakeConstants.CONFIG_NONCE), callback.received)
    }

    @Test
    fun `node nonce is answered with the node db then the packet stream`() = runTest {
        val callback = RecordingCallback()
        val transport = ReplayRadioTransport(callback, this, address = "", frames = asset(), packetDelayMs = 0)

        transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE).encode())
        testScheduler.advanceUntilIdle()

        val expected = nodeFrames + FromRadio(config_complete_id = HandshakeConstants.NODE_INFO_NONCE) + packetFrames
        assertEquals(expected, callback.received)
    }

    @Test
    fun `a second node nonce does not restart the packet stream`() = runTest {
        val callback = RecordingCallback()
        val transport = ReplayRadioTransport(callback, this, address = "", frames = asset(), packetDelayMs = 0)

        transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE).encode())
        testScheduler.advanceUntilIdle()
        transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE).encode())
        testScheduler.advanceUntilIdle()

        val packetsSent = callback.received.count { it.packet != null }
        assertEquals(packetFrames.size, packetsSent)
    }

    @Test
    fun `non-handshake traffic is ignored`() = runTest {
        val callback = RecordingCallback()
        val transport = ReplayRadioTransport(callback, this, address = "", frames = asset(), packetDelayMs = 0)

        transport.handleSendToRadio(ToRadio(packet = MeshPacket(id = 99)).encode())
        testScheduler.advanceUntilIdle()

        assertTrue(callback.received.isEmpty())
    }

    // ── Malformed-asset handling: the parser must fail fast with a clear error, never underflow or over-allocate. ──

    /** Builds a raw asset body so we can craft deliberately-corrupt inputs the [asset] helper cannot. */
    private fun rawAsset(block: Buffer.() -> Unit): ByteArray = Buffer().apply(block).readByteArray()

    @Test
    fun `truncated section count is rejected`() = runTest {
        // Only two bytes — not enough for the leading u32 config count.
        assertFailsWith<IllegalArgumentException> {
            ReplayRadioTransport(RecordingCallback(), this, address = "", frames = byteArrayOf(0x00, 0x01))
        }
    }

    @Test
    fun `frame length exceeding remaining bytes is rejected`() = runTest {
        val frames = rawAsset {
            writeInt(1) // config: 1 frame…
            writeInt(9_999) // …declaring 9999 bytes…
            write(byteArrayOf(1, 2, 3)) // …but only 3 follow.
        }
        assertFailsWith<IllegalArgumentException> {
            ReplayRadioTransport(RecordingCallback(), this, address = "", frames = frames)
        }
    }

    @Test
    fun `counted section with fewer frames than declared is rejected`() = runTest {
        val frames = rawAsset {
            writeInt(3) // config: claims 3 frames…
            writeInt(2)
            write(byteArrayOf(1, 2)) // …but provides only 1, then EOF.
        }
        assertFailsWith<IllegalArgumentException> {
            ReplayRadioTransport(RecordingCallback(), this, address = "", frames = frames)
        }
    }

    @Test
    fun `empty config and node sections with no packets are valid`() = runTest {
        val callback = RecordingCallback()
        val frames = rawAsset {
            writeInt(0) // 0 config frames
            writeInt(0) // 0 node frames
            // no packet bytes
        }
        val transport = ReplayRadioTransport(callback, this, address = "", frames = frames, packetDelayMs = 0)

        transport.start()
        transport.handleSendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE).encode())
        testScheduler.advanceUntilIdle()

        // Only the injected config_complete — no nodes, no packets — proves zero-length sections parse cleanly.
        assertEquals(listOf(FromRadio(config_complete_id = HandshakeConstants.NODE_INFO_NONCE)), callback.received)
    }
}
