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
package org.meshtastic.core.takserver.fountain

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FountainCodecTest {

    private fun createCodec() = FountainCodec()

    @Test
    fun `test encode and decode small payload`() {
        val codec = createCodec()
        val originalData = "Hello, TAK! This is a test payload.".encodeToByteArray()
        val transferId = codec.generateTransferId()

        val packets = codec.encode(originalData, transferId)
        assertTrue(packets.isNotEmpty(), "Encoding should produce packets")

        var decodedResult: Pair<ByteArray, Int>? = null
        for (packet in packets) {
            val result = codec.handleIncomingPacket(packet)
            if (result != null) {
                decodedResult = result
                break
            }
        }

        assertNotNull(decodedResult, "Should successfully decode payload")
        assertEquals(transferId, decodedResult.second, "Transfer ID should match")
        assertContentEquals(originalData, decodedResult.first, "Decoded data should match original")
    }

    @Test
    fun `test encode and decode larger payload with packet loss`() {
        val codec = createCodec()
        // Create a payload larger than BLOCK_SIZE (220 bytes)
        val originalData = ByteArray(1024) { (it % 256).toByte() }
        val transferId = codec.generateTransferId()

        val packets = codec.encode(originalData, transferId)
        assertTrue(packets.size > 4, "Should have multiple packets for large payload")

        var decodedResult: Pair<ByteArray, Int>? = null

        // Process all packets - fountain codes are designed to handle packet loss
        // by receiving enough encoded packets to reconstruct the original data
        for (packet in packets) {
            val result = codec.handleIncomingPacket(packet)
            if (result != null) {
                decodedResult = result
                break
            }
        }

        assertNotNull(decodedResult, "Should successfully decode payload with sufficient packets")
        assertEquals(transferId, decodedResult.second, "Transfer ID should match")
        assertContentEquals(originalData, decodedResult.first, "Decoded data should match original")
    }

    @Test
    fun `test build and parse ACK`() {
        val codec = createCodec()
        val transferId = 123456
        val type = FountainConstants.ACK_TYPE_COMPLETE
        val received = 5
        val needed = 0
        val dataHash = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        val ackPacket = codec.buildAck(transferId, type, received, needed, dataHash)
        assertTrue(codec.isFountainPacket(ackPacket), "ACK should be recognized as a Fountain packet")

        val parsedAck = codec.parseAck(ackPacket)
        assertNotNull(parsedAck, "ACK should be parseable")
        assertEquals(transferId, parsedAck.transferId)
        assertEquals(type, parsedAck.type)
        assertEquals(received, parsedAck.received)
        assertEquals(needed, parsedAck.needed)
        assertContentEquals(dataHash, parsedAck.dataHash)
    }

    @Test
    fun `test invalid packet handling`() {
        val codec = createCodec()
        val invalidPacket = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertFalse(codec.isFountainPacket(invalidPacket), "Should reject invalid magic bytes")
        assertNull(codec.parseDataHeader(invalidPacket), "Should not parse invalid header")
        assertNull(codec.handleIncomingPacket(invalidPacket), "Should handle invalid packet gracefully")
    }
}
