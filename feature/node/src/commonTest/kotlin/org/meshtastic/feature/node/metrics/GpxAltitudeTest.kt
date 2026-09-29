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
package org.meshtastic.feature.node.metrics

import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxAltitudeTest {

    private fun gpxFor(altitude: Int?): String = buildGpx(
        listOf(
            Position.Builder()
                .also { wb ->
                    wb.latitude_i = 525_200_000
                    wb.longitude_i = 134_050_000
                    wb.altitude = altitude
                }
                .build(),
        ),
        "track",
    )

    @Test
    fun `a sea level point keeps its elevation`() {
        assertTrue(gpxFor(altitude = 0).contains("<ele>0</ele>"))
    }

    @Test
    fun `a point without altitude has no elevation element`() {
        assertFalse(gpxFor(altitude = null).contains("<ele>"))
    }

    @Test
    fun `a measured altitude is written as the elevation`() {
        assertTrue(gpxFor(altitude = 34).contains("<ele>34</ele>"))
    }
}
