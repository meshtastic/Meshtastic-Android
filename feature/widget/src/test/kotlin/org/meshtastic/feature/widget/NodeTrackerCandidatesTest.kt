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
package org.meshtastic.feature.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class NodeTrackerCandidatesTest {

    @Test
    fun `favorites come first then the most recently heard`() {
        val nodes =
            listOf(
                testNode(1, lastHeard = 100),
                testNode(2, lastHeard = 300),
                testNode(3, lastHeard = 50, isFavorite = true),
                testNode(4, lastHeard = 200, isFavorite = true),
            )

        assertEquals(listOf(4, 3, 2, 1), trackerCandidates(nodes, "", ourNodeNum = null).map { it.num })
    }

    @Test
    fun `our own radio and ignored nodes are left out`() {
        val nodes = listOf(testNode(1), testNode(2, isIgnored = true), testNode(3))

        assertEquals(listOf(3), trackerCandidates(nodes, "", ourNodeNum = 1).map { it.num })
    }

    @Test
    fun `the query matches long name short name and id ignoring case`() {
        val nodes =
            listOf(
                testNode(0x11, shortName = "BCS1", longName = "Basecamp Solar"),
                testNode(0x22, shortName = "RPT", longName = "Ridge Repeater"),
                testNode(0x33, shortName = "HOME", longName = "Kitchen"),
            )

        assertEquals(listOf(0x11), trackerCandidates(nodes, "basecamp", null).map { it.num })
        assertEquals(listOf(0x22), trackerCandidates(nodes, "rpt", null).map { it.num })
        assertEquals(listOf(0x33), trackerCandidates(nodes, " !00000033 ", null).map { it.num })
    }

    @Test
    fun `a query with no match returns nothing`() {
        assertEquals(emptyList(), trackerCandidates(listOf(testNode(1)), "zzz", null))
    }
}
