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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetiredContactKeyTest {
    private val retired = ContactKey.retiredBroadcast("a1b2c3d4")

    @Test
    fun `a retired key parses as a broadcast with no live slot`() {
        assertTrue(retired.isRetired)
        assertEquals("a1b2c3d4", retired.retiredToken)
        assertNull(retired.channelOrNull, "a retired conversation has no slot to send on")
        assertEquals(NodeAddress.ID_BROADCAST, retired.addressString)
        assertEquals(NodeAddress.Broadcast, retired.address)
    }

    @Test
    fun `it still reads as a channel conversation rather than a direct message`() {
        assertTrue(retired.value.endsWith(NodeAddress.ID_BROADCAST))
    }

    @Test
    fun `live keys are unaffected`() {
        listOf(ContactKey.broadcast(0), ContactKey.broadcast(3), ContactKey("8!a1b2c3d4"), ContactKey("!a1b2c3d4"))
            .forEach { key ->
                assertFalse(key.isRetired, "${key.value} must not read as retired")
                assertNull(key.retiredToken)
            }
        assertEquals(3, ContactKey.broadcast(3).channelOrNull)
        assertEquals("!a1b2c3d4", ContactKey("8!a1b2c3d4").addressString)
    }
}
