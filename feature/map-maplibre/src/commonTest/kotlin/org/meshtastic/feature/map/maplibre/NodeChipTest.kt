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
package org.meshtastic.feature.map.maplibre

import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.maplibre.component.boundingBoxFromCorners
import org.meshtastic.feature.map.maplibre.geojson.MapChipGlyph
import org.meshtastic.feature.map.maplibre.geojson.MapChipKey
import org.meshtastic.feature.map.maplibre.geojson.featureValue
import org.meshtastic.feature.map.maplibre.geojson.toNodeChip
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The chip key is what one layer uses to pick a node's chip image out of many, so two nodes that should look different
 * must never share it. Getting this wrong is what previously made a shared short name fatal.
 */
class NodeChipKeyTest {

    private fun chipNode(num: Int, shortName: String, isIgnored: Boolean = false) =
        Node(num = num, user = User(short_name = shortName), isIgnored = isIgnored)

    @Test
    fun `two nodes with the same name and different colours get different keys`() {
        val a = chipNode(num = 1, shortName = "SWAR")
        val b = chipNode(num = 2, shortName = "SWAR")

        // Colours derive from the node number, so these two differ in colour alone.
        assertNotEquals(a.colors, b.colors, "the fixture must actually differ in colour")
        assertNotEquals(a.toNodeChip().featureValue(), b.toNodeChip().featureValue())
    }

    @Test
    fun `two nodes that look identical share a key so the image is only drawn once`() {
        val chip = chipNode(num = 7, shortName = "NORT").toNodeChip()
        val same = chipNode(num = 7, shortName = "NORT").toNodeChip()

        assertEquals(chip.featureValue(), same.featureValue())
        assertEquals(1, listOf(chip, same).distinct().size)
    }

    @Test
    fun `an ignored node is a different chip from the same node unignored`() {
        val plain = chipNode(num = 3, shortName = "DATI", isIgnored = false)
        val ignored = chipNode(num = 3, shortName = "DATI", isIgnored = true)

        assertNotEquals(plain.toNodeChip().featureValue(), ignored.toNodeChip().featureValue())
    }

    @Test
    fun `a node with no short name still gets a labelled chip`() {
        assertEquals("???", chipNode(num = 4, shortName = "").toNodeChip().label)
    }

    @Test
    fun `an outlined chip differs from a plain one with the same text and colours`() {
        val plain = MapChipKey(label = "You", background = 1, foreground = 2)
        val outlined = plain.copy(outlined = true)

        assertNotEquals(plain.featureValue(), outlined.featureValue())
    }

    @Test
    fun `a glyph chip differs from the same chip drawn with its text`() {
        val labelled = MapChipKey(label = "SWAR", background = 1, foreground = 2)
        val sensor = labelled.copy(glyph = MapChipGlyph.SENSOR)
        val social = labelled.copy(glyph = MapChipGlyph.SOCIAL)

        assertEquals(3, listOf(labelled, sensor, social).map { it.featureValue() }.distinct().size)
    }

    @Test
    fun `an emoji short name is carried through untouched`() {
        // The chip is rasterized text, so an emoji name needs no special case — but it must survive the key.
        val chip = chipNode(num = 5, shortName = "🐰").toNodeChip()
        assertTrue(chip.label == "🐰", "label was ${chip.label}")
    }
}

class PositionsBoundingBoxTest {

    @Test
    fun `no positions means no box rather than one at the origin`() {
        assertNull(positionsBoundingBox(emptyList()))
    }

    @Test
    fun `a single position is padded into a box the camera can frame`() {
        val box = positionsBoundingBox(listOf(Position(longitude = -107.6, latitude = 34.08)))

        requireNotNull(box)
        assertTrue(box.east > box.west, "east ${box.east} should exceed west ${box.west}")
        assertTrue(box.north > box.south, "north ${box.north} should exceed south ${box.south}")
    }

    @Test
    fun `a stationary track collapses to one spot and is still framable`() {
        val stationary = List(8) { Position(longitude = -107.6197182, latitude = 34.1320507) }
        val box = requireNotNull(positionsBoundingBox(stationary))

        assertTrue(box.east > box.west)
        assertTrue(box.north > box.south)
    }

    @Test
    fun `a real spread is covered exactly`() {
        val box =
            requireNotNull(
                positionsBoundingBox(
                    listOf(
                        Position(longitude = -107.62, latitude = 34.07),
                        Position(longitude = -107.59, latitude = 34.14),
                        Position(longitude = -107.61, latitude = 34.10),
                    ),
                ),
            )

        assertEquals(-107.62, box.west)
        assertEquals(-107.59, box.east)
        assertEquals(34.07, box.south)
        assertEquals(34.14, box.north)
    }
}

/** Two corner taps become a proto bounding box, whichever order they arrive in. */
class BoundingBoxFromCornersTest {

    private fun corner(latitude: Double, longitude: Double) = Position(longitude = longitude, latitude = latitude)

    @Test
    fun `corners are normalised into south-west and north-east`() {
        val box = boundingBoxFromCorners(corner(34.14, -107.59), corner(34.07, -107.62))

        assertEquals(340700000, box.latitude_south_i)
        assertEquals(341400000, box.latitude_north_i)
        assertEquals(-1076200000, box.longitude_west_i)
        assertEquals(-1075900000, box.longitude_east_i)
    }

    @Test
    fun `tap order does not matter`() {
        val a = corner(34.14, -107.59)
        val b = corner(34.07, -107.62)

        assertEquals(boundingBoxFromCorners(a, b), boundingBoxFromCorners(b, a))
    }
}

/**
 * Which nodes are worth a chip image.
 *
 * A DEF CON-scale mesh holds thousands of nodes while a phone shows a few dozen; spending the image budget on the whole
 * list left the nodes actually on screen falling back to plain dots.
 */
class NodesInViewTest {

    private fun at(latitude: Double, longitude: Double) =
        Node(num = (latitude * 1000).toInt(), position = protoPosition(latitude, longitude))

    private fun protoPosition(latitude: Double, longitude: Double) =
        org.meshtastic.proto.Position(latitude_i = (latitude * 1e7).toInt(), longitude_i = (longitude * 1e7).toInt())

    private val box = BoundingBox(west = -108.0, south = 34.0, east = -107.0, north = 35.0)

    @Test
    fun `no bounds means every node counts`() {
        val nodes = listOf(at(0.0, 0.0), at(80.0, 170.0))
        assertEquals(nodes, nodesInView(nodes, null))
    }

    @Test
    fun `a node outside the box is dropped`() {
        val inside = at(34.5, -107.5)
        val outside = at(40.0, -100.0)

        assertEquals(listOf(inside), nodesInView(listOf(inside, outside), box))
    }

    @Test
    fun `a node exactly on an edge counts as inside`() {
        val corner = at(34.0, -108.0)
        assertEquals(listOf(corner), nodesInView(listOf(corner), box))
    }

    @Test
    fun `padding grows the box by a fraction of its own span on every side`() {
        val padded = box.padded(0.5)

        assertEquals(-108.5, padded.west)
        assertEquals(-106.5, padded.east)
        assertEquals(33.5, padded.south)
        assertEquals(35.5, padded.north)
    }

    @Test
    fun `padding is what lets a node just off screen keep its chip`() {
        val justOutside = at(35.2, -107.5)

        assertEquals(emptyList(), nodesInView(listOf(justOutside), box))
        assertEquals(listOf(justOutside), nodesInView(listOf(justOutside), box.padded(0.5)))
    }
}
