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

class GpxTrackPointsTest {

    private fun position(latitude: Int?, longitude: Int?, time: Int = 0) = Position.Builder()
        .also { wb ->
            wb.latitude_i = latitude
            wb.longitude_i = longitude
            wb.time = time
        }
        .build()

    private val berlin = position(latitude = 525_200_000, longitude = 134_050_000)

    /** The `lat` and `lon` attributes of every track point, in file order. */
    private fun trackPoints(vararg positions: Position): List<Pair<String, String>> =
        Regex("""<trkpt lat="([^"]*)" lon="([^"]*)">""")
            .findAll(buildGpx(positions.toList(), "track"))
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    @Test
    fun `a position without latitude is left out of the track`() {
        assertEquals(
            listOf("52.5200000" to "13.4050000"),
            trackPoints(position(latitude = null, longitude = 134_050_000), berlin),
        )
    }

    @Test
    fun `a position without longitude is left out of the track`() {
        assertEquals(
            listOf("52.5200000" to "13.4050000"),
            trackPoints(position(latitude = 525_200_000, longitude = null), berlin),
        )
    }

    @Test
    fun `a position at zero latitude and zero longitude is left out of the track`() {
        assertEquals(listOf("52.5200000" to "13.4050000"), trackPoints(position(latitude = 0, longitude = 0), berlin))
    }

    @Test
    fun `points on the equator and the prime meridian are kept`() {
        assertEquals(
            listOf("0.0000000" to "13.4050000", "52.5200000" to "0.0000000"),
            trackPoints(
                position(latitude = 0, longitude = 134_050_000),
                position(latitude = 525_200_000, longitude = 0),
            ),
        )
    }

    @Test
    fun `points are written oldest first`() {
        val newer = position(latitude = 525_200_000, longitude = 134_050_000, time = 1_700_000_600)
        val older = position(latitude = 481_370_000, longitude = 115_750_000, time = 1_700_000_000)

        assertEquals(listOf("48.1370000" to "11.5750000", "52.5200000" to "13.4050000"), trackPoints(newer, older))
    }
}
