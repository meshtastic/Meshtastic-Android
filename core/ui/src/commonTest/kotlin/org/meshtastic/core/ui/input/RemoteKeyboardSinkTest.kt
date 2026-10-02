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
package org.meshtastic.core.ui.input

import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteKeyboardSinkTest {

    @Test
    fun aTypedCharacterIsInsertedText() {
        assertEquals(SinkEdit(deleted = 0, inserted = "a"), sinkEdit(listOf(SinkChange(0, "a"))))
        assertEquals(SinkEdit(deleted = 0, inserted = "ls -l\n"), sinkEdit(listOf(SinkChange(0, "ls -l\n"))))
    }

    @Test
    fun aDeletionWithNothingInsertedIsABackspace() {
        assertEquals(SinkEdit(deleted = 1, inserted = ""), sinkEdit(listOf(SinkChange(1, ""))))
    }

    @Test
    fun replacingTheSentinelSendsNoBackspaceAndKeepsALeadingSpace() {
        assertEquals(SinkEdit(deleted = 0, inserted = "x"), sinkEdit(listOf(SinkChange(1, "x"))))
        assertEquals(SinkEdit(deleted = 0, inserted = " foo"), sinkEdit(listOf(SinkChange(1, " foo"))))
    }

    @Test
    fun noChangesTypeNothing() {
        assertEquals(SinkEdit(deleted = 0, inserted = ""), sinkEdit(emptyList()))
    }

    @Test
    fun lineBreaksBecomeEnterBetweenTextRuns() {
        assertEquals(listOf("text:ls -l", "enter", "text:pwd"), delivered("ls -l\npwd"))
    }

    @Test
    fun eachLineBreakCharacterIsItsOwnEnter() {
        assertEquals(listOf("text:a", "enter", "enter", "text:b"), delivered("a\r\nb"))
        assertEquals(listOf("enter"), delivered("\n"))
    }

    private fun delivered(text: String): List<String> {
        val calls = mutableListOf<String>()
        deliverText(
            text,
            RemoteKeyHandler(
                onText = { calls += "text:$it" },
                onEnter = { calls += "enter" },
                onBackspace = { calls += "backspace" },
                onKey = { calls += "key:$it" },
            ),
        )
        return calls
    }
}
