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
package org.meshtastic.core.model

import android.os.Parcel
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DataPacketParcelTest {

    @Test
    fun `DataPacket parcelization round trip via writeToParcel and readParcelable`() {
        val original = createFullDataPacket()

        val parcel = Parcel.obtain()
        // Use writeParcelable to include class information/nullability flag needed by readParcelable
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)

        val created = parcel.readParcelable<DataPacket>(DataPacket::class.java.classLoader)
        parcel.recycle()

        assertNotNull(created)
        assertDataPacketsEqual(original, created!!)
    }

    @Test
    fun `DataPacket manual readFromParcel matches writeToParcel`() {
        val original = createFullDataPacket()

        // Write using generated writeToParcel (writes content only)
        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        // Read using manual readFromParcel
        // We start with an empty packet and populate it
        val restored = DataPacket(to = "dummy", channel = 0, text = "dummy")
        // Reset fields to ensure they are overwritten
        restored.to = null
        restored.from = null
        restored.bytes = null
        restored.sfppHash = null

        restored.readFromParcel(parcel)
        parcel.recycle()

        assertDataPacketsEqual(original, restored)
    }

    @Test
    fun `DataPacket with nulls handles parcelization correctly`() {
        val original =
            DataPacket(
                to = null,
                bytes = null,
                dataType = 99,
                from = null,
                time = 123L,
                status = null,
                replyId = null,
                relayNode = null,
                sfppHash = null,
            )

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val restored = DataPacket(to = "dummy", channel = 0, text = "dummy")
        restored.readFromParcel(parcel)
        parcel.recycle()

        assertDataPacketsEqual(original, restored)
    }

    private fun createFullDataPacket(): DataPacket = DataPacket(
        to = "destNode",
        bytes = "Hello World".toByteArray().toByteString(),
        dataType = 1,
        from = "srcNode",
        time = 1234567890L,
        id = 42,
        status = MessageStatus.DELIVERED,
        hopLimit = 3,
        channel = 5,
        wantAck = true,
        hopStart = 7,
        snr = 12.5f,
        rssi = -80,
        replyId = 101,
        relayNode = 202,
        relays = 1,
        viaMqtt = true,
        retryCount = 2,
        emoji = 0x1F600,
        sfppHash = "sfpp".toByteArray().toByteString(),
    )

    private fun assertDataPacketsEqual(expected: DataPacket, actual: DataPacket) {
        assertEquals("to", expected.to, actual.to)
        assertEquals("bytes", expected.bytes, actual.bytes)
        assertEquals("dataType", expected.dataType, actual.dataType)
        assertEquals("from", expected.from, actual.from)
        assertEquals("time", expected.time, actual.time)
        assertEquals("id", expected.id, actual.id)
        assertEquals("status", expected.status, actual.status)
        assertEquals("hopLimit", expected.hopLimit, actual.hopLimit)
        assertEquals("channel", expected.channel, actual.channel)
        assertEquals("wantAck", expected.wantAck, actual.wantAck)
        assertEquals("hopStart", expected.hopStart, actual.hopStart)
        assertEquals("snr", expected.snr, actual.snr, 0.001f)
        assertEquals("rssi", expected.rssi, actual.rssi)
        assertEquals("replyId", expected.replyId, actual.replyId)
        assertEquals("relayNode", expected.relayNode, actual.relayNode)
        assertEquals("relays", expected.relays, actual.relays)
        assertEquals("viaMqtt", expected.viaMqtt, actual.viaMqtt)
        assertEquals("retryCount", expected.retryCount, actual.retryCount)
        assertEquals("emoji", expected.emoji, actual.emoji)
        assertEquals("sfppHash", expected.sfppHash, actual.sfppHash)
    }
}
