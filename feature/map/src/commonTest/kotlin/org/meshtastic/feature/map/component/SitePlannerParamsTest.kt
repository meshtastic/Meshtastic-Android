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
package org.meshtastic.feature.map.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SitePlannerParamsTest {

    @Test
    fun `toQueryUrl emits the planner's flat contract with advanced defaults and run and bridge flags`() {
        val url =
            SitePlannerParams(
                name = "Tower A",
                latitude = 51.05,
                longitude = -114.07,
                txPowerWatts = 0.5,
                txFreqMhz = 915.0,
                txHeightMeters = 12.0,
                txGainDbi = 5.5,
                colorScale = "turbo",
            )
                .toQueryUrl("http://localhost:5173")

        // Advanced params carry their (planner-matching) defaults; high_res is omitted while false.
        assertEquals(
            "http://localhost:5173/?lat=51.05&lon=-114.07&name=Tower%20A" +
                "&tx_power=0.5&tx_freq=915.0&tx_height=12.0&tx_gain=5.5&color_scale=turbo" +
                "&rx_sensitivity=-130.0&rx_height=1.0&max_range=30.0" +
                "&min_dbm=-130.0&max_dbm=-80.0&overlay_transparency=50&run=1&bridge=1",
            url,
        )
    }

    @Test
    fun `toQueryUrl emits advanced overrides and high_res only when enabled`() {
        val url =
            SitePlannerParams(
                name = "N",
                latitude = 1.0,
                longitude = 2.0,
                rxSensitivityDbm = -139.0,
                maxRangeKm = 60.0,
                highResolution = true,
            )
                .toQueryUrl("http://localhost:5173")

        assertTrue(url.contains("&rx_sensitivity=-139.0"), url)
        assertTrue(url.contains("&max_range=60.0"), url)
        assertTrue(url.contains("&high_res=1"), url)
    }

    @Test
    fun `toQueryUrl does not double the slash when the base already ends in one`() {
        val url = SitePlannerParams("N", 1.0, 2.0).toQueryUrl("https://planner.example/")
        assertTrue(url.startsWith("https://planner.example/?lat=1.0&lon=2.0&name=N"))
    }

    @Test
    fun `encodeQueryComponent percent-encodes spaces and non-ASCII`() {
        assertEquals("Tower%20%C3%91%C3%B6r%C3%B0", encodeQueryComponent("Tower Ñörð"))
        assertEquals("plain-name_1.0~", encodeQueryComponent("plain-name_1.0~"))
    }
}
