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

/**
 * Units follow the phone, and the phone is metric almost everywhere. Every case here used to produce feet: a
 * region-less app locale that ICU completed to `en_US`, a measurement-system override the region lookup discarded, and
 * an `else` branch that made imperial the fallback for anything unrecognized.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeasurementSystemTest {

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

    private fun measurementSystemFor(appLocale: String, systemQualifiers: String): MeasurementSystem {
        RuntimeEnvironment.setQualifiers(systemQualifiers)
        Locale.setDefault(Locale.forLanguageTag(appLocale))
        return getSystemMeasurementSystem()
    }

    // The reported bug: the in-app language picker offers a bare "en", which becomes the process
    // default locale. ICU completes it to en_US, so a user in Indonesia was shown feet.
    @Test
    fun `region-less app language keeps the device region`() {
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor(appLocale = "en", systemQualifiers = "in-rID"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor(appLocale = "en", systemQualifiers = "fr-rFR"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor(appLocale = "fr", systemQualifiers = "in-rID"))
    }

    @Test
    fun `region-less app language still reports imperial on an imperial device`() {
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemFor(appLocale = "en", systemQualifiers = "en-rUS"))
    }

    // Android 16: Settings > System > Language & region > Measurement system.
    @Test
    fun `measurement-system override beats the region`() {
        assertEquals(
            MeasurementSystem.METRIC,
            measurementSystemFor(appLocale = "en-US-u-ms-metric", systemQualifiers = "en-rUS"),
        )
        assertEquals(
            MeasurementSystem.IMPERIAL,
            measurementSystemFor(appLocale = "fr-FR-u-ms-ussystem", systemQualifiers = "fr-rFR"),
        )
        assertEquals(
            MeasurementSystem.IMPERIAL,
            measurementSystemFor(appLocale = "fr-FR-u-ms-uksystem", systemQualifiers = "fr-rFR"),
        )
    }

    @Test
    fun `regions keep their own system`() {
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemFor("en-US", "en-rUS"))
        // The UK is its own bucket in ICU, and it measures road distance in miles.
        assertEquals(MeasurementSystem.IMPERIAL, measurementSystemFor("en-GB", "en-rGB"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("en-ID", "in-rID"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("en-AU", "en-rAU"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("en-CA", "en-rCA"))
        assertEquals(MeasurementSystem.METRIC, measurementSystemFor("de-DE", "de-rDE"))
    }
}
