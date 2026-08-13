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
package org.meshtastic.core.model.util

import kotlin.test.Test
import kotlin.test.assertEquals

class UnitConversionsTest {

    @Test
    fun celsiusToFahrenheit() {
        assertEquals(32f, UnitConversions.celsiusToFahrenheit(0f))
        assertEquals(212f, UnitConversions.celsiusToFahrenheit(100f))
    }

    @Test
    fun metersPerSecondToMph() {
        assertEquals(0f, UnitConversions.metersPerSecondToMph(0f))
        assertEquals(22.3694f, UnitConversions.metersPerSecondToMph(10f), absoluteTolerance = 0.001f)
    }
}
