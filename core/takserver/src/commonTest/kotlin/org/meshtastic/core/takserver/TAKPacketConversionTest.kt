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
package org.meshtastic.core.takserver

import org.meshtastic.core.takserver.TAKPacketConversion.toCoTMessage
import org.meshtastic.core.takserver.TAKPacketConversion.toTAKPacket
import org.meshtastic.proto.Contact
import org.meshtastic.proto.GeoChat
import org.meshtastic.proto.Group
import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.PLI
import org.meshtastic.proto.Status
import org.meshtastic.proto.TAKPacket
import org.meshtastic.proto.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TAKPacketConversionTest {

    @Test
    fun testCoTToTAKPacketPLI() {
        val cot =
            CoTMessage.pli(
                uid = "!1234",
                callsign = "Bob",
                latitude = 45.0,
                longitude = -90.0,
                altitude = 100.0,
                speed = 15.0,
                course = 180.0,
                team = "Blue",
                role = "Team Member",
                battery = 90,
            )

        val takPacket = cot.toTAKPacket()
        assertNotNull(takPacket)

        assertEquals(false, takPacket.is_compressed)
        assertEquals("Bob", takPacket.contact?.callsign)
        assertEquals("!1234", takPacket.contact?.device_callsign)
        assertEquals(Team.Blue, takPacket.group?.team)
        assertEquals(MemberRole.TeamMember, takPacket.group?.role)
        assertEquals(90, takPacket.status?.battery)

        assertNotNull(takPacket.pli)
        assertEquals(450000000, takPacket.pli?.latitude_i)
        assertEquals(-900000000, takPacket.pli?.longitude_i)
        assertEquals(100, takPacket.pli?.altitude)
        assertEquals(15, takPacket.pli?.speed)
        assertEquals(180, takPacket.pli?.course)
    }

    @Test
    fun testTAKPacketToCoTMessagePLI() {
        val takPacket =
            TAKPacket(
                is_compressed = false,
                contact = Contact(callsign = "Alice", device_callsign = "!5678"),
                group = Group(team = Team.Cyan, role = MemberRole.HQ),
                status = Status(battery = 85),
                pli = PLI(latitude_i = 300000000, longitude_i = -800000000, altitude = 50, speed = 5, course = 90),
            )

        val cot = takPacket.toCoTMessage()
        assertNotNull(cot)

        assertEquals("!5678", cot.uid)
        assertEquals("a-f-G-U-C", cot.type)
        assertEquals(30.0, cot.latitude, 0.0001)
        assertEquals(-80.0, cot.longitude, 0.0001)
        assertEquals(50.0, cot.hae, 0.0001)

        assertEquals("Alice", cot.contact?.callsign)
        assertEquals("Cyan", cot.group?.name)
        assertEquals("HQ", cot.group?.role)
        assertEquals(85, cot.status?.battery)

        assertNotNull(cot.track)
        assertEquals(5.0, cot.track?.speed)
        assertEquals(90.0, cot.track?.course)
    }

    @Test
    fun testCoTToTAKPacketChat() {
        val cot =
            CoTMessage.chat(
                senderUid = "!1234",
                senderCallsign = "Bob",
                message = "Hello World",
                chatroom = "All Chat Rooms",
            )

        val takPacket = cot.toTAKPacket()
        assertNotNull(takPacket)

        assertNotNull(takPacket.chat)
        assertEquals("Hello World", takPacket.chat?.message)
    }

    @Test
    fun testChatSmugglesMessageId() {
        val cot =
            CoTMessage.chat(
                senderUid = "my-device-123",
                senderCallsign = "Bob",
                message = "Hello World",
                chatroom = "All Chat Rooms",
            )

        val msgId = cot.uid.split(".").last()

        val takPacket = cot.toTAKPacket()
        assertNotNull(takPacket)

        val expectedDeviceCallsign = "my-device-123|$msgId"
        assertEquals(expectedDeviceCallsign, takPacket.contact?.device_callsign)
        assertEquals("Bob", takPacket.contact?.callsign)
        assertEquals("Hello World", takPacket.chat?.message)
    }

    @Test
    fun testParseSmuggledMessageId() {
        val takPacket =
            TAKPacket(
                is_compressed = false,
                contact = Contact(callsign = "Alice", device_callsign = "alice-device-456|msg-789"),
                chat = GeoChat(message = "Hi Bob", to = "Bob"),
            )

        val cot = takPacket.toCoTMessage()
        assertNotNull(cot)

        assertEquals("GeoChat.alice-device-456.Bob.msg-789", cot.uid)
        assertEquals("Alice", cot.chat?.senderCallsign)
        assertEquals("Hi Bob", cot.chat?.message)
        assertEquals("Bob", cot.chat?.chatroom)
    }
}
