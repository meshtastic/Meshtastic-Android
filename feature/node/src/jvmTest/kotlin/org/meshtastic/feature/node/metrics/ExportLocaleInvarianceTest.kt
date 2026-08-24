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
package org.meshtastic.feature.node.metrics

import org.meshtastic.proto.Position
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Display formatting follows the OS locale, but a GPX file does not: it is XML another program parses, and
 * `lat="52,5200000"` is a file no importer accepts. This pins that boundary from the wrong side of it — a comma locale
 * — because the failure is silent, happens off-device, and surfaces only when a user's import fails.
 */
class ExportLocaleInvarianceTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `gpx coordinates keep a dot separator on a comma locale`() {
        Locale.setDefault(Locale.GERMANY)

        val gpx = buildGpx(listOf(Position(latitude_i = 525_200_000, longitude_i = 134_050_000)), "Berlin")

        assertTrue(gpx.contains("lat=\"52.52"), "latitude was localized:\n$gpx")
        assertTrue(gpx.contains("lon=\"13.40"), "longitude was localized:\n$gpx")
        assertFalse(gpx.contains("52,52"), "latitude used a comma decimal:\n$gpx")
    }
}
