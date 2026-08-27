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

/**
 * The desktop client has no ICU measurement data, so it resolves units from the region table alone. It shares the table
 * and the override reader with Android so the two clients cannot drift into disagreeing about the same locale.
 */
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
    fun `regions keep their own system`() {
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemFor("en-US"))
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemFor("en-GB"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("en-ID"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("fr-FR"))
    }

    // Android 16 writes this extension; the desktop honors it so a shared account reads the same.
    @Test
    fun `measurement-system override beats the region`() {
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("en-US-u-ms-metric"))
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemFor("fr-FR-u-ms-ussystem"))
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemFor("fr-FR-u-ms-uksystem"))
    }

    // A region-less locale must never be read as imperial; metric is what most of the world uses.
    @Test
    fun `region-less locale is metric`() {
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("en"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("fr"))
    }
}
