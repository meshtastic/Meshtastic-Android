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

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemperatureUnitTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        RuntimeEnvironment.setQualifiers("en-rUS")
    }

    @Test
    fun `US locale prefers Fahrenheit`() {
        Locale.setDefault(Locale.US)
        assertEquals(TemperatureUnit.FAHRENHEIT, getSystemTemperatureUnit())
    }

    // The UK mixes systems: miles for distance, Celsius for temperature. Temperature must not
    // follow the (imperial) measurement system here.
    @Test
    fun `UK locale prefers Celsius despite imperial distances`() {
        Locale.setDefault(Locale.UK)
        assertEquals(TemperatureUnit.CELSIUS, getSystemTemperatureUnit())
        assertEquals(MeasurementSystem.IMPERIAL, getSystemMeasurementSystem())
    }

    @Test
    fun `German locale prefers Celsius`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals(TemperatureUnit.CELSIUS, getSystemTemperatureUnit())
    }

    // Android 14 regional preferences surface as the locale's "mu" Unicode extension.
    @Test
    fun `regional preference override wins over region default`() {
        Locale.setDefault(Locale.forLanguageTag("en-US-u-mu-celsius"))
        assertEquals(TemperatureUnit.CELSIUS, getSystemTemperatureUnit())
    }

    // The in-app language picker offers bare tags like "en". Temperature takes the region from the
    // system configuration then, the same as distance, so picking a language cannot split the two.
    @Test
    fun `region-less app language takes the temperature region from the device`() {
        RuntimeEnvironment.setQualifiers("en-rUS")
        Locale.setDefault(Locale.forLanguageTag("en"))
        assertEquals(TemperatureUnit.FAHRENHEIT, getSystemTemperatureUnit())

        RuntimeEnvironment.setQualifiers("fr-rFR")
        Locale.setDefault(Locale.forLanguageTag("en"))
        assertEquals(TemperatureUnit.CELSIUS, getSystemTemperatureUnit())
    }
}
