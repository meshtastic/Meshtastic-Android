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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MentionTest {

    private val myId = "!ffccee11"

    @Test
    fun `textMentionsNode matches an exact mention token`() {
        assertTrue(textMentionsNode("hey @!ffccee11 you there", myId))
    }

    @Test
    fun `textMentionsNode matches a mention at the end of text`() {
        assertTrue(textMentionsNode("ping @!ffccee11", myId))
    }

    @Test
    fun `textMentionsNode does not match when trailing hex extends the id`() {
        // Boundary rule: @!ffccee11a is a different (longer) hex run, not a mention of !ffccee11.
        assertFalse(textMentionsNode("look at @!ffccee11a here", myId))
    }

    @Test
    fun `textMentionsNode does not match a different node`() {
        assertFalse(textMentionsNode("hi @!12345678", myId))
    }

    @Test
    fun `textMentionsNode matches case-insensitively`() {
        assertTrue(textMentionsNode("hi @!FFCCEE11", "!ffccee11"))
        assertTrue(textMentionsNode("hi @!ffccee11", "!FFCCEE11"))
    }

    @Test
    fun `textMentionsNode does not match plain text without the token`() {
        assertFalse(textMentionsNode("no mentions here", myId))
    }

    @Test
    fun `textMentionsNode empty or unknown local id never matches`() {
        assertFalse(textMentionsNode("@!ffccee11", ""))
        assertFalse(textMentionsNode("@!ffccee11", "!"))
        assertFalse(textMentionsNode(null, myId))
    }

    @Test
    fun `MENTION_TOKEN_REGEX captures the hex id and rejects trailing hex`() {
        assertEquals("!ffccee11", MENTION_TOKEN_REGEX.find("yo @!ffccee11!")?.groupValues?.get(1))
        assertEquals(null, MENTION_TOKEN_REGEX.find("@!ffccee11a"))
    }
}
