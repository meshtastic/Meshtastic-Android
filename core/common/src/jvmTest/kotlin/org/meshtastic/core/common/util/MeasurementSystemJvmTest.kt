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
package org.meshtastic.core.common.util

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Wiring only: the region table and override mapping are covered in commonTest. */
class MeasurementSystemJvmTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    private fun measurementSystemFor(tag: String): MeasurementSystem {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return getSystemMeasurementSystem()
    }

    @Test
    fun `locale region and extension reach the lookup`() {
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemFor("en-US"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("fr-FR"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("en-US-u-ms-metric"))
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemFor("fr-FR-u-ms-ussystem"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("en"))
    }

    @Test
    fun `locale region reaches the temperature lookup`() {
        Locale.setDefault(Locale.US)
        assertEquals(TemperatureUnit.FAHRENHEIT, getSystemTemperatureUnit())
        Locale.setDefault(Locale.UK)
        assertEquals(TemperatureUnit.CELSIUS, getSystemTemperatureUnit())
    }
}
