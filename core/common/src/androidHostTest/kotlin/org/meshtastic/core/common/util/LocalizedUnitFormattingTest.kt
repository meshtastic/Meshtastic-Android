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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The ICU engine's contract: CLDR picks unit, digits, symbol, and spacing; the resolved [MeasurementSystem] rides the
 * `ms` keyword so the engine's choice always agrees with the provider. Expected strings are CLDR output pinned by probe
 * — a change here on an ICU update is a rendering change to review, not automatically a bug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizedUnitFormattingTest {

    private val original: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(original)
    }

    @Test
    fun `imperial length in feet then miles at CLDR's crossover`() {
        Locale.setDefault(Locale.US)
        assertEquals("285 ft", formatLengthLocalized(87.0, MeasurementSystem.IMPERIAL))
        assertEquals("1,421 ft", formatLengthLocalized(433.0, MeasurementSystem.IMPERIAL))
        assertEquals("14 mi", formatLengthLocalized(22900.0, MeasurementSystem.IMPERIAL))
    }

    @Test
    fun `metric length in metres then kilometres`() {
        Locale.setDefault(Locale.US)
        assertEquals("87 m", formatLengthLocalized(87.0, MeasurementSystem.METRIC))
        assertEquals("23 km", formatLengthLocalized(22900.0, MeasurementSystem.METRIC))
    }

    // The ms keyword forces the system regardless of the locale's own region — a French locale renders
    // imperial when the user forced it, and vice versa.
    @Test
    fun `the resolved system beats the locale's region`() {
        // French renders the localized symbol for feet ("pi") — asserting the unit, not CLDR's French symbol choice.
        Locale.setDefault(Locale.FRANCE)
        val forcedImperial = formatLengthLocalized(433.0, MeasurementSystem.IMPERIAL)
        assertEquals(true, forcedImperial != null && ("ft" in forcedImperial || "pi" in forcedImperial))

        Locale.setDefault(Locale.US)
        assertEquals("433 m", formatLengthLocalized(433.0, MeasurementSystem.METRIC))
    }

    // French renders with a narrow no-break space and French grouping — the localized symbols/digits the
    // hand-rolled fallback gave up (#6854); the engine restores them.
    @Test
    fun `french rendering is CLDR's, not ASCII`() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("433\u202fm", formatLengthLocalized(433.0, MeasurementSystem.METRIC))
    }

    @Test
    fun `elevation pins the small unit at any magnitude`() {
        Locale.setDefault(Locale.US)
        assertEquals("7,431 ft", formatElevationLocalized(2265.0, MeasurementSystem.IMPERIAL))
        assertEquals("2,265 m", formatElevationLocalized(2265.0, MeasurementSystem.METRIC))
    }

    @Test
    fun `speed and rainfall render in the system's units`() {
        Locale.setDefault(Locale.US)
        assertEquals("22 mph", formatSpeedLocalized(10.0, MeasurementSystem.IMPERIAL))
        assertEquals("36 km/h", formatSpeedLocalized(10.0, MeasurementSystem.METRIC))
        assertEquals("0.47 in", formatRainfallLocalized(12.0, MeasurementSystem.IMPERIAL))
        assertEquals("12 mm", formatRainfallLocalized(12.0, MeasurementSystem.METRIC))
    }

    @Test
    fun `non-finite values fall back`() {
        Locale.setDefault(Locale.US)
        assertNull(formatLengthLocalized(Double.NaN, MeasurementSystem.METRIC))
        assertNull(formatElevationLocalized(Double.POSITIVE_INFINITY, MeasurementSystem.METRIC))
    }
}
