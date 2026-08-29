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
package org.meshtastic.feature.map.maplibre.geojson

import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.proto.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaypointFeaturesTest {

    private fun packet(waypoint: Waypoint) = DataPacket(to = NodeAddress.ID_BROADCAST, channel = 0, waypoint = waypoint)

    @Test
    fun `a waypoint with both coordinates becomes a feature`() {
        val features =
            waypointsToFeatureCollection(
                listOf(packet(Waypoint(id = 1, latitude_i = 416_000_000, longitude_i = -936_000_000))),
            )

        assertEquals(1, features.features.size)
    }

    @Test
    fun `a waypoint missing one coordinate is dropped rather than pinned to the equator`() {
        // Substituting 0 for the absent ordinate put a confident marker at the wrong place, which is worse than none.
        val latitudeOnly = packet(Waypoint(id = 2, latitude_i = 416_000_000))
        val longitudeOnly = packet(Waypoint(id = 3, longitude_i = -936_000_000))

        assertTrue(waypointsToFeatureCollection(listOf(latitudeOnly, longitudeOnly)).features.isEmpty())
    }

    @Test
    fun `null island is still dropped`() {
        assertTrue(
            waypointsToFeatureCollection(listOf(packet(Waypoint(id = 4, latitude_i = 0, longitude_i = 0))))
                .features
                .isEmpty(),
        )
    }

    @Test
    fun `an unset icon falls back to the default pin`() {
        assertEquals("📍", iconGlyph(null))
        assertEquals("📍", iconGlyph(0))
    }

    @Test
    fun `a valid code point becomes its glyph`() {
        assertEquals("A", iconGlyph('A'.code))
        // Above the BMP, so it has to be emitted as a surrogate pair.
        assertEquals("🚀", iconGlyph(0x1F680))
    }

    @Test
    fun `a code point that is not a unicode scalar value falls back to the default pin`() {
        // Whatever the sending node put in the field. An unpaired surrogate or an out-of-range value would otherwise be
        // appended as a broken character and render as tofu.
        assertEquals("📍", iconGlyph(-1))
        assertEquals("📍", iconGlyph(0xD800))
        assertEquals("📍", iconGlyph(0xDFFF))
        assertEquals("📍", iconGlyph(0x110000))
    }
}
