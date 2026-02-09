/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.core.model.util.UnitConversions.toTempString

class UnitConversionsTest {

    // Test data: (celsius, isFahrenheit, expected)
    private val tempTestCases =
        listOf(
            // Issue #4150: negative zero should display as "0"
            Triple(-0.1f, false, "0°C"),
            Triple(-0.2f, false, "0°C"),
            Triple(-0.4f, false, "0°C"),
            Triple(-0.49f, false, "0°C"),
            // Boundary: -0.5 rounds to -1
            Triple(-0.5f, false, "-1°C"),
            Triple(-0.9f, false, "-1°C"),
            Triple(-1.0f, false, "-1°C"),
            // Zero and small positives
            Triple(0.0f, false, "0°C"),
            Triple(0.1f, false, "0°C"),
            Triple(0.4f, false, "0°C"),
            // Typical values
            Triple(1.0f, false, "1°C"),
            Triple(20.0f, false, "20°C"),
            Triple(25.4f, false, "25°C"),
            Triple(25.5f, false, "26°C"),
            // Negative
            Triple(-5.0f, false, "-5°C"),
            Triple(-10.0f, false, "-10°C"),
            Triple(-20.4f, false, "-20°C"),
            // Fahrenheit conversions
            Triple(0.0f, true, "32°F"),
            Triple(20.0f, true, "68°F"),
            Triple(25.0f, true, "77°F"),
            Triple(100.0f, true, "212°F"),
            Triple(-40.0f, true, "-40°F"), // -40°C = -40°F
            // Issue #4150: negative zero in Fahrenheit
            Triple(-0.1f, true, "32°F"),
            Triple(-17.78f, true, "0°F"),
        )

    @Test
    fun `toTempString formats all temperatures correctly`() {
        tempTestCases.forEach { (celsius, isFahrenheit, expected) ->
            assertEquals(
                "Failed for $celsius°C (Fahrenheit=$isFahrenheit)",
                expected,
                celsius.toTempString(isFahrenheit),
            )
        }
    }

    @Test
    fun `toTempString handles extreme temperatures`() {
        assertEquals("100°C", 100.0f.toTempString(false))
        assertEquals("-40°C", (-40.0f).toTempString(false))
        assertEquals("-40°F", (-40.0f).toTempString(true))
    }

    @Test
    fun `toTempString handles NaN`() {
        assertEquals("--", Float.NaN.toTempString(false))
        assertEquals("--", Float.NaN.toTempString(true))
    }

    @Test
    fun `celsiusToFahrenheit converts correctly`() {
        mapOf(
            0.0f to 32.0f,
            20.0f to 68.0f,
            100.0f to 212.0f,
            -40.0f to -40.0f,
        ).forEach { (celsius, expectedFahrenheit) ->
            assertEquals(expectedFahrenheit, UnitConversions.celsiusToFahrenheit(celsius), 0.01f)
        }
    }

    @Test
    fun `calculateDewPoint returns expected values`() {
        // At 100% humidity, dew point equals temperature
        assertEquals(20.0f, UnitConversions.calculateDewPoint(20.0f, 100.0f), 0.1f)

        // Known reference: 20°C at 60% humidity ≈ 12°C dew point
        assertEquals(12.0f, UnitConversions.calculateDewPoint(20.0f, 60.0f), 0.5f)

        // Higher humidity = higher dew point
        val highHumidity = UnitConversions.calculateDewPoint(25.0f, 80.0f)
        val lowHumidity = UnitConversions.calculateDewPoint(25.0f, 40.0f)
        assertTrue("Dew point should be higher at higher humidity", highHumidity > lowHumidity)
    }

    @Test
    fun `calculateDewPoint handles edge cases`() {
        // 0% humidity results in NaN (ln(0) = -Infinity, causing invalid calculation)
        val zeroHumidity = UnitConversions.calculateDewPoint(20.0f, 0.0f)
        assertTrue("Expected NaN for 0% humidity", zeroHumidity.isNaN())
    }

    @Test
    fun `convertToBaseUnit converts correctly`() {
        mapOf(
            18200f to 18.2f,
            -4f to 0.004f,
            0f to 0f,
            -9f to 0f,
        ).forEach { (number, expectedvalue) ->
            assertEquals(expectedvalue, UnitConversions.convertToBaseUnit(number))
        }
    }
}
