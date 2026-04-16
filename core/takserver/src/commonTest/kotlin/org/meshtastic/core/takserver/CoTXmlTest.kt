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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Round-trip and structure tests for [CoTMessage.toXml]. */
class CoTXmlTest {

    // ── PLI round-trip ────────────────────────────────────────────────────────

    @Test
    fun `toXml produces parseable XML for a PLI message`() {
        val original =
            CoTMessage.pli(
                uid = "!1234abcd",
                callsign = "TestUser",
                latitude = 37.7749,
                longitude = -122.4194,
                altitude = 15.0,
                speed = 5.0,
                course = 180.0,
                team = "Cyan",
                role = "Team Member",
                battery = 85,
            )

        val xml = original.toXml()
        val parsed = CoTXmlParser(xml).parse()

        assertTrue(parsed.isSuccess, "Parsed result should be success; error=${parsed.exceptionOrNull()}")
        val roundTripped = parsed.getOrThrow()

        assertEquals(original.uid, roundTripped.uid)
        assertEquals(original.type, roundTripped.type)
        assertEquals(original.latitude, roundTripped.latitude, 1e-4)
        assertEquals(original.longitude, roundTripped.longitude, 1e-4)
        assertEquals(original.hae, roundTripped.hae, 1e-4)
        assertEquals(original.contact?.callsign, roundTripped.contact?.callsign)
        assertEquals(original.group?.name, roundTripped.group?.name)
        assertEquals(original.group?.role, roundTripped.group?.role)
        assertEquals(original.status?.battery, roundTripped.status?.battery)
        assertEquals(original.track?.speed, roundTripped.track?.speed)
        assertEquals(original.track?.course, roundTripped.track?.course)
    }

    // ── Chat round-trip ───────────────────────────────────────────────────────

    @Test
    fun `toXml produces parseable XML for a chat message`() {
        val original =
            CoTMessage.chat(
                senderUid = "!aabbccdd",
                senderCallsign = "Alice",
                message = "Hello World",
                chatroom = "All Chat Rooms",
            )

        val xml = original.toXml()
        val parsed = CoTXmlParser(xml).parse()

        assertTrue(parsed.isSuccess, "Parsed result should be success; error=${parsed.exceptionOrNull()}")
        val roundTripped = parsed.getOrThrow()

        assertEquals("b-t-f", roundTripped.type)
        assertNotNull(roundTripped.chat)
        assertEquals("Hello World", roundTripped.chat.message)
        assertEquals("Alice", roundTripped.chat.senderCallsign)
    }

    // ── XML escaping ─────────────────────────────────────────────────────────

    @Test
    fun `toXml escapes special characters in UID`() {
        val message = CoTMessage.pli(uid = "uid&with<special>chars", callsign = "User", latitude = 0.0, longitude = 0.0)

        val xml = message.toXml()

        assertTrue(xml.contains("uid&amp;with&lt;special&gt;chars"), "Expected escaped UID in XML; got: $xml")
    }

    @Test
    fun `toXml escapes special characters in callsign`() {
        val message = CoTMessage.pli(uid = "!1234", callsign = "A&B<C>D", latitude = 0.0, longitude = 0.0)

        val xml = message.toXml()

        assertTrue(xml.contains("A&amp;B&lt;C&gt;D"), "Expected escaped callsign in XML; got: $xml")
    }

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    fun `toXml does not include XML declaration - CoT stream protocol`() {
        // The CoT TCP streaming protocol requires a concatenated sequence of <event> elements
        // with NO XML declaration. A mid-stream <?xml ... ?> tag breaks ATAK's parser and
        // causes the client to disconnect as soon as the first real event arrives.
        val message = CoTMessage.pli(uid = "!1234", callsign = "X", latitude = 0.0, longitude = 0.0)
        val xml = message.toXml()
        assertTrue(xml.startsWith("<event"), "XML should start with <event, not a declaration; got: $xml")
        assertTrue(!xml.contains("<?xml"), "XML should NOT contain a declaration; got: $xml")
    }

    @Test
    fun `toXml omits optional elements when null`() {
        val message =
            CoTMessage.pli(uid = "!1234", callsign = "X", latitude = 0.0, longitude = 0.0)
                .copy(track = null, status = null, group = null, contact = null)

        val xml = message.toXml()

        assertTrue(!xml.contains("<track"), "track element should be absent when null")
        assertTrue(!xml.contains("<status"), "status element should be absent when null")
        assertTrue(!xml.contains("<__group"), "__group element should be absent when null")
        assertTrue(!xml.contains("<contact"), "contact element should be absent when null")
    }

    @Test
    fun `toXml includes remarks when present`() {
        val message =
            CoTMessage.pli(uid = "!1234", callsign = "X", latitude = 0.0, longitude = 0.0).copy(remarks = "A remark")

        val xml = message.toXml()

        assertTrue(xml.contains("<remarks>A remark</remarks>"), "Expected remarks in XML; got: $xml")
    }
}
