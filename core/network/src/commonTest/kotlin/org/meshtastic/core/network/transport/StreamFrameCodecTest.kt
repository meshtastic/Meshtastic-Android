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
import io.kotest.property.arbitrary.filter
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

    // region Resynchronization after malformed input
    //
    // The framing protocol has no checksum, so a frame whose length prefix is corrupt cannot be detected as such —
    // the codec will consume that many bytes and may swallow a following frame. What it must always do is recover:
    // never throw, never wedge, and pick up the next well-formed frame.

    @Test
    fun `parses a frame whose header is preceded by a repeated START1`() {
        // Regression: the second 0x94 failed the START2 check and was *discarded*, taking the real header with it,
        // so the frame vanished silently. A repeated start byte is padding and must not cost a frame.
        val data = byteArrayOf(0x94.toByte(), 0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x42)

        data.forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertEquals(listOf(0x42.toByte()), receivedPackets[0].toList())
        assertEquals(0L, codec.framingDesyncCount, "padding is not a desync")
    }

    @Test
    fun `re-examines the rejected byte when a corrupt length ends in START1`() {
        // Declared length 0x0294 = 660 > MAX, and the low byte that triggered the rejection is itself START1 — it is
        // the start of the next frame's header. Dropping it would consume that header and desync into the payload.
        val corrupt = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x02, 0x94.toByte())
        val good = byteArrayOf(0xc3.toByte(), 0x00, 0x01, 0x42)

        (corrupt + good).forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertEquals(listOf(0x42.toByte()), receivedPackets[0].toList())
        assertEquals(1L, codec.framingDesyncCount)
    }

    @Test
    fun `parses a frame after a START1 run of any length`() {
        // Odd-length runs happened to work before the fix; even-length ones consumed the header. Cover both.
        for (runLength in 1..6) {
            receivedPackets.clear()
            val codec = StreamFrameCodec(onPacketReceived = { receivedPackets.add(it) }, logTag = "Test")
            val data =
                ByteArray(runLength) { 0x94.toByte() } + byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x42)

            data.forEach { codec.processInputByte(it) }

            assertEquals(1, receivedPackets.size, "run length $runLength should still yield one frame")
            assertEquals(listOf(0x42.toByte()), receivedPackets[0].toList(), "run length $runLength")
        }
    }

    @Test
    fun `parses a frame that arrives immediately after wake bytes`() {
        // WAKE_BYTES is four consecutive START1 — an even-length run, so this was the common real-world trigger.
        val data = StreamFrameCodec.WAKE_BYTES + byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x7F)

        data.forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertEquals(listOf(0x7F.toByte()), receivedPackets[0].toList())
    }

    @Test
    fun `recovers from a corrupt length prefix and parses the next frame`() {
        // Declared length 0x0201 = 513 > MAX_TO_FROM_RADIO_SIZE, so the header is rejected outright.
        val corrupt = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x02, 0x01)
        val good = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x02, 0x11, 0x22)

        (corrupt + good).forEach { codec.processInputByte(it) }

        assertEquals(1, receivedPackets.size)
        assertEquals(listOf(0x11.toByte(), 0x22.toByte()), receivedPackets[0].toList())
        assertEquals(1L, codec.framingDesyncCount)
    }

    @Test
    fun `recovers from a truncated frame and parses a later frame`() {
        // Declares 5 payload bytes but only 2 follow, so the codec absorbs the next frame's bytes as payload. That
        // frame is unrecoverable without a checksum; what matters is that the codec resynchronizes afterwards.
        val truncated = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x05, 0xAA.toByte(), 0xBB.toByte())
        val eaten = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x42)
        val recovered = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x43)

        (truncated + eaten + recovered).forEach { codec.processInputByte(it) }

        assertEquals(listOf(0x43.toByte()), receivedPackets.last().toList())
    }

    @Test
    fun `recovers from garbage between two frames`() {
        val first = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x01)
        val garbage = byteArrayOf(0x00, 0x7F, 0x3C, 0xFE.toByte(), 0x12, 0x94.toByte(), 0x00, 0x94.toByte())
        val second = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x02)

        (first + garbage + second).forEach { codec.processInputByte(it) }

        assertEquals(2, receivedPackets.size)
        assertEquals(listOf(0x01.toByte()), receivedPackets[0].toList())
        assertEquals(listOf(0x02.toByte()), receivedPackets[1].toList())
    }

    @Test
    fun `recovers from a frame header split by a desync in the middle of a payload`() {
        // A frame whose payload contains an incidental START1/START2 pair must still be delivered verbatim, and the
        // following frame must still parse.
        val payload = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01)
        val frame = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, payload.size.toByte()) + payload
        val next = byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x5A)

        (frame + next).forEach { codec.processInputByte(it) }

        assertEquals(2, receivedPackets.size)
        assertEquals(payload.toList(), receivedPackets[0].toList())
        assertEquals(listOf(0x5A.toByte()), receivedPackets[1].toList())
    }

    @Test
    fun `always parses a well-formed frame following non-protocol noise`() = runTest {
        // Noise with no START1 cannot be mistaken for a header, so recovery here is total and can be asserted for
        // arbitrary input rather than a handful of fixed cases.
        checkAll(Arb.byteArray(Arb.int(0, 300), Arb.byte().filter { it != 0x94.toByte() })) { noise ->
            val received = mutableListOf<ByteArray>()
            val codec = StreamFrameCodec(onPacketReceived = { received.add(it) })

            noise.forEach { codec.processInputByte(it) }
            byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x63).forEach { codec.processInputByte(it) }

            received.size.shouldBe(1)
            received[0].toList().shouldBe(listOf(0x63.toByte()))
        }
    }

    @Test
    fun `desync count tracks framing errors and resets with the codec`() {
        assertEquals(0L, codec.framingDesyncCount)

        byteArrayOf(0x94.toByte(), 0x00).forEach { codec.processInputByte(it) }
        assertEquals(1L, codec.framingDesyncCount)

        // A clean frame after the desync still parses, and does not add to the count.
        byteArrayOf(0x94.toByte(), 0xc3.toByte(), 0x00, 0x01, 0x42).forEach { codec.processInputByte(it) }
        assertEquals(1L, codec.framingDesyncCount)
        assertEquals(1, receivedPackets.size)

        codec.reset()
        assertEquals(0L, codec.framingDesyncCount)
    }

    // endregion

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
