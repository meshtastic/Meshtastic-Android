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
import kotlin.test.assertTrue

class CoTXmlFrameBufferTest {

    @Test
    fun `extracts multiple concatenated events`() {
        val buffer = CoTXmlFrameBuffer()
        val xml = "<event uid='1'></event><event uid='2'></event>"

        val messages = buffer.append(xml.encodeToByteArray())

        assertEquals(2, messages.size)
        assertEquals("<event uid='1'></event>", messages[0])
        assertEquals("<event uid='2'></event>", messages[1])
    }

    @Test
    fun `preserves partial event until completed`() {
        val buffer = CoTXmlFrameBuffer()

        val firstChunk = buffer.append("<event uid='1'>".encodeToByteArray())
        val secondChunk = buffer.append("</event>".encodeToByteArray())

        assertTrue(firstChunk.isEmpty())
        assertEquals(listOf("<event uid='1'></event>"), secondChunk)
    }

    @Test
    fun `drops oversized partial buffer`() {
        val buffer = CoTXmlFrameBuffer(maxMessageSize = 16)
        val validEvent = "<event></event>"

        val messages = buffer.append("<event uid='1234567890'".encodeToByteArray())
        val secondMessages = buffer.append("garbage".encodeToByteArray())
        val laterMessages = buffer.append(validEvent.encodeToByteArray())

        assertTrue(messages.isEmpty())
        assertTrue(secondMessages.isEmpty())
        assertEquals(listOf(validEvent), laterMessages)
    }
}
