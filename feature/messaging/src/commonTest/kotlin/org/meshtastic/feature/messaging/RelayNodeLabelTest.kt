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

import org.meshtastic.core.model.Node
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelayNodeLabelTest {

    private fun node(num: Int, longName: String, displayName: String = "") = Node(
        num = num,
        lastHeard = 100,
        hopsAway = 0,
        user = User.Builder().also { it.long_name = longName }.build(),
        displayName = displayName,
    )

    @Test
    fun noRelayRecordedGivesNoLabel() {
        assertNull(relayNodeLabel(null, emptyList(), null))
        assertNull(relayNodeLabel(0, listOf(node(0x100, "Zero")), null))
    }

    @Test
    fun matchedNodeShowsNameAndHexByte() {
        assertEquals("Ridge (0x5A)", relayNodeLabel(0x5A, listOf(node(0x1234565A, "Ridge")), null))
    }

    @Test
    fun unmatchedByteShowsHexOnlyWithLeadingZero() {
        assertEquals("0x0A", relayNodeLabel(0x0A, listOf(node(0x1234565A, "Ridge")), null))
    }

    @Test
    fun localDisplayNameReplacesBroadcastName() {
        assertEquals("Shed (0x5A)", relayNodeLabel(0x5A, listOf(node(0x5A, "Ridge", displayName = "Shed")), null))
    }

    @Test
    fun ourOwnNodeIsNeverTheRelay() {
        assertEquals("0x5A", relayNodeLabel(0x5A, listOf(node(0x5A, "Me")), ourNodeNum = 0x5A))
    }
}
