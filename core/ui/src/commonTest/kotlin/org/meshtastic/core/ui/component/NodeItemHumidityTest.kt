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
package org.meshtastic.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeItemHumidityTest {

    @Test
    fun `a reported zero humidity shows`() {
        assertEquals(0f, reportedHumidity(0f))
    }

    @Test
    fun `a missing humidity shows nothing`() {
        assertNull(reportedHumidity(null))
        assertNull(reportedHumidity(null, null))
    }

    @Test
    fun `a NaN humidity shows nothing`() {
        assertNull(reportedHumidity(Float.NaN, Float.NaN))
    }

    @Test
    fun `a reported zero CO2 sensor humidity shows when the environment sensor has none`() {
        assertEquals(0f, reportedHumidity(null, 0f))
    }

    @Test
    fun `the environment sensor wins over the CO2 sensor`() {
        assertEquals(40f, reportedHumidity(40f, 55f))
        assertEquals(55f, reportedHumidity(Float.NaN, 55f))
    }

    @Test
    fun `a reported zero from the environment sensor wins over a non-zero CO2 sensor`() {
        assertEquals(0f, reportedHumidity(0f, 40f))
    }
}
