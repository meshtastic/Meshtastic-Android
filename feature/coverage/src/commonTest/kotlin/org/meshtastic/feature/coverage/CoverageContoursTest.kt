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
package org.meshtastic.feature.coverage

import kotlin.test.Test
import kotlin.test.assertTrue

class CoverageContoursTest {

    @Test
    fun aSiteNameWithQuotesAndBackslashesStaysOneJsonString() {
        val site =
            Site(
                name = "Tom's \"Hill\" \\ relay",
                latitude = 47.6,
                longitude = -122.2,
                frequencyMhz = 915.0,
                txPowerDbm = 30.0,
                rxSensitivityDbm = -130.0,
            )
        val grid =
            CoverageGrid(
                site = site,
                width = 2,
                height = 2,
                north = 47.61,
                south = 47.59,
                east = -122.19,
                west = -122.21,
                dbm = DoubleArray(4) { -90.0 },
            )

        val geoJson = grid.toGeoJson()

        assertTrue("\"name\": \"Tom's \\\"Hill\\\" \\\\ relay\"" in geoJson, geoJson.take(300))
    }
}
