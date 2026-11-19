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
package org.meshtastic.core.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class MapLinksTest {

    @Test
    fun marksAndCentresThePoint() {
        assertEquals(
            "https://www.openstreetmap.org/?mlat=51.507400&mlon=-0.127800#map=17/51.507400/-0.127800",
            openStreetMapUrl(51.5074, -0.1278),
        )
    }

    @Test
    fun smallValuesStayInFixedPointForm() {
        assertEquals("0.000010", 0.00001.toUrlDegrees())
        assertEquals("-0.000010", (-0.00001).toUrlDegrees())
    }

    @Test
    fun valuesThatRoundToZeroLoseTheirSign() {
        assertEquals("0.000000", (-0.0000001).toUrlDegrees())
    }

    @Test
    fun roundsToTheNearestMicrodegree() {
        assertEquals("-122.419416", (-122.41941561).toUrlDegrees())
        assertEquals("180.000000", 180.0.toUrlDegrees())
    }
}
