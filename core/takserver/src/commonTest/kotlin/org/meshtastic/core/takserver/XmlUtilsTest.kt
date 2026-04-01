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

class XmlUtilsTest {

    // ── xmlEscaped ────────────────────────────────────────────────────────────

    @Test
    fun `xmlEscaped leaves clean strings unchanged`() {
        assertEquals("Hello World", "Hello World".xmlEscaped())
    }

    @Test
    fun `xmlEscaped escapes ampersand`() {
        assertEquals("A&amp;B", "A&B".xmlEscaped())
    }

    @Test
    fun `xmlEscaped escapes less-than`() {
        assertEquals("&lt;tag&gt;", "<tag>".xmlEscaped())
    }

    @Test
    fun `xmlEscaped escapes double quote`() {
        assertEquals("say &quot;hi&quot;", """say "hi"""".xmlEscaped())
    }

    @Test
    fun `xmlEscaped escapes single quote`() {
        assertEquals("it&apos;s", "it's".xmlEscaped())
    }

    @Test
    fun `xmlEscaped escapes all special chars in one string`() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", "&<>\"'".xmlEscaped())
    }

    @Test
    fun `xmlEscaped escapes ampersand before other entities to avoid double-escaping`() {
        // "&amp;" in input should become "&amp;amp;" — not "&amp;" (which would be a double-escape bug)
        assertEquals("&amp;amp;", "&amp;".xmlEscaped())
    }

    // ── geoChatSenderUid ──────────────────────────────────────────────────────

    @Test
    fun `geoChatSenderUid extracts sender from GeoChat UID`() {
        assertEquals("!1234abcd", "GeoChat.!1234abcd.All Chat Rooms.deadbeef".geoChatSenderUid())
    }

    @Test
    fun `geoChatSenderUid returns original string for non-GeoChat UID`() {
        assertEquals("!1234abcd", "!1234abcd".geoChatSenderUid())
    }

    @Test
    fun `geoChatSenderUid handles missing second segment gracefully`() {
        // "GeoChat." splits into ["GeoChat", ""] — getOrElse(1) returns "" (empty second segment)
        assertEquals("", "GeoChat.".geoChatSenderUid())
    }

    // ── geoChatMessageId ──────────────────────────────────────────────────────

    @Test
    fun `geoChatMessageId extracts messageId from GeoChat UID`() {
        assertEquals("deadbeef", "GeoChat.!1234abcd.All Chat Rooms.deadbeef".geoChatMessageId())
    }

    @Test
    fun `geoChatMessageId returns original string for non-GeoChat UID`() {
        assertEquals("!1234abcd", "!1234abcd".geoChatMessageId())
    }

    @Test
    fun `geoChatMessageId handles single-segment GeoChat UID gracefully`() {
        val uid = "GeoChat"
        assertEquals("GeoChat", uid.geoChatMessageId())
    }
}
