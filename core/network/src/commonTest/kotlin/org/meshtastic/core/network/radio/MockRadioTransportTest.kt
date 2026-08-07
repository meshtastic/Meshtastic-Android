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

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ToRadio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            received += FromRadio.ADAPTER.decode(bytes)
        }
    }

    @Test
    fun `an open transport delivers the delayed fake acknowledgement`() = runTest {
        val callback = RecordingCallback()
        val transport = MockRadioTransport(callback = callback, scope = backgroundScope, address = "mock")
        val outbound = ToRadio(packet = MeshPacket(id = 77, from = 1234, want_ack = true)).encode()

        transport.start()
        assertTrue(transport.handleSendToRadio(outbound))
        advanceTimeBy(MockRadioTransport.FAKE_ACK_DELAY.inWholeMilliseconds)
        runCurrent()

        assertTrue(
            callback.received.any { it.packet?.decoded?.request_id == 77 },
            "an open mock transport must deliver the fake ACK",
        )
        transport.close()
    }

    @Test
    fun `close is terminal and suppresses delayed acknowledgements`() = runTest {
        val callback = RecordingCallback()
        val transport = MockRadioTransport(callback = callback, scope = backgroundScope, address = "mock")
        val outbound = ToRadio(packet = MeshPacket(id = 77, from = 1234, want_ack = true)).encode()

        transport.start()
        assertTrue(transport.handleSendToRadio(outbound))
        val receivedBeforeClose = callback.received.toList()

        transport.close()
        advanceTimeBy(MockRadioTransport.FAKE_ACK_DELAY.inWholeMilliseconds)
        runCurrent()

        assertEquals(receivedBeforeClose, callback.received, "a delayed fake ACK must not escape after close")
        assertFalse(transport.handleSendToRadio(outbound))
        transport.start()
        runCurrent()
        assertEquals(1, callback.connects, "a closed mock transport must not reconnect")
    }
}
