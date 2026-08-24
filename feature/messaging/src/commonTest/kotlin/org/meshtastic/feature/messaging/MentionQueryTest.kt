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
package org.meshtastic.feature.messaging

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MentionQueryTest {

    private fun query(text: String, cursor: Int = text.length) = currentMentionQuery(text, TextRange(cursor))

    @Test
    fun `detects a query at the start`() {
        assertEquals(MentionQuery(start = 0, end = 3, query = "al"), query("@al"))
    }

    @Test
    fun `detects a query after whitespace`() {
        assertEquals(MentionQuery(start = 3, end = 7, query = "bob"), query("hi @bob"))
    }

    @Test
    fun `bare at-sign yields an empty query for showing all nodes`() {
        assertEquals(MentionQuery(start = 0, end = 1, query = ""), query("@"))
    }

    @Test
    fun `no at-sign means no query`() {
        assertNull(query("hello there"))
    }

    @Test
    fun `whitespace after the at-sign ends the query`() {
        assertNull(query("@bob said hi"))
    }

    @Test
    fun `at-sign mid-word such as an email is not a mention`() {
        assertNull(query("me@host"))
    }

    @Test
    fun `a completed token followed by a space does not re-trigger`() {
        assertNull(query("@!ffccee11 "))
    }

    @Test
    fun `only the token left of the caret is considered`() {
        // caret sits right after "@al"; the later "@bob" is ignored
        assertEquals(MentionQuery(start = 0, end = 3, query = "al"), query("@al @bob", cursor = 3))
    }

    @Test
    fun `a non-collapsed selection yields no query`() {
        assertNull(currentMentionQuery("@bob", TextRange(1, 4)))
    }
}
