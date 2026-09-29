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

    private fun row(altitude: Int?, speed: Int?, track: Int?) = positionCsvRow(
        Position.Builder()
            .also { wb ->
                wb.latitude_i = 525_200_000
                wb.longitude_i = 134_050_000
                wb.sats_in_view = 5
                wb.altitude = altitude
                wb.ground_speed = speed
                wb.ground_track = track
            }
            .build(),
    )

    /** Altitude, satellites, speed and heading, the cells after latitude and longitude. */
    private fun String.trailingCells() = removeSurrounding("\"").split("\",\"").drop(2)

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
}
