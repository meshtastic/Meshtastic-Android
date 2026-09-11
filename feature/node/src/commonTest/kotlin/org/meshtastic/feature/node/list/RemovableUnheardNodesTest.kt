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
package org.meshtastic.feature.node.list

import org.meshtastic.core.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the unheard banner counts and its one-tap clear would delete. This is the highest-consequence consumer of
 * [Node.isUnheardOnCurrentLora] - it removes nodes - so each exemption is pinned separately.
 */
class RemovableUnheardNodesTest {

    private val ourNum = 1

    private fun node(
        num: Int,
        heardOnCurrentLora: Boolean = false,
        viaMqtt: Boolean = false,
        isFavorite: Boolean = false,
    ) = Node(num = num, heardOnCurrentLora = heardOnCurrentLora, viaMqtt = viaMqtt, isFavorite = isFavorite)

    @Test
    fun offersANodeHeardOverRfOnAnotherConfig() {
        val nodes = listOf(node(2), node(3, heardOnCurrentLora = true))

        assertEquals(listOf(2), selectRemovableUnheardNodes(nodes, ourNum).map { it.num })
    }

    @Test
    fun neverOffersAnMqttNode() {
        // Reads unheard for the life of the entry, and deleting it achieves nothing - the next uplinked packet
        // brings it straight back.
        val nodes = listOf(node(2, viaMqtt = true), node(3))

        assertEquals(listOf(3), selectRemovableUnheardNodes(nodes, ourNum).map { it.num })
    }

    @Test
    fun neverOffersAFavourite() {
        // Also covers shared contacts: firmware marks a contact favourite, and one has never been heard over RF.
        val nodes = listOf(node(2, isFavorite = true), node(3))

        assertEquals(listOf(3), selectRemovableUnheardNodes(nodes, ourNum).map { it.num })
    }

    @Test
    fun neverOffersTheConnectedRadio() {
        val nodes = listOf(node(ourNum), node(3))

        assertEquals(listOf(3), selectRemovableUnheardNodes(nodes, ourNum).map { it.num })
    }

    @Test
    fun offersNothingUntilTheLocalNodeNumberIsKnown() {
        // ourNode and the node list arrive on independent flows; offering here could delete the user's own node.
        val nodes = listOf(node(2), node(3))

        assertTrue(selectRemovableUnheardNodes(nodes, ourNum = null).isEmpty())
    }
}
