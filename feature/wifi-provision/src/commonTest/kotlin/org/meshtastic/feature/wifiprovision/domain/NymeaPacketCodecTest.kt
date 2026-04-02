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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.wifiprovision.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NymeaPacketCodecTest {

    // -----------------------------------------------------------------------
    // encode()
    // -----------------------------------------------------------------------

    @Test
    fun `encode appends newline terminator`() {
        val packets = NymeaPacketCodec.encode("{}")
        val reassembled = packets.joinToString("") { it.decodeToString() }
        assertTrue(reassembled.endsWith("\n"), "Encoded payload must end with newline")
    }

    @Test
    fun `encode short message fits in single packet`() {
        val packets = NymeaPacketCodec.encode("{\"c\":4}")
        assertEquals(1, packets.size, "Short JSON should fit in a single packet")
        assertEquals("{\"c\":4}\n", packets[0].decodeToString())
    }

    @Test
    fun `encode long message splits across multiple packets`() {
        // 20-byte max packet size (default). Use a payload that exceeds it.
        val json = "A".repeat(50)
        val packets = NymeaPacketCodec.encode(json, maxPacketSize = 20)

        assertTrue(packets.size > 1, "Long payload should be split")
        packets.forEach { packet -> assertTrue(packet.size <= 20, "Each packet must be ≤ maxPacketSize") }

        // Reassemble and verify content
        val reassembled = packets.joinToString("") { it.decodeToString() }
        assertEquals(json + "\n", reassembled)
    }

    @Test
    fun `encode boundary payload exactly fills packets`() {
        // 19 chars + 1 newline = 20 bytes = exactly 1 packet at maxPacketSize=20
        val json = "A".repeat(19)
        val packets = NymeaPacketCodec.encode(json, maxPacketSize = 20)
        assertEquals(1, packets.size)
        assertEquals(20, packets[0].size)
    }

    @Test
    fun `encode boundary payload one byte over splits into two packets`() {
        // 20 chars + 1 newline = 21 bytes → 2 packets at maxPacketSize=20
        val json = "A".repeat(20)
        val packets = NymeaPacketCodec.encode(json, maxPacketSize = 20)
        assertEquals(2, packets.size)
        assertEquals(20, packets[0].size)
        assertEquals(1, packets[1].size)
    }

    @Test
    fun `encode empty string produces single packet with just newline`() {
        val packets = NymeaPacketCodec.encode("")
        assertEquals(1, packets.size)
        assertEquals("\n", packets[0].decodeToString())
    }

    @Test
    fun `encode custom maxPacketSize is respected`() {
        val json = "ABCDEFGHIJ" // 10 chars + 1 newline = 11 bytes
        val packets = NymeaPacketCodec.encode(json, maxPacketSize = 4)
        assertEquals(3, packets.size) // 4 + 4 + 3
        packets.forEach { assertTrue(it.size <= 4) }
        assertEquals(json + "\n", packets.joinToString("") { it.decodeToString() })
    }

    // -----------------------------------------------------------------------
    // Reassembler
    // -----------------------------------------------------------------------

    @Test
    fun `reassembler returns complete message on single feed with terminator`() {
        val reassembler = NymeaPacketCodec.Reassembler()
        val result = reassembler.feed("{\"c\":4}\n".encodeToByteArray())
        assertEquals("{\"c\":4}", result)
    }

    @Test
    fun `reassembler buffers partial data and returns null`() {
        val reassembler = NymeaPacketCodec.Reassembler()
        assertNull(reassembler.feed("{\"c\":".encodeToByteArray()))
        assertNull(reassembler.feed("4}".encodeToByteArray()))
    }

    @Test
    fun `reassembler completes when terminator arrives in later chunk`() {
        val reassembler = NymeaPacketCodec.Reassembler()
        assertNull(reassembler.feed("{\"c\":".encodeToByteArray()))
        assertNull(reassembler.feed("4}".encodeToByteArray()))
        val result = reassembler.feed("\n".encodeToByteArray())
        assertEquals("{\"c\":4}", result)
    }

    @Test
    fun `reassembler handles multiple messages sequentially`() {
        val reassembler = NymeaPacketCodec.Reassembler()
        val first = reassembler.feed("first\n".encodeToByteArray())
        assertEquals("first", first)

        val second = reassembler.feed("second\n".encodeToByteArray())
        assertEquals("second", second)
    }

    @Test
    fun `reassembler reset clears buffered data`() {
        val reassembler = NymeaPacketCodec.Reassembler()
        assertNull(reassembler.feed("partial".encodeToByteArray()))
        reassembler.reset()
        // After reset, the partial data is gone — new message starts fresh
        val result = reassembler.feed("fresh\n".encodeToByteArray())
        assertEquals("fresh", result)
    }

    @Test
    fun `encode and reassembler round-trip`() {
        val json = """{"c":1,"p":{"e":"MyNetwork","p":"secret123"}}"""
        val packets = NymeaPacketCodec.encode(json)
        val reassembler = NymeaPacketCodec.Reassembler()

        var result: String? = null
        for (packet in packets) {
            result = reassembler.feed(packet)
        }
        assertEquals(json, result)
    }

    @Test
    fun `encode and reassembler round-trip with small packet size`() {
        val json = """{"c":0,"r":0,"p":[{"e":"TestNet","m":"AA:BB","s":85,"p":1}]}"""
        val packets = NymeaPacketCodec.encode(json, maxPacketSize = 8)
        assertTrue(packets.size > 1, "Should require multiple packets with small MTU")

        val reassembler = NymeaPacketCodec.Reassembler()
        var result: String? = null
        for (packet in packets) {
            result = reassembler.feed(packet)
        }
        assertEquals(json, result)
    }
}
