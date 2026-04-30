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
package org.meshtastic.core.network.transport

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamFrameCodecTest {

    private val receivedPackets = mutableListOf<ByteArray>()
    private val codec = StreamFrameCodec(onPacketReceived = { receivedPackets.add(it) }, logTag = "Test")

    @Test
    fun `processInputByte delivers a 1-byte packet`() {
        val packet = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x42)

        packet.forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertEquals(listOf(0x42.toByte()), receivedPackets[0].toList())
    }

    @Test
    fun `processInputByte handles zero length packet`() {
        val packet = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x00)

        packet.forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertTrue(receivedPackets[0].isEmpty())
    }

    @Test
    fun `processInputByte loses sync on invalid START2`() {
        // START1, wrong START2, START1, START2, LenMSB=0, LenLSB=1, payload
        val data = byteArrayOf(0x94.toByte(), 0x00, 0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x55)

        data.forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertEquals(listOf(0x55.toByte()), receivedPackets[0].toList())
    }

    @Test
    fun `frameAndSend and processInputByte are inverse`() = runTest {
        checkAll(Arb.byteArray(Arb.int(0, 512), Arb.byte())) { payload ->
            var received: ByteArray? = null
            val codec = StreamFrameCodec(onPacketReceived = { received = it })

            val bytes = mutableListOf<ByteArray>()
            codec.frameAndSend(payload, sendBytes = { bytes.add(it) })

            bytes.forEach { arr -> arr.forEach { codec.processInputByte(it) } }

            received.shouldNotBeNull()
            received.shouldBe(payload)
        }
    }

    @Test
    fun `processInputByte is robust against random noise`() = runTest {
        checkAll(Arb.byteArray(Arb.int(0, 1000), Arb.byte())) { noise ->
            val codec = StreamFrameCodec(onPacketReceived = { /* ignore */ })
            noise.forEach { codec.processInputByte(it) }
            // Should not crash
        }
    }

    @Test
    fun `processInputByte handles multiple packets sequentially`() {
        val packet1 = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x11)
        val packet2 = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x22)

        packet1.forEach { codec.processInputByte(it) }
        packet2.forEach { codec.processInputByte(it) }

        assertEquals(2, receivedPackets.size)
        assertEquals(listOf(0x11.toByte()), receivedPackets[0].toList())
        assertEquals(listOf(0x22.toByte()), receivedPackets[1].toList())
    }

    @Test
    fun `processInputByte handles large packet up to MAX_TO_FROM_RADIO_SIZE`() {
        val size = 512
        val payload = ByteArray(size) { it.toByte() }
        val header = byteArrayOf(0x94.toByte(), 0xc3.toByte(), (size shr 8).toByte(), (size and 0xff).toByte())

        header.forEach { codec.processInputByte(it) }
        payload.forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertEquals(payload.toList(), receivedPackets[0].toList())
    }

    @Test
    fun `processInputByte loses sync on overly large packet length`() {
        // 513 bytes is > 512
        val header = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x02, 0x01)

        header.forEach { codec.processInputByte(it) }

        assertTrue(receivedPackets.isEmpty())
    }

    @Test
    fun `processInputByte handles multi-byte payload`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val header = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x05)

        header.forEach { codec.processInputByte(it) }
        payload.forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertEquals(payload.toList(), receivedPackets[0].toList())
    }

    @Test
    fun `reset clears framing state`() {
        // Feed partial header
        codec.processInputByte(0x94.toByte())
        codec.processInputByte(0xc3.toByte())

        // Reset mid-stream
        codec.reset()

        // Now feed a complete packet — should work from scratch
        val packet = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0xAA.toByte())
        packet.forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertEquals(listOf(0xAA.toByte()), receivedPackets[0].toList())
    }

    @Test
    fun `frameAndSend produces correct header for 1-byte payload`() = runTest {
        val payload = byteArrayOf(0x42.toByte())
        val sentBytes = mutableListOf<ByteArray>()

        codec.frameAndSend(payload, sendBytes = { sentBytes.add(it) })

        // First sent bytes are the 4-byte header, second is the payload
        assertEquals(2, sentBytes.size)
        val header = sentBytes[0]
        assertEquals(4, header.size)
        assertEquals(0x94.toByte(), header[0])
        assertEquals(0xc3.toByte(), header[1])
        assertEquals(0x00.toByte(), header[2])
        assertEquals(0x01.toByte(), header[3])

        val sentPayload = sentBytes[1]
        assertEquals(payload.toList(), sentPayload.toList())
    }

    @Test
    fun `WAKE_BYTES is four START1 bytes`() {
        assertEquals(4, StreamFrameCodec.WAKE_BYTES.size)
        StreamFrameCodec.WAKE_BYTES.forEach { assertEquals(0x94.toByte(), it) }
    }

    @Test
    fun `DEFAULT_TCP_PORT is 4403`() {
        assertEquals(4403, StreamFrameCodec.DEFAULT_TCP_PORT)
    }
}
