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
package org.meshtastic.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DataPacketTest {
    @Test
    fun `DataPacket sfppHash is nullable and correctly set`() {
        val hash = byteArrayOf(1, 2, 3, 4)
        val packet = DataPacket(to = "to", channel = 0, text = "hello").copy(sfppHash = hash)
        assertArrayEquals(hash, packet.sfppHash)

        val packetNoHash = DataPacket(to = "to", channel = 0, text = "hello")
        assertEquals(null, packetNoHash.sfppHash)
    }

    @Test
    fun `MessageStatus SFPP_CONFIRMED exists`() {
        val status = MessageStatus.SFPP_CONFIRMED
        assertEquals("SFPP_CONFIRMED", status.name)
    }

    @Test
    fun `DataPacket serialization preserves sfppHash`() {
        val hash = byteArrayOf(5, 6, 7, 8)
        val packet =
            DataPacket(to = "to", channel = 0, text = "test")
                .copy(sfppHash = hash, status = MessageStatus.SFPP_CONFIRMED)

        val json = Json { isLenient = true }
        val encoded = json.encodeToString(DataPacket.serializer(), packet)
        val decoded = json.decodeFromString(DataPacket.serializer(), encoded)

        assertEquals(packet.status, decoded.status)
        assertArrayEquals(hash, decoded.sfppHash)
    }

    @Test
    fun `DataPacket equals and hashCode include sfppHash`() {
        val hash1 = byteArrayOf(1, 2, 3)
        val hash2 = byteArrayOf(4, 5, 6)
        val p1 = DataPacket(to = "to", channel = 0, text = "text").copy(sfppHash = hash1)
        val p2 = DataPacket(to = "to", channel = 0, text = "text").copy(sfppHash = hash1)
        val p3 = DataPacket(to = "to", channel = 0, text = "text").copy(sfppHash = hash2)
        val p4 = DataPacket(to = "to", channel = 0, text = "text").copy(sfppHash = null)

        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())

        assertNotEquals(p1, p3)
        assertNotEquals(p1, p4)
        assertNotEquals(p1.hashCode(), p3.hashCode())
    }
}
