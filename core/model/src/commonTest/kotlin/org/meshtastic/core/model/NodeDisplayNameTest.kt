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

import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeDisplayNameTest {

    private fun node(longName: String = "Broadcast", shortName: String = "BC", displayName: String = "") = Node(
        num = 1,
        user =
        User.Builder()
            .also { wb ->
                wb.id = "!00000001"
                wb.long_name = longName
                wb.short_name = shortName
            }
            .build(),
        displayName = displayName,
    )

    @Test
    fun effectiveLongName_isDisplayNameWhenSet() {
        assertEquals("Mine", node(displayName = "Mine").effectiveLongName)
    }

    @Test
    fun effectiveLongName_isBroadcastNameWhenUnset() {
        assertEquals("Broadcast", node().effectiveLongName)
    }

    @Test
    fun effectiveLongName_ignoresWhitespaceOnlyDisplayName() {
        val n = node(displayName = "   ")
        assertFalse(n.hasDisplayName)
        assertEquals("Broadcast", n.effectiveLongName)
    }

    @Test
    fun effectiveLongName_isDisplayNameWhenBroadcastNameBlank() {
        assertEquals("Mine", node(longName = "", displayName = "Mine").effectiveLongName)
    }

    @Test
    fun shortNameIsNeverOverridden() {
        assertEquals("BC", node(displayName = "Mine").user.short_name)
    }

    @Test
    fun hiddenLongName_isNullWithoutDisplayName() {
        assertNull(node().hiddenLongName)
    }

    @Test
    fun hiddenLongName_isBroadcastNameWhileDisplayNameSet() {
        assertEquals("Broadcast", node(displayName = "Mine").hiddenLongName)
    }

    @Test
    fun hiddenLongName_isNullWhenBroadcastNameBlank() {
        assertNull(node(longName = "", displayName = "Mine").hiddenLongName)
    }

    @Test
    fun hiddenLongName_isKeptWhenDisplayNameEqualsBroadcastName() {
        assertEquals("Broadcast", node(displayName = "Broadcast").hiddenLongName)
    }

    @Test
    fun matchesSearch_matchesDisplayNameAndStillMatchesBroadcastNameShortNameAndId() {
        val n = node(displayName = "Mine")
        assertTrue(n.matchesSearch("mine"))
        assertTrue(n.matchesSearch("broad"))
        assertTrue(n.matchesSearch("bc"))
        assertTrue(n.matchesSearch("00000001"))
        assertFalse(n.matchesSearch("zzz"))
    }
}
