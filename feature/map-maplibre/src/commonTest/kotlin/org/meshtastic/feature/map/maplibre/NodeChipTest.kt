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
