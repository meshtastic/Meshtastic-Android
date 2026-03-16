/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio

class TCPInterfaceTest {

    @Test
    fun testHeartbeatFraming() = runTest {
        val sentBytes = mutableListOf<ByteArray>()

        val codec = StreamFrameCodec(onPacketReceived = {}, logTag = "Test")

        val heartbeat = ToRadio(heartbeat = Heartbeat()).encode()
        codec.frameAndSend(heartbeat, { sentBytes.add(it) })

        // First sent bytes are the 4-byte header, second is the payload
        assertEquals(2, sentBytes.size)
        val header = sentBytes[0]
        assertEquals(4, header.size)
        assertEquals(0x94.toByte(), header[0])
        assertEquals(0xc3.toByte(), header[1])

        val payload = sentBytes[1]
        assertEquals(heartbeat.toList(), payload.toList())
    }

    @Test
    fun testServicePort() {
        assertEquals(4403, TCPInterface.SERVICE_PORT)
    }
}
