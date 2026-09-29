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
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PositionCsvRowTest {

    private fun row(
        altitude: Int?,
        speed: Int?,
        track: Int?,
        latitude: Int? = 525_200_000,
        longitude: Int? = 134_050_000,
    ) = positionCsvRow(
        Position.Builder()
            .also { wb ->
                wb.latitude_i = latitude
                wb.longitude_i = longitude
                wb.sats_in_view = 5
                wb.altitude = altitude
                wb.ground_speed = speed
                wb.ground_track = track
            }
            .build(),
    )

    private fun String.cells() = removeSurrounding("\"").split("\",\"")

    /** Altitude, satellites, speed and heading, the cells after latitude and longitude. */
    private fun String.trailingCells() = cells().drop(2)

    /** Latitude and longitude. */
    private fun String.coordinateCells() = cells().take(2)

    @Test
    fun `fields the position did not report are empty cells`() {
        val line = row(altitude = null, speed = null, track = null)

        assertFalse(line.contains("null"), line)
        assertEquals(listOf("", "5", "", ""), line.trailingCells())
    }

    @Test
    fun `zero readings are written as zero`() {
        assertEquals(listOf("0", "5", "0", "0.00"), row(altitude = 0, speed = 0, track = 0).trailingCells())
    }

    @Test
    fun `coordinates the position did not report are empty cells`() {
        val line = row(altitude = 0, speed = 0, track = 0, latitude = null, longitude = null)

        assertEquals(listOf("", ""), line.coordinateCells())
    }

    @Test
    fun `zero coordinates are written as zero`() {
        val line = row(altitude = 0, speed = 0, track = 0, latitude = 0, longitude = 0)

        assertEquals(listOf("0.0", "0.0"), line.coordinateCells())
    }
}
