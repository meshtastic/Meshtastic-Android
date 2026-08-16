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
package org.meshtastic.core.database.entity

import org.meshtastic.core.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for [Packet.Companion.getRelayNode] — the copy of this resolver actually wired into production
 * (`DebugViewModel`), as opposed to the unused [Node.Companion.getRelayNode] of the same name. Both must sort an
 * unresolved hop count last rather than let it look like the closest relay (#6263).
 */
class PacketGetRelayNodeTest {

    @Test
    fun getRelayNode_neverPrefersAnUnresolvedHopCountOverAKnownOne() {
        val unresolved = Node(num = 0x000001AA, lastHeard = 100, hopsAway = Node.HOPS_AWAY_UNSET)
        val knownFar = Node(num = 0x000002AA, lastHeard = 100, hopsAway = 4)
        val otherSuffix = Node(num = 0x000003BB, lastHeard = 100, hopsAway = 1)

        val relayNode =
            Packet.getRelayNode(
                relayNodeId = 0x0000FFAA.toInt(),
                nodes = listOf(unresolved, knownFar, otherSuffix),
                ourNodeNum = null,
            )

        assertEquals(knownFar, relayNode)
    }

    @Test
    fun getRelayNode_picksTheClosestOfSeveralResolvedCandidates() {
        val far = Node(num = 0x000001AA, lastHeard = 100, hopsAway = 4)
        val near = Node(num = 0x000002AA, lastHeard = 100, hopsAway = 1)

        val relayNode =
            Packet.getRelayNode(relayNodeId = 0x0000FFAA.toInt(), nodes = listOf(far, near), ourNodeNum = null)

        assertEquals(near, relayNode)
    }
}
