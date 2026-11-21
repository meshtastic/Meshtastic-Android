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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The region table is what desktop and web resolve units from, and what Android falls back to below API 28. The
 * platform wiring around it is covered per platform.
 */
class MeasurementSystemRegionTest {

    @Test
    fun `regions keep their own system`() {
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemForRegion("US"))
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemForRegion("GB"))
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemForRegion("LR"))
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemForRegion("MM"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemForRegion("ID"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemForRegion("FR"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemForRegion("AU"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemForRegion("CA"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemForRegion("DE"))
    }

    // A region-less locale must never be read as imperial; metric is what most of the world uses.
    @Test
    fun `absent or unrecognized region is metric`() {
        assertEquals(MeasurementSystem.METRIC, measurementSystemForRegion(""))
        assertEquals(MeasurementSystem.METRIC, measurementSystemForRegion("ZZ"))
    }

    @Test
    fun `region lookup ignores case`() {
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemForRegion("us"))
        assertEquals(TemperatureUnit.FAHRENHEIT, temperatureUnitForRegion("us"))
    }

    @Test
    fun `measurement-system extension maps to a system`() {
        assertEquals(MeasurementSystem.METRIC, measurementSystemOverride("metric"))
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemOverride("ussystem"))
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemOverride("uksystem"))
        assertNull(measurementSystemOverride(null))
        assertNull(measurementSystemOverride("unknown"))
    }

    @Test
    fun `fahrenheit regions`() {
        listOf("US", "BS", "BZ", "KY", "PR", "PW").forEach {
            assertEquals(TemperatureUnit.FAHRENHEIT, temperatureUnitForRegion(it), it)
        }
    }

    // The UK mixes systems: miles for distance, Celsius for temperature.
    @Test
    fun `other regions are celsius`() {
        listOf("GB", "DE", "FR", "ID", "CA", "AU", "LR", "MM", "").forEach {
            assertEquals(TemperatureUnit.CELSIUS, temperatureUnitForRegion(it), it)
        }
    }
}
