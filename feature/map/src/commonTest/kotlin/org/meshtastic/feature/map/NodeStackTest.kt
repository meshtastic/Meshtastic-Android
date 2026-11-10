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
package org.meshtastic.feature.map

import org.meshtastic.core.model.Node
import org.meshtastic.proto.Position
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeStackTest {

    private fun node(num: Int, latitudeI: Int, longitudeI: Int, lastHeard: Int = 0, isFavorite: Boolean = false) = Node(
        num = num,
        position =
        Position.Builder()
            .also { wb ->
                wb.latitude_i = latitudeI
                wb.longitude_i = longitudeI
            }
            .build(),
        lastHeard = lastHeard,
        isFavorite = isFavorite,
    )

    @Test
    fun `nodes at identical coordinates share one stack`() {
        val stacks = stackCoincident(List(5) { node(it + 1, 406585344, -739508224) }, myNodeNum = null)

        assertEquals(1, stacks.size)
        assertEquals(5, stacks.single().count)
    }

    @Test
    fun `nodes a few meters apart share a stack`() {
        // 300 units of 1e-7 degrees is about 3.3 m north, under the 5 m spread.
        val stacks = stackCoincident(listOf(node(1, 406585344, -739508224), node(2, 406585644, -739508224)), null)

        assertEquals(listOf(2), stacks.map { it.count })
    }

    @Test
    fun `nodes farther apart than the spread stay separate`() {
        // 1,000 units is about 11 m, east-west as well as north-south.
        val north = listOf(node(1, 406585344, -739508224), node(2, 406586344, -739508224))
        val east = listOf(node(1, 406585344, -739508224), node(2, 406585344, -739506224))

        assertEquals(listOf(1, 1), stackCoincident(north, null).map { it.count })
        assertEquals(listOf(1, 1), stackCoincident(east, null).map { it.count })
    }

    @Test
    fun `a stack spanning two latitude bands still forms`() {
        // Bands are 5 m tall. This line starts 2 m south of a band edge and runs 4 m north, across it.
        val bandDegrees = STACK_SPREAD_METERS / 111_320
        val edge = floor(40.65 / bandDegrees) * bandDegrees
        val start = ((edge - 2.0 / 111_320) * 1e7).toInt()
        val nodes = (0 until 10).map { node(it + 1, start + it * 40, -739508224) }

        assertEquals(listOf(10), stackCoincident(nodes, null).map { it.count })
    }

    @Test
    fun `badge counts past 999 read as 999 plus`() {
        assertEquals("15", stackBadgeLabel(15))
        assertEquals("999", stackBadgeLabel(999))
        assertEquals("999+", stackBadgeLabel(1000))
    }

    @Test
    fun `this node is on top of its stack`() {
        val nodes = listOf(node(1, 0, 0, lastHeard = 900), node(2, 0, 0, lastHeard = 100), node(3, 0, 0))

        assertEquals(2, stackCoincident(nodes, myNodeNum = 2).single().top.num)
    }

    @Test
    fun `a favorite outranks a node heard more recently`() {
        val nodes = listOf(node(1, 0, 0, lastHeard = 900), node(2, 0, 0, lastHeard = 100, isFavorite = true))

        assertEquals(2, stackCoincident(nodes, null).single().top.num)
    }

    @Test
    fun `among ordinary nodes the lowest number stays on top however recently the others were heard`() {
        val nodes = listOf(node(7, 0, 0, lastHeard = 900), node(3, 0, 0, lastHeard = 100))

        assertEquals(3, stackCoincident(nodes, null).single().top.num)
    }

    @Test
    fun `stacks keep the order their first member had`() {
        val nodes = listOf(node(1, 0, 0), node(2, 100_000, 100_000), node(3, 0, 0))

        assertEquals(
            listOf(listOf(1, 3), listOf(2)),
            stackCoincident(nodes, null).map { s -> s.members.map { it.num } },
        )
    }
}
