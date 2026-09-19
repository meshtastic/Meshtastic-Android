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

import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MapCameraTest {
    private val fit = CameraPosition(target = Position(longitude = -122.0, latitude = 45.0), zoom = FRAME_MAX_ZOOM)

    @Test
    fun `a fit tighter than the framing ceiling is zoomed out to it`() {
        val tight = fit.copy(zoom = FRAME_MAX_ZOOM + 4)
        val capped = tight.cappedTo(FRAME_MAX_ZOOM)
        assertEquals(FRAME_MAX_ZOOM, capped.zoom)
        assertEquals(tight.target, capped.target)
    }

    @Test
    fun `a fit at or wider than the ceiling is left as it is`() {
        val wide = fit.copy(zoom = FRAME_MAX_ZOOM - 4)
        assertSame(wide, wide.cappedTo(FRAME_MAX_ZOOM))
        assertSame(fit, fit.cappedTo(FRAME_MAX_ZOOM))
    }
}
