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
package org.meshtastic.app.map.offline.pmtiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineMapStyleTest {

    @Test
    fun `secondary and tertiary major roads take the medium style`() {
        assertEquals(RoadClass.Medium, OfflineMapStyle.roadClass("major_road", "secondary"))
        assertEquals(RoadClass.Medium, OfflineMapStyle.roadClass("major_road", "tertiary"))
        assertEquals(RoadClass.Major, OfflineMapStyle.roadClass("major_road", "primary"))
        assertEquals(RoadClass.Major, OfflineMapStyle.roadClass("highway", null))
        assertEquals(RoadClass.Medium, OfflineMapStyle.roadClass("medium_road", null))
        assertEquals(RoadClass.Minor, OfflineMapStyle.roadClass("minor_road", null))
        assertEquals(RoadClass.Minor, OfflineMapStyle.roadClass("path", "pedestrian"))
        assertEquals(RoadClass.Path, OfflineMapStyle.roadClass("path", "footway"))
        assertEquals(RoadClass.Rail, OfflineMapStyle.roadClass("rail", null))
    }

    @Test
    fun `minor roads are not drawn zoomed out and widen as the map zooms in`() {
        assertEquals(0f, OfflineMapStyle.roadWidthDp(RoadClass.Minor, 11.9))
        assertEquals(0.3f, OfflineMapStyle.roadWidthDp(RoadClass.Minor, 12.0))
        assertEquals(0f, OfflineMapStyle.roadWidthDp(RoadClass.Medium, 8.9))
        assertEquals(0.9f, OfflineMapStyle.roadWidthDp(RoadClass.Major, 9.0))
        assertEquals(12f, OfflineMapStyle.roadWidthDp(RoadClass.Major, 20.0))
        assertTrue(
            OfflineMapStyle.roadWidthDp(RoadClass.Major, 13.5) > OfflineMapStyle.roadWidthDp(RoadClass.Major, 12.5),
        )
    }

    @Test
    fun `widths interpolate linearly between stops`() {
        // Major road: 0.9 at zoom 9, 1.6 at zoom 12, so zoom 10.5 is halfway.
        assertEquals(1.25f, OfflineMapStyle.roadWidthDp(RoadClass.Major, 10.5), 1e-5f)
    }

    @Test
    fun `rail and streams above min_zoom 11 draw only from zoom 11`() {
        assertFalse(OfflineMapStyle.lineVisible(minZoom = 12.0, zoom = 10.9))
        assertTrue(OfflineMapStyle.lineVisible(minZoom = 12.0, zoom = 11.0))
        assertTrue(OfflineMapStyle.lineVisible(minZoom = 11.0, zoom = 6.0))
        assertTrue(OfflineMapStyle.lineVisible(minZoom = 0.0, zoom = 6.0))
    }

    @Test
    fun `tunnels and bridges come from the structure flags or the older level`() {
        assertEquals(RoadStructure.Tunnel, OfflineMapStyle.roadStructure(mapOf("is_tunnel" to true)))
        assertEquals(RoadStructure.Bridge, OfflineMapStyle.roadStructure(mapOf("is_bridge" to true)))
        assertEquals(RoadStructure.Tunnel, OfflineMapStyle.roadStructure(mapOf("pmap:level" to -1L)))
        assertEquals(RoadStructure.Bridge, OfflineMapStyle.roadStructure(mapOf("pmap:level" to 1.0)))
        assertEquals(RoadStructure.Surface, OfflineMapStyle.roadStructure(mapOf("is_tunnel" to false)))
        assertEquals(RoadStructure.Surface, OfflineMapStyle.roadStructure(emptyMap()))
    }

    @Test
    fun `sort_rank is read from any numeric attribute and clamped`() {
        assertEquals(5, OfflineMapStyle.sortRank(mapOf("sort_rank" to 5L)))
        assertEquals(7, OfflineMapStyle.sortRank(mapOf("sort_rank" to 7.0f)))
        assertEquals(100_000, OfflineMapStyle.sortRank(mapOf("sort_rank" to 1e12)))
        assertEquals(0, OfflineMapStyle.sortRank(emptyMap()))
    }
}
