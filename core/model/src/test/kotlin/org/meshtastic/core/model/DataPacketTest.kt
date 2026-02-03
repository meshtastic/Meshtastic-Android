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

import android.os.Parcel
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataPacketTest {
    @Test
    fun `DataPacket sfppHash is nullable and correctly set`() {
        val hash = byteArrayOf(1, 2, 3, 4).toByteString()
        val packet = DataPacket(to = "to", channel = 0, text = "hello").copy(sfppHash = hash)
        assertEquals(hash, packet.sfppHash)

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
        val hash = byteArrayOf(5, 6, 7, 8).toByteString()
        val packet =
            DataPacket(to = "to", channel = 0, text = "test")
                .copy(sfppHash = hash, status = MessageStatus.SFPP_CONFIRMED)

        val json = Json { isLenient = true }
        val encoded = json.encodeToString(DataPacket.serializer(), packet)
        val decoded = json.decodeFromString(DataPacket.serializer(), encoded)

        assertEquals(packet.status, decoded.status)
        assertEquals(hash, decoded.sfppHash)
    }

    @Test
    fun `DataPacket equals and hashCode include sfppHash`() {
        val hash1 = byteArrayOf(1, 2, 3).toByteString()
        val hash2 = byteArrayOf(4, 5, 6).toByteString()
        val fixedTime = 1000L
        val base = DataPacket(to = "to", channel = 0, text = "text").copy(time = fixedTime)
        val p1 = base.copy(sfppHash = hash1)
        val p2 = base.copy(sfppHash = byteArrayOf(1, 2, 3).toByteString()) // same content
        val p3 = base.copy(sfppHash = hash2)
        val p4 = base.copy(sfppHash = null)

        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())

        assertNotEquals(p1, p3)
        assertNotEquals(p1, p4)
        assertNotEquals(p1.hashCode(), p3.hashCode())
    }

    @Test
    fun `readFromParcel maintains alignment and updates all fields including bytes and dataType`() {
        val original =
            DataPacket(
                to = "recipient",
                bytes = byteArrayOf(1, 2, 3),
                dataType = 42,
                from = "sender",
                time = 123456789L,
                id = 100,
                status = MessageStatus.RECEIVED,
                hopLimit = 3,
                channel = 1,
                wantAck = true,
                hopStart = 5,
                snr = 1.5f,
                rssi = -90,
                replyId = 50,
                relayNode = 123,
                relays = 2,
                viaMqtt = true,
                retryCount = 1,
                emoji = 10,
                sfppHash = byteArrayOf(4, 5, 6),
            )

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val packetToUpdate = DataPacket(to = "old", channel = 0, text = "old")
        packetToUpdate.readFromParcel(parcel)

        // Verify that all fields were updated correctly
        assertEquals("recipient", packetToUpdate.to)
        assertArrayEquals(byteArrayOf(1, 2, 3), packetToUpdate.bytes)
        assertEquals(42, packetToUpdate.dataType)
        assertEquals("sender", packetToUpdate.from)
        assertEquals(123456789L, packetToUpdate.time)
        assertEquals(100, packetToUpdate.id)
        assertEquals(MessageStatus.RECEIVED, packetToUpdate.status)
        assertEquals(3, packetToUpdate.hopLimit)
        assertEquals(1, packetToUpdate.channel)
        assertEquals(true, packetToUpdate.wantAck)
        assertEquals(5, packetToUpdate.hopStart)
        assertEquals(1.5f, packetToUpdate.snr)
        assertEquals(-90, packetToUpdate.rssi)
        assertEquals(50, packetToUpdate.replyId)
        assertEquals(123, packetToUpdate.relayNode)
        assertEquals(2, packetToUpdate.relays)
        assertEquals(true, packetToUpdate.viaMqtt)
        assertEquals(1, packetToUpdate.retryCount)
        assertEquals(10, packetToUpdate.emoji)
        assertArrayEquals(byteArrayOf(4, 5, 6), packetToUpdate.sfppHash)

        parcel.recycle()
    }
}
